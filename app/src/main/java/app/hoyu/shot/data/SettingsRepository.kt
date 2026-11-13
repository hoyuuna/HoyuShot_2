package app.hoyu.shot.data

import app.hoyu.shot.model.AspectRatio
import app.hoyu.shot.model.CaptureFormat
import app.hoyu.shot.model.ColorProfile
import app.hoyu.shot.model.CustomModePreset
import app.hoyu.shot.model.ExposureMode
import app.hoyu.shot.model.FlashMode
import app.hoyu.shot.model.NoiseReductionMode
import app.hoyu.shot.model.SharpnessMode
import app.hoyu.shot.model.WhiteBalancePreset
import org.json.JSONArray
import org.json.JSONObject

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("hoyushot_prefs", Context.MODE_PRIVATE)

    var isResumeSettingsEnabled: Boolean
        get() = true
        set(_) {}

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean("is_setup_completed", false)
        set(value) = prefs.edit().putBoolean("is_setup_completed", value).apply()

    var savedCameraId: String
        get() = prefs.getString("saved_camera_id", "0") ?: "0"
        set(value) = prefs.edit().putString("saved_camera_id", value).apply()

    var savedIso: Int
        get() = prefs.getInt("saved_iso", 100)
        set(value) = prefs.edit().putInt("saved_iso", value).apply()

    var savedShutterNanos: Long
        get() = prefs.getLong("saved_shutter_nanos", 10_000_000L)
        set(value) = prefs.edit().putLong("saved_shutter_nanos", value).apply()

    var savedMinShutterLimitNanos: Long?
        get() {
            val v = prefs.getLong("saved_min_shutter_limit", -1L)
            return if (v > 0) v else null
        }
        set(value) {
            if (value != null && value > 0) {
                prefs.edit().putLong("saved_min_shutter_limit", value).apply()
            } else {
                prefs.edit().remove("saved_min_shutter_limit").apply()
            }
        }

    var savedMaxIsoLimit: Int?
        get() {
            val v = prefs.getInt("saved_max_iso_limit", -1)
            return if (v > 0) v else null
        }
        set(value) {
            if (value != null && value > 0) {
                prefs.edit().putInt("saved_max_iso_limit", value).apply()
            } else {
                prefs.edit().remove("saved_max_iso_limit").apply()
            }
        }

    var savedFocusDiopters: Float
        get() = prefs.getFloat("saved_focus_diopters", 0f)
        set(value) = prefs.edit().putFloat("saved_focus_diopters", value).apply()

    var savedIsManualFocus: Boolean
        get() = prefs.getBoolean("saved_is_manual_focus", false)
        set(value) = prefs.edit().putBoolean("saved_is_manual_focus", value).apply()

    var savedEvIndex: Int
        get() = prefs.getInt("saved_ev_index", 0)
        set(value) = prefs.edit().putInt("saved_ev_index", value).apply()

    var savedKelvin: Int
        get() = prefs.getInt("saved_kelvin", 5500)
        set(value) = prefs.edit().putInt("saved_kelvin", value).apply()

    var savedExposureMode: String
        get() = prefs.getString("saved_exposure_mode", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("saved_exposure_mode", value).apply()

    var savedNoiseReduction: String
        get() = prefs.getString("saved_nr", "FAST") ?: "FAST"
        set(value) = prefs.edit().putString("saved_nr", value).apply()

    var savedSharpness: String
        get() = prefs.getString("saved_sharpness", "FAST") ?: "FAST"
        set(value) = prefs.edit().putString("saved_sharpness", value).apply()

    var savedRawEnabled: Boolean
        get() = prefs.getBoolean("saved_raw_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_raw_enabled", value).apply()

    var savedCaptureFormat: String
        get() = prefs.getString("saved_capture_format", null)
            ?: if (savedRawEnabled) "RAW_JPEG" else "JPEG"
        set(value) = prefs.edit().putString("saved_capture_format", value).apply()

    var savedGridMode: String
        get() = prefs.getString("saved_grid_mode", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("saved_grid_mode", value).apply()

    var savedAspectRatio: String
        get() = prefs.getString("saved_aspect_ratio", "RATIO_4_3") ?: "RATIO_4_3"
        set(value) = prefs.edit().putString("saved_aspect_ratio", value).apply()

    var savedHistogramVisible: Boolean
        get() = prefs.getBoolean("saved_histogram_visible", false)
        set(value) = prefs.edit().putBoolean("saved_histogram_visible", value).apply()

    var savedHorizonVisible: Boolean
        get() = prefs.getBoolean("saved_horizon_visible", true)
        set(value) = prefs.edit().putBoolean("saved_horizon_visible", value).apply()

    var savedPeakingEnabled: Boolean
        get() = prefs.getBoolean("saved_peaking_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_peaking_enabled", value).apply()

    var savedZebraEnabled: Boolean
        get() = prefs.getBoolean("saved_zebra_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_zebra_enabled", value).apply()

    var savedSunCompassEnabled: Boolean
        get() = prefs.getBoolean("saved_sun_compass_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_sun_compass_enabled", value).apply()

    var isGeotaggingEnabled: Boolean
        get() = prefs.getBoolean("saved_geotagging_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_geotagging_enabled", value).apply()

    // ==========================================
    // ADVANCED SETTINGS
    // ==========================================
    var jpegQuality: Int
        get() = prefs.getInt("adv_jpeg_quality", 95)
        set(value) = prefs.edit().putInt("adv_jpeg_quality", value).apply()

    var isMirrorSelfie: Boolean
        get() = prefs.getBoolean("adv_mirror_selfie", false)
        set(value) = prefs.edit().putBoolean("adv_mirror_selfie", value).apply()

    var cameraSoundMode: String
        get() = prefs.getString("adv_camera_sound", "VIBRATE") ?: "VIBRATE"
        set(value) = prefs.edit().putString("adv_camera_sound", value).apply()

    var uiHapticsMode: String
        get() = prefs.getString("adv_ui_haptics", "VIBRATE") ?: "VIBRATE"
        set(value) = prefs.edit().putString("adv_ui_haptics", value).apply()

    var tapAction: String
        get() = prefs.getString("adv_tap_action", "FOCUS") ?: "FOCUS"
        set(value) = prefs.edit().putString("adv_tap_action", value).apply()

    var holdAction: String
        get() = prefs.getString("adv_hold_action", "AE") ?: "AE"
        set(value) = prefs.edit().putString("adv_hold_action", value).apply()

    var volumeUpAction: String
        get() = prefs.getString("adv_vol_up_action", "SHUTTER") ?: "SHUTTER"
        set(value) = prefs.edit().putString("adv_vol_up_action", value).apply()

    var volumeDownAction: String
        get() = prefs.getString("adv_vol_down_action", "SHUTTER") ?: "SHUTTER"
        set(value) = prefs.edit().putString("adv_vol_down_action", value).apply()

    var antibandingMode: String
        get() = prefs.getString("adv_antibanding", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("adv_antibanding", value).apply()

    var isUiBlurEnabled: Boolean
        get() = prefs.getBoolean("adv_ui_blur", true)
        set(value) = prefs.edit().putBoolean("adv_ui_blur", value).apply()

    var isAutoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean("adv_auto_update_check", true)
        set(value) = prefs.edit().putBoolean("adv_auto_update_check", value).apply()

    var isPreReleaseUpdateEnabled: Boolean
        get() = prefs.getBoolean("adv_prerelease_update", true)
        set(value) = prefs.edit().putBoolean("adv_prerelease_update", value).apply()

    var isTeleZoomFallbackEnabled: Boolean
        get() = prefs.getBoolean("adv_tele_zoom_fallback", true)
        set(value) = prefs.edit().putBoolean("adv_tele_zoom_fallback", value).apply()

    var isExifSoftwareTagEnabled: Boolean
        get() = prefs.getBoolean("adv_exif_software_tag", true)
        set(value) = prefs.edit().putBoolean("adv_exif_software_tag", value).apply()

    var isHqViewportEnabled: Boolean
        get() = prefs.getBoolean("adv_hq_viewport", true)
        set(value) = prefs.edit().putBoolean("adv_hq_viewport", value).apply()

    var savedColorProfile: String
        get() = prefs.getString("saved_color_profile", ColorProfile.NATURAL.name) ?: ColorProfile.NATURAL.name
        set(value) = prefs.edit().putString("saved_color_profile", value).apply()

    var savedHdrEnabled: Boolean
        get() = prefs.getBoolean("saved_hdr_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_hdr_enabled", value).apply()

    var savedActiveCustomPresetId: String?
        get() = prefs.getString("saved_active_custom_preset_id", null)
        set(value) = if (value == null) {
            prefs.edit().remove("saved_active_custom_preset_id").apply()
        } else {
            prefs.edit().putString("saved_active_custom_preset_id", value).apply()
        }

    fun loadCustomPresets(): List<CustomModePreset> {
        val raw = prefs.getString("saved_custom_presets_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val baseMode = runCatching {
                        ExposureMode.valueOf(obj.optString("baseMode"))
                    }.getOrNull()?.takeIf { it.isCustomPresetEligible() } ?: continue

                    add(
                        CustomModePreset(
                            id = id,
                            name = name,
                            baseMode = baseMode,
                            iso = obj.optInt("iso", 100),
                            shutterSpeedNanos = obj.optLong("shutterSpeedNanos", 10_000_000L),
                            focusDistanceDiopters = obj.optDouble("focusDistanceDiopters", 0.0).toFloat(),
                            isManualFocusEnabled = obj.optBoolean("isManualFocusEnabled", false),
                            evCompensationIndex = obj.optInt("evCompensationIndex", 0),
                            whiteBalancePreset = runCatching {
                                WhiteBalancePreset.valueOf(obj.optString("whiteBalancePreset"))
                            }.getOrDefault(WhiteBalancePreset.AUTO),
                            kelvinTemperature = obj.optInt("kelvinTemperature", 5500),
                            cameraId = obj.optString("cameraId", "0"),
                            lensId = obj.optString("lensId", "phys_0"),
                            digitalZoomRatio = obj.optDouble("digitalZoomRatio", 1.0).toFloat(),
                            aspectRatio = runCatching {
                                AspectRatio.valueOf(obj.optString("aspectRatio"))
                            }.getOrDefault(AspectRatio.RATIO_4_3),
                            captureFormat = runCatching {
                                CaptureFormat.valueOf(obj.optString("captureFormat"))
                            }.getOrDefault(CaptureFormat.JPEG),
                            resolutionWidth = if (obj.has("resolutionWidth") && !obj.isNull("resolutionWidth")) obj.optInt("resolutionWidth") else null,
                            resolutionHeight = if (obj.has("resolutionHeight") && !obj.isNull("resolutionHeight")) obj.optInt("resolutionHeight") else null,
                            resolutionMpLabel = if (obj.has("resolutionMpLabel") && !obj.isNull("resolutionMpLabel")) obj.optString("resolutionMpLabel") else null,
                            flashMode = runCatching {
                                FlashMode.valueOf(obj.optString("flashMode"))
                            }.getOrDefault(FlashMode.OFF),
                            noiseReductionMode = runCatching {
                                NoiseReductionMode.valueOf(obj.optString("noiseReductionMode"))
                            }.getOrDefault(NoiseReductionMode.FAST),
                            sharpnessMode = runCatching {
                                SharpnessMode.valueOf(obj.optString("sharpnessMode"))
                            }.getOrDefault(SharpnessMode.NORMAL),
                            minShutterLimitNanos = if (obj.has("minShutterLimitNanos") && !obj.isNull("minShutterLimitNanos")) obj.optLong("minShutterLimitNanos") else null,
                            maxIsoLimit = if (obj.has("maxIsoLimit") && !obj.isNull("maxIsoLimit")) obj.optInt("maxIsoLimit") else null,
                            colorProfile = runCatching {
                                ColorProfile.valueOf(obj.optString("colorProfile"))
                            }.getOrDefault(ColorProfile.NATURAL),
                            isHdrEnabled = obj.optBoolean("isHdrEnabled", false),
                            createdAt = obj.optLong("createdAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomPresets(presets: List<CustomModePreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("baseMode", preset.baseMode.name)
                put("iso", preset.iso)
                put("shutterSpeedNanos", preset.shutterSpeedNanos)
                put("focusDistanceDiopters", preset.focusDistanceDiopters.toDouble())
                put("isManualFocusEnabled", preset.isManualFocusEnabled)
                put("evCompensationIndex", preset.evCompensationIndex)
                put("whiteBalancePreset", preset.whiteBalancePreset.name)
                put("kelvinTemperature", preset.kelvinTemperature)
                put("cameraId", preset.cameraId)
                put("lensId", preset.lensId)
                put("digitalZoomRatio", preset.digitalZoomRatio.toDouble())
                put("aspectRatio", preset.aspectRatio.name)
                put("captureFormat", preset.captureFormat.name)
                put("resolutionWidth", preset.resolutionWidth ?: JSONObject.NULL)
                put("resolutionHeight", preset.resolutionHeight ?: JSONObject.NULL)
                put("resolutionMpLabel", preset.resolutionMpLabel ?: JSONObject.NULL)
                put("flashMode", preset.flashMode.name)
                put("noiseReductionMode", preset.noiseReductionMode.name)
                put("sharpnessMode", preset.sharpnessMode.name)
                put("minShutterLimitNanos", preset.minShutterLimitNanos ?: JSONObject.NULL)
                put("maxIsoLimit", preset.maxIsoLimit ?: JSONObject.NULL)
                put("colorProfile", preset.colorProfile.name)
                put("isHdrEnabled", preset.isHdrEnabled)
                put("createdAt", preset.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_custom_presets_json", array.toString()).apply()
    }
}
