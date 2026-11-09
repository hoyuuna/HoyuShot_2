package app.hoyu.shot.ui

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hoyu.shot.camera.Camera2Controller
import app.hoyu.shot.camera.CameraHardwareInspector
import app.hoyu.shot.data.SettingsRepository
import app.hoyu.shot.model.*
import app.hoyu.shot.sensors.DeviceOrientationListener
import app.hoyu.shot.sensors.HorizonSensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val horizonSensor = HorizonSensorManager(application)
    private val orientationListener = DeviceOrientationListener(application)
    private val inspector = CameraHardwareInspector(application)
    private val settingsRepo = SettingsRepository(application)

    private var activeSurface: Surface? = null
    private var timerJob: Job? = null

    private val cameraController = Camera2Controller(
        context = application,
        onHistogramReady = { hist ->
            _uiState.update { it.copy(histogram = hist) }
        },
        onFrameMetadata = { actualIso, actualShutter ->
            _uiState.update {
                it.copy(
                    actualIso = actualIso,
                    actualShutterSpeedNanos = actualShutter
                )
            }
        },
        onPhotoSaved = { info ->
            _uiState.update { current ->
                val updatedList = listOf(info) + current.recentPhotos.filter { p -> p.uriString != info.uriString }
                current.copy(
                    isCapturing = false,
                    lastCapturedPhoto = info,
                    recentPhotos = updatedList
                )
            }
        },
        onError = { err ->
            _uiState.update { it.copy(errorMessage = err, isCapturing = false) }
        }
    )

    init {
        loadRecentPhotos()

        // Collect horizon tilt angle in real time
        viewModelScope.launch {
            horizonSensor.orientationFlow.collect { orientation ->
                _uiState.update { it.copy(horizon = orientation) }
            }
        }

        // Collect device orientation to rotate buttons and icons in-place
        viewModelScope.launch {
            orientationListener.rotationDegrees.collect { deg ->
                _uiState.update { it.copy(uiRotationDegrees = deg) }
            }
        }

        // Discover all physical and logical lenses (0.6x, 1x, 3.2x, Front)
        val allLenses = inspector.discoverAllLenses()
        val isResume = settingsRepo.isResumeSettingsEnabled

        val targetCamId = if (isResume) settingsRepo.savedCameraId else "0"
        val defaultLens = allLenses.find { it.cameraId == targetCamId && !it.isFront }
            ?: allLenses.find { !it.isFront && it.label == "1x" }
            ?: allLenses.firstOrNull { !it.isFront }
            ?: allLenses.firstOrNull()

        val initialCameraId = defaultLens?.cameraId ?: "0"
        val caps = inspector.inspectCamera(initialCameraId)

        val minZoom = allLenses.filter { !it.isFront }.minOfOrNull { it.zoomRatio } ?: 0.6f
        val maxZoom = 30.0f

        val initMode = if (isResume) {
            try { ExposureMode.valueOf(settingsRepo.savedExposureMode) } catch (e: Exception) { ExposureMode.AUTO }
        } else ExposureMode.AUTO

        val initIso = if (isResume) settingsRepo.savedIso else (caps?.isoRange?.lower ?: 100)
        val initShutter = if (isResume) settingsRepo.savedShutterNanos else (caps?.shutterSpeedRangeNanos?.lower ?: 10_000_000L)
        val initFocus = if (isResume) settingsRepo.savedFocusDiopters else 0f
        val initIsManualFocus = if (isResume) settingsRepo.savedIsManualFocus else false
        val initEv = if (isResume) settingsRepo.savedEvIndex else 0
        val initKelvin = if (isResume) settingsRepo.savedKelvin else 5500
        val initRaw = if (isResume) settingsRepo.savedRawEnabled else false

        val initNr = if (isResume) {
            try { NoiseReductionMode.valueOf(settingsRepo.savedNoiseReduction) } catch (e: Exception) { NoiseReductionMode.FAST }
        } else NoiseReductionMode.FAST

        val initSharp = if (isResume) {
            try { SharpnessMode.valueOf(settingsRepo.savedSharpness) } catch (e: Exception) { SharpnessMode.NORMAL }
        } else SharpnessMode.NORMAL

        _uiState.update {
            it.copy(
                availableLenses = allLenses,
                currentLens = defaultLens,
                currentCameraId = initialCameraId,
                currentZoomRatio = defaultLens?.zoomRatio ?: 1.0f,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                capabilities = caps,
                exposureMode = initMode,
                iso = initIso,
                shutterSpeedNanos = initShutter,
                focusDistanceDiopters = initFocus,
                isManualFocusEnabled = initIsManualFocus,
                evCompensationIndex = initEv,
                kelvinTemperature = initKelvin,
                captureRaw = initRaw,
                isResumeSettingsEnabled = isResume,
                noiseReductionMode = initNr,
                sharpnessMode = initSharp
            )
        }
    }

    fun startSensors() {
        horizonSensor.start()
        orientationListener.start()
    }

    fun stopSensors() {
        horizonSensor.stop()
        orientationListener.stop()
    }

    fun startCamera(previewSurface: Surface) {
        this.activeSurface = previewSurface
        val cameraId = _uiState.value.currentCameraId
        val ratio = _uiState.value.aspectRatio

        cameraController.openCamera(cameraId, previewSurface, ratio) { width, height ->
            _uiState.update { it.copy(previewSize = Pair(width, height)) }
        }
        _uiState.update { it.copy(isCameraReady = true) }
        applyCurrentSettings()
    }

    fun onTouchFocus(offset: Offset, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val normX = (offset.x / viewWidth).coerceIn(0f, 1f)
        val normY = (offset.y / viewHeight).coerceIn(0f, 1f)

        _uiState.update {
            it.copy(
                tapFocusPoint = TapFocusPoint(offset, FocusState.FOCUSING, System.currentTimeMillis()),
                isManualFocusEnabled = false
            )
        }

        cameraController.triggerTapToFocus(normX, normY) { success ->
            _uiState.update { current ->
                val prevPoint = current.tapFocusPoint
                if (prevPoint != null) {
                    current.copy(
                        tapFocusPoint = prevPoint.copy(
                            state = if (success) FocusState.LOCKED_SUCCESS else FocusState.LOCKED_FAILED
                        )
                    )
                } else current
            }
        }
    }

    fun toggleResumeSettings() {
        val next = !settingsRepo.isResumeSettingsEnabled
        settingsRepo.isResumeSettingsEnabled = next
        _uiState.update { it.copy(isResumeSettingsEnabled = next) }
        if (next) persistCurrentSettings()
    }

    fun cycleNoiseReduction() {
        val next = when (_uiState.value.noiseReductionMode) {
            NoiseReductionMode.OFF -> NoiseReductionMode.FAST
            NoiseReductionMode.FAST -> NoiseReductionMode.HIGH_QUALITY
            NoiseReductionMode.HIGH_QUALITY -> NoiseReductionMode.OFF
        }
        _uiState.update { it.copy(noiseReductionMode = next) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedNoiseReduction = next.name
        applyCurrentSettings()
    }

    fun cycleSharpness() {
        val next = when (_uiState.value.sharpnessMode) {
            SharpnessMode.OFF -> SharpnessMode.NORMAL
            SharpnessMode.NORMAL -> SharpnessMode.HIGH
            SharpnessMode.HIGH -> SharpnessMode.OFF
        }
        _uiState.update { it.copy(sharpnessMode = next) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedSharpness = next.name
        applyCurrentSettings()
    }

    fun toggleAeLock() {
        val newLocked = !_uiState.value.isAeLocked
        _uiState.update { it.copy(isAeLocked = newLocked) }
        cameraController.setAeLock(newLocked)
    }

    fun toggleAfLock() {
        val newLocked = !_uiState.value.isAfLocked
        _uiState.update { it.copy(isAfLocked = newLocked) }
        cameraController.setAfLock(newLocked)
    }

    fun toggleFlash() {
        val next = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        _uiState.update { it.copy(flashMode = next) }
        applyCurrentSettings()
    }

    fun toggleTimer() {
        val next = when (_uiState.value.timerMode) {
            TimerMode.OFF -> TimerMode.SEC_3
            TimerMode.SEC_3 -> TimerMode.SEC_5
            TimerMode.SEC_5 -> TimerMode.SEC_10
            TimerMode.SEC_10 -> TimerMode.OFF
        }
        _uiState.update { it.copy(timerMode = next) }
    }

    fun toggleGrid() {
        val next = when (_uiState.value.gridMode) {
            GridMode.NONE -> GridMode.THIRDS
            GridMode.THIRDS -> GridMode.GOLDEN
            GridMode.GOLDEN -> GridMode.NONE
        }
        _uiState.update { it.copy(gridMode = next) }
    }

    fun toggleHistogram() {
        _uiState.update { it.copy(isHistogramVisible = !it.isHistogramVisible) }
    }

    fun toggleHorizon() {
        _uiState.update { it.copy(isHorizonLevelVisible = !it.isHorizonLevelVisible) }
    }

    fun toggleFocusPeaking() {
        _uiState.update { it.copy(isFocusPeakingEnabled = !it.isFocusPeakingEnabled) }
    }

    fun toggleZebraStripes() {
        _uiState.update { it.copy(isZebraStripesEnabled = !it.isZebraStripesEnabled) }
    }

    fun openReviewDialog() {
        if (_uiState.value.recentPhotos.isNotEmpty() || _uiState.value.lastCapturedPhoto != null) {
            _uiState.update { it.copy(isGalleryVisible = true) }
        }
    }

    fun closeReviewDialog() {
        _uiState.update { it.copy(isGalleryVisible = false) }
    }

    fun openGallery() {
        if (_uiState.value.recentPhotos.isNotEmpty() || _uiState.value.lastCapturedPhoto != null) {
            _uiState.update { it.copy(isGalleryVisible = true) }
        }
    }

    fun closeGallery() {
        _uiState.update { it.copy(isGalleryVisible = false) }
    }

    fun loadRecentPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<CapturedPhotoInfo>()
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val cursor = getApplication<Application>().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val widthCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    var count = 0
                    while (c.moveToNext() && count < 25) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "IMG.jpg"
                        val w = c.getInt(widthCol)
                        val h = c.getInt(heightCol)
                        val sz = c.getLong(sizeCol)
                        val date = c.getLong(dateCol) * 1000L
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        list.add(
                            CapturedPhotoInfo(
                                uriString = contentUri.toString(),
                                isRaw = name.endsWith(".dng", ignoreCase = true),
                                width = if (w > 0) w else 4000,
                                height = if (h > 0) h else 3000,
                                iso = 100,
                                shutterSpeed = "1/120s",
                                focalLength = "1x",
                                timestamp = date,
                                fileName = name,
                                fileSizeBytes = sz,
                                aperture = "f/1.8"
                            )
                        )
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _uiState.update { current ->
                current.copy(
                    recentPhotos = list,
                    lastCapturedPhoto = list.firstOrNull() ?: current.lastCapturedPhoto
                )
            }
        }
    }

    fun deletePhoto(info: CapturedPhotoInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(info.uriString)
                getApplication<Application>().contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _uiState.update { current ->
                val remaining = current.recentPhotos.filter { it.uriString != info.uriString }
                current.copy(
                    recentPhotos = remaining,
                    lastCapturedPhoto = remaining.firstOrNull(),
                    isGalleryVisible = remaining.isNotEmpty()
                )
            }
        }
    }

    fun setCaptureFormat(format: CaptureFormat) {
        val willCaptureRaw = format == CaptureFormat.RAW || format == CaptureFormat.RAW_JPEG
        _uiState.update {
            it.copy(
                captureFormat = format,
                captureRaw = willCaptureRaw
            )
        }
        if (_uiState.value.isResumeSettingsEnabled) {
            settingsRepo.savedRawEnabled = willCaptureRaw
        }
    }

    fun setMegapixels(mp: String) {
        val targetSize = when (mp) {
            "12.6 MP" -> Pair(4096, 3072)
            "6.2 MP" -> Pair(2880, 2160)
            "2.8 MP" -> Pair(1920, 1440)
            else -> null
        }
        _uiState.update { it.copy(selectedMegapixels = mp) }
        cameraController.targetResolution = targetSize

        val surface = activeSurface
        if (surface != null) {
            cameraController.switchCamera(_uiState.value.currentCameraId, surface, _uiState.value.aspectRatio) { w, h ->
                _uiState.update { it.copy(previewSize = Pair(w, h)) }
            }
        }
    }

    fun selectLens(lens: LensItem) {
        val surface = activeSurface ?: return
        val currentCamId = _uiState.value.currentCameraId

        _uiState.update {
            it.copy(
                currentLens = lens,
                currentZoomRatio = lens.zoomRatio
            )
        }

        if (lens.cameraId != currentCamId) {
            val caps = inspector.inspectCamera(lens.cameraId)
            _uiState.update {
                it.copy(
                    currentCameraId = lens.cameraId,
                    capabilities = caps
                )
            }
            if (_uiState.value.isResumeSettingsEnabled) {
                settingsRepo.savedCameraId = lens.cameraId
            }
            cameraController.switchCamera(lens.cameraId, surface, _uiState.value.aspectRatio) { width, height ->
                _uiState.update { it.copy(previewSize = Pair(width, height)) }
            }
        }

        applyCurrentSettings()
    }

    fun setZoomRatio(zoom: Float) {
        val clamped = zoom.coerceIn(_uiState.value.minZoomRatio, _uiState.value.maxZoomRatio)
        _uiState.update { it.copy(currentZoomRatio = clamped) }
        applyCurrentSettings()
    }

    fun toggleCameraFacing() {
        val currentIsFront = _uiState.value.currentLens?.isFront ?: false
        val targetLens = if (currentIsFront) {
            _uiState.value.availableLenses.find { !it.isFront && it.label == "1x" }
                ?: _uiState.value.availableLenses.firstOrNull { !it.isFront }
        } else {
            _uiState.value.availableLenses.firstOrNull { it.isFront }
        }

        if (targetLens != null) {
            selectLens(targetLens)
        }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        val surface = activeSurface ?: return
        _uiState.update { it.copy(aspectRatio = ratio) }

        val cameraId = _uiState.value.currentCameraId
        cameraController.switchCamera(cameraId, surface, ratio) { width, height ->
            _uiState.update { it.copy(previewSize = Pair(width, height)) }
        }
        applyCurrentSettings()
    }

    fun stopCamera() {
        cameraController.closeCamera()
        _uiState.update { it.copy(isCameraReady = false) }
    }

    fun setExposureMode(mode: ExposureMode) {
        val currActualIso = _uiState.value.actualIso.coerceAtLeast(50)
        val currActualShutter = _uiState.value.actualShutterSpeedNanos.coerceAtLeast(500_000L)

        _uiState.update { current ->
            current.copy(
                exposureMode = mode,
                iso = if (mode == ExposureMode.MANUAL || mode == ExposureMode.ISO_PRIORITY || current.iso <= 50) currActualIso else current.iso,
                shutterSpeedNanos = if (mode == ExposureMode.MANUAL || mode == ExposureMode.SHUTTER_PRIORITY || current.shutterSpeedNanos <= 500_000L) currActualShutter else current.shutterSpeedNanos
            )
        }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedExposureMode = mode.name
        applyCurrentSettings()
    }

    fun setIso(iso: Int) {
        _uiState.update { it.copy(iso = iso) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedIso = iso
        applyCurrentSettings()
    }

    fun setShutterSpeedNanos(nanos: Long) {
        _uiState.update { it.copy(shutterSpeedNanos = nanos) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedShutterNanos = nanos
        applyCurrentSettings()
    }

    fun setFocusDistance(diopters: Float) {
        _uiState.update { it.copy(focusDistanceDiopters = diopters, isManualFocusEnabled = true) }
        if (_uiState.value.isResumeSettingsEnabled) {
            settingsRepo.savedFocusDiopters = diopters
            settingsRepo.savedIsManualFocus = true
        }
        applyCurrentSettings()
    }

    fun toggleAutoFocus() {
        val manual = !_uiState.value.isManualFocusEnabled
        _uiState.update { it.copy(isManualFocusEnabled = manual) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedIsManualFocus = manual
        applyCurrentSettings()
    }

    fun setEvCompensation(evIndex: Int) {
        _uiState.update { it.copy(evCompensationIndex = evIndex) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedEvIndex = evIndex
        applyCurrentSettings()
    }

    fun setWhiteBalancePreset(preset: WhiteBalancePreset) {
        _uiState.update { it.copy(whiteBalancePreset = preset) }
        applyCurrentSettings()
    }

    fun setKelvin(kelvin: Int) {
        _uiState.update { it.copy(kelvinTemperature = kelvin, whiteBalancePreset = WhiteBalancePreset.CUSTOM_KELVIN) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedKelvin = kelvin
        applyCurrentSettings()
    }

    fun toggleRaw() {
        val next = !_uiState.value.captureRaw
        _uiState.update { it.copy(captureRaw = next) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedRawEnabled = next
    }

    fun takePicture() {
        if (_uiState.value.isCapturing) return

        val timer = _uiState.value.timerMode.seconds
        if (timer > 0) {
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                for (s in timer downTo 1) {
                    _uiState.update { it.copy(timerCountdownSeconds = s) }
                    delay(1000)
                }
                _uiState.update { it.copy(timerCountdownSeconds = null, isCapturing = true) }
                cameraController.takePicture(captureRaw = _uiState.value.captureRaw)
            }
        } else {
            _uiState.update { it.copy(isCapturing = true) }
            cameraController.takePicture(captureRaw = _uiState.value.captureRaw)
        }
    }

    private fun persistCurrentSettings() {
        val s = _uiState.value
        settingsRepo.savedCameraId = s.currentCameraId
        settingsRepo.savedExposureMode = s.exposureMode.name
        settingsRepo.savedIso = s.iso
        settingsRepo.savedShutterNanos = s.shutterSpeedNanos
        settingsRepo.savedFocusDiopters = s.focusDistanceDiopters
        settingsRepo.savedIsManualFocus = s.isManualFocusEnabled
        settingsRepo.savedEvIndex = s.evCompensationIndex
        settingsRepo.savedKelvin = s.kelvinTemperature
        settingsRepo.savedRawEnabled = s.captureRaw
        settingsRepo.savedNoiseReduction = s.noiseReductionMode.name
        settingsRepo.savedSharpness = s.sharpnessMode.name
    }

    private fun applyCurrentSettings() {
        val s = _uiState.value
        cameraController.updateManualSettings(
            exposureMode = s.exposureMode,
            iso = s.iso,
            shutterNanos = s.shutterSpeedNanos,
            focusDistance = s.focusDistanceDiopters,
            isManualFocus = s.isManualFocusEnabled,
            evIndex = s.evCompensationIndex,
            whiteBalancePreset = s.whiteBalancePreset,
            kelvin = s.kelvinTemperature,
            zoomRatio = s.currentZoomRatio,
            flashMode = s.flashMode,
            noiseReduction = s.noiseReductionMode,
            sharpness = s.sharpnessMode
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopSensors()
        cameraController.closeCamera()
        cameraController.stopBackgroundThread()
    }
}
