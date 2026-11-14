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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
    supportsRaw: Boolean = true,
    isBlurEnabled: Boolean = true,
    blurredBackdrop: ImageBitmap? = null,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onResolutionSelected: (CameraResolution) -> Unit,
    onFormatSelected: (CaptureFormat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    // 8-second auto-close timer reset on touch/interaction
    var userInteractionTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(userInteractionTrigger) {
        delay(8000)
        onDismiss()
    }

    val formatOptions = listOf(CaptureFormat.JPEG, CaptureFormat.RAW, CaptureFormat.RAW_JPEG)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.88f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.88f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .rotateBounds(rotationDegrees)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                userInteractionTrigger = System.currentTimeMillis()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
            PopupGlassContainer(
                shape = shape,
                isBlurEnabled = isBlurEnabled,
                blurredBackdrop = blurredBackdrop,
                previewBoundsInRoot = previewBoundsInRoot,
                rotationDegrees = rotationDegrees,
                modifier = Modifier.width(300.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header (Centered modal style matching Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYS // RES & FORMAT",
                        color = Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "[ESC]",
                        color = Color(0xFFA1A1AA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onDismiss()
                            }
                            .padding(4.dp)
                    )
                }

                // Section 1: Megapixels (Horizontal row matching ProShot)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SENSOR RESOLUTION",
                        color = Color(0xFF71717A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
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
                                        view.triggerUiHaptic(uiHapticsEnabled)
                                        userInteractionTrigger = System.currentTimeMillis()
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
                }

                // Section 2: Output Formats (Horizontal row: JPEG, RAW, RAW+JPEG)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OUTPUT FORMAT",
                            color = Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!supportsRaw) {
                            Text(
                                text = "RAW N/A ON LENS",
                                color = Color(0xFF71717A),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        formatOptions.forEach { fmt ->
                            val isEnabled = fmt == CaptureFormat.JPEG || supportsRaw
                            val isSelected = currentFormat == fmt && isEnabled
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(shape)
                                    .background(
                                        when {
                                            isSelected -> Color(0xFFE11D48)
                                            !isEnabled -> Color(0xFF09090B)
                                            else -> Color(0xFF18181B)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                        shape
                                    )
                                    .clickable(enabled = isEnabled) {
                                        view.triggerUiHaptic(uiHapticsEnabled)
                                        userInteractionTrigger = System.currentTimeMillis()
                                        onFormatSelected(fmt)
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt.label,
                                    color = when {
                                        isSelected -> Color.White
                                        !isEnabled -> Color(0xFF52525B)
                                        else -> Color(0xFFFAFAFA)
                                    },
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
        }
    }
}
