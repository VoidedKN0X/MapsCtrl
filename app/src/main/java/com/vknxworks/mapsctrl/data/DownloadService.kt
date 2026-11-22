package com.vknxworks.mapsctrl.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.vknxworks.mapsctrl.MainActivity
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A short "data sync" foreground service with a "Downloading maps" notification while a map, search, routing or
 * terrain download runs, so Android doesn't freeze or stop MapsCtrl when you leave the app or the screen turns
 * off. It holds no logic of its own: the downloads run in their repositories as before, and the service only
 * shows their progress and stops when the last one ends (see [AppContainer] for when it starts and stops).
 */
class DownloadService : Service() {
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(ID, notification(this, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            running = true
        } catch (_: Exception) {
            // Android refused (for example from the background): the download goes on as before, without it.
            stopSelf()
            return START_NOT_STICKY
        }
        if (scope == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope = s
            s.launch { showProgress() }
        }
        return START_NOT_STICKY
    }

    /** Keeps the notification's progress bar up to date, at most once a second. */
    private suspend fun showProgress() {
        val c = container
        val nm = getSystemService(NotificationManager::class.java)
        combine(c.maps.downloads, c.segments.progress, c.terrain.job) { maps, segments, terrain ->
            DownloadProgress.percent(maps.values, segments.values, terrain)
        }.collectLatest { pct ->
            runCatching { nm.notify(ID, notification(this, pct)) }
            delay(1_000)
        }
    }

    /** Android 15+: a data sync service may run for 6 hours a day. When that's used up, it stops. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 2

        @Volatile private var running = false

        /** Starts the service, unless it's already in the foreground. Called while you start a download. */
        fun start(context: Context) {
            if (running) return
            runCatching { context.startForegroundService(Intent(context, DownloadService::class.java)) }
        }

        fun stop(context: Context) {
            running = false
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }

        private fun notification(context: Context, percent: Int?): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_downloads_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_maps)
                .setContentTitle(context.getString(R.string.notif_downloading))
                .apply { if (percent != null) setContentText(context.getString(R.string.notif_downloading_percent, percent)) }
                .setProgress(100, percent ?: 0, percent == null)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build()
        }
    }
}

/** The download notification's progress; pure, unit-tested. */
object DownloadProgress {
    /**
     * Percent done: over the bytes of every map, search-data and routing-data download with a known size; when
     * only terrain data is downloading, over its tiles. Null when nothing has a size yet.
     */
    fun percent(maps: Collection<DownloadState>, segments: Collection<Pair<Long, Long>>, terrain: TerrainJob?): Int? {
        var done = 0L
        var total = 0L
        for (m in maps) if (m is DownloadState.Running && m.total > 0) { done += m.done; total += m.total }
        for ((d, t) in segments) if (t > 0) { done += d; total += t }
        if (total <= 0 && terrain is TerrainJob.Running && terrain.total > 0) {
            done = terrain.done.toLong()
            total = terrain.total.toLong()
        }
        if (total <= 0) return null
        return ((done.coerceIn(0, total) * 100) / total).toInt()
    }
}
