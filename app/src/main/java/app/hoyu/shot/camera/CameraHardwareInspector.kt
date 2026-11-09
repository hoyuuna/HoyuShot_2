package app.hoyu.shot.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.util.Rational
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.LensItem
import kotlin.math.roundToInt

class CameraHardwareInspector(private val context: Context) {

    private val TAG = "CameraHardwareInspector"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Deep probe scanner that uncovers manufacturer-hidden cameras (Xiaomi, Samsung, etc.)
     * Scans official IDs, physical IDs, and brute-force probes candidate IDs 0..30 and vendor ranges.
     */
    fun discoverAllLenses(): List<LensItem> {
        val discoveredCameras = mutableMapOf<String, CameraCharacteristics>()

        // 1. Official IDs reported by OS
        try {
            for (id in cameraManager.cameraIdList) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    discoveredCameras[id] = chars
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying cameraIdList", e)
        }

        // 2. Physical Camera IDs declared under logical multi-cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for ((_, chars) in discoveredCameras.toList()) {
                try {
                    for (physId in chars.physicalCameraIds) {
                        if (!discoveredCameras.containsKey(physId)) {
                            try {
                                val physChars = cameraManager.getCameraCharacteristics(physId)
                                discoveredCameras[physId] = physChars
                            } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        // 3. Deep Probe: Brute-force query IDs 0..30, 50..70, 100..105
        // This is how ProShot Evaluator finds hidden lenses on Xiaomi (e.g. ID 4 for 63mm telephoto)
        val candidateProbeIds = mutableListOf<String>()
        for (i in 0..30) candidateProbeIds.add(i.toString())
        for (i in 50..70) candidateProbeIds.add(i.toString())
        for (i in 100..105) candidateProbeIds.add(i.toString())

        for (candidateId in candidateProbeIds) {
            if (!discoveredCameras.containsKey(candidateId)) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(candidateId)
                    // Verify that this camera has a valid preview configuration
                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)
                    if (!previewSizes.isNullOrEmpty()) {
                        discoveredCameras[candidateId] = chars
                        Log.d(TAG, "Discovered hidden camera ID: $candidateId")
                    }
                } catch (e: Exception) {
                    // ID does not exist on this device, ignore
                }
            }
        }

        // Now categorize discovered cameras into Back and Front
        val backSensors = mutableListOf<SensorInfo>()
        val frontSensors = mutableListOf<SensorInfo>()

        for ((id, chars) in discoveredCameras) {
            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: continue
            if (focal <= 0f) continue

            val focal35mm = calculate35mmEquivalent(chars)
            val info = SensorInfo(id, chars, facing, focal, focal35mm)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                backSensors.add(info)
            } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                frontSensors.add(info)
            }
        }

        // Sort back sensors by 35mm equivalent focal length
        backSensors.sortBy { it.focal35mm }

        // Find primary back camera: closest to standard wide ~23-28mm eq (typically ID "0" or around 24mm)
        val primaryBack = backSensors.find { it.id == "0" }
            ?: backSensors.minByOrNull { Math.abs(it.focal35mm - 24.0f) }
            ?: backSensors.firstOrNull()

        val primary35mm = primaryBack?.focal35mm ?: 24.0f

        val lenses = mutableListOf<LensItem>()

        // Generate clean optical labels for Back cameras
        for (sensor in backSensors) {
            val ratio = sensor.focal35mm / primary35mm
            val label = when {
                // Ultra-wide (e.g. 15mm on Xiaomi 13 -> 15/23 = 0.65 -> 0.6x)
                ratio in 0.5f..0.75f -> "0.6x"
                ratio in 0.4f..0.49f -> "0.5x"
                // Main wide (23-28mm)
                ratio in 0.9f..1.15f -> "1x"
                // 2x Telephoto (~46-52mm)
                ratio in 1.85f..2.25f -> "2x"
                // 3.2x Telephoto (63-75mm, like Xiaomi 13 ID 4: 63/23 = 2.74x Leica optical 3.2x)
                ratio in 2.5f..3.45f -> "3.2x"
                // 5x Telephoto (~120mm)
                ratio in 4.5f..5.5f -> "5x"
                // 10x Periscope (~240mm)
                ratio in 8.5f..11.0f -> "10x"
                ratio < 1.0f -> String.format("%.1fx", ratio)
                else -> "${(ratio * 10f).roundToInt() / 10f}x"
            }

            // Deduplicate if multiple logical IDs point to the same focal length
            if (!lenses.any { it.label == label }) {
                lenses.add(
                    LensItem(
                        id = "sensor_${sensor.id}_$label",
                        label = label,
                        cameraId = sensor.id,
                        zoomRatio = 1.0f,
                        isFront = false
                    )
                )
            }
        }

        // If for any reason telephoto or ultra-wide were not exposed as discrete IDs,
        // check CONTROL_ZOOM_RATIO_RANGE on primary back camera as fallback!
        if (primaryBack != null) {
            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                primaryBack.chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            } else null

            if (zoomRange != null) {
                if (zoomRange.lower < 0.95f && !lenses.any { it.label == "0.6x" || it.label == "0.5x" }) {
                    val label = String.format("%.1fx", zoomRange.lower)
                    lenses.add(0, LensItem("zoom_${label}", label, primaryBack.id, zoomRange.lower, false))
                }
            }
        }

        // Add Front Camera(s)
        frontSensors.forEachIndexed { index, sensor ->
            val label = if (frontSensors.size > 1) "FRONT ${index + 1}" else "FRONT"
            lenses.add(
                LensItem(
                    id = "sensor_${sensor.id}_front",
                    label = label,
                    cameraId = sensor.id,
                    zoomRatio = 1.0f,
                    isFront = true
                )
            )
        }

        // Guarantee at least 1x exists
        if (lenses.isEmpty()) {
            lenses.add(LensItem("default_1x", "1x", "0", 1.0f, false))
        }

        return lenses
    }

    private data class SensorInfo(
        val id: String,
        val chars: CameraCharacteristics,
        val facing: Int,
        val focalLength: Float,
        val focal35mm: Float
    )

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
