package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.ExposureMode

@Composable
fun ModeDialSelectorModal(
    currentMode: ExposureMode,
    isResumeEnabled: Boolean,
    onToggleResume: () -> Unit,
    onModeSelected: (ExposureMode) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    val shape = RoundedCornerShape(2.dp)

    val modes = listOf(
        ExposureMode.AUTO,
        ExposureMode.PROGRAM,
        ExposureMode.SHUTTER_PRIORITY,
        ExposureMode.ISO_PRIORITY,
        ExposureMode.MANUAL
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .padding(16.dp)
    ) {
        // Mode Selector Strip in Top-Right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(shape)
                .background(Color(0xFF09090B))
                .border(1.dp, Color(0xFF27272A), shape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(34.dp)
                        .clip(shape)
                        .background(if (isSelected) Color(0xFFFAFAFA) else Color(0xFF18181B))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            selectedMode = mode
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = mode.fullName.uppercase(),
                            color = if (isSelected) Color(0xFF52525B) else Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Center Description Card (Nothing OS Brutalist style)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 360.dp)
                .clip(shape)
                .background(Color(0xFF09090B))
                .border(1.dp, Color(0xFF27272A), shape)
                .padding(16.dp)
                .clickable { /* prevent click-through */ }
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedMode.label,
                            color = Color(0xFFFAFAFA),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "// ${selectedMode.fullName.uppercase()}",
                            color = Color(0xFF71717A),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Resume settings toggle pill (exactly like ProShot's "[ Resume settings | AUTO ]")
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .border(1.dp, if (isResumeEnabled) Color(0xFFE11D48) else Color(0xFF3F3F46), shape)
                            .background(if (isResumeEnabled) Color(0x33E11D48) else Color(0xFF18181B))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleResume()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STICKY: ${if (isResumeEnabled) "ON" else "OFF"}",
                            color = if (isResumeEnabled) Color(0xFFFAFAFA) else Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF27272A))
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = selectedMode.description,
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Apply button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(shape)
                        .background(Color(0xFFFAFAFA))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onModeSelected(selectedMode)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "APPLY [${selectedMode.label}]",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
