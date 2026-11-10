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
import androidx.compose.material.icons.filled.Close
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
import app.hoyu.shot.updater.UpdateInfo
import app.hoyu.shot.updater.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AdvancedSettingsDialog(
    visible: Boolean,
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

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

    // Fixed portrait only container (always oriented upright)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(200)),
        exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 6 }, animationSpec = tween(150)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE000000))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    // Tap outside dismiss
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clip(shape)
                    .background(Color(0xF809090B)) // shadcn zinc-950
                    .border(1.dp, Color(0xFF27272A), shape)
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
                    Column {
                        Text(
                            text = "ADVANCED // CONFIG",
                            color = Color(0xFFFAFAFA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Fixed Portrait Mode • System Parameters",
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
                // SECTION 1: CAPTURE & JPEG QUALITY
                // ==========================================
                SectionHeader("01 // CAPTURE & SENSOR")

                // JPEG Quality
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TỈ LỆ NÉN JPEG",
                            color = Color(0xFFFAFAFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${uiState.jpegQuality}% ${if (uiState.jpegQuality == 95) "(KHUYẾN CÁO)" else ""}",
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

                // Mirror Selfie Toggle
                SettingsToggleRow(
                    title = "LẬT ẢNH SELFIE (MIRROR)",
                    subtitle = if (uiState.isMirrorSelfie) "Ảnh selfie được lật gương như màn hình" else "Ảnh lưu theo góc nhìn thực tế",
                    isActive = uiState.isMirrorSelfie,
                    onToggle = onToggleMirrorSelfie
                )

                // Antibanding Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ANTIBANDING (CHỐNG NHÁY ĐÈN)",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "TẮT", "AUTO" to "AUTO", "50HZ" to "50Hz", "60HZ" to "60Hz").forEach { (mode, label) ->
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
                // SECTION 2: GESTURES & HARDWARE BUTTONS
                // ==========================================
                SectionHeader("02 // THAO TÁC CẢM ỨNG & PHÍM CỨNG")

                // Tap Screen Action
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "NHẤN MÀN HÌNH (TAP VIEW)",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "TẮT", "FOCUS" to "FOCUS", "AE" to "AE ĐIỂM", "SHUTTER" to "CHỤP").forEach { (action, label) ->
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

                // Hold Screen Action
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GIỮ MÀN HÌNH (HOLD VIEW)",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OFF" to "TẮT", "FOCUS" to "FOCUS", "AE" to "AE ĐIỂM", "SHUTTER" to "CHỤP").forEach { (action, label) ->
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

                // Volume Up Button
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PHÍM TĂNG ÂM LƯỢNG (VOL +)",
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
                        listOf("OFF" to "TẮT", "SHUTTER" to "CHỤP", "AE_LOCK" to "KHÓA AE", "EV" to "+/- EV", "ZOOM" to "+/- ZOOM").forEach { (action, label) ->
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

                // Volume Down Button
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PHÍM GIẢM ÂM LƯỢNG (VOL -)",
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
                        listOf("OFF" to "TẮT", "SHUTTER" to "CHỤP", "AE_LOCK" to "KHÓA AE", "EV" to "+/- EV", "ZOOM" to "+/- ZOOM").forEach { (action, label) ->
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
                // SECTION 3: SYSTEM FEEDBACK & DISPLAY
                // ==========================================
                SectionHeader("03 // HỆ THỐNG & HIỂN THỊ")

                // Camera Sound / Shutter Haptics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PHẢN HỒI NÚT CHỤP (SHUTTER)",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF" to "TẮT (IM LẶNG)", "VIBRATE" to "RUNG HAPTIC").forEach { (mode, label) ->
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

                // UI Touch Haptics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PHẢN HỒI CẢM ỨNG GIAO DIỆN (UI)",
                        color = Color(0xFFFAFAFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF" to "TẮT", "VIBRATE" to "RUNG HAPTIC").forEach { (mode, label) ->
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

                // UI Blur Toggle
                SettingsToggleRow(
                    title = "BLUR GIAO DIỆN (FROSTED GLASS)",
                    subtitle = if (uiState.isUiBlurEnabled) "Nền popup mờ nhẹ phong cách Nothing OS" else "Nền đen tuyền nguyên bản",
                    isActive = uiState.isUiBlurEnabled,
                    onToggle = onToggleUiBlur
                )

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                // ==========================================
                // SECTION 4: IN-APP UPDATER
                // ==========================================
                SectionHeader("04 // CẬP NHẬT ỨNG DỤNG")

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
                                text = "TRÌNH CẬP NHẬT TRỰC TIẾP",
                                color = Color(0xFFFAFAFA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Phiên bản hiện tại: v${updateManager.currentVersionName}",
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
                                Text("ĐANG TẢI...", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("KIỂM TRA", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
                                    text = "✓ Bạn đang sử dụng phiên bản mới nhất (${res.currentVersion}).",
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
                                        text = "PHIÊN BẢN MỚI: ${res.info.tagName}",
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
                                        Text("CÀI ĐẶT BẢN CẬP NHẬT (INSTALL APK)", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            scope.launch {
                                                downloadProgress = 0
                                                downloadProgressText = "Đang bắt đầu tải..."
                                                val apk = updateManager.downloadApk(
                                                    downloadUrl = res.info.downloadUrl,
                                                    versionTag = res.info.tagName
                                                ) { bytesRead, totalBytes, percent ->
                                                    downloadProgress = percent
                                                    val readMb = bytesRead / (1024f * 1024f)
                                                    val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f
                                                    downloadProgressText = if (totalBytes > 0) {
                                                        "Tải $percent% [${String.format(java.util.Locale.US, "%.1f", readMb)} / ${String.format(java.util.Locale.US, "%.1f", totalMb)} MB]"
                                                    } else {
                                                        "Đang tải... ${String.format(java.util.Locale.US, "%.1f", readMb)} MB"
                                                    }
                                                }
                                                downloadProgress = null
                                                if (apk != null && apk.exists()) {
                                                    downloadedApkFile = apk
                                                    updateManager.installApk(apk)
                                                } else {
                                                    downloadProgressText = "Tải thất bại, vui lòng thử lại."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                        shape = shape,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TẢI VÀ CÀI ĐẶT NGAY", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                        null -> {}
                    }
                }

                // Auto update check toggle
                SettingsToggleRow(
                    title = "TỰ ĐỘNG KIỂM TRA BẢN MỚI",
                    subtitle = if (uiState.isAutoUpdateCheckEnabled) "Tự động kiểm tra trên GitHub khi mở ứng dụng" else "Chỉ kiểm tra khi nhấn thủ công",
                    isActive = uiState.isAutoUpdateCheckEnabled,
                    onToggle = onToggleAutoUpdateCheck
                )

                // Pre-release toggle
                SettingsToggleRow(
                    title = "NHẬN BẢN THỬ NGHIỆM (PRE-RELEASE)",
                    subtitle = if (uiState.isPreReleaseUpdateEnabled) "Nhận các bản cập nhật sớm nhất từ GitHub CI/CD" else "Chỉ nhận bản phát hành chính thức",
                    isActive = uiState.isPreReleaseUpdateEnabled,
                    onToggle = onTogglePreReleaseUpdate
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "HoyuShot Pro Camera • Tailored for Xiaomi 13 (2211133C)\nStrict Nothing OS Architecture // shadcn Design System",
                    color = Color(0xFF52525B),
                    fontSize = 8.sp,
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                text = if (isActive) "BẬT" else "TẮT",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
