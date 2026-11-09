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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.*

@Composable
fun ParameterStrip(
    uiState: CameraUiState,
    activeParam: DialParameter?,
    onParamClick: (DialParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .width(58.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Focus Pill
        val isFocusActive = activeParam == DialParameter.FOCUS
        val focusText = if (uiState.isManualFocusEnabled) {
            String.format("%.1f D", uiState.focusDistanceDiopters)
        } else "AF"

        StripItem(
            label = "AF",
            value = focusText,
            isActive = isFocusActive,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onParamClick(DialParameter.FOCUS)
            }
        )

        // 2. Shutter Speed Pill
        val isShutterActive = activeParam == DialParameter.SHUTTER
        val shutterText = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.ISO_PRIORITY) {
            HardwareCapabilities.formatShutterSpeed(uiState.actualShutterSpeedNanos)
        } else {
            HardwareCapabilities.formatShutterSpeed(uiState.shutterSpeedNanos)
        }

        StripItem(
            label = "SEC",
            value = shutterText,
            isActive = isShutterActive,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onParamClick(DialParameter.SHUTTER)
            }
        )

        // 3. ISO Pill
        val isIsoActive = activeParam == DialParameter.ISO
        val isoText = if (uiState.exposureMode == ExposureMode.AUTO || uiState.exposureMode == ExposureMode.SHUTTER_PRIORITY) {
            "${uiState.actualIso}"
        } else {
            "${uiState.iso}"
        }

        StripItem(
            label = "ISO",
            value = isoText,
            isActive = isIsoActive,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onParamClick(DialParameter.ISO)
            }
        )

        // 4. White Balance Pill
        val isWbActive = activeParam == DialParameter.WHITE_BALANCE
        val wbText = if (uiState.whiteBalancePreset == WhiteBalancePreset.CUSTOM_KELVIN) {
            "${uiState.kelvinTemperature}K"
        } else {
            uiState.whiteBalancePreset.label
        }

        StripItem(
            label = "WB",
            value = wbText,
            isActive = isWbActive,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onParamClick(DialParameter.WHITE_BALANCE)
            }
        )
    }
}

@Composable
private fun StripItem(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isActive) Color(0x66FFFFFF) else Color(0x33000000))
            .border(
                1.dp,
                if (isActive) Color.White else Color(0x33FFFFFF),
                shape
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Black else Color(0x88FFFFFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
