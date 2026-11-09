package app.hoyu.shot.ui

import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.hoyu.shot.model.AspectRatio
import app.hoyu.shot.model.DialParameter
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.ui.components.*

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var surfaceViewRef by remember { mutableStateOf<AutoFitSurfaceView?>(null) }

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
            .pointerInput(uiState.currentZoomRatio) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1.0f) {
                        viewModel.setZoomRatio(uiState.currentZoomRatio * zoom)
                    }
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
                update = { view ->
                    val (camWidth, camHeight) = uiState.previewSize
                    view.setAspectRatio(camHeight, camWidth)
                }
            )
        }

        // 2. Electronic Horizon Level Overlay
        if (uiState.isHorizonLevelVisible) {
            HorizonLevelIndicator(
                orientation = uiState.horizon,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Top HUD: App Tag, Aspect Ratio Switcher & Live Histogram
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Hardware capability & Active Lens tag
            val caps = uiState.capabilities
            Column {
                Text(
                    text = "HOYUSHOT",
                    color = Color(0xFFFFD600),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                if (caps != null) {
                    val activeLabel = uiState.currentLens?.label ?: "1x"
                    Text(
                        text = "$activeLabel | ${caps.hardwareLevelName} | ${if (caps.supportsRawCapture) "RAW" else "NO RAW"}",
                        color = if (caps.isLevelFullOrHigher) Color(0xFF00E676) else Color(0xFFFF9100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Aspect Ratio Selector (4:3, 16:9, 1:1)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x80000000))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9, AspectRatio.RATIO_1_1).forEach { ratio ->
                    val isSelected = uiState.aspectRatio == ratio
                    Text(
                        text = ratio.ratioName,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0x88FFFFFF),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { viewModel.setAspectRatio(ratio) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Real-time RGB / Luma Histogram
            if (uiState.isHistogramVisible) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // 4. Error banner
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color(0xD9D50000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = error, color = Color.White, fontSize = 12.sp)
            }
        }

        // 5. Bottom Controls Group
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lens Switcher (e.g. 0.6x, 1x, 3.2x, Front)
            if (uiState.availableLenses.size > 1) {
                LensSelector(
                    lenses = uiState.availableLenses,
                    currentLens = uiState.currentLens,
                    onLensSelect = { selectedLens -> viewModel.selectLens(selectedLens) },
                    onFlipFacing = { viewModel.toggleCameraFacing() },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Dial Wheel Slider
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

            // Shutter Button Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(3.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .background(if (uiState.isCapturing) Color.Gray else Color.White, CircleShape)
                        .clickable(enabled = !uiState.isCapturing) {
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
            }
        }
    }
}
