package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ParameterGridMenu(
    visible: Boolean,
    title: String,
    items: List<T>,
    selectedItem: T?,
    limitItem: T? = null,
    onItemLongClick: ((T) -> Unit)? = null,
    footerNote: String? = null,
    isBlurEnabled: Boolean = true,
    blurredBackdrop: ImageBitmap? = null,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onItemSelected: (T) -> Unit,
    formatItem: (T) -> String,
    onAutoSelected: () -> Unit,
    isAutoSelected: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    // 8-second auto-close timer starting from the exact moment user lifts finger off the popup
    var isFingerTouching by remember { mutableStateOf(false) }
    var touchReleaseEpoch by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(touchReleaseEpoch, isFingerTouching) {
        if (!isFingerTouching) {
            delay(8000)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .rotateBounds(rotationDegrees)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val touching = event.changes.any { it.pressed }
                        if (touching != isFingerTouching) {
                            isFingerTouching = touching
                            if (!touching) {
                                touchReleaseEpoch = System.currentTimeMillis()
                            }
                        }
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
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 340.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header (Matching SettingsDialog)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYS // $title",
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
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                // Scrollable container: Entire list (AUTO METERING + MANUAL PRESETS) scrolls together
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. "AUTO TRÊN CÙNG TO NHẤT": Prominent wide card styled exactly like Resume Settings
                    val autoBgColor by animateColorAsState(
                        targetValue = if (isAutoSelected) Color(0x33E11D48) else Color(0xFF18181B),
                        label = "autoBg"
                    )
                    val autoBorderColor by animateColorAsState(
                        targetValue = if (isAutoSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                        label = "autoBorder"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(autoBgColor)
                            .border(1.dp, autoBorderColor, shape)
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                touchReleaseEpoch = System.currentTimeMillis()
                                onAutoSelected()
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AUTO METERING",
                                color = if (isAutoSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Automatic hardware metering & calculation",
                                color = Color(0xFF71717A),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Animated ON / OFF Pill
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (isAutoSelected) Color(0xFFE11D48) else Color(0xFF27272A))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAutoSelected) "ON" else "OFF",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // 2. "CUSTOM Ở DƯỚI NHỎ HƠN VÀ CÓ ANIMATION": Grid of smaller cards
                    Text(
                        text = "MANUAL PRESETS",
                        color = Color(0xFF71717A),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    // 2-Column layout of smaller cards with smooth animated selection
                    val chunkedItems = items.chunked(2)
                    chunkedItems.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { item ->
                                val isSelected = !isAutoSelected && item == selectedItem
                                val isLimited = item == limitItem
                                val itemBgColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0x33E11D48) else Color(0xFF18181B),
                                    label = "itemBg"
                                )
                                val itemBorderColor by animateColorAsState(
                                    targetValue = when {
                                        isLimited -> Color(0xFFE11D48)
                                        isSelected -> Color(0xFFE11D48)
                                        else -> Color(0xFF27272A)
                                    },
                                    label = "itemBorder"
                                )
                                val itemBorderWidth = if (isLimited) 1.5.dp else 1.dp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(shape)
                                        .background(itemBgColor)
                                        .border(itemBorderWidth, itemBorderColor, shape)
                                        .combinedClickable(
                                            onClick = {
                                                view.triggerUiHaptic(uiHapticsEnabled)
                                                touchReleaseEpoch = System.currentTimeMillis()
                                                onItemSelected(item)
                                            },
                                            onLongClick = if (onItemLongClick != null) {
                                                {
                                                    view.triggerUiHaptic(uiHapticsEnabled, HapticFeedbackConstants.LONG_PRESS)
                                                    touchReleaseEpoch = System.currentTimeMillis()
                                                    onItemLongClick(item)
                                                }
                                            } else null
                                        )
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = formatItem(item),
                                                color = if (isSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected || isLimited) FontWeight.Black else FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (isLimited) {
                                                Text(
                                                    text = "L",
                                                    color = Color(0xFFE11D48),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(shape)
                                                    .background(Color(0xFFE11D48))
                                            )
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (footerNote != null) {
                        Text(
                            text = footerNote,
                            color = Color(0xFF71717A),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
