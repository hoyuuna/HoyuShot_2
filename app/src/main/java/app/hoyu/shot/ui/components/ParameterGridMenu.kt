package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay

@Composable
fun <T> ParameterGridMenu(
    visible: Boolean,
    title: String,
    items: List<T>,
    selectedItem: T?,
    rotationDegrees: Float = 0f,
    onItemSelected: (T) -> Unit,
    formatItem: (T) -> String,
    onAutoSelected: () -> Unit,
    isAutoSelected: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    // 5-second inactivity auto-close timer
    var interactionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(interactionTimestamp, visible) {
        if (visible) {
            delay(5000)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .padding(16.dp)
            .rotate(rotationDegrees),
        contentAlignment = Alignment.Center
    ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .heightIn(max = 280.dp)
                    .clip(shape)
                    .background(Color(0xF509090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
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
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            interactionTimestamp = System.currentTimeMillis()
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
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chunkedItems = items.chunked(2)
                    chunkedItems.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { item ->
                                val isSelected = !isAutoSelected && item == selectedItem
                                val itemBgColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0x33E11D48) else Color(0xFF18181B),
                                    label = "itemBg"
                                )
                                val itemBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                    label = "itemBorder"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(shape)
                                        .background(itemBgColor)
                                        .border(1.dp, itemBorderColor, shape)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            interactionTimestamp = System.currentTimeMillis()
                                            onItemSelected(item)
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatItem(item),
                                            color = if (isSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
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
                }
            }
        }
    }
