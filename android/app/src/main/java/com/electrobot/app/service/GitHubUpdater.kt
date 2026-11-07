package com.electrobot.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val isLatest: Boolean = false,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkFileName: String,
    val publishedAt: String
)

object GitHubUpdater {

    const val CURRENT_VERSION = "v1.7"
    private const val GITHUB_REPO = "drkoh/ElectroBot"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "ElectroBot-Android")
            conn.setRequestProperty("Cache-Control", "no-cache")

            val code = conn.responseCode
            if (code == 404) {
                return@withContext Result.success(
                    UpdateInfo(
                        hasUpdate = false,
                        isLatest = true,
                        currentVersion = CURRENT_VERSION,
                        latestVersion = CURRENT_VERSION,
                        releaseTitle = "Оновлень не знайдено",
                        releaseNotes = "У вас встановлена остання версія.",
                        downloadUrl = "",
                        apkFileName = "",
                        publishedAt = ""
                    )
                )
            }

            if (code !in 200..299) {
                return@withContext Result.failure(Exception("Помилка сервера GitHub: HTTP $code"))
            }

            val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            conn.disconnect()

            val json = JSONObject(resp)
            val tagName = json.optString("tag_name", "").trim()
            val title = json.optString("name", tagName)
            val body = json.optString("body", "Опис релізу відсутній")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = ""
            var apkName = "ElectroBot-v1.1-mobile.apk"

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(CURRENT_VERSION, tagName)

            Result.success(
                UpdateInfo(
                    hasUpdate = isNewer && downloadUrl.isNotEmpty(),
                    isLatest = !isNewer,
                    currentVersion = CURRENT_VERSION,
                    latestVersion = tagName.ifEmpty { CURRENT_VERSION },
                    releaseTitle = title,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    apkFileName = apkName,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(current: String, remote: String): Boolean {
        if (remote.isEmpty()) return false
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()
        val cleanRemote = remote.removePrefix("v").removePrefix("V").trim()
        if (cleanCurrent == cleanRemote) return false

        val curParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }
        val remParts = cleanRemote.split(".", "-").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(curParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.cacheDir, "updates")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)

            var currentUrl = downloadUrl
            var conn: HttpURLConnection

            var redirects = 0
            while (true) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "ElectroBot-Android")
                conn.setRequestProperty("Accept", "application/octet-stream")
                conn.connect()

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location != null && redirects < 5) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext Result.failure(Exception("Помилка завантаження файлу: HTTP $code"))
                }
                break
            }

            val fileLength = conn.contentLength

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()

            withContext(Dispatchers.Main) {
                installApk(context, targetFile)
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
