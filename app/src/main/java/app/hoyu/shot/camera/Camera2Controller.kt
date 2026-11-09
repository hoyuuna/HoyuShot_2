package app.hoyu.shot.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import app.hoyu.shot.camera.analyzer.HistogramAnalyzer
import app.hoyu.shot.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var currentExposureMode = ExposureMode.AUTO
    private var currentIso = 100
    private var currentShutterNanos = 10_000_000L
    private var currentFocusDistance = 0f
    private var currentIsManualFocus = false
    private var currentEvIndex = 0
    private var currentWbPreset = WhiteBalancePreset.AUTO
    private var currentKelvin = 5500
    private var currentZoomRatio = 1.0f
    private var currentFlashMode = FlashMode.OFF

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
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
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
        openCamera(newCameraId, newSurface, targetRatio, onPreviewSizeCalculated)
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
        val largestJpeg = (if (matchingJpeg.isNotEmpty()) matchingJpeg else jpegSizes.toList())
            .maxByOrNull { it.width * it.height }

        if (largestJpeg != null) {
            jpegImageReader = ImageReader.newInstance(
                largestJpeg.width, largestJpeg.height, ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    backgroundHandler?.post {
                        saveJpegImage(image)
                        image.close()
                    }
                }, backgroundHandler)
            }
        }

        // 3. RAW_SENSOR stream
        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
        val largestRaw = rawSizes?.maxByOrNull { it.width * it.height }
        if (largestRaw != null) {
            rawImageReader = ImageReader.newInstance(
                largestRaw.width, largestRaw.height, ImageFormat.RAW_SENSOR, 2
            )
        }
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val pSurface = previewSurface ?: return

        try {
            val surfaces = mutableListOf<Surface>().apply {
                add(pSurface)
                analysisImageReader?.surface?.let { add(it) }
                jpegImageReader?.surface?.let { add(it) }
                rawImageReader?.surface?.let { add(it) }
            }

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                analysisImageReader?.surface?.let { addTarget(it) }
            }

            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    applyAllSettingsInternal()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Failed to configure CameraCaptureSession")
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
            onFrameMetadata(iso, shutter)
        }
    }

    /**
     * Tap to Focus and Meter on specific coordinate
     */
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

            // Convert normalized (0..1) screen coordinates to sensor active array coordinates
            val sensorX = (normY * activeArray.width()).toInt().coerceIn(0, activeArray.width())
            val sensorY = ((1f - normX) * activeArray.height()).toInt().coerceIn(0, activeArray.height())

            val halfBox = (activeArray.width() * 0.08f).toInt()
            val meteringBox = Rect(
                (sensorX - halfBox).coerceAtLeast(0),
                (sensorY - halfBox).coerceAtLeast(0),
                (sensorX + halfBox).coerceAtMost(activeArray.width()),
                (sensorY + halfBox).coerceAtMost(activeArray.height())
            )

            val meteringRect = MeteringRectangle(meteringBox, MeteringRectangle.METERING_WEIGHT_MAX)

            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
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

                    // Resume repeating request with triggered region
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Tap-to-focus failed", e)
            onFocusResult(false)
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
        flashMode: FlashMode
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

            // 2. Exposure Mode
            when (currentExposureMode) {
                ExposureMode.AUTO, ExposureMode.PROGRAM -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEvIndex)
                }
                ExposureMode.SHUTTER_PRIORITY -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentShutterNanos)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                }
                ExposureMode.ISO_PRIORITY -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentShutterNanos)
                }
                ExposureMode.MANUAL -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentShutterNanos)
                }
            }

            // 3. Locks
            builder.set(CaptureRequest.CONTROL_AE_LOCK, isAeLocked)

            // 4. Focus
            if (currentIsManualFocus) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance)
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
            when (currentFlashMode) {
                FlashMode.OFF -> {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.AUTO -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                FlashMode.ON -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                }
                FlashMode.TORCH -> {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }

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

    /**
     * Executes Still Photo Capture with Pre-capture Lock Routine
     */
    fun takePicture(captureRaw: Boolean) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return

        if (isCapturing) return
        isCapturing = true

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

                // Copy zoom
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val zoom = preview.get(CaptureRequest.CONTROL_ZOOM_RATIO)
                    if (zoom != null) captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
                } else {
                    val crop = preview.get(CaptureRequest.SCALER_CROP_REGION)
                    if (crop != null) captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop)
                }
            }

            captureBuilder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    if (willCaptureRaw) {
                        rawImageReader?.setOnImageAvailableListener({ reader ->
                            val rawImage = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                            backgroundHandler?.post {
                                saveDngImage(rawImage, chars, result)
                                rawImage.close()
                            }
                        }, backgroundHandler)
                    }

                    isCapturing = false
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

    private fun saveJpegImage(image: android.media.Image) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "IMG_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream.write(bytes)
            }

            val shutterStr = HardwareCapabilities.formatShutterSpeed(currentShutterNanos)
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = false,
                width = image.width,
                height = image.height,
                iso = currentIso,
                shutterSpeed = shutterStr,
                focalLength = "1x",
                timestamp = System.currentTimeMillis()
            )
            onPhotoSaved(info)
        }
    }

    private fun saveDngImage(
        image: android.media.Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "RAW_$timeStamp.dng"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HoyuShot")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                val dngCreator = DngCreator(characteristics, captureResult)
                dngCreator.writeImage(outputStream, image)
                dngCreator.close()
            }

            val shutterStr = HardwareCapabilities.formatShutterSpeed(currentShutterNanos)
            val info = CapturedPhotoInfo(
                uriString = destUri.toString(),
                isRaw = true,
                width = image.width,
                height = image.height,
                iso = currentIso,
                shutterSpeed = shutterStr,
                focalLength = "1x",
                timestamp = System.currentTimeMillis()
            )
            onPhotoSaved(info)
        }
    }

    fun closeCamera() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
}
