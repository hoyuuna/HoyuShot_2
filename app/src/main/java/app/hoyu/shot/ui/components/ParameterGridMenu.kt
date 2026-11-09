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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

    // 5-second inactivity timer that resets on user interaction
    var interactionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(interactionTimestamp, visible) {
        if (visible) {
            delay(5000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .rotate(rotationDegrees)
                .width(190.dp)
                .heightIn(max = 330.dp)
                .clip(shape)
                .background(Color(0xF509090B)) // shadcn zinc-950 semi-translucent
                .border(1.dp, Color(0xFF27272A), shape)
                .padding(8.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color(0xFFFAFAFA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "[X]",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2-Column Scrollable Grid of Values (Keeps open when tapped)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items) { item ->
                    val isSelected = !isAutoSelected && item == selectedItem
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFFAFAFA) else Color(0xFF18181B))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFAFAFA) else Color(0xFF27272A),
                                shape
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                interactionTimestamp = System.currentTimeMillis()
                                onItemSelected(item)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatItem(item),
                            color = if (isSelected) Color.Black else Color(0xFFFAFAFA),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Wide AUTO Button spanning 2 columns
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(shape)
                    .background(if (isAutoSelected) Color(0xFFFAFAFA) else Color(0xFF18181B))
                    .border(
                        1.dp,
                        if (isAutoSelected) Color(0xFFFAFAFA) else Color(0xFF27272A),
                        shape
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        interactionTimestamp = System.currentTimeMillis()
                        onAutoSelected()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AUTO",
                    color = if (isAutoSelected) Color.Black else Color(0xFFA1A1AA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}
