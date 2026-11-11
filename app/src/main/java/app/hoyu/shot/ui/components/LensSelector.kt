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
import app.hoyu.shot.model.LensItem

@Composable
fun LensSelector(
    lenses: List<LensItem>,
    currentLens: LensItem?,
    onLensSelect: (LensItem) -> Unit,
    onFlipFacing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val backLenses = lenses.filter { !it.isFront }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Back lens buttons (e.g. 0.6x, 1x, 3.2x)
        backLenses.forEach { lens ->
            val isSelected = currentLens?.id == lens.id
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFFFD600) else Color(0x22FFFFFF))
                    .clickable {
                        view.triggerUiHaptic(uiHapticsEnabled)
                        onLensSelect(lens)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Flip Front/Back Camera button
        val isFrontSelected = currentLens?.isFront == true
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isFrontSelected) Color(0xFFFFD600) else Color(0x33FFFFFF))
                .clickable {
                    view.triggerUiHaptic(uiHapticsEnabled)
                    onFlipFacing()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = if (isFrontSelected) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
