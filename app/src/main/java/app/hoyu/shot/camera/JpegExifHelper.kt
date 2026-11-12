package app.hoyu.shot.camera

import android.hardware.camera2.CameraCharacteristics
import android.location.Location
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.hoyu.shot.model.ExposureMode
import app.hoyu.shot.model.FlashMode
import app.hoyu.shot.model.WhiteBalancePreset
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

data class CaptureExifSnapshot(
    val captureTimeMs: Long,
    val characteristics: CameraCharacteristics?,
    val exposureMode: ExposureMode,
    @Volatile var iso: Int,
    @Volatile var exposureNanos: Long,
    val evIndex: Int,
    val evStep: Float,
    val whiteBalancePreset: WhiteBalancePreset,
    val flashMode: FlashMode,
    @Volatile var flashFired: Boolean,
    val digitalZoomRatio: Float,
    val tapAeActive: Boolean,
    val location: Location?,
    val isExifSoftwareTagEnabled: Boolean,
    val deviceJpegOrientation: Int
)

/**
 * Preserves HAL EXIF metadata across Bitmap re-encoding (HDR / Natural Color / Mirror Selfie)
 * and guarantees complete photographic, lens, flash, and timestamp EXIF tags on every JPEG.
 */
object JpegExifHelper {

    private val PRESERVED_HAL_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_LIGHT_SOURCE
    )

    fun extractHalExif(rawJpegBytes: ByteArray): Map<String, String> {
        return try {
            val halExif = ExifInterface(ByteArrayInputStream(rawJpegBytes))
            buildMap {
                for (tag in PRESERVED_HAL_TAGS) {
                    val value = halExif.getAttribute(tag)
                    if (!value.isNullOrBlank()) {
                        put(tag, value)
                    }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun writeCompleteExif(
        exif: ExifInterface,
        halTags: Map<String, String>,
        snapshot: CaptureExifSnapshot,
        imgWidth: Int,
        imgHeight: Int,
        finalOrientation: Int
    ) {
        // 1. Restore all HAL EXIF tags that Bitmap.compress() stripped
        for ((tag, value) in halTags) {
            if (exif.getAttribute(tag).isNullOrBlank()) {
                exif.setAttribute(tag, value)
            }
        }

        // 2. Dimensions & Orientation
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, finalOrientation.toString())
        if (imgWidth > 0) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, imgWidth.toString())
            exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, imgWidth.toString())
        }
        if (imgHeight > 0) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, imgHeight.toString())
            exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, imgHeight.toString())
        }

        // 3. Date, Time, Subsec, and Timezone Offset
        val captureDate = Date(snapshot.captureTimeMs)
        val exifDateTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(captureDate)
        val subsec = SimpleDateFormat("SSS", Locale.US).format(captureDate)
        val offsetRaw = SimpleDateFormat("XXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(captureDate)
        val offsetTime = if (offsetRaw == "Z") "+00:00" else offsetRaw

        if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateTime)
        }
        if (exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifDateTime)
        }
        if (exif.getAttribute(ExifInterface.TAG_DATETIME).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifDateTime)
        }
        if (exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
        }
        if (exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subsec)
        }
        if (exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, subsec)
        }
        if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetTime)
        }
        if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetTime)
        }
        if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetTime)
        }

        // 4. GPS Location
        val loc = snapshot.location
        if (loc != null) {
            exif.setGpsInfo(loc)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (loc.latitude >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (loc.longitude >= 0) "E" else "W")
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, loc.provider ?: "GPS")
        }

        // 5. Device Make, Model & Software
        val makeName = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        if (makeName.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_MAKE, makeName)
        }
        if (!Build.MODEL.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        }
        if (snapshot.isExifSoftwareTagEnabled) {
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Hoyuuna HoyuShot")
        }

        // 6. Exposure, ISO, Shutter, Bias, Metering, Mode, Program
        val chars = snapshot.characteristics
        val apertureFloat = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull()?.takeIf { it > 0f } ?: 1.8f
        val focalFloat = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.takeIf { it > 0f } ?: 5.0f

        val existingIso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            .takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
        if (existingIso <= 0) {
            exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, snapshot.iso.coerceAtLeast(1).toString())
        }
        if (exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0) <= 0) {
            exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, snapshot.iso.coerceAtLeast(1).toString())
        }

        val exposureSec = (snapshot.exposureNanos.coerceAtLeast(1L)) / 1_000_000_000.0
        if (exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0) <= 0.0) {
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, String.format(Locale.US, "%.6f", exposureSec))
        }
        if (exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE).isNullOrBlank()) {
            val tv = -ln(exposureSec) / ln(2.0)
            exif.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, toSignedRational(tv))
        }

        if (exif.getAttribute(ExifInterface.TAG_EXPOSURE_MODE).isNullOrBlank()) {
            val modeVal = when (snapshot.exposureMode) {
                ExposureMode.AUTO, ExposureMode.PROGRAM -> "0" // Auto exposure
                ExposureMode.SHUTTER_PRIORITY, ExposureMode.ISO_PRIORITY, ExposureMode.MANUAL -> "1" // Manual
            }
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, modeVal)
        }
        if (exif.getAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM).isNullOrBlank()) {
            val programVal = when (snapshot.exposureMode) {
                ExposureMode.AUTO, ExposureMode.PROGRAM -> "2" // Normal program
                ExposureMode.SHUTTER_PRIORITY -> "4" // Shutter priority
                ExposureMode.ISO_PRIORITY -> "3" // Aperture/Sensitivity priority
                ExposureMode.MANUAL -> "1" // Manual
            }
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, programVal)
        }
        if (exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE).isNullOrBlank()) {
            val biasEv = snapshot.evIndex * snapshot.evStep.toDouble()
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, toSignedRational(biasEv))
        }
        if (exif.getAttribute(ExifInterface.TAG_METERING_MODE).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_METERING_MODE, if (snapshot.tapAeActive) "3" else "5")
        }

        // 7. Lens Info, Aperture, Focal Length & 35mm Equivalent
        if (exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0) <= 0.0) {
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, String.format(Locale.US, "%.2f", apertureFloat))
        }
        val av = 2.0 * ln(apertureFloat.toDouble().coerceAtLeast(0.5)) / ln(2.0)
        if (exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_APERTURE_VALUE, toSignedRational(av))
        }
        if (exif.getAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, toSignedRational(av))
        }

        if (exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0) <= 0.0) {
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, String.format(Locale.US, "%.2f", focalFloat))
        }
        if (exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) <= 0) {
            val eq35 = compute35mmFocal(chars, focalFloat)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, eq35.toString())
        }
        if (exif.getAttribute(ExifInterface.TAG_LENS_MAKE).isNullOrBlank() && makeName.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_LENS_MAKE, makeName)
        }
        if (exif.getAttribute(ExifInterface.TAG_LENS_MODEL).isNullOrBlank()) {
            val facingLabel = when (chars?.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Rear"
            }
            val focalStr = String.format(Locale.US, "%.1fmm", focalFloat)
            val apStr = String.format(Locale.US, "f/%.1f", apertureFloat)
            val lensModel = listOfNotNull(
                makeName.takeIf { it.isNotBlank() },
                Build.MODEL?.takeIf { it.isNotBlank() },
                facingLabel,
                focalStr,
                apStr
            ).joinToString(" ")
            if (lensModel.isNotBlank()) {
                exif.setAttribute(ExifInterface.TAG_LENS_MODEL, lensModel)
            }
        }
        if (exif.getAttribute(ExifInterface.TAG_LENS_SPECIFICATION).isNullOrBlank()) {
            val fRat = toPositiveRational(focalFloat.toDouble())
            val aRat = toPositiveRational(apertureFloat.toDouble())
            exif.setAttribute(ExifInterface.TAG_LENS_SPECIFICATION, "$fRat,$fRat,$aRat,$aRat")
        }

        // 8. Flash Tag (EXIF 2.3 bitmask)
        if (exif.getAttribute(ExifInterface.TAG_FLASH).isNullOrBlank()) {
            val hasFlash = chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val flashCode = when {
                !hasFlash -> 32 // No flash function
                snapshot.flashMode == FlashMode.OFF -> 16 // Off, did not fire
                snapshot.flashMode == FlashMode.ON || snapshot.flashMode == FlashMode.TORCH -> 9 // On, fired
                snapshot.flashMode == FlashMode.AUTO -> if (snapshot.flashFired) 25 else 24 // Auto fired / not fired
                else -> 16
            }
            exif.setAttribute(ExifInterface.TAG_FLASH, flashCode.toString())
        }

        // 9. White Balance, Digital Zoom, Color Space, Scene Capture Type
        if (exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE).isNullOrBlank()) {
            val wbVal = if (snapshot.whiteBalancePreset == WhiteBalancePreset.AUTO) "0" else "1"
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, wbVal)
        }
        if (exif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO).isNullOrBlank() && snapshot.digitalZoomRatio > 1.0f) {
            exif.setAttribute(
                ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                String.format(Locale.US, "%.2f", snapshot.digitalZoomRatio)
            )
        }
        if (exif.getAttribute(ExifInterface.TAG_COLOR_SPACE).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1") // sRGB
        }
        if (exif.getAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE).isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, "0") // Standard
        }
    }

    private fun compute35mmFocal(chars: CameraCharacteristics?, focalFloat: Float): Int {
        val sensorSize = chars?.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (sensorSize != null && sensorSize.width > 0f && sensorSize.height > 0f) {
            val diagonal = hypot(sensorSize.width.toDouble(), sensorSize.height.toDouble())
            if (diagonal > 0.0) {
                return (focalFloat * (43.27 / diagonal)).roundToInt().coerceAtLeast(1)
            }
        }
        return (focalFloat * 5.0f).roundToInt().coerceAtLeast(1)
    }

    private fun toPositiveRational(value: Double): String {
        val scaled = (value * 100.0).roundToInt().coerceAtLeast(1)
        return "$scaled/100"
    }

    private fun toSignedRational(value: Double): String {
        val scaled = (value * 100.0).roundToInt()
        return "$scaled/100"
    }
}
