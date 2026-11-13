package app.hoyu.shot.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.hoyu.shot.model.Camera2LevelExplainer
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.LensItem

@Composable
fun CameraHardwareLevelDialog(
    visible: Boolean,
    capabilities: HardwareCapabilities?,
    availableLenses: List<LensItem> = emptyList(),
    currentCameraId: String = "0",
    onDismiss: () -> Unit
) {
    if (!visible) return

    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    val hwLevelName = capabilities?.hardwareLevelName ?: "UNKNOWN"
    val levelInfo = remember(hwLevelName) { Camera2LevelExplainer.getLevelInfo(hwLevelName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(shape)
                    .background(Color(0xFF09090B))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                // Top Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SYS // HARDWARE COMPATIBILITY",
                            color = Color(0xFFE11D48),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL.uppercase()} • SENSOR ID $currentCameraId",
                            color = Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color(0xFF27272A), shape)
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "[CLOSE]",
                            color = Color(0xFFFAFAFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Current Active Sensor Hardware Level Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, levelInfo.badgeColor.copy(alpha = 0.6f), shape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE CAMERA SENSOR",
                                color = Color(0xFFA1A1AA),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(levelInfo.badgeColor.copy(alpha = 0.15f))
                                    .border(1.dp, levelInfo.badgeColor, shape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = levelInfo.badgeLabel,
                                    color = levelInfo.badgeColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = levelInfo.title,
                            color = Color(0xFFFAFAFA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = levelInfo.summary,
                            color = if (levelInfo.isFullySupported) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = levelInfo.detailedExplanation,
                            color = Color(0xFFD4D4D8),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // 2. Hardware Capabilities Checklist
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color(0xFF27272A), shape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ACTUAL SENSOR CAPABILITIES",
                            color = Color(0xFFE11D48),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        CapabilityRow(
                            label = "MANUAL SENSOR (ISO / SHUTTER SPEED)",
                            isSupported = capabilities?.supportsManualSensor == true,
                            detail = if (capabilities?.supportsManualSensor == true) "Independent manual ISO & shutter duration supported" else "Locked or clamped by manufacturer driver"
                        )

                        CapabilityRow(
                            label = "RAW SENSOR OUTPUT (ADOBE DNG)",
                            isSupported = capabilities?.supportsRawCapture == true,
                            detail = if (capabilities?.supportsRawCapture == true) "Uncompressed 10/12-bit RAW DNG stream supported" else "No raw sensor buffer output capability"
                        )

                        CapabilityRow(
                            label = "MANUAL FOCUS DIOPTER (MF RING)",
                            isSupported = (capabilities?.minFocusDistanceDiopters ?: 0f) > 0f,
                            detail = if ((capabilities?.minFocusDistanceDiopters ?: 0f) > 0f) "Physical optical diopter focus distance supported" else "Fixed focus or no manual diopter control"
                        )

                        CapabilityRow(
                            label = "MANUAL POST-PROCESSING (AWB LOCK)",
                            isSupported = capabilities?.supportsManualPostProcessing == true,
                            detail = if (capabilities?.supportsManualPostProcessing == true) "Kelvin color temperature lock & tone matrix control" else "Basic automatic AWB only"
                        )
                    }

                    // 3. Discovered Multi-Camera Lenses (if any)
                    if (availableLenses.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(Color(0xFF18181B))
                                .border(1.dp, Color(0xFF27272A), shape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DISCOVERED OPTICAL LENSES (${availableLenses.size} LENSES)",
                                color = Color(0xFFA1A1AA),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            availableLenses.forEach { lens ->
                                val isCurrent = lens.cameraId == currentCameraId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .background(if (isCurrent) Color(0xFF27272A) else Color.Transparent)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${lens.label} (ID ${lens.cameraId})",
                                        color = if (isCurrent) Color(0xFFE11D48) else Color(0xFFFAFAFA),
                                        fontSize = 10.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (isCurrent) {
                                        Text(
                                            text = "[ACTIVE]",
                                            color = Color(0xFFE11D48),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Reference Guide: All Camera2 Hardware Levels Explained
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color(0xFF27272A), shape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "HARDWARE COMPATIBILITY TIERS GUIDE",
                            color = Color(0xFFE11D48),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "Hardware compatibility is categorized into distinct tiers based on manual sensor capabilities:",
                            color = Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Camera2LevelExplainer.allLevelsReference.forEach { ref ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(Color(0xFF09090B))
                                    .border(1.dp, Color(0xFF27272A), shape)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ref.title,
                                        color = Color(0xFFFAFAFA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(shape)
                                            .background(ref.badgeColor.copy(alpha = 0.15f))
                                            .border(1.dp, ref.badgeColor, shape)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (ref.isFullySupported) "FULL SUPPORT" else "RESTRICTED",
                                            color = ref.badgeColor,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = ref.detailedExplanation,
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    isSupported: Boolean,
    detail: String
) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF09090B))
            .border(1.dp, Color(0xFF27272A), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFFAFAFA),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isSupported) "✓ SUPPORTED" else "✕ UNSUPPORTED",
                color = if (isSupported) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = detail,
            color = Color(0xFF71717A),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
