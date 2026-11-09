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
import androidx.compose.ui.window.Dialog
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.model.NoiseReductionMode
import app.hoyu.shot.model.SharpnessMode

@Composable
fun SettingsDialog(
    uiState: CameraUiState,
    onToggleResumeSettings: () -> Unit,
    onCycleNoiseReduction: () -> Unit,
    onCycleSharpness: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleHorizon: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp) // shadcn razor-sharp corner

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(shape)
                .background(Color(0xFF09090B)) // shadcn zinc-950
                .border(1.dp, Color(0xFF27272A), shape) // zinc-800
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYS // CONFIG",
                    color = Color(0xFFFAFAFA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "[ESC]",
                    color = Color(0xFF71717A),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Resume Settings Row (Sticky Settings ON / OFF)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF27272A), shape)
                    .background(Color(0xFF18181B))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESUME SETTINGS",
                        color = Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (uiState.isResumeSettingsEnabled) "Preserves manual dials upon exit" else "Always boots fresh in Auto",
                        color = Color(0xFF71717A),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (uiState.isResumeSettingsEnabled) Color(0xFFFAFAFA) else Color(0xFF27272A))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleResumeSettings()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (uiState.isResumeSettingsEnabled) "ON" else "OFF",
                        color = if (uiState.isResumeSettingsEnabled) Color.Black else Color(0xFFA1A1AA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. NR & SHARP Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Noise Reduction
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF27272A), shape)
                        .background(Color(0xFF18181B))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCycleNoiseReduction()
                        }
                        .padding(10.dp)
                ) {
                    Text(text = "DENOISE (NR)", color = Color(0xFF71717A), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.noiseReductionMode.tag,
                        color = if (uiState.noiseReductionMode != NoiseReductionMode.OFF) Color(0xFFFAFAFA) else Color(0xFFE11D48),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Sharpness
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF27272A), shape)
                        .background(Color(0xFF18181B))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCycleSharpness()
                        }
                        .padding(10.dp)
                ) {
                    Text(text = "SHARPNESS", color = Color(0xFF71717A), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.sharpnessMode.tag,
                        color = if (uiState.sharpnessMode != SharpnessMode.OFF) Color(0xFFFAFAFA) else Color(0xFFE11D48),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Pro Tools 2x2 Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShadcnToggleTile(
                    label = "HISTOGRAM",
                    isActive = uiState.isHistogramVisible,
                    onClick = onToggleHistogram,
                    modifier = Modifier.weight(1f)
                )
                ShadcnToggleTile(
                    label = "HORIZON RETICLE",
                    isActive = uiState.isHorizonLevelVisible,
                    onClick = onToggleHorizon,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShadcnToggleTile(
                    label = "FOCUS PEAKING",
                    isActive = uiState.isFocusPeakingEnabled,
                    onClick = onTogglePeaking,
                    modifier = Modifier.weight(1f)
                )
                ShadcnToggleTile(
                    label = "ZEBRA HIGHLIGHT",
                    isActive = uiState.isZebraStripesEnabled,
                    onClick = onToggleZebra,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Path
            Text(
                text = "STORAGE -> /DCIM/HoyuShot",
                color = Color(0xFF52525B),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ShadcnToggleTile(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier = modifier
            .border(1.dp, if (isActive) Color(0xFF3F3F46) else Color(0xFF27272A), shape)
            .background(if (isActive) Color(0xFF27272A) else Color(0xFF18181B))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isActive) Color(0xFFFAFAFA) else Color(0xFFA1A1AA),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (isActive) "●" else "○",
            color = if (isActive) Color(0xFFE11D48) else Color(0xFF52525B),
            fontSize = 10.sp
        )
    }
}
