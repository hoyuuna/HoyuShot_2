package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val LocalUiHapticsEnabled = compositionLocalOf { true }

fun View.triggerUiHaptic(
    enabled: Boolean,
    constant: Int = HapticFeedbackConstants.KEYBOARD_TAP
) {
    if (enabled) {
        performHapticFeedback(constant)
    }
}

/**
 * Renders an iOS-style frosted dark glass backdrop strictly clipped inside a popup's own bounds
 * (never fullscreen), falling back to solid opaque black when UI blur is disabled.
 */
@Composable
fun PopupGlassContainer(
    shape: Shape,
    isBlurEnabled: Boolean,
    blurredBackdrop: ImageBitmap?,
    previewBoundsInRoot: Rect,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor = Color(0xFF27272A)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF09090B))
            .border(1.dp, borderColor, shape)
    ) {
        content()
    }
}
