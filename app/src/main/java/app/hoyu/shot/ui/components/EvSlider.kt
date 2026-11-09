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
fun HorizontalEvSlider(
    evIndex: Int,
    minEv: Int = -6,
    maxEv: Int = 6,
    onEvChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val totalSteps = (maxEv - minEv).coerceAtLeast(1)

    Row(
        modifier = modifier
            .width(136.dp)
            .height(38.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "−",
            color = Color(0xCCFFFFFF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )

        // Horizontal Slider Track with wide touch area
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .pointerInput(minEv, maxEv, evIndex) {
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            val width = size.width
                            val stepPx = width / totalSteps.toFloat()
                            val deltaSteps = (accumulatedDrag / stepPx).toInt()
                            if (deltaSteps != 0) {
                                val newEv = (evIndex + deltaSteps).coerceIn(minEv, maxEv)
                                if (newEv != evIndex) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onEvChanged(newEv)
                                    accumulatedDrag = 0f
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val width = size.width

                // Horizontal track line
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(6f, centerY),
                    end = Offset(width - 6f, centerY),
                    strokeWidth = 3f
                )

                // Center zero tick
                val zeroFraction = (0 - minEv).toFloat() / totalSteps.toFloat()
                val zeroX = zeroFraction * width
                drawLine(
                    color = Color(0x99FFFFFF),
                    start = Offset(zeroX, centerY - 6f),
                    end = Offset(zeroX, centerY + 6f),
                    strokeWidth = 2.5f
                )

                // Current EV Thumb position
                val fraction = (evIndex - minEv).toFloat() / totalSteps.toFloat()
                val thumbX = fraction * width

                // Draw prominent thumb pill
                drawRoundRect(
                    color = if (evIndex != 0) Color(0xFFE11D48) else Color(0xFFFAFAFA),
                    topLeft = Offset(thumbX - 14f, centerY - 5f),
                    size = androidx.compose.ui.geometry.Size(28f, 10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }
        }

        Text(
            text = "+",
            color = Color(0xCCFFFFFF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp)
        )
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
            .padding(horizontal = 8.dp, vertical = 3.dp)
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
