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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    // 5-second inactivity auto-close timer
    var interactionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(interactionTimestamp, visible) {
        if (visible) {
            delay(5000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
        modifier = modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier.rotate(rotationDegrees),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .heightIn(max = 280.dp)
                    .clip(shape)
                    .background(Color(0xF509090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header (Centered modal style matching Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
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

                Spacer(modifier = Modifier.height(2.dp))

                // Wide AUTO Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(shape)
                        .background(if (isAutoSelected) Color(0x33E11D48) else Color(0xFF18181B))
                        .border(
                            1.dp,
                            if (isAutoSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                            shape
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            interactionTimestamp = System.currentTimeMillis()
                            onAutoSelected()
                        }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUTO",
                        color = if (isAutoSelected) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isAutoSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(shape)
                                .background(Color(0xFFE11D48))
                        )
                    }
                }

                // Scrollable parameter list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(items) { item ->
                        val isSelected = !isAutoSelected && item == selectedItem
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(shape)
                                .background(if (isSelected) Color(0x33E11D48) else Color(0xFF18181B))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                    shape
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    interactionTimestamp = System.currentTimeMillis()
                                    onItemSelected(item)
                                }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatItem(item),
                                color = if (isSelected) Color(0xFFFF4D6D) else Color(0xFFE4E4E7),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
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
            }
        }
    }
}
