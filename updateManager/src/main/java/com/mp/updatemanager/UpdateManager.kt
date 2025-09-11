package com.mp.updatemanager


import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * UpdateCenter – single-file, robust updater with:
 * • JSON version check
 * • DownloadManager download + progress notification
 * • Install flow via Intent (REQUEST_INSTALL_PACKAGES handled)
 *
 * Usage:
 *   UpdateCenter.init(appContext)
 *   UpdateCenter.checkAndMaybeUpdate(force = false)
 */
@SuppressLint("StaticFieldLeak")
object UpdateCenter {


    // ======= CONFIG =======
    private val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/fresh-new/repo/AppUpdate.json?ts=${System.currentTimeMillis()}"

    private const val NOTI_CHANNEL_ID = "updates.channel"
    private const val NOTI_CHANNEL_NAME = "App Updates"
    private const val NOTI_ID_PROGRESS = 4201
    private const val SP_NAME = "update_center_prefs"
    private const val SP_KEY_DOWNLOAD_ID = "dm_download_id"
    private const val SP_KEY_LATEST_VERSION = "latest_version"

    // ======= STATE =======
    private lateinit var app: Context
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        app = context.applicationContext
        ensureChannel()
        val existingId = sp().getLong(SP_KEY_DOWNLOAD_ID, -1L)
        if (existingId > 0L) {
            Log.i("UpdateCenter", "Resuming watch on downloadId=$existingId")
            watchDownload(existingId)
        }
    }

    @MainThread
    fun checkAndMaybeUpdate(force: Boolean = false) {
        require(::app.isInitialized) { "Call UpdateCenter.init(context) first." }
        ioScope.launch {
            val info = fetchUpdateJson() ?: return@launch
            Log.i("UpdateCenter", "Fetched UpdateInfo=$info")

            val installed = getInstalledVersionName()
            Log.i("UpdateCenter", "Installed version=$installed")

            if (info.hasUpdate) {
                val shouldUpdate = force || info.version != installed
                if (!shouldUpdate) {
                    Log.i("UpdateCenter", "Update skipped: remote=${info.version}, installed=$installed")
                    return@launch
                }

                Log.i("UpdateCenter", "Update accepted: downloading ${info.version}")
                sp().edit().putString(SP_KEY_LATEST_VERSION, info.version).apply()

                val dmId = enqueueDownload(info.updateLink, info.version, info.whatsNew)
                sp().edit().putLong(SP_KEY_DOWNLOAD_ID, dmId).apply()
                watchDownload(dmId)
            } else {
                Log.i("UpdateCenter", "No update available (hasUpdate=false)")
            }
        }
    }

    // ======= JSON parse =======
    private data class UpdateInfo(
        val hasUpdate: Boolean,
        val version: String,
        val updateLink: String,
        val whatsNew: String
    )

    private fun fetchUpdateJson(): UpdateInfo? {
        val req = Request.Builder().url(UPDATE_JSON_URL).get().build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("UpdateCenter", "Failed fetching JSON: HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                Log.d("UpdateCenter", "Raw JSON: $body")

                val json = JSONObject(body)
                UpdateInfo(
                    hasUpdate = json.optBoolean("hasUpdate", false),
                    version = json.optString("version", ""),
                    updateLink = json.optString("updateLink", ""),
                    whatsNew = json.optJSONArray("whatsNew")?.join(",") ?: ""
                ).takeIf { it.updateLink.isNotBlank() && it.version.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e("UpdateCenter", "Error fetching JSON", e)
            null
        }
    }

    // ======= Download =======
    private fun enqueueDownload(url: String, version: String, whatsNew: String): Long {
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = url.toUri()
        val req = DownloadManager.Request(uri)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "app-$version.apk"
            )
            .setMimeType("application/vnd.android.package-archive")
            .setTitle("Downloading update $version")
            .setDescription(if (whatsNew.isBlank()) "App update" else whatsNew.take(120))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setVisibleInDownloadsUi(true)

        showProgress(0, "Preparing download…")
        return dm.enqueue(req).also {
            Log.i("UpdateCenter", "Enqueued download: id=$it, version=$version")
        }
    }

    private fun watchDownload(downloadId: Long) {
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        ioScope.launch {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var lastProgress = -1

            while (true) {
                delay(750)
                val cursor: Cursor = dm.query(query) ?: continue
                cursor.use {
                    if (!it.moveToFirst()) return@use
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))

                    when (status) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                            val progress = if (total > 0) ((soFar * 100L) / total).toInt() else 0
                            if (progress != lastProgress) {
                                lastProgress = progress
                                Log.d("UpdateCenter", "Download progress: $progress%")
                                showProgress(progress, "Downloading update… $progress%")
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.i("UpdateCenter", "Download successful for id=$downloadId")
                            showComplete()
                            sp().edit().remove(SP_KEY_DOWNLOAD_ID).apply()
                            val apkUri = dm.getUriForDownloadedFile(downloadId)
                            if (apkUri != null) {
                                promptInstall(apkUri)
                            } else {
                                showError("Downloaded, but file not found.")
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.e("UpdateCenter", "Download failed (code=$reason)")
                            sp().edit().remove(SP_KEY_DOWNLOAD_ID).apply()
                            showError("Download failed (code $reason). Tap to retry.")
                            return@launch
                        }
                    }
                }
            }
        }
    }
    // ======= Install =======
    private fun promptInstall(apkUri: Uri) {
        // For Android 8.0+ you might need unknown sources permission for your app
        val canInstall = app.packageManager.canRequestPackageInstalls()
        if (!canInstall) {
            // direct user to allow from settings
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            showError("Allow install from unknown sources and retry.")
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            app.startActivity(installIntent)
        } catch (e: Exception) {
            showError("Cannot open installer: ${e.localizedMessage}")
        }
    }

    // ======= Version compare =======
    private fun getInstalledVersionName(): String {
        return try {
            val p = app.packageManager.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toString() else p.versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
    }

    // ======= Notifications =======
    private fun ensureChannel() {
        val mgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            NOTI_CHANNEL_ID, NOTI_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        )
        ch.description = "Shows app update progress and results"
        mgr.createNotificationChannel(ch)
    }

    private fun notifyBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(app, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOnlyAlertOnce(true)
            .setColor(ContextCompat.getColor(app, android.R.color.holo_blue_light)).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun showProgress(percent: Int, text: String) {
        val mgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = notifyBuilder().setContentTitle("App update").setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0).build()
        mgr.notify(NOTI_ID_PROGRESS, n)
    }

    private fun showComplete() {
        val mgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(app, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update downloaded")
            .setContentText("Tap notification from installer to proceed.").setAutoCancel(true)
            .build()
        mgr.notify(NOTI_ID_PROGRESS, n)
    }

    private fun showError(message: String) {
        val mgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(app, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Update error")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true).build()
        mgr.notify(NOTI_ID_PROGRESS + 1, n)
    }

    // ======= Utils =======
    private fun sp(): SharedPreferences = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
}
