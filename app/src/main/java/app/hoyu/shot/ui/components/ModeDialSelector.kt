package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.ExposureMode

@Composable
fun ModeDialSelectorModal(
    currentMode: ExposureMode,
    onModeSelected: (ExposureMode) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }

    val modes = listOf(
        Pair(ExposureMode.AUTO, "Auto"),
        Pair(ExposureMode.PROGRAM, "Program (P)"),
        Pair(ExposureMode.MANUAL, "Manual (M)"),
        Pair(ExposureMode.SHUTTER_PRIORITY, "Shutter (S)"),
        Pair(ExposureMode.ISO_PRIORITY, "ISO Priority")
    )

    val description = when (selectedMode) {
        ExposureMode.AUTO -> "White balance, ISO and shutter speed are automatically chosen."
        ExposureMode.PROGRAM -> "Camera sets ISO and Shutter automatically. You can freely adjust EV compensation."
        ExposureMode.SHUTTER_PRIORITY -> "You set the exact Shutter speed. Camera automatically balances ISO."
        ExposureMode.ISO_PRIORITY -> "You set the exact ISO sensitivity. Camera automatically balances Shutter speed."
        ExposureMode.MANUAL -> "Full manual control over Shutter speed, ISO sensitivity, Focus distance, and White balance."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .padding(16.dp)
    ) {
        // Mode Selector Arc / Circular Dial in Top-Right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xD9121212))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { (mode, name) ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFFFFD600) else Color(0x22FFFFFF))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            selectedMode = mode
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = name,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                    )
                }
            }
        }

        // Center Description Card
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
                .clickable { /* prevent click-through */ }
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedMode.label,
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    // Resume / Confirm button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onModeSelected(selectedMode)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "SET | ${selectedMode.label}",
                            color = Color(0xFFFFD600),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x33000000))
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
