package com.rgbws2812.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.BluetoothTransport
import com.rgbws2812.controller.model.BluetoothUiState
import com.rgbws2812.controller.model.DeviceInfo
import com.rgbws2812.controller.model.SerialPortConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

class BluetoothSppClient(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var socket: BluetoothSocket? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var configCharacteristic: BluetoothGattCharacteristic? = null
    private var connectJob: Job? = null
    private var receiverRegistered = false
    private var bleScanning = false
    private var classicDiscoveryActive = false
    private var pendingBleConnection: CompletableDeferred<BluetoothGattCharacteristic>? = null
    private var pendingBleWrite: CompletableDeferred<Unit>? = null
    private var bleMtu = DefaultBleMtu
    private val bleWriteMutex = Mutex()

    private val _state = MutableStateFlow(
        BluetoothUiState(
            isAvailable = adapter != null,
            isEnabled = adapter?.isEnabled == true,
            statusMessage = if (adapter == null) "此设备不支持蓝牙" else "蓝牙未连接"
        )
    )
    val state: StateFlow<BluetoothUiState> = _state

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addDiscoveredDevice(it.toDeviceInfo(BluetoothTransport.ClassicSpp)) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    classicDiscoveryActive = true
                    updateScanningState("正在扫描蓝牙设备")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    classicDiscoveryActive = false
                    updateScanningState("扫描完成")
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDiscoveredDevice(result.toDeviceInfo())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addDiscoveredDevice(it.toDeviceInfo()) }
        }

        override fun onScanFailed(errorCode: Int) {
            bleScanning = false
            updateScanningState("扫描完成")
            _state.update {
                it.copy(errorMessage = "BLE 扫描失败：$errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeBleConnectionFailure("GATT 连接失败：$status")
                closeGatt()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(PreferredBleMtu)
                    }
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    completeBleConnectionFailure("BLE 已断开")
                    closeGatt()
                    _state.update {
                        it.copy(
                            connectionState = BluetoothConnectionState.Disconnected,
                            connectedDevice = null,
                            statusMessage = "蓝牙未连接"
                        )
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bleMtu = mtu.coerceAtLeast(DefaultBleMtu)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeBleConnectionFailure("发现 BLE 服务失败：$status")
                return
            }
            val service = gatt.getService(Ch9143ServiceUuid)
            val characteristic = service?.getCharacteristic(Ch9143WriteUuid)
            if (service == null || characteristic == null) {
                completeBleConnectionFailure("未找到 CH9143 BLE-UART 写入特征 FFF2")
                return
            }
            writeCharacteristic = characteristic
            val config = service.getCharacteristic(Ch9143ConfigUuid)
            configCharacteristic = config
            characteristic.writeType = if (characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            config?.writeType = if (config.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            enableNotifyIfAvailable(gatt, service)
            pendingBleConnection?.complete(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val pending = pendingBleWrite ?: return
            pendingBleWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pending.complete(Unit)
            } else {
                pending.completeExceptionally(IOException("BLE 写入失败：$status"))
            }
        }
    }

    fun refreshPairedDevices() {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            _state.update { it.copy(isAvailable = false, isEnabled = false, errorMessage = "此设备不支持蓝牙") }
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            _state.update {
                it.copy(
                    isAvailable = true,
                    isEnabled = false,
                    pairedDevices = emptyList(),
                    statusMessage = "请先打开系统蓝牙"
                )
            }
            return
        }

        val devices = bluetoothAdapter.bondedDevices
            .orEmpty()
            .map { it.toDeviceInfo(BluetoothTransport.ClassicSpp) }
            .sortedWith(DeviceInfoComparator)
        _state.update {
            it.copy(
                isAvailable = true,
                isEnabled = true,
                pairedDevices = devices,
                statusMessage = if (devices.isEmpty()) "没有已配对设备" else "已加载 ${devices.size} 个已配对设备",
                errorMessage = null
            )
        }
    }

    fun startDiscovery() {
        val bluetoothAdapter = adapter ?: return
        if (!bluetoothAdapter.isEnabled) {
            _state.update { it.copy(isEnabled = false, errorMessage = "请先打开系统蓝牙") }
            return
        }
        _state.update {
            it.copy(
                isAvailable = true,
                isEnabled = true,
                isScanning = true,
                discoveredDevices = emptyList(),
                statusMessage = "正在启动蓝牙扫描",
                errorMessage = null
            )
        }

        runCatching {
            registerReceiverIfNeeded()
            startBleScan(bluetoothAdapter)
            startClassicDiscovery(bluetoothAdapter)
        }.onFailure { throwable ->
            stopDiscovery()
            _state.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "蓝牙扫描未启动",
                    errorMessage = "蓝牙扫描失败：${throwable.toUserMessage()}"
                )
            }
        }
    }

    fun stopDiscovery() {
        stopBleScan()
        adapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
        classicDiscoveryActive = false
        _state.update { it.copy(isScanning = false) }
    }

    fun connect(device: DeviceInfo) {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.update { it.copy(errorMessage = "蓝牙不可用或未开启") }
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            disconnectSocketOnly()
            disconnectGattOnly()
            stopDiscovery()
            _state.update {
                it.copy(
                    connectionState = BluetoothConnectionState.Connecting,
                    connectedDevice = null,
                    statusMessage = "正在连接 ${device.displayName}",
                    errorMessage = null
                )
            }

            when (device.transport) {
                BluetoothTransport.BleUart -> connectBle(bluetoothAdapter, device)
                BluetoothTransport.ClassicSpp -> connectSpp(bluetoothAdapter, device)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        disconnectSocketOnly()
        disconnectGattOnly()
        _state.update {
            it.copy(
                connectionState = BluetoothConnectionState.Disconnected,
                connectedDevice = null,
                statusMessage = "蓝牙已断开"
            )
        }
    }

    suspend fun send(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when {
                socket != null -> sendSpp(bytes)
                gatt != null && writeCharacteristic != null -> sendBle(bytes, writeCharacteristic)
                else -> throw IOException("蓝牙未连接")
            }
        }.onFailure { throwable ->
            disconnectSocketOnly()
            disconnectGattOnly()
            _state.update {
                it.copy(
                    connectionState = BluetoothConnectionState.Disconnected,
                    connectedDevice = null,
                    errorMessage = "发送失败：${throwable.message ?: throwable.javaClass.simpleName}",
                    statusMessage = "蓝牙未连接"
                )
            }
        }
    }

    suspend fun configureSerialPort(config: SerialPortConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = config.clamped()
            val command = "AT+UART=${clean.baudRate},${clean.dataBits},${clean.stopBits},${clean.parity.wireValue},${clean.timeoutMs}\r\n"
                .encodeToByteArray()
            when {
                gatt != null -> sendBle(command, configCharacteristic ?: writeCharacteristic)
                socket != null -> sendSpp(command)
                else -> throw IOException("蓝牙未连接")
            }
        }.onFailure { throwable ->
            _state.update {
                it.copy(errorMessage = "串口配置失败：${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun release() {
        stopDiscovery()
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
        disconnectSocketOnly()
        disconnectGattOnly()
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectSpp(bluetoothAdapter: BluetoothAdapter, device: DeviceInfo) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                bluetoothAdapter.cancelDiscovery()
                val remote = bluetoothAdapter.getRemoteDevice(device.address)
                val rfcomm = remote.createRfcommSocketToServiceRecord(SppUuid)
                rfcomm.connect()
                rfcomm
            }
        }

        result.onSuccess { connectedSocket ->
            socket = connectedSocket
            _state.update {
                it.copy(
                    isScanning = false,
                    connectionState = BluetoothConnectionState.Connected,
                    connectedDevice = device,
                    statusMessage = "已连接 ${device.displayName}",
                    errorMessage = null
                )
            }
        }.onFailure { throwable ->
            disconnectSocketOnly()
            _state.update {
                it.copy(
                    connectionState = BluetoothConnectionState.Disconnected,
                    connectedDevice = null,
                    statusMessage = "蓝牙未连接",
                    errorMessage = "连接失败：${throwable.message ?: throwable.javaClass.simpleName}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBle(bluetoothAdapter: BluetoothAdapter, device: DeviceInfo) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val remote = bluetoothAdapter.getRemoteDevice(device.address)
                val deferred = CompletableDeferred<BluetoothGattCharacteristic>()
                pendingBleConnection = deferred
                gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    remote.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    remote.connectGatt(appContext, false, gattCallback)
                }
                withTimeout(ConnectTimeoutMillis) { deferred.await() }
            }
        }

        result.onSuccess {
            _state.update {
                it.copy(
                    isScanning = false,
                    connectionState = BluetoothConnectionState.Connected,
                    connectedDevice = device,
                    statusMessage = "已连接 ${device.displayName}",
                    errorMessage = null
                )
            }
        }.onFailure { throwable ->
            disconnectGattOnly()
            _state.update {
                it.copy(
                    connectionState = BluetoothConnectionState.Disconnected,
                    connectedDevice = null,
                    statusMessage = "蓝牙未连接",
                    errorMessage = "连接失败：${throwable.toBleConnectMessage()}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan(bluetoothAdapter: BluetoothAdapter) {
        val scanner = bluetoothAdapter.bluetoothLeScanner
            ?: throw IllegalStateException("BLE 扫描器不可用")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, bleScanCallback)
        bleScanning = true
        updateScanningState("正在扫描 BLE-UART 和经典蓝牙设备")
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery(bluetoothAdapter: BluetoothAdapter) {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        classicDiscoveryActive = bluetoothAdapter.startDiscovery()
        if (!classicDiscoveryActive && !bleScanning) {
            throw IllegalStateException("蓝牙扫描启动失败，请确认系统蓝牙和附近设备可被发现")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (bleScanning) {
            runCatching { scanner.stopScan(bleScanCallback) }
            bleScanning = false
        }
    }

    private fun updateScanningState(message: String) {
        _state.update {
            it.copy(
                isScanning = bleScanning || classicDiscoveryActive,
                statusMessage = message
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDeviceInfo(transport: BluetoothTransport): DeviceInfo =
        DeviceInfo(
            name = name.orEmpty(),
            address = address,
            bonded = bondState == BluetoothDevice.BOND_BONDED,
            transport = transport,
            preferred = name.orEmpty().contains(TargetDeviceName, ignoreCase = true)
        )

    @SuppressLint("MissingPermission")
    private fun ScanResult.toDeviceInfo(): DeviceInfo {
        val localName = scanRecord?.deviceName.orEmpty()
        val deviceName = localName.ifBlank { device.name.orEmpty() }
        val uuids = scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        return DeviceInfo(
            name = deviceName,
            address = device.address,
            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
            transport = BluetoothTransport.BleUart,
            preferred = deviceName.contains(TargetDeviceName, ignoreCase = true) || Ch9143ServiceUuid in uuids
        )
    }

    private fun addDiscoveredDevice(device: DeviceInfo) {
        _state.update { current ->
            val merged = (current.discoveredDevices.filterNot {
                it.address == device.address && it.transport == device.transport
            } + device).sortedWith(DeviceInfoComparator)
            current.copy(discoveredDevices = merged)
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifyIfAvailable(gatt: BluetoothGatt, service: BluetoothGattService) {
        val notifyCharacteristic = service.getCharacteristic(Ch9143NotifyUuid) ?: return
        if (!notifyCharacteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)) return
        gatt.setCharacteristicNotification(notifyCharacteristic, true)
        val descriptor = notifyCharacteristic.getDescriptor(ClientCharacteristicConfigUuid) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun completeBleConnectionFailure(message: String) {
        pendingBleConnection?.completeExceptionally(IOException(message))
        pendingBleConnection = null
    }

    private fun sendSpp(bytes: ByteArray) {
        val activeSocket = socket ?: throw IOException("蓝牙未连接")
        activeSocket.outputStream.write(bytes)
        activeSocket.outputStream.flush()
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendBle(bytes: ByteArray, targetCharacteristic: BluetoothGattCharacteristic?) {
        bleWriteMutex.withLock {
            val activeGatt = gatt ?: throw IOException("BLE 未连接")
            val characteristic = targetCharacteristic ?: throw IOException("BLE 写入特征不可用")
            val chunkSize = (bleMtu - BleAttOverhead).coerceAtLeast(MinBleChunkSize)
            bytes.asIterable().chunked(chunkSize).forEach { chunk ->
                val payload = chunk.toByteArray()
                val deferred = CompletableDeferred<Unit>()
                val expectsCallback = characteristic.writeType != BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                if (expectsCallback) {
                    pendingBleWrite = deferred
                }
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeCharacteristic(characteristic, payload, characteristic.writeType) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = payload
                    @Suppress("DEPRECATION")
                    activeGatt.writeCharacteristic(characteristic)
                }
                if (!started) {
                    pendingBleWrite = null
                    throw IOException("BLE 写入未启动")
                }
                if (expectsCallback) {
                    withTimeout(WriteTimeoutMillis) { deferred.await() }
                } else {
                    delay(NoResponseWriteDelayMillis)
                }
            }
        }
    }

    private fun disconnectSocketOnly() {
        runCatching { socket?.close() }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattOnly() {
        pendingBleConnection?.cancel()
        pendingBleConnection = null
        pendingBleWrite?.cancel()
        pendingBleWrite = null
        writeCharacteristic = null
        configCharacteristic = null
        bleMtu = DefaultBleMtu
        runCatching { gatt?.disconnect() }
        closeGatt()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        configCharacteristic = null
    }

    private companion object {
        const val TargetDeviceName = "CH9143BLE2U"
        const val DefaultBleMtu = 23
        const val PreferredBleMtu = 64
        const val BleAttOverhead = 3
        const val MinBleChunkSize = 20
        const val ConnectTimeoutMillis = 12_000L
        const val WriteTimeoutMillis = 2_000L
        const val NoResponseWriteDelayMillis = 25L
        val SppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val Ch9143ServiceUuid: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val Ch9143NotifyUuid: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
        val Ch9143WriteUuid: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
        val Ch9143ConfigUuid: UUID = UUID.fromString("0000FFF3-0000-1000-8000-00805F9B34FB")
        val ClientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val DeviceInfoComparator: Comparator<DeviceInfo> =
            compareByDescending<DeviceInfo> { it.preferred }
                .thenBy { it.transport != BluetoothTransport.BleUart }
                .thenBy { !it.bonded }
                .thenBy { it.displayName }
    }
}

private fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean =
    properties and property != 0

private fun Throwable.toUserMessage(): String =
    when (this) {
        is SecurityException -> "缺少蓝牙扫描权限，请重新授权"
        is ActivityNotFoundException -> "系统蓝牙扫描服务不可用"
        else -> message ?: javaClass.simpleName
    }

private fun Throwable.toBleConnectMessage(): String =
    when (this) {
        is TimeoutCancellationException -> "连接超时，请确认 CH9143BLE2U 已上电且未被其他设备连接"
        else -> message ?: javaClass.simpleName
    }
