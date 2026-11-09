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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.ExposureMode
import kotlinx.coroutines.delay

@Composable
fun ModeDialSelectorModal(
    visible: Boolean,
    currentMode: ExposureMode,
    rotationDegrees: Float = 0f,
    onModeSelected: (ExposureMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
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
        modifier = modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .rotate(rotationDegrees)
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
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .heightIn(max = 260.dp) // Safe bounds preventing overflow in landscape
                    .clip(shape)
                    .background(Color(0xF509090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
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
                        val isSelected = currentMode == mode
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
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                }
            }
        }
    }
}
