package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CaptureFormat
import kotlinx.coroutines.delay

@Composable
fun FormatMenu(
    currentMp: String,
    currentFormat: CaptureFormat,
    onMpSelected: (String) -> Unit,
    onFormatSelected: (CaptureFormat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)

    // 5-second inactivity auto-close timer
    var interactionEpoch by remember { mutableStateOf(0L) }
    LaunchedEffect(interactionEpoch) {
        delay(5000)
        onDismiss()
    }

    val mpOptions = listOf("12.6 MP", "6.2 MP", "2.8 MP")
    val formatOptions = listOf(CaptureFormat.JPEG, CaptureFormat.RAW, CaptureFormat.RAW_JPEG)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xE609090B)) // shadcn zinc-950 semi-translucent
            .border(1.dp, Color(0xFF27272A), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Megapixels Selection
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            mpOptions.forEach { mp ->
                val isSelected = mp == currentMp
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
                            onMpSelected(mp)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mp,
                        color = if (isSelected) Color.Black else Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
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
