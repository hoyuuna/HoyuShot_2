package app.hoyu.shot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.model.DialParameter
import app.hoyu.shot.model.ExposureMode
import app.hoyu.shot.model.HardwareCapabilities

@Composable
fun ParameterBar(
    uiState: CameraUiState,
    onParamClick: (DialParameter) -> Unit,
    onModeClick: (ExposureMode) -> Unit,
    onToggleRaw: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xD9000000))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Mode Selector Bar (AUTO, P, S, ISO, M)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposureMode.values().forEach { mode ->
                val isSelected = uiState.exposureMode == mode
                Text(
                    text = mode.label,
                    color = if (isSelected) Color(0xFFFFD600) else Color(0x88FFFFFF),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onModeClick(mode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // RAW toggle button
            val rawActive = uiState.captureRaw
            Box(
                modifier = Modifier
                    .border(
                        1.dp,
                        if (rawActive) Color(0xFFFFD600) else Color(0x44FFFFFF),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (rawActive) Color(0x33FFD600) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleRaw() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "RAW",
                    color = if (rawActive) Color(0xFFFFD600) else Color(0x88FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Parameter Pills: ISO, Shutter, Focus, EV, WB
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO Pill
            ParameterPill(
                label = "ISO",
                value = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY) {
                    "${uiState.actualIso}"
                } else {
                    "${uiState.iso}"
                },
                isActive = uiState.activeDialParameter == DialParameter.ISO,
                onClick = { onParamClick(DialParameter.ISO) }
            )

            // Shutter Speed Pill
            val shutterStr = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY) {
                HardwareCapabilities.formatShutterSpeed(uiState.actualShutterSpeedNanos)
            } else {
                HardwareCapabilities.formatShutterSpeed(uiState.shutterSpeedNanos)
            }
            ParameterPill(
                label = "SEC",
                value = shutterStr,
                isActive = uiState.activeDialParameter == DialParameter.SHUTTER,
                onClick = { onParamClick(DialParameter.SHUTTER) }
            )

            // Focus Pill
            val focusStr = if (uiState.isManualFocusEnabled) {
                String.format("%.1f D", uiState.focusDistanceDiopters)
            } else "AF"
            ParameterPill(
                label = "FOCUS",
                value = focusStr,
                isActive = uiState.activeDialParameter == DialParameter.FOCUS,
                onClick = { onParamClick(DialParameter.FOCUS) }
            )

            // EV Pill
            val evStr = if (uiState.evCompensationIndex > 0) "+${uiState.evCompensationIndex}" else "${uiState.evCompensationIndex}"
            ParameterPill(
                label = "EV",
                value = evStr,
                isActive = uiState.activeDialParameter == DialParameter.EV,
                onClick = { onParamClick(DialParameter.EV) }
            )

            // WB Pill
            val wbStr = if (uiState.whiteBalancePreset == app.hoyu.shot.model.WhiteBalancePreset.CUSTOM_KELVIN) {
                "${uiState.kelvinTemperature}K"
            } else uiState.whiteBalancePreset.label
            ParameterPill(
                label = "WB",
                value = wbStr,
                isActive = uiState.activeDialParameter == DialParameter.WHITE_BALANCE,
                onClick = { onParamClick(DialParameter.WHITE_BALANCE) }
            )
        }
    }
}

@Composable
private fun ParameterPill(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) Color(0xFFFFD600) else Color(0x66FFFFFF),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = if (isActive) Color(0xFFFFD600) else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

