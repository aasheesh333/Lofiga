package com.dhanuk.lofiga

import android.app.Application
import android.util.Log
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

        OneSignal.initWithContext(this)
        OneSignal.setAppId(BuildConfig.ONESIGNAL_APP_ID)

        OneSignal.setNotificationOpenedHandler { openedResult ->
            val actionId = openedResult.action?.actionId
            val data = openedResult.notification.additionalData
            Log.d("OneSignal", "Notification opened: actionId=$actionId, data=$data")
        }

        OneSignal.setSubscriptionObserver { state ->
            Log.d("OneSignal", "Subscription changed: subscribed=${state.subscribed}, userId=${state.userId}")
        }

        OneSignal.setNotificationWillShowInForegroundHandler { notif ->
            Log.d("OneSignal", "Notification received in foreground: ${notif.notificationId}")
            notif.show()
        }

        mediaSessionManager = MediaSessionManager(this)
        mediaNotificationManager = MediaNotificationManager(this)
        mediaNotificationManager.createChannel()

        AdManager.initialize(this)
    }
}
