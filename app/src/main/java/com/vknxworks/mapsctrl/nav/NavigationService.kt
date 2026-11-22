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
        try {
            startForeground(ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            running = true
        } catch (_: Exception) {
            // Without the location permission Android refuses a location service: navigation still works while visible.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val ID = 1

        /** The service is in the foreground with its notification. */
        @Volatile private var running = false

        /** The one notification: "Navigating" while navigating, "Recording a track" otherwise. */
        private fun notification(context: Context): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val text = when {
                context.container.navigation.state.value != null -> context.getString(R.string.notif_navigating)
                else -> context.getString(R.string.notif_recording)
            }
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_maps)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build()
        }

        fun start(context: Context) {
            if (!context.container.location.hasPermission()) return
            runCatching { context.startForegroundService(Intent(context, NavigationService::class.java)) }
        }

        /** Stops unless something else (navigation or recording) still needs it. */
        fun stop(context: Context) {
            val c = context.container
            if (c.navigation.state.value != null || c.recorder.recording.value) {
                // Refresh the text in place. Starting the service again would be refused by Android while the
                // app is in the background, and the notification kept saying "Navigating".
                if (running) runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, notification(context)) }
                return
            }
            context.stopService(Intent(context, NavigationService::class.java))
        }
    }
}
