package com.dark.download_manager

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * HXD ForegroundService.
 *
 * Lifecycle:
 *  - Started by [HxdManager.enqueue] or [HxdManager.resume] via startForegroundService().
 *  - Stops itself when the pending queue is drained and all slots are idle.
 *  - START_STICKY: Android restarts it after process death; [HxdManager.restoreQueue] re-populates the queue.
 *
 * Concurrency: Semaphore(3) limits concurrent downloads.
 * Each active download runs in a separate child coroutine on Dispatchers.IO.
 */
class HxdService : Service() {

    private val scope               = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore           = Semaphore(MAX_CONCURRENT)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val queueFile           by lazy  { File(filesDir, QUEUE_FILE) }

    companion object {
        private const val MAX_CONCURRENT = 3
        private const val QUEUE_FILE     = "hxd_queue.bin"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        HxdNotification.createChannel(this)
        startForeground(HxdNotification.NOTIFICATION_ID, HxdNotification.build(this, emptyList()))

        // Restore queue from previous session (handles process-death restarts)
        HxdManager.restoreQueue(this, queueFile)

        launchNotificationUpdater()
        launchDispatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        HxdManager.saveQueue(queueFile)
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification updater ──────────────────────────────────────────────────

    private fun launchNotificationUpdater() {
        scope.launch {
            HxdManager.tasks.collect { states ->
                val active = states.filter {
                    it.status == HxdStatus.DOWNLOADING || it.status == HxdStatus.CONNECTING
                }
                notificationManager.notify(
                    HxdNotification.NOTIFICATION_ID,
                    HxdNotification.build(this@HxdService, active)
                )
            }
        }
    }

    // ── Task dispatcher ───────────────────────────────────────────────────────

    private fun launchDispatcher() {
        scope.launch {
            while (isActive) {
                val task = synchronized(HxdManager.lock) {
                    HxdManager.pendingQueue.removeFirstOrNull()
                }

                if (task == null) {
                    delay(300)
                    // Stop service if nothing pending and all slots free
                    if (semaphore.availablePermits == MAX_CONCURRENT &&
                        synchronized(HxdManager.lock) { HxdManager.pendingQueue.isEmpty() }) {
                        HxdManager.saveQueue(queueFile)
                        stopSelf()
                    }
                    continue
                }

                semaphore.acquire()
                launch {
                    try { executeDownload(task) }
                    finally { semaphore.release() }
                }
            }
        }
    }

    // ── Download executor ─────────────────────────────────────────────────────

    private suspend fun executeDownload(task: HxdTask) = withContext(Dispatchers.IO) {
        val id = task.id
        HxdManager.updateStatus(id, HxdStatus.CONNECTING)

        // Prepare temp file — C++ returns resume offset (size of existing partial)
        val resumeOffset = HxdNative.nativePrepare(id, task.destPath)
        if (resumeOffset < 0) {
            HxdManager.updateStatus(id, HxdStatus.FAILED, "Cannot create destination file")
            return@withContext
        }

        val digest: MessageDigest? = task.options.expectedSha256
            ?.let { MessageDigest.getInstance("SHA-256") }

        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(task, resumeOffset)
            val code = conn.responseCode

            if (code != 200 && code != 206) {
                HxdNative.nativeFail(id)
                HxdManager.updateStatus(id, HxdStatus.FAILED, "HTTP $code")
                return@withContext
            }

            // Tell C++ the total expected bytes (for progress % calculation)
            val contentLen = conn.contentLengthLong
            if (contentLen > 0) {
                val total = if (code == 206) resumeOffset + contentLen else contentLen
                HxdNative.nativeSetTotal(id, total)
            }

            HxdManager.updateStatus(id, HxdStatus.DOWNLOADING)

            val buf = ByteArray(65_536)  // 64 KB read buffer
            var aborted = false

            conn.inputStream.use { stream ->
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    digest?.update(buf, 0, n)
                    if (!HxdNative.nativeWriteChunk(id, buf, 0, n)) {
                        aborted = true
                        break
                    }
                    HxdManager.updateProgress(id)
                }
            }

            if (aborted) {
                // Distinguish pause vs cancel via the state C++ set
                val prog   = HxdNative.nativeGetProgress(id)
                val status = HxdStatus.entries.getOrNull(prog[3].toInt()) ?: HxdStatus.FAILED

                HxdNative.nativeCleanup(id)  // close fd, keep temp file

                if (status == HxdStatus.CANCELLED) {
                    // User cancelled — delete temp file
                    File(task.destPath + ".hxd_tmp").delete()
                    HxdManager.updateStatus(id, HxdStatus.CANCELLED)
                } else {
                    // Paused — re-queue so resume() can dispatch it again
                    HxdManager.updateStatus(id, HxdStatus.PAUSED)
                }
                return@withContext
            }

            // Optional SHA-256 verification
            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(task.options.expectedSha256, ignoreCase = true)) {
                    HxdNative.nativeFail(id)
                    HxdManager.updateStatus(id, HxdStatus.FAILED, "Checksum mismatch")
                    return@withContext
                }
            }

            // fsync + rename temp → final destination
            if (HxdNative.nativeComplete(id)) {
                HxdManager.updateStatus(id, HxdStatus.COMPLETED)
            } else {
                HxdManager.updateStatus(id, HxdStatus.FAILED, "Failed to finalize file")
            }

        } catch (e: Exception) {
            HxdNative.nativeFail(id)
            HxdManager.updateStatus(id, HxdStatus.FAILED, e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun openConnection(task: HxdTask, resumeOffset: Long): HttpURLConnection {
        return (URL(task.url).openConnection() as HttpURLConnection).apply {
            connectTimeout      = 30_000
            readTimeout         = 30_000
            instanceFollowRedirects = true
            // Privacy: no fingerprint unless caller explicitly sets one
            setRequestProperty("User-Agent", task.options.userAgent)
            // Disable cookie sharing with system CookieManager
            setRequestProperty("Cookie", "")
            // Resume support
            if (resumeOffset > 0) {
                setRequestProperty("Range", "bytes=$resumeOffset-")
            }
            // Caller-provided headers (e.g. Authorization for gated model repos)
            task.options.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
    }
}
