package com.dhanuk.lofiga

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.ads.DebugFlags
import com.dhanuk.lofiga.media.MediaNotificationManager
import com.dhanuk.lofiga.media.MediaSessionManager
import com.onesignal.OneSignal

class LofigaApplication : Application() {

    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var mediaNotificationManager: MediaNotificationManager
        private set

    private var foregroundActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

        if (DebugFlags.isAdTestModeEnabled(this)) {
            AdManager.applyTestMode(this)
        }

        mediaSessionManager = MediaSessionManager(this)
        mediaNotificationManager = MediaNotificationManager(this)
        mediaNotificationManager.createChannel()

        AdManager.initialize(this)
        AdManager.loadInterstitial(this)
        AdManager.loadRewarded(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (foregroundActivityCount == 0) {
                    AdManager.resetFailureCounters(this@LofigaApplication)
                }
                foregroundActivityCount++
            }
            override fun onActivityStopped(activity: Activity) {
                foregroundActivityCount = (foregroundActivityCount - 1).coerceAtLeast(0)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
