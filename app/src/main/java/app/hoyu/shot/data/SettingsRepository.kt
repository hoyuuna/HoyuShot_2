package app.hoyu.shot.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("hoyushot_prefs", Context.MODE_PRIVATE)

    var isResumeSettingsEnabled: Boolean
        get() = prefs.getBoolean("resume_settings_enabled", false)
        set(value) = prefs.edit().putBoolean("resume_settings_enabled", value).apply()

    var savedCameraId: String
        get() = prefs.getString("saved_camera_id", "0") ?: "0"
        set(value) = prefs.edit().putString("saved_camera_id", value).apply()

    var savedIso: Int
        get() = prefs.getInt("saved_iso", 100)
        set(value) = prefs.edit().putInt("saved_iso", value).apply()

    var savedShutterNanos: Long
        get() = prefs.getLong("saved_shutter_nanos", 10_000_000L)
        set(value) = prefs.edit().putLong("saved_shutter_nanos", value).apply()

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

    var isGeotaggingEnabled: Boolean
        get() = prefs.getBoolean("saved_geotagging_enabled", false)
        set(value) = prefs.edit().putBoolean("saved_geotagging_enabled", value).apply()
}
