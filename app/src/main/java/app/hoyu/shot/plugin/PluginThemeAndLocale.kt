package app.hoyu.shot.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Dynamic theme properties configured by a Theme Plugin.
 */
data class PluginThemeConfig(
    val accentColor: Color = Color(0xFFE11D48),
    val backgroundColor: Color = Color(0xFF09090B),
    val cardBackground: Color = Color(0xFF18181B),
    val borderColor: Color = Color(0xFF27272A),
    val textColor: Color = Color(0xFFFAFAFA),
    val subtextColor: Color = Color(0xFFA1A1AA),
    val cornerRadiusDp: Int = 2,
    val isCustom: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject?): PluginThemeConfig {
            if (json == null) return PluginThemeConfig()
            return PluginThemeConfig(
                accentColor = parseHexColor(json.optString("accentColor"), Color(0xFFE11D48)),
                backgroundColor = parseHexColor(json.optString("backgroundColor"), Color(0xFF09090B)),
                cardBackground = parseHexColor(json.optString("cardBackground"), Color(0xFF18181B)),
                borderColor = parseHexColor(json.optString("borderColor"), Color(0xFF27272A)),
                textColor = parseHexColor(json.optString("textColor"), Color(0xFFFAFAFA)),
                subtextColor = parseHexColor(json.optString("subtextColor"), Color(0xFFA1A1AA)),
                cornerRadiusDp = json.optInt("cornerRadiusDp", 2),
                isCustom = true
            )
        }

        private fun parseHexColor(hex: String?, fallback: Color): Color {
            if (hex.isNullOrBlank()) return fallback
            return try {
                val clean = hex.trim().removePrefix("#")
                when (clean.length) {
                    6 -> Color(android.graphics.Color.parseColor("#$clean"))
                    8 -> Color(android.graphics.Color.parseColor("#$clean"))
                    else -> fallback
                }
            } catch (e: Exception) {
                fallback
            }
        }
    }
}

val LocalPluginTheme = compositionLocalOf { PluginThemeConfig() }

/**
 * Dynamic string dictionary configured by a Language Plugin.
 */
class PluginLocalization(
    val translations: Map<String, String> = emptyMap()
) {
    fun get(key: String, defaultVal: String): String {
        return translations[key] ?: defaultVal
    }
}

val LocalPluginLocalization = compositionLocalOf { PluginLocalization() }

@Composable
fun tr(key: String, defaultVal: String): String {
    return LocalPluginLocalization.current.get(key, defaultVal)
}
