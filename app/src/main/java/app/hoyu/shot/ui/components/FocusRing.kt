package app.hoyu.shot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.hoyu.shot.model.FocusState
import app.hoyu.shot.model.TapFocusPoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FocusRingOverlay(
    tapPoint: TapFocusPoint?,
    modifier: Modifier = Modifier
) {
    if (tapPoint == null) return

    var visible by remember(tapPoint.timestamp) { mutableStateOf(true) }

    LaunchedEffect(tapPoint.timestamp) {
        visible = true
        delay(2200)
        visible = false
    }

    val targetScale = when (tapPoint.state) {
        FocusState.FOCUSING -> 1.0f
        FocusState.LOCKED_SUCCESS -> 0.9f
        FocusState.LOCKED_FAILED -> 1.0f
        FocusState.IDLE -> 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 250),
        label = "scale"
    )

    val ringColor = when (tapPoint.state) {
        FocusState.FOCUSING -> Color(0xFFFFD600)
        FocusState.LOCKED_SUCCESS -> Color(0xFF00E676)
        FocusState.LOCKED_FAILED -> Color(0xFFFF1744)
        FocusState.IDLE -> Color.White
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(400))
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            val ringSize = 72.dp
            Canvas(
                modifier = Modifier
                    .size(ringSize)
                    .offset {
                        IntOffset(
                            (tapPoint.offset.x - (ringSize.toPx() / 2)).roundToInt(),
                            (tapPoint.offset.y - (ringSize.toPx() / 2)).roundToInt()
                        )
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.width / 2f - 4f) * animatedScale

                // Focus square with corner brackets
                val halfSide = radius * 0.95f
                val cornerLen = halfSide * 0.4f
                val strokeW = 2.5f

                // Top-Left Corner
                drawLine(ringColor, Offset(center.x - halfSide, center.y - halfSide), Offset(center.x - halfSide + cornerLen, center.y - halfSide), strokeW)
                drawLine(ringColor, Offset(center.x - halfSide, center.y - halfSide), Offset(center.x - halfSide, center.y - halfSide + cornerLen), strokeW)

                // Top-Right Corner
                drawLine(ringColor, Offset(center.x + halfSide, center.y - halfSide), Offset(center.x + halfSide - cornerLen, center.y - halfSide), strokeW)
                drawLine(ringColor, Offset(center.x + halfSide, center.y - halfSide), Offset(center.x + halfSide, center.y - halfSide + cornerLen), strokeW)

                // Bottom-Left Corner
                drawLine(ringColor, Offset(center.x - halfSide, center.y + halfSide), Offset(center.x - halfSide + cornerLen, center.y + halfSide), strokeW)
                drawLine(ringColor, Offset(center.x - halfSide, center.y + halfSide), Offset(center.x - halfSide, center.y + halfSide - cornerLen), strokeW)

                // Bottom-Right Corner
                drawLine(ringColor, Offset(center.x + halfSide, center.y + halfSide), Offset(center.x + halfSide - cornerLen, center.y + halfSide), strokeW)
                drawLine(ringColor, Offset(center.x + halfSide, center.y + halfSide), Offset(center.x + halfSide, center.y + halfSide - cornerLen), strokeW)

                // Center dot
                drawCircle(ringColor, radius = 2.5f, center = center)
            }
        }
    }
}
