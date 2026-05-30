package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.media.MediaNotificationManager
import com.dhanuk.lofiga.media.MediaSessionManager
import com.onesignal.OneSignal

class LofigaApplication : Application() {

    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var mediaNotificationManager: MediaNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Only initialize OneSignal when an App ID is configured (set the
        // ONESIGNAL_APP_ID secret/property at build time). Initializing with a
        // blank ID leaves the SDK in a broken state.
        val oneSignalAppId = BuildConfig.ONESIGNAL_APP_ID
        if (oneSignalAppId.isNotBlank()) {
            OneSignal.initWithContext(this, oneSignalAppId)
        }

        mediaSessionManager = MediaSessionManager(this)
        mediaNotificationManager = MediaNotificationManager(this)
        mediaNotificationManager.createChannel()

        AdManager.initialize(this)
    }
}
