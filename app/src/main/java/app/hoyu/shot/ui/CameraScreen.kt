package app.hoyu.shot.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.hoyu.shot.model.DialParameter
import app.hoyu.shot.model.HardwareCapabilities
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
                    viewModel.onTouchFocus(offset, containerSize.width, containerSize.height)
                }
            }
    ) {
        // 1. Camera2 Viewfinder with AutoFitSurfaceView
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

        // 2. Composition Grid Overlay (Rule of Thirds / Golden Ratio)
        GridOverlay(gridMode = uiState.gridMode)

        // 3. Focus Peaking & Zebra Stripes Overlay
        PeakingAndZebraOverlay(
            isPeakingEnabled = uiState.isFocusPeakingEnabled,
            isZebraEnabled = uiState.isZebraStripesEnabled,
            histogram = uiState.histogram
        )

        // 4. Electronic Horizon Level Overlay
        if (uiState.isHorizonLevelVisible) {
            HorizonLevelIndicator(
                orientation = uiState.horizon,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 5. Tap-to-Focus Reticle Ring
        FocusRingOverlay(tapPoint = uiState.tapFocusPoint)

        // 6. Top Section: Settings Bar & HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Quick toggles for Flash, Timer, Grid, Peaking, Zebra, Aspect Ratio, RAW
            TopSettingsBar(
                uiState = uiState,
                onToggleFlash = { viewModel.toggleFlash() },
                onToggleTimer = { viewModel.toggleTimer() },
                onToggleGrid = { viewModel.toggleGrid() },
                onToggleHistogram = { viewModel.toggleHistogram() },
                onToggleHorizon = { viewModel.toggleHorizon() },
                onTogglePeaking = { viewModel.toggleFocusPeaking() },
                onToggleZebra = { viewModel.toggleZebraStripes() },
                onSelectAspectRatio = { viewModel.setAspectRatio(it) },
                onToggleRaw = { viewModel.toggleRaw() }
            )

            // Top Status readout + Live Histogram
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "HOYUSHOT",
                            color = Color(0xFFFFD600),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // AE-L / AF-L indicators
                        if (uiState.isAeLocked) {
                            Text(
                                text = "AE-L",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .background(Color(0xFFFFD600), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (uiState.isAfLocked) {
                            Text(
                                text = "AF-L",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .background(Color(0xFF00E676), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                    val caps = uiState.capabilities
                    if (caps != null) {
                        val activeLabel = uiState.currentLens?.label ?: "1x"
                        Text(
                            text = "$activeLabel | ${caps.hardwareLevelName} | ${if (caps.supportsRawCapture) "RAW OK" else "NO RAW"}",
                            color = if (caps.isLevelFullOrHigher) Color(0xFF00E676) else Color(0xFFFF9100),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (uiState.isHistogramVisible) {
                    HistogramView(histogram = uiState.histogram)
                }
            }
        }

        // 7. Giant Timer Countdown Overlay
        uiState.timerCountdownSeconds?.let { countdown ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdown",
                    color = Color(0xFFFFD600),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 8. Error banner
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .background(Color(0xD9D50000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = error, color = Color.White, fontSize = 12.sp)
            }
        }

        // 9. Bottom Controls Group
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lens Switcher (0.6x, 1x, 3.2x, Front)
            if (uiState.availableLenses.size > 1) {
                LensSelector(
                    lenses = uiState.availableLenses,
                    currentLens = uiState.currentLens,
                    onLensSelect = { selectedLens -> viewModel.selectLens(selectedLens) },
                    onFlipFacing = { viewModel.toggleCameraFacing() },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Dial Wheel Slider (ISO, Shutter, Focus, EV, Kelvin)
            when (uiState.activeDialParameter) {
                DialParameter.ISO -> {
                    val minIso = uiState.capabilities?.isoRange?.lower ?: 50
                    val maxIso = uiState.capabilities?.isoRange?.upper ?: 6400
                    val fullList = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400)
                    val isoList = fullList.filter { it in minIso..maxIso }
                    val selectedIdx = isoList.indexOf(uiState.iso).takeIf { it >= 0 } ?: (isoList.size / 2)
                    DialWheelControl(
                        title = "ISO Sensitivity",
                        items = if (isoList.isNotEmpty()) isoList else fullList,
                        selectedIndex = selectedIdx.coerceIn(0, (if (isoList.isNotEmpty()) isoList else fullList).size - 1),
                        onItemSelected = { _, value -> viewModel.setIso(value) },
                        formatItem = { "$it" }
                    )
                }
                DialParameter.SHUTTER -> {
                    val shutterList = listOf(
                        125_000L,       // 1/8000s
                        250_000L,       // 1/4000s
                        500_000L,       // 1/2000s
                        1_000_000L,     // 1/1000s
                        2_000_000L,     // 1/500s
                        4_000_000L,     // 1/250s
                        8_000_000L,     // 1/125s
                        16_666_667L,    // 1/60s
                        33_333_333L,    // 1/30s
                        66_666_667L,    // 1/15s
                        125_000_000L,   // 1/8s
                        250_000_000L,   // 1/4s
                        500_000_000L,   // 1/2s
                        1_000_000_000L, // 1s
                        2_000_000_000L  // 2s
                    )
                    val selectedIdx = shutterList.indexOf(uiState.shutterSpeedNanos).takeIf { it >= 0 } ?: 6
                    DialWheelControl(
                        title = "Shutter Speed",
                        items = shutterList,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, nanos -> viewModel.setShutterSpeedNanos(nanos) },
                        formatItem = { HardwareCapabilities.formatShutterSpeed(it) }
                    )
                }
                DialParameter.FOCUS -> {
                    val maxDiopter = uiState.capabilities?.minFocusDistanceDiopters?.coerceAtLeast(1.0f) ?: 10.0f
                    val stepCount = 50
                    val focusSteps = (0..stepCount).map { (it.toFloat() / stepCount) * maxDiopter }
                    val selectedIdx = ((uiState.focusDistanceDiopters / maxDiopter) * stepCount).toInt().coerceIn(0, focusSteps.size - 1)
                    DialWheelControl(
                        title = "Manual Focus",
                        items = focusSteps,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, diopter -> viewModel.setFocusDistance(diopter) },
                        formatItem = { if (it == 0f) "∞ Infinity" else String.format("%.1f D", it) }
                    )
                }
                DialParameter.EV -> {
                    val evRange = uiState.capabilities?.evCompensationRange
                    val minEv = evRange?.lower ?: -6
                    val maxEv = evRange?.upper ?: 6
                    val evList = (minEv..maxEv).toList()
                    val selectedIdx = evList.indexOf(uiState.evCompensationIndex).takeIf { it >= 0 } ?: (evList.size / 2)
                    DialWheelControl(
                        title = "Exposure Compensation (EV)",
                        items = evList,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, ev -> viewModel.setEvCompensation(ev) },
                        formatItem = { if (it > 0) "+$it" else "$it" }
                    )
                }
                DialParameter.WHITE_BALANCE -> {
                    val kelvinList = (2500..8500 step 250).toList()
                    val selectedIdx = kelvinList.indexOf(uiState.kelvinTemperature).takeIf { it >= 0 } ?: 12
                    DialWheelControl(
                        title = "Kelvin Temperature",
                        items = kelvinList,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, k -> viewModel.setKelvin(k) },
                        formatItem = { "${it}K" }
                    )
                }
                null -> {}
            }

            // Parameter Bar (Mode selector + Parameter pills)
            ParameterBar(
                uiState = uiState,
                onParamClick = { param -> viewModel.setActiveDial(param) },
                onModeClick = { mode -> viewModel.setExposureMode(mode) },
                onToggleRaw = { viewModel.toggleRaw() }
            )

            // Bottom Shutter Bar with Thumbnail & Lock buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Quick Thumbnail Gallery Preview
                QuickThumbnail(
                    photoInfo = uiState.lastCapturedPhoto,
                    onClick = { viewModel.openReviewDialog() }
                )

                // Center: Big Shutter Trigger Button
                Box(
                    modifier = Modifier
                        .size(74.dp)
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

                // Right: AE-Lock / AF-Lock quick toggle buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isAeLocked) Color(0xFFFFD600) else Color(0x33FFFFFF))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleAeLock()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AEL",
                            color = if (uiState.isAeLocked) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isAfLocked) Color(0xFF00E676) else Color(0x33FFFFFF))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleAfLock()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AFL",
                            color = if (uiState.isAfLocked) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // 10. Fullscreen EXIF Review Dialog
        if (uiState.isReviewDialogVisible && uiState.lastCapturedPhoto != null) {
            ExifViewerDialog(
                photoInfo = uiState.lastCapturedPhoto!!,
                onDismiss = { viewModel.closeReviewDialog() }
            )
        }
    }
}
