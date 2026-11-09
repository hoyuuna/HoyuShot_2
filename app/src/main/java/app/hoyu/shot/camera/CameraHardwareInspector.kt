package app.hoyu.shot.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.Rational
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.LensItem
import kotlin.math.roundToInt

class CameraHardwareInspector(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun discoverAllLenses(): List<LensItem> {
        val lenses = mutableListOf<LensItem>()

        try {
            val cameraIds = cameraManager.cameraIdList

            // 1. First, analyze Back Cameras
            val backCameraIds = cameraIds.filter { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } catch (e: Exception) {
                    false
                }
            }

            // Look at Primary Back Camera (usually "0")
            val primaryBackId = backCameraIds.firstOrNull()
            if (primaryBackId != null) {
                val chars = cameraManager.getCameraCharacteristics(primaryBackId)
                val primary35mm = calculate35mmEquivalent(chars)

                // Check zoom ratio range on Android 11+
                val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                } else null

                val detectedOpticalRatios = mutableListOf<Float>()

                // Check physical cameras inside logical camera (API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val physicalIds = chars.physicalCameraIds
                    for (physId in physicalIds) {
                        try {
                            val physChars = cameraManager.getCameraCharacteristics(physId)
                            val phys35mm = calculate35mmEquivalent(physChars)
                            if (primary35mm > 0f) {
                                val ratio = phys35mm / primary35mm
                                detectedOpticalRatios.add(ratio)
                            }
                        } catch (e: Exception) {
                            // ignore physical camera read error
                        }
                    }
                }

                // If zoom range is supported, verify min zoom (e.g. 0.6x or 0.5x)
                if (zoomRange != null) {
                    if (zoomRange.lower < 0.95f) {
                        detectedOpticalRatios.add(zoomRange.lower)
                    }
                    if (zoomRange.upper >= 3.0f && !detectedOpticalRatios.any { it in 2.8f..3.5f }) {
                        // Standard telephoto step if device reports telephoto zoom range
                        detectedOpticalRatios.add(3.2f)
                    }
                }

                // If no physical cameras detected but there are other standalone back camera IDs in cameraIdList
                if (detectedOpticalRatios.isEmpty() && backCameraIds.size > 1) {
                    for (otherId in backCameraIds) {
                        if (otherId != primaryBackId) {
                            val otherChars = cameraManager.getCameraCharacteristics(otherId)
                            val other35mm = calculate35mmEquivalent(otherChars)
                            if (primary35mm > 0f) {
                                val ratio = other35mm / primary35mm
                                val label = formatZoomLabel(ratio)
                                lenses.add(LensItem(id = "cam_$otherId", label = label, cameraId = otherId, zoomRatio = 1.0f, isFront = false))
                            }
                        }
                    }
                }

                // Always add 1x
                if (!detectedOpticalRatios.any { it in 0.95f..1.05f }) {
                    detectedOpticalRatios.add(1.0f)
                }

                // Sort ratios ascending (e.g. 0.6, 1.0, 3.2)
                val sortedRatios = detectedOpticalRatios.sorted().distinctBy { formatZoomLabel(it) }

                for (ratio in sortedRatios) {
                    val label = formatZoomLabel(ratio)
                    val clampedRatio = if (zoomRange != null) ratio.coerceIn(zoomRange.lower, zoomRange.upper) else ratio
                    lenses.add(
                        LensItem(
                            id = "back_zoom_${label}",
                            label = label,
                            cameraId = primaryBackId,
                            zoomRatio = clampedRatio,
                            isFront = false
                        )
                    )
                }
            }

            // 2. Discover Front Camera(s)
            val frontCameraIds = cameraIds.filter { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                } catch (e: Exception) {
                    false
                }
            }

            frontCameraIds.forEachIndexed { index, frontId ->
                val label = if (frontCameraIds.size > 1) "FRONT ${index + 1}" else "FRONT"
                lenses.add(
                    LensItem(
                        id = "front_$frontId",
                        label = label,
                        cameraId = frontId,
                        zoomRatio = 1.0f,
                        isFront = true
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback default if detection is empty
        if (lenses.isEmpty()) {
            lenses.add(LensItem("default_1x", "1x", "0", 1.0f, false))
        }

        return lenses
    }

    private fun formatZoomLabel(ratio: Float): String {
        return when {
            ratio in 0.55f..0.65f -> "0.6x"
            ratio in 0.45f..0.54f -> "0.5x"
            ratio in 0.9f..1.1f -> "1x"
            ratio in 1.9f..2.2f -> "2x"
            ratio in 3.1f..3.4f -> "3.2x"
            ratio in 2.8f..3.09f -> "3x"
            ratio in 4.7f..5.3f -> "5x"
            ratio < 1.0f -> String.format("%.1fx", ratio)
            ratio % 1.0f == 0f -> "${ratio.toInt()}x"
            else -> String.format("%.1fx", ratio)
        }
    }

    private fun calculate35mmEquivalent(chars: CameraCharacteristics): Float {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 5.0f
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (sensorSize != null && sensorSize.width > 0f && sensorSize.height > 0f) {
            val diagonal = Math.sqrt((sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble())
            if (diagonal > 0.0) {
                val cropFactor = 43.27 / diagonal
                return (focal * cropFactor).toFloat()
            }
        }
        return focal * 5.0f
    }

    fun inspectCamera(cameraId: String): HardwareCapabilities? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            parseCapabilities(cameraId, chars)
        } catch (e: Exception) {
            null
        }
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
