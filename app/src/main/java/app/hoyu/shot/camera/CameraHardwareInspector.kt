package app.hoyu.shot.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Rational
import app.hoyu.shot.model.HardwareCapabilities

class CameraHardwareInspector(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun inspectAllCameras(): List<HardwareCapabilities> {
        val result = mutableListOf<HardwareCapabilities>()
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                result.add(parseCapabilities(id, chars))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun inspectPrimaryBackCamera(): HardwareCapabilities? {
        val all = inspectAllCameras()
        return all.firstOrNull { it.isFacingBack } ?: all.firstOrNull()
    }

    private fun parseCapabilities(id: String, chars: CameraCharacteristics): HardwareCapabilities {
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        val isBack = facing == CameraCharacteristics.LENS_FACING_BACK

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

