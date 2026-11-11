package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun <T> DialWheelControl(
    title: String,
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int, T) -> Unit,
    formatItem: (T) -> String,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemSpacingPx = 70f

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xEE121212), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Parameter Title & Highlighted Value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                color = Color(0xFF888888),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = if (items.isNotEmpty() && currentIndex in items.indices) formatItem(items[currentIndex]) else "--",
                color = Color(0xFFFFD600), // ProShot Yellow
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Ruler / Wheel canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .pointerInput(items) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.x
                            val indexDelta = (-dragOffset / itemSpacingPx).roundToInt()
                            if (indexDelta != 0) {
                                val newIndex = (currentIndex + indexDelta).coerceIn(0, items.size - 1)
                                if (newIndex != currentIndex) {
                                    currentIndex = newIndex
                                    dragOffset = 0f
                                    view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.CLOCK_TICK)
                                    onItemSelected(currentIndex, items[currentIndex])
                                }
                            }
                        },
                        onDragEnd = {
                            dragOffset = 0f
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            // Center indicator cursor (Yellow needle)
            drawLine(
                color = Color(0xFFFFD600),
                start = Offset(centerX, 0f),
                end = Offset(centerX, height),
                strokeWidth = 3f
            )

            // Draw tick marks and values
            for (i in items.indices) {
                val tickX = centerX + (i - currentIndex) * itemSpacingPx + dragOffset
                if (tickX in 0f..width) {
                    val distanceFactor = 1f - (Math.abs(tickX - centerX) / (width / 2f)).coerceIn(0f, 1f)
                    val isMajor = i % 2 == 0
                    val tickHeight = if (isMajor) height * 0.45f else height * 0.25f

                    // Tick line
                    drawLine(
                        color = Color(0xFFFFFFFF).copy(alpha = distanceFactor * 0.8f),
                        start = Offset(tickX, height - tickHeight),
                        end = Offset(tickX, height),
                        strokeWidth = if (isMajor) 2f else 1f
                    )

                    // Text labels for major ticks
                    if (isMajor && distanceFactor > 0.4f) {
                        val text = formatItem(items[i])
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                alpha = (distanceFactor * 220).toInt()
                                textSize = 26f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            drawText(text, tickX, height - tickHeight - 8f, paint)
                        }
                    }
                }
            }
        }
    }
}

