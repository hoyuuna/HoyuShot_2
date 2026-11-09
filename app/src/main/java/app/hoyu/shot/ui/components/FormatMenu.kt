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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
        modifier = modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier.rotate(rotationDegrees),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(Color(0xF509090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "[ESC]",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(2.dp)
                    )
                }

                // Row 1: Hardware Resolutions in a Horizontal Row (ProShot Image 8)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    resolutions.forEach { res ->
                        val isSelected = selectedResolution?.width == res.width && selectedResolution.height == res.height
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(shape)
                                .background(if (isSelected) Color(0xFFFAFAFA) else Color(0xFF18181B))
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
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = res.mpLabel,
                                color = if (isSelected) Color.Black else Color(0xFFFAFAFA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Row 2: Formats in a Horizontal Row (ProShot Image 8: JPEG, RAW, RAW+JPEG)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    formatOptions.forEach { fmt ->
                        val isSelected = currentFormat == fmt
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(shape)
                                .background(if (isSelected) Color(0xFFE11D48) else Color(0xFF18181B))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                    shape
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    interactionEpoch = System.currentTimeMillis()
                                    onFormatSelected(fmt)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fmt.label,
                                color = if (isSelected) Color.White else Color(0xFFFAFAFA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
