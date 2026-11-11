package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun DedicatedBottomEvBar(
    evIndex: Int,
    minEv: Int = -6,
    maxEv: Int = 6,
    rotationDegrees: Float = 0f,
    onEvChanged: (Int) -> Unit,
    onResetEv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val totalSteps = (maxEv - minEv).coerceAtLeast(1)

    // Direct 1:1 finger tracking fraction (null when not touching)
    var activeTouchFraction by remember { mutableStateOf<Float?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(Color(0xFF18181B)) // solid zinc-900 block
            .border(1.dp, if (evIndex != 0) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Minus Button
        Text(
            text = "−",
            color = Color(0xFFFAFAFA),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable {
                    if (evIndex > minEv) {
                        view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                        onEvChanged(evIndex - 1)
                    }
                }
                .padding(horizontal = 4.dp)
        )

        // Direct 1:1 finger-following Draggable Ruler Track
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(minEv, maxEv) {
                    detectTapGestures { offset ->
                        val trackW = size.width - 16f
                        val fraction = ((offset.x - 8f) / trackW).coerceIn(0f, 1f)
                        val step = (minEv + (fraction * totalSteps).roundToInt()).coerceIn(minEv, maxEv)
                        if (step != evIndex) {
                            view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                            onEvChanged(step)
                        }
                    }
                }
                .pointerInput(minEv, maxEv) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val trackW = size.width - 16f
                            val fraction = ((offset.x - 8f) / trackW).coerceIn(0f, 1f)
                            activeTouchFraction = fraction
                            val step = (minEv + (fraction * totalSteps).roundToInt()).coerceIn(minEv, maxEv)
                            if (step != evIndex) {
                                view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                                onEvChanged(step)
                            }
                        },
                        onDragEnd = {
                            activeTouchFraction = null
                        },
                        onDragCancel = {
                            activeTouchFraction = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val trackW = size.width - 16f
                            val fraction = ((change.position.x - 8f) / trackW).coerceIn(0f, 1f)
                            activeTouchFraction = fraction
                            val step = (minEv + (fraction * totalSteps).roundToInt()).coerceIn(minEv, maxEv)
                            if (step != evIndex) {
                                view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                                onEvChanged(step)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2f
                val paddingX = 8f
                val trackWidth = width - 2 * paddingX

                // Base ruler guide
                drawLine(
                    color = Color(0xFF27272A),
                    start = Offset(paddingX, centerY),
                    end = Offset(width - paddingX, centerY),
                    strokeWidth = 3f
                )

                // Tick marks along the track
                for (i in minEv..maxEv) {
                    val f = (i - minEv).toFloat() / totalSteps.toFloat()
                    val x = paddingX + f * trackWidth
                    val isZero = i == 0
                    val tickHeight = if (isZero) 14f else 8f
                    drawLine(
                        color = if (isZero) Color(0xFFE11D48) else Color(0xFF52525B),
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isZero) 2.5f else 1.5f
                    )
                }

                // Current EV Thumb (Tracks finger directly when dragging, otherwise snaps to evIndex)
                val targetFraction = activeTouchFraction ?: ((evIndex - minEv).toFloat() / totalSteps.toFloat())
                val thumbX = paddingX + targetFraction * trackWidth
                val thumbW = 24f
                val thumbH = 20f

                drawRoundRect(
                    color = if (evIndex != 0) Color(0xFFE11D48) else Color(0xFFFAFAFA),
                    topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbW, thumbH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                )
            }
        }

        // Plus Button
        Text(
            text = "+",
            color = Color(0xFFFAFAFA),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable {
                    if (evIndex < maxEv) {
                        view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                        onEvChanged(evIndex + 1)
                    }
                }
                .padding(horizontal = 4.dp)
        )

        // Reset Badge (Clickable to reset EV to 0)
        val sign = if (evIndex > 0) "+$evIndex" else "$evIndex"
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (evIndex != 0) Color(0x33E11D48) else Color(0xFF27272A))
                .border(1.dp, if (evIndex != 0) Color(0xFFE11D48) else Color(0xFF3F3F46), shape)
                .clickable(enabled = evIndex != 0) {
                    view.triggerUiHaptic(uiHapticsEnabled)
                    onResetEv()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (evIndex == 0) "0 EV" else "$sign EV",
                color = if (evIndex != 0) Color(0xFFFF4D6D) else Color(0xFFA1A1AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

fun Modifier.rotateBounds(degrees: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val normDeg = ((degrees % 360f) + 360f) % 360f
    val isRotated = (normDeg in 45f..135f) || (normDeg in 225f..315f)
    val layoutW = if (isRotated) placeable.height else placeable.width
    val layoutH = if (isRotated) placeable.width else placeable.height
    layout(layoutW, layoutH) {
        placeable.placeRelativeWithLayer(
            x = (layoutW - placeable.width) / 2,
            y = (layoutH - placeable.height) / 2
        ) {
            rotationZ = degrees
        }
    }
}

@Composable
fun EvPositionAlertBadge(
    evIndex: Int,
    rotationDegrees: Float = 0f,
    onResetEv: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (evIndex == 0) return

    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val sign = if (evIndex > 0) "+$evIndex" else "$evIndex"

    Box(
        modifier = modifier
            .rotateBounds(rotationDegrees)
            .clip(shape)
            .background(Color(0xD909090B)) // Translucent Nothing Dark
            .border(1.dp, Color(0xFFE11D48), shape) // Nothing Red border
            .clickable {
                view.triggerUiHaptic(uiHapticsEnabled)
                onResetEv() // Resets EV to 0 on tap as requested!
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "EV IN POSITION $sign",
                color = Color(0xFFFF4D6D),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "[RESET]",
                color = Color(0xFFFAFAFA),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
