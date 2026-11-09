package app.hoyu.shot.ui

import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clipToBounds
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
    var previewContainerSize by remember { mutableStateOf(IntSize.Zero) }

    var isModeModalVisible by remember { mutableStateOf(false) }
    var isSettingsDialogVisible by remember { mutableStateOf(false) }
    var isFormatMenuVisible by remember { mutableStateOf(false) }
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

    // Main layout container: strictly pure black Nothing OS aesthetic
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // 1. TOP TOOLBAR (Settings, AE, 4:3, Grid, MP/Format, Histogram)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Quick Controls: Settings, AE lock, Ratio, Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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

                // Center: Resolution & Format Menu Trigger Button (e.g. 12.6MP // JPEG)
                val formatStr = uiState.captureFormat.label
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
                            isFormatMenuVisible = !isFormatMenuVisible
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.rotate(animatedRotation),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uiState.selectedMegapixels.replace(" ", ""),
                            color = Color(0xFFFAFAFA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "//",
                            color = Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = formatStr,
                            color = if (uiState.captureRaw) Color(0xFFFF4D6D) else Color(0xFFA1A1AA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right: Live Histogram Toggle or View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uiState.isHistogramVisible) {
                        HistogramView(histogram = uiState.histogram)
                    } else {
                        ShadcnIconButton(
                            icon = Icons.Default.BarChart,
                            isActive = false,
                            rotationDegrees = animatedRotation,
                            onClick = { viewModel.toggleHistogram() }
                        )
                    }
                }
            }

            // ==========================================
            // 2. VIEWFINDER PREVIEW (Strict aspect ratio & constrained overlays)
            // ==========================================
            val previewRatio = when (uiState.aspectRatio) {
                AspectRatio.RATIO_4_3 -> 3f / 4f
                AspectRatio.RATIO_16_9 -> 9f / 16f
                AspectRatio.RATIO_1_1 -> 1f
                else -> 3f / 4f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewRatio)
                    .clipToBounds()
                    .onSizeChanged { previewContainerSize = it }
                    .pointerInput(uiState.currentZoomRatio) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1.0f) {
                                viewModel.setZoomRatio(uiState.currentZoomRatio * zoom)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // Swipe left to quickly open recent photo gallery!
                        var horizontalSwipeAccumulator = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalSwipeAccumulator = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                horizontalSwipeAccumulator += dragAmount
                                if (horizontalSwipeAccumulator < -50f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    horizontalSwipeAccumulator = 0f
                                    viewModel.openGallery()
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // Tap to focus
                        detectTapGestures { offset ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            activeParamMenu = null
                            isFormatMenuVisible = false
                            viewModel.onTouchFocus(
                                offset,
                                previewContainerSize.width,
                                previewContainerSize.height
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Camera2 Hardware Surface
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
                        v.setAspectRatio(camHeight, camWidth)
                    }
                )

                // Composition Grid Overlay (strictly inside the preview container!)
                GridOverlay(gridMode = uiState.gridMode)

                // Focus Peaking & Zebra Stripes Overlay
                PeakingAndZebraOverlay(
                    isPeakingEnabled = uiState.isFocusPeakingEnabled,
                    isZebraEnabled = uiState.isZebraStripesEnabled,
                    histogram = uiState.histogram
                )

                // Dual-Axis ProShot Horizon Level (Roll & Pitch independently turning green)
                if (uiState.isHorizonLevelVisible) {
                    HorizonLevelIndicator(
                        orientation = uiState.horizon,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Tap-to-Focus Reticle
                FocusRingOverlay(tapPoint = uiState.tapFocusPoint)

                // Vertical EV Slider anchored along right edge of preview
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    VerticalEvSlider(
                        evIndex = uiState.evCompensationIndex,
                        onEvChanged = { viewModel.setEvCompensation(it) }
                    )
                }
            }

            // ==========================================
            // 3. BOTTOM CONTROL REGION (Pure black, clean, no overlapping)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row of Bottom Area: EV Position Alert & Lens Switcher
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // EV Alert Badge (Only shown when EV is adjusted != 0)
                    EvPositionAlertBadge(evIndex = uiState.evCompensationIndex)

                    // Lens Switcher (e.g. 0.6x, 1x, 3.2x)
                    if (uiState.availableLenses.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            uiState.availableLenses.filter { !it.isFront }.forEach { lens ->
                                val isSelected = uiState.currentLens?.id == lens.id
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(shape)
                                        .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x33000000))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFFAFAFA) else Color(0xFF27272A),
                                            shape
                                        )
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
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.rotate(animatedRotation)
                                    )
                                }
                            }
                        }
                    }
                }

                // Middle Row: Horizontal Parameter Strip (AF, SEC, ISO, WB)
                HorizontalParameterStrip(
                    uiState = uiState,
                    activeParam = activeParamMenu,
                    rotationDegrees = animatedRotation,
                    onParamClick = { param ->
                        activeParamMenu = if (activeParamMenu == param) null else param
                    }
                )

                // Bottom Action Bar: Thumbnail, Mode [A], Shutter Button, Flip & Flash
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Quick Thumbnail Gallery Trigger
                    QuickThumbnail(
                        photoInfo = uiState.lastCapturedPhoto,
                        onClick = { viewModel.openGallery() }
                    )

                    // Center-Left: Mode Selector Dial Trigger [A], [P], [S], [ISO], [M]
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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

                    // Center: Shutter Button (White circle with prominent Nothing Red outer border!)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(3.dp, Color(0xFFE11D48), CircleShape) // Nothing Red outer border
                            .padding(5.dp)
                            .background(
                                if (uiState.isCapturing) Color(0xFFE4E4E7) else Color(0xFFFFFFFF),
                                CircleShape
                            )
                            .clickable(enabled = !uiState.isCapturing) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                viewModel.takePicture()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isCapturing) {
                            CircularProgressIndicator(
                                color = Color(0xFFE11D48),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            // Center Nothing Red dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFE11D48), CircleShape)
                            )
                        }
                    }

                    // Right: Flip Camera
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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
                                .size(18.dp)
                                .rotate(animatedRotation)
                        )
                    }

                    // Flash Toggle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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
                                .size(18.dp)
                                .rotate(animatedRotation)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. TEMPORARY OVERLAYS & POPUPS (Stay open on select, auto-close 5s, tap outside dismisses without focusing)
        // ==========================================

        // Outside Tap Scrim Interceptor (Active when any popup is visible)
        if (activeParamMenu != null || isFormatMenuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        activeParamMenu = null
                        isFormatMenuVisible = false
                    }
            )
        }

        // Format & Megapixel Selector Popover
        if (isFormatMenuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 52.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                FormatMenu(
                    currentMp = uiState.selectedMegapixels,
                    currentFormat = uiState.captureFormat,
                    onMpSelected = { mp -> viewModel.setMegapixels(mp) },
                    onFormatSelected = { fmt -> viewModel.setCaptureFormat(fmt) },
                    onDismiss = { isFormatMenuVisible = false }
                )
            }
        }

        // Parameter 2-Column Grid Menu (Positioned above the parameter strip without overlapping!)
        activeParamMenu?.let { param ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 160.dp),
                contentAlignment = Alignment.BottomCenter
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
                            },
                            formatItem = { HardwareCapabilities.formatShutterSpeed(it) },
                            onAutoSelected = {
                                viewModel.setExposureMode(ExposureMode.AUTO)
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
                            },
                            formatItem = { "$it" },
                            onAutoSelected = {
                                viewModel.setExposureMode(ExposureMode.AUTO)
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
                            },
                            formatItem = { if (it == 0f) "∞ MACRO" else String.format("%.1fD", it) },
                            onAutoSelected = {
                                viewModel.toggleAutoFocus()
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
                            },
                            formatItem = { "${it}K" },
                            onAutoSelected = {
                                viewModel.setWhiteBalancePreset(WhiteBalancePreset.AUTO)
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

        // ProShot-style Gallery Viewer (Swipe left/right browsing, EXIF bar, Share & Delete)
        if (uiState.isGalleryVisible && uiState.recentPhotos.isNotEmpty()) {
            GalleryViewer(
                photos = uiState.recentPhotos,
                onDismiss = { viewModel.closeGallery() },
                onDeletePhoto = { photo -> viewModel.deletePhoto(photo) }
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
