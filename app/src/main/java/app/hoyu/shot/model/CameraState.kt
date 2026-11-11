package app.hoyu.shot.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap

data class HistogramData(
    val lumaBins: IntArray = IntArray(256),
    val redBins: IntArray = IntArray(256),
    val greenBins: IntArray = IntArray(256),
    val blueBins: IntArray = IntArray(256),
    val maxCount: Int = 1
)

data class ViewfinderOverlayData(
    val cols: Int = 0,
    val rows: Int = 0,
    val peakingStrength: ByteArray = ByteArray(0),
    val zebraMask: ByteArray = ByteArray(0),
    val frameId: Long = 0L
)

data class HorizonOrientation(
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val isRollLevel: Boolean = false,
    val isPitchLevel: Boolean = false,
    val isLevel: Boolean = false
)

data class CameraResolution(
    val width: Int,
    val height: Int,
    val mpLabel: String
)

enum class FocusState {
    IDLE,
    FOCUSING,
    LOCKED_SUCCESS,
    LOCKED_FAILED
}

data class TapFocusPoint(
    val offset: Offset,
    val state: FocusState = FocusState.FOCUSING,
    val timestamp: Long = System.currentTimeMillis()
)

data class CameraUiState(
    val isCameraReady: Boolean = false,
    val errorMessage: String? = null,
    val currentCameraId: String = "0",
    val capabilities: HardwareCapabilities? = null,

    // Dynamic In-place Element Rotation (0, 90, 180, 270)
    val uiRotationDegrees: Float = 0f,

    // Multi-Camera & Optical Lenses
    val availableLenses: List<LensItem> = emptyList(),
    val currentLens: LensItem? = null,
    val currentZoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 0.6f,
    val maxZoomRatio: Float = 30.0f,

    // Per-Camera Internal Digital Zoom (1.0f to max, never jumps camera)
    val digitalZoomRatio: Float = 1.0f,

    // Preview Dimensions & Aspect Ratio
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val previewSize: Pair<Int, Int> = Pair(1920, 1440),

    // Tap-to-Focus & Locks
    val tapFocusPoint: TapFocusPoint? = null,
    val isAeLocked: Boolean = false,
    val isAfLocked: Boolean = false,

    // Spot AE Metering (Independent of AF)
    val tapAePoint: Offset? = null,
    val isSpotAeActive: Boolean = false,

    // Manual Exposure Controls
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int = 100,
    val shutterSpeedNanos: Long = 10_000_000L,
    val focusDistanceDiopters: Float = 0f,
    val isManualFocusEnabled: Boolean = false,
    val evCompensationIndex: Int = 0,
    val whiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val kelvinTemperature: Int = 5500,

    // Image Processing: NR & Sharpness
    val noiseReductionMode: NoiseReductionMode = NoiseReductionMode.FAST,
    val sharpnessMode: SharpnessMode = SharpnessMode.NORMAL,
    val isResumeSettingsEnabled: Boolean = false,

    // Live Readout from Sensor
    val actualIso: Int = 100,
    val actualShutterSpeedNanos: Long = 10_000_000L,

    // Capture, Timer & Format options
    val captureFormat: CaptureFormat = CaptureFormat.JPEG,
    val availableResolutions: List<CameraResolution> = emptyList(),
    val selectedResolution: CameraResolution? = null,
    val selectedMegapixels: String = "12 MP",
    val captureRaw: Boolean = false,
    val isCapturing: Boolean = false,
    val isLongExposing: Boolean = false,
    val timerMode: TimerMode = TimerMode.OFF,
    val timerCountdownSeconds: Int? = null,
    val flashMode: FlashMode = FlashMode.OFF,

    // Gallery & Review
    val lastCapturedPhoto: CapturedPhotoInfo? = null,
    val recentPhotos: List<CapturedPhotoInfo> = emptyList(),
    val isReviewDialogVisible: Boolean = false,
    val isGalleryVisible: Boolean = false,

    // Viewfinder Pro Aids & Overlays
    val gridMode: GridMode = GridMode.NONE,
    val isHistogramVisible: Boolean = false,
    val isHorizonLevelVisible: Boolean = true,
    val isFocusPeakingEnabled: Boolean = false,
    val isZebraStripesEnabled: Boolean = false,
    val isGeotaggingEnabled: Boolean = false,

    // Advanced Config States
    val jpegQuality: Int = 95,
    val isMirrorSelfie: Boolean = false,
    val cameraSoundMode: String = "VIBRATE",
    val uiHapticsMode: String = "VIBRATE",
    val tapAction: String = "FOCUS",
    val holdAction: String = "AE",
    val volumeUpAction: String = "SHUTTER",
    val volumeDownAction: String = "SHUTTER",
    val antibandingMode: String = "AUTO",
    val isUiBlurEnabled: Boolean = true,
    val isAutoUpdateCheckEnabled: Boolean = true,
    val isPreReleaseUpdateEnabled: Boolean = true,
    val isTeleZoomFallbackEnabled: Boolean = true,

    // Real-time sensor states
    val histogram: HistogramData = HistogramData(),
    val overlayAnalysis: ViewfinderOverlayData = ViewfinderOverlayData(),
    val blurredBackdrop: ImageBitmap? = null,
    val horizon: HorizonOrientation = HorizonOrientation(),

    // Semi-Auto Bounds / Limits (Auto ISO Min Shutter Speed & Max ISO Limit)
    val minShutterLimitNanos: Long? = null,
    val maxIsoLimit: Int? = null,

    // Shutter curtain animation trigger & simulated telephoto base zoom
    val shutterBlinkTrigger: Long = 0L,
    val simulatedTeleBaseZoom: Float = 1.0f,

    // UI Active parameter dial
    val activeDialParameter: DialParameter? = null
)

enum class DialParameter {
    ISO,
    SHUTTER,
    FOCUS,
    EV,
    WHITE_BALANCE
}
