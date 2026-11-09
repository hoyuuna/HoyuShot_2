package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun VerticalEvSlider(
    evIndex: Int,
    minEv: Int = -6,
    maxEv: Int = 6,
    onEvChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val totalSteps = (maxEv - minEv).coerceAtLeast(1)
    val shape = RoundedCornerShape(2.dp)

    Column(
        modifier = modifier
            .width(42.dp)
            .height(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "+",
            color = Color(0xEEFFFFFF),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        // Vertical Slider Track with generous touch area
        Box(
            modifier = Modifier
                .width(40.dp)
                .weight(1f)
                .pointerInput(minEv, maxEv, evIndex) {
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            val height = size.height
                            val stepPx = height / totalSteps.toFloat()
                            val deltaSteps = (-accumulatedDrag / stepPx).toInt()
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
                val centerX = size.width / 2f
                val height = size.height

                // Wider track line
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(centerX, 8f),
                    end = Offset(centerX, height - 8f),
                    strokeWidth = 3f
                )

                // Zero center tick
                val zeroFraction = (0 - minEv).toFloat() / totalSteps.toFloat()
                val zeroY = height - (zeroFraction * height)
                drawLine(
                    color = Color(0x99FFFFFF),
                    start = Offset(centerX - 8f, zeroY),
                    end = Offset(centerX + 8f, zeroY),
                    strokeWidth = 2.5f
                )

                // Current EV Thumb position
                val fraction = (evIndex - minEv).toFloat() / totalSteps.toFloat()
                val thumbY = height - (fraction * height)

                // Draw prominent thumb pill
                drawRoundRect(
                    color = if (evIndex != 0) Color(0xFFE11D48) else Color(0xFFFAFAFA),
                    topLeft = Offset(centerX - 6f, thumbY - 16f),
                    size = androidx.compose.ui.geometry.Size(12f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }

        Text(
            text = "−",
            color = Color(0xEEFFFFFF),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun EvPositionAlertBadge(
    evIndex: Int,
    modifier: Modifier = Modifier
) {
    if (evIndex == 0) return

    val shape = RoundedCornerShape(2.dp)
    val sign = if (evIndex > 0) "+$evIndex" else "$evIndex"

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x33E11D48)) // Translucent Nothing Red tint
            .border(1.dp, Color(0xFFE11D48), shape) // Nothing Red border
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "EV IN POSITION $sign",
            color = Color(0xFFFF4D6D),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}
