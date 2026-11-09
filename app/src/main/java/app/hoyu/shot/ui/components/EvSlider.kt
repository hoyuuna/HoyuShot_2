package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val shape = RoundedCornerShape(2.dp)
    val totalSteps = (maxEv - minEv).coerceAtLeast(1)

    // A prominent, solid bar at the bottom with the height of a camera lens button (~38dp)
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
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onEvChanged(evIndex - 1)
                    }
                }
                .padding(horizontal = 4.dp)
        )

        // Thick, prominent Draggable Ruler Track
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(minEv, maxEv, evIndex) {
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            val width = size.width
                            val stepPx = width / totalSteps.toFloat()
                            val deltaSteps = (accumulated / stepPx).toInt()
                            if (deltaSteps != 0) {
                                val next = (evIndex + deltaSteps).coerceIn(minEv, maxEv)
                                if (next != evIndex) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onEvChanged(next)
                                    accumulated = 0f
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2f

                // Base ruler guide
                drawLine(
                    color = Color(0xFF27272A),
                    start = Offset(4f, centerY),
                    end = Offset(width - 4f, centerY),
                    strokeWidth = 4f
                )

                // Tick marks along the track
                for (i in minEv..maxEv) {
                    val f = (i - minEv).toFloat() / totalSteps.toFloat()
                    val x = 4f + f * (width - 8f)
                    val isZero = i == 0
                    val tickHeight = if (isZero) 14f else 8f
                    drawLine(
                        color = if (isZero) Color(0xFFE11D48) else Color(0xFF52525B),
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isZero) 2.5f else 1.5f
                    )
                }

                // Current EV Thumb (Solid thick rectangular pill)
                val fraction = (evIndex - minEv).toFloat() / totalSteps.toFloat()
                val thumbX = 4f + fraction * (width - 8f)
                val thumbW = 22f
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
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

@Composable
fun EvPositionAlertBadge(
    evIndex: Int,
    rotationDegrees: Float = 0f,
    onResetEv: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (evIndex == 0) return

    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    val sign = if (evIndex > 0) "+$evIndex" else "$evIndex"

    Box(
        modifier = modifier
            .rotate(rotationDegrees)
            .clip(shape)
            .background(Color(0xD909090B)) // Translucent Nothing Dark
            .border(1.dp, Color(0xFFE11D48), shape) // Nothing Red border
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
