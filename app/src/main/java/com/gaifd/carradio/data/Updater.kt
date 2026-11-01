package com.gaifd.carradio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gaifd.carradio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val apkUrl: String, val notes: String, val size: Long)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/** Новые версии берутся из GitHub Releases репозитория, куда их выкладывает сборка. */
object Updater {
    private const val API = "https://api.github.com/repos/GaifD/car-radio/releases/latest"
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        try {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", USER_AGENT)
            val code = c.responseCode
            if (code == 404) {
                _state.value = UpdateState.UpToDate
                return@withContext null
            }
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            val ver = o.optString("tag_name").removePrefix("v")
            val assets = o.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (url.isBlank() || !isNewer(ver, BuildConfig.VERSION_NAME)) {
                _state.value = UpdateState.UpToDate
                return@withContext null
            }
            val info = UpdateInfo(ver, url, o.optString("body").take(1500), size)
            _state.value = UpdateState.Available(info)
            info
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не удалось проверить: нет связи с GitHub")
            null
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        try {
            _state.value = UpdateState.Downloading(info, 0f)
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "car-radio-${info.version}.apk")
            val c = URL(info.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastShown > 200_000) {
                            lastShown = done
                            _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            c.disconnect()
            _state.value = UpdateState.Ready(info, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не удалось скачать обновление")
        }
    }

    /** Открывает системную установку. Первый раз Android попросит разрешить установку из «Авторадио». */
    fun install(ctx: Context, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
