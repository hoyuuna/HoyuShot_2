package app.hoyu.shot.ui.components

import android.os.Build
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.camera.CameraHardwareInspector
import app.hoyu.shot.model.Camera2LevelExplainer
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.LensItem

@Composable
fun SetupWizardScreen(
    initialStep: Int = 0,
    isCameraGranted: Boolean,
    isLocationGranted: Boolean,
    isAudioGranted: Boolean = true,
    isNotificationGranted: Boolean = true,
    onRequestCameraPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    onCompleteSetup: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val shape = RoundedCornerShape(2.dp)

    var currentStep by remember { mutableIntStateOf(initialStep.coerceIn(0, 2)) }
    val scrollState = rememberScrollState()

    // Inspect hardware dynamically when reaching step 2 or on demand
    val inspector = remember { CameraHardwareInspector(context) }
    var discoveredLenses by remember { mutableStateOf<List<LensItem>>(emptyList()) }
    var primaryCapabilities by remember { mutableStateOf<HardwareCapabilities?>(null) }

    LaunchedEffect(currentStep, isCameraGranted) {
        if (currentStep == 2 && isCameraGranted) {
            try {
                val lenses = inspector.discoverAllLenses()
                discoveredLenses = lenses
                val primaryBack = lenses.find { !it.isFront && it.cameraId == "0" }
                    ?: lenses.firstOrNull { !it.isFront }
                    ?: lenses.firstOrNull()
                val targetId = primaryBack?.cameraId ?: "0"
                primaryCapabilities = inspector.inspectCamera(targetId)
            } catch (e: Exception) {
                // Fallback safe probe
                primaryCapabilities = inspector.inspectCamera("0")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Navigation & Step Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYS // SETUP WIZARD",
                        color = Color(0xFFE11D48),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "INITIALIZATION & SENSOR CALIBRATION",
                        color = Color(0xFF71717A),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Step Pills (01, 02, 03)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StepPill(step = 1, active = currentStep == 0, completed = currentStep > 0)
                    StepPill(step = 2, active = currentStep == 1, completed = currentStep > 1)
                    StepPill(step = 3, active = currentStep == 2, completed = false)
                }
            }

            HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Body Content based on currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentStep) {
                    0 -> WelcomePage(
                        onNext = {
                            view.triggerUiHaptic(true)
                            currentStep = 1
                        }
                    )
                    1 -> PermissionsPage(
                        isCameraGranted = isCameraGranted,
                        isLocationGranted = isLocationGranted,
                        isNotificationGranted = isNotificationGranted,
                        onRequestCamera = onRequestCameraPermission,
                        onRequestLocation = onRequestLocationPermission,
                        onRequestNotification = onRequestNotificationPermission,
                        onBack = {
                            view.triggerUiHaptic(true)
                            currentStep = 0
                        },
                        onNext = {
                            if (isCameraGranted) {
                                view.triggerUiHaptic(true)
                                currentStep = 2
                            }
                        }
                    )
                    2 -> HardwareAuditPage(
                        capabilities = primaryCapabilities,
                        lenses = discoveredLenses,
                        onBack = {
                            view.triggerUiHaptic(true)
                            currentStep = 1
                        },
                        onFinish = {
                            view.triggerUiHaptic(true)
                            onCompleteSetup()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPill(step: Int, active: Boolean, completed: Boolean) {
    val shape = RoundedCornerShape(2.dp)
    val bgColor = when {
        active -> Color(0xFFE11D48)
        completed -> Color(0xFF10B981)
        else -> Color(0xFF18181B)
    }
    val textColor = if (active || completed) Color.White else Color(0xFF71717A)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, if (active) Color(0xFFE11D48) else Color(0xFF27272A), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "0$step",
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ==========================================
// STEP 1: WELCOME PAGE
// ==========================================
@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Hero Title Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF18181B))
                    .border(1.dp, Color(0xFF27272A), shape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERSION 1.0 // DSLR ENGINE",
                        color = Color(0xFFE11D48),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981), shape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "READY",
                            color = Color(0xFF10B981),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "HOYUSHOT",
                        color = Color(0xFFFAFAFA),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = " •",
                        color = Color(0xFFE11D48),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = "Professional manual DSLR camera system for Android, providing direct hardware control over optical sensors.",
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Highlights
            HighlightCard(
                tag = "01 // SENSOR DIRECT CONTROL",
                title = "INDEPENDENT EXPOSURE TRIANGLE",
                description = "Granular nanosecond shutter speed (SEC) from 1/8000s to 30s, ISO gain from 50 to 6400+, optical diopter manual focus, and Kelvin white balance."
            )

            HighlightCard(
                tag = "02 // UNCOMPRESSED RAW & CALIBRATION",
                title = "12-BIT RAW DNG CAPTURE",
                description = "Acquire uncompressed raw sensor data alongside calibrated Natural and Vivid photographic color engines with skin-tone protection."
            )

            HighlightCard(
                tag = "03 // PROFESSIONAL METRICS",
                title = "VIEWFINDER & SCOPES",
                description = "Real-time multi-channel RGB/Luma histogram, dual-axis horizon level indicator, independent Spot AE metering, and antibanding filter."
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
        ) {
            Text(
                text = "START SETUP // PROCEED  →",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HighlightCard(tag: String, title: String, description: String) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF18181B))
            .border(1.dp, Color(0xFF27272A), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = tag,
            color = Color(0xFFE11D48),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = title,
            color = Color(0xFFFAFAFA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = description,
            color = Color(0xFFA1A1AA),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ==========================================
// STEP 2: PERMISSIONS PAGE
// ==========================================
@Composable
private fun PermissionsPage(
    isCameraGranted: Boolean,
    isLocationGranted: Boolean,
    isNotificationGranted: Boolean,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestNotification: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "02 // SYSTEM PERMISSIONS",
                    color = Color(0xFFFAFAFA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Grant system permissions to interface directly with device hardware sensors.",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // SECTION A: REQUIRED
            Text(
                text = "REQUIRED PERMISSIONS (MUST BE GRANTED TO PROCEED)",
                color = Color(0xFFE11D48),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            PermissionCard(
                title = "CAMERA SENSOR ACCESS",
                systemPermission = "android.permission.CAMERA",
                description = "Direct access to optical sensors and real-time DSLR viewfinder stream. Required to operate the camera.",
                isRequired = true,
                isGranted = isCameraGranted,
                actionLabel = "GRANT CAMERA PERMISSION",
                onAction = onRequestCamera
            )

            // SECTION B: OPTIONAL
            Text(
                text = "OPTIONAL PERMISSIONS (CAN BE SKIPPED)",
                color = Color(0xFFA1A1AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            PermissionCard(
                title = "GEOGRAPHIC LOCATION (GPS)",
                systemPermission = "android.permission.ACCESS_FINE_LOCATION",
                description = "Embeds WGS84 coordinates into EXIF photo metadata and enables real-time Sun Compass solar tracking.",
                isRequired = false,
                isGranted = isLocationGranted,
                actionLabel = "ENABLE LOCATION",
                onAction = onRequestLocation
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "SYSTEM NOTIFICATIONS",
                    systemPermission = "android.permission.POST_NOTIFICATIONS",
                    description = "Receive release alerts when new HoyuShot builds are available on GitHub.",
                    isRequired = false,
                    isGranted = isNotificationGranted,
                    actionLabel = "ENABLE NOTIFICATIONS",
                    onAction = onRequestNotification
                )
            }

            // Status Notice Banner
            if (!isCameraGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                        .border(1.dp, Color(0xFFEF4444), shape)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚠ ATTENTION: Camera permission is required. You must grant Camera access to proceed to the hardware audit.",
                        color = Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF10B981).copy(alpha = 0.10f))
                        .border(1.dp, Color(0xFF10B981), shape)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "✓ Camera sensor access is active. Click 'PROCEED' to inspect device hardware capabilities.",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA1A1AA)),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    text = "← BACK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onNext,
                enabled = isCameraGranted,
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE11D48),
                    disabledContainerColor = Color(0xFF27272A),
                    disabledContentColor = Color(0xFF71717A)
                )
            ) {
                Text(
                    text = "PROCEED  →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    systemPermission: String,
    description: String,
    isRequired: Boolean,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF18181B))
            .border(1.dp, if (isRequired && !isGranted) Color(0xFFE11D48).copy(alpha = 0.5f) else Color(0xFF27272A), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFFFAFAFA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = systemPermission,
                    color = Color(0xFF71717A),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else (if (isRequired) Color(0xFFE11D48).copy(alpha = 0.15f) else Color(0xFF27272A)))
                    .border(1.dp, if (isGranted) Color(0xFF10B981) else (if (isRequired) Color(0xFFE11D48) else Color(0xFF71717A)), shape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isGranted) "✓ GRANTED" else (if (isRequired) "REQUIRED" else "OPTIONAL"),
                    color = if (isGranted) Color(0xFF10B981) else (if (isRequired) Color(0xFFE11D48) else Color(0xFFA1A1AA)),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = description,
            color = Color(0xFFA1A1AA),
            fontSize = 9.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace
        )

        if (!isGranted) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRequired) Color(0xFFE11D48) else Color(0xFF27272A)
                )
            ) {
                Text(
                    text = actionLabel,
                    color = if (isRequired) Color.White else Color(0xFFFAFAFA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ==========================================
// STEP 3: HARDWARE AUDIT PAGE
// ==========================================
@Composable
private fun HardwareAuditPage(
    capabilities: HardwareCapabilities?,
    lenses: List<LensItem>,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    val scrollState = rememberScrollState()

    val hwLevelName = capabilities?.hardwareLevelName ?: "PROBING"
    val levelInfo = remember(hwLevelName) { Camera2LevelExplainer.getLevelInfo(hwLevelName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "03 // HARDWARE COMPATIBILITY",
                    color = Color(0xFFFAFAFA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "DEVICE: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL.uppercase()} (ANDROID ${Build.VERSION.RELEASE})",
                    color = Color(0xFF71717A),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Main Level Evaluation Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF18181B))
                    .border(1.dp, levelInfo.badgeColor.copy(alpha = 0.7f), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HARDWARE SUPPORT LEVEL",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
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
                            text = if (levelInfo.isFullySupported) "FULL SUPPORT" else "RESTRICTED",
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
                    fontSize = 14.sp,
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

                HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)

                Text(
                    text = levelInfo.detailedExplanation,
                    color = Color(0xFFD4D4D8),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Capabilities Checklist Card
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
                    text = "SENSOR CAPABILITIES",
                    color = Color(0xFFE11D48),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                AuditChecklistRow(
                    label = "MANUAL SENSOR (ISO / SEC)",
                    isSupported = capabilities?.supportsManualSensor == true,
                    note = "Independent shutter duration & sensor sensitivity"
                )

                AuditChecklistRow(
                    label = "RAW SENSOR STREAM (RAW DNG)",
                    isSupported = capabilities?.supportsRawCapture == true,
                    note = "10/12-bit uncompressed sensor data recording"
                )

                AuditChecklistRow(
                    label = "OPTICAL MANUAL FOCUS (MF DIOPTER)",
                    isSupported = (capabilities?.minFocusDistanceDiopters ?: 0f) > 0f,
                    note = "Physical lens diopter distance override"
                )

                AuditChecklistRow(
                    label = "MANUAL AWB & COLOR MATRIX",
                    isSupported = capabilities?.supportsManualPostProcessing == true,
                    note = "Kelvin color temperature lock & tone matrix control"
                )
            }

            // Discovered Lenses (if any)
            if (lenses.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "DISCOVERED OPTICAL LENSES (${lenses.size} LENSES)",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    lenses.forEach { lens ->
                        Text(
                            text = "• ${lens.label} (Camera ID ${lens.cameraId})",
                            color = Color(0xFFFAFAFA),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA1A1AA)),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    text = "← BACK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(2f)
                    .height(48.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Text(
                    text = "COMPLETE & LAUNCH VIEWFINDER  →",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun AuditChecklistRow(
    label: String,
    isSupported: Boolean,
    note: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(0xFFFAFAFA),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = note,
                color = Color(0xFF71717A),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = if (isSupported) "✓ SUPPORTED" else "✕ UNSUPPORTED",
            color = if (isSupported) Color(0xFF10B981) else Color(0xFFEF4444),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
