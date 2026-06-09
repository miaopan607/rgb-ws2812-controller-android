@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.rgbws2812.controller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.DeviceInfo
import com.rgbws2812.controller.model.Preset
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.model.SendHistoryItem
import com.rgbws2812.controller.protocol.RgbFrameBuilder
import com.rgbws2812.controller.ui.theme.RgbControllerTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RgbControllerTheme {
                RgbControllerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun RgbControllerApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissions = remember { bluetoothPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshBluetooth()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
        viewModel.refreshBluetooth()
    }

    LaunchedEffect(uiState.errorMessage, uiState.statusMessage) {
        val message = uiState.errorMessage ?: uiState.statusMessage
        if (!message.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.clearMessages()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopDiscovery() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeaderSection(
                    bluetoothState = uiState.bluetooth,
                    onRefresh = { viewModel.refreshBluetooth() },
                    onRequestPermission = { permissionLauncher.launch(permissions) }
                )
            }
            item {
                DeviceSection(
                    state = uiState.bluetooth,
                    onRefresh = { viewModel.refreshBluetooth() },
                    onScan = { viewModel.startDiscovery() },
                    onStopScan = { viewModel.stopDiscovery() },
                    onConnect = { viewModel.connect(it) },
                    onDisconnect = { viewModel.disconnect() }
                )
            }
            item {
                ControlSection(
                    state = uiState.control,
                    autoSendEnabled = uiState.autoSendEnabled,
                    onMode = { viewModel.updateMode(it) },
                    onColor = { r, g, b -> viewModel.updateColor(r, g, b) },
                    onBrightness = { viewModel.updateBrightness(it) },
                    onPeriod = { viewModel.updatePeriod(it) },
                    onToggleLed = { viewModel.toggleOrderLed(it) },
                    onOrder = { viewModel.setOrder(it) },
                    onAutoSend = { viewModel.setAutoSend(it) }
                )
            }
            item {
                FrameSection(
                    state = uiState,
                    onSend = { viewModel.sendCurrent() },
                    onManualHex = { viewModel.updateManualHex(it) },
                    onLoadCurrentFrame = { viewModel.loadCurrentFrameToManualHex() },
                    onSendManual = { viewModel.sendManualHex() }
                )
            }
            item {
                WorkbenchSection(
                    state = uiState,
                    onSavePreset = { viewModel.savePreset(it) },
                    onLoadPreset = { viewModel.loadPreset(it) },
                    onRenamePreset = { preset, name -> viewModel.renamePreset(preset, name) },
                    onDeletePreset = { viewModel.deletePreset(it) },
                    onResendHistory = { viewModel.resendHistory(it) },
                    onClearHistory = { viewModel.clearHistory() },
                    onExport = { viewModel.exportData() },
                    onImportText = { viewModel.updateImportExportText(it) },
                    onImport = { viewModel.importData() }
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeaderSection(
    bluetoothState: com.rgbws2812.controller.model.BluetoothUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("RGB 彩灯控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = bluetoothState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onRefresh,
                    label = { Text(if (bluetoothState.isEnabled) "刷新设备" else "检查蓝牙") }
                )
                AssistChip(
                    onClick = onRequestPermission,
                    label = { Text("授权") }
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (bluetoothState.connectionState) {
                                BluetoothConnectionState.Connected -> "已连接"
                                BluetoothConnectionState.Connecting -> "连接中"
                                BluetoothConnectionState.Disconnected -> "未连接"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    state: com.rgbws2812.controller.model.BluetoothUiState,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DeviceInfo) -> Unit,
    onDisconnect: () -> Unit
) {
    AppCard(title = "蓝牙设备") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRefresh) { Text("已配对") }
            OutlinedButton(onClick = if (state.isScanning) onStopScan else onScan) {
                Text(if (state.isScanning) "停止扫描" else "扫描发现")
            }
            if (state.connectionState == BluetoothConnectionState.Connected) {
                OutlinedButton(onClick = onDisconnect) { Text("断开") }
            }
        }
        if (state.isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(12.dp))
        DeviceList(
            title = "已配对",
            devices = state.pairedDevices,
            connectedAddress = state.connectedDevice?.address,
            onConnect = onConnect
        )
        Spacer(modifier = Modifier.height(10.dp))
        DeviceList(
            title = "扫描结果",
            devices = state.discoveredDevices,
            connectedAddress = state.connectedDevice?.address,
            onConnect = onConnect
        )
    }
}

@Composable
private fun DeviceList(
    title: String,
    devices: List<DeviceInfo>,
    connectedAddress: String?,
    onConnect: (DeviceInfo) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    if (devices.isEmpty()) {
        Text("暂无设备", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                color = if (connectedAddress == device.address) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onConnect(device) }) {
                        Text(if (connectedAddress == device.address) "重连" else "连接")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    state: RgbControlState,
    autoSendEnabled: Boolean,
    onMode: (ControlMode) -> Unit,
    onColor: (Int, Int, Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onPeriod: (Int) -> Unit,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit,
    onAutoSend: (Boolean) -> Unit
) {
    AppCard(title = "控制参数") {
        Text("模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onMode(mode) },
                    label = { Text(mode.title) }
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (state.mode != ControlMode.Gradient) {
            ColorControls(state = state, onColor = onColor)
            Spacer(modifier = Modifier.height(12.dp))
        }
        NumberSlider(
            label = "亮度",
            value = state.brightness,
            range = 0..255,
            onValue = onBrightness
        )
        if (state.mode == ControlMode.Breath) {
            NumberSlider(
                label = "呼吸周期 x100ms",
                value = state.period,
                range = 1..255,
                onValue = onPeriod
            )
        }
        if (state.mode == ControlMode.Flow) {
            FlowOrderEditor(
                order = state.order,
                onToggleLed = onToggleLed,
                onOrder = onOrder
            )
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = autoSendEnabled, onCheckedChange = onAutoSend)
            Column {
                Text("自动发送", fontWeight = FontWeight.SemiBold)
                Text("默认关闭，开启后参数变化会短延迟发送", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColorControls(
    state: RgbControlState,
    onColor: (Int, Int, Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(state.red, state.green, state.blue))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(state.rgbHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(
                "RGB ${state.red}, ${state.green}, ${state.blue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    NumberSlider("R", state.red, 0..255) { onColor(it, state.green, state.blue) }
    NumberSlider("G", state.green, 0..255) { onColor(state.red, it, state.blue) }
    NumberSlider("B", state.blue, 0..255) { onColor(state.red, state.green, it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "红" to Triple(255, 0, 0),
            "绿" to Triple(0, 255, 0),
            "蓝" to Triple(0, 0, 255),
            "白" to Triple(255, 255, 255),
            "暖" to Triple(255, 160, 64)
        ).forEach { (label, rgb) ->
            OutlinedButton(onClick = { onColor(rgb.first, rgb.second, rgb.third) }) {
                Text(label)
            }
        }
    }
}

@Composable
private fun NumberSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let { onValue(it.coerceIn(range.first, range.last)) } },
                modifier = Modifier.width(92.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun FlowOrderEditor(
    order: List<Int>,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text("流水顺序", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    val displayToHardware = listOf(3, 2, 1, 0, 4, 5, 6, 7)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        displayToHardware.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { led ->
                    val step = order.indexOf(led).takeIf { it >= 0 }?.plus(1)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.35f)
                            .clickable { onToggleLed(led) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        color = if (step != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("LED $led", fontWeight = FontWeight.SemiBold)
                            Text(step?.let { "第 $it 步" } ?: "未选", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mapOf(
            "正序" to listOf(3, 2, 1, 0, 4, 5, 6, 7),
            "反序" to listOf(7, 6, 5, 4, 0, 1, 2, 3),
            "先偶后奇" to listOf(3, 1, 4, 6, 2, 0, 5, 7),
            "交错" to listOf(3, 4, 2, 5, 1, 6, 0, 7)
        ).forEach { (label, preset) ->
            OutlinedButton(onClick = { onOrder(preset) }) { Text(label) }
        }
        OutlinedButton(onClick = { onOrder(emptyList()) }) { Text("清空") }
    }
}

@Composable
private fun FrameSection(
    state: MainUiState,
    onSend: () -> Unit,
    onManualHex: (String) -> Unit,
    onLoadCurrentFrame: () -> Unit,
    onSendManual: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AppCard(title = "帧与发送") {
        Text("当前 17 字节帧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF111827)
        ) {
            Text(
                text = state.frame.spacedHex(),
                modifier = Modifier.padding(14.dp),
                color = Color(0xFFD1FAE5),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.bluetooth.connectionState == BluetoothConnectionState.Connected,
                onClick = onSend
            ) { Text("发送当前帧") }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.frame.spacedHex())) }) { Text("复制 Hex") }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.frame.compactHex())) }) { Text("复制紧凑") }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ByteTable(frameHex = state.frame.spacedHex())
        Spacer(modifier = Modifier.height(14.dp))
        Text("手动 Hex", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.manualHex,
            onValueChange = onManualHex,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp),
            minLines = 3,
            label = { Text("17 字节 Hex") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadCurrentFrame) { Text("填入当前帧") }
            Button(
                enabled = state.bluetooth.connectionState == BluetoothConnectionState.Connected,
                onClick = onSendManual
            ) { Text("校验并发送") }
        }
    }
}

@Composable
private fun ByteTable(frameHex: String) {
    val values = frameHex.split(" ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(index.toString().padStart(2, '0'), modifier = Modifier.width(32.dp), fontFamily = FontFamily.Monospace)
                Text(RgbFrameBuilder.ByteLabels[index], modifier = Modifier.width(58.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WorkbenchSection(
    state: MainUiState,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (Preset) -> Unit,
    onRenamePreset: (Preset, String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onResendHistory: (SendHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onExport: () -> Unit,
    onImportText: (String) -> Unit,
    onImport: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("预设", "历史", "导入导出")
    AppCard(title = "工作台") {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (selectedTab) {
            0 -> PresetPanel(
                presets = state.presets,
                onSavePreset = onSavePreset,
                onLoadPreset = onLoadPreset,
                onRenamePreset = onRenamePreset,
                onDeletePreset = onDeletePreset
            )

            1 -> HistoryPanel(
                history = state.history,
                onResendHistory = onResendHistory,
                onClearHistory = onClearHistory
            )

            2 -> ImportExportPanel(
                text = state.importExportText,
                onExport = onExport,
                onImportText = onImportText,
                onImport = onImport
            )
        }
    }
}

@Composable
private fun PresetPanel(
    presets: List<Preset>,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (Preset) -> Unit,
    onRenamePreset: (Preset, String) -> Unit,
    onDeletePreset: (Preset) -> Unit
) {
    var name by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("预设名称") }
        )
        Button(onClick = {
            onSavePreset(name)
            name = ""
        }) { Text("保存") }
    }
    Spacer(modifier = Modifier.height(10.dp))
    if (presets.isEmpty()) {
        Text("暂无预设", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    presets.forEach { preset ->
        PresetRow(
            preset = preset,
            onLoad = { onLoadPreset(preset) },
            onRename = { onRenamePreset(preset, it) },
            onDelete = { onDeletePreset(preset) }
        )
    }
}

@Composable
private fun PresetRow(
    preset: Preset,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var newName by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            } else {
                Text(preset.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${preset.control.mode.title} ${preset.control.rgbHex} 亮度 ${preset.control.brightness}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoad) { Text("加载") }
                OutlinedButton(onClick = {
                    if (editing) {
                        onRename(newName)
                    }
                    editing = !editing
                }) { Text(if (editing) "保存名称" else "重命名") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<SendHistoryItem>,
    onResendHistory: (SendHistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("最近 ${history.size} 条", fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onClearHistory, enabled = history.isNotEmpty()) { Text("清空") }
    }
    if (history.isEmpty()) {
        Text("暂无发送历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    history.forEach { item ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${item.source} · ${formatTime(item.timestamp)}", fontWeight = FontWeight.SemiBold)
                Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(item.hex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onResendHistory(item) }) { Text("重发") }
            }
        }
    }
}

@Composable
private fun ImportExportPanel(
    text: String,
    onExport: () -> Unit,
    onImportText: (String) -> Unit,
    onImport: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExport) { Text("生成导出 JSON") }
        OutlinedButton(onClick = onImport) { Text("导入 JSON") }
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = text,
        onValueChange = onImportText,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        minLines = 8,
        label = { Text("预设和历史 JSON") }
    )
}

@Composable
private fun AppCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
