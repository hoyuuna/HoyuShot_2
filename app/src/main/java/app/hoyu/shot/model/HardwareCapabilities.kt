package app.hoyu.shot.model

import android.util.Range
import android.util.Rational

data class HardwareCapabilities(
    val cameraId: String,
    val isFacingBack: Boolean,
    val hardwareLevelName: String,
    val isLevelFullOrHigher: Boolean,
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRawCapture: Boolean,
    val isoRange: Range<Int>?,
    val shutterSpeedRangeNanos: Range<Long>?,
    val minFocusDistanceDiopters: Float,
    val evCompensationRange: Range<Int>?,
    val evCompensationStep: Rational,
    val supportedPreviewSizes: List<Pair<Int, Int>>,
    val supportedJpegSizes: List<Pair<Int, Int>>,
    val supportedRawSizes: List<Pair<Int, Int>>
) {
    val supportsManualFocus: Boolean
        get() = minFocusDistanceDiopters > 0f

    val formattedMinShutterSpeed: String
        get() = formatShutterSpeed(shutterSpeedRangeNanos?.lower ?: 100_000L)

    val formattedMaxShutterSpeed: String
        get() = formatShutterSpeed(shutterSpeedRangeNanos?.upper ?: 1_000_000_000L)

    companion object {
        fun formatShutterSpeed(nanos: Long): String {
            val seconds = nanos / 1_000_000_000.0
            return when {
                seconds >= 1.0 -> String.format("%.1fs", seconds)
                seconds > 0.0 -> {
                    val denom = Math.round(1.0 / seconds)
                    "1/${denom}s"
                }
                else -> "Auto"
            }
        }
    }
}

