package app.hoyu.shot.plugin

import android.content.Context
import android.util.Log
import com.quickjs.JSArray
import com.quickjs.JSContext
import com.quickjs.JSObject
import com.quickjs.JavaVoidCallback
import com.quickjs.QuickJS
import org.json.JSONObject

data class CaptureParamsHookResult(
    var iso: Int? = null,
    var shutterSpeedNanos: Long? = null,
    var exposureCompensation: Int? = null,
    var customTag: String? = null
)

class PluginScriptEngine(private val context: Context) {
    private val TAG = "PluginScriptEngine"

    fun executeBeforeCapture(
        instance: PluginInstance,
        userSettings: Map<String, Any>,
        currentIso: Int,
        currentShutterSpeedNanos: Long,
        currentEv: Int
    ): CaptureParamsHookResult {
        val result = CaptureParamsHookResult(
            iso = currentIso,
            shutterSpeedNanos = currentShutterSpeedNanos,
            exposureCompensation = currentEv
        )

        if (instance.scriptCode.isBlank()) return result

        var quickJS: QuickJS? = null
        var jsContext: JSContext? = null
        try {
            quickJS = QuickJS.createRuntime()
            jsContext = quickJS.createContext()

            // Bridge console.log -> HoyuShot Logcat
            jsContext.registerJavaMethod(object : JavaVoidCallback {
                override fun invoke(receiver: JSObject?, args: JSArray?) {
                    if (args != null && args.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until args.length()) {
                            if (i > 0) sb.append(" ")
                            sb.append(args.get(i)?.toString() ?: "null")
                        }
                        Log.d("HoyuShotPlugin[${instance.manifest.id}]", sb.toString())
                    }
                }
            }, "nativeLog")

            // Inject settings JSON & console wrapper
            val settingsJson = JSONObject(userSettings).toString()
            val initScript = """
                var pluginSettings = $settingsJson;
                var console = {
                    log: function() {
                        var str = Array.prototype.slice.call(arguments).join(" ");
                        nativeLog(str);
                    }
                };
            """.trimIndent()
            jsContext.executeVoidScript(initScript, "init.js")

            // Evaluate plugin index.js code
            jsContext.executeVoidScript(instance.scriptCode, "index.js")

            // Check if onBeforeCapture exists
            val hasHook = jsContext.executeBooleanScript("typeof onBeforeCapture === 'function'", "check.js")
            if (hasHook) {
                val execHook = """
                    (function() {
                        var params = {
                            iso: $currentIso,
                            shutterNanos: $currentShutterSpeedNanos,
                            ev: $currentEv
                        };
                        var res = onBeforeCapture(params, pluginSettings);
                        return JSON.stringify(res || params);
                    })();
                """.trimIndent()

                val hookResJson = jsContext.executeStringScript(execHook, "hook.js")
                if (!hookResJson.isNullOrBlank()) {
                    val resObj = JSONObject(hookResJson)
                    if (resObj.has("iso")) {
                        result.iso = resObj.getInt("iso")
                    }
                    if (resObj.has("shutterNanos")) {
                        result.shutterSpeedNanos = resObj.getLong("shutterNanos")
                    }
                    if (resObj.has("ev")) {
                        result.exposureCompensation = resObj.getInt("ev")
                    }
                    if (resObj.has("customTag")) {
                        result.customTag = resObj.getString("customTag")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing plugin ${instance.manifest.id}: ${e.message}", e)
        } finally {
            try {
                jsContext?.close()
                quickJS?.close()
            } catch (ignored: Throwable) {}
        }

        return result
    }

    /**
     * Executes photo post-processing hook on bitmap
     * JS hook: onProcessPhoto(metrics, pluginSettings) -> returns { rMultiplier, gMultiplier, bMultiplier, vibranceBoost, saturationBoost }
     */
    fun executeProcessPhotoHook(
        instance: PluginInstance,
        userSettings: Map<String, Any>,
        avgR: Float,
        avgG: Float,
        avgB: Float,
        avgSatPercent: Float,
        maxClipPercent: Float
    ): Map<String, Any> {
        val resultMap = mutableMapOf<String, Any>()
        if (instance.scriptCode.isBlank()) return resultMap

        var quickJS: QuickJS? = null
        var jsContext: JSContext? = null
        try {
            quickJS = QuickJS.createRuntime()
            jsContext = quickJS.createContext()

            jsContext.registerJavaMethod(object : JavaVoidCallback {
                override fun invoke(receiver: JSObject?, args: JSArray?) {
                    if (args != null && args.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until args.length()) {
                            if (i > 0) sb.append(" ")
                            sb.append(args.get(i)?.toString() ?: "null")
                        }
                        Log.d("HoyuShotPlugin[${instance.manifest.id}]", sb.toString())
                    }
                }
            }, "nativeLog")

            val settingsJson = JSONObject(userSettings).toString()
            val initScript = """
                var pluginSettings = $settingsJson;
                var console = {
                    log: function() {
                        var str = Array.prototype.slice.call(arguments).join(" ");
                        nativeLog(str);
                    }
                };
            """.trimIndent()
            jsContext.executeVoidScript(initScript, "init.js")
            jsContext.executeVoidScript(instance.scriptCode, "index.js")

            val hasHook = jsContext.executeBooleanScript("typeof onProcessPhoto === 'function'", "check.js")
            if (hasHook) {
                val execHook = """
                    (function() {
                        var metrics = {
                            avgR: $avgR,
                            avgG: $avgG,
                            avgB: $avgB,
                            avgSatPercent: $avgSatPercent,
                            maxClipPercent: $maxClipPercent
                        };
                        var res = onProcessPhoto(metrics, pluginSettings);
                        return JSON.stringify(res || {});
                    })();
                """.trimIndent()

                val hookResJson = jsContext.executeStringScript(execHook, "process.js")
                if (!hookResJson.isNullOrBlank()) {
                    val obj = JSONObject(hookResJson)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        resultMap[k] = obj.get(k)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing onProcessPhoto for ${instance.manifest.id}: ${e.message}", e)
        } finally {
            try {
                jsContext?.close()
                quickJS?.close()
            } catch (ignored: Throwable) {}
        }

        return resultMap
    }
}
