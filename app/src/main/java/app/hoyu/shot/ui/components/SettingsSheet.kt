package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
        modifier = modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier.rotate(rotationDegrees),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .heightIn(max = 280.dp)
                    .clip(shape)
                    .background(Color(0xF509090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(14.dp)
                    .verticalScroll(scrollState),
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

                // 1. Resume Settings Row (Entire row clickable)
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
                            onToggleResumeSettings()
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

                // 2. NR & SHARP Controls (Strictly Nothing Red palette)
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
                                NoiseReductionMode.OFF -> Color(0xFF71717A)
                                NoiseReductionMode.FAST -> Color(0xFFFAFAFA)
                                NoiseReductionMode.HIGH_QUALITY -> Color(0xFFE11D48)
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
                                SharpnessMode.HIGH -> Color(0xFFE11D48)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Pro Viewfinder Aids Toggles (HORIZON, PEAKING, ZEBRA)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigToggleItem(
                        title = "HISTOGRAM",
                        isActive = uiState.isHistogramVisible,
                        onClick = onToggleHistogram,
                        modifier = Modifier.weight(1f)
                    )
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
            .background(if (isActive) Color(0x33E11D48) else Color(0xFF18181B))
            .border(1.dp, if (isActive) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isActive) Color(0xFFFF4D6D) else Color(0xFF71717A),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isActive) "ON" else "OFF",
            color = if (isActive) Color(0xFFFAFAFA) else Color(0xFF52525B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
