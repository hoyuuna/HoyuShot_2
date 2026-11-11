package app.hoyu.shot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.hoyu.shot.model.GridMode

@Composable
fun GridOverlay(
    gridMode: GridMode,
    modifier: Modifier = Modifier
) {
    if (gridMode == GridMode.NONE) return

    val gridColor = Color(0xCCFFFFFF)
    val shadowColor = Color(0x59000000)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val strokeWidth = 1.25.dp.toPx()
        val shadowStrokeWidth = 2.5.dp.toPx()

        val (v1, v2, h1, h2) = when (gridMode) {
            GridMode.THIRDS -> listOf(width / 3f, width * 2f / 3f, height / 3f, height * 2f / 3f)
            GridMode.GOLDEN -> listOf(width * 0.382f, width * 0.618f, height * 0.382f, height * 0.618f)
            GridMode.NONE -> return@Canvas
        }

        val lines = listOf(
            Offset(v1, 0f) to Offset(v1, height),
            Offset(v2, 0f) to Offset(v2, height),
            Offset(0f, h1) to Offset(width, h1),
            Offset(0f, h2) to Offset(width, h2)
        )

        lines.forEach { (start, end) ->
            drawLine(shadowColor, start, end, shadowStrokeWidth)
            drawLine(gridColor, start, end, strokeWidth)
        }
    }
}
