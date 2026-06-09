package com.dhanuk.lofiga

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.media.MediaNotificationManager
import com.dhanuk.lofiga.media.MediaSessionManager
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LofigaApplication : Application() {

    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var mediaNotificationManager: MediaNotificationManager
        private set

    private var foregroundActivityCount = 0

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Main thread: media managers and notification channel (required early / by Android)
        mediaSessionManager = MediaSessionManager(this)
        mediaNotificationManager = MediaNotificationManager(this)
        mediaNotificationManager.createChannel()

        // Main thread: lifecycle callbacks
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

        // Defer heavy SDK initialisation to avoid blocking the first frame render
        // Note: AdMob SDK MUST be initialized and loaded on the Main UI thread
        applicationScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(500)
            
            try {
                OneSignal.initWithContext(this@LofigaApplication, BuildConfig.ONESIGNAL_APP_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                AdManager.initialize(this@LofigaApplication)
                AdManager.loadInterstitial(this@LofigaApplication)
                AdManager.loadRewarded(this@LofigaApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
