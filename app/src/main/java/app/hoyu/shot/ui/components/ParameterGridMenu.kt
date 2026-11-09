package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun <T> ParameterGridMenu(
    title: String,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    formatItem: (T) -> String,
    onAutoSelected: () -> Unit,
    isAutoSelected: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .width(185.dp)
            .heightIn(max = 320.dp)
            .clip(shape)
            .background(Color(0xD9141414))
            .border(1.dp, Color(0x44FFFFFF), shape)
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
                color = Color(0xFFFFD600),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                text = "✕",
                color = Color(0x88FFFFFF),
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2-Column Scrollable Grid of Values
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
                        .height(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0x33FFD600) else Color(0x33222222))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFFFD600) else Color(0x22FFFFFF),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onItemSelected(item)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatItem(item),
                        color = if (isSelected) Color(0xFFFFD600) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large 2-slot AUTO Button at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isAutoSelected) Color.White else Color(0x44222222))
                .border(
                    1.dp,
                    if (isAutoSelected) Color.White else Color(0x44FFFFFF),
                    RoundedCornerShape(4.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAutoSelected()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AUTO",
                color = if (isAutoSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}
