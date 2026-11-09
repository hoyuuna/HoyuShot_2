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
}
