package app.hoyu.shot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import app.hoyu.shot.model.ViewfinderOverlayData

@Composable
fun PeakingAndZebraOverlay(
    isPeakingEnabled: Boolean,
    isZebraEnabled: Boolean,
    overlayData: ViewfinderOverlayData,
    modifier: Modifier = Modifier
) {
    if (!isPeakingEnabled && !isZebraEnabled) return
    if (overlayData.cols <= 0 || overlayData.rows <= 0) return

    // Animated diagonal offset for Zebra stripes
    val infiniteTransition = rememberInfiniteTransition(label = "zebra")
    val zebraOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cols = overlayData.cols
        val rows = overlayData.rows
        val cellW = width / cols
        val cellH = height / rows

        // 1. Real Spatial Zebra Stripes strictly over overexposed highlight cells (>= 238 luma)
        if (isZebraEnabled && overlayData.zebraMask.size == cols * rows) {
            val stripeSpacing = 18f
            val stripeColor = Color(0xBBFFFFFF)
            val shadowStripeColor = Color(0x66000000)
            val stripeWidth = 2.4f

            for (r in 0 until rows) {
                var c = 0
                while (c < cols) {
                    if (overlayData.zebraMask[r * cols + c].toInt() != 0) {
                        val startC = c
                        while (c < cols && overlayData.zebraMask[r * cols + c].toInt() != 0) {
                            c++
                        }
                        val left = startC * cellW
                        val top = r * cellH
                        val right = c * cellW
                        val bottom = (r + 1) * cellH

                        clipRect(left = left, top = top, right = right, bottom = bottom) {
                            var x = left - cellH + (zebraOffset % stripeSpacing)
                            while (x < right + cellH) {
                                drawLine(
                                    color = shadowStripeColor,
                                    start = Offset(x - 1.5f, bottom),
                                    end = Offset(x + cellH - 1.5f, top),
                                    strokeWidth = stripeWidth + 1.2f
                                )
                                drawLine(
                                    color = stripeColor,
                                    start = Offset(x, bottom),
                                    end = Offset(x + cellH, top),
                                    strokeWidth = stripeWidth
                                )
                                x += stripeSpacing
                            }
                        }
                    } else {
                        c++
                    }
                }
            }
        }

        // 2. Real Focus Peaking highlights over sharp in-focus edge cells
        if (isPeakingEnabled && overlayData.peakingStrength.size == cols * rows) {
            val strokeW = (cellW * 0.22f).coerceIn(2.0f, 3.8f)
            for (r in 0 until rows) {
                val rowBase = r * cols
                val cy = (r + 0.5f) * cellH
                for (c in 0 until cols) {
                    val strength = overlayData.peakingStrength[rowBase + c].toInt() and 0xFF
                    if (strength >= 36) {
                        val alpha = ((strength - 36) / 64f).coerceIn(0.45f, 0.95f)
                        val peakColor = Color(0xFF00E676).copy(alpha = alpha)
                        val cx = (c + 0.5f) * cellW
                        val half = cellW * 0.36f
                        drawRect(
                            color = peakColor,
                            topLeft = Offset(cx - half, cy - half * 0.35f),
                            size = Size(half * 2f, strokeW)
                        )
                    }
                }
            }
        }
    }
}
