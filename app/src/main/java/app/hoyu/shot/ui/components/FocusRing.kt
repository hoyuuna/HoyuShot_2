package app.hoyu.shot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
    val animatedScale = remember(tapPoint.timestamp) { Animatable(1.4f) }

    // Entrance and state animation
    LaunchedEffect(tapPoint.timestamp) {
        visible = true
        // Quick, snappy zoom-in spring when tapping
        animatedScale.snapTo(1.4f)
        animatedScale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        // Auto-dismiss after 2 seconds
        delay(2000)
        visible = false
    }

    // Secondary pulse when lock succeeds
    LaunchedEffect(tapPoint.state) {
        if (tapPoint.state == FocusState.LOCKED_SUCCESS) {
            animatedScale.animateTo(
                targetValue = 0.88f,
                animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
            )
            animatedScale.animateTo(
                targetValue = 0.95f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
        }
    }

    // Strictly Nothing OS minimal palette: White focusing, Nothing Red locked!
    val ringColor by animateColorAsState(
        targetValue = when (tapPoint.state) {
            FocusState.FOCUSING -> Color(0xFFFAFAFA)
            FocusState.LOCKED_SUCCESS -> Color(0xFFE11D48)
            FocusState.LOCKED_FAILED -> Color(0xFF71717A)
            FocusState.IDLE -> Color(0xFFFAFAFA)
        },
        animationSpec = tween(150),
        label = "ringColor"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(350))
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
                val currentScale = animatedScale.value
                val radius = (size.width / 2f - 4f) * currentScale

                // Focus square with corner brackets
                val halfSide = radius * 0.95f
                val cornerLen = halfSide * 0.42f
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

                // Center crosshair dot on lock
                if (tapPoint.state == FocusState.LOCKED_SUCCESS) {
                    drawCircle(
                        color = Color(0xFFE11D48),
                        radius = 3.5f,
                        center = center
                    )
                }
            }
        }
    }
}

@Composable
fun SpotAeRingOverlay(
    tapPoint: Offset?,
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (tapPoint == null) return

    val animatedScale = remember { Animatable(1.3f) }

    LaunchedEffect(tapPoint) {
        animatedScale.snapTo(1.3f)
        animatedScale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val reticleColor = Color(0xFFE11D48) // Nothing Red for AE Lock/Spot

    Box(modifier = modifier.fillMaxSize()) {
        val ringSize = 72.dp
        Box(
            modifier = Modifier
                .size(ringSize)
                .offset {
                    IntOffset(
                        (tapPoint.x - (ringSize.toPx() / 2)).roundToInt(),
                        (tapPoint.y - (ringSize.toPx() / 2)).roundToInt()
                    )
                },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val currentScale = animatedScale.value
                val radius = (size.width / 2f - 4f) * currentScale

                // Four corner brackets for AE
                val halfSide = radius * 0.92f
                val cornerLen = halfSide * 0.38f
                val strokeW = 2.5f

                // Top-Left Corner
                drawLine(reticleColor, Offset(center.x - halfSide, center.y - halfSide), Offset(center.x - halfSide + cornerLen, center.y - halfSide), strokeW)
                drawLine(reticleColor, Offset(center.x - halfSide, center.y - halfSide), Offset(center.x - halfSide, center.y - halfSide + cornerLen), strokeW)

                // Top-Right Corner
                drawLine(reticleColor, Offset(center.x + halfSide, center.y - halfSide), Offset(center.x + halfSide - cornerLen, center.y - halfSide), strokeW)
                drawLine(reticleColor, Offset(center.x + halfSide, center.y - halfSide), Offset(center.x + halfSide, center.y - halfSide + cornerLen), strokeW)

                // Bottom-Left Corner
                drawLine(reticleColor, Offset(center.x - halfSide, center.y + halfSide), Offset(center.x - halfSide + cornerLen, center.y + halfSide), strokeW)
                drawLine(reticleColor, Offset(center.x - halfSide, center.y + halfSide), Offset(center.x - halfSide, center.y + halfSide - cornerLen), strokeW)

                // Bottom-Right Corner
                drawLine(reticleColor, Offset(center.x + halfSide, center.y + halfSide), Offset(center.x + halfSide - cornerLen, center.y + halfSide), strokeW)
                drawLine(reticleColor, Offset(center.x + halfSide, center.y + halfSide), Offset(center.x + halfSide, center.y + halfSide - cornerLen), strokeW)
            }

            // Monospace "AE" Text badge in the center
            Box(
                modifier = Modifier
                    .androidx.compose.ui.draw.rotate(rotationDegrees)
                    .androidx.compose.ui.draw.clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .androidx.compose.foundation.background(Color(0xBB000000))
                    .androidx.compose.foundation.border(1.dp, Color(0xFFE11D48), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .androidx.compose.foundation.layout.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "AE",
                    color = Color(0xFFFAFAFA),
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
