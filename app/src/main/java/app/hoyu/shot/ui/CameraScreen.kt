package app.hoyu.shot.ui

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.hoyu.shot.model.*
import app.hoyu.shot.ui.components.*

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    var surfaceViewRef by remember { mutableStateOf<AutoFitSurfaceView?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var isModeModalVisible by remember { mutableStateOf(false) }
    var isSettingsDialogVisible by remember { mutableStateOf(false) }
    var activeParamMenu by remember { mutableStateOf<DialParameter?>(null) }

    DisposableEffect(Unit) {
        viewModel.startSensors()
        onDispose {
            viewModel.stopSensors()
            viewModel.stopCamera()
        }
    }

    LaunchedEffect(uiState.previewSize, uiState.aspectRatio) {
        val (camWidth, camHeight) = uiState.previewSize
        if (isLandscape) {
            surfaceViewRef?.setAspectRatio(camWidth, camHeight)
        } else {
            surfaceViewRef?.setAspectRatio(camHeight, camWidth)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(uiState.currentZoomRatio) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1.0f) {
                        viewModel.setZoomRatio(uiState.currentZoomRatio * zoom)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    activeParamMenu = null
                    viewModel.onTouchFocus(offset, containerSize.width, containerSize.height)
                }
            }
    ) {
        // 1. Camera2 Viewfinder centered
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    AutoFitSurfaceView(context).apply {
                        surfaceViewRef = this
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                viewModel.startCamera(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                viewModel.stopCamera()
                            }
                        })
                    }
                },
                update = { v ->
                    val (camWidth, camHeight) = uiState.previewSize
                    if (isLandscape) {
                        v.setAspectRatio(camWidth, camHeight)
                    } else {
                        v.setAspectRatio(camHeight, camWidth)
                    }
                }
            )
        }

        // 2. Composition Grid Overlay
        GridOverlay(gridMode = uiState.gridMode)

        // 3. Focus Peaking & Zebra Stripes Overlay
        PeakingAndZebraOverlay(
            isPeakingEnabled = uiState.isFocusPeakingEnabled,
            isZebraEnabled = uiState.isZebraStripesEnabled,
            histogram = uiState.histogram
        )

        // 4. ProShot-style Electronic Horizon Level
        if (uiState.isHorizonLevelVisible) {
            HorizonLevelIndicator(
                orientation = uiState.horizon,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 5. Tap-to-Focus Reticle
        FocusRingOverlay(tapPoint = uiState.tapFocusPoint)

        // 6. Top-Center Format & Resolution Pill (e.g. 12.6MP JPEG / RAW)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            val formatStr = if (uiState.captureRaw) "12MP RAW" else "12MP JPEG"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .border(
                        1.dp,
                        if (uiState.captureRaw) Color(0xFFFFD600) else Color(0x44FFFFFF),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.toggleRaw()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatStr,
                    color = if (uiState.captureRaw) Color(0xFFFFD600) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 7. Live Histogram in top-right area (if enabled)
        if (uiState.isHistogramVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 85.dp)
            ) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // 8. LEFT SIDEBAR (In Landscape) / TOP BAR (In Portrait): Outside preview in empty space
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Controls Group
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Settings Gear Button
                SideIconButton(
                    icon = Icons.Default.Settings,
                    onClick = { isSettingsDialogVisible = true }
                )

                // AE Mode indicator / Lock
                SideTextButton(
                    text = if (uiState.isAeLocked) "AEL" else "AE",
                    isActive = uiState.isAeLocked,
                    onClick = { viewModel.toggleAeLock() }
                )

                // Aspect Ratio Selector
                SideTextButton(
                    text = uiState.aspectRatio.ratioName,
                    isActive = false,
                    onClick = {
                        val next = when (uiState.aspectRatio) {
                            AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_1_1
                            AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_3
                            else -> AspectRatio.RATIO_4_3
                        }
                        viewModel.setAspectRatio(next)
                    }
                )

                // Grid Mode Selector
                SideIconButton(
                    icon = Icons.Default.GridOn,
                    isActive = uiState.gridMode != GridMode.NONE,
                    badgeText = when (uiState.gridMode) {
                        GridMode.NONE -> null
                        GridMode.THIRDS -> "3"
                        GridMode.GOLDEN -> "Φ"
                    },
                    onClick = { viewModel.toggleGrid() }
                )
            }

            // Bottom-Left: Quick Photo Preview Thumbnail
            QuickThumbnail(
                photoInfo = uiState.lastCapturedPhoto,
                onClick = { viewModel.openReviewDialog() }
            )
        }

        // 9. RIGHT SIDEBAR: Controls in the right margin
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Lens Switcher (e.g. 0.6x, 1x, 3.2x)
            if (uiState.availableLenses.size > 1) {
                Column(
                    modifier = Modifier.align(Alignment.Bottom),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.availableLenses.filter { !it.isFront }.forEach { lens ->
                        val isSelected = uiState.currentLens?.id == lens.id
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFFFD600) else Color(0x33000000))
                                .border(1.dp, if (isSelected) Color(0xFFFFD600) else Color(0x44FFFFFF), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.selectLens(lens)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lens.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Vertical Parameter Strip (AF, SEC, ISO, WB)
            ParameterStrip(
                uiState = uiState,
                activeParam = activeParamMenu,
                onParamClick = { param ->
                    activeParamMenu = if (activeParamMenu == param) null else param
                }
            )

            // Vertical EV Slider
            VerticalEvSlider(
                evIndex = uiState.evCompensationIndex,
                onEvChanged = { viewModel.setEvCompensation(it) }
            )

            // Rightmost Column: Mode Dial, Shutter, Flip Camera, Flash
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: Exposure Mode Selector Button [ A ] / [ P ] / [ M ]
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                        .border(1.5.dp, Color(0xFFFFD600), RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isModeModalVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.exposureMode.label,
                        color = Color(0xFFFFD600),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Center: Big Shutter Trigger Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(3.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .background(if (uiState.isCapturing) Color.Gray else Color.White, CircleShape)
                        .clickable(enabled = !uiState.isCapturing) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.takePicture()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFD600),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Tools: Flip Camera & Flash Mode
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Flip Camera
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0x44FFFFFF), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleCameraFacing()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Camera",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Flash Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (uiState.flashMode != FlashMode.OFF) Color(0x33FFD600) else Color(0x33000000))
                            .border(1.dp, if (uiState.flashMode != FlashMode.OFF) Color(0xFFFFD600) else Color(0x44FFFFFF), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleFlash()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (uiState.flashMode) {
                                FlashMode.OFF -> Icons.Default.FlashOff
                                FlashMode.AUTO -> Icons.Default.FlashAuto
                                FlashMode.ON -> Icons.Default.FlashOn
                                FlashMode.TORCH -> Icons.Default.Highlight
                            },
                            contentDescription = "Flash",
                            tint = if (uiState.flashMode != FlashMode.OFF) Color(0xFFFFD600) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 10. TEMPORARY OVERLAYS (Only appear when user triggers them!)

        // Parameter 2-Column Scrollable Grid Menu
        activeParamMenu?.let { param ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 120.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                when (param) {
                    DialParameter.SHUTTER -> {
                        val shutterSpeeds = listOf(
                            125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L,
                            8_000_000L, 16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L,
                            250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L
                        )
                        ParameterGridMenu(
                            title = "Shutter Speed",
                            items = shutterSpeeds,
                            selectedItem = uiState.shutterSpeedNanos,
                            onItemSelected = { nanos ->
                                viewModel.setExposureMode(ExposureMode.SHUTTER_PRIORITY)
                                viewModel.setShutterSpeedNanos(nanos)
                                activeParamMenu = null
                            },
                            formatItem = { HardwareCapabilities.formatShutterSpeed(it) },
                            onAutoSelected = {
                                viewModel.setExposureMode(ExposureMode.AUTO)
                                activeParamMenu = null
                            },
                            isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    DialParameter.ISO -> {
                        val isoValues = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400)
                        ParameterGridMenu(
                            title = "ISO Sensitivity",
                            items = isoValues,
                            selectedItem = uiState.iso,
                            onItemSelected = { iso ->
                                viewModel.setExposureMode(ExposureMode.ISO_PRIORITY)
                                viewModel.setIso(iso)
                                activeParamMenu = null
                            },
                            formatItem = { "$it" },
                            onAutoSelected = {
                                viewModel.setExposureMode(ExposureMode.AUTO)
                                activeParamMenu = null
                            },
                            isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    DialParameter.FOCUS -> {
                        val focusDiopters = listOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 7.0f, 10.0f)
                        ParameterGridMenu(
                            title = "Focus Diopter",
                            items = focusDiopters,
                            selectedItem = if (uiState.isManualFocusEnabled) uiState.focusDistanceDiopters else null,
                            onItemSelected = { diopter ->
                                viewModel.setFocusDistance(diopter)
                                activeParamMenu = null
                            },
                            formatItem = { if (it == 0f) "∞ Macro" else String.format("%.1f D", it) },
                            onAutoSelected = {
                                viewModel.toggleAutoFocus()
                                activeParamMenu = null
                            },
                            isAutoSelected = !uiState.isManualFocusEnabled,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    DialParameter.WHITE_BALANCE -> {
                        val kelvinValues = (2800..7500 step 300).toList()
                        ParameterGridMenu(
                            title = "White Balance",
                            items = kelvinValues,
                            selectedItem = if (uiState.whiteBalancePreset == WhiteBalancePreset.CUSTOM_KELVIN) uiState.kelvinTemperature else null,
                            onItemSelected = { k ->
                                viewModel.setKelvin(k)
                                activeParamMenu = null
                            },
                            formatItem = { "${it}K" },
                            onAutoSelected = {
                                viewModel.setWhiteBalancePreset(WhiteBalancePreset.AUTO)
                                activeParamMenu = null
                            },
                            isAutoSelected = uiState.whiteBalancePreset == WhiteBalancePreset.AUTO,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    else -> {}
                }
            }
        }

        // Mode Dial Selector Modal
        if (isModeModalVisible) {
            ModeDialSelectorModal(
                currentMode = uiState.exposureMode,
                onModeSelected = { viewModel.setExposureMode(it) },
                onDismiss = { isModeModalVisible = false }
            )
        }

        // Settings Dialog
        if (isSettingsDialogVisible) {
            SettingsDialog(
                uiState = uiState,
                onToggleHistogram = { viewModel.toggleHistogram() },
                onToggleHorizon = { viewModel.toggleHorizon() },
                onTogglePeaking = { viewModel.toggleFocusPeaking() },
                onToggleZebra = { viewModel.toggleZebraStripes() },
                onDismiss = { isSettingsDialogVisible = false }
            )
        }

        // EXIF Review Fullscreen Dialog
        if (uiState.isReviewDialogVisible && uiState.lastCapturedPhoto != null) {
            ExifViewerDialog(
                photoInfo = uiState.lastCapturedPhoto!!,
                onDismiss = { viewModel.closeReviewDialog() }
            )
        }
    }
}

@Composable
private fun SideIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isActive) Color(0x33FFD600) else Color(0x33000000))
            .border(1.dp, if (isActive) Color(0xFFFFD600) else Color(0x44FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color(0xFFFFD600) else Color.White,
            modifier = Modifier.size(19.dp)
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = Color(0xFFFFD600),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun SideTextButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFFFFD600) else Color(0x33000000))
            .border(1.dp, if (isActive) Color(0xFFFFD600) else Color(0x44FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}
