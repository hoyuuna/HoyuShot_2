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
        val halfLineWidth = size.width * 0.18f

        // Center reticle dot
        drawCircle(
            color = levelColor,
            radius = 3.5f,
            center = Offset(centerX, centerY)
        )

        // Center cross brackets
        drawLine(
            color = Color(0x66FFFFFF),
            start = Offset(centerX - 15f, centerY),
            end = Offset(centerX + 15f, centerY),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0x66FFFFFF),
            start = Offset(centerX, centerY - 15f),
            end = Offset(centerX, centerY + 15f),
            strokeWidth = 2f
        )

        // Dynamic Horizon line rotating with roll angle
        rotate(degrees = -orientation.rollDegrees, pivot = Offset(centerX, centerY)) {
            // Left wing
            drawLine(
                color = levelColor,
                start = Offset(centerX - halfLineWidth - 30f, centerY),
                end = Offset(centerX - 30f, centerY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Right wing
            drawLine(
                color = levelColor,
                start = Offset(centerX + 30f, centerY),
                end = Offset(centerX + halfLineWidth + 30f, centerY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

