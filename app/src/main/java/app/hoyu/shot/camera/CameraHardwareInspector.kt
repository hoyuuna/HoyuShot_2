package app.hoyu.shot.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Rational
import app.hoyu.shot.model.CameraDeviceInfo
import app.hoyu.shot.model.HardwareCapabilities
import kotlin.math.roundToInt

class CameraHardwareInspector(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun inspectAllAvailableCameras(): List<CameraDeviceInfo> {
        val list = mutableListOf<Pair<String, CameraCharacteristics>>()
        try {
            for (id in cameraManager.cameraIdList) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    list.add(Pair(id, chars))
                } catch (e: Exception) {
                    // ignore inaccessible camera
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val backCameras = list.filter {
            it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        val frontCameras = list.filter {
            it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }

        // Determine base focal length for back cameras (standard wide ~ 4mm to 6mm)
        val sortedBack = backCameras.sortedBy { (_, chars) ->
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 5.0f
        }

        // The base 1x is usually the primary camera (often id "0" or around ~4-6mm)
        val baseBackFocal = sortedBack.firstOrNull { (_, chars) ->
            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 5.0f
            focal >= 3.5f // standard wide is >= 3.5mm; ultra-wides are < 3.5mm
        }?.let { (_, chars) ->
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        } ?: sortedBack.firstOrNull()?.let { (_, chars) ->
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        } ?: 5.0f

        val result = mutableListOf<CameraDeviceInfo>()

        // Process Back cameras with optical zoom labels
        for ((id, chars) in sortedBack) {
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(5.0f)
            val primaryFocal = focalLengths.firstOrNull() ?: 5.0f
            val ratio = primaryFocal / baseBackFocal

            val label = when {
                ratio < 0.75f -> "0.5x"
                ratio in 0.85f..1.25f -> "1x"
                ratio in 1.75f..2.4f -> "2x"
                ratio in 2.5f..3.5f -> "3x"
                ratio in 4.5f..5.5f -> "5x"
                ratio > 5.5f -> "${ratio.roundToInt()}x"
                else -> String.format("%.1fx", ratio)
            }

            val caps = parseCapabilities(id, chars)
            result.add(CameraDeviceInfo(id, CameraCharacteristics.LENS_FACING_BACK, primaryFocal, label, caps))
        }

        // Process Front cameras
        frontCameras.forEachIndexed { index, (id, chars) ->
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(3.0f)
            val primaryFocal = focalLengths.firstOrNull() ?: 3.0f
            val label = if (frontCameras.size > 1) "FRONT ${index + 1}" else "FRONT"
            val caps = parseCapabilities(id, chars)
            result.add(CameraDeviceInfo(id, CameraCharacteristics.LENS_FACING_FRONT, primaryFocal, label, caps))
        }

        return result
    }

    fun inspectPrimaryBackCamera(): HardwareCapabilities? {
        val all = inspectAllAvailableCameras()
        return all.firstOrNull { it.isBack }?.capabilities ?: all.firstOrNull()?.capabilities
    }

    private fun parseCapabilities(id: String, chars: CameraCharacteristics): HardwareCapabilities {
        val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val isBack = facing == CameraCharacteristics.LENS_FACING_BACK
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(5.0f)

        val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

        val hwLevelName = when (hwLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN ($hwLevel)"
        }

        val isLevelFullOrHigher = hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

        val capabilitiesArray = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsManualSensor = capabilitiesArray.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )
        val supportsManualPostProcessing = capabilitiesArray.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
        )
        val supportsRaw = capabilitiesArray.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minFocusDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: Rational(1, 1)

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.map {
            Pair(it.width, it.height)
        } ?: emptyList()

        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.map {
            Pair(it.width, it.height)
        } ?: emptyList()

        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.map {
            Pair(it.width, it.height)
        } ?: emptyList()

        return HardwareCapabilities(
            cameraId = id,
            isFacingBack = isBack,
            lensFacing = facing,
            sensorOrientation = sensorOrientation,
            focalLengths = focalLengths,
            hardwareLevelName = hwLevelName,
            isLevelFullOrHigher = isLevelFullOrHigher,
            supportsManualSensor = supportsManualSensor,
            supportsManualPostProcessing = supportsManualPostProcessing,
            supportsRawCapture = supportsRaw,
            isoRange = isoRange,
            shutterSpeedRangeNanos = shutterRange,
            minFocusDistanceDiopters = minFocusDistance,
            evCompensationRange = evRange,
            evCompensationStep = evStep,
            supportedPreviewSizes = previewSizes,
            supportedJpegSizes = jpegSizes,
            supportedRawSizes = rawSizes
        )
    }
}
