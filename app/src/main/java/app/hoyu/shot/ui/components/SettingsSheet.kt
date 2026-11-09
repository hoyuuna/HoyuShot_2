package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.hoyu.shot.model.CameraUiState

@Composable
fun SettingsDialog(
    uiState: CameraUiState,
    onToggleHistogram: () -> Unit,
    onToggleHorizon: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(12.dp)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(shape)
                .background(Color(0xE6141414))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAMERA SETTINGS",
                    color = Color(0xFFFFD600),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "✕",
                    color = Color(0x88FFFFFF),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Storage Path Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "STORAGE PATH", color = Color(0x88FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(text = "DCIM/HoyuShot", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Grid of Toggle Tiles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingTile(
                    title = "HISTOGRAM",
                    icon = Icons.Default.BarChart,
                    isEnabled = uiState.isHistogramVisible,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleHistogram()
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingTile(
                    title = "HORIZON LEVEL",
                    icon = Icons.Default.LineWeight,
                    isEnabled = uiState.isHorizonLevelVisible,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleHorizon()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingTile(
                    title = "FOCUS PEAKING",
                    icon = Icons.Default.CenterFocusStrong,
                    isEnabled = uiState.isFocusPeakingEnabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTogglePeaking()
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingTile(
                    title = "ZEBRA STRIPES",
                    icon = Icons.Default.Warning,
                    isEnabled = uiState.isZebraStripesEnabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleZebra()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingTile(
    title: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(shape)
            .background(if (isEnabled) Color(0x33FFD600) else Color(0x22222222))
            .border(1.dp, if (isEnabled) Color(0xFFFFD600) else Color(0x22FFFFFF), shape)
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFFFFD600) else Color(0x88FFFFFF),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isEnabled) "ON" else "OFF",
                color = if (isEnabled) Color(0xFFFFD600) else Color(0x66FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = title,
            color = if (isEnabled) Color.White else Color(0xAAFFFFFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
