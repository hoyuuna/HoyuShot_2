package app.hoyu.shot.model

import android.util.Range
import android.util.Rational

data class HardwareCapabilities(
    val cameraId: String,
    val isFacingBack: Boolean,
    val lensFacing: Int,
    val sensorOrientation: Int,
    val focalLengths: FloatArray,
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

    /**
     * Finds optimal preview size that matches target aspect ratio without stretching
     */
    fun findOptimalPreviewSize(targetRatio: AspectRatio, maxDimension: Int = 1920): Pair<Int, Int> {
        val targetRatioVal = when (targetRatio) {
            AspectRatio.RATIO_4_3 -> 4.0 / 3.0
            AspectRatio.RATIO_16_9 -> 16.0 / 9.0
            AspectRatio.RATIO_1_1 -> 1.0
            AspectRatio.RATIO_3_2 -> 3.0 / 2.0
        }

        // Camera sizes are width > height (landscape)
        val matchingSizes = supportedPreviewSizes.filter { (w, h) ->
            val ratio = w.toDouble() / h.toDouble()
            Math.abs(ratio - targetRatioVal) < 0.05
        }

        val pool = if (matchingSizes.isNotEmpty()) matchingSizes else supportedPreviewSizes

        // Prefer resolution around 1080p - 1440p for crisp, lag-free preview
        return pool.filter { (w, _) -> w <= maxDimension }
            .maxByOrNull { (w, h) -> w * h }
            ?: pool.maxByOrNull { (w, h) -> w * h }
            ?: Pair(1920, 1080)
    }

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
