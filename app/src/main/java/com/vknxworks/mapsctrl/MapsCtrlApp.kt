package com.vknxworks.mapsctrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.mapsctrl.ui.lock.AppLock
import kotlinx.coroutines.launch
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MapsCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Mapsforge draws with Android's own graphics.
        AndroidGraphicFactory.createInstance(this)
        val c = AppContainer(this)
        container = c
        // The app lock locks after the chosen time away ("Lock again"). It only covers the screens: navigation,
        // track recording and ETA sharing run in their own session and service and don't notice it.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(c.vault.enabled, c.settings.settings.value.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(c.vault.enabled, c.settings.settings.value.lockTimeout.millis)
                // A recent search waiting to be written is written now, before Android may stop the app.
                c.appScope.launch { c.saved.flushRecent() }
            }
        })
    }
}
