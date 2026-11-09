package app.hoyu.shot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.hoyu.shot.model.HistogramData

@Composable
fun HistogramView(
    histogram: HistogramData,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)

    Canvas(
        modifier = modifier
            .width(130.dp)
            .height(65.dp)
            .clip(shape)
            .background(Color(0x99000000))
            .border(0.5.dp, Color(0x44FFFFFF), shape)
    ) {
        val width = size.width
        val height = size.height
        val max = if (histogram.maxCount > 0) histogram.maxCount.toFloat() else 1f

        // Draw Luma (Grey/White)
        drawChannelCurve(histogram.lumaBins, max, width, height, Color(0xBBFFFFFF))

        // Draw RGB Channels
        drawChannelCurve(histogram.redBins, max, width, height, Color(0x88FF1744))
        drawChannelCurve(histogram.greenBins, max, width, height, Color(0x8800E676))
        drawChannelCurve(histogram.blueBins, max, width, height, Color(0x882979FF))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChannelCurve(
    bins: IntArray,
    max: Float,
    width: Float,
    height: Float,
    color: Color
) {
    if (bins.isEmpty()) return

    val path = Path()
    path.moveTo(0f, height)

    for (i in bins.indices) {
        val x = (i / 255f) * width
        val normalizedValue = (bins[i] / max).coerceIn(0f, 1f)
        val y = height - (normalizedValue * height)
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(path = path, color = color, style = Stroke(width = 1.2f))
}

