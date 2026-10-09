package com.proyecto.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

/**
 * Auto-actualización vía GitHub Releases (repo público de APKs del proyecto):
 * 1. consulta la última release publicada (tag = v{versionCode})
 * 2. si es mayor que la instalada, la UI muestra "Actualizar"
 * 3. al tocar: descarga el APK y abre el instalador nativo de Android
 */
class AppUpdater(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val releasesRepo = "jfelipeholguin/proyecto-releases"
    private val latestApi = "https://api.github.com/repos/$releasesRepo/releases/latest"

    fun installedVersionCode(): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (_: Exception) {
        0
    }

    /** Consulta la última release pública. null si no hay release o falla la red. */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(latestApi).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val release = json.decodeFromString<GitHubRelease>(body)
                val code = release.tagName.removePrefix("v").toIntOrNull() ?: return@withContext null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null
                ReleaseInfo(
                    versionCode = code,
                    versionName = release.name.ifBlank { release.tagName },
                    apkUrl = apk.browserDownloadUrl,
                )
            }
        }.getOrNull()
    }

    /** Descarga el APK y lanza el instalador del sistema. */
    suspend fun downloadAndInstall(info: ReleaseInfo): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(info.apkUrl).build()
            val bytes = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.bytes() ?: return@withContext false
            }

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "proyecto-${info.versionCode}.apk")
            apk.writeBytes(bytes)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
