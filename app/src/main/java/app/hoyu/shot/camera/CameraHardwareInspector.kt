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
import app.hoyu.shot.data.SettingsRepository
import app.hoyu.shot.model.HardwareCapabilities
import app.hoyu.shot.model.LensItem
import kotlin.math.roundToInt

class CameraHardwareInspector(private val context: Context) {

    private val TAG = "CameraHardwareInspector"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val settingsRepo = SettingsRepository(context)

    /**
     * Deep probe scanner that uncovers manufacturer physical cameras (Xiaomi, Samsung, etc.)
     * Discovers discrete physical lenses (0.6x, 1x, 3.2x, etc.) based on true optical sensor characteristics.
     * Deduplicates fake auxiliary cameras (e.g. 1x [ID 5], 1x [ID 6]) and low-res depth sensors.
     *
     * Cached permanently in SettingsRepository so scanning only executes once (persisted across app restarts).
     * Set [forceRefresh] to true to re-probe hardware (0..99).
     */
    fun discoverAllLenses(forceRefresh: Boolean = false): List<LensItem> {
        if (!forceRefresh) {
            val cached = settingsRepo.loadDiscoveredLenses()
            if (!cached.isNullOrEmpty()) {
                Log.d(TAG, "Loaded ${cached.size} cached lenses from storage")
                return cached
            }
        }

        val discoveredCameras = mutableMapOf<String, CameraCharacteristics>()

        // 1. Official IDs reported by OS
        try {
            for (id in cameraManager.cameraIdList) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    if (isValidCaptureCamera(chars)) {
                        discoveredCameras[id] = chars
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying cameraIdList", e)
        }

        // 2. Physical Camera IDs declared under logical multi-cameras (if valid capture cameras)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for ((_, chars) in discoveredCameras.toList()) {
                try {
                    for (physId in chars.physicalCameraIds) {
                        if (!discoveredCameras.containsKey(physId)) {
                            try {
                                val physChars = cameraManager.getCameraCharacteristics(physId)
                                if (isValidCaptureCamera(physChars)) {
                                    discoveredCameras[physId] = physChars
                                }
                            } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        // 3. Deep Probe: Query candidate IDs 0..99 (uncovers vendor physical lenses like ID 4, 52, etc.)
        val candidateProbeIds = (0..99).map { it.toString() }
        for (candidateId in candidateProbeIds) {
            if (!discoveredCameras.containsKey(candidateId)) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(candidateId)
                    if (isValidCaptureCamera(chars)) {
                        discoveredCameras[candidateId] = chars
                        Log.d(TAG, "Discovered valid hidden camera ID: $candidateId")
                    }
                } catch (e: Exception) {}
            }
        }

        // Categorize into Back and Front
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

        // Sort back sensors by 35mm focal length. Prioritize ID "0" as the primary 1x wide.
        backSensors.sortWith(compareBy({ it.focal35mm }, { if (it.id == "0") 0 else 1 }))

        val primaryBack = backSensors.find { it.id == "0" }
            ?: backSensors.minByOrNull { Math.abs(it.focal35mm - 24.0f) }
            ?: backSensors.firstOrNull()

        val primary35mm = primaryBack?.focal35mm ?: 24.0f
        val lenses = mutableListOf<LensItem>()

        for (sensor in backSensors) {
            val ratio = sensor.focal35mm / primary35mm
            val label = when {
                ratio in 0.5f..0.75f -> "0.6x"
                ratio in 0.4f..0.49f -> "0.5x"
                ratio in 0.85f..1.25f -> "1x"
                ratio in 1.85f..2.35f -> "2x"
                ratio in 2.5f..3.6f -> "3.2x"
                ratio in 4.5f..6.0f -> "5x"
                ratio in 8.5f..11.5f -> "10x"
                ratio < 1.0f -> String.format(java.util.Locale.US, "%.1fx", ratio)
                else -> "${(ratio * 10f).roundToInt() / 10f}x"
            }

            val baseZoomRatio = when (label) {
                "0.5x" -> 0.5f
                "0.6x" -> 0.6f
                "1x" -> 1.0f
                "2x" -> 2.0f
                "3.2x" -> 3.2f
                "5x" -> 5.0f
                "10x" -> 10.0f
                else -> (ratio * 10f).roundToInt() / 10f
            }

            // CRITICAL: Deduplicate! Never add virtual duplicate sensors with the same focal length (e.g. 1x [ID 5], 1x [ID 6])
            if (lenses.any { it.label == label || Math.abs(it.zoomRatio - baseZoomRatio) < 0.25f }) {
                Log.d(TAG, "Skipping duplicate rear camera ID ${sensor.id} (label: $label, ratio: $baseZoomRatio)")
                continue
            }

            lenses.add(
                LensItem(
                    id = "sensor_${sensor.id}_$label",
                    label = label,
                    cameraId = sensor.id,
                    zoomRatio = baseZoomRatio,
                    isFront = false
                )
            )
        }

        // Add front cameras
        addFrontCameras(lenses, frontSensors)

        // Guarantee at least 1x exists
        if (lenses.none { !it.isFront }) {
            lenses.add(0, LensItem("default_1x", "1x", "0", 1.0f, false))
        }

        // Save to persistent storage so subsequent app launches are instantaneous
        if (lenses.isNotEmpty()) {
            settingsRepo.saveDiscoveredLenses(lenses)
        }

        return lenses
    }

    private fun addFrontCameras(
        targetList: MutableList<LensItem>,
        discoveredFrontSensors: List<SensorInfo>? = null
    ) {
        val sensors = discoveredFrontSensors ?: run {
            val list = mutableListOf<SensorInfo>()
            try {
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT && isValidCaptureCamera(chars)) {
                        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 3.0f
                        list.add(SensorInfo(id, chars, facing, focal, calculate35mmEquivalent(chars)))
                    }
                }
            } catch (e: Exception) {}
            list
        }

        sensors.forEachIndexed { index, sensor ->
            val label = if (sensors.size > 1) "FRONT ${index + 1}" else "FRONT"
            if (!targetList.any { it.cameraId == sensor.id }) {
                targetList.add(
                    LensItem(
                        id = "sensor_${sensor.id}_front",
                        label = label,
                        cameraId = sensor.id,
                        zoomRatio = 1.0f,
                        isFront = true
                    )
                )
            }
        }
    }

    private fun isValidCaptureCamera(chars: CameraCharacteristics): Boolean {
        // 1. Must support standard Camera2 capture (BACKWARD_COMPATIBLE)
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return false
        val isBackwardCompatible = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
        if (!isBackwardCompatible) return false

        // 2. Must support valid preview output and JPEG capture
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
        if (previewSizes.isNullOrEmpty() || jpegSizes.isNullOrEmpty()) return false

        // 3. Must have photographic resolution (at least 1.5 MP) - filters out TOF/depth/bokeh sensors
        val maxPixels = jpegSizes.maxOfOrNull { it.width.toLong() * it.height.toLong() } ?: 0L
        if (maxPixels < 1_500_000L) return false

        // 4. Must report valid focal length
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
        if (focal <= 0f) return false

        return true
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

        val zoomRatioRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null

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
            supportedRawSizes = rawSizes,
            zoomRatioRange = zoomRatioRange
        )
    }
}
