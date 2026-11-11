package app.hoyu.shot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.SunCompassState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SunCompassHud(
    enabled: Boolean,
    state: SunCompassState,
    modifier: Modifier = Modifier
) {
    // Auto-hide completely at night / darkness once location is known and sun is below the horizon
    val shouldShow = enabled && (!state.hasLocation || state.isDaylight)
    val shape = RoundedCornerShape(2.dp)

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (!state.hasLocation) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(Color(0x77000000))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SUN // WAIT GPS",
                    color = Color(0xFFFACC15),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            val altInt = kotlin.math.round(state.sunAltitudeDeg).toInt()
            val absRel = abs(state.relativeAngleDeg)
            val directionLabel = when {
                state.isOverhead -> "ZENITH ${altInt}°"
                state.sunAltitudeDeg in 0.0..12.0 -> "GOLDEN ${altInt}°"
                absRel <= 35f -> "BACKLIT ${altInt}°"
                absRel >= 145f -> "FRONTLIT ${altInt}°"
                state.relativeAngleDeg > 0f -> "SIDE-R ${altInt}°"
                else -> "SIDE-L ${altInt}°"
            }

            Column(
                modifier = Modifier
                    .clip(shape)
                    .background(Color(0x77000000))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerR = size.minDimension / 2f - 4.dp.toPx()

                    // Outer white compass ring + top 12-o'clock forward camera sight tick
                    drawCircle(
                        color = Color(0xCCFFFFFF),
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawLine(
                        color = Color(0xFFFFFFFF),
                        start = Offset(cx, cy - outerR - 2.dp.toPx()),
                        end = Offset(cx, cy - outerR + 3.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (state.isOverhead) {
                        // Overhead midday sun: yellow ring sits directly on top of our center white dot; NO directional beam
                        drawCircle(
                            color = Color(0x4DFACC15),
                            radius = 10.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color(0xFFFACC15),
                            radius = 7.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        // Compute sun position on radar (0° relative -> 12 o'clock / -90° in screen polar space)
                        val angleRad = Math.toRadians((state.relativeAngleDeg - 90f).toDouble())
                        val clampedAlt = state.sunAltitudeDeg.toFloat().coerceIn(0f, 90f)
                        val radialDist = outerR * ((90f - clampedAlt) / 90f).coerceIn(0.18f, 0.96f)
                        val sx = cx + (cos(angleRad) * radialDist).toFloat()
                        val sy = cy + (sin(angleRad) * radialDist).toFloat()

                        // Directional light cone & ray from yellow sun toward our center white dot
                        val dirX = (cx - sx)
                        val dirY = (cy - sy)
                        val dist = kotlin.math.hypot(dirX, dirY).coerceAtLeast(1f)
                        val ux = dirX / dist
                        val uy = dirY / dist
                        // Perpendicular vector for soft beam spread
                        val px = -uy
                        val py = ux
                        val spread = 9.dp.toPx()

                        val conePath = Path().apply {
                            moveTo(sx, sy)
                            lineTo(cx + px * spread, cy + py * spread)
                            lineTo(cx - px * spread, cy - py * spread)
                            close()
                        }
                        drawPath(
                            path = conePath,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0x88FDE047), Color(0x14FACC15)),
                                start = Offset(sx, sy),
                                end = Offset(cx, cy)
                            )
                        )
                        // Subtle highlight beam line from sun toward us
                        drawLine(
                            color = Color(0xCCFDE047),
                            start = Offset(sx, sy),
                            end = Offset(sx + ux * (dist - 5.dp.toPx()).coerceAtLeast(0f), sy + uy * (dist - 5.dp.toPx()).coerceAtLeast(0f)),
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Yellow sun halo + crisp yellow sun ring/dot
                        drawCircle(
                            color = Color(0x4DFACC15),
                            radius = 7.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = Color(0xFFFACC15),
                            radius = 4.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }

                    // Center white dot representing us / camera
                    drawCircle(
                        color = Color(0xAA000000),
                        radius = 4.2.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }

                Text(
                    text = directionLabel,
                    color = Color(0xFFFAFAFA),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
