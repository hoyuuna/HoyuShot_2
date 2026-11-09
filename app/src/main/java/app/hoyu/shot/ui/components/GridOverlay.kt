package app.hoyu.shot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.hoyu.shot.model.GridMode

@Composable
fun GridOverlay(
    gridMode: GridMode,
    modifier: Modifier = Modifier
) {
    if (gridMode == GridMode.NONE) return

    val gridColor = Color(0x38FFFFFF)
    val strokeWidth = 1.2f

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        when (gridMode) {
            GridMode.THIRDS -> {
                // Rule of Thirds: 2 vertical lines, 2 horizontal lines
                val v1 = width / 3f
                val v2 = width * 2f / 3f
                val h1 = height / 3f
                val h2 = height * 2f / 3f

                drawLine(gridColor, Offset(v1, 0f), Offset(v1, height), strokeWidth)
                drawLine(gridColor, Offset(v2, 0f), Offset(v2, height), strokeWidth)
                drawLine(gridColor, Offset(0f, h1), Offset(width, h1), strokeWidth)
                drawLine(gridColor, Offset(0f, h2), Offset(width, h2), strokeWidth)
            }
            GridMode.GOLDEN -> {
                // Golden ratio lines (0.382 and 0.618)
                val phi1 = 0.382f
                val phi2 = 0.618f

                val v1 = width * phi1
                val v2 = width * phi2
                val h1 = height * phi1
                val h2 = height * phi2

                drawLine(gridColor, Offset(v1, 0f), Offset(v1, height), strokeWidth)
                drawLine(gridColor, Offset(v2, 0f), Offset(v2, height), strokeWidth)
                drawLine(gridColor, Offset(0f, h1), Offset(width, h1), strokeWidth)
                drawLine(gridColor, Offset(0f, h2), Offset(width, h2), strokeWidth)
            }
            GridMode.NONE -> {}
        }
    }
}
