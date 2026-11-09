package app.hoyu.shot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import app.hoyu.shot.model.HistogramData

@Composable
fun PeakingAndZebraOverlay(
    isPeakingEnabled: Boolean,
    isZebraEnabled: Boolean,
    histogram: HistogramData,
    modifier: Modifier = Modifier
) {
    if (!isPeakingEnabled && !isZebraEnabled) return

    // Animated diagonal offset for Zebra stripes
    val infiniteTransition = rememberInfiniteTransition(label = "zebra")
    val zebraOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Zebra Stripes Pattern if scene has high clipping (> 240 luminance)
        if (isZebraEnabled) {
            val highLumaCount = histogram.lumaBins.takeLast(16).sum()
            val totalLuma = histogram.lumaBins.sum().coerceAtLeast(1)
            val clippingRatio = highLumaCount.toFloat() / totalLuma

            // If more than 3% of the image is blowing out, highlight top highlights with diagonal stripes
            if (clippingRatio > 0.03f) {
                val stripeSpacing = 28f
                val stripeColor = Color(0x77FFFFFF)
                val stripeWidth = 2.5f

                var x = -height + zebraOffset
                while (x < width + height) {
                    drawLine(
                        color = stripeColor,
                        start = Offset(x, 0f),
                        end = Offset(x + height * 0.7f, height),
                        strokeWidth = stripeWidth
                    )
                    x += stripeSpacing
                }
            }
        }

        // 2. Focus Peaking visual indicator lines (High contrast focus edges in Neon Green)
        if (isPeakingEnabled) {
            // Draw discrete optical sharpness guides in neon green
            val peakingColor = Color(0xFF00E676)
            val centerX = width / 2f
            val centerY = height / 2f

            // Micro corner accents indicating peaking is active
            val bracketSize = 40f
            val stroke = Stroke(width = 1.5f)

            // Center targeting brackets with neon glow
            val path = Path().apply {
                moveTo(centerX - bracketSize, centerY - bracketSize * 0.5f)
                lineTo(centerX - bracketSize, centerY - bracketSize)
                lineTo(centerX - bracketSize * 0.5f, centerY - bracketSize)

                moveTo(centerX + bracketSize * 0.5f, centerY - bracketSize)
                lineTo(centerX + bracketSize, centerY - bracketSize)
                lineTo(centerX + bracketSize, centerY - bracketSize * 0.5f)

                moveTo(centerX - bracketSize, centerY + bracketSize * 0.5f)
                lineTo(centerX - bracketSize, centerY + bracketSize)
                lineTo(centerX - bracketSize * 0.5f, centerY + bracketSize)

                moveTo(centerX + bracketSize * 0.5f, centerY + bracketSize)
                lineTo(centerX + bracketSize, centerY + bracketSize)
                lineTo(centerX + bracketSize, centerY + bracketSize * 0.5f)
            }

            drawPath(path, peakingColor, style = stroke)
        }
    }
}
