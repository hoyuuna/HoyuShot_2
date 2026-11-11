package app.hoyu.shot.ui

import android.app.Application
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hoyu.shot.camera.Camera2Controller
import app.hoyu.shot.camera.CameraHardwareInspector
import app.hoyu.shot.data.SettingsRepository
import app.hoyu.shot.model.*
import app.hoyu.shot.sensors.DeviceOrientationListener
import app.hoyu.shot.sensors.HorizonSensorManager
import app.hoyu.shot.sensors.SunCalculator
import app.hoyu.shot.sensors.SunCompassManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _openUpdateSettingsEvent = MutableStateFlow(false)
    val openUpdateSettingsEvent: StateFlow<Boolean> = _openUpdateSettingsEvent.asStateFlow()

    fun triggerOpenUpdateSettings() {
        _openUpdateSettingsEvent.value = true
    }

    fun consumeOpenUpdateSettings() {
        _openUpdateSettingsEvent.value = false
    }

    private val horizonSensor = HorizonSensorManager(application)
    private val orientationListener = DeviceOrientationListener(application)
    private val sunCompassManager = SunCompassManager(application)
    private val inspector = CameraHardwareInspector(application)
    private val settingsRepo = SettingsRepository(application)

    private var activeSurface: Surface? = null
    private var timerJob: Job? = null
    private var sunRefreshJob: Job? = null
    private var lastSunLocation: Location? = null
    private var lastAttemptedLens: LensItem? = null
    private val failedPhysicalCameraIds = mutableSetOf<String>()

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
                    lastCapturedPhoto = info,
                    recentPhotos = updatedList
                )
            }
        },
        onError = { err ->
            _uiState.update { it.copy(errorMessage = err, isCapturing = false, isLongExposing = false) }
            val currentId = _uiState.value.currentCameraId
            if (currentId != "0") {
                failedPhysicalCameraIds.add(currentId)
                viewModelScope.launch {
                    val fallbackLens = _uiState.value.availableLenses.find { it.cameraId == "0" && !it.isFront }
                        ?: _uiState.value.availableLenses.firstOrNull { !it.isFront }
                    if (fallbackLens != null) {
                        settingsRepo.savedCameraId = "0"
                        val attempted = lastAttemptedLens
                        if (attempted != null && canUseZoomFallback(attempted)) {
                            activateSimulatedLens(attempted)
                        } else {
                            selectLens(fallbackLens)
                        }
                    }
                }
            }
        }
    )

    init {
        loadRecentPhotos()

        cameraController.onExposureCompleted = {
            viewModelScope.launch {
                _uiState.update { it.copy(isCapturing = false) }
            }
        }

        cameraController.onLongExposureStateChanged = { isLong ->
            viewModelScope.launch {
                _uiState.update { it.copy(isLongExposing = isLong) }
            }
        }

        cameraController.onCaptureError = { msg ->
            viewModelScope.launch {
                _uiState.update { it.copy(errorMessage = msg, isCapturing = false, isLongExposing = false) }
            }
        }

        cameraController.onOverlayDataReady = { overlay ->
            _uiState.update { it.copy(overlayAnalysis = overlay) }
        }

        cameraController.onBlurredBackdropReady = { bmp ->
            _uiState.update { it.copy(blurredBackdrop = bmp) }
        }

        // Collect horizon tilt angle in real time
        viewModelScope.launch {
            horizonSensor.orientationFlow.collect { orientation ->
                _uiState.update { it.copy(horizon = orientation) }
            }
        }

        // Collect device orientation to rotate buttons and icons in-place
        viewModelScope.launch {
            orientationListener.rotationDegrees.collect { deg ->
                horizonSensor.currentUiRotationDegrees = deg
                sunCompassManager.currentUiRotationDegrees = deg
                cameraController.deviceOrientationDegrees = deg.toInt()
                _uiState.update { it.copy(uiRotationDegrees = deg) }
            }
        }

        // Collect live compass heading for Sun Compass HUD
        viewModelScope.launch {
            sunCompassManager.headingDegrees.collect { heading ->
                if (_uiState.value.isSunCompassEnabled) {
                    _uiState.update { current ->
                        val s = current.sunCompassState
                        val rel = (((s.sunAzimuthDeg - heading) + 540.0) % 360.0 - 180.0).toFloat()
                        current.copy(
                            sunCompassState = s.copy(
                                cameraHeadingDeg = heading,
                                relativeAngleDeg = rel
                            )
                        )
                    }
                }
            }
        }

        // Discover all physical and logical lenses (0.6x, 1x, 3.2x, Front)
        val allLenses = inspector.discoverAllLenses()
        val isResume = settingsRepo.isResumeSettingsEnabled

        val targetCamId = if (isResume && allLenses.any { it.cameraId == settingsRepo.savedCameraId }) settingsRepo.savedCameraId else "0"
        val defaultLens = allLenses.find { it.cameraId == targetCamId && !it.isFront }
            ?: allLenses.find { !it.isFront && it.label == "1x" }
            ?: allLenses.firstOrNull { !it.isFront }
            ?: allLenses.firstOrNull()

        val initialCameraId = defaultLens?.cameraId ?: "0"
        val caps = inspector.inspectCamera(initialCameraId)

        val initRatio = try { AspectRatio.valueOf(settingsRepo.savedAspectRatio) } catch (e: Exception) { AspectRatio.RATIO_4_3 }
        val initResolutions = computeAvailableResolutions(caps, initRatio)
        val defaultRes = initResolutions.firstOrNull()
        if (defaultRes != null) {
            cameraController.targetResolution = Pair(defaultRes.width, defaultRes.height)
        }

        val minZoom = allLenses.filter { !it.isFront }.minOfOrNull { it.zoomRatio } ?: 0.6f
        val maxZoom = 30.0f

        val initMode = if (isResume) {
            try { ExposureMode.valueOf(settingsRepo.savedExposureMode) } catch (e: Exception) { ExposureMode.AUTO }
        } else ExposureMode.AUTO

        val initIso = if (isResume) {
            val saved = settingsRepo.savedIso
            caps?.isoRange?.let { saved.coerceIn(it.lower, it.upper) } ?: saved
        } else (caps?.isoRange?.lower ?: 100)

        val initShutter = if (isResume) {
            val saved = settingsRepo.savedShutterNanos
            caps?.shutterSpeedRangeNanos?.let { saved.coerceIn(it.lower, it.upper) } ?: saved
        } else (caps?.shutterSpeedRangeNanos?.lower ?: 10_000_000L)

        val initFocus = if (isResume && caps != null && caps.supportsManualFocus) {
            settingsRepo.savedFocusDiopters.coerceIn(0f, caps.minFocusDistanceDiopters)
        } else 0f
        val initIsManualFocus = if (isResume && caps != null && caps.supportsManualFocus) {
            settingsRepo.savedIsManualFocus
        } else false
        val initEv = if (isResume) {
            val saved = settingsRepo.savedEvIndex
            caps?.evCompensationRange?.let { saved.coerceIn(it.lower, it.upper) } ?: saved
        } else 0
        val initKelvin = if (isResume) settingsRepo.savedKelvin else 5500
        val lensSupportsRaw = caps?.supportsRawCapture == true && caps.supportedRawSizes.isNotEmpty()
        val parsedFormat = try {
            CaptureFormat.valueOf(settingsRepo.savedCaptureFormat)
        } catch (e: Exception) {
            if (settingsRepo.savedRawEnabled) CaptureFormat.RAW_JPEG else CaptureFormat.JPEG
        }
        val initFormat = if (isResume && lensSupportsRaw) parsedFormat else CaptureFormat.JPEG
        val initRaw = initFormat == CaptureFormat.RAW || initFormat == CaptureFormat.RAW_JPEG

        val initMinShutter = settingsRepo.savedMinShutterLimitNanos?.let { saved ->
            caps?.shutterSpeedRangeNanos?.let { saved.coerceIn(it.lower, it.upper) } ?: saved
        }
        val initMaxIso = settingsRepo.savedMaxIsoLimit?.let { saved ->
            caps?.isoRange?.let { saved.coerceIn(it.lower, it.upper) } ?: saved
        }

        val initNr = if (isResume) {
            try { NoiseReductionMode.valueOf(settingsRepo.savedNoiseReduction) } catch (e: Exception) { NoiseReductionMode.FAST }
        } else NoiseReductionMode.FAST

        val initSharp = if (isResume) {
            try { SharpnessMode.valueOf(settingsRepo.savedSharpness) } catch (e: Exception) { SharpnessMode.NORMAL }
        } else SharpnessMode.NORMAL

        val initGrid = try { GridMode.valueOf(settingsRepo.savedGridMode) } catch (e: Exception) { GridMode.NONE }
        val initHist = settingsRepo.savedHistogramVisible
        val initHorizon = settingsRepo.savedHorizonVisible
        val initPeaking = settingsRepo.savedPeakingEnabled
        val initZebra = settingsRepo.savedZebraEnabled
        val hasLocPerm = cameraController.hasLocationPermission()
        val initGeo = settingsRepo.isGeotaggingEnabled && hasLocPerm
        if (settingsRepo.isGeotaggingEnabled && !hasLocPerm) {
            settingsRepo.isGeotaggingEnabled = false
        }
        val initSunCompass = settingsRepo.savedSunCompassEnabled && hasLocPerm
        if (settingsRepo.savedSunCompassEnabled && !hasLocPerm) {
            settingsRepo.savedSunCompassEnabled = false
        }
        sunCompassManager.isFrontCamera = defaultLens?.isFront == true
        cameraController.isGeotaggingEnabled = initGeo
        cameraController.isSunCompassLocationNeeded = initSunCompass
        cameraController.setOnLocationUpdated { loc ->
            updateSunLocation(loc)
        }
        cameraController.captureFormat = initFormat
        cameraController.isPeakingOverlayEnabled = initPeaking
        cameraController.isZebraOverlayEnabled = initZebra
        cameraController.isHistogramOverlayEnabled = initHist
        cameraController.jpegQuality = settingsRepo.jpegQuality
        cameraController.isMirrorSelfie = settingsRepo.isMirrorSelfie
        cameraController.antibandingMode = settingsRepo.antibandingMode
        cameraController.cameraSoundMode = settingsRepo.cameraSoundMode
        cameraController.isExifSoftwareTagEnabled = settingsRepo.isExifSoftwareTagEnabled
        cameraController.isHqViewportEnabled = settingsRepo.isHqViewportEnabled

        _uiState.update {
            it.copy(
                availableLenses = allLenses,
                currentLens = defaultLens,
                currentCameraId = initialCameraId,
                currentZoomRatio = defaultLens?.zoomRatio ?: 1.0f,
                digitalZoomRatio = 1.0f,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                capabilities = caps,
                aspectRatio = initRatio,
                exposureMode = initMode,
                iso = initIso,
                shutterSpeedNanos = initShutter,
                minShutterLimitNanos = initMinShutter,
                maxIsoLimit = initMaxIso,
                focusDistanceDiopters = initFocus,
                isManualFocusEnabled = initIsManualFocus,
                evCompensationIndex = initEv,
                kelvinTemperature = initKelvin,
                captureFormat = initFormat,
                captureRaw = initRaw,
                availableResolutions = initResolutions,
                selectedResolution = defaultRes,
                selectedMegapixels = defaultRes?.mpLabel ?: "12 MP",
                isResumeSettingsEnabled = isResume,
                noiseReductionMode = initNr,
                sharpnessMode = initSharp,
                gridMode = initGrid,
                isHistogramVisible = initHist,
                isHorizonLevelVisible = initHorizon,
                isFocusPeakingEnabled = initPeaking,
                isZebraStripesEnabled = initZebra,
                isGeotaggingEnabled = initGeo,
                isSunCompassEnabled = initSunCompass,
                jpegQuality = settingsRepo.jpegQuality,
                isMirrorSelfie = settingsRepo.isMirrorSelfie,
                cameraSoundMode = settingsRepo.cameraSoundMode,
                uiHapticsMode = settingsRepo.uiHapticsMode,
                tapAction = settingsRepo.tapAction,
                holdAction = settingsRepo.holdAction,
                volumeUpAction = settingsRepo.volumeUpAction,
                volumeDownAction = settingsRepo.volumeDownAction,
                antibandingMode = settingsRepo.antibandingMode,
                isUiBlurEnabled = settingsRepo.isUiBlurEnabled,
                isAutoUpdateCheckEnabled = settingsRepo.isAutoUpdateCheckEnabled,
                isPreReleaseUpdateEnabled = settingsRepo.isPreReleaseUpdateEnabled,
                isTeleZoomFallbackEnabled = settingsRepo.isTeleZoomFallbackEnabled,
                isExifSoftwareTagEnabled = settingsRepo.isExifSoftwareTagEnabled,
                isHqViewportEnabled = settingsRepo.isHqViewportEnabled
            )
        }

        // Background auto-check for updates on app boot
        if (settingsRepo.isAutoUpdateCheckEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(1500)
                try {
                    val updateManager = app.hoyu.shot.updater.UpdateManager(application)
                    val result = updateManager.checkForUpdates(includePreRelease = settingsRepo.isPreReleaseUpdateEnabled)
                    if (result is app.hoyu.shot.updater.UpdateCheckResult.UpdateAvailable) {
                        updateManager.showUpdateNotification(result.info)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun startSensors() {
        horizonSensor.start()
        orientationListener.start()
        val needGeo = _uiState.value.isGeotaggingEnabled
        val needSun = _uiState.value.isSunCompassEnabled
        if (needGeo || needSun) {
            if (cameraController.hasLocationPermission()) {
                cameraController.resumeLocation()
                if (needSun) {
                    sunCompassManager.start()
                    updateSunLocation(cameraController.getBestLocation())
                    startSunRefreshLoop()
                }
            } else {
                if (needGeo) setGeotaggingEnabled(false)
                if (needSun) setSunCompassEnabled(false)
            }
        }
    }

    fun stopSensors() {
        horizonSensor.stop()
        orientationListener.stop()
        sunCompassManager.stop()
        sunRefreshJob?.cancel()
        cameraController.pauseLocation()
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

        // If user taps on or near the current focus ring, CANCEL focus and return to Continuous AF
        val existingPoint = _uiState.value.tapFocusPoint
        if (existingPoint != null) {
            val dx = offset.x - existingPoint.offset.x
            val dy = offset.y - existingPoint.offset.y
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < 110f) {
                cancelTouchFocus()
                return
            }
        }

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

    fun cancelTouchFocus() {
        _uiState.update { it.copy(tapFocusPoint = null, isAfLocked = false) }
        cameraController.resetAutoFocus()
    }

    fun onTouchAe(offset: Offset, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val existingAe = _uiState.value.tapAePoint
        if (existingAe != null) {
            val dx = offset.x - existingAe.x
            val dy = offset.y - existingAe.y
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < 110f) {
                cancelTouchAe()
                return
            }
        }

        val normX = (offset.x / viewWidth).coerceIn(0f, 1f)
        val normY = (offset.y / viewHeight).coerceIn(0f, 1f)

        _uiState.update {
            it.copy(tapAePoint = offset, isSpotAeActive = true)
        }
        cameraController.triggerSpotAe(normX, normY)
    }

    fun cancelTouchAe() {
        _uiState.update { it.copy(tapAePoint = null, isSpotAeActive = false) }
        cameraController.triggerSpotAe(null, null)
    }

    fun handleScreenTap(offset: Offset, viewWidth: Int, viewHeight: Int) {
        when (_uiState.value.tapAction.uppercase()) {
            "FOCUS" -> onTouchFocus(offset, viewWidth, viewHeight)
            "AE" -> onTouchAe(offset, viewWidth, viewHeight)
            "SHUTTER" -> takePicture()
            else -> {}
        }
    }

    fun handleScreenHold(offset: Offset, viewWidth: Int, viewHeight: Int) {
        when (_uiState.value.holdAction.uppercase()) {
            "AE" -> onTouchAe(offset, viewWidth, viewHeight)
            "FOCUS" -> onTouchFocus(offset, viewWidth, viewHeight)
            "SHUTTER" -> takePicture()
            else -> {}
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

    fun setTimerMode(mode: TimerMode) {
        timerJob?.cancel()
        _uiState.update { it.copy(timerMode = mode, timerCountdownSeconds = null) }
    }

    fun toggleTimer() {
        val next = when (_uiState.value.timerMode) {
            TimerMode.OFF -> TimerMode.SEC_1
            TimerMode.SEC_1 -> TimerMode.SEC_2
            TimerMode.SEC_2 -> TimerMode.SEC_5
            TimerMode.SEC_5 -> TimerMode.SEC_10
            TimerMode.SEC_10 -> TimerMode.OFF
        }
        setTimerMode(next)
    }

    fun toggleGrid() {
        val next = when (_uiState.value.gridMode) {
            GridMode.NONE -> GridMode.THIRDS
            GridMode.THIRDS -> GridMode.GOLDEN
            GridMode.GOLDEN -> GridMode.NONE
        }
        _uiState.update { it.copy(gridMode = next) }
        settingsRepo.savedGridMode = next.name
    }

    fun toggleHistogram() {
        val next = !_uiState.value.isHistogramVisible
        _uiState.update { it.copy(isHistogramVisible = next) }
        cameraController.isHistogramOverlayEnabled = next
        cameraController.refreshAnalysisStream()
        settingsRepo.savedHistogramVisible = next
    }

    fun toggleHorizon() {
        val next = !_uiState.value.isHorizonLevelVisible
        _uiState.update { it.copy(isHorizonLevelVisible = next) }
        settingsRepo.savedHorizonVisible = next
    }

    fun toggleFocusPeaking() {
        val next = !_uiState.value.isFocusPeakingEnabled
        _uiState.update { it.copy(isFocusPeakingEnabled = next) }
        cameraController.isPeakingOverlayEnabled = next
        cameraController.refreshAnalysisStream()
        settingsRepo.savedPeakingEnabled = next
    }

    fun toggleZebraStripes() {
        val next = !_uiState.value.isZebraStripesEnabled
        _uiState.update { it.copy(isZebraStripesEnabled = next) }
        cameraController.isZebraOverlayEnabled = next
        cameraController.refreshAnalysisStream()
        settingsRepo.savedZebraEnabled = next
    }

    fun setPopupBackdropBlurActive(active: Boolean) {
        val changed = cameraController.isBlurBackdropNeeded != active
        cameraController.isBlurBackdropNeeded = active
        if (changed) {
            cameraController.refreshAnalysisStream()
        }
        if (!active && _uiState.value.blurredBackdrop != null) {
            _uiState.update { it.copy(blurredBackdrop = null) }
        }
    }

    fun hasLocationPermission(): Boolean = cameraController.hasLocationPermission()

    fun setGeotaggingEnabled(enabled: Boolean) {
        val actual = if (enabled) cameraController.hasLocationPermission() else false
        _uiState.update { it.copy(isGeotaggingEnabled = actual) }
        settingsRepo.isGeotaggingEnabled = actual
        cameraController.isGeotaggingEnabled = actual
    }

    fun toggleGeotagging() {
        val next = !_uiState.value.isGeotaggingEnabled
        setGeotaggingEnabled(next)
    }

    fun setSunCompassEnabled(enabled: Boolean) {
        val actual = if (enabled) cameraController.hasLocationPermission() else false
        _uiState.update { it.copy(isSunCompassEnabled = actual) }
        settingsRepo.savedSunCompassEnabled = actual
        cameraController.isSunCompassLocationNeeded = actual
        if (actual) {
            sunCompassManager.isFrontCamera = _uiState.value.currentLens?.isFront == true
            sunCompassManager.start()
            updateSunLocation(cameraController.getBestLocation())
            startSunRefreshLoop()
        } else {
            sunCompassManager.stop()
            sunRefreshJob?.cancel()
        }
    }

    fun toggleSunCompass() {
        val next = !_uiState.value.isSunCompassEnabled
        setSunCompassEnabled(next)
    }

    private fun updateSunLocation(location: Location?) {
        if (location != null) {
            lastSunLocation = location
            sunCompassManager.updateLocation(location)
        }
        if (_uiState.value.isSunCompassEnabled) {
            recalculateSunPosition()
        }
    }

    private fun startSunRefreshLoop() {
        sunRefreshJob?.cancel()
        sunRefreshJob = viewModelScope.launch {
            while (isActive && _uiState.value.isSunCompassEnabled) {
                recalculateSunPosition()
                delay(300_000L) // Refresh solar ephemeris every 5 minutes (100% offline math)
            }
        }
    }

    private fun recalculateSunPosition() {
        val loc = lastSunLocation ?: cameraController.getBestLocation() ?: return
        lastSunLocation = loc
        val pos = SunCalculator.calculate(System.currentTimeMillis(), loc.latitude, loc.longitude)
        val heading = sunCompassManager.headingDegrees.value
        val rel = (((pos.azimuthDeg - heading) + 540.0) % 360.0 - 180.0).toFloat()
        val isDay = pos.altitudeDeg > -0.833
        val isOverhead = pos.altitudeDeg >= 72.0
        _uiState.update {
            it.copy(
                sunCompassState = SunCompassState(
                    hasLocation = true,
                    sunAzimuthDeg = pos.azimuthDeg,
                    sunAltitudeDeg = pos.altitudeDeg,
                    cameraHeadingDeg = heading,
                    relativeAngleDeg = rel,
                    isDaylight = isDay,
                    isOverhead = isOverhead
                )
            )
        }
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
        timerJob?.cancel()
        if (_uiState.value.recentPhotos.isNotEmpty() || _uiState.value.lastCapturedPhoto != null) {
            _uiState.update { it.copy(isGalleryVisible = true, timerCountdownSeconds = null) }
        } else if (_uiState.value.timerCountdownSeconds != null) {
            _uiState.update { it.copy(timerCountdownSeconds = null) }
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
                    val resolver = getApplication<Application>().contentResolver
                    while (c.moveToNext() && count < 25) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "IMG.jpg"
                        val w = c.getInt(widthCol)
                        val h = c.getInt(heightCol)
                        val sz = c.getLong(sizeCol)
                        if (sz <= 0L) continue
                        val date = c.getLong(dateCol) * 1000L
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        var iso = 0
                        var shutterStr = ""
                        var apertureStr = ""
                        var focalStr = ""
                        var outW = w
                        var outH = h
                        try {
                            resolver.openInputStream(contentUri)?.use { stream ->
                                val exif = ExifInterface(stream)
                                val isoAttr = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                                    .takeIf { it > 0 }
                                    ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                                if (isoAttr > 0) iso = isoAttr

                                val expSec = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                                if (expSec > 0.0) {
                                    shutterStr = HardwareCapabilities.formatShutterSpeed((expSec * 1_000_000_000.0).toLong())
                                }

                                val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                                if (fNum > 0.0) {
                                    apertureStr = String.format(java.util.Locale.US, "f/%.1f", fNum)
                                }

                                val focalMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                                if (focalMm > 0.0) {
                                    focalStr = String.format(java.util.Locale.US, "%.1fmm", focalMm)
                                }

                                val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                                if ((orient == ExifInterface.ORIENTATION_ROTATE_90 || orient == ExifInterface.ORIENTATION_ROTATE_270) &&
                                    outW > outH
                                ) {
                                    val tmp = outW
                                    outW = outH
                                    outH = tmp
                                }
                            }
                        } catch (_: Exception) {
                        }

                        list.add(
                            CapturedPhotoInfo(
                                uriString = contentUri.toString(),
                                isRaw = name.endsWith(".dng", ignoreCase = true),
                                width = if (outW > 0) outW else 4000,
                                height = if (outH > 0) outH else 3000,
                                iso = if (iso > 0) iso else 100,
                                shutterSpeed = shutterStr.ifEmpty { "AUTO" },
                                focalLength = focalStr.ifEmpty { "--" },
                                timestamp = date,
                                fileName = name,
                                fileSizeBytes = sz,
                                aperture = apertureStr.ifEmpty { "f/1.8" }
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
        val caps = _uiState.value.capabilities
        val supportsRaw = caps?.supportsRawCapture == true && caps.supportedRawSizes.isNotEmpty()
        val effectiveFormat = if (!supportsRaw && format != CaptureFormat.JPEG) {
            _uiState.update { it.copy(errorMessage = "Current lens does not support RAW (DNG) capture") }
            CaptureFormat.JPEG
        } else {
            format
        }
        val willCaptureRaw = effectiveFormat == CaptureFormat.RAW || effectiveFormat == CaptureFormat.RAW_JPEG
        _uiState.update {
            it.copy(
                captureFormat = effectiveFormat,
                captureRaw = willCaptureRaw
            )
        }
        cameraController.captureFormat = effectiveFormat
        if (_uiState.value.isResumeSettingsEnabled) {
            settingsRepo.savedCaptureFormat = effectiveFormat.name
            settingsRepo.savedRawEnabled = willCaptureRaw
        }
    }

    fun setResolution(res: CameraResolution) {
        _uiState.update {
            it.copy(
                selectedResolution = res,
                selectedMegapixels = res.mpLabel
            )
        }
        cameraController.targetResolution = Pair(res.width, res.height)

        val surface = activeSurface
        if (surface != null) {
            cameraController.switchCamera(_uiState.value.currentCameraId, surface, _uiState.value.aspectRatio) { w, h ->
                _uiState.update { it.copy(previewSize = Pair(w, h)) }
            }
        }
    }

    fun setMegapixels(mp: String) {
        val matching = _uiState.value.availableResolutions.find { it.mpLabel == mp }
        if (matching != null) {
            setResolution(matching)
        } else {
            _uiState.update { it.copy(selectedMegapixels = mp) }
        }
    }

    fun selectLens(lens: LensItem) {
        val surface = activeSurface ?: return
        val currentCamId = _uiState.value.currentCameraId
        lastAttemptedLens = lens

        // If this physical camera previously failed on this device, directly use the simulated fallback as last resort
        if (failedPhysicalCameraIds.contains(lens.cameraId) && canUseZoomFallback(lens)) {
            activateSimulatedLens(lens)
            return
        }

        _uiState.update {
            it.copy(
                currentLens = lens,
                currentZoomRatio = lens.zoomRatio,
                digitalZoomRatio = 1.0f,
                simulatedTeleBaseZoom = 1.0f
            )
        }
        sunCompassManager.isFrontCamera = lens.isFront
        cameraController.currentZoomRatio = 1.0f

        if (lens.cameraId != currentCamId) {
            val caps = inspector.inspectCamera(lens.cameraId)
            val resList = computeAvailableResolutions(caps, _uiState.value.aspectRatio)
            val topRes = resList.firstOrNull()
            if (topRes != null) {
                cameraController.targetResolution = Pair(topRes.width, topRes.height)
            }

            // Strictly clamp parameters to the new sensor's physical hardware limits
            val clampedIso = caps?.isoRange?.let { range -> _uiState.value.iso.coerceIn(range.lower, range.upper) } ?: _uiState.value.iso
            val clampedShutter = caps?.shutterSpeedRangeNanos?.let { range -> _uiState.value.shutterSpeedNanos.coerceIn(range.lower, range.upper) } ?: _uiState.value.shutterSpeedNanos
            val clampedFocus = if (caps != null && caps.supportsManualFocus) {
                _uiState.value.focusDistanceDiopters.coerceIn(0f, caps.minFocusDistanceDiopters)
            } else 0f
            val clampedIsManualFocus = if (caps != null && !caps.supportsManualFocus) false else _uiState.value.isManualFocusEnabled
            val clampedEv = caps?.evCompensationRange?.let { range -> _uiState.value.evCompensationIndex.coerceIn(range.lower, range.upper) } ?: _uiState.value.evCompensationIndex
            val supportsRaw = caps?.supportsRawCapture == true && caps.supportedRawSizes.isNotEmpty()
            val nextFormat = if (!supportsRaw && _uiState.value.captureFormat != CaptureFormat.JPEG) CaptureFormat.JPEG else _uiState.value.captureFormat
            val nextRaw = nextFormat == CaptureFormat.RAW || nextFormat == CaptureFormat.RAW_JPEG

            _uiState.update {
                it.copy(
                    currentCameraId = lens.cameraId,
                    capabilities = caps,
                    availableResolutions = resList,
                    selectedResolution = topRes,
                    selectedMegapixels = topRes?.mpLabel ?: it.selectedMegapixels,
                    iso = clampedIso,
                    shutterSpeedNanos = clampedShutter,
                    focusDistanceDiopters = clampedFocus,
                    isManualFocusEnabled = clampedIsManualFocus,
                    evCompensationIndex = clampedEv,
                    captureFormat = nextFormat,
                    captureRaw = nextRaw
                )
            }
            cameraController.captureFormat = nextFormat
            if (_uiState.value.isResumeSettingsEnabled) {
                settingsRepo.savedCameraId = lens.cameraId
                settingsRepo.savedIso = clampedIso
                settingsRepo.savedShutterNanos = clampedShutter
                settingsRepo.savedFocusDiopters = clampedFocus
                settingsRepo.savedIsManualFocus = clampedIsManualFocus
                settingsRepo.savedEvIndex = clampedEv
            }
            cameraController.switchCamera(lens.cameraId, surface, _uiState.value.aspectRatio) { width, height ->
                _uiState.update { it.copy(previewSize = Pair(width, height)) }
            }
        }

        applyCurrentSettings()
    }

    private fun canUseZoomFallback(lens: LensItem): Boolean {
        if (!settingsRepo.isTeleZoomFallbackEnabled) return false
        if (lens.isFront) return false
        if (lens.cameraId == "0") return false

        // For tele / standard / sensor crop lenses (zoom >= 1.0f):
        if (lens.zoomRatio >= 1.0f) return true

        // For ultrawide lenses (zoom < 1.0f, like 0.5x or 0.6x):
        // Camera "0" MUST support zooming out (e.g. Pixel logical camera with lower zoom range <= lens.zoomRatio + 0.05f)
        val cam0Caps = inspector.inspectCamera("0")
        val lowerBound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cam0Caps?.zoomRatioRange?.lower ?: 1.0f
        } else {
            1.0f
        }
        return lowerBound <= (lens.zoomRatio + 0.05f)
    }

    private fun activateSimulatedLens(lens: LensItem) {
        val surface = activeSurface ?: return
        // If tele (> 1.5x), add 0.1x (e.g. 5.1x for 5x, 3.3x for 3.2x) to guarantee crossing the Google HAL telephoto threshold
        // If ultrawide (< 1.0x, e.g. 0.6x or 0.5x) or 1x..1.5x, use exact lens.zoomRatio
        val targetSimulatedZoom = if (lens.zoomRatio > 1.5f) {
            lens.zoomRatio + 0.1f
        } else {
            lens.zoomRatio
        }
        val isAlreadyOnCam0 = _uiState.value.currentCameraId == "0"

        val caps = inspector.inspectCamera("0")
        val resList = computeAvailableResolutions(caps, _uiState.value.aspectRatio)
        val topRes = resList.firstOrNull()

        _uiState.update {
            it.copy(
                currentCameraId = "0",
                currentLens = lens,
                currentZoomRatio = lens.zoomRatio,
                digitalZoomRatio = 1.0f,
                simulatedTeleBaseZoom = targetSimulatedZoom,
                capabilities = caps,
                availableResolutions = resList,
                selectedResolution = topRes ?: it.selectedResolution
            )
        }
        cameraController.currentZoomRatio = targetSimulatedZoom

        if (!isAlreadyOnCam0) {
            cameraController.switchCamera("0", surface, _uiState.value.aspectRatio) { width, height ->
                _uiState.update { it.copy(previewSize = Pair(width, height)) }
            }
        }
        applyCurrentSettings()
    }

    fun onPinchZoom(zoomMultiplier: Float) {
        val currentDigital = _uiState.value.digitalZoomRatio
        val newDigital = (currentDigital * zoomMultiplier).coerceIn(1.0f, 10.0f)
        val baseZoom = _uiState.value.simulatedTeleBaseZoom
        _uiState.update { it.copy(digitalZoomRatio = newDigital) }
        val minHwZoom = if (baseZoom < 1.0f) baseZoom else 1.0f
        val maxHwZoom = _uiState.value.maxZoomRatio
        cameraController.currentZoomRatio = (baseZoom * newDigital).coerceIn(minHwZoom, maxHwZoom)
        applyCurrentSettings()
    }

    fun resetDigitalZoom() {
        val baseZoom = _uiState.value.simulatedTeleBaseZoom
        _uiState.update { it.copy(digitalZoomRatio = 1.0f) }
        cameraController.currentZoomRatio = baseZoom
        applyCurrentSettings()
    }

    fun stepZoom(factor: Float) {
        val currentDigital = _uiState.value.digitalZoomRatio
        val newDigital = (currentDigital * factor).coerceIn(1.0f, 10.0f)
        val baseZoom = _uiState.value.simulatedTeleBaseZoom
        _uiState.update { it.copy(digitalZoomRatio = newDigital) }
        val minHwZoom = if (baseZoom < 1.0f) baseZoom else 1.0f
        val maxHwZoom = _uiState.value.maxZoomRatio
        cameraController.currentZoomRatio = (baseZoom * newDigital).coerceIn(minHwZoom, maxHwZoom)
        applyCurrentSettings()
    }

    fun setZoomRatio(zoom: Float) {
        val clamped = zoom.coerceIn(_uiState.value.minZoomRatio, _uiState.value.maxZoomRatio)
        _uiState.update { it.copy(currentZoomRatio = clamped) }
        applyCurrentSettings()
    }

    fun handleVolumeKey(isUp: Boolean): Boolean {
        val action = if (isUp) _uiState.value.volumeUpAction else _uiState.value.volumeDownAction
        return when (action.uppercase()) {
            "SHUTTER" -> {
                takePicture()
                true
            }
            "AE_LOCK" -> {
                toggleAeLock()
                true
            }
            "EV" -> {
                val step = if (isUp) 1 else -1
                val newEv = _uiState.value.evCompensationIndex + step
                val minEv = _uiState.value.capabilities?.evCompensationRange?.lower ?: -6
                val maxEv = _uiState.value.capabilities?.evCompensationRange?.upper ?: 6
                setEvCompensation(newEv.coerceIn(minEv, maxEv))
                true
            }
            "ZOOM" -> {
                stepZoom(if (isUp) 1.25f else 0.8f)
                true
            }
            else -> false
        }
    }

    fun setJpegQuality(quality: Int) {
        _uiState.update { it.copy(jpegQuality = quality) }
        settingsRepo.jpegQuality = quality
        cameraController.jpegQuality = quality
    }

    fun toggleMirrorSelfie() {
        val next = !_uiState.value.isMirrorSelfie
        _uiState.update { it.copy(isMirrorSelfie = next) }
        settingsRepo.isMirrorSelfie = next
        cameraController.isMirrorSelfie = next
    }

    fun setAntibandingMode(mode: String) {
        _uiState.update { it.copy(antibandingMode = mode) }
        settingsRepo.antibandingMode = mode
        cameraController.antibandingMode = mode
        applyCurrentSettings()
    }

    fun setTapAction(action: String) {
        _uiState.update { it.copy(tapAction = action) }
        settingsRepo.tapAction = action
    }

    fun setHoldAction(action: String) {
        _uiState.update { it.copy(holdAction = action) }
        settingsRepo.holdAction = action
    }

    fun setVolumeUpAction(action: String) {
        _uiState.update { it.copy(volumeUpAction = action) }
        settingsRepo.volumeUpAction = action
    }

    fun setVolumeDownAction(action: String) {
        _uiState.update { it.copy(volumeDownAction = action) }
        settingsRepo.volumeDownAction = action
    }

    fun setCameraSoundMode(mode: String) {
        _uiState.update { it.copy(cameraSoundMode = mode) }
        settingsRepo.cameraSoundMode = mode
        cameraController.cameraSoundMode = mode
    }

    fun setUiHapticsMode(mode: String) {
        _uiState.update { it.copy(uiHapticsMode = mode) }
        settingsRepo.uiHapticsMode = mode
    }

    fun toggleUiBlur() {
        val next = !_uiState.value.isUiBlurEnabled
        _uiState.update { it.copy(isUiBlurEnabled = next) }
        settingsRepo.isUiBlurEnabled = next
    }

    fun toggleAutoUpdateCheck() {
        val next = !_uiState.value.isAutoUpdateCheckEnabled
        _uiState.update { it.copy(isAutoUpdateCheckEnabled = next) }
        settingsRepo.isAutoUpdateCheckEnabled = next
    }

    fun togglePreReleaseUpdate() {
        val next = !_uiState.value.isPreReleaseUpdateEnabled
        _uiState.update { it.copy(isPreReleaseUpdateEnabled = next) }
        settingsRepo.isPreReleaseUpdateEnabled = next
    }

    fun toggleTeleZoomFallback() {
        val next = !_uiState.value.isTeleZoomFallbackEnabled
        _uiState.update { it.copy(isTeleZoomFallbackEnabled = next) }
        settingsRepo.isTeleZoomFallbackEnabled = next
    }

    fun toggleExifSoftwareTag() {
        val next = !_uiState.value.isExifSoftwareTagEnabled
        _uiState.update { it.copy(isExifSoftwareTagEnabled = next) }
        settingsRepo.isExifSoftwareTagEnabled = next
        cameraController.isExifSoftwareTagEnabled = next
    }

    fun toggleHqViewport() {
        val next = !_uiState.value.isHqViewportEnabled
        _uiState.update { it.copy(isHqViewportEnabled = next) }
        settingsRepo.isHqViewportEnabled = next
        cameraController.isHqViewportEnabled = next
        val surface = activeSurface
        if (surface != null) {
            cameraController.switchCamera(_uiState.value.currentCameraId, surface, _uiState.value.aspectRatio) { w, h ->
                _uiState.update { it.copy(previewSize = Pair(w, h)) }
            }
        }
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
        val caps = _uiState.value.capabilities
        val resList = computeAvailableResolutions(caps, ratio)
        val topRes = resList.firstOrNull()
        if (topRes != null) {
            cameraController.targetResolution = Pair(topRes.width, topRes.height)
        }

        _uiState.update {
            it.copy(
                aspectRatio = ratio,
                availableResolutions = resList,
                selectedResolution = topRes,
                selectedMegapixels = topRes?.mpLabel ?: it.selectedMegapixels
            )
        }
        settingsRepo.savedAspectRatio = ratio.name

        val cameraId = _uiState.value.currentCameraId
        cameraController.switchCamera(cameraId, surface, ratio) { width, height ->
            _uiState.update { it.copy(previewSize = Pair(width, height)) }
        }
        applyCurrentSettings()
    }

    fun stopCamera() {
        timerJob?.cancel()
        cameraController.closeCamera()
        _uiState.update { it.copy(isCameraReady = false, timerCountdownSeconds = null) }
    }

    fun toggleShutterLimit(nanos: Long) {
        val currentLimit = _uiState.value.minShutterLimitNanos
        val newLimit = if (currentLimit == nanos) null else nanos
        _uiState.update { it.copy(minShutterLimitNanos = newLimit, exposureMode = ExposureMode.AUTO) }
        settingsRepo.savedMinShutterLimitNanos = newLimit
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedExposureMode = ExposureMode.AUTO.name
        applyCurrentSettings()
    }

    fun toggleIsoLimit(iso: Int) {
        val currentLimit = _uiState.value.maxIsoLimit
        val newLimit = if (currentLimit == iso) null else iso
        _uiState.update { it.copy(maxIsoLimit = newLimit, exposureMode = ExposureMode.AUTO) }
        settingsRepo.savedMaxIsoLimit = newLimit
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedExposureMode = ExposureMode.AUTO.name
        applyCurrentSettings()
    }

    fun setExposureMode(mode: ExposureMode) {
        val currActualIso = _uiState.value.actualIso.coerceAtLeast(50)
        val currActualShutter = _uiState.value.actualShutterSpeedNanos.coerceAtLeast(500_000L)

        val newMinShutter = if (mode == ExposureMode.SHUTTER_PRIORITY || mode == ExposureMode.MANUAL) null else _uiState.value.minShutterLimitNanos
        val newMaxIso = if (mode == ExposureMode.ISO_PRIORITY || mode == ExposureMode.MANUAL) null else _uiState.value.maxIsoLimit

        _uiState.update { current ->
            current.copy(
                exposureMode = mode,
                iso = if (mode == ExposureMode.MANUAL || mode == ExposureMode.ISO_PRIORITY || current.iso <= 50) currActualIso else current.iso,
                shutterSpeedNanos = if (mode == ExposureMode.MANUAL || mode == ExposureMode.SHUTTER_PRIORITY || current.shutterSpeedNanos <= 500_000L) currActualShutter else current.shutterSpeedNanos,
                minShutterLimitNanos = newMinShutter,
                maxIsoLimit = newMaxIso
            )
        }
        if (mode == ExposureMode.SHUTTER_PRIORITY || mode == ExposureMode.MANUAL) {
            settingsRepo.savedMinShutterLimitNanos = null
        }
        if (mode == ExposureMode.ISO_PRIORITY || mode == ExposureMode.MANUAL) {
            settingsRepo.savedMaxIsoLimit = null
        }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedExposureMode = mode.name
        applyCurrentSettings()
    }

    fun setIso(iso: Int) {
        val caps = _uiState.value.capabilities
        val clamped = caps?.isoRange?.let { iso.coerceIn(it.lower, it.upper) } ?: iso
        _uiState.update { it.copy(iso = clamped, maxIsoLimit = null) }
        settingsRepo.savedMaxIsoLimit = null
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedIso = clamped
        applyCurrentSettings()
    }

    fun setShutterSpeedNanos(nanos: Long) {
        val caps = _uiState.value.capabilities
        val clamped = caps?.shutterSpeedRangeNanos?.let { nanos.coerceIn(it.lower, it.upper) } ?: nanos
        _uiState.update { it.copy(shutterSpeedNanos = clamped, minShutterLimitNanos = null) }
        settingsRepo.savedMinShutterLimitNanos = null
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedShutterNanos = clamped
        applyCurrentSettings()
    }

    fun setFocusDistance(diopters: Float) {
        val caps = _uiState.value.capabilities
        if (caps != null && !caps.supportsManualFocus) return
        val maxDiopters = caps?.minFocusDistanceDiopters ?: 10f
        val clamped = diopters.coerceIn(0f, maxDiopters)
        _uiState.update { it.copy(focusDistanceDiopters = clamped, isManualFocusEnabled = true) }
        if (_uiState.value.isResumeSettingsEnabled) {
            settingsRepo.savedFocusDiopters = clamped
            settingsRepo.savedIsManualFocus = true
        }
        applyCurrentSettings()
    }

    fun toggleAutoFocus() {
        val caps = _uiState.value.capabilities
        if (caps != null && !caps.supportsManualFocus) {
            _uiState.update { it.copy(isManualFocusEnabled = false) }
            return
        }
        val manual = !_uiState.value.isManualFocusEnabled
        _uiState.update { it.copy(isManualFocusEnabled = manual) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedIsManualFocus = manual
        applyCurrentSettings()
    }

    fun setEvCompensation(evIndex: Int) {
        val caps = _uiState.value.capabilities
        val clamped = caps?.evCompensationRange?.let { evIndex.coerceIn(it.lower, it.upper) } ?: evIndex
        _uiState.update { it.copy(evCompensationIndex = clamped) }
        if (_uiState.value.isResumeSettingsEnabled) settingsRepo.savedEvIndex = clamped
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
        val nextFormat = if (_uiState.value.captureRaw) CaptureFormat.JPEG else CaptureFormat.RAW_JPEG
        setCaptureFormat(nextFormat)
    }

    fun takePicture() {
        if (_uiState.value.isCapturing) return

        val timer = _uiState.value.timerMode.seconds
        if (timer > 0) {
            if (_uiState.value.timerCountdownSeconds != null) {
                timerJob?.cancel()
                _uiState.update { it.copy(timerCountdownSeconds = null) }
                return
            }
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                for (s in timer downTo 1) {
                    _uiState.update { it.copy(timerCountdownSeconds = s) }
                    delay(1000)
                }
                _uiState.update { it.copy(timerCountdownSeconds = null, isCapturing = true, shutterBlinkTrigger = System.currentTimeMillis()) }
                cameraController.takePicture(
                    captureRaw = _uiState.value.captureRaw,
                    format = _uiState.value.captureFormat
                )
            }
        } else {
            _uiState.update { it.copy(isCapturing = true, shutterBlinkTrigger = System.currentTimeMillis()) }
            cameraController.takePicture(
                captureRaw = _uiState.value.captureRaw,
                format = _uiState.value.captureFormat
            )
        }
    }

    fun takeBurstShot() {
        _uiState.update { it.copy(isCapturing = true, shutterBlinkTrigger = System.currentTimeMillis()) }
        cameraController.takeBurstPicture(
            captureRaw = _uiState.value.captureRaw,
            format = _uiState.value.captureFormat
        )
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
        settingsRepo.savedCaptureFormat = s.captureFormat.name
        settingsRepo.savedRawEnabled = s.captureRaw
        settingsRepo.savedNoiseReduction = s.noiseReductionMode.name
        settingsRepo.savedSharpness = s.sharpnessMode.name
        settingsRepo.savedMinShutterLimitNanos = s.minShutterLimitNanos
        settingsRepo.savedMaxIsoLimit = s.maxIsoLimit
    }

    private fun applyCurrentSettings() {
        val s = _uiState.value
        // If this lens is a logical zoom stop on the same camera (like on Pixel or logical zoom_0.5x):
        // Hardware zoom is lens.zoomRatio * digitalZoomRatio.
        // If simulated telephoto fallback is active (bypassing HAL lock on primary camera):
        // Hardware zoom is simulatedTeleBaseZoom * digitalZoomRatio.
        // If this lens is a discrete physical camera:
        // Hardware zoom is strictly digitalZoomRatio (base optical zoom is already 1.0x on that sensor!).
        val rawZoom = if (s.currentLens?.id?.startsWith("zoom_") == true) {
            s.currentLens.zoomRatio * s.digitalZoomRatio
        } else if (s.simulatedTeleBaseZoom != 1.0f) {
            s.simulatedTeleBaseZoom * s.digitalZoomRatio
        } else {
            s.digitalZoomRatio
        }
        val minHwZoom = if (s.simulatedTeleBaseZoom < 1.0f) s.simulatedTeleBaseZoom else 1.0f
        val effectiveHardwareZoom = rawZoom.coerceIn(minHwZoom, s.maxZoomRatio)

        cameraController.updateManualSettings(
            exposureMode = s.exposureMode,
            iso = s.iso,
            shutterNanos = s.shutterSpeedNanos,
            focusDistance = s.focusDistanceDiopters,
            isManualFocus = s.isManualFocusEnabled,
            evIndex = s.evCompensationIndex,
            whiteBalancePreset = s.whiteBalancePreset,
            kelvin = s.kelvinTemperature,
            zoomRatio = effectiveHardwareZoom,
            flashMode = s.flashMode,
            noiseReduction = s.noiseReductionMode,
            sharpness = s.sharpnessMode,
            minShutterLimitNanos = s.minShutterLimitNanos,
            maxIsoLimit = s.maxIsoLimit
        )
    }

    private fun computeAvailableResolutions(caps: HardwareCapabilities?, ratio: AspectRatio): List<CameraResolution> {
        if (caps == null) return emptyList()
        val targetRatio = ratio.widthFactor.toDouble() / ratio.heightFactor.toDouble()
        val matching = caps.supportedJpegSizes.filter {
            val r = it.first.toDouble() / it.second.toDouble()
            Math.abs(r - targetRatio) < 0.08
        }.sortedByDescending { it.first * it.second }

        val list = mutableListOf<CameraResolution>()
        val seenMp = mutableSetOf<String>()
        for (size in matching) {
            val mp = String.format(java.util.Locale.US, "%.1f MP", (size.first * size.second) / 1_000_000f)
            if (seenMp.add(mp)) {
                list.add(CameraResolution(size.first, size.second, mp))
            }
            if (list.size >= 4) break
        }
        if (list.isEmpty() && matching.isNotEmpty()) {
            val top = matching.first()
            val mp = String.format(java.util.Locale.US, "%.1f MP", (top.first * top.second) / 1_000_000f)
            list.add(CameraResolution(top.first, top.second, mp))
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopSensors()
        cameraController.closeCamera()
        cameraController.stopBackgroundThread()
    }
}
