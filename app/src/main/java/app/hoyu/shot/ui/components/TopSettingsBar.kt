package app.hoyu.shot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.*

@Composable
fun TopSettingsBar(
    uiState: CameraUiState,
    onToggleFlash: () -> Unit,
    onToggleTimer: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleHorizon: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onSelectAspectRatio: (AspectRatio) -> Unit,
    onToggleRaw: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash Mode Button
        IconButtonToggle(
            icon = when (uiState.flashMode) {
                FlashMode.OFF -> Icons.Default.FlashOff
                FlashMode.AUTO -> Icons.Default.FlashAuto
                FlashMode.ON -> Icons.Default.FlashOn
                FlashMode.TORCH -> Icons.Default.Highlight
            },
            isActive = uiState.flashMode != FlashMode.OFF,
            onClick = onToggleFlash
        )

        // Timer Button
        TextButtonToggle(
            text = when (uiState.timerMode) {
                TimerMode.OFF -> "Timer"
                TimerMode.SEC_1 -> "1s"
                TimerMode.SEC_2 -> "2s"
                TimerMode.SEC_5 -> "5s"
                TimerMode.SEC_10 -> "10s"
            },
            isActive = uiState.timerMode != TimerMode.OFF,
            onClick = onToggleTimer
        )

        // Grid Button
        IconButtonToggle(
            icon = Icons.Default.GridOn,
            isActive = uiState.gridMode != GridMode.NONE,
            onClick = onToggleGrid
        )

        // Peaking Button
        TextButtonToggle(
            text = "PEAK",
            isActive = uiState.isFocusPeakingEnabled,
            onClick = onTogglePeaking
        )

        // Zebra Button
        TextButtonToggle(
            text = "ZEBRA",
            isActive = uiState.isZebraStripesEnabled,
            onClick = onToggleZebra
        )

        // Aspect Ratio Selector
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x80000000))
                .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9, AspectRatio.RATIO_1_1).forEach { ratio ->
                val isSelected = uiState.aspectRatio == ratio
                Text(
                    text = ratio.ratioName,
                    color = if (isSelected) Color(0xFFFFD600) else Color(0x88FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onSelectAspectRatio(ratio) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // RAW Toggle Button
        val rawActive = uiState.captureRaw
        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    if (rawActive) Color(0xFFFFD600) else Color(0x44FFFFFF),
                    RoundedCornerShape(4.dp)
                )
                .background(
                    if (rawActive) Color(0x33FFD600) else Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .clickable { onToggleRaw() }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "RAW",
                color = if (rawActive) Color(0xFFFFD600) else Color(0x88FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IconButtonToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isActive) Color(0x33FFD600) else Color(0x44000000))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color(0xFFFFD600) else Color.White,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun TextButtonToggle(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color(0x33FFD600) else Color(0x44000000))
            .border(0.5.dp, if (isActive) Color(0xFFFFD600) else Color(0x33FFFFFF), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color(0xFFFFD600) else Color(0xCCFFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
