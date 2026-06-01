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
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL = 10000L
    private const val MAX_FAILED_LOADS = 3

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var bannerAd: com.google.android.gms.ads.AdView? = null
    private var lastInterstitialTime = 0L
    private var consecutiveInterstitialFailures = 0
    private var consecutiveRewardedFailures = 0

    private val _isConsentObtained = MutableStateFlow(false)
    val isConsentObtained: StateFlow<Boolean> = _isConsentObtained.asStateFlow()

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    data class AdDiagnostics(
        val appId: String = BuildConfig.ADMOB_APP_ID,
        val bannerAdUnitId: String = BuildConfig.ADMOB_BANNER_ID,
        val interstitialAdUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_ID,
        val rewardedAdUnitId: String = BuildConfig.ADMOB_REWARDED_ID,
        val isMobileAdsInitialized: Boolean = false,
        val isAdFree: Boolean = false,
        val isConsentObtained: Boolean = false,
        val isInterstitialReady: Boolean = false,
        val isRewardedReady: Boolean = false,
        val consecutiveInterstitialFailures: Int = 0,
        val consecutiveRewardedFailures: Int = 0,
        val lastInterstitialError: String? = null,
        val lastRewardedError: String? = null,
        val minInterstitialIntervalMs: Long = MIN_INTERSTITIAL_INTERVAL,
        val maxFailedLoads: Int = MAX_FAILED_LOADS
    )

    private var mobileAdsInitialized = false
    private var lastInterstitialError: String? = null
    private var lastRewardedError: String? = null

    private val _diagnostics = MutableStateFlow(AdDiagnostics())
    val diagnostics: StateFlow<AdDiagnostics> = _diagnostics.asStateFlow()

    private fun pushDiagnostics() {
        _diagnostics.value = AdDiagnostics(
            isMobileAdsInitialized = mobileAdsInitialized,
            isAdFree = _isAdFree.value,
            isConsentObtained = _isConsentObtained.value,
            isInterstitialReady = interstitialAd != null,
            isRewardedReady = rewardedAd != null,
            consecutiveInterstitialFailures = consecutiveInterstitialFailures,
            consecutiveRewardedFailures = consecutiveRewardedFailures,
            lastInterstitialError = lastInterstitialError,
            lastRewardedError = lastRewardedError
        )
    }

    fun initialize(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "AdMob SDK initialized")
            mobileAdsInitialized = true
            pushDiagnostics()
        }
    }

    fun requestConsent(activity: Activity) {
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
                    pushDiagnostics()
                }
            },
            { error ->
                Log.e(TAG, "Consent info update failed: ${error.message}")
                _isConsentObtained.value = true
                pushDiagnostics()
            }
        )
    }

    private fun loadAndShowConsentForm(activity: Activity) {
        UserMessagingPlatform.loadConsentForm(activity,
            { consentForm ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) {
                        loadAndShowConsentForm(activity)
                    }
                } else {
                    _isConsentObtained.value = true
                    pushDiagnostics()
                }
            },
            { error ->
                Log.e(TAG, "Consent form load failed: ${error.message}")
                _isConsentObtained.value = true
                pushDiagnostics()
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
                    lastInterstitialError = null
                    Log.d(TAG, "Interstitial ad loaded")
                    pushDiagnostics()
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            pushDiagnostics()
                            loadInterstitial(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                            Log.e(TAG, "Interstitial failed to show: ${error.message}")
                            pushDiagnostics()
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveInterstitialFailures++
                    lastInterstitialError = "code=${error.code} domain=${error.domain} msg=${error.message}"
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    pushDiagnostics()
                    if (consecutiveInterstitialFailures < MAX_FAILED_LOADS) {
                        loadInterstitial(context)
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
            interstitialAd?.show(activity)
            pushDiagnostics()
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
        } else {
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
                    lastRewardedError = null
                    Log.d(TAG, "Rewarded ad loaded")
                    pushDiagnostics()
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            pushDiagnostics()
                            loadRewarded(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            rewardedAd = null
                            Log.e(TAG, "Rewarded failed to show: ${error.message}")
                            pushDiagnostics()
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveRewardedFailures++
                    lastRewardedError = "code=${error.code} domain=${error.domain} msg=${error.message}"
                    Log.e(TAG, "Rewarded failed to load: ${error.message}")
                    pushDiagnostics()
                    if (consecutiveRewardedFailures < MAX_FAILED_LOADS) {
                        loadRewarded(context)
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
            rewardedAd?.show(activity) {
                onRewarded()
            }
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
        } else {
            onDismissed()
        }
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    // ========================
    // BANNER (Singleton)
    // ========================

    fun getOrCreateBannerAd(context: Context, adUnitId: String): com.google.android.gms.ads.AdView {
        return bannerAd ?: createBannerAd(context, adUnitId).also { bannerAd = it }
    }

    private fun createBannerAd(context: Context, adUnitId: String): com.google.android.gms.ads.AdView {
        return com.google.android.gms.ads.AdView(context).apply {
            setAdSize(com.google.android.gms.ads.AdSize.BANNER)
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    fun refreshBannerIfNeeded(context: Context) {
        val ad = bannerAd ?: return
        if (ad.adUnitId == null) return
        val isLoading = ad.isLoading
        if (!isLoading) {
            ad.loadAd(AdRequest.Builder().build())
        }
    }

    fun destroyBanner() {
        bannerAd?.destroy()
        bannerAd = null
    }

    fun setAdFree(adFree: Boolean) {
        _isAdFree.value = adFree
        if (adFree) {
            destroyBanner()
        }
        pushDiagnostics()
    }
}
