package com.karomi.downloader.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karomi.downloader.KaromiApp
import com.karomi.downloader.R
import com.karomi.downloader.data.DownloadEntity
import com.karomi.downloader.extractor.AudioExtractor
import com.karomi.downloader.ui.MainActivity
import com.karomi.downloader.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * خدمة أمامية (Foreground Service) للتحميل في الخلفية مع إشعار تقدم مباشر،
 * ودعم الإيقاف المؤقت والاستئناف والإلغاء لكل مهمة.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val downloaders = ConcurrentHashMap<Long, MultiThreadDownloader>()

    private val dao by lazy { (application as KaromiApp).database.downloads() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownload(
                id = intent.getLongExtra(EXTRA_ID, -1),
                url = intent.getStringExtra(EXTRA_URL).orEmpty(),
                dest = intent.getStringExtra(EXTRA_DEST).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                convertToMp3 = intent.getBooleanExtra(EXTRA_TO_MP3, false),
                convertToM4a = intent.getBooleanExtra(EXTRA_TO_M4A, false)
            )
            ACTION_PAUSE -> pauseDownload(intent.getLongExtra(EXTRA_ID, -1))
            ACTION_CANCEL -> cancelDownload(intent.getLongExtra(EXTRA_ID, -1))
        }
        return START_STICKY
    }

    private fun startDownload(
        id: Long, url: String, dest: String, title: String,
        convertToMp3: Boolean, convertToM4a: Boolean
    ) {
        if (id < 0 || jobs.containsKey(id)) return

        val downloader = MultiThreadDownloader(client)
        downloaders[id] = downloader
        startForegroundWithNotification(id, title, 0, -1)

        val job = scope.launch(Dispatchers.IO) {
            dao.updateProgress(id, DownloadEntity.STATUS_DOWNLOADING, 0, 0)
            try {
                downloader.download(url, File(dest), object : MultiThreadDownloader.Listener {
                    override fun onProgress(downloaded: Long, total: Long) {
                        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                        updateProgressNotification(id, title, percent, downloaded, total)
                    }

                    override fun onError(error: Exception) = Unit
                })

                var finalPath = dest
                // استخراج الصوت من الفيديو بعد اكتمال التحميل
                if (convertToM4a || convertToMp3) {
                    notifyText(id, title, getString(R.string.extracting_audio))
                    finalPath = AudioExtractor.extractToM4a(dest)
                    if (convertToMp3) {
                        // ملاحظة: التحويل إلى MP3 يتطلب دمج FFmpeg (راجع AudioExtractor)
                        finalPath = AudioExtractor.convertToMp3(finalPath)
                    }
                    File(dest).delete()
                }

                val size = File(finalPath).length()
                dao.updateProgress(id, DownloadEntity.STATUS_COMPLETED, size, size)
                dao.byId(id)?.let { dao.update(it.copy(filePath = finalPath)) }
                notifyDone(id, title, true)
            } catch (e: MultiThreadDownloader.PausedException) {
                val entity = dao.byId(id)
                dao.updateProgress(
                    id, DownloadEntity.STATUS_PAUSED,
                    entity?.downloadedBytes ?: 0, entity?.totalBytes ?: 0
                )
                notifyText(id, title, getString(R.string.download_paused))
            } catch (e: Exception) {
                dao.updateProgress(id, DownloadEntity.STATUS_FAILED, 0, 0)
                notifyDone(id, title, false)
            } finally {
                jobs.remove(id)
                downloaders.remove(id)
                if (jobs.isEmpty()) stopSelf()
            }
        }
        jobs[id] = job
    }

    private fun pauseDownload(id: Long) {
        downloaders[id]?.pause()
    }

    private fun cancelDownload(id: Long) {
        downloaders[id]?.pause()
        jobs[id]?.cancel()
        jobs.remove(id)
        downloaders.remove(id)
        scope.launch(Dispatchers.IO) { dao.byId(id)?.let { dao.delete(it) } }
        NotificationManagerCompat.from(this).cancel(notificationId(id))
        if (jobs.isEmpty()) stopSelf()
    }

    // ---------- الإشعارات ----------

    private fun baseNotification(title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, KaromiApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())

    private fun startForegroundWithNotification(id: Long, title: String, percent: Int, total: Long) {
        val notification = baseNotification(title)
            .setProgress(100, percent, total <= 0)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                notificationId(id), notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId(id), notification)
        }
    }

    private fun updateProgressNotification(id: Long, title: String, percent: Int, done: Long, total: Long) {
        val text = if (total > 0)
            "${FormatUtils.size(done)} / ${FormatUtils.size(total)} ($percent%)"
        else FormatUtils.size(done)

        val pauseIntent = PendingIntent.getService(
            this, id.toInt(),
            Intent(this, DownloadService::class.java)
                .setAction(ACTION_PAUSE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = baseNotification(title)
            .setContentText(text)
            .setProgress(100, percent, total <= 0)
            .addAction(R.drawable.ic_pause, getString(R.string.pause), pauseIntent)
            .build()
        NotificationManagerCompat.from(this).notify(notificationId(id), notification)
    }

    private fun notifyText(id: Long, title: String, text: String) {
        val notification = baseNotification(title)
            .setContentText(text)
            .setProgress(0, 0, false)
            .build()
        NotificationManagerCompat.from(this).notify(notificationId(id), notification)
    }

    private fun notifyDone(id: Long, title: String, success: Boolean) {
        val text = getString(if (success) R.string.download_complete else R.string.download_failed)
        val notification: Notification = baseNotification(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        NotificationManagerCompat.from(this).notify(notificationId(id), notification)
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationId(id: Long) = 1000 + id.toInt()

    companion object {
        const val ACTION_START = "com.karomi.downloader.action.START"
        const val ACTION_PAUSE = "com.karomi.downloader.action.PAUSE"
        const val ACTION_CANCEL = "com.karomi.downloader.action.CANCEL"
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"
        const val EXTRA_DEST = "dest"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TO_MP3 = "to_mp3"
        const val EXTRA_TO_M4A = "to_m4a"

        fun start(
            context: Context, id: Long, url: String, dest: String, title: String,
            toMp3: Boolean = false, toM4a: Boolean = false
        ) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_DEST, dest)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TO_MP3, toMp3)
                .putExtra(EXTRA_TO_M4A, toM4a)
            context.startForegroundService(intent)
        }

        fun pause(context: Context, id: Long) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_PAUSE).putExtra(EXTRA_ID, id)
            )
        }
    }
}
