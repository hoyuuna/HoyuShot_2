package app.hoyu.shot.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.hoyu.shot.model.*
import app.hoyu.shot.ui.components.*
import kotlinx.coroutines.*

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.setGeotaggingEnabled(true)
        } else {
            viewModel.setGeotaggingEnabled(false)
        }
    }

    var surfaceViewRef by remember { mutableStateOf<AutoFitSurfaceView?>(null) }
    var previewContainerSize by remember { mutableStateOf(IntSize.Zero) }

    var isModeModalVisible by remember { mutableStateOf(false) }
    var isSettingsDialogVisible by remember { mutableStateOf(false) }
    var isAdvancedSettingsVisible by remember { mutableStateOf(false) }
    var scrollToUpdates by remember { mutableStateOf(false) }
    var isFormatMenuVisible by remember { mutableStateOf(false) }
    var activeParamMenu by remember { mutableStateOf<DialParameter?>(null) }
    // Maintain last non-null parameter card so the exit animation (scale + fade) renders smoothly
    var displayedParamMenu by remember { mutableStateOf<DialParameter?>(null) }
    LaunchedEffect(activeParamMenu) {
        if (activeParamMenu != null) {
            displayedParamMenu = activeParamMenu
        }
    }

    val openUpdateEvent by viewModel.openUpdateSettingsEvent.collectAsState()
    LaunchedEffect(openUpdateEvent) {
        if (openUpdateEvent) {
            activeParamMenu = null
            isFormatMenuVisible = false
            isModeModalVisible = false
            isSettingsDialogVisible = false
            isAdvancedSettingsVisible = true
            scrollToUpdates = true
            viewModel.consumeOpenUpdateSettings()
        }
    }

    // Intercept system Back button so it navigates internally inside the app
    BackHandler(
        enabled = uiState.isGalleryVisible || activeParamMenu != null || isFormatMenuVisible || isModeModalVisible || isSettingsDialogVisible || isAdvancedSettingsVisible
    ) {
        when {
            uiState.isGalleryVisible -> viewModel.closeGallery()
            isAdvancedSettingsVisible -> {
                isAdvancedSettingsVisible = false
                scrollToUpdates = false
            }
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

        val isAnyPopupOpen = activeParamMenu != null || isFormatMenuVisible || isModeModalVisible || isSettingsDialogVisible || isAdvancedSettingsVisible

        // Subtle single haptic when horizon level aligns (with hysteresis to avoid micro-jitter repeats)
        var hasSignaledHorizonLevel by remember { mutableStateOf(false) }
        var lastHorizonHapticTime by remember { mutableStateOf(0L) }
        LaunchedEffect(
            uiState.horizon,
            uiState.isHorizonLevelVisible,
            uiState.isGalleryVisible,
            uiState.uiHapticsMode,
            isAnyPopupOpen
        ) {
            if (!uiState.isHorizonLevelVisible || uiState.isGalleryVisible || isAnyPopupOpen) {
                hasSignaledHorizonLevel = false
                return@LaunchedEffect
            }
            val h = uiState.horizon
            if (h.isLevel && !hasSignaledHorizonLevel) {
                val now = System.currentTimeMillis()
                if (now - lastHorizonHapticTime >= 800L) {
                    if (uiState.uiHapticsMode == "VIBRATE") {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    lastHorizonHapticTime = now
                }
                hasSignaledHorizonLevel = true
            } else if (
                hasSignaledHorizonLevel &&
                (kotlin.math.abs(h.rollDegrees) > 3.5f || kotlin.math.abs(h.pitchDegrees) > 4.0f)
            ) {
                hasSignaledHorizonLevel = false
            }
        }

        // ==========================================
        // MAIN VIEWPORT STRUCTURE (Never blurred!)
        // ==========================================
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                            onZoomChanged = { zoom -> viewModel.onPinchZoom(zoom) },
                            onSwipeToGallery = { viewModel.openGallery() },
                            onTouchTap = { offset ->
                                activeParamMenu = null
                                isFormatMenuVisible = false
                                viewModel.handleScreenTap(offset, previewContainerSize.width, previewContainerSize.height)
                            },
                            onTouchHold = { offset ->
                                activeParamMenu = null
                                isFormatMenuVisible = false
                                viewModel.handleScreenHold(offset, previewContainerSize.width, previewContainerSize.height)
                            },
                            onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                            onResetDigitalZoom = { viewModel.resetDigitalZoom() },
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
                            onBurstCapture = { viewModel.takeBurstShot() },
                            onToggleFlip = { viewModel.toggleCameraFacing() },
                            onToggleFlash = { viewModel.toggleFlash() },
                            onEvChanged = { viewModel.setEvCompensation(it) },
                            onResetEv = { viewModel.setEvCompensation(0) },
                            onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                            onResetDigitalZoom = { viewModel.resetDigitalZoom() }
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
                            onZoomChanged = { zoom -> viewModel.onPinchZoom(zoom) },
                            onSwipeToGallery = { viewModel.openGallery() },
                            onTouchTap = { offset ->
                                activeParamMenu = null
                                isFormatMenuVisible = false
                                viewModel.handleScreenTap(offset, previewContainerSize.width, previewContainerSize.height)
                            },
                            onTouchHold = { offset ->
                                activeParamMenu = null
                                isFormatMenuVisible = false
                                viewModel.handleScreenHold(offset, previewContainerSize.width, previewContainerSize.height)
                            },
                            onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                            onResetDigitalZoom = { viewModel.resetDigitalZoom() },
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
                            onBurstCapture = { viewModel.takeBurstShot() },
                            onToggleFlip = { viewModel.toggleCameraFacing() },
                            onToggleFlash = { viewModel.toggleFlash() },
                            onEvChanged = { viewModel.setEvCompensation(it) },
                            onResetEv = { viewModel.setEvCompensation(0) },
                            onSelectLens = { lens: LensItem -> viewModel.selectLens(lens) },
                            onResetDigitalZoom = { viewModel.resetDigitalZoom() }
                        )
                    }
                }
            }
        }

        // ==========================================
        // TEMPORARY OVERLAYS & POPUPS (Click outside to dismiss)
        // ==========================================

        // Outside Tap Scrim Interceptor (Closes active dialog without focusing camera!)
        if (isAnyPopupOpen) {
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
                        isAdvancedSettingsVisible = false
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
            modifier = Modifier.fillMaxSize()
        )

        // Parameter Menu Popover (Centered popup with scale and fade animations)
        AnimatedVisibility(
            visible = activeParamMenu != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.88f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.88f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (displayedParamMenu) {
                    DialParameter.SHUTTER -> {
                        val minShutter = uiState.capabilities?.shutterSpeedRangeNanos?.lower ?: 100_000L
                        val maxShutter = uiState.capabilities?.shutterSpeedRangeNanos?.upper ?: 2_000_000_000L
                        val standardShutters = listOf(
                            100_000L, 125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L,
                            8_000_000L, 16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L,
                            250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L,
                            8_000_000_000L, 15_000_000_000L, 30_000_000_000L
                        )
                        val shutterSpeeds = (standardShutters.filter { it in minShutter..maxShutter } + listOf(minShutter, maxShutter)).distinct().sorted()
                        ParameterGridMenu(
                            visible = true,
                            title = "SEC // SHUTTER",
                            items = shutterSpeeds,
                            selectedItem = uiState.shutterSpeedNanos.coerceIn(minShutter, maxShutter),
                            limitItem = uiState.minShutterLimitNanos,
                            onItemLongClick = { nanos -> viewModel.toggleShutterLimit(nanos) },
                            footerNote = "TIP // Long-press a shutter speed to set as Minimum Shutter Limit (L) for Auto mode.",
                            rotationDegrees = animatedRotation,
                            onItemSelected = { nanos ->
                                viewModel.setExposureMode(ExposureMode.SHUTTER_PRIORITY)
                                viewModel.setShutterSpeedNanos(nanos)
                            },
                            formatItem = { HardwareCapabilities.formatShutterSpeed(it) },
                            onAutoSelected = { viewModel.setExposureMode(ExposureMode.AUTO) },
                            isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    DialParameter.ISO -> {
                        val minIso = uiState.capabilities?.isoRange?.lower ?: 50
                        val maxIso = uiState.capabilities?.isoRange?.upper ?: 3200
                        val standardIsos = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400, 12800)
                        val isoValues = (standardIsos.filter { it in minIso..maxIso } + listOf(minIso, maxIso)).distinct().sorted()
                        ParameterGridMenu(
                            visible = true,
                            title = "ISO // SENSOR",
                            items = isoValues,
                            selectedItem = uiState.iso.coerceIn(minIso, maxIso),
                            limitItem = uiState.maxIsoLimit,
                            onItemLongClick = { iso -> viewModel.toggleIsoLimit(iso) },
                            footerNote = "TIP // Long-press an ISO to set as Maximum ISO Ceiling (L) for Auto mode.",
                            rotationDegrees = animatedRotation,
                            onItemSelected = { iso ->
                                viewModel.setExposureMode(ExposureMode.ISO_PRIORITY)
                                viewModel.setIso(iso)
                            },
                            formatItem = { "$it" },
                            onAutoSelected = { viewModel.setExposureMode(ExposureMode.AUTO) },
                            isAutoSelected = uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY,
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    DialParameter.FOCUS -> {
                        val maxDiopters = uiState.capabilities?.minFocusDistanceDiopters ?: 10.0f
                        val standardDiopters = listOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 7.0f, 10.0f, 15.0f)
                        val focusDiopters = if (maxDiopters > 0f) {
                            (standardDiopters.filter { it in 0.0f..maxDiopters } + listOf(maxDiopters)).distinct().sorted()
                        } else {
                            listOf(0.0f)
                        }
                        ParameterGridMenu(
                            visible = true,
                            title = "LENS // FOCUS",
                            items = focusDiopters,
                            selectedItem = if (uiState.isManualFocusEnabled) uiState.focusDistanceDiopters.coerceIn(0f, maxDiopters) else null,
                            rotationDegrees = animatedRotation,
                            onItemSelected = { diopter -> viewModel.setFocusDistance(diopter) },
                            formatItem = { if (it == 0f) "∞ MACRO" else String.format(java.util.Locale.US, "%.1fD", it) },
                            onAutoSelected = { viewModel.toggleAutoFocus() },
                            isAutoSelected = !uiState.isManualFocusEnabled,
                            onDismiss = { activeParamMenu = null }
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
                            onDismiss = { activeParamMenu = null }
                        )
                    }
                    else -> {}
                }
            }
        }

        // Mode Dial Selector Modal (Centered modal)
        ModeDialSelectorModal(
            visible = isModeModalVisible,
            currentMode = uiState.exposureMode,
            rotationDegrees = animatedRotation,
            onModeSelected = { viewModel.setExposureMode(it) },
            onDismiss = { isModeModalVisible = false },
            modifier = Modifier.fillMaxSize()
        )

        // Settings Dialog (Centered modal)
        SettingsDialog(
            visible = isSettingsDialogVisible,
            uiState = uiState,
            rotationDegrees = animatedRotation,
            onToggleResumeSettings = { viewModel.toggleResumeSettings() },
            onToggleGeotagging = {
                if (uiState.isGeotaggingEnabled) {
                    viewModel.setGeotaggingEnabled(false)
                } else {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        viewModel.setGeotaggingEnabled(true)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            },
            onCycleNoiseReduction = { viewModel.cycleNoiseReduction() },
            onSelectTimerMode = { viewModel.setTimerMode(it) },
            onCycleSharpness = { viewModel.cycleSharpness() },
            onToggleHistogram = { viewModel.toggleHistogram() },
            onToggleHorizon = { viewModel.toggleHorizon() },
            onTogglePeaking = { viewModel.toggleFocusPeaking() },
            onToggleZebra = { viewModel.toggleZebraStripes() },
            onOpenAdvancedSettings = {
                isSettingsDialogVisible = false
                isAdvancedSettingsVisible = true
            },
            onDismiss = { isSettingsDialogVisible = false },
            modifier = Modifier.fillMaxSize()
        )

        // Advanced Settings Dialog (Fixed Portrait Only, Nothing OS / shadcn)
        AdvancedSettingsDialog(
            visible = isAdvancedSettingsVisible,
            scrollToUpdates = scrollToUpdates,
            uiState = uiState,
            onJpegQualityChanged = { viewModel.setJpegQuality(it) },
            onToggleMirrorSelfie = { viewModel.toggleMirrorSelfie() },
            onAntibandingChanged = { viewModel.setAntibandingMode(it) },
            onTapActionChanged = { viewModel.setTapAction(it) },
            onHoldActionChanged = { viewModel.setHoldAction(it) },
            onVolumeUpActionChanged = { viewModel.setVolumeUpAction(it) },
            onVolumeDownActionChanged = { viewModel.setVolumeDownAction(it) },
            onCameraSoundChanged = { viewModel.setCameraSoundMode(it) },
            onUiHapticsChanged = { viewModel.setUiHapticsMode(it) },
            onToggleUiBlur = { viewModel.toggleUiBlur() },
            onToggleAutoUpdateCheck = { viewModel.toggleAutoUpdateCheck() },
            onTogglePreReleaseUpdate = { viewModel.togglePreReleaseUpdate() },
            onToggleTeleZoomFallback = { viewModel.toggleTeleZoomFallback() },
            onDismiss = {
                isAdvancedSettingsVisible = false
                scrollToUpdates = false
            }
        )

        // ProShot-style Gallery Viewer (Animated with swipe direction)
        AnimatedVisibility(
            visible = uiState.isGalleryVisible && uiState.recentPhotos.isNotEmpty(),
            enter = when (uiState.uiRotationDegrees) {
                90f -> slideInVertically(initialOffsetY = { -it }) + fadeIn()
                180f -> slideInHorizontally(initialOffsetX = { it }) + fadeIn()
                270f -> slideInVertically(initialOffsetY = { it }) + fadeIn()
                else -> slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
            },
            exit = when (uiState.uiRotationDegrees) {
                90f -> slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                180f -> slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                270f -> slideOutVertically(targetOffsetY = { it }) + fadeOut()
                else -> slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            GalleryViewer(
                photos = uiState.recentPhotos,
                rotationDegrees = animatedRotation,
                onDismiss = { viewModel.closeGallery() },
                onDeletePhoto = { photo -> viewModel.deletePhoto(photo) },
                modifier = Modifier.fillMaxSize()
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
    onTouchTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onTouchHold: (androidx.compose.ui.geometry.Offset) -> Unit,
    onSelectLens: (LensItem) -> Unit,
    onResetDigitalZoom: () -> Unit,
    onResetEv: () -> Unit
) {
    val view = LocalView.current
    val shutterCurtainAlpha = remember { Animatable(0f) }

    LaunchedEffect(uiState.shutterBlinkTrigger) {
        if (uiState.shutterBlinkTrigger > 0L) {
            if (uiState.cameraSoundMode == "VIBRATE") {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            shutterCurtainAlpha.snapTo(0f)
            shutterCurtainAlpha.animateTo(
                targetValue = 0.92f,
                animationSpec = tween(durationMillis = 25, easing = FastOutSlowInEasing)
            )
            shutterCurtainAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 35, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(uiState.timerCountdownSeconds) {
        if (uiState.timerCountdownSeconds != null && uiState.uiHapticsMode == "VIBRATE") {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { onSizeChanged(it) }
            // 1. Multi-touch isolated gestures: STRICT 2-finger Pinch Zoom vs 1-finger Gallery Swipe
            .pointerInput(uiState.uiRotationDegrees) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isMultiTouch = false
                    var prevPinchDistance = 0f
                    var horizontalDragAccumulator = 0f
                    var totalDragDistance = 0f
                    var hasTriggeredGallery = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            // Two or more fingers down: STRICTLY PINCH ZOOM, NEVER trigger gallery!
                            isMultiTouch = true
                            val p1 = pressedPointers[0].position
                            val p2 = pressedPointers[1].position
                            val distance = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)

                            if (prevPinchDistance > 0f && distance > 0f) {
                                val zoomDelta = distance / prevPinchDistance
                                if (kotlin.math.abs(zoomDelta - 1.0f) > 0.005f) {
                                    onZoomChanged(zoomDelta)
                                }
                            }
                            prevPinchDistance = distance
                            pressedPointers.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && !isMultiTouch && !hasTriggeredGallery) {
                            // Exactly 1 finger, and 2 fingers were NEVER present in this gesture
                            val change = pressedPointers[0]
                            val dragAmount = change.position - change.previousPosition
                            val dragDistance = kotlin.math.hypot(dragAmount.x, dragAmount.y)
                            totalDragDistance += dragDistance

                            // Only evaluate directional drag after exceeding slight movement slop (> 20px)
                            if (totalDragDistance > 20f) {
                                val userHorizontalDrag = when (uiState.uiRotationDegrees) {
                                    90f -> dragAmount.y
                                    180f -> -dragAmount.x
                                    270f -> -dragAmount.y
                                    else -> dragAmount.x
                                }
                                horizontalDragAccumulator += userHorizontalDrag

                                if (horizontalDragAccumulator > 120f) {
                                    hasTriggeredGallery = true
                                    if (uiState.uiHapticsMode == "VIBRATE") {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    onSwipeToGallery()
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (uiState.uiHapticsMode == "VIBRATE") {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onTouchTap(offset)
                    },
                    onLongPress = { offset ->
                        if (uiState.uiHapticsMode == "VIBRATE") {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        onTouchHold(offset)
                    }
                )
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

        // Spot AE Metering Reticle (Shows independently from AF)
        SpotAeRingOverlay(tapPoint = uiState.tapAePoint, rotationDegrees = animatedRotation)

        val hasLensSwitcherInPreview = uiState.availableLenses.size > 1 && uiState.aspectRatio != AspectRatio.RATIO_16_9

        // Live Histogram: Positioned in corner unless obstructed by lens switcher in preview
        if (uiState.isHistogramVisible) {
            val histAlignment = when (uiState.uiRotationDegrees) {
                90f -> Alignment.TopStart
                180f -> Alignment.TopEnd
                270f -> Alignment.BottomEnd
                else -> Alignment.BottomStart
            }
            val histPadding = when (uiState.uiRotationDegrees) {
                90f -> Modifier.padding(start = 14.dp, top = 14.dp)
                180f -> Modifier.padding(end = 14.dp, top = 14.dp)
                270f -> if (hasLensSwitcherInPreview) Modifier.padding(end = 14.dp, bottom = 76.dp) else Modifier.padding(end = 14.dp, bottom = 14.dp)
                else -> if (hasLensSwitcherInPreview) Modifier.padding(start = 14.dp, bottom = 76.dp) else Modifier.padding(start = 14.dp, bottom = 14.dp)
            }
            Box(
                modifier = Modifier
                    .align(histAlignment)
                    .then(histPadding)
                    .rotateBounds(animatedRotation)
                    .clip(shape)
                    .background(Color(0x77000000))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(4.dp)
            ) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // EV Position Alert Badge: In portrait sits above lens switcher if present; in other orientations sits near edge
        if (uiState.evCompensationIndex != 0) {
            val alertAlignment = when (uiState.uiRotationDegrees) {
                90f -> Alignment.CenterStart
                180f -> Alignment.TopCenter
                270f -> Alignment.CenterEnd
                else -> Alignment.BottomCenter
            }
            val alertPadding = when (uiState.uiRotationDegrees) {
                90f -> Modifier.padding(start = 12.dp)
                180f -> Modifier.padding(top = 12.dp)
                270f -> Modifier.padding(end = 12.dp)
                else -> Modifier.padding(bottom = if (hasLensSwitcherInPreview) 56.dp else 12.dp)
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

        // Bottom Inside Preview: Lens Switcher (0.6x, 1x, 3.2x, 5x, etc. horizontally)
        // In 16:9, lens switcher is placed next to the parameter strip instead of bottom center
        if (uiState.availableLenses.size > 1 && uiState.aspectRatio != AspectRatio.RATIO_16_9) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.availableLenses.filter { !it.isFront }.forEach { lens ->
                    val isSelected = uiState.currentLens?.id == lens.id
                    val displayLabel = if (isSelected && uiState.digitalZoomRatio > 1.05f) {
                        String.format(java.util.Locale.US, "%.1fx", lens.zoomRatio * uiState.digitalZoomRatio)
                    } else {
                        lens.label
                    }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 36.dp)
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x66000000))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFAFAFA) else Color(0x66FFFFFF),
                                shape
                            )
                            .clickable {
                                if (uiState.uiHapticsMode == "VIBRATE") {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                if (isSelected && uiState.digitalZoomRatio > 1.05f) {
                                    onResetDigitalZoom()
                                } else {
                                    onSelectLens(lens)
                                }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayLabel,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Self-Timer Countdown Overlay (Nothing OS Monospace)
        val countdownSec = uiState.timerCountdownSeconds
        if (countdownSec != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .rotateBounds(animatedRotation)
                    .clip(shape)
                    .background(Color(0xB809090B))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "TIMER // ${uiState.timerMode.label}",
                    color = Color(0xFFE11D48),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = countdownSec.toString(),
                    color = Color(0xFFFAFAFA),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TAP SHUTTER TO CANCEL",
                    color = Color(0xFFA1A1AA),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Mechanical shutter curtain blackout animation (~60ms total)
        if (shutterCurtainAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shutterCurtainAlpha.value))
            )
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
    onBurstCapture: () -> Unit,
    onToggleFlip: () -> Unit,
    onToggleFlash: () -> Unit,
    onEvChanged: (Int) -> Unit,
    onResetEv: () -> Unit,
    onSelectLens: (LensItem) -> Unit,
    onResetDigitalZoom: () -> Unit = {}
) {
    val view = LocalView.current
    var isBursting by remember { mutableStateOf(false) }
    var burstCount by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // In 16:9, place Lens Switcher right above/beside the parameter strip so Shutter doesn't cover it
        if (uiState.aspectRatio == AspectRatio.RATIO_16_9 && uiState.availableLenses.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.availableLenses.filter { !it.isFront }.forEach { lens ->
                    val isSelected = uiState.currentLens?.id == lens.id
                    val displayLabel = if (isSelected && uiState.digitalZoomRatio > 1.05f) {
                        String.format(java.util.Locale.US, "%.1fx", lens.zoomRatio * uiState.digitalZoomRatio)
                    } else {
                        lens.label
                    }
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .defaultMinSize(minWidth = 34.dp)
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x66000000))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFAFAFA) else Color(0x66FFFFFF),
                                shape
                            )
                            .clickable {
                                if (uiState.uiHapticsMode == "VIBRATE") {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                if (isSelected && uiState.digitalZoomRatio > 1.05f) {
                                    onResetDigitalZoom()
                                } else {
                                    onSelectLens(lens)
                                }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayLabel,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

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
                rotationDegrees = animatedRotation,
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

            // Shutter Button with Burst Shooting (Hold >= 0.5s) & Exposure duration feedback
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Burst count indicator badge when burst shooting is active
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBursting && burstCount > 0,
                    enter = fadeIn(tween(100)) + scaleIn(tween(100)),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-36).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(Color(0xFFE11D48))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "BURST // $burstCount",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .border(3.dp, Color(0xFFE11D48), CircleShape)
                        .padding(5.dp)
                        .background(
                            if (uiState.isCapturing || uiState.isLongExposing) Color(0xFFE4E4E7) else Color(0xFFFFFFFF),
                            CircleShape
                        )
                        .pointerInput(uiState.isLongExposing, uiState.timerMode, uiState.timerCountdownSeconds) {
                            detectTapGestures(
                                onPress = {
                                    if (uiState.isLongExposing) return@detectTapGestures
                                    val isTimerActive = uiState.timerMode != TimerMode.OFF || uiState.timerCountdownSeconds != null
                                    var isHolding = true
                                    val startTime = System.currentTimeMillis()
                                    var burstJob: Job? = null

                                    if (!isTimerActive) {
                                        burstJob = coroutineScope.launch {
                                            delay(500)
                                            if (isHolding) {
                                                isBursting = true
                                                burstCount = 0
                                                while (isHolding) {
                                                    burstCount++
                                                    onBurstCapture()
                                                    val exposureMs = (uiState.shutterSpeedNanos / 1_000_000L).coerceIn(100L, 500L)
                                                    delay(exposureMs)
                                                }
                                            }
                                        }
                                    }

                                    tryAwaitRelease()
                                    isHolding = false
                                    val duration = System.currentTimeMillis() - startTime
                                    burstJob?.cancel()

                                    if (!isBursting && (isTimerActive || duration < 500)) {
                                        onCapture()
                                    }

                                    isBursting = false
                                    burstCount = 0
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLongExposing) {
                        CircularProgressIndicator(
                            color = Color(0xFFE11D48),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (uiState.timerCountdownSeconds != null) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE11D48))
                        )
                    } else if (uiState.timerMode != TimerMode.OFF) {
                        Text(
                            text = uiState.timerMode.label,
                            color = Color(0xFFE11D48),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.rotate(animatedRotation)
                        )
                    } else {
                        val dotScale by animateFloatAsState(
                            targetValue = if (uiState.isCapturing) 1.5f else 1.0f,
                            animationSpec = tween(durationMillis = 80),
                            label = "dotScale"
                        )
                        Box(
                            modifier = Modifier
                                .size((8 * dotScale).dp)
                                .background(Color(0xFFE11D48), CircleShape)
                        )
                    }
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
