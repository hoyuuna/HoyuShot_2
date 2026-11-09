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
    // Independent color states: Roll turns green when roll is level, Pitch turns green when pitch is level
    val rollColor by animateColorAsState(
        targetValue = if (orientation.isRollLevel) Color(0xFF00E676) else Color(0xEEFFFFFF),
        label = "rollColor"
    )

    val pitchColor by animateColorAsState(
        targetValue = if (orientation.isPitchLevel) Color(0xFF00E676) else Color(0xEEFFFFFF),
        label = "pitchColor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Larger size as requested ("cho 2 thằng này to ra")
        val boxWidth = 260f
        val boxHeight = 130f
        val tickLen = 14f
        val strokeW = 2.2f
        val guideColor = Color(0x66FFFFFF)

        val leftX = centerX - boxWidth / 2f
        val rightX = centerX + boxWidth / 2f
        val topY = centerY - boxHeight / 2f
        val bottomY = centerY + boxHeight / 2f

        // 1. Fixed Brackets: Left '[' and Right ']'
        // Left bracket spine, top, bottom corners
        drawLine(guideColor, Offset(leftX, topY), Offset(leftX, bottomY), strokeW)
        drawLine(guideColor, Offset(leftX, topY), Offset(leftX + tickLen, topY), strokeW)
        drawLine(guideColor, Offset(leftX, bottomY), Offset(leftX + tickLen, bottomY), strokeW)

        // Center zero reference marks on brackets
        val centerMarkLen = tickLen * 1.8f
        drawLine(
            if (orientation.isPitchLevel) Color(0xFF00E676) else guideColor,
            Offset(leftX, centerY),
            Offset(leftX + centerMarkLen, centerY),
            if (orientation.isPitchLevel) 3f else strokeW
        )

        // Intermediate pitch ruler ticks (+10 deg, -10 deg)
        val quarterH = boxHeight / 4f
        drawLine(guideColor, Offset(leftX, centerY - quarterH), Offset(leftX + tickLen * 0.9f, centerY - quarterH), strokeW)
        drawLine(guideColor, Offset(leftX, centerY + quarterH), Offset(leftX + tickLen * 0.9f, centerY + quarterH), strokeW)

        // Right bracket spine, top, bottom corners
        drawLine(guideColor, Offset(rightX, topY), Offset(rightX, bottomY), strokeW)
        drawLine(guideColor, Offset(rightX, topY), Offset(rightX - tickLen, topY), strokeW)
        drawLine(guideColor, Offset(rightX, bottomY), Offset(rightX - tickLen, bottomY), strokeW)

        // Center zero reference mark on right bracket
        drawLine(
            if (orientation.isPitchLevel) Color(0xFF00E676) else guideColor,
            Offset(rightX, centerY),
            Offset(rightX - centerMarkLen, centerY),
            if (orientation.isPitchLevel) 3f else strokeW
        )

        // Intermediate pitch ruler ticks on right bracket
        drawLine(guideColor, Offset(rightX, centerY - quarterH), Offset(rightX - tickLen * 0.9f, centerY - quarterH), strokeW)
        drawLine(guideColor, Offset(rightX, centerY + quarterH), Offset(rightX - tickLen * 0.9f, centerY + quarterH), strokeW)

        // 2. Moving Pitch Indicator ticks (Cúi/Ngửa ruler ticks moving inside brackets)
        // Pitch mapping: +-20 degrees relative to eye level
        val pitchFraction = (orientation.pitchDegrees / 20f).coerceIn(-1f, 1f)
        val dynamicPitchY = centerY + (pitchFraction * (boxHeight / 2.2f))

        // Left bracket moving tick
        drawLine(
            color = pitchColor,
            start = Offset(leftX, dynamicPitchY),
            end = Offset(leftX + 22f, dynamicPitchY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Right bracket moving tick
        drawLine(
            color = pitchColor,
            start = Offset(rightX, dynamicPitchY),
            end = Offset(rightX - 22f, dynamicPitchY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // 3. Center Rotating Roll Line (Xoay Horizon)
        rotate(degrees = -orientation.rollDegrees, pivot = Offset(centerX, centerY)) {
            drawLine(
                color = rollColor,
                start = Offset(leftX + 30f, centerY),
                end = Offset(rightX - 30f, centerY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}
