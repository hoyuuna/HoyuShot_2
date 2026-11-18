package app.hoyu.shot.plugin

import org.json.JSONArray
import org.json.JSONObject

enum class SettingType {
    TOGGLE,
    SLIDER,
    DROPDOWN
}

data class PluginSettingItem(
    val key: String,
    val title: String,
    val description: String,
    val type: SettingType,
    val defaultValue: Any,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val options: List<String> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): PluginSettingItem {
            val key = json.optString("key", "setting_${System.currentTimeMillis()}")
            val title = json.optString("title", key)
            val desc = json.optString("description", "")
            val typeStr = json.optString("type", "toggle").uppercase()
            val type = when (typeStr) {
                "SLIDER" -> SettingType.SLIDER
                "DROPDOWN", "SELECT" -> SettingType.DROPDOWN
                else -> SettingType.TOGGLE
            }
            val defaultVal: Any = when (type) {
                SettingType.TOGGLE -> json.optBoolean("defaultValue", false)
                SettingType.SLIDER -> json.optDouble("defaultValue", 0.0).toFloat()
                SettingType.DROPDOWN -> json.optString("defaultValue", "")
            }
            val min = json.optDouble("min", 0.0).toFloat()
            val max = json.optDouble("max", 100.0).toFloat()
            val step = json.optDouble("step", 1.0).toFloat()
            val opts = mutableListOf<String>()
            val optArr = json.optJSONArray("options")
            if (optArr != null) {
                for (i in 0 until optArr.length()) {
                    opts.add(optArr.getString(i))
                }
            }
            return PluginSettingItem(
                key = key,
                title = title,
                description = desc,
                type = type,
                defaultValue = defaultVal,
                min = min,
                max = max,
                step = step,
                options = opts
            )
        }
    }
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val gitUrl: String = "",
    val settings: List<PluginSettingItem> = emptyList()
) {
    companion object {
        fun fromJson(jsonString: String, gitUrl: String = ""): PluginManifest {
            val obj = JSONObject(jsonString)
            val id = obj.optString("id", "custom_plugin_${System.currentTimeMillis()}")
            val name = obj.optString("name", id)
            val version = obj.optString("version", "1.0.0")
            val author = obj.optString("author", "Community")
            val desc = obj.optString("description", "HoyuShot Git Plugin")
            val settingsList = mutableListOf<PluginSettingItem>()
            val settingsArr = obj.optJSONArray("settings")
            if (settingsArr != null) {
                for (i in 0 until settingsArr.length()) {
                    val itemObj = settingsArr.getJSONObject(i)
                    settingsList.add(PluginSettingItem.fromJson(itemObj))
                }
            }
            return PluginManifest(
                id = id,
                name = name,
                version = version,
                author = author,
                description = desc,
                gitUrl = gitUrl,
                settings = settingsList
            )
        }
    }
}

data class PluginInstance(
    val manifest: PluginManifest,
    val isEnabled: Boolean = true,
    val scriptCode: String = "",
    val dirPath: String = ""
)
