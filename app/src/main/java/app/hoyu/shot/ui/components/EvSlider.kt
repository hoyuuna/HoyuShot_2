package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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

    Column(
        modifier = modifier
            .width(28.dp)
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "+",
            color = Color(0xCCFFFFFF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        // Vertical Slider Track
        Box(
            modifier = Modifier
                .width(24.dp)
                .weight(1f)
                .pointerInput(minEv, maxEv, evIndex) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val height = size.height
                        // Dragging up (negative y) increases EV
                        val stepPx = height / totalSteps.toFloat()
                        val deltaSteps = (-dragAmount / stepPx).roundToInt()
                        if (deltaSteps != 0) {
                            val newEv = (evIndex + deltaSteps).coerceIn(minEv, maxEv)
                            if (newEv != evIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onEvChanged(newEv)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val height = size.height

                // Thin track line
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(centerX, 8f),
                    end = Offset(centerX, height - 8f),
                    strokeWidth = 2f
                )

                // Center zero marker
                val zeroFraction = (0 - minEv).toFloat() / totalSteps.toFloat()
                val zeroY = height - (zeroFraction * height)
                drawLine(
                    color = Color(0x88FFFFFF),
                    start = Offset(centerX - 6f, zeroY),
                    end = Offset(centerX + 6f, zeroY),
                    strokeWidth = 2f
                )

                // Current EV Thumb (fraction from 0 (bottom) to 1 (top))
                val fraction = (evIndex - minEv).toFloat() / totalSteps.toFloat()
                val thumbY = height - (fraction * height)

                // Draw thumb pill
                drawRoundRect(
                    color = if (evIndex != 0) Color(0xFFFFD600) else Color.White,
                    topLeft = Offset(centerX - 4f, thumbY - 14f),
                    size = androidx.compose.ui.geometry.Size(8f, 28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }

        Text(
            text = "−",
            color = Color(0xCCFFFFFF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
