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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.model.NoiseReductionMode
import app.hoyu.shot.model.SharpnessMode
import app.hoyu.shot.model.TimerMode

@Composable
fun SettingsDialog(
    visible: Boolean,
    uiState: CameraUiState,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onToggleResumeSettings: () -> Unit,
    onToggleGeotagging: () -> Unit,
    onSelectTimerMode: (TimerMode) -> Unit,
    onCycleNoiseReduction: () -> Unit,
    onCycleSharpness: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleHorizon: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onToggleSunCompass: () -> Unit = {},
    onOpenAdvancedSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.rotateBounds(rotationDegrees),
                contentAlignment = Alignment.Center
            ) {
            PopupGlassContainer(
                shape = shape,
                isBlurEnabled = uiState.isUiBlurEnabled,
                blurredBackdrop = uiState.blurredBackdrop,
                previewBoundsInRoot = previewBoundsInRoot,
                rotationDegrees = rotationDegrees,
                modifier = Modifier
                    .width(320.dp)
                    .heightIn(max = 340.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                            view.triggerUiHaptic(uiHapticsEnabled)
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

                // Geotagging GPS Toggle
                val isGeo = uiState.isGeotaggingEnabled
                val geoBgColor by animateColorAsState(
                    targetValue = if (isGeo) Color(0x33E11D48) else Color(0xFF18181B),
                    label = "geoBg"
                )
                val geoBorderColor by animateColorAsState(
                    targetValue = if (isGeo) Color(0xFFE11D48) else Color(0xFF27272A),
                    label = "geoBorder"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(geoBgColor)
                        .border(1.dp, geoBorderColor, shape)
                        .clickable {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onToggleGeotagging()
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GEOTAGGING // GPS",
                            color = if (isGeo) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isGeo) "Embed GPS coordinates into EXIF" else "Location tags disabled",
                            color = Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isGeo) Color(0xFFE11D48) else Color(0xFF27272A))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isGeo) "ON" else "OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 2. NR & SHARP Controls (Màu xám/trắng nếu tắt, hiện số và chế độ màu đỏ nếu đã chỉnh)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Noise Reduction
                    val isNrActive = uiState.noiseReductionMode != NoiseReductionMode.OFF
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (isNrActive) Color(0x22E11D48) else Color(0xFF18181B))
                            .border(1.dp, if (isNrActive) Color(0xFFE11D48) else Color(0xFF27272A), shape)
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onCycleNoiseReduction()
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "NOISE REDUCTION",
                            color = if (isNrActive) Color(0xFFFF4D6D) else Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (uiState.noiseReductionMode) {
                                NoiseReductionMode.OFF -> "OFF"
                                NoiseReductionMode.FAST -> "MIN"
                                NoiseReductionMode.HIGH_QUALITY -> "HQ"
                            },
                            color = if (isNrActive) Color(0xFFE11D48) else Color(0xFF71717A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Sharpness
                    val isSharpActive = uiState.sharpnessMode != SharpnessMode.OFF
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (isSharpActive) Color(0x22E11D48) else Color(0xFF18181B))
                            .border(1.dp, if (isSharpActive) Color(0xFFE11D48) else Color(0xFF27272A), shape)
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onCycleSharpness()
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "SHARPNESS",
                            color = if (isSharpActive) Color(0xFFFF4D6D) else Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (uiState.sharpnessMode) {
                                SharpnessMode.OFF -> "OFF"
                                SharpnessMode.NORMAL -> "0"
                                SharpnessMode.HIGH -> "+1"
                            },
                            color = if (isSharpActive) Color(0xFFE11D48) else Color(0xFF71717A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Self-Timer Delay (OFF - 1S - 2S - 5S - 10S)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELF TIMER // DELAY",
                            color = if (uiState.timerMode != TimerMode.OFF) Color(0xFFFF4D6D) else Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.timerMode.label,
                            color = if (uiState.timerMode != TimerMode.OFF) Color(0xFFE11D48) else Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimerMode.entries.forEach { mode ->
                            val isSelected = uiState.timerMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (isSelected) Color(0x33E11D48) else Color(0xFF09090B))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                        shape
                                    )
                                    .clickable {
                                        view.triggerUiHaptic(uiHapticsEnabled)
                                        onSelectTimerMode(mode)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color(0xFFFAFAFA) else Color(0xFF71717A),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // 4. Pro Viewfinder Aids Toggles (HISTOGRAM, HORIZON, PEAKING, ZEBRA)
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

                // Separate row for Sun Compass HUD (defaults OFF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigToggleItem(
                        title = "SUN COMPASS",
                        isActive = uiState.isSunCompassEnabled,
                        onClick = onToggleSunCompass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Advanced Settings Entry Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .clickable {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onOpenAdvancedSettings()
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SYS // ADVANCED CONFIG",
                            color = Color(0xFFFAFAFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "JPEG quality, gestures, hardware keys, antibanding & updater",
                            color = Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "OPEN >",
                        color = Color(0xFFE11D48),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
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
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isActive) Color(0x33E11D48) else Color(0xFF18181B))
            .border(1.dp, if (isActive) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .clickable {
                view.triggerUiHaptic(uiHapticsEnabled)
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
