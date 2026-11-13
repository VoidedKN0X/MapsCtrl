package com.vknxworks.mapsctrl

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MapsCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Mapsforge draws with Android's own graphics.
        AndroidGraphicFactory.createInstance(this)
        container = AppContainer(this)
    }
}
