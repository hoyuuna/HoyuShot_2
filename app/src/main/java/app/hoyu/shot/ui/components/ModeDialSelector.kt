package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CustomModePreset
import app.hoyu.shot.model.ExposureMode
import kotlinx.coroutines.delay

@Composable
fun ModeDialSelectorModal(
    visible: Boolean,
    currentMode: ExposureMode,
    customPresets: List<CustomModePreset> = emptyList(),
    activeCustomPresetId: String? = null,
    isBlurEnabled: Boolean = true,
    blurredBackdrop: ImageBitmap? = null,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onModeSelected: (ExposureMode) -> Unit,
    onCustomPresetSelected: (CustomModePreset) -> Unit = {},
    onRequestCreateCustom: () -> Unit = {},
    onRequestDeleteCustom: (CustomModePreset) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    // 8-second auto-close timer starting from the exact moment user lifts finger off the popup
    var isFingerTouching by remember { mutableStateOf(false) }
    var touchReleaseEpoch by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(touchReleaseEpoch, isFingerTouching, visible) {
        if (visible && !isFingerTouching) {
            delay(8000)
            onDismiss()
        }
    }

    val modes = listOf(
        ExposureMode.AUTO,
        ExposureMode.PROGRAM,
        ExposureMode.SHUTTER_PRIORITY,
        ExposureMode.ISO_PRIORITY,
        ExposureMode.MANUAL
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.88f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.88f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .rotateBounds(rotationDegrees)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val touching = event.changes.any { it.pressed }
                                if (touching != isFingerTouching) {
                                    isFingerTouching = touching
                                    if (!touching) {
                                        touchReleaseEpoch = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
            PopupGlassContainer(
                shape = shape,
                isBlurEnabled = isBlurEnabled,
                blurredBackdrop = blurredBackdrop,
                previewBoundsInRoot = previewBoundsInRoot,
                rotationDegrees = rotationDegrees,
                modifier = Modifier
                    .width(288.dp)
                    .heightIn(max = 330.dp) // Safe bounds preventing overflow in landscape
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SHOOTING // MODE",
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

                Spacer(modifier = Modifier.height(2.dp))

                // Scrollable list of shooting modes with safe margins
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.forEach { mode ->
                        val isSelected = activeCustomPresetId == null && currentMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isSelected) Color(0x33E11D48) else Color(0xFF18181B))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                    shape
                                )
                                .clickable {
                                    view.triggerUiHaptic(uiHapticsEnabled)
                                    onModeSelected(mode)
                                    onDismiss() // Selects and applies immediately!
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Mode Tag Badge
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(shape)
                                    .background(if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Mode Description
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.name.replace("_", " "),
                                    color = if (isSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = when (mode) {
                                        ExposureMode.AUTO -> "Full automatic exposure"
                                        ExposureMode.PROGRAM -> "Auto shutter & ISO with manual EV"
                                        ExposureMode.SHUTTER_PRIORITY -> "Manual shutter speed, auto ISO"
                                        ExposureMode.ISO_PRIORITY -> "Manual sensor gain, auto shutter"
                                        ExposureMode.MANUAL -> "Full manual control"
                                    },
                                    color = Color(0xFF71717A),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Create Custom Preset button right below M (MANUAL)
                    val isEligibleMode = currentMode.isCustomPresetEligible()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFF141418))
                            .border(
                                1.dp,
                                if (isEligibleMode) Color(0xFF52525B) else Color(0xFF27272A),
                                shape
                            )
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onRequestCreateCustom()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(shape)
                                .background(if (isEligibleMode) Color(0xFF27272A) else Color(0xFF18181B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = if (isEligibleMode) Color(0xFFFAFAFA) else Color(0xFF71717A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CREATE CUSTOM MODE",
                                color = if (isEligibleMode) Color(0xFFFAFAFA) else Color(0xFFA1A1AA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isEligibleMode) {
                                    "Save current ${currentMode.label} settings as preset"
                                } else {
                                    "Requires S, ISO, or M mode"
                                },
                                color = Color(0xFF71717A),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (customPresets.isNotEmpty()) {
                        Text(
                            text = "CUSTOM // PRESETS (${customPresets.size})",
                            color = Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )

                        customPresets.forEachIndexed { idx, preset ->
                            val isPresetSelected = activeCustomPresetId == preset.id
                            val badgeText = "C${idx + 1}"
                            val summary = formatCustomPresetSummary(
                                shutterSpeedNanos = preset.shutterSpeedNanos,
                                iso = preset.iso,
                                whiteBalancePreset = preset.whiteBalancePreset,
                                kelvinTemperature = preset.kelvinTemperature,
                                aspectRatioName = preset.aspectRatio.ratioName,
                                captureFormatLabel = preset.captureFormat.label
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(if (isPresetSelected) Color(0x33E11D48) else Color(0xFF18181B))
                                    .border(
                                        1.dp,
                                        if (isPresetSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                        shape
                                    )
                                    .clickable {
                                        view.triggerUiHaptic(uiHapticsEnabled)
                                        onCustomPresetSelected(preset)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(shape)
                                        .background(if (isPresetSelected) Color(0xFFE11D48) else Color(0xFF27272A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = if (badgeText.length > 2) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = preset.name,
                                            color = if (isPresetSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "[${preset.baseMode.label}]",
                                            color = Color(0xFFA1A1AA),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = summary,
                                        color = Color(0xFF71717A),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(shape)
                                        .background(Color(0xFF27272A))
                                        .clickable {
                                            view.triggerUiHaptic(uiHapticsEnabled)
                                            onRequestDeleteCustom(preset)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "DEL",
                                        color = Color(0xFFFF4D6D),
                                        fontSize = 8.sp,
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
        }
        }
    }
}
