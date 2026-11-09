package app.hoyu.shot.model

data class HistogramData(
    val lumaBins: IntArray = IntArray(256),
    val redBins: IntArray = IntArray(256),
    val greenBins: IntArray = IntArray(256),
    val blueBins: IntArray = IntArray(256),
    val maxCount: Int = 1
)

data class HorizonOrientation(
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val isLevel: Boolean = false
)

data class CameraUiState(
    val isCameraReady: Boolean = false,
    val errorMessage: String? = null,
    val currentCameraId: String = "0",
    val capabilities: HardwareCapabilities? = null,

    // Multi-Camera & Optical Lenses
    val availableLenses: List<LensItem> = emptyList(),
    val currentLens: LensItem? = null,
    val currentZoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 0.6f,
    val maxZoomRatio: Float = 30.0f,

    // Preview Dimensions & Aspect Ratio
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val previewSize: Pair<Int, Int> = Pair(1920, 1440),

    // Manual Exposure Controls
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int = 100,
    val shutterSpeedNanos: Long = 10_000_000L,
    val focusDistanceDiopters: Float = 0f,
    val isManualFocusEnabled: Boolean = false,
    val evCompensationIndex: Int = 0,
    val whiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val kelvinTemperature: Int = 5500,

    // Live Readout from Sensor
    val actualIso: Int = 100,
    val actualShutterSpeedNanos: Long = 10_000_000L,

    // Capture & Format options
    val captureRaw: Boolean = false,
    val isCapturing: Boolean = false,
    val lastCapturedUri: String? = null,

    // On-Screen Pro Aids
    val isHistogramVisible: Boolean = true,
    val isHorizonLevelVisible: Boolean = true,
    val isFocusPeakingEnabled: Boolean = false,
    val isZebraStripesEnabled: Boolean = false,

    // Real-time sensor states
    val histogram: HistogramData = HistogramData(),
    val horizon: HorizonOrientation = HorizonOrientation(),

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
