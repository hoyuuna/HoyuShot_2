package app.hoyu.shot.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.hoyu.shot.model.DialParameter
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.WhiteBalancePreset
import app.hoyu.shot.ui.components.DialWheelControl
import app.hoyu.shot.ui.components.HistogramView
import app.hoyu.shot.ui.components.HorizonLevelIndicator
import app.hoyu.shot.ui.components.ParameterBar

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startSensors()
        onDispose {
            viewModel.stopSensors()
            viewModel.stopCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camera2 Viewfinder via AndroidView & SurfaceView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
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
            }
        )

        // 2. Electronic Horizon Level Overlay
        if (uiState.isHorizonLevelVisible) {
            HorizonLevelIndicator(
                orientation = uiState.horizon,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Top HUD: Hardware Level Tag & Live Histogram
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Hardware capability tag
            val caps = uiState.capabilities
            Column {
                Text(
                    text = "PROSHOT",
                    color = Color(0xFFFFD600),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                if (caps != null) {
                    Text(
                        text = "LEVEL: ${caps.hardwareLevelName} | ${if (caps.supportsRawCapture) "RAW OK" else "NO RAW"}",
                        color = if (caps.isLevelFullOrHigher) Color(0xFF00E676) else Color(0xFFFF9100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real-time RGB / Luma Histogram
            if (uiState.isHistogramVisible) {
                HistogramView(histogram = uiState.histogram)
            }
        }

        // 4. Error banner if any
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
            // Dial Wheel Slider (Appears when user taps ISO / Shutter / Focus / EV / WB)
            when (uiState.activeDialParameter) {
                DialParameter.ISO -> {
                    val isoList = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400)
                    val selectedIdx = isoList.indexOf(uiState.iso).takeIf { it >= 0 } ?: 3
                    DialWheelControl(
                        title = "ISO Sensitivity",
                        items = isoList,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, value -> viewModel.setIso(value) },
                        formatItem = { "$it" }
                    )
                }
                DialParameter.SHUTTER -> {
                    // Shutter speeds from 1/8000s up to 2 seconds
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
                    val focusSteps = (0..50).map { it / 5.0f } // 0.0 to 10.0 Diopters
                    val selectedIdx = (uiState.focusDistanceDiopters * 5).toInt().coerceIn(0, focusSteps.size - 1)
                    DialWheelControl(
                        title = "Manual Focus (Diopters)",
                        items = focusSteps,
                        selectedIndex = selectedIdx,
                        onItemSelected = { _, diopter -> viewModel.setFocusDistance(diopter) },
                        formatItem = { if (it == 0f) "∞ Macro" else String.format("%.1f D", it) }
                    )
                }
                DialParameter.EV -> {
                    val evList = (-6..6).toList()
                    val selectedIdx = (uiState.evCompensationIndex + 6).coerceIn(0, evList.size - 1)
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
                // Large circular Shutter Trigger
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

