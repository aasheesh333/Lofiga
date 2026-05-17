package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.media.MediaNotificationManager
import com.dhanuk.lofiga.media.MediaSessionManager
import com.onesignal.OneSignal
import kotlinx.coroutines.runBlocking

class LofigaApplication : Application() {

    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var mediaNotificationManager: MediaNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()

        runBlocking {
            OneSignal.initWithContext(this@LofigaApplication)
        }

        mediaSessionManager = MediaSessionManager(this)
        mediaNotificationManager = MediaNotificationManager(this)
        mediaNotificationManager.createChannel()

        AdManager.initialize(this)
    }
}
