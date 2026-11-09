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
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Strictly Nothing OS Red theme (no rainbow neon colors)
    val rollColor by animateColorAsState(
        targetValue = if (orientation.isRollLevel) Color(0xFFE11D48) else Color(0x99FFFFFF),
        label = "rollColor"
    )

    val pitchColor by animateColorAsState(
        targetValue = if (orientation.isPitchLevel) Color(0xFFE11D48) else Color(0x99FFFFFF),
        label = "pitchColor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Entire gauge rotates along with user's holding orientation!
        rotate(degrees = rotationDegrees, pivot = Offset(centerX, centerY)) {
            val boxWidth = 320f
            val boxHeight = 160f
            val tickLen = 18f
            val strokeW = 2.5f
            val guideColor = if (orientation.isLevel) Color(0x66E11D48) else Color(0x44FFFFFF)

            val leftX = centerX - boxWidth / 2f
            val rightX = centerX + boxWidth / 2f
            val topY = centerY - boxHeight / 2f
            val bottomY = centerY + boxHeight / 2f

            // 1. Fixed Brackets: Left '[' and Right ']' aligned with user orientation
            // Left bracket
            drawLine(guideColor, Offset(leftX, topY), Offset(leftX, bottomY), strokeW)
            drawLine(guideColor, Offset(leftX, topY), Offset(leftX + tickLen, topY), strokeW)
            drawLine(guideColor, Offset(leftX, bottomY), Offset(leftX + tickLen, bottomY), strokeW)

            // Center zero reference marks on left bracket
            val centerMarkLen = tickLen * 2f
            drawLine(
                if (orientation.isPitchLevel) Color(0xFFE11D48) else guideColor,
                Offset(leftX, centerY),
                Offset(leftX + centerMarkLen, centerY),
                if (orientation.isPitchLevel) 3.2f else strokeW
            )

            // Intermediate pitch ruler ticks (+10 deg, -10 deg)
            val quarterH = boxHeight / 4f
            drawLine(guideColor, Offset(leftX, centerY - quarterH), Offset(leftX + tickLen * 0.8f, centerY - quarterH), strokeW)
            drawLine(guideColor, Offset(leftX, centerY + quarterH), Offset(leftX + tickLen * 0.8f, centerY + quarterH), strokeW)

            // Right bracket
            drawLine(guideColor, Offset(rightX, topY), Offset(rightX, bottomY), strokeW)
            drawLine(guideColor, Offset(rightX, topY), Offset(rightX - tickLen, topY), strokeW)
            drawLine(guideColor, Offset(rightX, bottomY), Offset(rightX - tickLen, bottomY), strokeW)

            // Center zero reference mark on right bracket
            drawLine(
                if (orientation.isPitchLevel) Color(0xFFE11D48) else guideColor,
                Offset(rightX, centerY),
                Offset(rightX - centerMarkLen, centerY),
                if (orientation.isPitchLevel) 3.2f else strokeW
            )

            // Intermediate pitch ruler ticks on right bracket
            drawLine(guideColor, Offset(rightX, centerY - quarterH), Offset(rightX - tickLen * 0.8f, centerY - quarterH), strokeW)
            drawLine(guideColor, Offset(rightX, centerY + quarterH), Offset(rightX - tickLen * 0.8f, centerY + quarterH), strokeW)

            // 2. Moving Pitch Indicator ticks (Cúi/Ngửa relative to eye level)
            // pitchDegrees is 0 when screen is upright vertical at eye level
            val pitchFraction = (orientation.pitchDegrees / 20f).coerceIn(-1f, 1f)
            val dynamicPitchY = centerY + (pitchFraction * (boxHeight / 2.2f))

            // Left bracket moving tick
            drawLine(
                color = pitchColor,
                start = Offset(leftX, dynamicPitchY),
                end = Offset(leftX + 28f, dynamicPitchY),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )

            // Right bracket moving tick
            drawLine(
                color = pitchColor,
                start = Offset(rightX, dynamicPitchY),
                end = Offset(rightX - 28f, dynamicPitchY),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )

            // Center Level Reference Crosshair (Xiaomi style): turns Nothing Red when perfectly level
            val crosshairColor = if (orientation.isLevel) Color(0xFFE11D48) else Color(0x66FFFFFF)
            val crosshairSize = 10f
            drawLine(
                color = crosshairColor,
                start = Offset(centerX - crosshairSize, centerY),
                end = Offset(centerX + crosshairSize, centerY),
                strokeWidth = if (orientation.isLevel) 3f else 1.8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = crosshairColor,
                start = Offset(centerX, centerY - crosshairSize),
                end = Offset(centerX, centerY + crosshairSize),
                strokeWidth = if (orientation.isLevel) 3f else 1.8f,
                cap = StrokeCap.Round
            )

            // 4. Center Rotating Roll Line
            rotate(degrees = -orientation.rollDegrees, pivot = Offset(centerX, centerY)) {
                drawLine(
                    color = rollColor,
                    start = Offset(leftX + 36f, centerY),
                    end = Offset(rightX - 36f, centerY),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
