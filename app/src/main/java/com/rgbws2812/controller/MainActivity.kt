@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.rgbws2812.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.DeviceInfo
import com.rgbws2812.controller.model.GradientPattern
import com.rgbws2812.controller.model.Preset
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.model.RgbColor
import com.rgbws2812.controller.model.SendHistoryItem
import com.rgbws2812.controller.model.isGradientFamily
import com.rgbws2812.controller.protocol.RgbFrameBuilder
import com.rgbws2812.controller.protocol.toHexByte
import com.rgbws2812.controller.ui.theme.RgbControllerTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MaxInlineDevices = 8
private const val MaxInlinePresets = 8
private const val MaxInlineHistory = 12
private const val PageTransitionMillis = 220
private const val PredictiveBackTravelFraction = 0.32f
private const val LedPreviewDiscoStepMillis = 250
private const val LedPreviewGamma = 0.3f
private val FlowEditorGridMaxWidth = 480.dp
private val LedPreviewPanelMaxWidth = 220.dp
private val LedPreviewPanelMaxHeight = 96.dp
private val LedPreviewDiameter = 18.dp
private val LedPreviewColumnGap = LedPreviewDiameter * 1.5f
private val LedPreviewRowGap = LedPreviewDiameter
private val LedPreviewGridWidth = LedPreviewDiameter * 4f + LedPreviewColumnGap * 3f
private val LedPreviewGridHeight = LedPreviewDiameter * 2f + LedPreviewRowGap
private val SectionHorizontalPadding = 20.dp

private val DisplayToHardwareOrder = listOf(3, 2, 1, 0, 4, 5, 6, 7)
private val WorkbenchTabs = listOf("预设", "历史", "导入导出")
private val BluetoothTabs = listOf("扫描发现", "已配对")
private val FlowEditorTabs = listOf("基础流水", "高级画面")
private data class PaletteColor(val label: String, val red: Int, val green: Int, val blue: Int)

private data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

private val BasicColors = listOf(
    PaletteColor("红", 255, 0, 0),
    PaletteColor("绿", 0, 255, 0),
    PaletteColor("蓝", 0, 0, 255)
)
private val PrimaryControlModes = listOf(
    ControlMode.Static,
    ControlMode.Flow,
    ControlMode.Breath,
    ControlMode.Disco,
    ControlMode.Gradient
)
private val FlowOrderPresets = listOf(
    "正序" to listOf(3, 2, 1, 0, 4, 5, 6, 7),
    "反序" to listOf(7, 6, 5, 4, 0, 1, 2, 3),
    "先偶后奇" to listOf(3, 1, 4, 6, 2, 0, 5, 7),
    "交错" to listOf(3, 4, 2, 5, 1, 6, 0, 7)
)

private fun rgbToHsv(red: Int, green: Int, blue: Int): HsvColor {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(red, green, blue, hsv)
    return HsvColor(hsv[0], hsv[1], hsv[2])
}

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Triple<Int, Int, Int> {
    val color = AndroidColor.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
    return Triple(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
}

private fun colorForHue(hue: Float): Color {
    val rgb = hsvToRgb(hue, 1f, 1f)
    return Color(rgb.first, rgb.second, rgb.third)
}

private fun isBasicPaletteColor(red: Int, green: Int, blue: Int): Boolean =
    BasicColors.any { it.red == red && it.green == green && it.blue == blue }

private fun checkMarkColor(red: Int, green: Int, blue: Int): Color {
    val luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
    return if (luminance > 0.58f) Color.Black else Color.White
}

@Composable
private fun connectedStatusColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF7DDA9B) else Color(0xFF1B7F45)

@Composable
private fun frameCodeBlockColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF0B1220) else Color(0xFF111827)

@Composable
private fun frameCodeTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA7F3D0) else Color(0xFFD1FAE5)

private enum class AppPage {
    Controller,
    Bluetooth
}

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
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(AppPage.Controller) }
    var previewVisible by rememberSaveable { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableStateOf(0f) }
    val isPredictiveBackInProgress = predictiveBackProgress > 0f
    val navigateBackToController = {
        predictiveBackProgress = 0f
        currentPage = AppPage.Controller
    }
    val permissions = remember { bluetoothPermissions() }
    var bluetoothPermissionsGranted by remember {
        mutableStateOf(context.hasBluetoothPermissions(permissions))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        bluetoothPermissionsGranted = permissions.all { result[it] == true } || context.hasBluetoothPermissions(permissions)
        if (bluetoothPermissionsGranted) {
            viewModel.refreshBluetooth()
        }
    }

    LaunchedEffect(Unit) {
        bluetoothPermissionsGranted = context.hasBluetoothPermissions(permissions)
        if (bluetoothPermissionsGranted) {
            viewModel.refreshBluetooth()
        }
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

    PredictiveBackHandler(enabled = currentPage == AppPage.Bluetooth) { progress ->
        try {
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            navigateBackToController()
        } finally {
            predictiveBackProgress = 0f
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { pageTransitionSpec() },
                label = "TopBarTransition"
            ) { page ->
                if (page == AppPage.Controller) {
                    ControllerTopBar(
                        connectionState = uiState.bluetooth.connectionState,
                        flowFramesValid = uiState.flowFramesValid,
                        autoSendEnabled = uiState.autoSendEnabled,
                        showAdvancedSendPanel = uiState.showAdvancedSendPanel,
                        previewVisible = previewVisible,
                        onSendClick = { viewModel.sendCurrent() },
                        onPreviewClick = { previewVisible = true },
                        onBluetoothClick = { currentPage = AppPage.Bluetooth },
                        onToggleAutoSend = { viewModel.setAutoSend(it) },
                        onToggleAdvancedSendPanel = { viewModel.setShowAdvancedSendPanel(it) },
                        onResetAllParameters = { viewModel.resetControlParameters() }
                    )
                } else {
                    BluetoothTopBar(
                        onBack = navigateBackToController
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (isPredictiveBackInProgress && currentPage == AppPage.Bluetooth) {
            Box(modifier = Modifier.fillMaxSize()) {
                ControllerContent(
                    modifier = Modifier
                        .padding(padding)
                        .graphicsLayer {
                            translationX = -size.width * 0.08f * (1f - predictiveBackProgress)
                            alpha = 0.88f + 0.12f * predictiveBackProgress
                        },
                    state = uiState,
                    viewModel = viewModel,
                    previewVisible = previewVisible,
                    onClosePreview = { previewVisible = false }
                )
                BluetoothContent(
                    modifier = Modifier
                        .padding(padding)
                        .graphicsLayer {
                            translationX = size.width * PredictiveBackTravelFraction * predictiveBackProgress
                            alpha = 1f - 0.18f * predictiveBackProgress
                        },
                    state = uiState.bluetooth,
                    permissionsGranted = bluetoothPermissionsGranted,
                    permissions = permissions,
                    permissionLauncher = permissionLauncher,
                    viewModel = viewModel,
                    navigateBackToController = navigateBackToController
                )
            }
        } else {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { pageTransitionSpec() },
                label = "PageTransition"
            ) { page ->
                when (page) {
                    AppPage.Controller -> ControllerContent(
                        modifier = Modifier.padding(padding),
                        state = uiState,
                        viewModel = viewModel,
                        previewVisible = previewVisible,
                        onClosePreview = { previewVisible = false }
                    )

                    AppPage.Bluetooth -> BluetoothContent(
                        modifier = Modifier.padding(padding),
                        state = uiState.bluetooth,
                        permissionsGranted = bluetoothPermissionsGranted,
                        permissions = permissions,
                        permissionLauncher = permissionLauncher,
                        viewModel = viewModel,
                        navigateBackToController = navigateBackToController
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerContent(
    modifier: Modifier,
    state: MainUiState,
    viewModel: MainViewModel,
    previewVisible: Boolean,
    onClosePreview: () -> Unit
) {
    ControllerPage(
        modifier = modifier,
        state = state,
        previewVisible = previewVisible,
        onClosePreview = onClosePreview,
        onMode = { viewModel.updateMode(it) },
        onColor = { r, g, b -> viewModel.updateColor(r, g, b) },
        onBrightness = { viewModel.updateBrightness(it) },
        onActivePeriod = { viewModel.updateActivePeriod(it) },
        onToggleLed = { viewModel.toggleOrderLed(it) },
        onOrder = { viewModel.setOrder(it) },
        onToggleFlowFrameLed = { frameIndex, led -> viewModel.toggleFlowFrameLed(frameIndex, led) },
        onFlowFrames = { viewModel.setFlowFrames(it) },
        onGenerateFlowFramesFromOrder = { viewModel.generateFlowFramesFromOrder() },
        onAddFlowFrame = { viewModel.addFlowFrame() },
        onDeleteFlowFrame = { viewModel.deleteFlowFrame(it) },
        onMoveFlowFrameUp = { viewModel.moveFlowFrameUp(it) },
        onMoveFlowFrameDown = { viewModel.moveFlowFrameDown(it) },
        onSend = { viewModel.sendCurrent() },
        onManualHex = { viewModel.updateManualHex(it) },
        onLoadCurrentFrame = { viewModel.loadCurrentFrameToManualHex() },
        onSendManual = { viewModel.sendManualHex() },
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

@Composable
private fun BluetoothContent(
    modifier: Modifier,
    state: com.rgbws2812.controller.model.BluetoothUiState,
    permissionsGranted: Boolean,
    permissions: Array<String>,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    viewModel: MainViewModel,
    navigateBackToController: () -> Unit
) {
    BluetoothConnectionPage(
        modifier = modifier,
        state = state,
        permissionsGranted = permissionsGranted,
        onRefresh = {
            if (permissionsGranted) {
                viewModel.refreshBluetooth()
            } else {
                permissionLauncher.launch(permissions)
            }
        },
        onScan = {
            if (permissionsGranted) {
                viewModel.startDiscovery()
            } else {
                permissionLauncher.launch(permissions)
            }
        },
        onStopScan = { viewModel.stopDiscovery() },
        onConnect = {
            viewModel.connect(it)
            navigateBackToController()
        },
        onDisconnect = { viewModel.disconnect() },
        onRequestPermission = { permissionLauncher.launch(permissions) }
    )
}

private fun AnimatedContentTransitionScope<AppPage>.pageTransitionSpec() =
    if (targetState == AppPage.Bluetooth) {
        val enter = slideInHorizontally(animationSpec = tween(PageTransitionMillis)) { fullWidth -> fullWidth } +
            fadeIn(animationSpec = tween(PageTransitionMillis))
        val exit = slideOutHorizontally(animationSpec = tween(PageTransitionMillis)) { fullWidth -> -fullWidth / 4 } +
            fadeOut(animationSpec = tween(PageTransitionMillis))
        enter togetherWith exit
    } else {
        val enter = slideInHorizontally(animationSpec = tween(PageTransitionMillis)) { fullWidth -> -fullWidth } +
            fadeIn(animationSpec = tween(PageTransitionMillis))
        val exit = slideOutHorizontally(animationSpec = tween(PageTransitionMillis)) { fullWidth -> fullWidth / 4 } +
            fadeOut(animationSpec = tween(PageTransitionMillis))
        enter togetherWith exit
    }

@Composable
private fun ControllerPage(
    modifier: Modifier,
    state: MainUiState,
    previewVisible: Boolean,
    onClosePreview: () -> Unit,
    onMode: (ControlMode) -> Unit,
    onColor: (Int, Int, Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onActivePeriod: (Int) -> Unit,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit,
    onToggleFlowFrameLed: (Int, Int) -> Unit,
    onFlowFrames: (List<Int>) -> Unit,
    onGenerateFlowFramesFromOrder: () -> Unit,
    onAddFlowFrame: () -> Unit,
    onDeleteFlowFrame: (Int) -> Unit,
    onMoveFlowFrameUp: (Int) -> Unit,
    onMoveFlowFrameDown: (Int) -> Unit,
    onSend: () -> Unit,
    onManualHex: (String) -> Unit,
    onLoadCurrentFrame: () -> Unit,
    onSendManual: () -> Unit,
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (previewVisible && isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                LedPreviewPanel(
                    state = state.control,
                    isLandscape = true,
                    onClose = onClosePreview,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = LedPreviewPanelMaxWidth)
                        .width(LedPreviewPanelMaxWidth)
                )
                ControllerPageList(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onMode = onMode,
                    onColor = onColor,
                    onBrightness = onBrightness,
                    onActivePeriod = onActivePeriod,
                    onToggleLed = onToggleLed,
                    onOrder = onOrder,
                    onToggleFlowFrameLed = onToggleFlowFrameLed,
                    onFlowFrames = onFlowFrames,
                    onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
                    onAddFlowFrame = onAddFlowFrame,
                    onDeleteFlowFrame = onDeleteFlowFrame,
                    onMoveFlowFrameUp = onMoveFlowFrameUp,
                    onMoveFlowFrameDown = onMoveFlowFrameDown,
                    onSend = onSend,
                    onManualHex = onManualHex,
                    onLoadCurrentFrame = onLoadCurrentFrame,
                    onSendManual = onSendManual,
                    onSavePreset = onSavePreset,
                    onLoadPreset = onLoadPreset,
                    onRenamePreset = onRenamePreset,
                    onDeletePreset = onDeletePreset,
                    onResendHistory = onResendHistory,
                    onClearHistory = onClearHistory,
                    onExport = onExport,
                    onImportText = onImportText,
                    onImport = onImport
                )
            }
        } else if (previewVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                ControllerPageList(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onMode = onMode,
                    onColor = onColor,
                    onBrightness = onBrightness,
                    onActivePeriod = onActivePeriod,
                    onToggleLed = onToggleLed,
                    onOrder = onOrder,
                    onToggleFlowFrameLed = onToggleFlowFrameLed,
                    onFlowFrames = onFlowFrames,
                    onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
                    onAddFlowFrame = onAddFlowFrame,
                    onDeleteFlowFrame = onDeleteFlowFrame,
                    onMoveFlowFrameUp = onMoveFlowFrameUp,
                    onMoveFlowFrameDown = onMoveFlowFrameDown,
                    onSend = onSend,
                    onManualHex = onManualHex,
                    onLoadCurrentFrame = onLoadCurrentFrame,
                    onSendManual = onSendManual,
                    onSavePreset = onSavePreset,
                    onLoadPreset = onLoadPreset,
                    onRenamePreset = onRenamePreset,
                    onDeletePreset = onDeletePreset,
                    onResendHistory = onResendHistory,
                    onClearHistory = onClearHistory,
                    onExport = onExport,
                    onImportText = onImportText,
                    onImport = onImport
                )
                LedPreviewPanel(
                    state = state.control,
                    isLandscape = false,
                    onClose = onClosePreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = LedPreviewPanelMaxHeight)
                        .height(LedPreviewPanelMaxHeight)
                )
            }
        } else {
            ControllerPageList(
                modifier = Modifier.fillMaxSize(),
                state = state,
                onMode = onMode,
                onColor = onColor,
                onBrightness = onBrightness,
                onActivePeriod = onActivePeriod,
                onToggleLed = onToggleLed,
                onOrder = onOrder,
                onToggleFlowFrameLed = onToggleFlowFrameLed,
                onFlowFrames = onFlowFrames,
                onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
                onAddFlowFrame = onAddFlowFrame,
                onDeleteFlowFrame = onDeleteFlowFrame,
                onMoveFlowFrameUp = onMoveFlowFrameUp,
                onMoveFlowFrameDown = onMoveFlowFrameDown,
                onSend = onSend,
                onManualHex = onManualHex,
                onLoadCurrentFrame = onLoadCurrentFrame,
                onSendManual = onSendManual,
                onSavePreset = onSavePreset,
                onLoadPreset = onLoadPreset,
                onRenamePreset = onRenamePreset,
                onDeletePreset = onDeletePreset,
                onResendHistory = onResendHistory,
                onClearHistory = onClearHistory,
                onExport = onExport,
                onImportText = onImportText,
                onImport = onImport
            )
        }
    }
}

@Composable
private fun ControllerPageList(
    modifier: Modifier,
    state: MainUiState,
    onMode: (ControlMode) -> Unit,
    onColor: (Int, Int, Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onActivePeriod: (Int) -> Unit,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit,
    onToggleFlowFrameLed: (Int, Int) -> Unit,
    onFlowFrames: (List<Int>) -> Unit,
    onGenerateFlowFramesFromOrder: () -> Unit,
    onAddFlowFrame: () -> Unit,
    onDeleteFlowFrame: (Int) -> Unit,
    onMoveFlowFrameUp: (Int) -> Unit,
    onMoveFlowFrameDown: (Int) -> Unit,
    onSend: () -> Unit,
    onManualHex: (String) -> Unit,
    onLoadCurrentFrame: () -> Unit,
    onSendManual: () -> Unit,
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
        item {
            ControlSection(
                state = state.control,
                onMode = onMode,
                onColor = onColor,
                onBrightness = onBrightness,
                onActivePeriod = onActivePeriod,
                onToggleLed = onToggleLed,
                onOrder = onOrder,
                onToggleFlowFrameLed = onToggleFlowFrameLed,
                onFlowFrames = onFlowFrames,
                onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
                onAddFlowFrame = onAddFlowFrame,
                onDeleteFlowFrame = onDeleteFlowFrame,
                onMoveFlowFrameUp = onMoveFlowFrameUp,
                onMoveFlowFrameDown = onMoveFlowFrameDown
            )
        }
        if (state.showAdvancedSendPanel) {
            item {
                FrameSection(
                    state = state,
                    onSend = onSend,
                    onManualHex = onManualHex,
                    onLoadCurrentFrame = onLoadCurrentFrame,
                    onSendManual = onSendManual
                )
            }
        }
        item {
            WorkbenchSection(
                state = state,
                onSavePreset = onSavePreset,
                onLoadPreset = onLoadPreset,
                onRenamePreset = onRenamePreset,
                onDeletePreset = onDeletePreset,
                onResendHistory = onResendHistory,
                onClearHistory = onClearHistory,
                onExport = onExport,
                onImportText = onImportText,
                onImport = onImport
            )
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BluetoothConnectionPage(
    modifier: Modifier,
    state: com.rgbws2812.controller.model.BluetoothUiState,
    permissionsGranted: Boolean,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DeviceInfo) -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (permissionsGranted) {
            onRefresh()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
        item {
            BluetoothStatusPanel(
                state = state,
                permissionsGranted = permissionsGranted,
                onRequestPermission = onRequestPermission,
                onDisconnect = onDisconnect
            )
        }
        item {
            AppSection(title = "选择设备") {
                TabRow(selectedTabIndex = selectedTab) {
                    BluetoothTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when (selectedTab) {
                    0 -> ScanDevicesTab(
                        state = state,
                        permissionsGranted = permissionsGranted,
                        onScan = onScan,
                        onStopScan = onStopScan,
                        onConnect = onConnect
                    )

                    1 -> PairedDevicesTab(
                        state = state,
                        permissionsGranted = permissionsGranted,
                        onRefresh = onRefresh,
                        onConnect = onConnect
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ControllerTopBar(
    connectionState: BluetoothConnectionState,
    flowFramesValid: Boolean,
    autoSendEnabled: Boolean,
    showAdvancedSendPanel: Boolean,
    previewVisible: Boolean,
    onSendClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onToggleAutoSend: (Boolean) -> Unit,
    onToggleAdvancedSendPanel: (Boolean) -> Unit,
    onResetAllParameters: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val sendEnabled = flowFramesValid && connectionState == BluetoothConnectionState.Connected

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("还原全部参数") },
            text = { Text("确认后会将模式、颜色、亮度、流水间隔、呼吸周期、渐变周期、流水顺序和高级画面还原为默认值。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetAllParameters()
                    }
                ) {
                    Text("确认还原")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    TopAppBar(
        title = { Text("RGB 彩灯控制") },
        actions = {
            IconButton(onClick = onSendClick, enabled = sendEnabled) {
                SendPlaneIcon(
                    modifier = Modifier.size(21.dp),
                    color = if (sendEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            IconButton(onClick = onPreviewClick, enabled = !previewVisible) {
                PlayPreviewIcon(
                    modifier = Modifier.size(22.dp),
                    color = if (previewVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onBluetoothClick) {
                Icon(
                    painter = painterResource(
                        id = if (connectionState == BluetoothConnectionState.Connected) {
                            R.drawable.ic_bluetooth_connected
                        } else {
                            R.drawable.ic_bluetooth_disabled
                        }
                    ),
                    contentDescription = if (connectionState == BluetoothConnectionState.Connected) "蓝牙已连接" else "连接蓝牙设备",
                    tint = if (connectionState == BluetoothConnectionState.Connected) {
                        connectedStatusColor()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu),
                        contentDescription = "打开菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    ControllerMenuContent(
                        autoSendEnabled = autoSendEnabled,
                        showAdvancedSendPanel = showAdvancedSendPanel,
                        onToggleAutoSend = {
                            onToggleAutoSend(!autoSendEnabled)
                            menuExpanded = false
                        },
                        onToggleAdvancedSendPanel = {
                            onToggleAdvancedSendPanel(!showAdvancedSendPanel)
                            menuExpanded = false
                        },
                        onResetAllParameters = {
                            menuExpanded = false
                            showResetConfirm = true
                        }
                    )
                } 
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun ControllerMenuContent(
    autoSendEnabled: Boolean,
    showAdvancedSendPanel: Boolean,
    onToggleAutoSend: () -> Unit,
    onToggleAdvancedSendPanel: () -> Unit,
    onResetAllParameters: () -> Unit
) {
    CheckableMenuItem(
        text = "自动发送",
        checked = autoSendEnabled,
        supportingText = "参数变化后短延迟发送",
        onClick = onToggleAutoSend
    )
    CheckableMenuItem(
        text = "显示高级发送面板",
        checked = showAdvancedSendPanel,
        supportingText = "显示当前帧、复制和手动 Hex",
        onClick = onToggleAdvancedSendPanel
    )
    Divider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
    DropdownMenuItem(
        text = { Text("还原全部参数") },
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.error,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        onClick = onResetAllParameters
    )
}

@Composable
private fun CheckableMenuItem(
    text: String,
    checked: Boolean,
    supportingText: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text, fontWeight = FontWeight.Medium)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = null
            )
        },
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            trailingIconColor = MaterialTheme.colorScheme.primary,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
        onClick = onClick
    )
}

@Composable
private fun BluetoothTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("连接蓝牙设备") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "返回"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun LedPreviewPanel(
    state: RgbControlState,
    isLandscape: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanFrames = RgbControlState.sanitizeFlowFrames(state.flowFrames)
    val animationDurationMillis = when (state.mode) {
        ControlMode.Breath -> (state.breathPeriod.coerceIn(1, 255) * 20).coerceAtLeast(40)
        ControlMode.Flow -> (cleanFrames.size * state.flowInterval.coerceIn(1, 255) * 10).coerceAtLeast(10)
        ControlMode.Disco -> 8 * LedPreviewDiscoStepMillis
        ControlMode.Gradient,
        ControlMode.FlowGradient -> (state.gradientPeriod.coerceIn(1, 255) * 50).coerceAtLeast(50)
        else -> LedPreviewDiscoStepMillis
    }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(state.mode, state.flowInterval, state.breathPeriod, state.gradientPeriod, cleanFrames) {
        val startMillis = withFrameMillis { it }
        while (true) {
            val frameMillis = withFrameMillis { it }
            val elapsed = (frameMillis - startMillis).coerceAtLeast(0L)
            progress = (elapsed % animationDurationMillis).toFloat() / animationDurationMillis
        }
    }

    val frameIndex = ((progress * cleanFrames.size).toInt()).coerceIn(0, cleanFrames.lastIndex)
    val discoPhase = ((progress * 8f).toInt()).coerceIn(0, 7)
    val gradientPhase = ((progress * GradientPattern.PhaseCount).toInt()).coerceIn(0, GradientPattern.PhaseCount - 1)
    val activeMask = when (state.mode) {
        ControlMode.Flow -> cleanFrames[frameIndex]
        else -> 0xFF
    }
    val brightnessPhase = when (state.mode) {
        ControlMode.Breath -> {
            if (progress < 0.5f) {
                progress * 2f
            } else {
                (1f - progress) * 2f
            }
        }
        else -> 1f
    }
    val displayMaxBrightness = (state.brightness.coerceIn(0, 255) / 255f).pow(LedPreviewGamma)
    val brightnessFactor = brightnessPhase * displayMaxBrightness
    val baseColor = Color(state.red.coerceIn(0, 255), state.green.coerceIn(0, 255), state.blue.coerceIn(0, 255))
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (isLandscape) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = dividerColor,
                    start = Offset(size.width - 1.dp.toPx() / 2f, 0f),
                    end = Offset(size.width - 1.dp.toPx() / 2f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LedPreviewGrid(
                    activeMask = activeMask,
                    baseColor = baseColor,
                    discoPhase = discoPhase,
                    gradientPhase = gradientPhase,
                    mode = state.mode,
                    brightnessFactor = brightnessFactor,
                    modifier = Modifier.size(LedPreviewGridWidth, LedPreviewGridHeight)
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = onClose) {
                    ClosePreviewIcon(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 1.dp.toPx() / 2f),
                    end = Offset(size.width, 1.dp.toPx() / 2f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                LedPreviewGrid(
                    activeMask = activeMask,
                    baseColor = baseColor,
                    discoPhase = discoPhase,
                    gradientPhase = gradientPhase,
                    mode = state.mode,
                    brightnessFactor = brightnessFactor,
                    modifier = Modifier.size(LedPreviewGridWidth, LedPreviewGridHeight)
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onClose) {
                    ClosePreviewIcon(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LedPreviewGrid(
    activeMask: Int,
    baseColor: Color,
    discoPhase: Int,
    gradientPhase: Int,
    mode: ControlMode,
    brightnessFactor: Float,
    modifier: Modifier = Modifier
) {
    val offColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val displayBrightness = brightnessFactor.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val columns = 4
        val diameter = LedPreviewDiameter.toPx()
        val radius = diameter / 2f
        val columnGap = LedPreviewColumnGap.toPx()
        val rowGap = LedPreviewRowGap.toPx()
        val gridWidth = diameter * 4f + columnGap * 3f
        val gridHeight = diameter * 2f + rowGap
        val startX = (size.width - gridWidth) / 2f + radius
        val startY = (size.height - gridHeight) / 2f + radius

        DisplayToHardwareOrder.forEachIndexed { index, led ->
            val row = index / columns
            val column = index % columns
            val center = Offset(
                x = startX + column * (diameter + columnGap),
                y = startY + row * (diameter + rowGap)
            )
            val selected = (activeMask and (1 shl led)) != 0
            val ledColor = previewLedColor(mode = mode, baseColor = baseColor, led = led, discoPhase = discoPhase, gradientPhase = gradientPhase)
            if (selected) {
                drawCircle(
                    color = ledColor.copy(alpha = (0.14f + 0.32f * displayBrightness).coerceIn(0f, 0.6f)),
                    radius = radius * 1.55f,
                    center = center
                )
                drawCircle(
                    color = ledColor.copy(alpha = displayBrightness),
                    radius = radius,
                    center = center
                )
            } else {
                drawCircle(
                    color = offColor,
                    radius = radius,
                    center = center
                )
            }
            drawCircle(
                color = outlineColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

private fun previewLedColor(
    mode: ControlMode,
    baseColor: Color,
    led: Int,
    discoPhase: Int,
    gradientPhase: Int
): Color =
    when (mode) {
        ControlMode.Disco -> gradientPreviewColor(GradientPattern.discoColor(discoPhase + led))
        ControlMode.Gradient -> gradientPreviewColor(GradientPattern.gradientColor(gradientPhase))
        ControlMode.FlowGradient -> gradientPreviewColor(GradientPattern.flowGradientColor(gradientPhase, led))
        else -> baseColor
    }

private fun gradientPreviewColor(color: RgbColor): Color = Color(color.red, color.green, color.blue)

@Composable
private fun BluetoothStatusPanel(
    state: com.rgbws2812.controller.model.BluetoothUiState,
    permissionsGranted: Boolean,
    onRequestPermission: () -> Unit,
    onDisconnect: () -> Unit
) {
    AppSection(title = "连接状态") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (state.connectionState) {
                            BluetoothConnectionState.Connected -> connectedStatusColor()
                            BluetoothConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
                            BluetoothConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
                        }
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (state.connectionState) {
                        BluetoothConnectionState.Connected -> state.connectedDevice?.displayName ?: "已连接"
                        BluetoothConnectionState.Connecting -> "正在连接"
                        BluetoothConnectionState.Disconnected -> "未连接"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                if (state.connectedDevice != null) {
                    Text(state.connectedDevice.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.connectionState == BluetoothConnectionState.Connected) {
                OutlinedButton(onClick = onDisconnect) { Text("断开") }
            } else if (!permissionsGranted) {
                Button(onClick = onRequestPermission) { Text("授权蓝牙") }
            }
        }
    }
}

@Composable
private fun ScanDevicesTab(
    state: com.rgbws2812.controller.model.BluetoothUiState,
    permissionsGranted: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DeviceInfo) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = if (state.isScanning) onStopScan else onScan) {
            Text(
                when {
                    state.isScanning -> "停止扫描"
                    permissionsGranted -> "开始扫描"
                    else -> "授权并扫描"
                }
            )
        }
    }
    if (state.isScanning) {
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(modifier = Modifier.height(12.dp))
    DeviceList(
        emptyText = when {
            !permissionsGranted -> "需要蓝牙权限后才能扫描"
            state.isScanning -> "正在搜索附近设备"
            else -> "暂无扫描结果"
        },
        devices = state.discoveredDevices,
        connectedAddress = state.connectedDevice?.address,
        onConnect = onConnect
    )
}

@Composable
private fun PairedDevicesTab(
    state: com.rgbws2812.controller.model.BluetoothUiState,
    permissionsGranted: Boolean,
    onRefresh: () -> Unit,
    onConnect: (DeviceInfo) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onRefresh) { Text(if (permissionsGranted) "刷新已配对" else "授权并刷新") }
    }
    Spacer(modifier = Modifier.height(12.dp))
    DeviceList(
        emptyText = if (permissionsGranted) "暂无已配对设备" else "需要蓝牙权限后才能读取已配对设备",
        devices = state.pairedDevices,
        connectedAddress = state.connectedDevice?.address,
        onConnect = onConnect
    )
}

@Composable
private fun DeviceList(
    emptyText: String,
    devices: List<DeviceInfo>,
    connectedAddress: String?,
    onConnect: (DeviceInfo) -> Unit
) {
    if (devices.isEmpty()) {
        Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val visibleDevices = remember(devices) { devices.take(MaxInlineDevices) }
    Column {
        visibleDevices.forEachIndexed { index, device ->
            val connected = connectedAddress == device.address
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onConnect(device) }) {
                    Text(if (connected) "重连" else "连接")
                }
            }
            if (index < visibleDevices.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            }
        }
        if (devices.size > MaxInlineDevices) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "还有 ${devices.size - MaxInlineDevices} 个设备未展开，继续扫描或刷新后可按名称识别",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ControlSection(
    state: RgbControlState,
    onMode: (ControlMode) -> Unit,
    onColor: (Int, Int, Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onActivePeriod: (Int) -> Unit,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit,
    onToggleFlowFrameLed: (Int, Int) -> Unit,
    onFlowFrames: (List<Int>) -> Unit,
    onGenerateFlowFramesFromOrder: () -> Unit,
    onAddFlowFrame: () -> Unit,
    onDeleteFlowFrame: (Int) -> Unit,
    onMoveFlowFrameUp: (Int) -> Unit,
    onMoveFlowFrameDown: (Int) -> Unit
) {
    AppSection(title = "控制参数") {
        SectionSubheading("模式")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryControlModes.forEach { mode ->
                FilterChip(
                    selected = selectedPrimaryMode(state.mode) == mode,
                    onClick = {
                        onMode(
                            if (mode == ControlMode.Gradient) {
                                if (state.mode.isGradientFamily) state.mode else ControlMode.Gradient
                            } else {
                                mode
                            }
                        )
                    },
                    label = { Text(mode.title) }
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (state.mode.isGradientFamily) {
            SectionSubheading("渐变细分")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ControlMode.Gradient to "普通渐变",
                    ControlMode.FlowGradient to "流动渐变"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { onMode(mode) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        if (state.mode != ControlMode.Disco && !state.mode.isGradientFamily) {
            ColorControls(state = state, onColor = onColor)
            Spacer(modifier = Modifier.height(12.dp))
        }
        NumberSlider(
            label = "亮度",
            value = state.brightness,
            range = 0..255,
            onValue = onBrightness
        )
        if (state.mode == ControlMode.Flow) {
            NumberSlider(
                label = "流水间隔 x10ms",
                value = state.flowInterval,
                range = 1..255,
                valueHint = durationSecondsText(state.flowInterval.coerceIn(1, 255) * 10),
                onValue = onActivePeriod
            )
        }
        if (state.mode == ControlMode.Breath) {
            NumberSlider(
                label = "呼吸周期 x20ms",
                value = state.breathPeriod,
                range = 1..255,
                valueHint = durationSecondsText(state.breathPeriod.coerceIn(1, 255) * 20),
                onValue = onActivePeriod
            )
        }
        if (state.mode.isGradientFamily) {
            NumberSlider(
                label = "渐变周期 x50ms",
                value = state.gradientPeriod,
                range = 1..255,
                valueHint = durationSecondsText(state.gradientPeriod.coerceIn(1, 255) * 50),
                onValue = onActivePeriod
            )
        }
        if (state.mode == ControlMode.Flow) {
            FlowOrderEditor(
                order = state.order,
                flowFrames = state.flowFrames,
                onToggleLed = onToggleLed,
                onOrder = onOrder,
                onToggleFlowFrameLed = onToggleFlowFrameLed,
                onFlowFrames = onFlowFrames,
                onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
                onAddFlowFrame = onAddFlowFrame,
                onDeleteFlowFrame = onDeleteFlowFrame,
                onMoveFlowFrameUp = onMoveFlowFrameUp,
                onMoveFlowFrameDown = onMoveFlowFrameDown
            )
        }
    }
}

private fun selectedPrimaryMode(mode: ControlMode): ControlMode =
    if (mode.isGradientFamily) ControlMode.Gradient else mode

private fun durationSecondsText(durationMillis: Int): String {
    val seconds = durationMillis / 1000f
    val roundedToInt = seconds.toInt()
    return if (seconds == roundedToInt.toFloat()) {
        "$roundedToInt 秒"
    } else {
        val scaled = (seconds * 100).roundToInt()
        val integerPart = scaled / 100
        val fractionalPart = scaled % 100
        if (fractionalPart % 10 == 0) {
            "$integerPart.${fractionalPart / 10} 秒"
        } else {
            "$integerPart.${fractionalPart.toString().padStart(2, '0')} 秒"
        }
    }
}

@Composable
private fun ColorControls(
    state: RgbControlState,
    onColor: (Int, Int, Int) -> Unit
) {
    var advancedExpanded by remember { mutableStateOf(false) }
    var customSelected by remember { mutableStateOf(!isBasicPaletteColor(state.red, state.green, state.blue)) }
    var customPanelExpanded by remember { mutableStateOf(false) }
    var rememberedCustomColor by remember { mutableStateOf(Triple(state.red, state.green, state.blue).takeUnless { isBasicPaletteColor(it.first, it.second, it.third) } ?: Triple(255, 160, 64)) }
    val updateCustomColor: (Int, Int, Int) -> Unit = { red, green, blue ->
        rememberedCustomColor = Triple(red, green, blue)
        onColor(red, green, blue)
    }
    val hsv = rgbToHsv(state.red, state.green, state.blue)
    val displayHue = if (hsv.saturation == 0f && hsv.value == 0f) 0f else hsv.hue
    val hueColor = colorForHue(displayHue)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionSubheading("颜色")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            BasicColors.forEach { color ->
                BasicPaletteItem(
                    color = color,
                    selected = !customSelected && state.red == color.red && state.green == color.green && state.blue == color.blue,
                    onClick = {
                        customSelected = false
                        customPanelExpanded = false
                        advancedExpanded = false
                        onColor(color.red, color.green, color.blue)
                    }
                )
            }
            CustomPaletteItem(
                selected = customSelected,
                color = Color(rememberedCustomColor.first, rememberedCustomColor.second, rememberedCustomColor.third),
                checkColor = checkMarkColor(rememberedCustomColor.first, rememberedCustomColor.second, rememberedCustomColor.third),
                onClick = {
                    customSelected = true
                    customPanelExpanded = true
                    onColor(rememberedCustomColor.first, rememberedCustomColor.second, rememberedCustomColor.third)
                }
            )
        }

        if (customSelected && customPanelExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(state.red, state.green, state.blue))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.rgbHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "RGB ${state.red}, ${state.green}, ${state.blue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    customPanelExpanded = false
                    advancedExpanded = false
                }) {
                    EyeOffIcon(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("隐藏调色板")
                }
            }
            SaturationValuePicker(
                hue = displayHue,
                saturation = hsv.saturation,
                value = hsv.value,
                onColor = { saturation, value ->
                    val rgb = hsvToRgb(displayHue, saturation, value)
                    updateCustomColor(rgb.first, rgb.second, rgb.third)
                }
            )
            ColorGradientSlider(
                label = "色相",
                value = displayHue,
                valueRange = 0f..360f,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                ),
                valueText = "${displayHue.roundToInt()}°",
                onValue = { hue ->
                    val rgb = hsvToRgb(hue, hsv.saturation.takeIf { it > 0f } ?: 1f, hsv.value.takeIf { it > 0f } ?: 1f)
                    updateCustomColor(rgb.first, rgb.second, rgb.third)
                }
            )
            ColorGradientSlider(
                label = "明度",
                value = hsv.value * 100f,
                valueRange = 0f..100f,
                brush = Brush.horizontalGradient(listOf(Color.Black, hueColor)),
                valueText = "${(hsv.value * 100f).roundToInt()}%",
                onValue = { value ->
                    val rgb = hsvToRgb(displayHue, hsv.saturation.takeIf { it > 0f } ?: 1f, value / 100f)
                    updateCustomColor(rgb.first, rgb.second, rgb.third)
                }
            )

            TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                ExpandCollapseIcon(expanded = advancedExpanded, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (advancedExpanded) "收起 RGB 精调" else "展开 RGB 精调")
            }
            if (advancedExpanded) {
                CompactRgbSlider("R", state.red) { updateCustomColor(it, state.green, state.blue) }
                CompactRgbSlider("G", state.green) { updateCustomColor(state.red, it, state.blue) }
                CompactRgbSlider("B", state.blue) { updateCustomColor(state.red, state.green, it) }
            }
            DashedDivider()
        }
    }
}

@Composable
private fun EyeOffIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val eyeWidth = size.width * 0.82f
        val eyeHeight = size.height * 0.42f
        val left = center.x - eyeWidth / 2f
        val top = center.y - eyeHeight / 2f
        val bottom = center.y + eyeHeight / 2f

        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(left, bottom - eyeHeight),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight),
            style = Stroke(width = stroke)
        )
        drawCircle(color, radius = size.minDimension * 0.11f, center = center)
        drawLine(
            color = color,
            start = Offset(size.width * 0.14f, size.height * 0.86f),
            end = Offset(size.width * 0.86f, size.height * 0.14f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun ExpandCollapseIcon(expanded: Boolean, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val left = size.width * 0.28f
        val right = size.width * 0.72f
        val top = size.height * 0.38f
        val bottom = size.height * 0.62f
        val middle = size.width * 0.5f

        if (expanded) {
            drawLine(color, Offset(left, bottom), Offset(middle, top), strokeWidth = stroke)
            drawLine(color, Offset(middle, top), Offset(right, bottom), strokeWidth = stroke)
        } else {
            drawLine(color, Offset(left, top), Offset(middle, bottom), strokeWidth = stroke)
            drawLine(color, Offset(middle, bottom), Offset(right, top), strokeWidth = stroke)
        }
    }
}

@Composable
private fun AddFrameIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.2f),
            end = Offset(size.width * 0.5f, size.height * 0.8f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.5f),
            end = Offset(size.width * 0.8f, size.height * 0.5f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun PlayPreviewIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.2f)
            lineTo(size.width * 0.78f, size.height * 0.5f)
            lineTo(size.width * 0.32f, size.height * 0.8f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun SendPlaneIcon(modifier: Modifier = Modifier, color: Color) {
    Icon(
        imageVector = Icons.Rounded.Send,
        contentDescription = "发送当前帧",
        modifier = modifier,
        tint = color
    )
}

@Composable
private fun ClosePreviewIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.25f, size.height * 0.25f),
            end = Offset(size.width * 0.75f, size.height * 0.75f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.75f, size.height * 0.25f),
            end = Offset(size.width * 0.25f, size.height * 0.75f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun MoveFrameIcon(up: Boolean, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val top = size.height * 0.22f
        val middle = size.height * 0.48f
        val bottom = size.height * 0.78f
        val left = size.width * 0.28f
        val centerX = size.width * 0.5f
        val right = size.width * 0.72f

        if (up) {
            drawLine(color, Offset(centerX, top), Offset(centerX, bottom), strokeWidth = stroke)
            drawLine(color, Offset(left, middle), Offset(centerX, top), strokeWidth = stroke)
            drawLine(color, Offset(centerX, top), Offset(right, middle), strokeWidth = stroke)
        } else {
            drawLine(color, Offset(centerX, top), Offset(centerX, bottom), strokeWidth = stroke)
            drawLine(color, Offset(left, middle), Offset(centerX, bottom), strokeWidth = stroke)
            drawLine(color, Offset(centerX, bottom), Offset(right, middle), strokeWidth = stroke)
        }
    }
}

@Composable
private fun DeleteFrameIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val handleLeft = size.width * 0.4f
        val handleTop = size.height * 0.16f
        val handleWidth = size.width * 0.2f
        val handleHeight = size.height * 0.1f
        val lidY = size.height * 0.3f
        val lidLeft = size.width * 0.22f
        val lidRight = size.width * 0.78f
        val bodyLeft = size.width * 0.3f
        val bodyTop = size.height * 0.36f
        val bodyWidth = size.width * 0.4f
        val bodyHeight = size.height * 0.46f

        drawRoundRect(
            color = color,
            topLeft = Offset(handleLeft, handleTop),
            size = androidx.compose.ui.geometry.Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(color, Offset(lidLeft, lidY), Offset(lidRight, lidY), strokeWidth = stroke)
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.43f, size.height * 0.44f),
            end = Offset(size.width * 0.43f, size.height * 0.74f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.57f, size.height * 0.44f),
            end = Offset(size.width * 0.57f, size.height * 0.74f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun DashedDivider() {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val dash = 7.dp.toPx()
        val gap = 5.dp.toPx()
        val y = center.y
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, y),
                end = Offset((x + dash).coerceAtMost(size.width), y),
                strokeWidth = 1.dp.toPx()
            )
            x += dash + gap
        }
    }
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColor: (Float, Float) -> Unit
) {
    val baseColor = colorForHue(hue)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(hue) {
                fun updateColor(offset: Offset) {
                    val newSaturation = (offset.x / size.width).coerceIn(0f, 1f)
                    val newValue = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onColor(newSaturation, newValue)
                }
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    updateColor(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        if (change.pressed) {
                            updateColor(change.position)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, baseColor)),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        val handle = Offset(saturation.coerceIn(0f, 1f) * size.width, (1f - value.coerceIn(0f, 1f)) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = handle, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.65f), radius = 11.dp.toPx(), center = handle, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun ColorGradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    valueText: String,
    onValue: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(valueText, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GradientTrackSlider(
            value = value,
            valueRange = valueRange,
            brush = brush,
            onValue = onValue
        )
    }
}

@Composable
private fun GradientTrackSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onValue: (Float) -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(valueRange) {
                fun updateValue(offset: Offset) {
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onValue(valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction)
                }
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    updateValue(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        if (change.pressed) {
                            updateValue(change.position)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val trackHeight = 8.dp.toPx()
        val trackTop = center.y - trackHeight / 2f
        val trackCorner = trackHeight / 2f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(0f, trackTop),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackCorner, trackCorner)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(0f, trackTop),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackCorner, trackCorner),
            style = Stroke(width = 1.dp.toPx())
        )

        val fraction = ((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val x = fraction * size.width
        drawCircle(surfaceColor, radius = 10.dp.toPx(), center = Offset(x, center.y))
        drawCircle(outlineColor, radius = 10.dp.toPx(), center = Offset(x, center.y), style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun BasicPaletteItem(
    color: PaletteColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    val checkColor = checkMarkColor(color.red, color.green, color.blue)
    PaletteOption(
        label = color.label,
        selected = selected,
        onClick = onClick
    ) {
        drawCircle(Color(color.red, color.green, color.blue), radius = size.minDimension / 2f)
        if (selected) {
            drawCheckMark(checkColor)
        }
    }
}

@Composable
private fun CustomPaletteItem(
    selected: Boolean,
    color: Color,
    checkColor: Color,
    onClick: () -> Unit
) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.background
    PaletteOption(
        label = "自定义",
        selected = selected,
        onClick = onClick
    ) {
        val radius = size.minDimension / 2f - 2.dp.toPx()
        drawCircle(if (selected) color else backgroundColor, radius = radius)
        drawCircle(iconColor, radius = radius, style = Stroke(width = 2.dp.toPx()))
        if (selected) {
            drawCheckMark(checkColor)
        } else {
            val line = 8.dp.toPx()
            drawLine(
                color = iconColor,
                start = Offset(center.x - line, center.y),
                end = Offset(center.x + line, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = iconColor,
                start = Offset(center.x, center.y - line),
                end = Offset(center.x, center.y + line),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckMark(color: Color) {
    val stroke = 3.dp.toPx()
    drawLine(
        color = color,
        start = Offset(size.width * 0.30f, size.height * 0.52f),
        end = Offset(size.width * 0.44f, size.height * 0.66f),
        strokeWidth = stroke
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.44f, size.height * 0.66f),
        end = Offset(size.width * 0.72f, size.height * 0.34f),
        strokeWidth = stroke
    )
}

@Composable
private fun PaletteOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Column(
        modifier = Modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            content()
            drawCircle(
                color = borderColor,
                radius = size.minDimension / 2f - 1.5.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactRgbSlider(
    label: String,
    value: Int,
    onValue: (Int) -> Unit
) {
    InlineNumberSlider(
        label = label,
        value = value,
        range = 0..255,
        labelWidth = 18.dp,
        onValue = onValue
    )
}

@Composable
private fun InlineNumberSlider(
    label: String,
    value: Int,
    range: IntRange,
    labelWidth: androidx.compose.ui.unit.Dp = 112.dp,
    onValue: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f)
        )
        UnderlineNumberField(
            value = value,
            range = range,
            onValue = { onValue(it.coerceIn(range.first, range.last)) }
        )
    }
}

@Composable
private fun UnderlineNumberField(
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val lineColor = MaterialTheme.colorScheme.outline

    BasicTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(3)
            text = filtered
            filtered.toIntOrNull()?.let { onValue(it.coerceIn(range.first, range.last)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .width(42.dp)
            .height(28.dp),
        decorationBox = { innerTextField ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    innerTextField()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor)
                )
            }
        }
    )
}

@Composable
private fun NumberSlider(
    label: String,
    value: Int,
    range: IntRange,
    valueHint: String? = null,
    onValue: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        valueHint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        InlineNumberSlider(label = label, value = value, range = range, onValue = onValue)
    }
}

@Composable
private fun FlowOrderEditor(
    order: List<Int>,
    flowFrames: List<Int>,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit,
    onToggleFlowFrameLed: (Int, Int) -> Unit,
    onFlowFrames: (List<Int>) -> Unit,
    onGenerateFlowFramesFromOrder: () -> Unit,
    onAddFlowFrame: () -> Unit,
    onDeleteFlowFrame: (Int) -> Unit,
    onMoveFlowFrameUp: (Int) -> Unit,
    onMoveFlowFrameDown: (Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Spacer(modifier = Modifier.height(12.dp))
    TabRow(selectedTabIndex = selectedTab) {
        FlowEditorTabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = { Text(title) }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    if (selectedTab == 0) {
        BasicFlowOrderEditor(
            order = order,
            onToggleLed = onToggleLed,
            onOrder = onOrder
        )
    } else {
        AdvancedFlowFrameEditor(
            flowFrames = flowFrames,
            onToggleFlowFrameLed = onToggleFlowFrameLed,
            onFlowFrames = onFlowFrames,
            onGenerateFlowFramesFromOrder = onGenerateFlowFramesFromOrder,
            onAddFlowFrame = onAddFlowFrame,
            onDeleteFlowFrame = onDeleteFlowFrame,
            onMoveFlowFrameUp = onMoveFlowFrameUp,
            onMoveFlowFrameDown = onMoveFlowFrameDown
        )
    }
}

@Composable
private fun BasicFlowOrderEditor(
    order: List<Int>,
    onToggleLed: (Int) -> Unit,
    onOrder: (List<Int>) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = FlowEditorGridMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DisplayToHardwareOrder.chunked(4).forEach { row ->
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
                                Text(
                                    text = step?.toString().orEmpty(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowOrderPresets.forEach { (label, preset) ->
            OutlinedButton(onClick = { onOrder(preset) }) { Text(label) }
        }
        OutlinedButton(onClick = { onOrder(emptyList()) }) { Text("清空选择") }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "基础流水顺序单独保存；点击“用基础灯序生成”后才会覆盖高级画面。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!RgbFrameBuilder.isValidOrder(order)) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "基础流水灯序未点满；高级画面仍可发送，或点击“用基础灯序生成”。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedFlowFrameEditor(
    flowFrames: List<Int>,
    onToggleFlowFrameLed: (Int, Int) -> Unit,
    onFlowFrames: (List<Int>) -> Unit,
    onGenerateFlowFramesFromOrder: () -> Unit,
    onAddFlowFrame: () -> Unit,
    onDeleteFlowFrame: (Int) -> Unit,
    onMoveFlowFrameUp: (Int) -> Unit,
    onMoveFlowFrameDown: (Int) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onGenerateFlowFramesFromOrder) { Text("用基础灯序生成") }
        OutlinedButton(onClick = { onFlowFrames(List(RgbControlState.MaxFlowFrames) { 0xFF }) }) { Text("全亮") }
        OutlinedButton(onClick = { onFlowFrames(List(RgbControlState.MaxFlowFrames) { 0x00 }) }) { Text("全灭") }
    }
    Spacer(modifier = Modifier.height(8.dp))
    flowFrames.forEachIndexed { frameIndex, mask ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("画面 ${frameIndex + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mask.coerceIn(0, 255).toHexByte(), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        enabled = frameIndex > 0,
                        onClick = { onMoveFlowFrameUp(frameIndex) }
                    ) {
                        MoveFrameIcon(up = true, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        enabled = frameIndex < flowFrames.lastIndex,
                        onClick = { onMoveFlowFrameDown(frameIndex) }
                    ) {
                        MoveFrameIcon(up = false, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        enabled = flowFrames.size > 1,
                        onClick = { onDeleteFlowFrame(frameIndex) }
                    ) {
                        DeleteFrameIcon(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .widthIn(max = FlowEditorGridMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DisplayToHardwareOrder.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { led ->
                                val selected = (mask and (1 shl led)) != 0
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { onToggleFlowFrameLed(frameIndex, led) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text((DisplayToHardwareOrder.indexOf(led) + 1).toString(), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (frameIndex < flowFrames.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            }
        }
    }
    if (flowFrames.size < RgbControlState.MaxFlowFrames) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onAddFlowFrame) {
                AddFrameIcon(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加画面")
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "每个画面是 1 字节灯掩码，可同时点亮多颗灯；画面数量范围 1~8。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
    AppSection(title = "帧与发送") {
        SectionSubheading("当前 ${state.frame.bytes.size} 字节帧")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = frameCodeBlockColor()
        ) {
            Text(
                text = state.frame.spacedHex(),
                modifier = Modifier.padding(14.dp),
                color = frameCodeTextColor(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (!state.flowFramesValid) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "流水画面无效：画面数量必须为 1..8。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.flowFramesValid && state.bluetooth.connectionState == BluetoothConnectionState.Connected,
                onClick = onSend
            ) { Text("发送当前帧") }
            OutlinedButton(
                enabled = state.flowFramesValid,
                onClick = { clipboard.setText(AnnotatedString(state.frame.spacedHex())) }
            ) { Text("复制 Hex") }
            OutlinedButton(
                enabled = state.flowFramesValid,
                onClick = { clipboard.setText(AnnotatedString(state.frame.compactHex())) }
            ) { Text("复制紧凑") }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ByteTable(frameHex = state.frame.spacedHex())
        Spacer(modifier = Modifier.height(14.dp))
        SectionSubheading("手动 Hex")
        OutlinedTextField(
            value = state.manualHex,
            onValueChange = onManualHex,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp),
            minLines = 3,
            label = { Text("${RgbFrameBuilder.MinFrameLength}..${RgbFrameBuilder.MaxFrameLength} 字节 Hex") }
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
    val values = remember(frameHex) { frameHex.split(" ") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(index.toString().padStart(2, '0'), modifier = Modifier.width(32.dp), fontFamily = FontFamily.Monospace)
                Text(RgbFrameBuilder.byteLabel(index, values.size), modifier = Modifier.width(58.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    AppSection(title = "工作台") {
        TabRow(selectedTabIndex = selectedTab) {
            WorkbenchTabs.forEachIndexed { index, title ->
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
    val visiblePresets = remember(presets) { presets.take(MaxInlinePresets) }
    visiblePresets.forEach { preset ->
        PresetRow(
            preset = preset,
            onLoad = { onLoadPreset(preset) },
            onRename = { onRenamePreset(preset, it) },
            onDelete = { onDeletePreset(preset) }
        )
    }
    if (presets.size > MaxInlinePresets) {
        Text(
            "还有 ${presets.size - MaxInlinePresets} 个预设未显示，导出 JSON 可查看完整列表",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PresetRow(
    preset: Preset,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean = false
) {
    var editing by remember { mutableStateOf(false) }
    var newName by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editing) {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
        } else {
            Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
        if (showDivider) {
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
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
        Text(
            "最近 ${history.size} 条",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = onClearHistory, enabled = history.isNotEmpty()) { Text("清空") }
    }
    if (history.isEmpty()) {
        Text("暂无发送历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val visibleHistory = remember(history) { history.take(MaxInlineHistory) }
    visibleHistory.forEachIndexed { index, item ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${item.source} · ${formatTime(item.timestamp)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(item.hex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onResendHistory(item) }) { Text("重发") }
            if (index < visibleHistory.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            }
        }
    }
    if (history.size > MaxInlineHistory) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "还有 ${history.size - MaxInlineHistory} 条历史未显示，导出 JSON 可查看完整列表",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
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
private fun AppSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionHorizontalPadding)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
    }
}

@Composable
private fun SectionSubheading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasBluetoothPermissions(permissions: Array<String>): Boolean =
    permissions.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
