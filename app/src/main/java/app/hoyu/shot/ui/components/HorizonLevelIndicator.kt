package app.hoyu.shot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import app.hoyu.shot.model.HorizonOrientation

@Composable
fun HorizonLevelIndicator(
    orientation: HorizonOrientation,
    modifier: Modifier = Modifier
) {
    val levelColor by animateColorAsState(
        targetValue = if (orientation.isLevel) Color(0xFF00E676) else Color(0xCCFFFFFF),
        label = "horizonColor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val boxWidth = 140f
        val boxHeight = 70f
        val tickLen = 8f
        val strokeW = 1.8f
        val guideColor = Color(0x66FFFFFF)

        // Left bracket with tick
        val leftX = centerX - boxWidth / 2f
        val rightX = centerX + boxWidth / 2f
        val topY = centerY - boxHeight / 2f
        val bottomY = centerY + boxHeight / 2f

        // Left bracket: top corner, bottom corner, middle tick
        drawLine(guideColor, Offset(leftX, topY), Offset(leftX, bottomY), strokeW)
        drawLine(guideColor, Offset(leftX, topY), Offset(leftX + tickLen, topY), strokeW)
        drawLine(guideColor, Offset(leftX, bottomY), Offset(leftX + tickLen, bottomY), strokeW)
        drawLine(guideColor, Offset(leftX, centerY), Offset(leftX + tickLen * 1.5f, centerY), strokeW)

        // Right bracket: top corner, bottom corner, middle tick
        drawLine(guideColor, Offset(rightX, topY), Offset(rightX, bottomY), strokeW)
        drawLine(guideColor, Offset(rightX, topY), Offset(rightX - tickLen, topY), strokeW)
        drawLine(guideColor, Offset(rightX, bottomY), Offset(rightX - tickLen, bottomY), strokeW)
        drawLine(guideColor, Offset(rightX, centerY), Offset(rightX - tickLen * 1.5f, centerY), strokeW)

        // Center Horizon Level Line rotating with roll degrees
        rotate(degrees = -orientation.rollDegrees, pivot = Offset(centerX, centerY)) {
            drawLine(
                color = levelColor,
                start = Offset(leftX + 4f, centerY),
                end = Offset(rightX - 4f, centerY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }
    }
}
