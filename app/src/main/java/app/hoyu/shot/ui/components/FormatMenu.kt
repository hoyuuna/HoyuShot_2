package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraResolution
import app.hoyu.shot.model.CaptureFormat
import kotlinx.coroutines.delay

@Composable
fun FormatMenu(
    visible: Boolean,
    resolutions: List<CameraResolution>,
    selectedResolution: CameraResolution?,
    currentFormat: CaptureFormat,
    rotationDegrees: Float = 0f,
    onResolutionSelected: (CameraResolution) -> Unit,
    onFormatSelected: (CaptureFormat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)

    // 5-second inactivity auto-close timer
    var interactionEpoch by remember { mutableStateOf(0L) }
    LaunchedEffect(interactionEpoch, visible) {
        if (visible) {
            delay(5000)
            onDismiss()
        }
    }

    val formatOptions = listOf(CaptureFormat.JPEG, CaptureFormat.RAW, CaptureFormat.RAW_JPEG)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .rotate(rotationDegrees)
                .clip(shape)
                .background(Color(0xF209090B)) // shadcn zinc-950 semi-translucent
                .border(1.dp, Color(0xFF27272A), shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RES // FORMAT",
                    color = Color(0xFFFAFAFA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "[X]",
                    color = Color(0xFFA1A1AA),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(2.dp)
                )
            }

            // Row 1: Real Megapixels from hardware sensor
            if (resolutions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    resolutions.forEach { res ->
                        val isSelected = selectedResolution?.width == res.width && selectedResolution.height == res.height
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x33000000))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFFAFAFA) else Color(0xFF27272A),
                                    shape
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    interactionEpoch = System.currentTimeMillis()
                                    onResolutionSelected(res)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = res.mpLabel,
                                color = if (isSelected) Color.Black else Color(0xFFFAFAFA),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Row 2: Format Selection (JPEG, RAW, RAW+JPEG)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                formatOptions.forEach { fmt ->
                    val isSelected = fmt == currentFormat
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFFAFAFA) else Color(0x33000000))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFFAFAFA) else Color(0xFF27272A),
                            shape
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            interactionEpoch = System.currentTimeMillis()
                            onFormatSelected(fmt)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fmt.label,
                            color = if (isSelected) Color.Black else Color(0xFFFAFAFA),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
