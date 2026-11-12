package app.hoyu.shot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.model.CustomModePreset
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.WhiteBalancePreset

/**
 * Formats a concise single-line summary of exposure, WB, ratio, and format for preset cards & dialogs.
 */
fun formatCustomPresetSummary(
    shutterSpeedNanos: Long,
    iso: Int,
    whiteBalancePreset: WhiteBalancePreset,
    kelvinTemperature: Int,
    aspectRatioName: String,
    captureFormatLabel: String
): String {
    val shutterText = HardwareCapabilities.formatShutterSpeed(shutterSpeedNanos)
    val wbText = if (whiteBalancePreset == WhiteBalancePreset.CUSTOM_KELVIN) {
        "${kelvinTemperature}K"
    } else {
        whiteBalancePreset.label
    }
    return "$shutterText • ISO $iso • $wbText • $aspectRatioName • $captureFormatLabel"
}

@Composable
private fun CustomModeModalFrame(
    visible: Boolean,
    isBlurEnabled: Boolean,
    blurredBackdrop: ImageBitmap?,
    previewBoundsInRoot: Rect,
    rotationDegrees: Float,
    borderAccent: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.rotateBounds(rotationDegrees),
                contentAlignment = Alignment.Center
            ) {
                PopupGlassContainer(
                    shape = shape,
                    isBlurEnabled = isBlurEnabled,
                    blurredBackdrop = blurredBackdrop,
                    previewBoundsInRoot = previewBoundsInRoot,
                    rotationDegrees = rotationDegrees,
                    modifier = Modifier
                        .width(296.dp)
                        .then(
                            if (borderAccent != null) {
                                Modifier.border(1.dp, borderAccent, shape)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun CustomModeNameDialog(
    visible: Boolean,
    uiState: CameraUiState,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(2.dp)
    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (visible) {
            nameInput = ""
        }
    }

    val summary = formatCustomPresetSummary(
        shutterSpeedNanos = uiState.shutterSpeedNanos,
        iso = uiState.iso,
        whiteBalancePreset = uiState.whiteBalancePreset,
        kelvinTemperature = uiState.kelvinTemperature,
        aspectRatioName = uiState.aspectRatio.ratioName,
        captureFormatLabel = uiState.captureFormat.label
    )

    CustomModeModalFrame(
        visible = visible,
        isBlurEnabled = uiState.isUiBlurEnabled,
        blurredBackdrop = uiState.blurredBackdrop,
        previewBoundsInRoot = previewBoundsInRoot,
        rotationDegrees = rotationDegrees,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM // NEW PRESET",
                    color = Color(0xFFFAFAFA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "[ESC]",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable {
                            keyboardController?.hide()
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onDismiss()
                        }
                        .padding(4.dp)
                )
            }

            // Snapshot preview strip showing base mode and locked parameters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF141418))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(Color(0xFFE11D48))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = uiState.exposureMode.label,
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = summary,
                    color = Color(0xFFA1A1AA),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }

            Text(
                text = "Preset settings are read-only once saved. Editing any parameter detaches back to ${uiState.exposureMode.label} mode.",
                color = Color(0xFF71717A),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF09090B))
                    .border(1.dp, Color(0xFF3F3F46), shape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                if (nameInput.isEmpty()) {
                    Text(
                        text = "Enter preset name...",
                        color = Color(0xFF52525B),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = nameInput,
                    onValueChange = { raw ->
                        val singleLine = raw.replace("\n", "").replace("\r", "")
                        if (singleLine.length <= 24) {
                            nameInput = singleLine
                        }
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFFFAFAFA)),
                    textStyle = TextStyle(
                        color = Color(0xFFFAFAFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onConfirm(nameInput.trim())
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomDialogActionButton(
                    label = "CANCEL",
                    background = Color(0xFF18181B),
                    borderColor = Color(0xFF27272A),
                    textColor = Color(0xFFA1A1AA),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        keyboardController?.hide()
                        view.triggerUiHaptic(uiHapticsEnabled)
                        onDismiss()
                    }
                )
                CustomDialogActionButton(
                    label = "SAVE",
                    background = Color(0xFFFAFAFA),
                    borderColor = Color(0xFFFAFAFA),
                    textColor = Color(0xFF09090B),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        keyboardController?.hide()
                        view.triggerUiHaptic(uiHapticsEnabled)
                        onConfirm(nameInput.trim())
                    }
                )
            }
        }
    }
}

@Composable
fun CustomModeDeleteDialog(
    preset: CustomModePreset?,
    isBlurEnabled: Boolean,
    blurredBackdrop: ImageBitmap?,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onConfirmDelete: (CustomModePreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    var cachedPreset by remember { mutableStateOf<CustomModePreset?>(null) }
    if (preset != null) {
        cachedPreset = preset
    }
    val displayPreset = preset ?: cachedPreset

    CustomModeModalFrame(
        visible = preset != null,
        isBlurEnabled = isBlurEnabled,
        blurredBackdrop = blurredBackdrop,
        previewBoundsInRoot = previewBoundsInRoot,
        rotationDegrees = rotationDegrees,
        borderAccent = Color(0xFFE11D48),
        modifier = modifier
    ) {
        if (displayPreset != null) {
            val summary = formatCustomPresetSummary(
                shutterSpeedNanos = displayPreset.shutterSpeedNanos,
                iso = displayPreset.iso,
                whiteBalancePreset = displayPreset.whiteBalancePreset,
                kelvinTemperature = displayPreset.kelvinTemperature,
                aspectRatioName = displayPreset.aspectRatio.ratioName,
                captureFormatLabel = displayPreset.captureFormat.label
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CUSTOM // DELETE",
                        color = Color(0xFFE11D48),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "[ESC]",
                        color = Color(0xFFA1A1AA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                view.triggerUiHaptic(uiHapticsEnabled)
                                onDismiss()
                            }
                            .padding(4.dp)
                    )
                }

                Text(
                    text = "Delete custom preset \"${displayPreset.name}\"?",
                    color = Color(0xFFFAFAFA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "[${displayPreset.baseMode.label}] $summary",
                    color = Color(0xFFA1A1AA),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )

                Text(
                    text = "This action permanently removes the saved custom profile.",
                    color = Color(0xFF71717A),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomDialogActionButton(
                        label = "CANCEL",
                        background = Color(0xFF18181B),
                        borderColor = Color(0xFF27272A),
                        textColor = Color(0xFFA1A1AA),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onDismiss()
                        }
                    )
                    CustomDialogActionButton(
                        label = "DELETE",
                        background = Color(0xFFE11D48),
                        borderColor = Color(0xFFE11D48),
                        textColor = Color(0xFFFAFAFA),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onConfirmDelete(displayPreset)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomModeErrorDialog(
    errorMessage: String?,
    isBlurEnabled: Boolean,
    blurredBackdrop: ImageBitmap?,
    previewBoundsInRoot: Rect = Rect.Zero,
    rotationDegrees: Float = 0f,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    var cachedError by remember { mutableStateOf("") }
    if (!errorMessage.isNullOrBlank()) {
        cachedError = errorMessage
    }

    CustomModeModalFrame(
        visible = !errorMessage.isNullOrBlank(),
        isBlurEnabled = isBlurEnabled,
        blurredBackdrop = blurredBackdrop,
        previewBoundsInRoot = previewBoundsInRoot,
        rotationDegrees = rotationDegrees,
        borderAccent = Color(0xFFE11D48),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM // ERROR",
                    color = Color(0xFFE11D48),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "[ESC]",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable {
                            view.triggerUiHaptic(uiHapticsEnabled)
                            onDismiss()
                        }
                        .padding(4.dp)
                )
            }

            Text(
                text = cachedError,
                color = Color(0xFFFAFAFA),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )

            CustomDialogActionButton(
                label = "OK",
                background = Color(0xFFFAFAFA),
                borderColor = Color(0xFFFAFAFA),
                textColor = Color(0xFF09090B),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    view.triggerUiHaptic(uiHapticsEnabled)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun CustomDialogActionButton(
    label: String,
    background: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}
