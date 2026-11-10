package app.hoyu.shot.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import app.hoyu.shot.camera.analyzer.HistogramAnalyzer
import app.hoyu.shot.model.*
import app.hoyu.shot.sensors.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class Camera2Controller(
    private val context: Context,
    private val onHistogramReady: (HistogramData) -> Unit,
    private val onFrameMetadata: (actualIso: Int, actualShutterNanos: Long) -> Unit,
    private val onPhotoSaved: (CapturedPhotoInfo) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "Camera2Controller"

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val hardwareInspector = CameraHardwareInspector(context)

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var previewSurface: Surface? = null
    private var analysisImageReader: ImageReader? = null
    private var jpegImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null

    private var currentCharacteristics: CameraCharacteristics? = null
    private val histogramAnalyzer = HistogramAnalyzer()

    private var isCapturing = false
    private var isAeLocked = false
    private var isAfLocked = false

    private val locationHelper = LocationHelper(context)
    var isGeotaggingEnabled = false
        set(value) {
            field = value
            if (value && locationHelper.hasLocationPermission()) {
                locationHelper.startListening()
            } else {
                locationHelper.stopListening()
            }
        }
    var onExposureCompleted: (() -> Unit)? = null
    var isRawCaptureEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                val chars = currentCharacteristics
                if (cameraDevice != null && chars != null) {
                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (map != null) {
                        setupRawImageReader(map)
                        createCaptureSession()
                    }
                }
            }
        }
    private var openRetryCount = 0

    fun hasLocationPermission(): Boolean = locationHelper.hasLocationPermission()

    fun resumeLocation() {
        if (isGeotaggingEnabled && locationHelper.hasLocationPermission()) {
            locationHelper.startListening()
        }
    }

    fun pauseLocation() {
        locationHelper.stopListening()
    }

    var currentExposureMode = ExposureMode.AUTO
    var currentIso = 100
    var currentShutterNanos = 10_000_000L
    var currentFocusDistance = 0f
    var currentIsManualFocus = false
    var currentEvIndex = 0
    var currentWbPreset = WhiteBalancePreset.AUTO
    var currentKelvin = 5500
    var currentZoomRatio = 1.0f
    var currentFlashMode = FlashMode.OFF
    var currentNoiseReduction = NoiseReductionMode.FAST
    var currentSharpness = SharpnessMode.NORMAL
    var targetResolution: Pair<Int, Int>? = null
    var deviceOrientationDegrees: Int = 0
    private var lastJpegOrientation: Int = 0

    var currentAeMeteringRect: MeteringRectangle? = null
    var currentAfMeteringRect: MeteringRectangle? = null
    var isSpotAeActive: Boolean = false
    var jpegQuality: Int = 95
    var isMirrorSelfie: Boolean = false
    var antibandingMode: String = "AUTO"
    var cameraSoundMode: String = "VIBRATE"

    var lastMeasuredIso = 100
    var lastMeasuredShutter = 10_000_000L

    var minShutterLimitNanos: Long? = null
    var maxIsoLimit: Int? = null
    private var isExposureClamped = false
    private var currentClampedIso = 100
    private var lumaCheckCounter = 0

    private val _capabilities = MutableStateFlow<HardwareCapabilities?>(null)
    val capabilities: StateFlow<HardwareCapabilities?> = _capabilities.asStateFlow()

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackgroundThread").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(
        cameraId: String,
        previewSurface: Surface,
        targetRatio: AspectRatio,
        onPreviewSizeCalculated: (width: Int, height: Int) -> Unit
    ) {
        this.previewSurface = previewSurface
        startBackgroundThread()

        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            currentCharacteristics = chars

            val caps = hardwareInspector.inspectCamera(cameraId)
            _capabilities.value = caps

            val optimalPreviewSize = caps?.findOptimalPreviewSize(targetRatio) ?: Pair(1920, 1080)
            onPreviewSizeCalculated(optimalPreviewSize.first, optimalPreviewSize.second)

            setupImageReaders(chars, targetRatio)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    openRetryCount = 0
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera $cameraId error code: $error")
                    camera.close()
                    cameraDevice = null

                    // If camera was busy (driver still releasing previous device), retry once after 120ms
                    if ((error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ||
                                error == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE) &&
                        openRetryCount < 1
                    ) {
                        openRetryCount++
                        Log.w(TAG, "Retrying openCamera for $cameraId after busy error...")
                        backgroundHandler?.postDelayed({
                            openCamera(cameraId, previewSurface, targetRatio, onPreviewSizeCalculated)
                        }, 120L)
                        return
                    }

                    openRetryCount = 0
                    onError("Camera error code: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
            onError("Cannot open camera: ${e.localizedMessage}")
        }
    }

    fun switchCamera(
        newCameraId: String,
        newSurface: Surface,
        targetRatio: AspectRatio,
        onPreviewSizeCalculated: (width: Int, height: Int) -> Unit
    ) {
        closeCamera()
        // Allow HAL a short breather on the background handler to release hardware device locks
        backgroundHandler?.postDelayed({
            openCamera(newCameraId, newSurface, targetRatio, onPreviewSizeCalculated)
        }, 80L) ?: run {
            openCamera(newCameraId, newSurface, targetRatio, onPreviewSizeCalculated)
        }
    }

    private fun setupRawImageReader(map: android.hardware.camera2.params.StreamConfigurationMap) {
        rawImageReader?.close()
        rawImageReader = null
        if (isRawCaptureEnabled) {
            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
            val largestRaw = rawSizes?.maxByOrNull { it.width * it.height }
            if (largestRaw != null) {
                rawImageReader = ImageReader.newInstance(
                    largestRaw.width, largestRaw.height, ImageFormat.RAW_SENSOR, 2
                )
            }
        }
    }

    private fun setupImageReaders(chars: CameraCharacteristics, targetRatio: AspectRatio) {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

        // 1. Fast Analysis stream
        val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
        val optimalYuv = yuvSizes?.minByOrNull { it.width * it.height } ?: yuvSizes?.firstOrNull()
        if (optimalYuv != null) {
            analysisImageReader = ImageReader.newInstance(
                optimalYuv.width, optimalYuv.height, ImageFormat.YUV_420_888, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val hist = histogramAnalyzer.analyze(image)
                            onHistogramReady(hist)

                            if (isExposureClamped && (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.PROGRAM)) {
                                lumaCheckCounter++
                                if (lumaCheckCounter >= 6) {
                                    lumaCheckCounter = 0
                                    adjustClampedExposure(hist)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore drop
                        } finally {
                            image.close()
                        }
                    }
                }, backgroundHandler)
            }
        }

        // 2. Full-Res JPEG stream matching aspect ratio
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val targetRatioVal = when (targetRatio) {
            AspectRatio.RATIO_4_3 -> 4.0 / 3.0
            AspectRatio.RATIO_16_9 -> 16.0 / 9.0
            AspectRatio.RATIO_1_1 -> 1.0
            AspectRatio.RATIO_3_2 -> 3.0 / 2.0
        }
        val matchingJpeg = jpegSizes.filter {
            Math.abs((it.width.toDouble() / it.height.toDouble()) - targetRatioVal) < 0.05
        }

        val chosenJpegSize: Size = if (targetResolution != null) {
            val (w, h) = targetResolution!!
            matchingJpeg.find { it.width == w && it.height == h }
                ?: matchingJpeg.maxByOrNull { it.width * it.height }
                ?: Size(4000, 3000)
        } else {
            matchingJpeg.maxByOrNull { it.width * it.height } ?: Size(4000, 3000)
        }

        jpegImageReader = ImageReader.newInstance(
            chosenJpegSize.width, chosenJpegSize.height, ImageFormat.JPEG, 4
        ).apply {
            setOnImageAvailableListener({ reader ->
                while (true) {
                    val image = try {
                        reader.acquireNextImage()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    backgroundHandler?.post {
                        saveJpegBytes(bytes)
                    }
                }
            }, backgroundHandler)
        }

        // 3. RAW_SENSOR stream (strictly allocated only when RAW is active)
        setupRawImageReader(map)
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val pSurface = previewSurface ?: return

        try {
            // Keep stream count minimal (Open Camera pattern) to prevent HAL driver crash on auxiliary sub-cameras
            val surfaces = mutableListOf<Surface>().apply {
                add(pSurface)
                jpegImageReader?.surface?.let { add(it) }
                if (isRawCaptureEnabled && rawImageReader != null) {
                    rawImageReader?.surface?.let { add(it) }
                } else {
                    analysisImageReader?.surface?.let { add(it) }
                }
            }

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                if (!isRawCaptureEnabled) {
                    analysisImageReader?.surface?.let { addTarget(it) }
                }
            }

            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    applyAllSettingsInternal()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.w(TAG, "Capture session configuration failed, attempting fallback preview+jpeg session")
                    try {
                        val fallbackSurfaces = listOfNotNull(pSurface, jpegImageReader?.surface)
                        previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(pSurface)
                        }
                        device.createCaptureSession(fallbackSurfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(fbSession: CameraCaptureSession) {
                                captureSession = fbSession
                                applyAllSettingsInternal()
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                onError("Failed to configure CameraCaptureSession")
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        onError("Failed to configure CameraCaptureSession: ${e.message}")
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
            onError("CaptureSession creation failed: ${e.message}")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
            val shutter = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 10_000_000L
            lastMeasuredIso = iso
            lastMeasuredShutter = shutter
            onFrameMetadata(iso, shutter)

            // When in Auto or Program mode with limits active and NOT yet clamped:
            // Intervene the moment native AE breaches the user-defined bounds
            if (!isExposureClamped && (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.PROGRAM)) {
                val shouldClampShutter = minShutterLimitNanos != null && shutter > minShutterLimitNanos!!
                val shouldClampIso = maxIsoLimit != null && iso > (maxIsoLimit ?: Int.MAX_VALUE)
                if (shouldClampShutter || shouldClampIso) {
                    applyAllSettingsInternal()
                }
            }
        }
    }

    private fun adjustClampedExposure(hist: HistogramData) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        var sum = 0L
        var count = 0L
        for (i in 0..255) {
            val c = hist.lumaBins[i].toLong()
            sum += i * c
            count += c
        }
        val avgLuma = if (count > 0) (sum / count).toInt() else 128

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val minIso = isoRange?.lower ?: 50
        val maxIso = isoRange?.upper ?: 6400
        val effectiveMaxIso = maxIsoLimit?.coerceIn(minIso, maxIso) ?: maxIso

        // If at minIso and scene is significantly bright (overexposed), return to hardware AE to shoot faster!
        if (currentClampedIso <= minIso + 20 && avgLuma > 140) {
            isExposureClamped = false
            applyAllSettingsInternal()
            return
        }

        // Target luma is ~120 (standard 18% gray in 8-bit sRGB)
        // If error > 12 luma levels, gently adjust ISO to balance exposure
        if (Math.abs(avgLuma - 120) > 12) {
            val factor = 120.0 / avgLuma.coerceAtLeast(15)
            val stepFactor = 1.0 + (factor - 1.0) * 0.35
            val newIso = (currentClampedIso * stepFactor).toInt().coerceIn(minIso, effectiveMaxIso)
            if (Math.abs(newIso - currentClampedIso) >= 15) {
                currentClampedIso = newIso
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                try {
                    session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating clamped ISO", e)
                }
            }
        }
    }

    fun triggerSpotAe(normX: Float?, normY: Float?) {
        val chars = currentCharacteristics ?: return
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            if (normX != null && normY != null) {
                val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: Rect(0, 0, 4000, 3000)
                val sensorX = (normX * activeArray.width()).toInt().coerceIn(0, activeArray.width())
                val sensorY = (normY * activeArray.height()).toInt().coerceIn(0, activeArray.height())
                val halfBox = (activeArray.width() * 0.06f).toInt()
                val meteringBox = Rect(
                    (sensorX - halfBox).coerceAtLeast(0),
                    (sensorY - halfBox).coerceAtLeast(0),
                    (sensorX + halfBox).coerceAtMost(activeArray.width()),
                    (sensorY + halfBox).coerceAtMost(activeArray.height())
                )
                val rect = MeteringRectangle(meteringBox, MeteringRectangle.METERING_WEIGHT_MAX)
                currentAeMeteringRect = rect
                isSpotAeActive = true
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rect))
                builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            } else {
                currentAeMeteringRect = null
                isSpotAeActive = false
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
                builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
            }
            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Spot AE metering failed", e)
        }
    }

    fun triggerTapToFocus(
        normX: Float,
        normY: Float,
        onFocusResult: (Boolean) -> Unit
    ) {
        val chars = currentCharacteristics ?: return
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: Rect(0, 0, 4000, 3000)

            // Convert normalized screen coords (0..1) to sensor active array
            val sensorX = (normX * activeArray.width()).toInt().coerceIn(0, activeArray.width())
            val sensorY = (normY * activeArray.height()).toInt().coerceIn(0, activeArray.height())

            val halfBox = (activeArray.width() * 0.08f).toInt()
            val meteringBox = Rect(
                (sensorX - halfBox).coerceAtLeast(0),
                (sensorY - halfBox).coerceAtLeast(0),
                (sensorX + halfBox).coerceAtMost(activeArray.width()),
                (sensorY + halfBox).coerceAtMost(activeArray.height())
            )

            val meteringRect = MeteringRectangle(meteringBox, MeteringRectangle.METERING_WEIGHT_MAX)
            currentAfMeteringRect = meteringRect

            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            // Only set AE to focus point if Spot AE is not currently active
            if (currentAeMeteringRect == null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
            }
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    val isSuccess = afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED
                    onFocusResult(isSuccess)

                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Tap-to-focus failed", e)
            onFocusResult(false)
        }
    }

    fun resetAutoFocus() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            isAfLocked = false
            currentAfMeteringRect = null
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
            // Only reset AE region if Spot AE is not active
            if (currentAeMeteringRect == null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
            }
            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Reset autofocus failed", e)
        }
    }

    fun setAeLock(locked: Boolean) {
        this.isAeLocked = locked
        applyAllSettingsInternal()
    }

    fun setAfLock(locked: Boolean) {
        this.isAfLocked = locked
        applyAllSettingsInternal()
    }

    fun updateManualSettings(
        exposureMode: ExposureMode,
        iso: Int,
        shutterNanos: Long,
        focusDistance: Float,
        isManualFocus: Boolean,
        evIndex: Int,
        whiteBalancePreset: WhiteBalancePreset,
        kelvin: Int,
        zoomRatio: Float,
        flashMode: FlashMode,
        noiseReduction: NoiseReductionMode,
        sharpness: SharpnessMode,
        minShutterLimitNanos: Long? = null,
        maxIsoLimit: Int? = null
    ) {
        this.currentExposureMode = exposureMode
        this.currentIso = iso
        this.currentShutterNanos = shutterNanos
        this.currentFocusDistance = focusDistance
        this.currentIsManualFocus = isManualFocus
        this.currentEvIndex = evIndex
        this.currentWbPreset = whiteBalancePreset
        this.currentKelvin = kelvin
        this.currentZoomRatio = zoomRatio
        this.currentFlashMode = flashMode
        this.currentNoiseReduction = noiseReduction
        this.currentSharpness = sharpness
        this.minShutterLimitNanos = minShutterLimitNanos
        this.maxIsoLimit = maxIsoLimit
        if (minShutterLimitNanos == null && maxIsoLimit == null) {
            isExposureClamped = false
        }

        applyAllSettingsInternal()
    }

    private fun applyAllSettingsInternal() {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        try {
            // 1. Zoom
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zoomRange != null) {
                    val clamped = currentZoomRatio.coerceIn(zoomRange.lower, zoomRange.upper)
                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clamped)
                }
            } else {
                val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (activeArray != null && currentZoomRatio > 1.0f) {
                    val cropW = (activeArray.width() / currentZoomRatio).toInt()
                    val cropH = (activeArray.height() / currentZoomRatio).toInt()
                    val cropX = (activeArray.width() - cropW) / 2
                    val cropY = (activeArray.height() - cropH) / 2
                    builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(cropX, cropY, cropX + cropW, cropY + cropH))
                }
            }

            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val validEv = if (evRange != null) currentEvIndex.coerceIn(evRange.lower, evRange.upper) else currentEvIndex

            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val minIso = isoRange?.lower ?: 50
            val maxIso = isoRange?.upper ?: 6400
            val validIso = currentIso.coerceIn(minIso, maxIso)

            val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minShutter = shutterRange?.lower ?: 100_000L
            val maxShutter = shutterRange?.upper ?: 1_000_000_000L
            val validShutter = currentShutterNanos.coerceIn(minShutter, maxShutter)

            val maxDiopters = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val validFocus = currentFocusDistance.coerceIn(0f, maxDiopters)

            // 2. Exposure Mode calculation (Fixing dark preview bug & Auto Limits)
            when (currentExposureMode) {
                ExposureMode.AUTO, ExposureMode.PROGRAM -> {
                    val effectiveMaxIso = maxIsoLimit?.coerceIn(minIso, maxIso) ?: maxIso
                    val isShutterViolated = minShutterLimitNanos != null && lastMeasuredShutter > minShutterLimitNanos!!
                    val isIsoViolated = maxIsoLimit != null && lastMeasuredIso > effectiveMaxIso

                    if (isShutterViolated || isIsoViolated) {
                        isExposureClamped = true
                        // User-defined Auto limits breached -> intervene with manual override to protect shutter/ISO bounds
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        val targetExposure = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble()).coerceAtLeast(minIso.toDouble() * minShutter.toDouble())

                        if (isShutterViolated) {
                            val enforcedShutter = minShutterLimitNanos!!.coerceIn(minShutter, maxShutter)
                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, enforcedShutter)
                            currentClampedIso = (targetExposure / enforcedShutter.toDouble()).toInt().coerceIn(minIso, effectiveMaxIso)
                            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                        } else {
                            currentClampedIso = effectiveMaxIso
                            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                            val dynShutter = (targetExposure / effectiveMaxIso.toDouble()).toLong().coerceIn(minShutter, maxShutter)
                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, dynShutter)
                        }
                    } else {
                        isExposureClamped = false
                        // Fully automated AE with user EV compensation
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, validEv)
                    }
                }
                ExposureMode.SHUTTER_PRIORITY -> {
                    // Lock Shutter, dynamically balance ISO using current scene brightness
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, validShutter)
                    val targetExposure = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble()).coerceAtLeast(minIso.toDouble() * minShutter.toDouble())
                    val dynIso = (targetExposure / validShutter.toDouble()).toInt().coerceIn(minIso, maxIso)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, dynIso)
                }
                ExposureMode.ISO_PRIORITY -> {
                    // Lock ISO, dynamically balance Shutter using current scene brightness
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, validIso)
                    val targetExposure = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble()).coerceAtLeast(minIso.toDouble() * minShutter.toDouble())
                    val dynShutter = (targetExposure / validIso.toDouble()).toLong().coerceIn(minShutter, maxShutter)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, dynShutter)
                }
                ExposureMode.MANUAL -> {
                    // Full manual control
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, validIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, validShutter)
                }
            }

            // 3. Locks
            builder.set(CaptureRequest.CONTROL_AE_LOCK, isAeLocked)

            // 4. Focus
            if (currentIsManualFocus && maxDiopters > 0f) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, validFocus)
            } else {
                if (isAfLocked) {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                } else {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
            }

            // 5. White Balance
            applyWhiteBalance(builder, currentWbPreset, currentKelvin)

            // 6. Flash / Torch
            val isManualOrClampedExposure = currentExposureMode == ExposureMode.MANUAL ||
                    currentExposureMode == ExposureMode.SHUTTER_PRIORITY ||
                    currentExposureMode == ExposureMode.ISO_PRIORITY ||
                    isExposureClamped

            if (isManualOrClampedExposure) {
                // In manual or limit-clamped modes, CONTROL_AE_MODE must stay OFF to preserve shutter/ISO bounds!
                // Flash is controlled directly via FLASH_MODE instead of overriding AE mode to full auto.
                when (currentFlashMode) {
                    FlashMode.TORCH -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    else -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            } else {
                // In standard Auto mode without limits, hardware AE handles flash metering
                when (currentFlashMode) {
                    FlashMode.OFF -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    FlashMode.AUTO -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    FlashMode.ON -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    FlashMode.TORCH -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

            // 7. Noise Reduction
            when (currentNoiseReduction) {
                NoiseReductionMode.OFF -> builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                NoiseReductionMode.FAST -> builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                NoiseReductionMode.HIGH_QUALITY -> builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            }

            // 8. Edge Mode (Sharpness)
            when (currentSharpness) {
                SharpnessMode.OFF -> builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                SharpnessMode.NORMAL -> builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
                SharpnessMode.HIGH -> builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }

            // 9. Spot AE & AF Metering Regions
            if (currentAeMeteringRect != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(currentAeMeteringRect))
            }
            if (currentAfMeteringRect != null) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(currentAfMeteringRect))
            }

            // 10. Antibanding Mode
            val abMode = when (antibandingMode) {
                "OFF" -> CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF
                "50HZ" -> CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ
                "60HZ" -> CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ
                else -> CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
            }
            builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, abMode)

            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply camera settings", e)
        }
    }

    private fun applyWhiteBalance(builder: CaptureRequest.Builder, preset: WhiteBalancePreset, kelvin: Int) {
        when (preset) {
            WhiteBalancePreset.AUTO -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            WhiteBalancePreset.DAYLIGHT -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
            WhiteBalancePreset.CLOUDY -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
            WhiteBalancePreset.SHADE -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE)
            WhiteBalancePreset.FLUORESCENT -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
            WhiteBalancePreset.INCANDESCENT -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
            WhiteBalancePreset.CUSTOM_KELVIN -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                val gains = kelvinToRggbVector(kelvin)
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
            }
        }
    }

    private fun kelvinToRggbVector(kelvin: Int): RggbChannelVector {
        val temp = kelvin / 100.0
        val r: Float = if (temp <= 66) 1.0f else (temp - 60).let {
            (1.2929 * Math.pow(it, -0.1332)).toFloat()
        }.coerceIn(0.5f, 2.5f)
        val g: Float = 1.0f
        val b: Float = if (temp >= 66) 1.0f else if (temp <= 19) 0.0f else (temp - 10).let {
            (0.5432 * Math.log(it) - 0.3025).toFloat()
        }.coerceIn(0.5f, 2.5f)
        return RggbChannelVector(r, g, g, b)
    }

    private val photoCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun takeBurstPicture(captureRaw: Boolean) {
        submitCaptureRequest(captureRaw = captureRaw, isBurst = true)
    }

    fun takePicture(captureRaw: Boolean) {
        submitCaptureRequest(captureRaw = captureRaw, isBurst = false)
    }

    private fun submitCaptureRequest(captureRaw: Boolean, isBurst: Boolean) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        if (!isBurst) {
            if (isCapturing) return
            isCapturing = true
        }

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(jpegImageReader!!.surface)

            val willCaptureRaw = captureRaw && rawImageReader != null
            if (willCaptureRaw) {
                captureBuilder.addTarget(rawImageReader!!.surface)
            }

            previewRequestBuilder?.let { preview ->
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, preview.get(CaptureRequest.CONTROL_AE_MODE))
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, preview.get(CaptureRequest.SENSOR_SENSITIVITY))
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, preview.get(CaptureRequest.SENSOR_EXPOSURE_TIME))
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, preview.get(CaptureRequest.CONTROL_AF_MODE))
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, preview.get(CaptureRequest.LENS_FOCUS_DISTANCE))
                captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, preview.get(CaptureRequest.CONTROL_AWB_MODE))
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, preview.get(CaptureRequest.NOISE_REDUCTION_MODE))
                captureBuilder.set(CaptureRequest.EDGE_MODE, preview.get(CaptureRequest.EDGE_MODE))

                // Copy zoom
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val zoom = preview.get(CaptureRequest.CONTROL_ZOOM_RATIO)
                    if (zoom != null) captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
                } else {
                    val crop = preview.get(CaptureRequest.SCALER_CROP_REGION)
                    if (crop != null) captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop)
                }
            }

            captureBuilder.set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())

            if (cameraSoundMode == "VIBRATE") {
                triggerShutterVibration()
            }

            // Correct EXIF orientation for portrait / landscape holding
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val isFacingFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            val jpegOrientation = if (isFacingFront) {
                (sensorOrientation + deviceOrientationDegrees) % 360
            } else {
                (sensorOrientation - deviceOrientationDegrees + 360) % 360
            }
            lastJpegOrientation = jpegOrientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

            // Geotagging GPS: Set hardware GPS location on CaptureRequest
            if (isGeotaggingEnabled) {
                val loc = locationHelper.getBestLocation()
                if (loc != null) {
                    captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, loc)
                }
            }

            // Flash synchronization under manual or clamped AE
            val aeMode = captureBuilder.get(CaptureRequest.CONTROL_AE_MODE)
            if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
                when (currentFlashMode) {
                    FlashMode.ON -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                    FlashMode.AUTO -> {
                        if (lastMeasuredIso > 400 || isExposureClamped) {
                            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                        } else {
                            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                    }
                    FlashMode.TORCH -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    FlashMode.OFF -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    // Release capture lock immediately as exposure begins: zero shutter lag for rapid shooting
                    isCapturing = false
                    onExposureCompleted?.invoke()
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    if (willCaptureRaw) {
                        rawImageReader?.setOnImageAvailableListener({ reader ->
                            while (true) {
                                val rawImage = try {
                                    reader.acquireNextImage()
                                } catch (e: Exception) {
                                    null
                                } ?: break
                                backgroundHandler?.post {
                                    saveDngImage(rawImage, chars, result)
                                    rawImage.close()
                                }
                            }
                        }, backgroundHandler)
                    }

                    isCapturing = false
                    onExposureCompleted?.invoke()
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    super.onCaptureFailed(session, request, failure)
                    isCapturing = false
                    onError("Capture failed: reason ${failure.reason}")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            isCapturing = false
            Log.e(TAG, "Failed to capture picture", e)
            onError("Capture error: ${e.message}")
        }
    }

    private fun triggerShutterVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Short, snappy, full-amplitude mechanical shutter kick (single strong jolt)
                vibrator?.vibrate(VibrationEffect.createOneShot(42L, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(42L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shutter vibration failed", e)
        }
    }

    private fun saveJpegBytes(bytes: ByteArray) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val count = photoCounter.incrementAndGet() % 10000
        val fileName = "IMG_${timeStamp}_${String.format(Locale.US, "%04d", count)}.jpg"
        val loc = if (isGeotaggingEnabled) locationHelper.getBestLocation() else null

        val isFrontCam = currentCharacteristics?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val finalBytes = if (isFrontCam && isMirrorSelfie) {
            try {
                val origBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (origBitmap != null) {
                    val matrix = Matrix().apply { postScale(-1f, 1f) }
                    val mirrored = Bitmap.createBitmap(origBitmap, 0, 0, origBitmap.width, origBitmap.height, matrix, true)
                    val baos = ByteArrayOutputStream()
                    mirrored.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    origBitmap.recycle()
                    mirrored.recycle()
                    baos.toByteArray()
                } else bytes
            } catch (e: Exception) {
                Log.e(TAG, "Mirror selfie processing failed", e)
                bytes
            }
        } else {
            bytes
        }

        // 1. Write bytes to temporary file on disk so ExifInterface can safely write GPS tags
        val tempFile = java.io.File(context.cacheDir, "temp_$fileName")
        var imgWidth = 4000
        var imgHeight = 3000

        try {
            tempFile.outputStream().use { fos ->
                fos.write(finalBytes)
            }

            val exif = ExifInterface(tempFile.absolutePath)
            if (loc != null) {
                exif.setGpsInfo(loc)
                val latRef = if (loc.latitude >= 0) "N" else "S"
                val lonRef = if (loc.longitude >= 0) "E" else "W"
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, loc.provider ?: "GPS")
            }
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, lastJpegOrientation.toString())
            exif.setAttribute(ExifInterface.TAG_MAKE, "Xiaomi")
            exif.setAttribute(ExifInterface.TAG_MODEL, "Xiaomi 13")
            exif.saveAttributes()

            imgWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 4000)
            imgHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EXIF to temp file", e)
        }

        // 2. Stream byte-complete JPEG (with valid EXIF GPS) directly into MediaStore
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            }
            if (loc != null) {
                put(MediaStore.Images.Media.LATITUDE, loc.latitude)
                put(MediaStore.Images.Media.LONGITUDE, loc.longitude)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { destUri ->
            var writtenBytes = 0L
            try {
                context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        writtenBytes = inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy to MediaStore", e)
            } finally {
                tempFile.delete()
            }

            val chars = currentCharacteristics
            val apertures = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            val ap = if (apertures != null && apertures.isNotEmpty()) "f/${apertures[0]}" else "f/1.8"

            val shutterStr = HardwareCapabilities.formatShutterSpeed(
                if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.ISO_PRIORITY) lastMeasuredShutter else currentShutterNanos
            )
            val isPortrait = (lastJpegOrientation == 90 || lastJpegOrientation == 270)
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = false,
                width = if (isPortrait) imgHeight else imgWidth,
                height = if (isPortrait) imgWidth else imgHeight,
                iso = if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.SHUTTER_PRIORITY) lastMeasuredIso else currentIso,
                shutterSpeed = shutterStr,
                focalLength = "1x",
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSizeBytes = writtenBytes,
                aperture = ap
            )
            onPhotoSaved(info)
        }
    }

    private fun saveDngImage(
        image: android.media.Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val count = photoCounter.incrementAndGet() % 10000
        val fileName = "RAW_${timeStamp}_${String.format(Locale.US, "%04d", count)}.dng"
        val loc = if (isGeotaggingEnabled) locationHelper.getBestLocation() else null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            }
            if (loc != null) {
                put(MediaStore.Images.Media.LATITUDE, loc.latitude)
                put(MediaStore.Images.Media.LONGITUDE, loc.longitude)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                val dngCreator = DngCreator(characteristics, captureResult)
                dngCreator.setOrientation(lastJpegOrientation)
                if (loc != null) {
                    dngCreator.setLocation(loc)
                }
                dngCreator.writeImage(outputStream, image)
                dngCreator.close()
            }

            val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            val ap = if (apertures != null && apertures.isNotEmpty()) "f/${apertures[0]}" else "f/1.8"

            val shutterStr = HardwareCapabilities.formatShutterSpeed(
                if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.ISO_PRIORITY) lastMeasuredShutter else currentShutterNanos
            )
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = true,
                width = image.width,
                height = image.height,
                iso = if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.SHUTTER_PRIORITY) lastMeasuredIso else currentIso,
                shutterSpeed = shutterStr,
                focalLength = "1x",
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSizeBytes = 24_000_000L,
                aperture = ap
            )
            onPhotoSaved(info)
        }
    }

    fun closeCamera() {
        try {
            try {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            } catch (e: Exception) {
                // Ignore if session already closed or invalid
            }
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            analysisImageReader?.close()
            analysisImageReader = null

            jpegImageReader?.close()
            jpegImageReader = null

            rawImageReader?.close()
            rawImageReader = null

            previewRequestBuilder = null
            currentCharacteristics = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
}
