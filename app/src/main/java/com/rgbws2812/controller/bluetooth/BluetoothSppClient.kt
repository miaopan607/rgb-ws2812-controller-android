package com.rgbws2812.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.BluetoothUiState
import com.rgbws2812.controller.model.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var connectJob: Job? = null
    private var receiverRegistered = false

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
                    device?.let { addDiscoveredDevice(it.toDeviceInfo()) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _state.update { it.copy(isScanning = true, statusMessage = "正在扫描蓝牙设备") }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { it.copy(isScanning = false, statusMessage = "扫描完成") }
                }
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
            .map { it.toDeviceInfo() }
            .sortedBy { it.displayName }
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
        registerReceiverIfNeeded()
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        _state.update { it.copy(discoveredDevices = emptyList(), errorMessage = null) }
        val started = bluetoothAdapter.startDiscovery()
        if (!started) {
            _state.update { it.copy(isScanning = false, errorMessage = "蓝牙扫描启动失败") }
        }
    }

    fun stopDiscovery() {
        adapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
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
            _state.update {
                it.copy(
                    connectionState = BluetoothConnectionState.Connecting,
                    connectedDevice = null,
                    statusMessage = "正在连接 ${device.displayName}",
                    errorMessage = null
                )
            }

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
    }

    fun disconnect() {
        connectJob?.cancel()
        disconnectSocketOnly()
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
            val activeSocket = socket ?: throw IOException("蓝牙未连接")
            activeSocket.outputStream.write(bytes)
            activeSocket.outputStream.flush()
        }.onFailure { throwable ->
            disconnectSocketOnly()
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
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDeviceInfo(): DeviceInfo =
        DeviceInfo(
            name = name.orEmpty(),
            address = address,
            bonded = bondState == BluetoothDevice.BOND_BONDED
        )

    private fun addDiscoveredDevice(device: DeviceInfo) {
        _state.update { current ->
            val merged = (current.discoveredDevices.filterNot { it.address == device.address } + device)
                .sortedWith(compareBy<DeviceInfo> { !it.bonded }.thenBy { it.displayName })
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(discoveryReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun disconnectSocketOnly() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        val SppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
