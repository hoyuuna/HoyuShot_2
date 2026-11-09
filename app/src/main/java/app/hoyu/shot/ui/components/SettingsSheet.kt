package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.model.NoiseReductionMode
import app.hoyu.shot.model.SharpnessMode

@Composable
fun SettingsDialog(
    visible: Boolean,
    uiState: CameraUiState,
    rotationDegrees: Float = 0f,
    onToggleResumeSettings: () -> Unit,
    onCycleNoiseReduction: () -> Unit,
    onCycleSharpness: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleHorizon: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .rotate(rotationDegrees)
                .width(330.dp)
                .clip(shape)
                .background(Color(0xF509090B)) // shadcn zinc-950
                .border(1.dp, Color(0xFF27272A), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    letterSpacing = 1.sp
                )
                Text(
                    text = "[ESC]",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            // 1. Resume Settings Row (Entire row clickable with smooth animation!)
            val isResume = uiState.isResumeSettingsEnabled
            val resumeBgColor by animateColorAsState(
                targetValue = if (isResume) Color(0x33E11D48) else Color(0xFF18181B),
                label = "resumeBg"
            )
            val resumeBorderColor by animateColorAsState(
                targetValue = if (isResume) Color(0xFFE11D48) else Color(0xFF27272A),
                label = "resumeBorder"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(resumeBgColor)
                    .border(1.dp, resumeBorderColor, shape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleResumeSettings() // Click anywhere on the row!
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESUME SETTINGS",
                        color = if (isResume) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isResume) "Saves manual dials across app launches" else "Resets to Auto on boot",
                        color = Color(0xFF71717A),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Animated ON / OFF Pill
                val pillOffset by animateDpAsState(
                    targetValue = if (isResume) 0.dp else 0.dp,
                    label = "pillOffset"
                )
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isResume) Color(0xFFE11D48) else Color(0xFF27272A))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isResume) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 2. NR & SHARP Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Noise Reduction
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCycleNoiseReduction()
                        }
                        .padding(10.dp)
                ) {
                    Text(
                        text = "NOISE REDUCTION",
                        color = Color(0xFF71717A),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.noiseReductionMode.tag,
                        color = when (uiState.noiseReductionMode) {
                            NoiseReductionMode.OFF -> Color(0xFFE11D48)
                            NoiseReductionMode.FAST -> Color(0xFFFAFAFA)
                            NoiseReductionMode.HIGH_QUALITY -> Color(0xFF00E676)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Sharpness
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCycleSharpness()
                        }
                        .padding(10.dp)
                ) {
                    Text(
                        text = "SHARPNESS",
                        color = Color(0xFF71717A),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.sharpnessMode.tag,
                        color = when (uiState.sharpnessMode) {
                            SharpnessMode.OFF -> Color(0xFF71717A)
                            SharpnessMode.NORMAL -> Color(0xFFFAFAFA)
                            SharpnessMode.HIGH -> Color(0xFF00E676)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. Pro Aids Toggles (Grid, Peaking, Zebra, Horizon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfigToggleItem(
                    title = "HORIZON",
                    isActive = uiState.isHorizonLevelVisible,
                    onClick = onToggleHorizon,
                    modifier = Modifier.weight(1f)
                )
                ConfigToggleItem(
                    title = "PEAKING",
                    isActive = uiState.isFocusPeakingEnabled,
                    onClick = onTogglePeaking,
                    modifier = Modifier.weight(1f)
                )
                ConfigToggleItem(
                    title = "ZEBRA",
                    isActive = uiState.isZebraStripesEnabled,
                    onClick = onToggleZebra,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConfigToggleItem(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isActive) Color(0x2600E676) else Color(0xFF18181B))
            .border(1.dp, if (isActive) Color(0xFF00E676) else Color(0xFF27272A), shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isActive) Color(0xFF00E676) else Color(0xFF71717A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (isActive) "ON" else "OFF",
            color = if (isActive) Color(0xFFFAFAFA) else Color(0xFF52525B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
