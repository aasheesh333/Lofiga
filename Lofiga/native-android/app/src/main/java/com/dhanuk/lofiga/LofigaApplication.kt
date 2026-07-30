package com.dhanuk.lofiga

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.media.Media3MediaSessionManager
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log

class LofigaApplication : Application() {

    lateinit var mediaSessionManager: Media3MediaSessionManager
        private set

    private var foregroundActivityCount = 0

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Main thread: media session manager (Media3) and notification channel (required early / by Android)
        mediaSessionManager = Media3MediaSessionManager(this)
        mediaSessionManager.ensureChannel("lofiga_playback", "Music Playback")

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

        // Defer heavy SDK initialisation: OneSignal can init regardless of ad
        // consent (push isn't personalised); AdMob MobileAds + ad loads MUST
        // wait for UMP consent state to resolve, otherwise EEA users see ads
        // before they have a chance to consent.
        //
        // MainViewModel triggers AdManager.requestConsent() from
        // MainActivity.onCreate — until then _isConsentObtained is false.
        // Outside EEA/UK/CH, requestConsent sets it true synchronously (via
        // ConsentInformation.canRequestAds).
        applicationScope.launch(Dispatchers.Main) {
            try {
                OneSignal.initWithContext(this@LofigaApplication, BuildConfig.ONESIGNAL_APP_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Block ad init until UMP resolves. Non-EEA users see ad loads
            // immediately (canRequestAds flips true synchronously inside
            // requestConsent); EEA users see loads only after they respond
            // to the consent form.
            try {
                withTimeoutOrNull(60_000L) {
                    AdManager.isConsentObtained.first { it }
                }
            } catch (e: Exception) {
                Log.w("Lofiga", "Consent flow timed out — ads will not load this session")
                return@launch
            }
            if (!AdManager.isConsentObtained.value) {
                Log.w("Lofiga", "Consent not obtained — skipping ad init")
                return@launch
            }

            try {
                AdManager.initialize(this@LofigaApplication)
                AdManager.loadInterstitial(this@LofigaApplication)
                AdManager.loadRewarded(this@LofigaApplication)
                // If a banner is already attached by the time consent resolves
                // (e.g. user navigated straight to a screen that hosts one),
                // kick an initial banner load — otherwise the user would have
                // to detach+re-attach to trigger the refresh loop.
                AdManager.onConsentResolved(this@LofigaApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
