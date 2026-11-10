package app.hoyu.shot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CameraUiState
import app.hoyu.shot.updater.UpdateCheckResult
import app.hoyu.shot.updater.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AdvancedSettingsDialog(
    visible: Boolean,
    scrollToUpdates: Boolean = false,
    uiState: CameraUiState,
    onJpegQualityChanged: (Int) -> Unit,
    onToggleMirrorSelfie: () -> Unit,
    onAntibandingChanged: (String) -> Unit,
    onTapActionChanged: (String) -> Unit,
    onHoldActionChanged: (String) -> Unit,
    onVolumeUpActionChanged: (String) -> Unit,
    onVolumeDownActionChanged: (String) -> Unit,
    onCameraSoundChanged: (String) -> Unit,
    onUiHapticsChanged: (String) -> Unit,
    onToggleUiBlur: () -> Unit,
    onToggleAutoUpdateCheck: () -> Unit,
    onTogglePreReleaseUpdate: () -> Unit,
    onToggleTeleZoomFallback: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val updateManager = remember { UpdateManager(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadProgressText by remember { mutableStateOf("") }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(visible, scrollToUpdates) {
        if (visible && scrollToUpdates) {
            delay(250)
            scrollState.animateScrollTo(scrollState.maxValue)
            if (updateResult == null && !isCheckingUpdate) {
                isCheckingUpdate = true
                scope.launch {
                    val res = updateManager.checkForUpdates(includePreRelease = uiState.isPreReleaseUpdateEnabled)
                    updateResult = res
                    isCheckingUpdate = false
                    delay(120)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    }

    // Fixed portrait modal with smooth scale + fade + slide open/close animation
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
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clip(shape)
                    .background(Color(0xF809090B))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        // Prevent dismiss when clicking inside dialog
                    }
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
                    Text(
                        text = "SYS // ADVANCED CONFIG",
                        color = Color(0xFFFAFAFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color(0xFF27272A), shape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ==========================================
                // SECTION 1: CAPTURE & SENSOR
                // ==========================================
                SectionHeader("01 // CAPTURE & SENSOR")

                // JPEG Quality
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "JPEG COMPRESSION",
                            color = Color(0xFFFAFAFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${uiState.jpegQuality}% ${if (uiState.jpegQuality == 95) "(REC)" else ""}",
                            color = if (uiState.jpegQuality == 95) Color(0xFFE11D48) else Color(0xFFA1A1AA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 75, 90, 95, 99, 100).forEach { q ->
                            val isSelected = uiState.jpegQuality == q
                            ChipItem(
                                label = if (q == 95) "95% (REC)" else "$q%",
                                isSelected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onJpegQualityChanged(q)
                                }
                            )
                        }
                    }
                }

                // Mirror Front Camera Toggle
                SettingsToggleRow(
                    title = "MIRROR FRONT CAMERA",
                    subtitle = if (uiState.isMirrorSelfie) "Save selfies mirrored as previewed" else "Save standard non-mirrored photo",
                    isActive = uiState.isMirrorSelfie,
                    onToggle = onToggleMirrorSelfie
                )

                // Aux Lens Zoom Fallback (Ultrawide & Telephoto HAL bypass)
                SettingsToggleRow(
                    title = "AUX LENS ZOOM FALLBACK",
                    subtitle = if (uiState.isTeleZoomFallbackEnabled) "Last resort bypass: simulates auxiliary lenses via primary camera zoom if physical sensor access is blocked by HAL" else "Standard physical sensor switch (reverts to 1x without zoom if blocked by HAL)",
                    isActive = uiState.isTeleZoomFallbackEnabled,
                    onToggle = onToggleTeleZoomFallback
                )

                // Antibanding Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ANTIBANDING",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "OFF", "AUTO" to "AUTO", "50HZ" to "50Hz", "60HZ" to "60Hz").forEach { (mode, label) ->
                            val isSelected = uiState.antibandingMode.equals(mode, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onAntibandingChanged(mode)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ==========================================
                // SECTION 2: GESTURES & HARDWARE KEYS
                // ==========================================
                SectionHeader("02 // GESTURES & HARDWARE KEYS")

                // Tap Viewport Action
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TAP VIEWPORT",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "OFF", "FOCUS" to "FOCUS", "AE" to "SPOT AE", "SHUTTER" to "SHUTTER").forEach { (action, label) ->
                            val isSelected = uiState.tapAction.equals(action, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onTapActionChanged(action)
                                }
                            )
                        }
                    }
                }

                // Hold Viewport Action
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "HOLD VIEWPORT",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "OFF", "FOCUS" to "FOCUS", "AE" to "SPOT AE", "SHUTTER" to "SHUTTER").forEach { (action, label) ->
                            val isSelected = uiState.holdAction.equals(action, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onHoldActionChanged(action)
                                }
                            )
                        }
                    }
                }

                // Volume Up Key
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VOLUME UP KEY",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "OFF", "SHUTTER" to "SHUTTER", "AE_LOCK" to "AE LOCK", "EV" to "+/- EV", "ZOOM" to "+/- ZOOM").forEach { (action, label) ->
                            val isSelected = uiState.volumeUpAction.equals(action, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onVolumeUpActionChanged(action)
                                }
                            )
                        }
                    }
                }

                // Volume Down Key
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VOLUME DOWN KEY",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "OFF", "SHUTTER" to "SHUTTER", "AE_LOCK" to "AE LOCK", "EV" to "+/- EV", "ZOOM" to "+/- ZOOM").forEach { (action, label) ->
                            val isSelected = uiState.volumeDownAction.equals(action, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onVolumeDownActionChanged(action)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ==========================================
                // SECTION 3: FEEDBACK & INTERACTION
                // ==========================================
                SectionHeader("03 // FEEDBACK & INTERACTION")

                // Shutter Feedback (Strictly OFF / VIBRATE)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SHUTTER FEEDBACK",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF" to "OFF", "VIBRATE" to "VIBRATE").forEach { (mode, label) ->
                            val isSelected = uiState.cameraSoundMode.equals(mode, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onCameraSoundChanged(mode)
                                }
                            )
                        }
                    }
                }

                // UI Haptic Feedback (Strictly OFF / VIBRATE)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "UI HAPTIC FEEDBACK",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF" to "OFF", "VIBRATE" to "VIBRATE").forEach { (mode, label) ->
                            val isSelected = uiState.uiHapticsMode.equals(mode, ignoreCase = true)
                            ChipItem(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onUiHapticsChanged(mode)
                                }
                            )
                        }
                    }
                }

                // Interface Blur Toggle
                SettingsToggleRow(
                    title = "INTERFACE BLUR",
                    subtitle = if (uiState.isUiBlurEnabled) "Translucent frosted popup backdrops" else "Solid opaque black panels",
                    isActive = uiState.isUiBlurEnabled,
                    onToggle = onToggleUiBlur
                )

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ==========================================
                // SECTION 4: SYSTEM UPDATES
                // ==========================================
                SectionHeader("04 // SYSTEM UPDATES")

                // Update Action Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "OTA UPDATE MANAGER",
                                color = Color(0xFFFAFAFA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Current installed: v${updateManager.currentVersionName}",
                                color = Color(0xFF71717A),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isCheckingUpdate = true
                                updateResult = null
                                downloadProgress = null
                                scope.launch {
                                    val res = updateManager.checkForUpdates(includePreRelease = uiState.isPreReleaseUpdateEnabled)
                                    updateResult = res
                                    isCheckingUpdate = false
                                }
                            },
                            enabled = !isCheckingUpdate && downloadProgress == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE11D48),
                                disabledContainerColor = Color(0xFF27272A)
                            ),
                            shape = shape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CHECKING...", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CHECK", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    // Result Display
                    when (val res = updateResult) {
                        is UpdateCheckResult.LatestVersion -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(Color(0x2210B981))
                                    .border(1.dp, Color(0xFF10B981), shape)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "✓ App is up to date (${res.currentVersion}).",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        is UpdateCheckResult.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(Color(0x22EF4444))
                                    .border(1.dp, Color(0xFFEF4444), shape)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "✕ ${res.message}",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        is UpdateCheckResult.UpdateAvailable -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(Color(0x22E11D48))
                                    .border(1.dp, Color(0xFFE11D48), shape)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "UPDATE AVAILABLE: ${res.info.tagName}",
                                        color = Color(0xFFFF4D6D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (res.info.isPreRelease) {
                                        Text(
                                            text = "[PRE-RELEASE]",
                                            color = Color(0xFFF59E0B),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (res.info.changelog.isNotBlank()) {
                                    Text(
                                        text = res.info.changelog.take(200),
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 4
                                    )
                                }

                                // Download & Install Button or Progress
                                if (downloadProgress != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = (downloadProgress ?: 0) / 100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(shape),
                                            color = Color(0xFFE11D48),
                                            trackColor = Color(0xFF27272A)
                                        )
                                        Text(
                                            text = downloadProgressText,
                                            color = Color(0xFFFAFAFA),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else if (downloadedApkFile != null) {
                                    Button(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            updateManager.installApk(downloadedApkFile!!)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = shape,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("INSTALL APK", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            scope.launch {
                                                downloadProgress = 0
                                                downloadProgressText = "Connecting..."
                                                val apk = updateManager.downloadApk(
                                                    downloadUrl = res.info.downloadUrl,
                                                    versionTag = res.info.tagName
                                                ) { bytesRead, totalBytes, percent ->
                                                    downloadProgress = percent
                                                    val readMb = bytesRead / (1024f * 1024f)
                                                    val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f
                                                    downloadProgressText = if (totalBytes > 0) {
                                                        "Downloading: $percent% [${String.format(java.util.Locale.US, "%.1f", readMb)} / ${String.format(java.util.Locale.US, "%.1f", totalMb)} MB]"
                                                    } else {
                                                        "Downloading: ${String.format(java.util.Locale.US, "%.1f", readMb)} MB"
                                                    }
                                                }
                                                downloadProgress = null
                                                if (apk != null && apk.exists()) {
                                                    downloadedApkFile = apk
                                                    updateManager.installApk(apk)
                                                } else {
                                                    downloadProgressText = "Download failed, please try again."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                        shape = shape,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("DOWNLOAD & INSTALL", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                        null -> {}
                    }
                }

                // Auto update check toggle
                SettingsToggleRow(
                    title = "CHECK UPDATES ON BOOT",
                    subtitle = if (uiState.isAutoUpdateCheckEnabled) "Automatically checks GitHub on app launch" else "Check manually only",
                    isActive = uiState.isAutoUpdateCheckEnabled,
                    onToggle = onToggleAutoUpdateCheck
                )

                // Pre-release toggle
                SettingsToggleRow(
                    title = "INCLUDE PRE-RELEASES",
                    subtitle = if (uiState.isPreReleaseUpdateEnabled) "Receive early builds from GitHub CI/CD" else "Stable releases only",
                    isActive = uiState.isPreReleaseUpdateEnabled,
                    onToggle = onTogglePreReleaseUpdate
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Footer exactly as requested
                Text(
                    text = "HoyuShot • Made with love by Hoyuuna",
                    color = Color(0xFF71717A),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFFE11D48),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) Color(0xFFE11D48) else Color(0xFF18181B))
            .border(1.dp, if (isSelected) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFA1A1AA),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isActive) Color(0x22E11D48) else Color(0xFF18181B))
            .border(1.dp, if (isActive) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle()
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
                text = title,
                color = if (isActive) Color(0xFFFF4D6D) else Color(0xFFFAFAFA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF71717A),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (isActive) Color(0xFFE11D48) else Color(0xFF27272A))
                .padding(horizontal = 9.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isActive) "ON" else "OFF",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
