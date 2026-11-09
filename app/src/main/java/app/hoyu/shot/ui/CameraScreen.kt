package app.hoyu.shot.ui

import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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

    // Intercept system Back button so it navigates internally inside the app
    BackHandler(
        enabled = uiState.isGalleryVisible || activeParamMenu != null || isFormatMenuVisible || isModeModalVisible || isSettingsDialogVisible
    ) {
        when {
            uiState.isGalleryVisible -> viewModel.closeGallery()
            activeParamMenu != null -> activeParamMenu = null
            isFormatMenuVisible -> isFormatMenuVisible = false
            isModeModalVisible -> isModeModalVisible = false
            isSettingsDialogVisible -> isSettingsDialogVisible = false
        }
    }

    // Smooth In-Place Rotation for icons and dialogs
    val animatedRotation by animateFloatAsState(
        targetValue = uiState.uiRotationDegrees,
        animationSpec = tween(durationMillis = 250),
        label = "uiRotation"
    )

    val shape = RoundedCornerShape(2.dp) // shadcn razor-sharp corners

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

    val previewRatio = when (uiState.aspectRatio) {
        AspectRatio.RATIO_4_3 -> 3f / 4f
        AspectRatio.RATIO_16_9 -> 9f / 16f
        AspectRatio.RATIO_1_1 -> 1f
        else -> 3f / 4f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Dynamic Layout Calculation:
        // In 4:3 or 1:1 ratios, black margins are preserved. Controls sit in black bars without obscuring preview.
        // In 16:9 or Full, controls dynamically overlay the preview with translucent glass styling so buttons never get crushed.
        val previewHeightEstimate = maxWidth / previewRatio
        val leftoverHeight = maxHeight - previewHeightEstimate
        val isOverlayNeeded = leftoverHeight < 210.dp

        // ==========================================
        // MAIN VIEWPORT STRUCTURE
        // ==========================================
        if (!isOverlayNeeded) {
            // 4:3 / 1:1 Layout: Clean Column (TopBar -> Preview -> BottomControls in black margin)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar in black letterbox
                TopToolbar(
                    uiState = uiState,
                    animatedRotation = animatedRotation,
                    shape = shape,
                    onOpenSettings = { isSettingsDialogVisible = true },
                    onToggleAeLock = { viewModel.toggleAeLock() },
                    onChangeRatio = {
                        val next = when (uiState.aspectRatio) {
                            AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_1_1
                            AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_3
                            else -> AspectRatio.RATIO_4_3
                        }
                        viewModel.setAspectRatio(next)
                    },
                    onToggleGrid = { viewModel.toggleGrid() },
                    onToggleFormatMenu = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isFormatMenuVisible = !isFormatMenuVisible
                    }
                )

                // Viewfinder Preview (Strict aspect ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(previewRatio)
                ) {
                    ViewfinderPreviewContent(
                        uiState = uiState,
                        animatedRotation = animatedRotation,
                        shape = shape,
                        isOverlayMode = false,
                        onSurfaceReady = { view: AutoFitSurfaceView -> surfaceViewRef = view },
                        onSurfaceCreated = { surface -> viewModel.startCamera(surface) },
                        onSurfaceDestroyed = { viewModel.stopCamera() },
                        onSizeChanged = { previewContainerSize = it },
                        onZoomChanged = { zoom -> viewModel.setZoomRatio(uiState.currentZoomRatio * zoom) },
                        onSwipeToGallery = { viewModel.openGallery() },
                        onTouchFocus = { offset ->
                            activeParamMenu = null
                            isFormatMenuVisible = false
                            viewModel.onTouchFocus(offset, previewContainerSize.width, previewContainerSize.height)
                        },
                        onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                        onResetEv = { viewModel.setEvCompensation(0) }
                    )
                }

                // Bottom Controls in black margin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.Center
                ) {
                    BottomControlsContent(
                        uiState = uiState,
                        activeParamMenu = activeParamMenu,
                        animatedRotation = animatedRotation,
                        shape = shape,
                        onParamClick = { param ->
                            activeParamMenu = if (activeParamMenu == param) null else param
                        },
                        onOpenGallery = { viewModel.openGallery() },
                        onOpenModeModal = { isModeModalVisible = true },
                        onCapture = { viewModel.takePicture() },
                        onToggleFlip = { viewModel.toggleCameraFacing() },
                        onToggleFlash = { viewModel.toggleFlash() },
                        onEvChanged = { viewModel.setEvCompensation(it) },
                        onResetEv = { viewModel.setEvCompensation(0) }
                    )
                }
            }
        } else {
            // 16:9 / Full Layout: Preview fills background, Controls dynamically overlay with translucent styling
            Box(modifier = Modifier.fillMaxSize()) {
                // Viewfinder Preview as primary layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(previewRatio)
                        .align(Alignment.Center)
                ) {
                    ViewfinderPreviewContent(
                        uiState = uiState,
                        animatedRotation = animatedRotation,
                        shape = shape,
                        isOverlayMode = true,
                        onSurfaceReady = { view: AutoFitSurfaceView -> surfaceViewRef = view },
                        onSurfaceCreated = { surface -> viewModel.startCamera(surface) },
                        onSurfaceDestroyed = { viewModel.stopCamera() },
                        onSizeChanged = { previewContainerSize = it },
                        onZoomChanged = { zoom -> viewModel.setZoomRatio(uiState.currentZoomRatio * zoom) },
                        onSwipeToGallery = { viewModel.openGallery() },
                        onTouchFocus = { offset ->
                            activeParamMenu = null
                            isFormatMenuVisible = false
                            viewModel.onTouchFocus(offset, previewContainerSize.width, previewContainerSize.height)
                        },
                        onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                        onResetEv = { viewModel.setEvCompensation(0) }
                    )
                }

                // Top Toolbar (Translucent Overlay)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0x55000000))
                ) {
                    TopToolbar(
                        uiState = uiState,
                        animatedRotation = animatedRotation,
                        shape = shape,
                        onOpenSettings = { isSettingsDialogVisible = true },
                        onToggleAeLock = { viewModel.toggleAeLock() },
                        onChangeRatio = {
                            val next = when (uiState.aspectRatio) {
                                AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                                AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_1_1
                                AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_3
                                else -> AspectRatio.RATIO_4_3
                            }
                            viewModel.setAspectRatio(next)
                        },
                        onToggleGrid = { viewModel.toggleGrid() },
                        onToggleFormatMenu = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isFormatMenuVisible = !isFormatMenuVisible
                        }
                    )
                }

                // Bottom Controls (Translucent Glass Overlay)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x55000000))
                        .navigationBarsPadding()
                ) {
                    BottomControlsContent(
                        uiState = uiState,
                        activeParamMenu = activeParamMenu,
                        animatedRotation = animatedRotation,
                        shape = shape,
                        onParamClick = { param ->
                            activeParamMenu = if (activeParamMenu == param) null else param
                        },
                        onOpenGallery = { viewModel.openGallery() },
                        onOpenModeModal = { isModeModalVisible = true },
                        onCapture = { viewModel.takePicture() },
                        onToggleFlip = { viewModel.toggleCameraFacing() },
                        onToggleFlash = { viewModel.toggleFlash() },
                        onEvChanged = { viewModel.setEvCompensation(it) },
                        onResetEv = { viewModel.setEvCompensation(0) }
                    )
                }
            }
        }

        // ==========================================
        // TEMPORARY OVERLAYS & POPUPS (Click outside to dismiss)
        // ==========================================

        // Outside Tap Scrim Interceptor (Closes active dialog without focusing camera!)
        if (activeParamMenu != null || isFormatMenuVisible || isModeModalVisible || isSettingsDialogVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        activeParamMenu = null
                        isFormatMenuVisible = false
                        isModeModalVisible = false
                        isSettingsDialogVisible = false
                    }
            )
        }

        // Format & Megapixel Selector Popover (Centered popup matching Settings)
        FormatMenu(
            visible = isFormatMenuVisible,
            resolutions = uiState.availableResolutions,
            selectedResolution = uiState.selectedResolution,
            currentFormat = uiState.captureFormat,
            rotationDegrees = animatedRotation,
            onResolutionSelected = { res -> viewModel.setResolution(res) },
            onFormatSelected = { fmt -> viewModel.setCaptureFormat(fmt) },
            onDismiss = { isFormatMenuVisible = false },
            modifier = Modifier.align(Alignment.Center)
        )

        // Parameter Menu Popover (Centered popup matching Settings)
        activeParamMenu?.let { param ->
            when (param) {
                DialParameter.SHUTTER -> {
                    val shutterSpeeds = listOf(
                        125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L,
                        8_000_000L, 16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L,
                        250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L
                    )
                    ParameterGridMenu(
                        visible = true,
                        title = "SEC // SHUTTER",
                        items = shutterSpeeds,
                        selectedItem = uiState.shutterSpeedNanos,
                        rotationDegrees = animatedRotation,
                        onItemSelected = { nanos ->
                            viewModel.setExposureMode(ExposureMode.SHUTTER_PRIORITY)
                            viewModel.setShutterSpeedNanos(nanos)
                        },
                        formatItem = { HardwareCapabilities.formatShutterSpeed(it) },
                        onAutoSelected = { viewModel.setExposureMode(ExposureMode.AUTO) },
                        isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY,
                        onDismiss = { activeParamMenu = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                DialParameter.ISO -> {
                    val isoValues = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400)
                    ParameterGridMenu(
                        visible = true,
                        title = "ISO // SENSOR",
                        items = isoValues,
                        selectedItem = uiState.iso,
                        rotationDegrees = animatedRotation,
                        onItemSelected = { iso ->
                            viewModel.setExposureMode(ExposureMode.ISO_PRIORITY)
                            viewModel.setIso(iso)
                        },
                        formatItem = { "$it" },
                        onAutoSelected = { viewModel.setExposureMode(ExposureMode.AUTO) },
                        isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY,
                        onDismiss = { activeParamMenu = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                DialParameter.FOCUS -> {
                    val focusDiopters = listOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 7.0f, 10.0f)
                    ParameterGridMenu(
                        visible = true,
                        title = "LENS // FOCUS",
                        items = focusDiopters,
                        selectedItem = if (uiState.isManualFocusEnabled) uiState.focusDistanceDiopters else null,
                        rotationDegrees = animatedRotation,
                        onItemSelected = { diopter -> viewModel.setFocusDistance(diopter) },
                        formatItem = { if (it == 0f) "∞ MACRO" else String.format("%.1fD", it) },
                        onAutoSelected = { viewModel.toggleAutoFocus() },
                        isAutoSelected = !uiState.isManualFocusEnabled,
                        onDismiss = { activeParamMenu = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                DialParameter.WHITE_BALANCE -> {
                    val kelvinValues = (2800..7500 step 300).toList()
                    ParameterGridMenu(
                        visible = true,
                        title = "WB // KELVIN",
                        items = kelvinValues,
                        selectedItem = if (uiState.whiteBalancePreset == WhiteBalancePreset.CUSTOM_KELVIN) uiState.kelvinTemperature else null,
                        rotationDegrees = animatedRotation,
                        onItemSelected = { k -> viewModel.setKelvin(k) },
                        formatItem = { "${it}K" },
                        onAutoSelected = { viewModel.setWhiteBalancePreset(WhiteBalancePreset.AUTO) },
                        isAutoSelected = uiState.whiteBalancePreset == WhiteBalancePreset.AUTO,
                        onDismiss = { activeParamMenu = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {}
            }
        }

        // Mode Dial Selector Modal (Centered modal)
        ModeDialSelectorModal(
            visible = isModeModalVisible,
            currentMode = uiState.exposureMode,
            rotationDegrees = animatedRotation,
            onModeSelected = { viewModel.setExposureMode(it) },
            onDismiss = { isModeModalVisible = false },
            modifier = Modifier.align(Alignment.Center)
        )

        // Settings Dialog (Centered modal)
        SettingsDialog(
            visible = isSettingsDialogVisible,
            uiState = uiState,
            rotationDegrees = animatedRotation,
            onToggleResumeSettings = { viewModel.toggleResumeSettings() },
            onCycleNoiseReduction = { viewModel.cycleNoiseReduction() },
            onCycleSharpness = { viewModel.cycleSharpness() },
            onToggleHistogram = { viewModel.toggleHistogram() },
            onToggleHorizon = { viewModel.toggleHorizon() },
            onTogglePeaking = { viewModel.toggleFocusPeaking() },
            onToggleZebra = { viewModel.toggleZebraStripes() },
            onDismiss = { isSettingsDialogVisible = false },
            modifier = Modifier.align(Alignment.Center)
        )

        // ProShot-style Gallery Viewer (Swiping left-to-right enters, matching the gesture!)
        AnimatedVisibility(
            visible = uiState.isGalleryVisible && uiState.recentPhotos.isNotEmpty(),
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            GalleryViewer(
                photos = uiState.recentPhotos,
                rotationDegrees = animatedRotation,
                onDismiss = { viewModel.closeGallery() },
                onDeletePhoto = { photo -> viewModel.deletePhoto(photo) }
            )
        }
    }
}

// ==========================================
// TOP TOOLBAR COMPOSABLE
// ==========================================
@Composable
private fun TopToolbar(
    uiState: CameraUiState,
    animatedRotation: Float,
    shape: RoundedCornerShape,
    onOpenSettings: () -> Unit,
    onToggleAeLock: () -> Unit,
    onChangeRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleFormatMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Quick Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings Gear
            ShadcnIconButton(
                icon = Icons.Default.Settings,
                rotationDegrees = animatedRotation,
                onClick = onOpenSettings
            )

            // AE Lock
            ShadcnTextButton(
                text = if (uiState.isAeLocked) "AEL" else "AE",
                isActive = uiState.isAeLocked,
                rotationDegrees = animatedRotation,
                onClick = onToggleAeLock
            )

            // Aspect Ratio Selector
            ShadcnTextButton(
                text = uiState.aspectRatio.ratioName,
                isActive = false,
                rotationDegrees = animatedRotation,
                onClick = onChangeRatio
            )

            // Grid Mode Selector
            ShadcnIconButton(
                icon = Icons.Default.GridOn,
                isActive = uiState.gridMode != GridMode.NONE,
                rotationDegrees = animatedRotation,
                onClick = onToggleGrid
            )
        }

        // Right: Megapixels & Format Pill (e.g. 12.5MP // JPEG)
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
                .clickable { onToggleFormatMenu() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
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
    }
}

// ==========================================
// VIEWFINDER PREVIEW CONTENT
// ==========================================
@Composable
private fun ViewfinderPreviewContent(
    uiState: CameraUiState,
    animatedRotation: Float,
    shape: RoundedCornerShape,
    isOverlayMode: Boolean,
    onSurfaceReady: (AutoFitSurfaceView) -> Unit,
    onSurfaceCreated: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onSwipeToGallery: () -> Unit,
    onTouchFocus: (androidx.compose.ui.geometry.Offset) -> Unit,
    onSelectLens: (LensItem) -> Unit,
    onResetEv: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { onSizeChanged(it) }
            .pointerInput(uiState.currentZoomRatio) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1.0f) {
                        onZoomChanged(zoom)
                    }
                }
            }
            .pointerInput(uiState.uiRotationDegrees) {
                var accumulator = 0f
                detectDragGestures(
                    onDragStart = { accumulator = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Calculate drag in user's visual frame of reference:
                        // In ALL orientations, dragging left-to-right from user's perspective triggers gallery!
                        val userHorizontalDrag = when (uiState.uiRotationDegrees) {
                            90f -> -dragAmount.y // screen top-to-bottom is user's left-to-right
                            180f -> -dragAmount.x // screen right-to-left is user's left-to-right
                            270f -> dragAmount.y // screen bottom-to-top is user's left-to-right
                            else -> dragAmount.x // screen left-to-right is user's left-to-right
                        }
                        accumulator += userHorizontalDrag
                        if (accumulator > 50f) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            accumulator = 0f
                            onSwipeToGallery()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTouchFocus(offset)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Hardware Camera2 SurfaceView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AutoFitSurfaceView(context).apply {
                    onSurfaceReady(this)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurfaceCreated(holder.surface)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurfaceDestroyed()
                        }
                    })
                }
            },
            update = { v ->
                val (camWidth, camHeight) = uiState.previewSize
                v.setAspectRatio(camHeight, camWidth)
            }
        )

        // Composition Grid Overlay
        GridOverlay(gridMode = uiState.gridMode)

        // Focus Peaking & Zebra Stripes Overlay
        PeakingAndZebraOverlay(
            isPeakingEnabled = uiState.isFocusPeakingEnabled,
            isZebraEnabled = uiState.isZebraStripesEnabled,
            histogram = uiState.histogram
        )

        // Large Dual-Axis Horizon Level (Rotates with user holding orientation)
        if (uiState.isHorizonLevelVisible) {
            HorizonLevelIndicator(
                orientation = uiState.horizon,
                rotationDegrees = animatedRotation,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap-to-Focus Reticle (Animated spring and pulse on lock)
        FocusRingOverlay(tapPoint = uiState.tapFocusPoint)

        // Live Histogram: Only shown if enabled in settings, safe padding preventing overlay
        if (uiState.isHistogramVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = if (uiState.uiRotationDegrees != 0f) 80.dp else if (isOverlayMode) 60.dp else 14.dp,
                        start = if (uiState.uiRotationDegrees != 0f) 64.dp else 14.dp
                    )
                    .rotate(animatedRotation)
                    .clip(shape)
                    .background(Color(0x77000000))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(4.dp)
            ) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // EV Position Alert Badge: ALWAYS at the bottom of the user's view inside the preview!
        if (uiState.evCompensationIndex != 0) {
            val alertAlignment = when (uiState.uiRotationDegrees) {
                90f -> Alignment.CenterStart
                180f -> Alignment.TopCenter
                270f -> Alignment.CenterEnd
                else -> Alignment.BottomCenter
            }
            val alertPadding = when (uiState.uiRotationDegrees) {
                90f -> Modifier.padding(start = 20.dp)
                180f -> Modifier.padding(top = 56.dp)
                270f -> Modifier.padding(end = 20.dp)
                else -> Modifier.padding(bottom = 56.dp)
            }
            Box(
                modifier = Modifier
                    .align(alertAlignment)
                    .then(alertPadding)
            ) {
                EvPositionAlertBadge(
                    evIndex = uiState.evCompensationIndex,
                    rotationDegrees = animatedRotation,
                    onResetEv = onResetEv
                )
            }
        }

        // Bottom Inside Preview: Lens Switcher (0.6x, 1x, 3.2x horizontally)
        if (uiState.availableLenses.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.availableLenses.filter { !it.isFront }.forEach { lens ->
                    val isSelected = uiState.currentLens?.id == lens.id
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x66000000))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFAFAFA) else Color(0x66FFFFFF),
                                shape
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelectLens(lens)
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
}

// ==========================================
// BOTTOM CONTROLS CONTENT
// ==========================================
@Composable
private fun BottomControlsContent(
    uiState: CameraUiState,
    activeParamMenu: DialParameter?,
    animatedRotation: Float,
    shape: RoundedCornerShape,
    onParamClick: (DialParameter) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenModeModal: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlip: () -> Unit,
    onToggleFlash: () -> Unit,
    onEvChanged: (Int) -> Unit,
    onResetEv: () -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Horizontal Parameter Strip (AF, SEC, ISO, WB)
        HorizontalParameterStrip(
            uiState = uiState,
            activeParam = activeParamMenu,
            rotationDegrees = animatedRotation,
            onParamClick = onParamClick
        )

        // Row 2: Bottom Action Bar (Thumbnail, Mode [A], Shutter, Flip Camera, Flash)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Gallery Thumbnail
            QuickThumbnail(
                photoInfo = uiState.lastCapturedPhoto,
                onClick = onOpenGallery
            )

            // Mode Selector Button [A]
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(shape)
                    .background(Color(0x33000000))
                    .border(1.dp, Color(0xFFFAFAFA), shape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenModeModal()
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

            // Shutter Button (White circle, thick Nothing Red border #E11D48, center red dot)
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .border(3.dp, Color(0xFFE11D48), CircleShape)
                    .padding(5.dp)
                    .background(
                        if (uiState.isCapturing) Color(0xFFE4E4E7) else Color(0xFFFFFFFF),
                        CircleShape
                    )
                    .clickable(enabled = !uiState.isCapturing) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onCapture()
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFE11D48), CircleShape)
                    )
                }
            }

            // Flip Camera Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shape)
                    .background(Color(0x33000000))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleFlip()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                    modifier = Modifier
                        .size(17.dp)
                        .rotate(animatedRotation)
                )
            }

            // Flash Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shape)
                    .background(if (uiState.flashMode != FlashMode.OFF) Color(0xFF27272A) else Color(0x33000000))
                    .border(1.dp, if (uiState.flashMode != FlashMode.OFF) Color(0xFFFAFAFA) else Color(0xFF27272A), shape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleFlash()
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
                        .size(17.dp)
                        .rotate(animatedRotation)
                )
            }
        }

        // Row 3: Dedicated Bottom EV Bar - ON ITS OWN ROW AT THE VERY BOTTOM (Near charging port)
        // Spans the full width of the control region, matching lens switcher width!
        DedicatedBottomEvBar(
            evIndex = uiState.evCompensationIndex,
            minEv = uiState.capabilities?.evCompensationRange?.lower ?: -6,
            maxEv = uiState.capabilities?.evCompensationRange?.upper ?: 6,
            rotationDegrees = animatedRotation,
            onEvChanged = onEvChanged,
            onResetEv = onResetEv
        )
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
