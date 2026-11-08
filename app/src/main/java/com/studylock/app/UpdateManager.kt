package com.studylock.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Checks the project's GitHub releases and installs a newer APK when one exists. */
object UpdateManager {
    private const val LATEST_RELEASE = "https://api.github.com/repos/adamahmadfathy/StudyLock/releases/latest"
    private const val PREFS = "studylock_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    var status by mutableStateOf("")
        private set

    private val running = AtomicBoolean(false)

    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    private fun versionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun setStatus(message: String) { status = message }

    /**
     * Starts a background check. [force] ignores the 6-hour spacing. [silentOnly] is used by the
     * background service: it only installs where Android 12+ allows an install without a prompt.
     */
    fun check(context: Context, force: Boolean, silentOnly: Boolean) {
        val app = context.applicationContext
        if (silentOnly && Build.VERSION.SDK_INT < 31) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                run(app)
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                status = "Could not check for updates."
            } finally {
                running.set(false)
            }
        }.start()
    }

    private fun run(app: Context) {
        status = "Checking for updates..."
        val connection = open(LATEST_RELEASE)
        val code = connection.responseCode
        if (code == 404) { status = "Updates unavailable: the release page is private."; return }
        if (code != 200) { status = "Could not check for updates (code $code)."; return }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val name = json.optString("name")
        val latest = Regex("(\\d+)\\s*$").find(name)?.value?.toLongOrNull()
        if (latest == null) { status = "Up to date."; return }
        if (latest <= versionCode(app)) { status = "Up to date."; return }

        val assets = json.optJSONArray("assets")
        var url: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) { url = asset.optString("browser_download_url"); break }
            }
        }
        if (url.isNullOrEmpty()) { status = "Update found, but no APK is attached."; return }
        if (!app.packageManager.canRequestPackageInstalls()) {
            status = "Update available. Allow StudyLock to install updates, then check again."
            return
        }

        status = "Downloading update..."
        val file = File(app.cacheDir, "update.apk")
        val download = open(url)
        if (download.responseCode != 200) { status = "Download failed (code ${download.responseCode})."; return }
        download.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }

        status = "Installing update..."
        install(app, file)
    }

    private fun open(address: String): HttpURLConnection {
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "StudyLock")
        return c
    }

    private fun install(app: Context, file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            file.inputStream().use { input ->
                session.openWrite("StudyLock.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = PendingIntent.getBroadcast(
                app, id, Intent(app, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
        } finally {
            session.close()
        }
    }
}
