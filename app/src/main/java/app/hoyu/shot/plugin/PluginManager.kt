package app.hoyu.shot.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context) {
    private val TAG = "PluginManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("hoyushot_plugins_prefs", Context.MODE_PRIVATE)
    private val pluginsDir: File = File(context.filesDir, "plugins")
    private val scriptEngine = PluginScriptEngine(context)

    private val _installedPlugins = MutableStateFlow<List<PluginInstance>>(emptyList())
    val installedPlugins: StateFlow<List<PluginInstance>> = _installedPlugins.asStateFlow()

    val currentTheme = MutableStateFlow<PluginThemeConfig>(PluginThemeConfig())
    val currentLocalization = MutableStateFlow<PluginLocalization>(PluginLocalization())

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
        loadInstalledPlugins()
    }

    private fun recomputeThemeAndLocale(plugins: List<PluginInstance>) {
        val active = plugins.filter { it.isEnabled }
        // Compute active theme
        val activeTheme = active.mapNotNull { it.manifest.theme }.firstOrNull() ?: PluginThemeConfig()
        currentTheme.value = activeTheme

        // Compute merged active localization
        val mergedTranslations = mutableMapOf<String, String>()
        for (p in active) {
            mergedTranslations.putAll(p.manifest.translations)
        }
        currentLocalization.value = PluginLocalization(mergedTranslations)
    }

    fun loadInstalledPlugins() {
        val list = mutableListOf<PluginInstance>()
        val pluginFolders = pluginsDir.listFiles() ?: emptyArray()

        for (folder in pluginFolders) {
            if (folder.isDirectory) {
                val manifestFile = File(folder, "plugin.json")
                val scriptFile = File(folder, "index.js")
                if (manifestFile.exists()) {
                    try {
                        val manifestJson = manifestFile.readText()
                        val gitUrl = File(folder, ".giturl").let { if (it.exists()) it.readText() else "" }
                        val manifest = PluginManifest.fromJson(manifestJson, gitUrl)
                        val isEnabled = prefs.getBoolean("plugin_enabled_${manifest.id}", true)
                        val scriptCode = if (scriptFile.exists()) scriptFile.readText() else ""
                        list.add(
                            PluginInstance(
                                manifest = manifest,
                                isEnabled = isEnabled,
                                scriptCode = scriptCode,
                                dirPath = folder.absolutePath
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading plugin in folder ${folder.name}: ${e.message}")
                    }
                }
            }
        }
        _installedPlugins.value = list
        recomputeThemeAndLocale(list)
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()
        val updated = _installedPlugins.value.map {
            if (it.manifest.id == pluginId) it.copy(isEnabled = enabled) else it
        }
        _installedPlugins.value = updated
        recomputeThemeAndLocale(updated)
    }

    fun deletePlugin(pluginId: String): Boolean {
        val folder = File(pluginsDir, pluginId)
        val success = folder.deleteRecursively()
        prefs.edit().remove("plugin_enabled_$pluginId").apply()
        // Clear plugin settings
        val settingsPref = context.getSharedPreferences("plugin_settings_$pluginId", Context.MODE_PRIVATE)
        settingsPref.edit().clear().apply()
        loadInstalledPlugins()
        return success
    }

    fun getPluginSettings(pluginId: String): Map<String, Any> {
        val settingsPref = context.getSharedPreferences("plugin_settings_$pluginId", Context.MODE_PRIVATE)
        val all = settingsPref.all ?: return emptyMap()
        val result = mutableMapOf<String, Any>()
        for ((k, v) in all) {
            if (v != null) {
                result[k] = v
            }
        }
        return result
    }

    fun savePluginSetting(pluginId: String, key: String, value: Any) {
        val settingsPref = context.getSharedPreferences("plugin_settings_$pluginId", Context.MODE_PRIVATE)
        val editor = settingsPref.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            else -> editor.putString(key, value.toString())
        }
        editor.apply()
    }

    /**
     * Resolves raw GitHub / GitLab plugin.json raw content URL to check for updates
     */
    fun resolveRawPluginJsonUrl(gitUrl: String): String {
        var cleanUrl = gitUrl.trim()
        if (cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length - 4)
        }
        if (cleanUrl.contains("github.com")) {
            val parts = cleanUrl.replace("https://github.com/", "").split("/")
            if (parts.size >= 2) {
                val owner = parts[0]
                val repo = parts[1]
                return "https://raw.githubusercontent.com/$owner/$repo/main/plugin.json"
            }
        }
        if (cleanUrl.contains("gitlab.com")) {
            val parts = cleanUrl.replace("https://gitlab.com/", "").split("/")
            if (parts.size >= 2) {
                val owner = parts[0]
                val repo = parts[1]
                return "https://gitlab.com/$owner/$repo/-/raw/main/plugin.json"
            }
        }
        return ""
    }

    suspend fun checkForPluginUpdates() = withContext(Dispatchers.IO) {
        val currentList = _installedPlugins.value
        val updatedList = mutableListOf<PluginInstance>()

        for (instance in currentList) {
            val gitUrl = instance.manifest.gitUrl
            if (gitUrl.isNotBlank()) {
                val rawUrl = resolveRawPluginJsonUrl(gitUrl)
                var newVersion: String? = null
                if (rawUrl.isNotBlank()) {
                    try {
                        var conn = URL(rawUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        if (conn.responseCode == 404 && rawUrl.contains("/main/")) {
                            val masterRawUrl = rawUrl.replace("/main/", "/master/")
                            conn = URL(masterRawUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 8000
                            conn.readTimeout = 8000
                        }
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val remoteManifest = PluginManifest.fromJson(jsonStr, gitUrl)
                            if (remoteManifest.version != instance.manifest.version) {
                                newVersion = remoteManifest.version
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed checking update for ${instance.manifest.id}: ${e.message}")
                    }
                }
                updatedList.add(instance.copy(updateAvailableVersion = newVersion))
            } else {
                updatedList.add(instance)
            }
        }
        _installedPlugins.value = updatedList
    }

    suspend fun updatePlugin(
        pluginId: String,
        onStatus: (String) -> Unit
    ): Result<PluginManifest> {
        val instance = _installedPlugins.value.find { it.manifest.id == pluginId }
            ?: return Result.failure(IllegalArgumentException("Plugin $pluginId not found"))
        val gitUrl = instance.manifest.gitUrl
        if (gitUrl.isBlank()) {
            return Result.failure(IllegalStateException("Plugin was not installed from Git"))
        }
        return installPluginFromGit(gitUrl, onStatus)
    }
    fun resolveGitDownloadUrl(gitUrl: String): String {
        var cleanUrl = gitUrl.trim()
        if (cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length - 4)
        }
        if (cleanUrl.contains("github.com")) {
            return if (cleanUrl.endsWith("/")) "${cleanUrl}archive/refs/heads/main.zip" else "$cleanUrl/archive/refs/heads/main.zip"
        }
        if (cleanUrl.contains("gitlab.com")) {
            return if (cleanUrl.endsWith("/")) "${cleanUrl}-/archive/main/main.zip" else "$cleanUrl/-/archive/main/main.zip"
        }
        return gitUrl
    }

    suspend fun installPluginFromGit(
        gitUrl: String,
        onStatus: (String) -> Unit
    ): Result<PluginManifest> = withContext(Dispatchers.IO) {
        try {
            if (!gitUrl.trim().endsWith(".git") && !gitUrl.contains("github.com") && !gitUrl.contains("gitlab.com")) {
                return@withContext Result.failure(IllegalArgumentException("Link must be a valid .git repository URL (e.g., https://github.com/user/repo.git)"))
            }

            onStatus("Connecting to repository...")
            val downloadUrl = resolveGitDownloadUrl(gitUrl)
            val tempZipFile = File(context.cacheDir, "plugin_download_${System.currentTimeMillis()}.zip")

            // Download zip
            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                // Try fallback to master branch if main branch is 404
                if (connection.responseCode == 404 && downloadUrl.contains("/main.zip")) {
                    val masterUrl = downloadUrl.replace("/main.zip", "/master.zip")
                    val retryConn = URL(masterUrl).openConnection() as HttpURLConnection
                    retryConn.instanceFollowRedirects = true
                    retryConn.connect()
                    if (retryConn.responseCode == HttpURLConnection.HTTP_OK) {
                        retryConn.inputStream.use { input ->
                            FileOutputStream(tempZipFile).use { output -> input.copyTo(output) }
                        }
                    } else {
                        return@withContext Result.failure(IOException("Failed to download repository (HTTP ${retryConn.responseCode})"))
                    }
                } else {
                    return@withContext Result.failure(IOException("Failed to connect to git repository (HTTP ${connection.responseCode})"))
                }
            } else {
                connection.inputStream.use { input ->
                    FileOutputStream(tempZipFile).use { output -> input.copyTo(output) }
                }
            }

            onStatus("Extracting repository...")
            val tempExtractDir = File(context.cacheDir, "plugin_extracted_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()

            unzip(tempZipFile, tempExtractDir)
            tempZipFile.delete()

            // Locate plugin.json
            val rootDir = findFolderWithPluginJson(tempExtractDir)
                ?: return@withContext Result.failure(FileNotFoundException("Repository does not contain 'plugin.json' at root!"))

            val manifestFile = File(rootDir, "plugin.json")
            val manifestJson = manifestFile.readText()
            val manifest = PluginManifest.fromJson(manifestJson, gitUrl)

            onStatus("Installing plugin: ${manifest.name}...")
            val targetFolder = File(pluginsDir, manifest.id)
            if (targetFolder.exists()) {
                targetFolder.deleteRecursively()
            }
            targetFolder.mkdirs()

            rootDir.copyRecursively(targetFolder, overwrite = true)
            File(targetFolder, ".giturl").writeText(gitUrl)

            tempExtractDir.deleteRecursively()

            // Initialize default settings if not exists
            val settingsPref = context.getSharedPreferences("plugin_settings_${manifest.id}", Context.MODE_PRIVATE)
            val editor = settingsPref.edit()
            for (setting in manifest.settings) {
                if (!settingsPref.contains(setting.key)) {
                    when (val defaultVal = setting.defaultValue) {
                        is Boolean -> editor.putBoolean(setting.key, defaultVal)
                        is Float -> editor.putFloat(setting.key, defaultVal)
                        is Int -> editor.putInt(setting.key, defaultVal)
                        is Long -> editor.putLong(setting.key, defaultVal)
                        is String -> editor.putString(setting.key, defaultVal)
                    }
                }
            }
            editor.apply()

            loadInstalledPlugins()
            return@withContext Result.success(manifest)
        } catch (e: Exception) {
            Log.e(TAG, "Install plugin failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    fun applyBeforeCaptureHooks(
        currentIso: Int,
        currentShutterSpeedNanos: Long,
        currentEv: Int
    ): CaptureParamsHookResult {
        var result = CaptureParamsHookResult(currentIso, currentShutterSpeedNanos, currentEv)
        val activePlugins = _installedPlugins.value.filter { it.isEnabled }
        for (plugin in activePlugins) {
            val userSettings = getPluginSettings(plugin.manifest.id)
            val hookRes = scriptEngine.executeBeforeCapture(
                instance = plugin,
                userSettings = userSettings,
                currentIso = result.iso ?: currentIso,
                currentShutterSpeedNanos = result.shutterSpeedNanos ?: currentShutterSpeedNanos,
                currentEv = result.exposureCompensation ?: currentEv
            )
            if (hookRes.iso != null) result.iso = hookRes.iso
            if (hookRes.shutterSpeedNanos != null) result.shutterSpeedNanos = hookRes.shutterSpeedNanos
            if (hookRes.exposureCompensation != null) result.exposureCompensation = hookRes.exposureCompensation
            if (hookRes.customTag != null) result.customTag = hookRes.customTag
        }
        return result
    }

    fun isColorEngineOverridden(): String? {
        val active = _installedPlugins.value.filter { it.isEnabled }
        for (plugin in active) {
            val hasExplicit = plugin.manifest.overrides.any { it.equals("COLOR_ENGINE", ignoreCase = true) || it.equals("COLOR", ignoreCase = true) }
            val hasHook = plugin.scriptCode.contains("onProcessPhoto")
            if (hasExplicit || hasHook) {
                return plugin.manifest.name
            }
        }
        return null
    }

    fun isFeatureOverridden(featureKey: String): String? {
        val active = _installedPlugins.value.filter { it.isEnabled }
        for (plugin in active) {
            if (plugin.manifest.overrides.any { it.equals(featureKey, ignoreCase = true) }) {
                return plugin.manifest.name
            }
        }
        return null
    }

    fun hasActivePhotoProcessPlugin(): Boolean {
        return _installedPlugins.value.any { it.isEnabled && it.scriptCode.contains("onProcessPhoto") }
    }

    fun applyProcessPhotoHooks(
        avgR: Float,
        avgG: Float,
        avgB: Float,
        avgSatPercent: Float,
        maxClipPercent: Float
    ): Map<String, Any> {
        val merged = mutableMapOf<String, Any>()
        val activePlugins = _installedPlugins.value.filter { it.isEnabled }
        for (plugin in activePlugins) {
            val userSettings = getPluginSettings(plugin.manifest.id)
            val res = scriptEngine.executeProcessPhotoHook(
                instance = plugin,
                userSettings = userSettings,
                avgR = avgR,
                avgG = avgG,
                avgB = avgB,
                avgSatPercent = avgSatPercent,
                maxClipPercent = maxClipPercent
            )
            merged.putAll(res)
        }
        return merged
    }

    private fun findFolderWithPluginJson(dir: File): File? {
        if (File(dir, "plugin.json").exists()) return dir
        val subs = dir.listFiles() ?: return null
        for (sub in subs) {
            if (sub.isDirectory) {
                if (File(sub, "plugin.json").exists()) return sub
            }
        }
        return null
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
