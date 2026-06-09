package com.dhanuk.lofiga.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dhanuk.lofiga.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL = 10000L
    private const val MAX_FAILED_LOADS = 3
    private const val BANNER_REFRESH_INTERVAL_MS = 60_000L

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bannerRefreshJob: Job? = null

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var lastInterstitialTime = 0L
    private var consecutiveInterstitialFailures = 0
    private var consecutiveRewardedFailures = 0
    private var consentFormAttempts = 0

    private val _isConsentObtained = MutableStateFlow(false)
    val isConsentObtained: StateFlow<Boolean> = _isConsentObtained.asStateFlow()

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private var bannerAd: com.google.android.gms.ads.AdView? = null
    private var lastBannerLoadTime = 0L

    fun resetFailureCounters(context: Context) {
        consecutiveInterstitialFailures = 0
        consecutiveRewardedFailures = 0
        Log.d(TAG, "Failure counters reset")
        loadInterstitial(context)
        loadRewarded(context)
    }

    fun initialize(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "AdMob SDK initialized")
        }
    }

    fun requestConsent(activity: Activity) {
        consentFormAttempts = 0
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity)
                } else {
                    _isConsentObtained.value = true
                }
            },
            { error ->
                Log.e(TAG, "Consent info update failed: ${error.message}")
                _isConsentObtained.value = true
            }
        )
    }

    private fun loadAndShowConsentForm(activity: Activity) {
        consentFormAttempts++
        if (consentFormAttempts >= 2) {
            Log.w(TAG, "Consent form max attempts reached, treating as obtained")
            _isConsentObtained.value = true
            return
        }
        UserMessagingPlatform.loadConsentForm(activity,
            { consentForm ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) {
                        loadAndShowConsentForm(activity)
                    }
                } else {
                    _isConsentObtained.value = true
                }
            },
            { error ->
                Log.e(TAG, "Consent form load failed: ${error.message}")
                _isConsentObtained.value = true
            }
        )
    }

    // ========================
    // INTERSTITIAL
    // ========================

    fun loadInterstitial(context: Context) {
        if (_isAdFree.value) return

        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    consecutiveInterstitialFailures = 0
                    Log.d(TAG, "Interstitial ad loaded")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                            Log.e(TAG, "Interstitial failed to show: ${error.message}")
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveInterstitialFailures++
                    Log.e(TAG, "Interstitial failed to load: code=${error.code} - ${error.message}")
                    if (consecutiveInterstitialFailures < MAX_FAILED_LOADS) {
                        val retryDelay = 2000L * consecutiveInterstitialFailures
                        Log.d(TAG, "Retrying interstitial load in ${retryDelay}ms")
                        refreshScope.launch {
                            delay(retryDelay)
                            loadInterstitial(context)
                        }
                    }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null) {
        if (_isAdFree.value) {
            onDismissed?.invoke()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastInterstitialTime < MIN_INTERSTITIAL_INTERVAL) {
            Log.d(TAG, "Interstitial skipped - too soon")
            onDismissed?.invoke()
            return
        }

        if (interstitialAd != null) {
            lastInterstitialTime = now
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onDismissed?.invoke()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    onDismissed?.invoke()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial skipped - no ad ready (fill=0 or load failed)")
            onDismissed?.invoke()
        }
    }

    // ========================
    // REWARDED
    // ========================

    fun loadRewarded(context: Context) {
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    consecutiveRewardedFailures = 0
                    Log.d(TAG, "Rewarded ad loaded")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            loadRewarded(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            rewardedAd = null
                            Log.e(TAG, "Rewarded failed to show: ${error.message}")
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveRewardedFailures++
                    Log.e(TAG, "Rewarded failed to load: code=${error.code} - ${error.message}")
                    if (consecutiveRewardedFailures < MAX_FAILED_LOADS) {
                        val retryDelay = 2000L * consecutiveRewardedFailures
                        Log.d(TAG, "Retrying rewarded load in ${retryDelay}ms")
                        refreshScope.launch {
                            delay(retryDelay)
                            loadRewarded(context)
                        }
                    }
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    onDismissed()
                }
            }
            rewardedAd?.show(activity) {
                onRewarded()
            }
        } else {
            onDismissed()
        }
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    // ========================
    // BANNER (singleton, periodic refresh)
    // ========================

    /**
     * Returns the singleton banner AdView, creating it on first call.
     * The AdView is created ONCE and persists for the app's lifetime.
     *
     * Per AdMob policy, automatic refresh happens at most every
     * BANNER_REFRESH_INTERVAL_MS (60s). Visibility (VISIBLE / GONE) is
     * toggled by the caller based on which screen the user is on; the
     * AdView itself is never destroyed or recreated during normal
     * navigation.
     */
    fun getOrCreateBannerAd(context: Context, adUnitId: String): com.google.android.gms.ads.AdView {
        if (bannerAd == null) {
            val view = com.google.android.gms.ads.AdView(context)
            view.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
            view.adUnitId = adUnitId
            view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    startBannerRefresh()
                }
                override fun onViewDetachedFromWindow(v: android.view.View) {
                    stopBannerRefresh()
                }
            })
            bannerAd = view
        }
        return bannerAd!!
    }

    /**
     * Triggers the first banner load and starts the periodic refresh loop.
     * Safe to call multiple times — guards prevent duplicate loads and
     * duplicate refresh jobs. The first load happens when the AdView is
     * attached to a window; refreshes happen every
     * BANNER_REFRESH_INTERVAL_MS while the app is in the foreground.
     */
    fun startBannerRefresh() {
        val ad = bannerAd ?: return
        if (!ad.isAttachedToWindow) {
            Log.d(TAG, "startBannerRefresh: AdView not attached to window, deferring")
            return
        }
        if (ad.isLoading) return

        if (lastBannerLoadTime == 0L) {
            ad.loadAd(AdRequest.Builder().build())
            lastBannerLoadTime = System.currentTimeMillis()
            Log.d(TAG, "Banner initial load (refresh loop start)")
        }

        if (bannerRefreshJob?.isActive == true) return
        bannerRefreshJob = refreshScope.launch {
            while (isActive) {
                delay(BANNER_REFRESH_INTERVAL_MS)
                val current = bannerAd ?: break
                if (!current.isAttachedToWindow) continue
                if (current.isLoading) continue
                if (current.visibility != android.view.View.VISIBLE) continue
                current.loadAd(AdRequest.Builder().build())
                lastBannerLoadTime = System.currentTimeMillis()
                Log.d(TAG, "Banner auto-refresh (interval=${BANNER_REFRESH_INTERVAL_MS / 1000}s)")
            }
        }
    }

    fun stopBannerRefresh() {
        bannerRefreshJob?.cancel()
        bannerRefreshJob = null
    }

    fun destroyBanner() {
        stopBannerRefresh()
        bannerAd?.destroy()
        bannerAd = null
    }

    fun isBannerAdAttached(): Boolean = bannerAd?.isAttachedToWindow == true

    fun setBannerVisibility(visible: Boolean) {
        bannerAd?.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun setAdFree(adFree: Boolean) {
        _isAdFree.value = adFree
        if (adFree) {
            destroyBanner()
        }
    }
}
