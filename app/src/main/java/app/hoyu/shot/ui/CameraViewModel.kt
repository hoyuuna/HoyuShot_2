package app.hoyu.shot.ui

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hoyu.shot.camera.Camera2Controller
import app.hoyu.shot.camera.CameraHardwareInspector
import app.hoyu.shot.model.*
import app.hoyu.shot.sensors.HorizonSensorManager
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
        onPhotoSaved = { uriString ->
            _uiState.update { it.copy(isCapturing = false, lastCapturedUri = uriString) }
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

        // Discover all cameras (Wide, Ultra-wide, Telephoto, Front)
        val allCameras = inspector.inspectAllAvailableCameras()
        val defaultCam = allCameras.firstOrNull { it.isBack } ?: allCameras.firstOrNull()

        _uiState.update {
            it.copy(
                availableCameras = allCameras,
                currentCameraId = defaultCam?.cameraId ?: "0",
                currentLensLabel = defaultCam?.label ?: "1x",
                capabilities = defaultCam?.capabilities,
                iso = defaultCam?.capabilities?.isoRange?.lower ?: 100,
                shutterSpeedNanos = defaultCam?.capabilities?.shutterSpeedRangeNanos?.lower ?: 10_000_000L
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

    fun switchCamera(newCameraId: String) {
        val surface = activeSurface ?: return
        if (newCameraId == _uiState.value.currentCameraId) return

        val camInfo = _uiState.value.availableCameras.find { it.cameraId == newCameraId } ?: return
        val ratio = _uiState.value.aspectRatio

        _uiState.update {
            it.copy(
                currentCameraId = newCameraId,
                currentLensLabel = camInfo.label,
                capabilities = camInfo.capabilities,
                // Adjust ranges if needed
                iso = it.iso.coerceIn(
                    camInfo.capabilities.isoRange?.lower ?: 100,
                    camInfo.capabilities.isoRange?.upper ?: 3200
                )
            )
        }

        cameraController.switchCamera(newCameraId, surface, ratio) { width, height ->
            _uiState.update { it.copy(previewSize = Pair(width, height)) }
        }
        applyCurrentSettings()
    }

    fun toggleCameraFacing() {
        val currentIsBack = _uiState.value.capabilities?.isFacingBack ?: true
        val targetCam = _uiState.value.availableCameras.firstOrNull { it.isBack != currentIsBack }
        if (targetCam != null) {
            switchCamera(targetCam.cameraId)
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
        _uiState.update { it.copy(isCapturing = true) }
        cameraController.takePicture(captureRaw = _uiState.value.captureRaw)
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
            kelvin = s.kelvinTemperature
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopSensors()
        cameraController.closeCamera()
        cameraController.stopBackgroundThread()
    }
}
