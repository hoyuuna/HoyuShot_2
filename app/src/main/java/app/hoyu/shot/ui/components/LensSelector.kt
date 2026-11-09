package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraDeviceInfo

@Composable
fun LensSelector(
    cameras: List<CameraDeviceInfo>,
    currentCameraId: String,
    onCameraSelect: (String) -> Unit,
    onFlipFacing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    // Group cameras into back lenses and front
    val backLenses = cameras.filter { it.isBack }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Back lens buttons (e.g. 0.5x, 1x, 2x, 3x)
        backLenses.forEach { cam ->
            val isSelected = cam.cameraId == currentCameraId
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFFFD600) else Color.Transparent)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCameraSelect(cam.cameraId)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cam.label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Flip Front/Back Camera button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onFlipFacing()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
