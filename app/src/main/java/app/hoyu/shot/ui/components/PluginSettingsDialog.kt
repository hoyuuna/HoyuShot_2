package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
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
import app.hoyu.shot.plugin.PluginInstance
import app.hoyu.shot.plugin.PluginManager
import app.hoyu.shot.plugin.SettingType

@Composable
fun PluginSettingsDialog(
    plugin: PluginInstance,
    pluginManager: PluginManager,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val currentSettings = remember { mutableStateMapOf<String, Any>() }

    LaunchedEffect(plugin.manifest.id) {
        val saved = pluginManager.getPluginSettings(plugin.manifest.id)
        currentSettings.clear()
        currentSettings.putAll(saved)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = plugin.manifest.name.uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "v${plugin.manifest.version} • by ${plugin.manifest.author}",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (plugin.manifest.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plugin.manifest.description,
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                Spacer(modifier = Modifier.height(16.dp))

                if (plugin.manifest.settings.isEmpty()) {
                    Text(
                        text = "This plugin does not provide custom settings schema.",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    plugin.manifest.settings.forEach { item ->
                        when (item.type) {
                            SettingType.TOGGLE -> {
                                val checked = (currentSettings[item.key] as? Boolean)
                                    ?: (item.defaultValue as? Boolean)
                                    ?: false

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable {
                                            val newVal = !checked
                                            currentSettings[item.key] = newVal
                                            pluginManager.savePluginSetting(plugin.manifest.id, item.key, newVal)
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (item.description.isNotBlank()) {
                                            Text(
                                                text = item.description,
                                                color = Color(0xFF888888),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { newVal ->
                                            currentSettings[item.key] = newVal
                                            pluginManager.savePluginSetting(plugin.manifest.id, item.key, newVal)
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFE53935)
                                        )
                                    )
                                }
                            }
                            SettingType.SLIDER -> {
                                val rawVal = (currentSettings[item.key] as? Number)?.toFloat()
                                    ?: (item.defaultValue as? Number)?.toFloat()
                                    ?: item.min
                                var sliderVal by remember(rawVal) { mutableFloatStateOf(rawVal) }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", sliderVal),
                                            color = Color(0xFFE53935),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (item.description.isNotBlank()) {
                                        Text(
                                            text = item.description,
                                            color = Color(0xFF888888),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Slider(
                                        value = sliderVal,
                                        onValueChange = { newVal ->
                                            sliderVal = newVal
                                        },
                                        onValueChangeFinished = {
                                            currentSettings[item.key] = sliderVal
                                            pluginManager.savePluginSetting(plugin.manifest.id, item.key, sliderVal)
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        },
                                        valueRange = item.min..item.max,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color(0xFFE53935)
                                        )
                                    )
                                }
                            }
                            SettingType.DROPDOWN -> {
                                val currentVal = (currentSettings[item.key] as? String)
                                    ?: (item.defaultValue as? String)
                                    ?: item.options.firstOrNull() ?: ""

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (item.description.isNotBlank()) {
                                        Text(
                                            text = item.description,
                                            color = Color(0xFF888888),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item.options.forEach { opt ->
                                            val isSelected = opt == currentVal
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) Color(0xFFE53935) else Color(0xFF222222))
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color(0xFFFF5252) else Color(0xFF333333),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        currentSettings[item.key] = opt
                                                        pluginManager.savePluginSetting(plugin.manifest.id, item.key, opt)
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = opt,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                ) {
                    Text(
                        text = "DONE",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
