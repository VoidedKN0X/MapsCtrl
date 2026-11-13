package com.vknxworks.mapsctrl.nav

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

/**
 * A foreground service with one notification while navigating or recording a track, so Android keeps
 * delivering location updates with the screen off. It holds no logic of its own.
 */
class NavigationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val c = container
        val text = when {
            c.navigation.state.value != null -> getString(R.string.notif_navigating)
            else -> getString(R.string.notif_recording)
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_maps)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .build()
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: Exception) {
            // Without the location permission Android refuses a location service: navigation still works while visible.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "navigation"

        fun start(context: Context) {
            if (!context.container.location.hasPermission()) return
            runCatching { context.startForegroundService(Intent(context, NavigationService::class.java)) }
        }

        /** Stops unless something else (navigation or recording) still needs it. */
        fun stop(context: Context) {
            val c = context.container
            if (c.navigation.state.value != null || c.recorder.recording.value) {
                start(context) // refresh the text
                return
            }
            context.stopService(Intent(context, NavigationService::class.java))
        }
    }
}
