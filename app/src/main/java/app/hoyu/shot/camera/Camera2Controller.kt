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
import android.os.Looper
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
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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
    private var analysisThread: HandlerThread? = null
    private var analysisHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var previewSurface: Surface? = null
    private var analysisImageReader: ImageReader? = null
    private var jpegImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null

    private var currentCameraId: String? = null
    private var currentCharacteristics: CameraCharacteristics? = null
    private val histogramAnalyzer = HistogramAnalyzer()

    private var isCapturing = false
    private var isAeLocked = false
    private var isAfLocked = false

    private val locationHelper = LocationHelper(context)
    var isGeotaggingEnabled = false
        set(value) {
            field = value
            syncLocationListening()
        }
    var isSunCompassLocationNeeded = false
        set(value) {
            field = value
            syncLocationListening()
        }

    private fun syncLocationListening() {
        if ((isGeotaggingEnabled || isSunCompassLocationNeeded) && locationHelper.hasLocationPermission()) {
            locationHelper.startListening()
        } else {
            locationHelper.stopListening()
        }
    }
    @Volatile var isExifSoftwareTagEnabled: Boolean = true
    @Volatile private var lastCapturedIso: Int? = null
    @Volatile private var lastCapturedExposureNanos: Long? = null
    var onExposureCompleted: (() -> Unit)? = null
    var onLongExposureStateChanged: ((Boolean) -> Unit)? = null
    var onCaptureError: ((String) -> Unit)? = null
    var onOverlayDataReady: ((ViewfinderOverlayData) -> Unit)? = null
    var onBlurredBackdropReady: ((androidx.compose.ui.graphics.ImageBitmap) -> Unit)? = null
    @Volatile var isHistogramOverlayEnabled: Boolean = false
    @Volatile var isPeakingOverlayEnabled: Boolean = false
    @Volatile var isZebraOverlayEnabled: Boolean = false
    @Volatile var isBlurBackdropNeeded: Boolean = false
    @Volatile var isHqViewportEnabled: Boolean = true
    @Volatile var colorProfile: ColorProfile = ColorProfile.NATURAL
    @Volatile var isHdrEnabled: Boolean = false

    private data class HdrBurstBuffer(
        val id: Long,
        val exifSnapshot: CaptureExifSnapshot,
        val frames: Array<ByteArray?> = arrayOfNulls(3),
        var receivedCount: Int = 0
    )
    private val hdrLock = Any()
    private val pendingHdrBursts = mutableListOf<HdrBurstBuffer>()
    private val pendingJpegSnapshots = mutableListOf<CaptureExifSnapshot>()
    private var nextHdrBurstId: Long = 1L
    // Dedicated worker pool for HDR fusion / NaturalColorProcessor / JPEG EXIF saving
    // so CameraBackgroundThread stays free for zero-lag shutter & ImageReader callbacks.
    private val postProcessExecutor = Executors.newFixedThreadPool(2)
    private val activePostProcessCount = java.util.concurrent.atomic.AtomicInteger(0)
    var onPendingPostProcessCountChanged: ((Int) -> Unit)? = null

    private fun enqueuePostProcess(task: () -> Unit) {
        val count = activePostProcessCount.incrementAndGet()
        onPendingPostProcessCountChanged?.invoke(count)
        postProcessExecutor.execute {
            try {
                task()
            } finally {
                val remaining = activePostProcessCount.decrementAndGet().coerceAtLeast(0)
                onPendingPostProcessCountChanged?.invoke(remaining)
            }
        }
    }
    private var isAnalysisTargetAttached: Boolean = false
    private var lastBackdropGeneratedMs: Long = 0L
    var captureFormat: CaptureFormat = CaptureFormat.JPEG
        set(value) {
            field = value
            isRawCaptureEnabled = (value == CaptureFormat.RAW || value == CaptureFormat.RAW_JPEG)
        }
    var isRawCaptureEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                val task = Runnable {
                    val chars = currentCharacteristics
                    if (cameraDevice != null && chars != null) {
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        if (map != null) {
                            setupRawImageReader(map, chars)
                            createCaptureSession()
                        }
                    }
                }
                val handler = backgroundHandler
                if (handler != null && Looper.myLooper() != handler.looper) {
                    handler.post(task)
                } else {
                    task.run()
                }
            }
        }
    private var isRawStreamConfigured = false
    private val rawPairingLock = Any()
    private val pendingRawImages = LinkedHashMap<Long, android.media.Image>()
    private val pendingRawResults = LinkedHashMap<Long, Pair<TotalCaptureResult, Int>>()
    private var openRetryCount = 0

    fun hasLocationPermission(): Boolean = locationHelper.hasLocationPermission()

    fun getBestLocation(): android.location.Location? = locationHelper.getBestLocation()

    fun setOnLocationUpdated(callback: ((android.location.Location) -> Unit)?) {
        locationHelper.onLocationChangedCallback = callback
    }

    fun resumeLocation() {
        syncLocationListening()
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
    @Volatile private var spotAeNormX: Float? = null
    @Volatile private var spotAeNormY: Float? = null
    var isSpotAeActive: Boolean = false
    var jpegQuality: Int = 95
    var isMirrorSelfie: Boolean = false
    var antibandingMode: String = "AUTO"
    var cameraSoundMode: String = "VIBRATE"

    @Volatile var lastMeasuredIso = 100
    @Volatile var lastMeasuredShutter = 10_000_000L

    var minShutterLimitNanos: Long? = null
    var maxIsoLimit: Int? = null
    private var isExposureClamped = false
    private var currentClampedIso = 100
    private var currentClampedShutter = 10_000_000L
    private var currentPriorityIso = 100
    private var currentPriorityShutter = 10_000_000L
    private var prevPriorityMode: ExposureMode? = null
    private var prevLockedShutterForS: Long = 0L
    private var prevLockedIsoForI: Int = 0
    @Volatile private var aeMeteringSettleUntilMs = 0L
    @Volatile private var lastMeteringAdjustMs = 0L
    private var clampReleaseCooldownUntilMs = 0L
    private var forceHardwareAeOnce = false
    private var lastHistogramFrameMs = 0L
    private var lastFallbackProbeMs = 0L
    private var clampedEnteredMs = 0L
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
        if (analysisThread == null) {
            analysisThread = HandlerThread("CameraAnalysisThread").apply {
                start()
                analysisHandler = Handler(looper)
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
        analysisThread?.quitSafely()
        try {
            analysisThread?.join()
            analysisThread = null
            analysisHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping analysis thread", e)
        }
    }

    private fun needsAnalysisStream(): Boolean {
        val needPriorityMeter = !isAeLocked &&
            (currentExposureMode == ExposureMode.SHUTTER_PRIORITY || currentExposureMode == ExposureMode.ISO_PRIORITY)
        val needClamp = !isAeLocked && isExposureClamped &&
            (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.PROGRAM)
        return isHistogramOverlayEnabled ||
            isPeakingOverlayEnabled ||
            isZebraOverlayEnabled ||
            isBlurBackdropNeeded ||
            needPriorityMeter ||
            needClamp
    }

    fun refreshAnalysisStream() {
        val handler = backgroundHandler
        if (handler != null && Looper.myLooper() != handler.looper) {
            handler.post { applyAllSettingsInternal() }
        } else {
            applyAllSettingsInternal()
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
        this.currentCameraId = cameraId
        startBackgroundThread()

        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            currentCharacteristics = chars

            val caps = hardwareInspector.inspectCamera(cameraId)
            _capabilities.value = caps

            val maxPreviewDim = if (isHqViewportEnabled) 1920 else 960
            val optimalPreviewSize = caps?.findOptimalPreviewSize(targetRatio, maxDimension = maxPreviewDim)
                ?: Pair(1280, 720)
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

    private fun clearPendingRawBuffers() {
        synchronized(rawPairingLock) {
            pendingRawImages.values.forEach { img ->
                try { img.close() } catch (_: Exception) {}
            }
            pendingRawImages.clear()
            pendingRawResults.clear()
        }
    }

    private fun doesSizeMatchSensorForDng(chars: CameraCharacteristics, width: Int, height: Int): Boolean {
        val preCorr = chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        val pixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val matchesPre = preCorr != null && preCorr.width() == width && preCorr.height() == height
        val matchesPixel = pixelSize != null && pixelSize.width == width && pixelSize.height == height
        return matchesPre || matchesPixel
    }

    private fun setupRawImageReader(
        map: android.hardware.camera2.params.StreamConfigurationMap,
        chars: CameraCharacteristics
    ) {
        clearPendingRawBuffers()
        rawImageReader?.close()
        rawImageReader = null
        isRawStreamConfigured = false
        if (isRawCaptureEnabled) {
            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
            val compatibleSizes = rawSizes?.filter { doesSizeMatchSensorForDng(chars, it.width, it.height) }
            val chosenRaw = compatibleSizes?.maxByOrNull { it.width * it.height }
                ?: rawSizes?.maxByOrNull { it.width * it.height }
            val preCorr = chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            val pixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            Log.i(
                TAG,
                "setupRawImageReader cam=$currentCameraId chosen=${chosenRaw?.width}x${chosenRaw?.height} " +
                    "all=${rawSizes?.joinToString { "${it.width}x${it.height}" }} " +
                    "preCorr=${preCorr?.width()}x${preCorr?.height()} pixel=${pixelSize?.width}x${pixelSize?.height}"
            )
            if (chosenRaw != null) {
                rawImageReader = ImageReader.newInstance(
                    chosenRaw.width, chosenRaw.height, ImageFormat.RAW_SENSOR, 4
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        while (true) {
                            val rawImage = try {
                                reader.acquireNextImage()
                            } catch (e: Exception) {
                                null
                            } ?: break

                            val ts = rawImage.timestamp
                            var matchedResult: Pair<TotalCaptureResult, Int>? = null
                            synchronized(rawPairingLock) {
                                matchedResult = pendingRawResults.remove(ts)
                                if (matchedResult == null) {
                                    // Result hasn't arrived yet: buffer image by timestamp
                                    while (pendingRawImages.size >= 3) {
                                        val oldestKey = pendingRawImages.keys.firstOrNull() ?: break
                                        try { pendingRawImages.remove(oldestKey)?.close() } catch (_: Exception) {}
                                    }
                                    pendingRawImages[ts] = rawImage
                                }
                            }

                            val pair = matchedResult
                            if (pair != null) {
                                val activeChars = currentCharacteristics
                                try {
                                    if (activeChars != null) {
                                        saveDngImage(rawImage, activeChars, pair.first, pair.second)
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed saving DNG on image arrival", t)
                                    onCaptureError?.invoke("RAW (DNG) save failed: ${t.message}")
                                } finally {
                                    try { rawImage.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    }, backgroundHandler)
                }
            }
        }
    }

    private fun setupImageReaders(chars: CameraCharacteristics, targetRatio: AspectRatio) {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

        // 1. Fast Analysis stream (dedicated thread + zero-copy skip when unused)
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
                            val needPriorityMeter = !isAeLocked &&
                                (currentExposureMode == ExposureMode.SHUTTER_PRIORITY || currentExposureMode == ExposureMode.ISO_PRIORITY)
                            val needClampCheck = !isAeLocked && isExposureClamped &&
                                (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.PROGRAM)
                            val needMetering = needPriorityMeter || needClampCheck
                            val needOverlays = isPeakingOverlayEnabled || isZebraOverlayEnabled
                            val needBlur = isBlurBackdropNeeded

                            if (!isHistogramOverlayEnabled && !needMetering && !needOverlays && !needBlur) {
                                return@setOnImageAvailableListener
                            }

                            val cChars = currentCharacteristics
                            val sensorOrient = cChars?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                            val isFront = cChars?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

                            val nowMs = System.currentTimeMillis()
                            val meterTick = needMetering &&
                                nowMs >= aeMeteringSettleUntilMs &&
                                (nowMs - lastMeteringAdjustMs >= 110L)
                            val shouldAnalyzeHist = meterTick || (isHistogramOverlayEnabled && nowMs - lastHistogramFrameMs >= 80L)
                            if (shouldAnalyzeHist) {
                                val hist = histogramAnalyzer.analyze(
                                    image = image,
                                    computeRgb = isHistogramOverlayEnabled,
                                    spotScreenX = spotAeNormX,
                                    spotScreenY = spotAeNormY,
                                    sensorOrientation = sensorOrient,
                                    isFront = isFront
                                )
                                lastHistogramFrameMs = nowMs
                                if (isHistogramOverlayEnabled) {
                                    onHistogramReady(hist)
                                }
                                if (meterTick) {
                                    lastMeteringAdjustMs = nowMs
                                    backgroundHandler?.post {
                                        adjustClosedLoopExposure(hist)
                                    }
                                }
                            }

                            if (needOverlays) {
                                val overlay = histogramAnalyzer.analyzeOverlays(
                                    image = image,
                                    sensorOrientation = sensorOrient,
                                    isFront = isFront,
                                    computePeaking = isPeakingOverlayEnabled,
                                    computeZebra = isZebraOverlayEnabled
                                )
                                onOverlayDataReady?.invoke(overlay)
                            }

                            if (needBlur && nowMs - lastBackdropGeneratedMs >= 70L) {
                                lastBackdropGeneratedMs = nowMs
                                histogramAnalyzer.generateBlurredBackdrop(
                                    image = image,
                                    sensorOrientation = sensorOrient,
                                    isFront = isFront
                                )?.let { bmp ->
                                    onBlurredBackdropReady?.invoke(bmp)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore drop
                        } finally {
                            image.close()
                        }
                    }
                }, analysisHandler ?: backgroundHandler)
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
            chosenJpegSize.width, chosenJpegSize.height, ImageFormat.JPEG, 6
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

                    var hdrCompleteTriple: Triple<ByteArray, ByteArray, ByteArray>? = null
                    var hdrSnapshot: CaptureExifSnapshot? = null
                    var singleSnapshot: CaptureExifSnapshot? = null
                    var consumedByHdr = false
                    synchronized(hdrLock) {
                        val burst = pendingHdrBursts.firstOrNull()
                        if (burst != null && burst.receivedCount < 3) {
                            burst.frames[burst.receivedCount] = bytes
                            burst.receivedCount++
                            consumedByHdr = true
                            if (burst.receivedCount == 3) {
                                pendingHdrBursts.removeAt(0)
                                hdrSnapshot = burst.exifSnapshot
                                hdrCompleteTriple = Triple(
                                    burst.frames[0]!!,
                                    burst.frames[1]!!,
                                    burst.frames[2]!!
                                )
                            }
                        } else if (pendingJpegSnapshots.isNotEmpty()) {
                            singleSnapshot = pendingJpegSnapshots.removeAt(0)
                        }
                    }

                    if (hdrCompleteTriple != null) {
                        val (refBytes, underBytes, overBytes) = hdrCompleteTriple!!
                        val snap = hdrSnapshot
                        enqueuePostProcess {
                            saveJpegBytes(
                                refBytes,
                                hdrUnderBytes = underBytes,
                                hdrOverBytes = overBytes,
                                exifSnapshot = snap
                            )
                        }
                    } else if (!consumedByHdr) {
                        val snap = singleSnapshot
                        enqueuePostProcess {
                            saveJpegBytes(bytes, exifSnapshot = snap)
                        }
                    }
                }
            }, backgroundHandler)
        }

        // 3. RAW_SENSOR stream (strictly allocated only when RAW is active)
        setupRawImageReader(map, chars)
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val pSurface = previewSurface ?: return

        try {
            val includeRawInPrimary = isRawCaptureEnabled && rawImageReader != null
            val surfaces = mutableListOf<Surface>().apply {
                add(pSurface)
                jpegImageReader?.surface?.let { add(it) }
                if (includeRawInPrimary) {
                    rawImageReader?.surface?.let { add(it) }
                }
                analysisImageReader?.surface?.let { add(it) }
            }

            val attachAnalysis = needsAnalysisStream() && analysisImageReader != null
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                if (attachAnalysis) {
                    analysisImageReader?.surface?.let { addTarget(it) }
                }
            }
            isAnalysisTargetAttached = attachAnalysis

            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    isRawStreamConfigured = includeRawInPrimary
                    applyAllSettingsInternal()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.w(TAG, "Capture session configuration failed, attempting fallback without analysis stream")
                    try {
                        val includeRawInFallback = isRawCaptureEnabled && rawImageReader != null
                        val fallbackSurfaces = mutableListOf<Surface>().apply {
                            add(pSurface)
                            jpegImageReader?.surface?.let { add(it) }
                            if (includeRawInFallback) {
                                rawImageReader?.surface?.let { add(it) }
                            }
                        }
                        previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(pSurface)
                        }
                        isAnalysisTargetAttached = false
                        device.createCaptureSession(fallbackSurfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(fbSession: CameraCaptureSession) {
                                captureSession = fbSession
                                isRawStreamConfigured = includeRawInFallback
                                applyAllSettingsInternal()
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                if (isRawCaptureEnabled) {
                                    val minimalSurfaces = listOfNotNull(pSurface, jpegImageReader?.surface)
                                    device.createCaptureSession(minimalSurfaces, object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(minSession: CameraCaptureSession) {
                                            captureSession = minSession
                                            isRawStreamConfigured = false
                                            applyAllSettingsInternal()
                                        }
                                        override fun onConfigureFailed(s2: CameraCaptureSession) {
                                            onError("Failed to configure CameraCaptureSession")
                                        }
                                    }, backgroundHandler)
                                } else {
                                    onError("Failed to configure CameraCaptureSession")
                                }
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
            // Intervene the moment native AE breaches the user-defined bounds (after any release cooldown)
            val now = System.currentTimeMillis()
            if (!isAeLocked && (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.PROGRAM)) {
                if (!isExposureClamped && now >= clampReleaseCooldownUntilMs) {
                    val isoRange = currentCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                    val minIso = isoRange?.lower ?: 50
                    val maxIso = isoRange?.upper ?: 6400
                    val effectiveMaxIso = maxIsoLimit?.coerceIn(minIso, maxIso) ?: maxIso
                    val shouldClampShutter = minShutterLimitNanos != null &&
                        shutter > (minShutterLimitNanos!! * 1.08).toLong()
                    val shouldClampIso = maxIsoLimit != null &&
                        iso > (effectiveMaxIso + (effectiveMaxIso * 0.08).toInt().coerceAtLeast(8))
                    if (shouldClampShutter || shouldClampIso) {
                        applyAllSettingsInternal()
                    }
                } else if (isExposureClamped &&
                    now - clampedEnteredMs > 1800L &&
                    now - lastHistogramFrameMs > 1800L &&
                    now - lastFallbackProbeMs > 1800L
                ) {
                    // Fallback when YUV analysis stream isn't active: periodically probe hardware AE so clamp cannot stick
                    lastFallbackProbeMs = now
                    isExposureClamped = false
                    forceHardwareAeOnce = true
                    clampReleaseCooldownUntilMs = now + 450L
                    applyAllSettingsInternal()
                }
            }
        }
    }

    /**
     * Computes a harmonious linear-light exposure multiplier from weighted scene luminance,
     * median midtone balance, EV compensation, and highlight clipping protection.
     * Returns null if exposure is already within a stable deadband.
     */
    private fun computeExposureStepRatio(
        hist: HistogramData,
        chars: CameraCharacteristics
    ): Double? {
        val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: android.util.Rational(1, 3)
        val evStepFloat = evStepRational.toFloat().let { if (it > 0f) it.toDouble() else (1.0 / 3.0) }
        val evStops = currentEvIndex * evStepFloat
        // 18% middle gray in sRGB gamma (~118/255), scaled in gamma 2.2 space per real stop of EV
        val targetLuma = (118.0 * Math.pow(2.0, evStops / 2.2)).coerceIn(42.0, 200.0)

        val baseLuma = 0.65 * hist.weightedMeanLuma + 0.35 * hist.medianLuma
        var effectiveLuma = baseLuma

        // Highlight protection & dynamic range harmony (avoid blowing out bright skies/highlights)
        if (evStops <= 0.35) {
            if (hist.highlightClipRatio > 0.03 && hist.shadowRatio < 0.35) {
                val clipPenalty = ((hist.highlightClipRatio - 0.03) * 180.0).coerceIn(0.0, 38.0)
                effectiveLuma = (baseLuma + clipPenalty).coerceAtMost(250.0)
            } else if (hist.highlightClipRatio > 0.14) {
                val clipPenalty = ((hist.highlightClipRatio - 0.14) * 120.0).coerceIn(0.0, 24.0)
                effectiveLuma = (baseLuma + clipPenalty).coerceAtMost(250.0)
            }
        }
        // Gentle shadow rescue when highlights are completely clean
        if (hist.shadowRatio > 0.45 && hist.highlightClipRatio < 0.01 && effectiveLuma < targetLuma) {
            val shadowLift = ((hist.shadowRatio - 0.45) * 28.0).coerceIn(0.0, 14.0)
            effectiveLuma = (effectiveLuma - shadowLift).coerceAtLeast(8.0)
        }

        if (Math.abs(effectiveLuma - targetLuma) <= 5.5) {
            return null
        }

        // Convert sRGB luma ratio to linear sensor exposure ratio (gamma ~2.2) with smooth damping
        val linearRatio = Math.pow(targetLuma / effectiveLuma.coerceAtLeast(6.0), 2.2).coerceIn(0.32, 3.0)
        val damping = if (linearRatio > 1.8 || linearRatio < 0.55) 0.72 else 0.58
        return 1.0 + (linearRatio - 1.0) * damping
    }

    private fun adjustClosedLoopExposure(hist: HistogramData) {
        if (isAeLocked) return
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        when (currentExposureMode) {
            ExposureMode.SHUTTER_PRIORITY -> {
                val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val minShutter = shutterRange?.lower ?: 100_000L
                val maxShutter = shutterRange?.upper ?: 1_000_000_000L
                val validShutter = currentShutterNanos.coerceIn(minShutter, maxShutter)

                // Strictly wait until the sensor confirms the user's chosen shutter has taken effect
                val shutterTolerance = (validShutter / 8L).coerceAtLeast(100_000L)
                if (Math.abs(lastMeasuredShutter - validShutter) > shutterTolerance) return

                val stepRatio = computeExposureStepRatio(hist, chars) ?: return
                val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val minIso = isoRange?.lower ?: 50
                val maxIso = isoRange?.upper ?: 6400

                val newIso = Math.round(currentPriorityIso.toDouble() * stepRatio).toInt().coerceIn(minIso, maxIso)
                val isoDeltaThreshold = (currentPriorityIso / 30).coerceAtLeast(1)
                if (Math.abs(newIso - currentPriorityIso) >= isoDeltaThreshold) {
                    currentPriorityIso = newIso
                    aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, validShutter)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentPriorityIso)
                    try {
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating S-mode ISO", e)
                    }
                }
            }
            ExposureMode.ISO_PRIORITY -> {
                val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val minIso = isoRange?.lower ?: 50
                val maxIso = isoRange?.upper ?: 6400
                val validIso = currentIso.coerceIn(minIso, maxIso)

                // Strictly wait until the sensor confirms the user's chosen ISO has taken effect
                val isoTolerance = (validIso / 8).coerceAtLeast(8)
                if (Math.abs(lastMeasuredIso - validIso) > isoTolerance) return

                val stepRatio = computeExposureStepRatio(hist, chars) ?: return
                val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val minShutter = shutterRange?.lower ?: 100_000L
                val maxShutter = shutterRange?.upper ?: 1_000_000_000L

                val prevShutter = currentPriorityShutter
                val newShutter = (prevShutter.toDouble() * stepRatio).toLong().coerceIn(minShutter, maxShutter)
                val shutterDeltaThreshold = (prevShutter / 30L).coerceAtLeast(5_000L)
                if (Math.abs(newShutter - prevShutter) >= shutterDeltaThreshold) {
                    currentPriorityShutter = newShutter
                    aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, validIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentPriorityShutter)
                    if ((prevShutter <= 33_333_333L) != (newShutter <= 33_333_333L)) {
                        selectPreviewFpsRange(chars, newShutter)?.let {
                            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                        }
                    }
                    try {
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating ISO-mode shutter", e)
                    }
                }
            }
            ExposureMode.AUTO, ExposureMode.PROGRAM -> {
                if (isExposureClamped) {
                    adjustClampedExposure(hist)
                }
            }
            ExposureMode.MANUAL -> {}
        }
    }

    private fun adjustClampedExposure(hist: HistogramData) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val minIso = isoRange?.lower ?: 50
        val maxIso = isoRange?.upper ?: 6400
        val effectiveMaxIso = maxIsoLimit?.coerceIn(minIso, maxIso) ?: maxIso

        val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minShutter = shutterRange?.lower ?: 100_000L
        val maxShutter = shutterRange?.upper ?: 1_000_000_000L
        val effectiveMaxShutter = minShutterLimitNanos?.coerceIn(minShutter, maxShutter) ?: maxShutter

        val currentExposure = currentClampedIso.coerceAtLeast(minIso).toDouble() *
            currentClampedShutter.coerceAtLeast(minShutter).toDouble()
        val rawStep = computeExposureStepRatio(hist, chars)
        val desiredExposure = currentExposure * (rawStep ?: 1.0)
        val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: android.util.Rational(1, 3)
        val evStops = currentEvIndex * evStepRational.toFloat().let { if (it > 0f) it.toDouble() else (1.0 / 3.0) }
        val targetLuma = (118.0 * Math.pow(2.0, evStops / 2.2)).coerceIn(42.0, 200.0)
        val avgLuma = 0.65 * hist.weightedMeanLuma + 0.35 * hist.medianLuma

        if (minShutterLimitNanos != null) {
            val minPossibleExposure = minIso.toDouble() * effectiveMaxShutter.toDouble()
            // Scene is bright enough to shoot faster than minShutterLimit at minIso -> release back to hardware AE
            if ((currentClampedIso <= minIso + 5 && avgLuma > targetLuma + 10.0) ||
                desiredExposure < minPossibleExposure * 0.96
            ) {
                isExposureClamped = false
                forceHardwareAeOnce = true
                clampReleaseCooldownUntilMs = System.currentTimeMillis() + 700L
                applyAllSettingsInternal()
                return
            }

            if (rawStep != null || currentClampedShutter != effectiveMaxShutter) {
                val newIso = Math.round(desiredExposure / effectiveMaxShutter.toDouble()).toInt().coerceIn(minIso, effectiveMaxIso)
                if (newIso != currentClampedIso || currentClampedShutter != effectiveMaxShutter) {
                    currentClampedShutter = effectiveMaxShutter
                    currentClampedIso = newIso
                    aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentClampedShutter)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                    try {
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating clamped exposure", e)
                    }
                }
            }
        } else if (maxIsoLimit != null) {
            // Only ISO ceiling is active: release when bright enough for <= 1/60s well below maxIsoLimit
            val comfortThresholdExposure = effectiveMaxIso.toDouble() * 16_666_667.0 * 0.65
            if ((desiredExposure < comfortThresholdExposure && avgLuma > targetLuma + 10.0) ||
                (currentClampedShutter <= minShutter + (minShutter / 10L) && avgLuma > targetLuma + 10.0)
            ) {
                isExposureClamped = false
                forceHardwareAeOnce = true
                clampReleaseCooldownUntilMs = System.currentTimeMillis() + 700L
                applyAllSettingsInternal()
                return
            }

            if (rawStep != null || currentClampedIso != effectiveMaxIso) {
                val newShutter = (desiredExposure / effectiveMaxIso.toDouble()).toLong().coerceIn(minShutter, maxShutter)
                if (Math.abs(newShutter - currentClampedShutter) > (currentClampedShutter / 25L).coerceAtLeast(10_000L) ||
                    currentClampedIso != effectiveMaxIso
                ) {
                    currentClampedIso = effectiveMaxIso
                    currentClampedShutter = newShutter
                    aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentClampedShutter)
                    try {
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating ISO-clamped shutter", e)
                    }
                }
            }
        } else {
            isExposureClamped = false
            forceHardwareAeOnce = true
            applyAllSettingsInternal()
        }
    }

    fun triggerSpotAe(normX: Float?, normY: Float?) {
        val chars = currentCharacteristics ?: return
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            spotAeNormX = normX
            spotAeNormY = normY
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
            } else {
                currentAeMeteringRect = null
                isSpotAeActive = false
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
            }

            val isSoftwareMeterMode = isExposureClamped ||
                currentExposureMode == ExposureMode.SHUTTER_PRIORITY ||
                currentExposureMode == ExposureMode.ISO_PRIORITY
            if (isSoftwareMeterMode) {
                aeMeteringSettleUntilMs = 0L
                lastMeteringAdjustMs = 0L
                session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
            } else {
                if (normX != null && normY != null) {
                    builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                } else {
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
            }
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
        val prevMinShutter = this.minShutterLimitNanos
        val prevMaxIso = this.maxIsoLimit
        this.minShutterLimitNanos = minShutterLimitNanos
        this.maxIsoLimit = maxIsoLimit
        if (minShutterLimitNanos == null && maxIsoLimit == null) {
            isExposureClamped = false
            forceHardwareAeOnce = true
            clampReleaseCooldownUntilMs = 0L
        } else if (prevMinShutter != minShutterLimitNanos || prevMaxIso != maxIsoLimit) {
            isExposureClamped = false
            clampReleaseCooldownUntilMs = 0L
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
                    val effectiveMaxShutter = minShutterLimitNanos?.coerceIn(minShutter, maxShutter) ?: maxShutter
                    val hasAnyLimit = minShutterLimitNanos != null || maxIsoLimit != null

                    if (forceHardwareAeOnce || !hasAnyLimit) {
                        forceHardwareAeOnce = false
                        isExposureClamped = false
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, validEv)
                    } else if (isExposureClamped) {
                        // Preserve active clamped exposure state across non-exposure updates (zoom, focus, WB, etc.)
                        currentClampedShutter = if (minShutterLimitNanos != null) {
                            effectiveMaxShutter
                        } else {
                            currentClampedShutter.coerceIn(minShutter, maxShutter)
                        }
                        currentClampedIso = if (minShutterLimitNanos == null && maxIsoLimit != null) {
                            effectiveMaxIso
                        } else {
                            currentClampedIso.coerceIn(minIso, effectiveMaxIso)
                        }
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentClampedShutter)
                        builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                    } else {
                        val isShutterViolated = System.currentTimeMillis() >= clampReleaseCooldownUntilMs &&
                            minShutterLimitNanos != null &&
                            lastMeasuredShutter > (minShutterLimitNanos!! * 1.08).toLong()
                        val isIsoViolated = System.currentTimeMillis() >= clampReleaseCooldownUntilMs &&
                            maxIsoLimit != null &&
                            lastMeasuredIso > (effectiveMaxIso + (effectiveMaxIso * 0.08).toInt().coerceAtLeast(8))

                        if (isShutterViolated || isIsoViolated) {
                            isExposureClamped = true
                            clampedEnteredMs = System.currentTimeMillis()
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                            val targetExposure = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble())
                                .coerceAtLeast(minIso.toDouble() * minShutter.toDouble())

                            if (isShutterViolated) {
                                currentClampedShutter = effectiveMaxShutter
                                currentClampedIso = (targetExposure / currentClampedShutter.toDouble())
                                    .toInt()
                                    .coerceIn(minIso, effectiveMaxIso)
                            } else {
                                currentClampedIso = effectiveMaxIso
                                currentClampedShutter = (targetExposure / currentClampedIso.toDouble())
                                    .toLong()
                                    .coerceIn(minShutter, effectiveMaxShutter)
                            }
                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentClampedShutter)
                            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentClampedIso)
                        } else {
                            isExposureClamped = false
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, validEv)
                        }
                    }
                }
                ExposureMode.SHUTTER_PRIORITY -> {
                    isExposureClamped = false
                    forceHardwareAeOnce = false
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, validShutter)

                    if (prevPriorityMode != ExposureMode.SHUTTER_PRIORITY) {
                        val initTargetExp = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble())
                            .coerceAtLeast(minIso.toDouble() * minShutter.toDouble())
                        currentPriorityIso = Math.round(initTargetExp / validShutter.toDouble()).toInt().coerceIn(minIso, maxIso)
                        prevLockedShutterForS = validShutter
                        aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                        lastMeteringAdjustMs = 0L
                    } else if (prevLockedShutterForS != validShutter) {
                        // Scale proportionally as initial guess, then meter the ACTUAL live frame at validShutter
                        val prevExp = currentPriorityIso.toDouble() * prevLockedShutterForS.coerceAtLeast(1L).toDouble()
                        currentPriorityIso = Math.round(prevExp / validShutter.toDouble()).toInt().coerceIn(minIso, maxIso)
                        prevLockedShutterForS = validShutter
                        aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                        lastMeteringAdjustMs = 0L
                    } else {
                        currentPriorityIso = currentPriorityIso.coerceIn(minIso, maxIso)
                        lastMeteringAdjustMs = 0L
                    }
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentPriorityIso)
                }
                ExposureMode.ISO_PRIORITY -> {
                    isExposureClamped = false
                    forceHardwareAeOnce = false
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, validIso)

                    if (prevPriorityMode != ExposureMode.ISO_PRIORITY) {
                        val initTargetExp = (lastMeasuredIso.toDouble() * lastMeasuredShutter.toDouble())
                            .coerceAtLeast(minIso.toDouble() * minShutter.toDouble())
                        currentPriorityShutter = (initTargetExp / validIso.toDouble()).toLong().coerceIn(minShutter, maxShutter)
                        prevLockedIsoForI = validIso
                        aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                        lastMeteringAdjustMs = 0L
                    } else if (prevLockedIsoForI != validIso) {
                        // Scale proportionally as initial guess, then meter the ACTUAL live frame at validIso
                        val prevExp = prevLockedIsoForI.coerceAtLeast(1).toDouble() * currentPriorityShutter.toDouble()
                        currentPriorityShutter = (prevExp / validIso.toDouble()).toLong().coerceIn(minShutter, maxShutter)
                        prevLockedIsoForI = validIso
                        aeMeteringSettleUntilMs = System.currentTimeMillis() + 140L
                        lastMeteringAdjustMs = 0L
                    } else {
                        currentPriorityShutter = currentPriorityShutter.coerceIn(minShutter, maxShutter)
                        lastMeteringAdjustMs = 0L
                    }
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentPriorityShutter)
                }
                ExposureMode.MANUAL -> {
                    isExposureClamped = false
                    forceHardwareAeOnce = false
                    // Full manual control
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, validIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, validShutter)
                }
            }
            prevPriorityMode = currentExposureMode

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

            // 7. Preview Noise Reduction & Sharpness (prioritize zero-latency preview; full quality applies at capture)
            val previewNr = if (isHqViewportEnabled) {
                if (currentNoiseReduction == NoiseReductionMode.OFF) NoiseReductionMode.OFF else NoiseReductionMode.FAST
            } else {
                NoiseReductionMode.OFF
            }
            val previewSharp = if (isHqViewportEnabled) {
                if (currentSharpness == SharpnessMode.OFF) SharpnessMode.OFF else SharpnessMode.NORMAL
            } else {
                SharpnessMode.OFF
            }
            applyNoiseAndEdgeModes(builder, chars, previewNr, previewSharp, isPreview = true)

            // 8. Target FPS Range for fluid viewfinder pacing (relaxed if shutter > 1/30s)
            val activePreviewShutter = when {
                currentExposureMode == ExposureMode.MANUAL || currentExposureMode == ExposureMode.SHUTTER_PRIORITY -> validShutter
                currentExposureMode == ExposureMode.ISO_PRIORITY -> currentPriorityShutter
                isExposureClamped -> currentClampedShutter
                else -> 0L
            }
            selectPreviewFpsRange(chars, activePreviewShutter)?.let { fpsRange ->
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }

            // 9. Dynamically attach/detach CPU analysis stream only when Histogram / Peaking / Zebra / Blur / Clamp needs it
            val analysisSurface = analysisImageReader?.surface
            val shouldAttachAnalysis = analysisSurface != null && needsAnalysisStream()
            if (shouldAttachAnalysis && !isAnalysisTargetAttached) {
                builder.addTarget(analysisSurface!!)
                isAnalysisTargetAttached = true
            } else if (!shouldAttachAnalysis && isAnalysisTargetAttached && analysisSurface != null) {
                builder.removeTarget(analysisSurface)
                isAnalysisTargetAttached = false
            }

            // 10. Spot AE & AF Metering Regions
            if (currentAeMeteringRect != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(currentAeMeteringRect))
            }
            if (currentAfMeteringRect != null) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(currentAfMeteringRect))
            }

            // 11. Antibanding Mode
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

    private fun selectPreviewFpsRange(chars: CameraCharacteristics, forcedShutterNanos: Long): android.util.Range<Int>? {
        val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return null
        val standardRanges = ranges.filter { it.lower > 0 && it.upper in 15..60 }
        if (standardRanges.isEmpty()) return null

        // When user locks a shutter slower than ~1/30s, use a range whose lower bound permits long exposures
        if (forcedShutterNanos > 33_333_333L) {
            return standardRanges
                .filter { it.upper >= 24 }
                .minWithOrNull(compareBy<android.util.Range<Int>> { it.lower }.thenByDescending { it.upper })
                ?: standardRanges.minByOrNull { it.lower }
        }

        // Prefer 60fps peak with reasonable 15-30fps floor (keeps indoor exposure bright & daylight ultra-smooth)
        val sixtyPeak = standardRanges.filter { it.upper >= 60 && it.lower in 15..30 }
        if (sixtyPeak.isNotEmpty()) {
            return sixtyPeak.maxWithOrNull(compareBy<android.util.Range<Int>> { it.lower }.thenBy { it.upper })
        }

        // Next prefer locked 30fps or highest minimum floor up to 30fps
        return standardRanges.find { it.lower == 30 && it.upper == 30 }
            ?: standardRanges
                .filter { it.upper >= 30 && it.lower <= 30 }
                .maxWithOrNull(compareBy<android.util.Range<Int>> { it.upper }.thenBy { it.lower })
            ?: standardRanges.maxByOrNull { it.upper }
    }

    private fun applyNoiseAndEdgeModes(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        nrMode: NoiseReductionMode,
        sharpMode: SharpnessMode,
        isPreview: Boolean
    ) {
        val availNr = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: IntArray(0)
        val desiredNr = when (nrMode) {
            NoiseReductionMode.OFF -> when {
                availNr.isEmpty() || availNr.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) -> CaptureRequest.NOISE_REDUCTION_MODE_OFF
                availNr.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) -> CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                else -> CaptureRequest.NOISE_REDUCTION_MODE_FAST
            }
            NoiseReductionMode.FAST -> CaptureRequest.NOISE_REDUCTION_MODE_FAST
            NoiseReductionMode.HIGH_QUALITY -> if (!isPreview && (availNr.isEmpty() || availNr.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY))) {
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
            } else {
                CaptureRequest.NOISE_REDUCTION_MODE_FAST
            }
        }
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, desiredNr)

        val availEdge = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: IntArray(0)
        val desiredEdge = when (sharpMode) {
            SharpnessMode.OFF -> if (availEdge.isEmpty() || availEdge.contains(CaptureRequest.EDGE_MODE_OFF)) {
                CaptureRequest.EDGE_MODE_OFF
            } else {
                CaptureRequest.EDGE_MODE_FAST
            }
            SharpnessMode.NORMAL -> CaptureRequest.EDGE_MODE_FAST
            SharpnessMode.HIGH -> if (!isPreview && (availEdge.isEmpty() || availEdge.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY))) {
                CaptureRequest.EDGE_MODE_HIGH_QUALITY
            } else {
                CaptureRequest.EDGE_MODE_FAST
            }
        }
        builder.set(CaptureRequest.EDGE_MODE, desiredEdge)
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

    fun takeBurstPicture(captureRaw: Boolean, format: CaptureFormat = this.captureFormat) {
        submitCaptureRequest(captureRaw = captureRaw, format = format, isBurst = true)
    }

    fun takePicture(captureRaw: Boolean, format: CaptureFormat = this.captureFormat) {
        submitCaptureRequest(captureRaw = captureRaw, format = format, isBurst = false)
    }

    private fun submitCaptureRequest(captureRaw: Boolean, format: CaptureFormat, isBurst: Boolean) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        if (!isBurst) {
            if (isCapturing) return
            isCapturing = true
        }

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

            val wantsRaw = captureRaw || format == CaptureFormat.RAW || format == CaptureFormat.RAW_JPEG
            val willCaptureRaw = wantsRaw && isRawStreamConfigured && rawImageReader != null
            val willCaptureJpeg = (format != CaptureFormat.RAW || !willCaptureRaw) && jpegImageReader != null

            if (willCaptureJpeg) {
                captureBuilder.addTarget(jpegImageReader!!.surface)
            }
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

                // Copy zoom
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val zoom = preview.get(CaptureRequest.CONTROL_ZOOM_RATIO)
                    if (zoom != null) captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
                } else {
                    val crop = preview.get(CaptureRequest.SCALER_CROP_REGION)
                    if (crop != null) captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop)
                }
            }

            // Apply full photo-capture Noise Reduction & Sharpness regardless of viewport latency mode
            applyNoiseAndEdgeModes(captureBuilder, chars, currentNoiseReduction, currentSharpness, isPreview = false)

            // Enable high-quality ISP color correction & tone mapping when Natural/Vivid profile is active
            if (colorProfile != ColorProfile.OFF) {
                val awbMode = captureBuilder.get(CaptureRequest.CONTROL_AWB_MODE)
                if (awbMode != CaptureRequest.CONTROL_AWB_MODE_OFF) {
                    captureBuilder.set(
                        CaptureRequest.COLOR_CORRECTION_MODE,
                        CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
                    )
                }
                val availTonemap = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
                if (availTonemap != null && availTonemap.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)) {
                    captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                }
            }

            captureBuilder.set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())

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

            val effectiveExposureNanos = if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
                captureBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME) ?: currentShutterNanos
            } else {
                lastMeasuredShutter
            }
            val isLongExposure = !isBurst && effectiveExposureNanos >= 350_000_000L
            if (isLongExposure) {
                onLongExposureStateChanged?.invoke(true)
            }

            lastCapturedIso = null
            lastCapturedExposureNanos = null

            val useHdrBurst = isHdrEnabled && !isBurst && willCaptureJpeg && format != CaptureFormat.RAW
            val zslRequestKeys = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_REQUEST_KEYS)
            if (
                willCaptureJpeg &&
                !willCaptureRaw &&
                !isBurst &&
                !useHdrBurst &&
                aeMode == CaptureRequest.CONTROL_AE_MODE_ON &&
                currentFlashMode == FlashMode.OFF &&
                zslRequestKeys?.contains(CaptureRequest.CONTROL_ENABLE_ZSL) == true
            ) {
                captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true)
            }

            val snapIso = if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
                (captureBuilder.get(CaptureRequest.SENSOR_SENSITIVITY) ?: currentIso).coerceAtLeast(1)
            } else {
                lastMeasuredIso.coerceAtLeast(1)
            }
            val snapFlashFired = currentFlashMode == FlashMode.ON ||
                currentFlashMode == FlashMode.TORCH ||
                (currentFlashMode == FlashMode.AUTO && (lastMeasuredIso > 400 || isExposureClamped))
            val snapEvStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.5f
            val shotSnapshot = CaptureExifSnapshot(
                captureTimeMs = System.currentTimeMillis(),
                characteristics = chars,
                exposureMode = currentExposureMode,
                iso = snapIso,
                exposureNanos = effectiveExposureNanos.coerceAtLeast(1L),
                evIndex = currentEvIndex,
                evStep = snapEvStep,
                whiteBalancePreset = currentWbPreset,
                flashMode = currentFlashMode,
                flashFired = snapFlashFired,
                digitalZoomRatio = currentZoomRatio,
                tapAeActive = currentAeMeteringRect != null,
                location = if (isGeotaggingEnabled) locationHelper.getBestLocation() else null,
                isExifSoftwareTagEnabled = isExifSoftwareTagEnabled,
                deviceJpegOrientation = jpegOrientation
            )
            var currentHdrBurstId = -1L
            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    if (!isLongExposure && !useHdrBurst) {
                        // Release capture lock immediately as short exposure begins: zero shutter lag for rapid shooting
                        isCapturing = false
                        onExposureCompleted?.invoke()
                    }
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { if (it > 0) lastCapturedIso = it }
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { if (it > 0L) lastCapturedExposureNanos = it }
                    if (!useHdrBurst || request.tag == "HDR_REF") {
                        result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { if (it > 0) shotSnapshot.iso = it }
                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { if (it > 0L) shotSnapshot.exposureNanos = it }
                        result.get(CaptureResult.FLASH_STATE)?.let { fState ->
                            if (fState == CaptureResult.FLASH_STATE_FIRED) {
                                shotSnapshot.flashFired = true
                            }
                        }
                    }

                    if (willCaptureRaw && (!useHdrBurst || request.tag == "HDR_REF")) {
                        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP)
                        if (ts != null) {
                            var matchedImage: android.media.Image? = null
                            synchronized(rawPairingLock) {
                                matchedImage = pendingRawImages.remove(ts)
                                if (matchedImage == null) {
                                    while (pendingRawResults.size >= 6) {
                                        val oldestKey = pendingRawResults.keys.firstOrNull() ?: break
                                        pendingRawResults.remove(oldestKey)
                                    }
                                    pendingRawResults[ts] = Pair(result, jpegOrientation)
                                }
                            }
                            val img = matchedImage
                            if (img != null) {
                                try {
                                    saveDngImage(img, chars, result, jpegOrientation)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed saving matched DNG", t)
                                    onCaptureError?.invoke("RAW (DNG) save failed: ${t.message}")
                                } finally {
                                    try { img.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    if (!useHdrBurst || request.tag == "HDR_OVER") {
                        isCapturing = false
                        if (isLongExposure) {
                            onLongExposureStateChanged?.invoke(false)
                        }
                        onExposureCompleted?.invoke()
                    }
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    super.onCaptureFailed(session, request, failure)
                    if (useHdrBurst && currentHdrBurstId >= 0L) {
                        var refFallback: ByteArray? = null
                        var snapFallback: CaptureExifSnapshot? = null
                        synchronized(hdrLock) {
                            val idx = pendingHdrBursts.indexOfFirst { it.id == currentHdrBurstId }
                            if (idx >= 0) {
                                val burst = pendingHdrBursts.removeAt(idx)
                                refFallback = burst.frames[0]
                                snapFallback = burst.exifSnapshot
                            }
                        }
                        refFallback?.let { fb ->
                            val snap = snapFallback
                            enqueuePostProcess { saveJpegBytes(fb, exifSnapshot = snap) }
                        }
                    }
                    isCapturing = false
                    if (isLongExposure) {
                        onLongExposureStateChanged?.invoke(false)
                    }
                    onExposureCompleted?.invoke()
                    onError("Capture failed: reason ${failure.reason}")
                }
            }

            if (useHdrBurst) {
                val burstId = synchronized(hdrLock) {
                    val id = nextHdrBurstId++
                    pendingHdrBursts.add(HdrBurstBuffer(id = id, exifSnapshot = shotSnapshot))
                    id
                }
                currentHdrBurstId = burstId
                val burstRequests = buildHdrBurstRequests(
                    device = device,
                    chars = chars,
                    baseBuilder = captureBuilder,
                    willCaptureRaw = willCaptureRaw
                )
                backgroundHandler?.postDelayed({
                    var timedOutRef: ByteArray? = null
                    var timedOutSnap: CaptureExifSnapshot? = null
                    synchronized(hdrLock) {
                        val idx = pendingHdrBursts.indexOfFirst { it.id == burstId }
                        if (idx >= 0) {
                            val b = pendingHdrBursts.removeAt(idx)
                            timedOutRef = b.frames[0]
                            timedOutSnap = b.exifSnapshot
                        }
                    }
                    if (timedOutRef != null) {
                        val snap = timedOutSnap
                        enqueuePostProcess { saveJpegBytes(timedOutRef!!, exifSnapshot = snap) }
                        isCapturing = false
                        onExposureCompleted?.invoke()
                    }
                }, 4500L)
                session.captureBurst(burstRequests, captureCallback, backgroundHandler)
            } else {
                if (willCaptureJpeg) {
                    synchronized(hdrLock) {
                        while (pendingJpegSnapshots.size >= 12) {
                            pendingJpegSnapshots.removeAt(0)
                        }
                        pendingJpegSnapshots.add(shotSnapshot)
                    }
                }
                session.capture(captureBuilder.build(), captureCallback, backgroundHandler)
            }
        } catch (e: Exception) {
            isCapturing = false
            onLongExposureStateChanged?.invoke(false)
            onExposureCompleted?.invoke()
            Log.e(TAG, "Failed to capture picture", e)
            onError("Capture error: ${e.message}")
        }
    }

    private fun degreesToExifOrientation(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun exifOrientationToDegrees(exifOrientation: Int): Float = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    private fun buildHdrBurstRequests(
        device: CameraDevice,
        chars: CameraCharacteristics,
        baseBuilder: CaptureRequest.Builder,
        willCaptureRaw: Boolean
    ): List<CaptureRequest> {
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.5f

        val aeMode = baseBuilder.get(CaptureRequest.CONTROL_AE_MODE) ?: CaptureRequest.CONTROL_AE_MODE_ON
        val canLockManualExposure = isoRange != null && shutterRange != null &&
            (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF || (lastMeasuredIso > 0 && lastMeasuredShutter > 0L))

        val baseIso = if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
            baseBuilder.get(CaptureRequest.SENSOR_SENSITIVITY) ?: lastMeasuredIso.coerceAtLeast(100)
        } else {
            lastMeasuredIso.coerceAtLeast(100)
        }
        val baseNanos = if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
            baseBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME) ?: lastMeasuredShutter.coerceAtLeast(1_000_000L)
        } else {
            lastMeasuredShutter.coerceAtLeast(1_000_000L)
        }

        val frameTags = listOf("HDR_REF", "HDR_UNDER", "HDR_OVER")
        return frameTags.mapIndexed { idx, tag ->
            val b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            jpegImageReader?.surface?.let { b.addTarget(it) }
            if (idx == 0 && willCaptureRaw) {
                rawImageReader?.surface?.let { b.addTarget(it) }
            }

            // Copy common photographic settings from baseBuilder
            b.set(CaptureRequest.CONTROL_AF_MODE, baseBuilder.get(CaptureRequest.CONTROL_AF_MODE))
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, baseBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE))
            b.set(CaptureRequest.CONTROL_AWB_MODE, baseBuilder.get(CaptureRequest.CONTROL_AWB_MODE))
            baseBuilder.get(CaptureRequest.COLOR_CORRECTION_MODE)?.let { b.set(CaptureRequest.COLOR_CORRECTION_MODE, it) }
            baseBuilder.get(CaptureRequest.COLOR_CORRECTION_GAINS)?.let { b.set(CaptureRequest.COLOR_CORRECTION_GAINS, it) }
            baseBuilder.get(CaptureRequest.TONEMAP_MODE)?.let { b.set(CaptureRequest.TONEMAP_MODE, it) }
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, baseBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE))
            b.set(CaptureRequest.EDGE_MODE, baseBuilder.get(CaptureRequest.EDGE_MODE))
            b.set(CaptureRequest.JPEG_QUALITY, baseBuilder.get(CaptureRequest.JPEG_QUALITY))
            b.set(CaptureRequest.JPEG_ORIENTATION, baseBuilder.get(CaptureRequest.JPEG_ORIENTATION))
            baseBuilder.get(CaptureRequest.JPEG_GPS_LOCATION)?.let { b.set(CaptureRequest.JPEG_GPS_LOCATION, it) }
            baseBuilder.get(CaptureRequest.FLASH_MODE)?.let { b.set(CaptureRequest.FLASH_MODE, it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                baseBuilder.get(CaptureRequest.CONTROL_ZOOM_RATIO)?.let { b.set(CaptureRequest.CONTROL_ZOOM_RATIO, it) }
            } else {
                baseBuilder.get(CaptureRequest.SCALER_CROP_REGION)?.let { b.set(CaptureRequest.SCALER_CROP_REGION, it) }
            }

            if (canLockManualExposure && isoRange != null && shutterRange != null) {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val (targetIso, targetNanos) = when (idx) {
                    0 -> baseIso.coerceIn(isoRange.lower, isoRange.upper) to
                        baseNanos.coerceIn(shutterRange.lower, shutterRange.upper)
                    1 -> {
                        // -2 EV: prioritize 4x faster shutter to freeze motion & highlights
                        val underNanos = (baseNanos / 4L).coerceIn(shutterRange.lower, shutterRange.upper)
                        val ratioAchieved = baseNanos.toDouble() / underNanos.coerceAtLeast(1L).toDouble()
                        val extraDiv = (4.0 / ratioAchieved).coerceAtLeast(1.0)
                        val underIso = (baseIso / extraDiv).toInt().coerceIn(isoRange.lower, isoRange.upper)
                        underIso to underNanos
                    }
                    else -> {
                        // +2 EV: cap handheld shutter at 250ms, make up remaining EV with ISO
                        val maxHandheldShutter = minOf(shutterRange.upper, 250_000_000L).coerceAtLeast(shutterRange.lower)
                        val overNanos = (baseNanos * 4L).coerceIn(shutterRange.lower, maxHandheldShutter)
                        val ratioAchieved = overNanos.toDouble() / baseNanos.coerceAtLeast(1L).toDouble()
                        val extraGain = (4.0 / ratioAchieved).coerceAtLeast(1.0)
                        val overIso = (baseIso * extraGain).toInt().coerceIn(isoRange.lower, isoRange.upper)
                        overIso to overNanos
                    }
                }
                b.set(CaptureRequest.SENSOR_SENSITIVITY, targetIso)
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetNanos)
            } else {
                b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
                if (evRange != null) {
                    val baseEv = baseBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
                    val deltaSteps = (2.0f / evStep.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)
                    val targetEv = when (idx) {
                        0 -> baseEv
                        1 -> (baseEv - deltaSteps).coerceIn(evRange.lower, evRange.upper)
                        else -> (baseEv + deltaSteps).coerceIn(evRange.lower, evRange.upper)
                    }
                    b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, targetEv)
                }
            }

            b.setTag(tag)
            b.build()
        }
    }

    private fun saveJpegBytes(
        bytes: ByteArray,
        hdrUnderBytes: ByteArray? = null,
        hdrOverBytes: ByteArray? = null,
        exifSnapshot: CaptureExifSnapshot? = null
    ) {
        val snapshot = exifSnapshot ?: CaptureExifSnapshot(
            captureTimeMs = System.currentTimeMillis(),
            characteristics = currentCharacteristics,
            exposureMode = currentExposureMode,
            iso = if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.SHUTTER_PRIORITY) {
                (lastCapturedIso ?: lastMeasuredIso).coerceAtLeast(1)
            } else {
                currentIso.coerceAtLeast(1)
            },
            exposureNanos = if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.ISO_PRIORITY) {
                (lastCapturedExposureNanos ?: lastMeasuredShutter).coerceAtLeast(1L)
            } else {
                currentShutterNanos.coerceAtLeast(1L)
            },
            evIndex = currentEvIndex,
            evStep = currentCharacteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.5f,
            whiteBalancePreset = currentWbPreset,
            flashMode = currentFlashMode,
            flashFired = currentFlashMode == FlashMode.ON || currentFlashMode == FlashMode.TORCH,
            digitalZoomRatio = currentZoomRatio,
            tapAeActive = currentAeMeteringRect != null,
            location = if (isGeotaggingEnabled) locationHelper.getBestLocation() else null,
            isExifSoftwareTagEnabled = isExifSoftwareTagEnabled,
            deviceJpegOrientation = lastJpegOrientation
        )

        val captureDate = Date(snapshot.captureTimeMs)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(captureDate)
        val count = photoCounter.incrementAndGet() % 10000
        val fileName = "IMG_${timeStamp}_${String.format(Locale.US, "%04d", count)}.jpg"
        val loc = snapshot.location

        // Extract ALL HAL EXIF tags BEFORE any Bitmap re-encoding strips APP1!
        val halExifTags = JpegExifHelper.extractHalExif(bytes)

        val chars = snapshot.characteristics
        val isFrontCam = chars?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val shouldMirror = isFrontCam && isMirrorSelfie
        val shouldApplyColor = colorProfile != ColorProfile.OFF
        val shouldApplyHdr = hdrUnderBytes != null && hdrOverBytes != null
        var wasReencodedUpright = false
        var preservedExifOrientation = ExifInterface.ORIENTATION_UNDEFINED
        val finalBytes = if (shouldMirror || shouldApplyColor || shouldApplyHdr) {
            try {
                val rawExifOrientation = try {
                    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
                } catch (e: Exception) {
                    ExifInterface.ORIENTATION_UNDEFINED
                }
                preservedExifOrientation = rawExifOrientation
                val decodeOpts = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val origBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                if (origBitmap != null) {
                    if (shouldApplyHdr) {
                        HdrProcessor.mergeHdrInPlace(origBitmap, hdrUnderBytes!!, hdrOverBytes!!)
                    }
                    if (shouldApplyColor) {
                        NaturalColorProcessor.processBitmapInPlace(origBitmap, colorProfile)
                    }
                    val outBitmap = if (shouldMirror) {
                        val rotDeg = when {
                            rawExifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                                rawExifOrientation == ExifInterface.ORIENTATION_ROTATE_180 ||
                                rawExifOrientation == ExifInterface.ORIENTATION_ROTATE_270 -> exifOrientationToDegrees(rawExifOrientation)
                            (rawExifOrientation == ExifInterface.ORIENTATION_UNDEFINED || rawExifOrientation == ExifInterface.ORIENTATION_NORMAL) &&
                                (snapshot.deviceJpegOrientation == 90 || snapshot.deviceJpegOrientation == 270) &&
                                origBitmap.width > origBitmap.height -> snapshot.deviceJpegOrientation.toFloat()
                            else -> 0f
                        }
                        val matrix = Matrix().apply {
                            if (rotDeg != 0f) postRotate(rotDeg)
                            postScale(-1f, 1f)
                        }
                        wasReencodedUpright = true
                        Bitmap.createBitmap(origBitmap, 0, 0, origBitmap.width, origBitmap.height, matrix, true)
                    } else {
                        origBitmap
                    }
                    val baos = ByteArrayOutputStream()
                    outBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    if (outBitmap != origBitmap) outBitmap.recycle()
                    origBitmap.recycle()
                    baos.toByteArray()
                } else bytes
            } catch (e: Exception) {
                Log.e(TAG, "JPEG color/mirror post-processing failed", e)
                bytes
            }
        } else {
            bytes
        }

        // 1. Write bytes to temporary file on disk so ExifInterface can safely write GPS & EXIF tags
        val tempFile = java.io.File(context.cacheDir, "temp_$fileName")
        var imgWidth = 4000
        var imgHeight = 3000
        var finalExifOrientation = ExifInterface.ORIENTATION_NORMAL

        val apertures = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val apertureFloat = apertures?.firstOrNull()?.takeIf { it > 0f } ?: 1.8f
        val ap = String.format(Locale.US, "f/%.1f", apertureFloat)
        val focalArr = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focalFloat = focalArr?.firstOrNull()?.takeIf { it > 0f } ?: 5.0f
        val focalStr = String.format(Locale.US, "%.1fmm", focalFloat)
        val effectiveIso = snapshot.iso.coerceAtLeast(1)
        val effectiveExposureNanos = snapshot.exposureNanos.coerceAtLeast(1L)

        try {
            tempFile.outputStream().use { fos ->
                fos.write(finalBytes)
            }

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOpts)
            if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
                imgWidth = boundsOpts.outWidth
                imgHeight = boundsOpts.outHeight
            }

            val exif = ExifInterface(tempFile.absolutePath)
            val fileExifOrientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            val halOrientation = if (fileExifOrientation != ExifInterface.ORIENTATION_UNDEFINED) {
                fileExifOrientation
            } else {
                preservedExifOrientation
            }
            finalExifOrientation = when {
                wasReencodedUpright -> ExifInterface.ORIENTATION_NORMAL
                halOrientation == ExifInterface.ORIENTATION_UNDEFINED -> degreesToExifOrientation(snapshot.deviceJpegOrientation)
                halOrientation == ExifInterface.ORIENTATION_NORMAL &&
                    (snapshot.deviceJpegOrientation == 90 || snapshot.deviceJpegOrientation == 270) &&
                    imgWidth > imgHeight -> degreesToExifOrientation(snapshot.deviceJpegOrientation)
                else -> halOrientation
            }

            JpegExifHelper.writeCompleteExif(
                exif = exif,
                halTags = halExifTags,
                snapshot = snapshot,
                imgWidth = imgWidth,
                imgHeight = imgHeight,
                finalOrientation = finalExifOrientation
            )
            exif.saveAttributes()

            if (imgWidth <= 0 || imgHeight <= 0) {
                imgWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 4000)
                imgHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EXIF to temp file", e)
        }

        // 2. Stream byte-complete JPEG (with valid EXIF GPS & metadata) directly into MediaStore
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, snapshot.captureTimeMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            } else if (loc != null) {
                put(MediaStore.Images.Media.LATITUDE, loc.latitude)
                put(MediaStore.Images.Media.LONGITUDE, loc.longitude)
            }
        }

        val uri = try {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed inserting JPEG MediaStore row", t)
            tempFile.delete()
            null
        }
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

            val shutterStr = HardwareCapabilities.formatShutterSpeed(effectiveExposureNanos)
            val isExifRotated = finalExifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                finalExifOrientation == ExifInterface.ORIENTATION_ROTATE_270
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = false,
                width = if (isExifRotated) imgHeight else imgWidth,
                height = if (isExifRotated) imgWidth else imgHeight,
                iso = effectiveIso,
                shutterSpeed = shutterStr,
                focalLength = focalStr,
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSizeBytes = writtenBytes,
                aperture = ap
            )
            onPhotoSaved(info)
        }
    }

    private fun resolveCharacteristicsForDng(
        baseCharacteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        imageWidth: Int,
        imageHeight: Int
    ): CameraCharacteristics {
        if (doesSizeMatchSensorForDng(baseCharacteristics, imageWidth, imageHeight)) {
            return baseCharacteristics
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activePhysId = captureResult.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            if (!activePhysId.isNullOrBlank()) {
                try {
                    val physChars = cameraManager.getCameraCharacteristics(activePhysId)
                    if (doesSizeMatchSensorForDng(physChars, imageWidth, imageHeight)) {
                        Log.i(TAG, "Resolved DNG characteristics via activePhysicalId=$activePhysId (${imageWidth}x$imageHeight)")
                        return physChars
                    }
                } catch (_: Exception) {}
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                for (physId in baseCharacteristics.physicalCameraIds) {
                    try {
                        val physChars = cameraManager.getCameraCharacteristics(physId)
                        if (doesSizeMatchSensorForDng(physChars, imageWidth, imageHeight)) {
                            Log.i(TAG, "Resolved DNG characteristics via physicalCameraIds=$physId (${imageWidth}x$imageHeight)")
                            return physChars
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        return baseCharacteristics
    }

    private fun saveDngImage(
        image: android.media.Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        orientationDegrees: Int = lastJpegOrientation
    ) {
        val dngChars = resolveCharacteristicsForDng(characteristics, captureResult, image.width, image.height)
        val preCorr = dngChars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        val pixelSize = dngChars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        Log.i(
            TAG,
            "saveDngImage cam=$currentCameraId img=${image.width}x${image.height} fmt=${image.format} " +
                "preCorr=${preCorr?.width()}x${preCorr?.height()} pixel=${pixelSize?.width}x${pixelSize?.height}"
        )

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val count = photoCounter.incrementAndGet() % 10000
        val fileName = "RAW_${timeStamp}_${String.format(Locale.US, "%04d", count)}.dng"
        val loc = if (isGeotaggingEnabled) locationHelper.getBestLocation() else null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            } else if (loc != null) {
                put(MediaStore.Images.Media.LATITUDE, loc.latitude)
                put(MediaStore.Images.Media.LONGITUDE, loc.longitude)
            }
        }

        val uri = try {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed inserting DNG MediaStore row", t)
            onCaptureError?.invoke("Failed to create DNG file in MediaStore: ${t.message}")
            null
        }
        uri?.let { destUri ->
            var writtenBytes = 0L
            var saveError: Throwable? = null
            var dngCreator: DngCreator? = null
            try {
                context.contentResolver.openOutputStream(destUri)?.use { rawStream ->
                    val countingStream = object : java.io.FilterOutputStream(rawStream) {
                        override fun write(b: Int) {
                            out.write(b)
                            writtenBytes++
                        }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            out.write(b, off, len)
                            writtenBytes += len
                        }
                    }
                    val buffered = BufferedOutputStream(countingStream, 65536)
                    dngCreator = DngCreator(dngChars, captureResult)
                    dngCreator?.setOrientation(degreesToExifOrientation(orientationDegrees))
                    if (loc != null) {
                        try {
                            dngCreator?.setLocation(loc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping DNG GPS location tag", e)
                        }
                    }
                    dngCreator?.writeImage(buffered, image)
                    buffered.flush()
                    countingStream.flush()
                }
            } catch (t: Throwable) {
                saveError = t
                Log.e(
                    TAG,
                    "Failed to write DNG image (cam=$currentCameraId img=${image.width}x${image.height} " +
                        "preCorr=${preCorr?.width()}x${preCorr?.height()} pixel=${pixelSize?.width}x${pixelSize?.height})",
                    t
                )
            } finally {
                try { dngCreator?.close() } catch (_: Throwable) {}
            }

            if (saveError != null || writtenBytes <= 0L) {
                try { context.contentResolver.delete(destUri, null, null) } catch (_: Exception) {}
                val diag = "RAW (DNG) failed [${image.width}x${image.height} vs ${preCorr?.width()}x${preCorr?.height()}]: ${saveError?.message ?: "0 bytes"}"
                onCaptureError?.invoke(diag)
                return
            }

            val apertures = dngChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            val ap = if (apertures != null && apertures.isNotEmpty()) "f/${apertures[0]}" else "f/1.8"
            val focalArr = dngChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalStr = if (focalArr != null && focalArr.isNotEmpty()) {
                String.format(Locale.US, "%.1fmm", focalArr[0])
            } else {
                "5.0mm"
            }

            val shutterStr = HardwareCapabilities.formatShutterSpeed(
                if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.ISO_PRIORITY) lastMeasuredShutter else currentShutterNanos
            )
            val exifOrientation = degreesToExifOrientation(orientationDegrees)
            val isPortrait = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == ExifInterface.ORIENTATION_ROTATE_270
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = true,
                width = if (isPortrait) image.height else image.width,
                height = if (isPortrait) image.width else image.height,
                iso = if (currentExposureMode == ExposureMode.AUTO || currentExposureMode == ExposureMode.SHUTTER_PRIORITY) lastMeasuredIso else currentIso,
                shutterSpeed = shutterStr,
                focalLength = focalStr,
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSizeBytes = writtenBytes,
                aperture = ap
            )
            onPhotoSaved(info)
        }
    }

    fun closeCamera() {
        clearPendingRawBuffers()
        isRawStreamConfigured = false
        isAnalysisTargetAttached = false
        isCapturing = false
        isExposureClamped = false
        forceHardwareAeOnce = false
        prevPriorityMode = null
        prevLockedShutterForS = 0L
        prevLockedIsoForI = 0
        aeMeteringSettleUntilMs = 0L
        lastMeteringAdjustMs = 0L
        spotAeNormX = null
        spotAeNormY = null
        clampReleaseCooldownUntilMs = 0L
        lastHistogramFrameMs = 0L
        lastFallbackProbeMs = 0L
        clampedEnteredMs = 0L
        lumaCheckCounter = 0
        onLongExposureStateChanged?.invoke(false)
        onExposureCompleted?.invoke()
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
