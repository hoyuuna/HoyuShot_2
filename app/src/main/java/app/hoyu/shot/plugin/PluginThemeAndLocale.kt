package app.hoyu.shot.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
        return translations[key] ?: translations[defaultVal] ?: defaultVal
    }
}

val LocalPluginLocalization = compositionLocalOf { PluginLocalization() }

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    val translatedText = tr(text)
    androidx.compose.material3.Text(
        text = translatedText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun tr(keyOrDefault: String): String {
    return LocalPluginLocalization.current.get(keyOrDefault, keyOrDefault)
}

@Composable
fun tr(key: String, defaultVal: String): String {
    return LocalPluginLocalization.current.get(key, defaultVal)
}
