package app.hoyu.shot.ui

import android.app.Application
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hoyu.shot.camera.Camera2Controller
import app.hoyu.shot.camera.CameraHardwareInspector
import app.hoyu.shot.model.*
import app.hoyu.shot.sensors.HorizonSensorManager
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
    private val inspector = CameraHardwareInspector(application)

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
            _uiState.update {
                it.copy(
                    isCapturing = false,
                    lastCapturedPhoto = info
                )
            }
        },
        onError = { err ->
            _uiState.update { it.copy(errorMessage = err, isCapturing = false) }
        }
    )

    init {
        // Collect horizon tilt angle in real time
        viewModelScope.launch {
            horizonSensor.orientationFlow.collect { orientation ->
                _uiState.update { it.copy(horizon = orientation) }
            }
        }

        // Discover all physical and logical lenses (0.6x, 1x, 3.2x, Front)
        val allLenses = inspector.discoverAllLenses()
        val defaultLens = allLenses.find { !it.isFront && it.label == "1x" }
            ?: allLenses.firstOrNull { !it.isFront }
            ?: allLenses.firstOrNull()

        val initialCameraId = defaultLens?.cameraId ?: "0"
        val caps = inspector.inspectCamera(initialCameraId)

        val minZoom = allLenses.filter { !it.isFront }.minOfOrNull { it.zoomRatio } ?: 0.6f
        val maxZoom = 30.0f

        _uiState.update {
            it.copy(
                availableLenses = allLenses,
                currentLens = defaultLens,
                currentCameraId = initialCameraId,
                currentZoomRatio = defaultLens?.zoomRatio ?: 1.0f,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                capabilities = caps,
                iso = caps?.isoRange?.lower ?: 100,
                shutterSpeedNanos = caps?.shutterSpeedRangeNanos?.lower ?: 10_000_000L
            )
        }
    }

    fun startSensors() {
        horizonSensor.start()
    }

    fun stopSensors() {
        horizonSensor.stop()
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
        if (_uiState.value.lastCapturedPhoto != null) {
            _uiState.update { it.copy(isReviewDialogVisible = true) }
        }
    }

    fun closeReviewDialog() {
        _uiState.update { it.copy(isReviewDialogVisible = false) }
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
        _uiState.update { it.copy(exposureMode = mode) }
        applyCurrentSettings()
    }

    fun setIso(iso: Int) {
        _uiState.update { it.copy(iso = iso) }
        applyCurrentSettings()
    }

    fun setShutterSpeedNanos(nanos: Long) {
        _uiState.update { it.copy(shutterSpeedNanos = nanos) }
        applyCurrentSettings()
    }

    fun setFocusDistance(diopters: Float) {
        _uiState.update { it.copy(focusDistanceDiopters = diopters, isManualFocusEnabled = true) }
        applyCurrentSettings()
    }

    fun toggleAutoFocus() {
        _uiState.update { it.copy(isManualFocusEnabled = !it.isManualFocusEnabled) }
        applyCurrentSettings()
    }

    fun setEvCompensation(evIndex: Int) {
        _uiState.update { it.copy(evCompensationIndex = evIndex) }
        applyCurrentSettings()
    }

    fun setWhiteBalancePreset(preset: WhiteBalancePreset) {
        _uiState.update { it.copy(whiteBalancePreset = preset) }
        applyCurrentSettings()
    }

    fun setKelvin(kelvin: Int) {
        _uiState.update { it.copy(kelvinTemperature = kelvin, whiteBalancePreset = WhiteBalancePreset.CUSTOM_KELVIN) }
        applyCurrentSettings()
    }

    fun toggleRaw() {
        _uiState.update { it.copy(captureRaw = !it.captureRaw) }
    }

    fun setActiveDial(param: DialParameter?) {
        _uiState.update { it.copy(activeDialParameter = if (it.activeDialParameter == param) null else param) }
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
            flashMode = s.flashMode
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
