package app.hoyu.shot.model

import androidx.compose.ui.graphics.Color

/**
 * Technical specification and evaluation of camera hardware support levels.
 */
data class Camera2LevelInfo(
    val levelName: String,
    val title: String,
    val badgeLabel: String,
    val badgeColor: Color,
    val isFullySupported: Boolean,
    val summary: String,
    val detailedExplanation: String,
    val manualControlsStatus: String,
    val rawCaptureStatus: String,
    val perFrameControlStatus: String
)

object Camera2LevelExplainer {

    fun getLevelInfo(levelName: String): Camera2LevelInfo {
        return when (levelName) {
            "LEVEL_3" -> Camera2LevelInfo(
                levelName = "LEVEL_3",
                title = "LEVEL 3 // MAXIMUM SUPPORT",
                badgeLabel = "OPTIMAL // FULL + RAW",
                badgeColor = Color(0xFF10B981), // Green
                isFullySupported = true,
                summary = "Full feature set. Supports all manual DSLR controls, zero-lag preview, and uncompressed RAW DNG.",
                detailedExplanation = "Hardware provides the highest level of camera control. Supports unrestricted manual shutter speed (SEC), manual ISO sensitivity, optical diopter manual focus, 12-bit uncompressed RAW DNG capture, and per-frame exposure synchronization.",
                manualControlsStatus = "Fully Supported (ISO, SEC, MF Diopter, EV, AWB Lock)",
                rawCaptureStatus = "Supported (10/12-bit Adobe DNG Sensor Stream)",
                perFrameControlStatus = "Zero-latency per-frame exposure synchronization"
            )
            "FULL" -> Camera2LevelInfo(
                levelName = "FULL",
                title = "FULL // PROFESSIONAL DSLR CONTROLS",
                badgeLabel = "SUPPORTED // FULL MANUAL",
                badgeColor = Color(0xFF10B981), // Green
                isFullySupported = true,
                summary = "Full manual controls supported. Fully compatible with M, S, ISO, and Program AE modes.",
                detailedExplanation = "Hardware supports comprehensive manual shooting controls: independent shutter duration (SEC), sensor ISO gain, manual focus distance, exposure compensation (EV), and manual white balance lock, with full 30fps burst capture.",
                manualControlsStatus = "Fully Supported (Manual ISO, Shutter, MF, EV)",
                rawCaptureStatus = "Supported (Sensor dependent)",
                perFrameControlStatus = "Supports per-frame capture parameter locking"
            )
            "LIMITED" -> Camera2LevelInfo(
                levelName = "LIMITED",
                title = "LIMITED // RESTRICTED CAPABILITY",
                badgeLabel = "PARTIAL // RESTRICTED",
                badgeColor = Color(0xFFF59E0B), // Amber
                isFullySupported = false,
                summary = "Restricted capabilities. Device manufacturer has locked certain direct sensor controls.",
                detailedExplanation = "Sensor operates at LIMITED tier. The manufacturer restricts advanced hardware controls: basic capture and partial tuning work, but key features like uncompressed RAW DNG or extreme shutter speeds may be unavailable or clamped.",
                manualControlsStatus = "Partial Support (Manufacturer restricted)",
                rawCaptureStatus = "Typically Unsupported (No RAW DNG output)",
                perFrameControlStatus = "No guaranteed inter-frame parameter synchronization"
            )
            "LEGACY" -> Camera2LevelInfo(
                levelName = "LEGACY",
                title = "LEGACY // BASIC AUTOMATIC ONLY",
                badgeLabel = "UNSUPPORTED // LEGACY",
                badgeColor = Color(0xFFEF4444), // Red
                isFullySupported = false,
                summary = "Unsupported tier. Device only supports basic automatic mode; manual controls are disabled.",
                detailedExplanation = "Hardware only runs in legacy compatibility mode. Direct manual adjustments (manual ISO, shutter speed, optical focus diopter, and RAW DNG) are unsupported by the device.",
                manualControlsStatus = "Unsupported (Legacy compatibility mode)",
                rawCaptureStatus = "Unsupported",
                perFrameControlStatus = "No per-frame control support"
            )
            "EXTERNAL" -> Camera2LevelInfo(
                levelName = "EXTERNAL",
                title = "EXTERNAL // USB CAMERA",
                badgeLabel = "EXTERNAL // USB UVC",
                badgeColor = Color(0xFF06B6D4), // Cyan
                isFullySupported = false,
                summary = "External USB camera attached. Features depend on the connected device.",
                detailedExplanation = "External camera connected over USB. Manual controls depend entirely on the external hardware driver.",
                manualControlsStatus = "Dependent on external device driver",
                rawCaptureStatus = "Unsupported",
                perFrameControlStatus = "Dependent on USB UVC stream"
            )
            else -> Camera2LevelInfo(
                levelName = levelName,
                title = "$levelName // UNKNOWN TIER",
                badgeLabel = "UNKNOWN STATUS",
                badgeColor = Color(0xFF71717A), // Gray
                isFullySupported = false,
                summary = "Hardware reported an unrecognized tier ($levelName).",
                detailedExplanation = "Hardware reported level $levelName. Specific manual shooting capabilities must be audited via individual sensor metrics.",
                manualControlsStatus = "Probing...",
                rawCaptureStatus = "Probing...",
                perFrameControlStatus = "Probing..."
            )
        }
    }

    /**
     * Complete list of all camera hardware levels for the reference guide.
     */
    val allLevelsReference: List<Camera2LevelInfo> by lazy {
        listOf(
            getLevelInfo("LEVEL_3"),
            getLevelInfo("FULL"),
            getLevelInfo("LIMITED"),
            getLevelInfo("LEGACY"),
            getLevelInfo("EXTERNAL")
        )
    }
}
