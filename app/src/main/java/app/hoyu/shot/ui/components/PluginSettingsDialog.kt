package app.hoyu.shot.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import app.hoyu.shot.plugin.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.plugin.PluginInstance
import app.hoyu.shot.plugin.PluginManager
import app.hoyu.shot.plugin.SettingType

@Composable
fun PluginSettingsDialog(
    visible: Boolean,
    plugin: PluginInstance?,
    pluginManager: PluginManager,
    uiState: CameraUiState,
    previewBoundsInRoot: Rect = Rect.Zero,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastPlugin by remember { mutableStateOf<PluginInstance?>(null) }
    if (plugin != null) {
        lastPlugin = plugin
    }
    val activePlugin = plugin ?: lastPlugin ?: return

    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()
    val currentSettings = remember { mutableStateMapOf<String, Any>() }

    LaunchedEffect(activePlugin.manifest.id, visible) {
        if (visible) {
            val saved = pluginManager.getPluginSettings(activePlugin.manifest.id)
            currentSettings.clear()
            currentSettings.putAll(saved)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f) + slideInVertically(tween(200), initialOffsetY = { it / 10 }),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f) + slideOutVertically(tween(150), targetOffsetY = { it / 10 }),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            PopupGlassContainer(
                shape = shape,
                isBlurEnabled = uiState.isUiBlurEnabled,
                blurredBackdrop = uiState.blurredBackdrop,
                previewBoundsInRoot = previewBoundsInRoot,
                rotationDegrees = 0f,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Prevent click propagation
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PLUGIN // ${activePlugin.manifest.name.uppercase()}",
                                color = Color(0xFFFAFAFA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${activePlugin.manifest.version} • by ${activePlugin.manifest.author}",
                                color = Color(0xFF71717A),
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
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "[ESC]",
                                color = Color(0xFFA1A1AA),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (activePlugin.manifest.description.isNotBlank()) {
                        Text(
                            text = activePlugin.manifest.description,
                            color = Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }

                    HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                    if (activePlugin.manifest.settings.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF27272A), shape)
                                .background(Color(0xFF0F0F12))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NO CONFIGURATION OPTIONS FOR THIS PLUGIN",
                                color = Color(0xFF52525B),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            activePlugin.manifest.settings.forEach { item ->
                                when (item.type) {
                                    SettingType.TOGGLE -> {
                                        val checked = (currentSettings[item.key] as? Boolean)
                                            ?: (item.defaultValue as? Boolean)
                                            ?: false

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(shape)
                                                .background(if (checked) Color(0x22E11D48) else Color(0xFF18181B))
                                                .border(1.dp, if (checked) Color(0xFFE11D48) else Color(0xFF27272A), shape)
                                                .clickable {
                                                    view.triggerUiHaptic(uiHapticsEnabled)
                                                    val next = !checked
                                                    currentSettings[item.key] = next
                                                    pluginManager.savePluginSetting(activePlugin.manifest.id, item.key, next)
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    text = item.title.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                if (item.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = item.description,
                                                        color = Color(0xFFA1A1AA),
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(shape)
                                                    .background(if (checked) Color(0xFFE11D48) else Color(0xFF27272A))
                                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (checked) "ON" else "OFF",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
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
                                                .clip(shape)
                                                .background(Color(0xFF18181B))
                                                .border(1.dp, Color(0xFF27272A), shape)
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.title.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = String.format(java.util.Locale.US, "%.1f", sliderVal),
                                                    color = Color(0xFFE11D48),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            if (item.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.description,
                                                    color = Color(0xFFA1A1AA),
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Slider(
                                                value = sliderVal,
                                                onValueChange = { newVal -> sliderVal = newVal },
                                                onValueChangeFinished = {
                                                    view.triggerUiHaptic(uiHapticsEnabled)
                                                    currentSettings[item.key] = sliderVal
                                                    pluginManager.savePluginSetting(activePlugin.manifest.id, item.key, sliderVal)
                                                },
                                                valueRange = item.min..item.max,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Color(0xFFE11D48),
                                                    inactiveTrackColor = Color(0xFF27272A)
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
                                                .clip(shape)
                                                .background(Color(0xFF18181B))
                                                .border(1.dp, Color(0xFF27272A), shape)
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = item.title.uppercase(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (item.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.description,
                                                    color = Color(0xFFA1A1AA),
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                item.options.forEach { opt ->
                                                    val isSelected = opt == currentVal
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(shape)
                                                            .background(if (isSelected) Color(0xFFE11D48) else Color(0xFF141416))
                                                            .border(
                                                                1.dp,
                                                                if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A),
                                                                shape
                                                            )
                                                            .clickable {
                                                                view.triggerUiHaptic(uiHapticsEnabled)
                                                                currentSettings[item.key] = opt
                                                                pluginManager.savePluginSetting(activePlugin.manifest.id, item.key, opt)
                                                            }
                                                            .padding(vertical = 7.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = opt,
                                                            color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                                            fontSize = 9.sp,
                                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
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
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18181B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)),
                        shape = shape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CLOSE SETTINGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFAFAFA),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
