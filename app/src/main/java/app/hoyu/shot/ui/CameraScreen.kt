package app.hoyu.shot.ui

import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
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

    var surfaceViewRef by remember { mutableStateOf<AutoFitSurfaceView?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var isModeModalVisible by remember { mutableStateOf(false) }
    var isSettingsDialogVisible by remember { mutableStateOf(false) }
    var activeParamMenu by remember { mutableStateOf<DialParameter?>(null) }

    // Smooth In-Place Rotation for icons and text elements (Nothing OS / ProShot style)
    val animatedRotation by animateFloatAsState(
        targetValue = uiState.uiRotationDegrees,
        animationSpec = tween(durationMillis = 250),
        label = "uiRotation"
    )

    val shape = RoundedCornerShape(2.dp) // shadcn sharp corners

    DisposableEffect(Unit) {
        viewModel.startSensors()
        onDispose {
            viewModel.stopSensors()
            viewModel.stopCamera()
        }
    }

    LaunchedEffect(uiState.previewSize, uiState.aspectRatio) {
        val (camWidth, camHeight) = uiState.previewSize
        surfaceViewRef?.setAspectRatio(camHeight, camWidth)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
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
        // 1. Camera2 Viewfinder centered (unobstructed preview)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
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
                    v.setAspectRatio(camHeight, camWidth)
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

        // 6. Top Resolution / RAW Pill (In empty top space for 4:3, or floating transparent)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            val formatStr = if (uiState.captureRaw) "RAW" else "JPEG"
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(Color(0x33000000))
                    .border(
                        1.dp,
                        if (uiState.captureRaw) Color(0xFFE11D48) else Color(0xFF27272A),
                        shape
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.toggleRaw()
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "12MP // $formatStr",
                    color = if (uiState.captureRaw) Color(0xFFFAFAFA) else Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(animatedRotation)
                )
            }
        }

        // 7. Live Histogram in top area
        if (uiState.isHistogramVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 70.dp)
            ) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // 8. LEFT TOOLBAR (In-place rotated elements)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 12.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Group Top: Settings, AE lock, Ratio, Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Settings Gear
                ShadcnIconButton(
                    icon = Icons.Default.Settings,
                    rotationDegrees = animatedRotation,
                    onClick = { isSettingsDialogVisible = true }
                )

                // AE Lock
                ShadcnTextButton(
                    text = if (uiState.isAeLocked) "AEL" else "AE",
                    isActive = uiState.isAeLocked,
                    rotationDegrees = animatedRotation,
                    onClick = { viewModel.toggleAeLock() }
                )

                // Aspect Ratio Selector
                ShadcnTextButton(
                    text = uiState.aspectRatio.ratioName,
                    isActive = false,
                    rotationDegrees = animatedRotation,
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
                ShadcnIconButton(
                    icon = Icons.Default.GridOn,
                    isActive = uiState.gridMode != GridMode.NONE,
                    rotationDegrees = animatedRotation,
                    onClick = { viewModel.toggleGrid() }
                )
            }

            // Bottom-Left: Quick Photo Preview Thumbnail
            QuickThumbnail(
                photoInfo = uiState.lastCapturedPhoto,
                onClick = { viewModel.openReviewDialog() }
            )
        }

        // 9. RIGHT TOOLBAR (In-place rotated elements)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 12.dp, top = 20.dp, bottom = 20.dp),
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
                                .size(34.dp)
                                .clip(shape)
                                .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x33000000))
                                .border(1.dp, if (isSelected) Color(0xFFFAFAFA) else Color(0xFF27272A), shape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.selectLens(lens)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lens.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.rotate(animatedRotation)
                            )
                        }
                    }
                }
            }

            // Vertical Parameter Strip (AF, SEC, ISO, WB)
            ParameterStrip(
                uiState = uiState,
                activeParam = activeParamMenu,
                rotationDegrees = animatedRotation,
                onParamClick = { param ->
                    activeParamMenu = if (activeParamMenu == param) null else param
                }
            )

            // Vertical EV Slider
            VerticalEvSlider(
                evIndex = uiState.evCompensationIndex,
                onEvChanged = { viewModel.setEvCompensation(it) }
            )

            // Rightmost Column: Mode Dial [A], Shutter, Flip Camera, Flash
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: Exposure Mode Selector (just "A", "P", "S", "ISO", "M")
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(shape)
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0xFFFAFAFA), shape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isModeModalVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.exposureMode.label,
                        color = Color(0xFFFAFAFA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.rotate(animatedRotation)
                    )
                }

                // Center: Big Shutter Trigger Button (Nothing OS style)
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .border(2.dp, Color(0xFFFAFAFA), CircleShape)
                        .padding(4.dp)
                        .background(if (uiState.isCapturing) Color(0xFF71717A) else Color(0xFFFAFAFA), CircleShape)
                        .clickable(enabled = !uiState.isCapturing) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.takePicture()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        // Subtle center red dot (Nothing camera signature)
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFE11D48), CircleShape)
                        )
                    }
                }

                // Bottom Tools: Flip Camera & Flash
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Flip Camera
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(shape)
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0xFF27272A), shape)
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
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(animatedRotation)
                        )
                    }

                    // Flash Toggle
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(shape)
                            .background(if (uiState.flashMode != FlashMode.OFF) Color(0xFF27272A) else Color(0x33000000))
                            .border(1.dp, if (uiState.flashMode != FlashMode.OFF) Color(0xFFFAFAFA) else Color(0xFF27272A), shape)
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
                            tint = if (uiState.flashMode != FlashMode.OFF) Color(0xFFFAFAFA) else Color(0xFF71717A),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(animatedRotation)
                        )
                    }
                }
            }
        }

        // 10. TEMPORARY OVERLAYS (Only appear when active)

        // Parameter 2-Column Grid Menu
        activeParamMenu?.let { param ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 110.dp),
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
                            title = "SEC // SHUTTER",
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
                            title = "ISO // SENSOR",
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
                            title = "LENS // FOCUS",
                            items = focusDiopters,
                            selectedItem = if (uiState.isManualFocusEnabled) uiState.focusDistanceDiopters else null,
                            onItemSelected = { diopter ->
                                viewModel.setFocusDistance(diopter)
                                activeParamMenu = null
                            },
                            formatItem = { if (it == 0f) "∞ MACRO" else String.format("%.1fD", it) },
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
                            title = "WB // KELVIN",
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
                isResumeEnabled = uiState.isResumeSettingsEnabled,
                onToggleResume = { viewModel.toggleResumeSettings() },
                onModeSelected = { viewModel.setExposureMode(it) },
                onDismiss = { isModeModalVisible = false }
            )
        }

        // Settings Dialog (with NR, SHARP, Resume Settings)
        if (isSettingsDialogVisible) {
            SettingsDialog(
                uiState = uiState,
                onToggleResumeSettings = { viewModel.toggleResumeSettings() },
                onCycleNoiseReduction = { viewModel.cycleNoiseReduction() },
                onCycleSharpness = { viewModel.cycleSharpness() },
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
private fun ShadcnIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    rotationDegrees: Float = 0f,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (isActive) Color(0xFFFAFAFA) else Color(0x33000000))
            .border(1.dp, if (isActive) Color(0xFFFAFAFA) else Color(0xFF27272A), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.Black else Color(0xFFFAFAFA),
            modifier = Modifier
                .size(17.dp)
                .rotate(rotationDegrees)
        )
    }
}

@Composable
private fun ShadcnTextButton(
    text: String,
    isActive: Boolean,
    rotationDegrees: Float = 0f,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (isActive) Color(0xFFFAFAFA) else Color(0x33000000))
            .border(1.dp, if (isActive) Color(0xFFFAFAFA) else Color(0xFF27272A), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color(0xFFFAFAFA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.rotate(rotationDegrees)
        )
    }
}
