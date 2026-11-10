package app.hoyu.shot.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import app.hoyu.shot.MainActivity
import app.hoyu.shot.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val isPreRelease: Boolean
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data class LatestVersion(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateManager(private val context: Context) {

    private val TAG = "UpdateManager"
    private val REPO_OWNER = "hoyuuna"
    private val REPO_NAME = "HoyuShot_2"
    private val CHANNEL_ID = "hoyushot_update_channel"

    val currentVersionName: String
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                pInfo.versionName ?: "0.0.1"
            } catch (e: Exception) {
                "0.0.1"
            }
        }

    suspend fun checkForUpdates(includePreRelease: Boolean = true): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = currentVersionName

        // 1. Primary Source: GitHub REST API
        try {
            val apiUrl = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "HoyuShot-Android-App")
            }

            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(jsonString)

                for (i in 0 until releases.length()) {
                    val rel = releases.getJSONObject(i)
                    val isDraft = rel.optBoolean("draft", false)
                    val isPrerelease = rel.optBoolean("prerelease", false)

                    if (isDraft) continue
                    if (isPrerelease && !includePreRelease) continue

                    val tagName = rel.optString("tag_name", "")
                    val cleanVersion = tagName.removePrefix("v").removePrefix("V")
                    val body = rel.optString("body", "Bug fixes and performance improvements.")

                    var downloadUrl = ""
                    var fileSize = 0L

                    val assets = rel.optJSONArray("assets")
                    if (assets != null) {
                        for (a in 0 until assets.length()) {
                            val asset = assets.getJSONObject(a)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                fileSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    if (downloadUrl.isEmpty()) {
                        // Fallback convention URL
                        downloadUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/HoyuShot-$tagName.apk"
                    }

                    if (isNewerVersion(cleanVersion, currentVersion)) {
                        return@withContext UpdateCheckResult.UpdateAvailable(
                            UpdateInfo(
                                tagName = tagName,
                                versionName = cleanVersion,
                                changelog = body,
                                downloadUrl = downloadUrl,
                                fileSizeBytes = fileSize,
                                isPreRelease = isPrerelease
                            )
                        )
                    } else {
                        return@withContext UpdateCheckResult.LatestVersion(currentVersion)
                    }
                }
                if (releases.length() > 0) {
                    return@withContext UpdateCheckResult.LatestVersion(currentVersion)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub REST API failed, attempting Atom fallback: ${e.message}")
        }

        // 2. Fallback: GitHub Atom Feed
        try {
            val atomUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases.atom"
            val conn = (URL(atomUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "HoyuShot-Android-App")
            }

            if (conn.responseCode == 200) {
                val xmlString = conn.inputStream.bufferedReader().use { it.readText() }
                // Match e.g. <id>tag:github.com,2008:Repository/123456/v0.0.23</id>
                val pattern = Pattern.compile("(?i)<id>.*?(v[0-9]+(?:\\.[0-9]+)+).*?</id>")
                val matcher = pattern.matcher(xmlString)
                if (matcher.find()) {
                    val rawTag = matcher.group(1) ?: ""
                    val cleanVersion = rawTag.removePrefix("v").removePrefix("V")
                    if (isNewerVersion(cleanVersion, currentVersion)) {
                        val directApkUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$rawTag/HoyuShot-$rawTag.apk"
                        return@withContext UpdateCheckResult.UpdateAvailable(
                            UpdateInfo(
                                tagName = rawTag,
                                versionName = cleanVersion,
                                changelog = "Version $rawTag release.",
                                downloadUrl = directApkUrl,
                                fileSizeBytes = 0L,
                                isPreRelease = false
                            )
                        )
                    } else {
                        return@withContext UpdateCheckResult.LatestVersion(currentVersion)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Atom fallback also failed: ${e.message}")
        }

        UpdateCheckResult.Error("Cannot connect to GitHub releases server.")
    }

    /**
     * Compares remote and local semantic version numbers [major, minor, patch]
     * Returns true only when remote is strictly greater than local.
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val rClean = remote.trim().removePrefix("v").removePrefix("V").split("-")[0]
            val lClean = local.trim().removePrefix("v").removePrefix("V").split("-")[0]
            val rParts = rClean.split(".").map { it.toIntOrNull() ?: 0 }
            val lParts = lClean.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
        }
        return false
    }

    /**
     * Downloads APK stream to app-specific storage with continuous progress callback
     */
    suspend fun downloadApk(
        downloadUrl: String,
        versionTag: String,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!targetDir.exists()) targetDir.mkdirs()
            val apkFile = File(targetDir, "HoyuShot-$versionTag.apk")
            if (apkFile.exists()) apkFile.delete()

            var urlStr = downloadUrl
            // Follow up to 5 redirects for GitHub asset download
            var redirects = 0
            while (redirects < 5) {
                val url = URL(urlStr)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "HoyuShot-Android-App")
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val loc = connection.getHeaderField("Location") ?: break
                    urlStr = loc
                    redirects++
                } else {
                    break
                }
            }

            val totalBytes = connection?.contentLength?.toLong() ?: -1L
            input = connection?.inputStream ?: return@withContext null
            output = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead = 0L
            var readLen: Int

            while (input.read(buffer).also { readLen = it } != -1) {
                output.write(buffer, 0, readLen)
                bytesRead += readLen
                val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
                onProgress(bytesRead, totalBytes, percent)
            }

            output.flush()
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            null
        } finally {
            try { output?.close() } catch (e: Exception) {}
            try { input?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    /**
     * Launches Android Package Installer using FileProvider content:// URI
     */
    fun installApk(apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer", e)
        }
    }

    /**
     * Posts a system notification when auto-check detects a new release
     */
    fun showUpdateNotification(info: UpdateInfo) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HoyuShot Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new HoyuShot version updates"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification to immediately download latest APK via browser / system downloader
        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .apply {
                if (largeIconBitmap != null) setLargeIcon(largeIconBitmap)
            }
            .setContentTitle("HoyuShot ${info.tagName} Available")
            .setContentText("Tap to download update ${info.tagName}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Version ${info.tagName} is ready:\n${info.changelog}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            notificationManager.notify(1001, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
    }
}
