package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.*

@Composable
fun HorizontalParameterStrip(
    uiState: CameraUiState,
    activeParam: DialParameter?,
    rotationDegrees: Float,
    onParamClick: (DialParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current

    val isFocusActive = activeParam == DialParameter.FOCUS
    val isFocusManual = uiState.isManualFocusEnabled
    val focusText = if (isFocusManual) {
        String.format("%.1fD", uiState.focusDistanceDiopters)
    } else "AF"

    val isShutterActive = activeParam == DialParameter.SHUTTER
    val isShutterManual = uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY || uiState.exposureMode == ExposureMode.MANUAL
    val shutterText = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY) {
        HardwareCapabilities.formatShutterSpeed(uiState.actualShutterSpeedNanos)
    } else {
        HardwareCapabilities.formatShutterSpeed(uiState.shutterSpeedNanos)
    }

    val isIsoActive = activeParam == DialParameter.ISO
    val isIsoManual = uiState.exposureMode == ExposureMode.ISO_PRIORITY || uiState.exposureMode == ExposureMode.MANUAL
    val isoText = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY) {
        "${uiState.actualIso}"
    } else {
        "${uiState.iso}"
    }

    val isWbActive = activeParam == DialParameter.WHITE_BALANCE
    val isWbManual = uiState.whiteBalancePreset != WhiteBalancePreset.AUTO
    val wbText = if (uiState.whiteBalancePreset == WhiteBalancePreset.CUSTOM_KELVIN) {
        "${uiState.kelvinTemperature}K"
    } else {
        uiState.whiteBalancePreset.label
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StripItem(
            label = "AF",
            value = focusText,
            isActive = isFocusActive,
            isManual = isFocusManual,
            rotationDegrees = rotationDegrees,
            onClick = {
                view.triggerUiHaptic(uiHapticsEnabled)
                onParamClick(DialParameter.FOCUS)
            },
            modifier = Modifier.weight(1f)
        )
        StripItem(
            label = "SEC",
            value = shutterText,
            isActive = isShutterActive,
            isManual = isShutterManual,
            hasLimit = false,
            rotationDegrees = rotationDegrees,
            onClick = {
                view.triggerUiHaptic(uiHapticsEnabled)
                onParamClick(DialParameter.SHUTTER)
            },
            modifier = Modifier.weight(1f)
        )
        StripItem(
            label = "ISO",
            value = isoText,
            isActive = isIsoActive,
            isManual = isIsoManual,
            hasLimit = false,
            rotationDegrees = rotationDegrees,
            onClick = {
                view.triggerUiHaptic(uiHapticsEnabled)
                onParamClick(DialParameter.ISO)
            },
            modifier = Modifier.weight(1f)
        )
        StripItem(
            label = "WB",
            value = wbText,
            isActive = isWbActive,
            isManual = isWbManual,
            rotationDegrees = rotationDegrees,
            onClick = {
                view.triggerUiHaptic(uiHapticsEnabled)
                onParamClick(DialParameter.WHITE_BALANCE)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StripItem(
    label: String,
    value: String,
    isActive: Boolean,
    isManual: Boolean,
    hasLimit: Boolean = false,
    rotationDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(2.dp)

    val borderColor = when {
        isActive -> Color.White
        isManual -> Color(0xFFE11D48)
        else -> Color(0xFF27272A)
    }

    val backgroundColor = when {
        isActive -> Color(0xFFFAFAFA)
        isManual -> Color(0x33E11D48)
        else -> Color(0x33000000)
    }

    // Adaptive font size so parameters never overflow their container when rotated
    val fontSize = when {
        value.length > 7 -> 8.sp
        value.length > 5 -> 9.sp
        value.length > 4 -> 10.sp
        else -> 11.sp
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.rotate(rotationDegrees),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    color = if (isActive) Color(0xFF71717A) else if (isManual) Color(0xFFFF4D6D) else Color(0xFF71717A),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (hasLimit) {
                    Text(
                        text = "L",
                        color = Color(0xFFE11D48),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (isManual && !isActive) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(Color(0xFFE11D48), RoundedCornerShape(1.dp))
                    )
                }
            }
            Text(
                text = value,
                color = if (isActive) Color.Black else if (isManual) Color(0xFFFAFAFA) else Color(0xFFE4E4E7),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
