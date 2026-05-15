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
    private const val MIN_INTERSTITIAL_INTERVAL = 90000L
    private const val MAX_FAILED_LOADS = 3

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var lastInterstitialTime = 0L
    private var consecutiveFailures = 0

    private val _isConsentObtained = MutableStateFlow(false)
    val isConsentObtained: StateFlow<Boolean> = _isConsentObtained.asStateFlow()

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    fun initialize(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "AdMob SDK initialized")
        }
        requestConsent(context)
    }

    private fun requestConsent(context: Context) {
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)

        consentInformation.requestConsentInfoUpdate(
            context as Activity,
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    loadAndShowConsentForm(context)
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

    private fun loadAndShowConsentForm(context: Context) {
        UserMessagingPlatform.loadConsentForm(context,
            { consentForm ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(context)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(context as Activity) {
                        loadAndShowConsentForm(context)
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

    fun loadInterstitial(context: Context) {
        if (_isAdFree.value) return

        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    consecutiveFailures = 0
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
                    consecutiveFailures++
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    if (consecutiveFailures < MAX_FAILED_LOADS) {
                        loadInterstitial(context)
                    }
                }
            }
        )
    }

    fun showInterstitial(context: Context, onDismissed: (() -> Unit)? = null) {
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
            interstitialAd?.show(context as Activity)
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(context)
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

    fun loadRewarded(context: Context) {
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    consecutiveFailures = 0
                    Log.d(TAG, "Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveFailures++
                    Log.e(TAG, "Rewarded failed to load: ${error.message}")
                }
            }
        )
    }

    fun showRewarded(
        context: Context,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        if (rewardedAd != null) {
            rewardedAd?.show(context as Activity) {
                onRewarded()
            }
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewarded(context)
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

    fun setAdFree(adFree: Boolean) {
        _isAdFree.value = adFree
    }
}
