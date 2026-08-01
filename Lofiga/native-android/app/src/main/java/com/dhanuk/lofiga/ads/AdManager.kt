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
    // Play/AdMob policy-safe cooldowns (per user spec):
    //  - Interstitial: at most once every 2 minutes, and only on explicit tab switches.
    //  - Rewarded: strictly user-initiated, at most once every 3 minutes.
    const val MIN_INTERSTITIAL_INTERVAL = 120_000L
    private const val MIN_REWARDED_INTERVAL = 180_000L
    private const val MAX_FAILED_LOADS = 3
    private const val BANNER_REFRESH_INTERVAL_MS = 60_000L

    /** Duration of a rewarded-ad ad-free window (1 hour). */
    private const val AD_FREE_DURATION_MS = 60 * 60 * 1000L

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bannerRefreshJob: Job? = null

    /** Wall-clock time (ms) until which ads stay suppressed. 0 = not ad-free. */
    private var adFreeUntilMs = 0L
    private var adFreeExpiryJob: Job? = null

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var lastInterstitialTime = 0L
    private var lastRewardedTime = 0L
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
        // Don't attempt to load ads before consent has been obtained — the
        // LofigaApplication applicationScope is responsible for first-time
        // ad loads after consent completes; this entry point only handles
        // subsequent foreground re-entry.
        if (!_isConsentObtained.value) return
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

        // Google UMP guidance: canRequestAds is the single source of truth for
        // "may we show personalize / load ads in this region". It is true when
        // the user has consented OR when no consent form is required in their
        // region (e.g. users outside EEA/UK/CH). We poll it after every step
        // rather than synthesising "obtained" from error paths.
        fun syncCanRequestAds() {
            val canRequest = consentInformation.canRequestAds()
            if (canRequest && !_isConsentObtained.value) {
                _isConsentObtained.value = true
            }
        }
        syncCanRequestAds()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity)
                } else {
                    // No form available — rely on canRequestAds.
                    syncCanRequestAds()
                }
            },
            { error ->
                Log.e(TAG, "Consent info update failed: ${error.message}")
                // Don't synthesise "obtained" from failure — let canRequestAds
                // decide. (If the user's region needs consent and we couldn't
                // request it, we should NOT load ads.)
                syncCanRequestAds()
            }
        )
    }

    private fun loadAndShowConsentForm(activity: Activity) {
        consentFormAttempts++
        if (consentFormAttempts >= 2) {
            Log.w(TAG, "Consent form max attempts reached; relying on canRequestAds")
            syncCanRequestAdsFromActivity(activity)
            return
        }
        UserMessagingPlatform.loadConsentForm(activity,
            { consentForm ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) {
                        // After form dismissed, refresh canRequestAds and recurse
                        // in case the form re-shows (REQUIRED can persist briefly).
                        val ci = UserMessagingPlatform.getConsentInformation(activity)
                        if (ci.canRequestAds()) _isConsentObtained.value = true
                        if (ci.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                            && consentFormAttempts < 2) {
                            loadAndShowConsentForm(activity)
                        } else {
                            _isConsentObtained.value = ci.canRequestAds()
                        }
                    }
                } else {
                    val ci = UserMessagingPlatform.getConsentInformation(activity)
                    _isConsentObtained.value = ci.canRequestAds()
                }
            },
            { error ->
                Log.e(TAG, "Consent form load failed: ${error.message}")
                // Don't synthesise obtained — let canRequestAds decide.
                val ci = UserMessagingPlatform.getConsentInformation(activity)
                _isConsentObtained.value = ci.canRequestAds()
            }
        )
    }

    private fun syncCanRequestAdsFromActivity(activity: Activity) {
        val ci = UserMessagingPlatform.getConsentInformation(activity)
        _isConsentObtained.value = ci.canRequestAds()
    }

    // ========================
    // INTERSTITIAL
    // ========================

    fun loadInterstitial(context: Context) {
        if (_isAdFree.value) return
        if (!_isConsentObtained.value) {
            Log.d(TAG, "loadInterstitial: consent pending, deferring")
            return
        }

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

    fun loadRewarded(context: Context, onLoaded: ((RewardedAd) -> Unit)? = null, onFailed: ((LoadAdError) -> Unit)? = null) {
        if (!_isConsentObtained.value) {
            Log.d(TAG, "loadRewarded: consent pending, deferring")
            return
        }
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
                    onLoaded?.invoke(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    consecutiveRewardedFailures++
                    Log.e(TAG, "Rewarded failed to load: code=${error.code} - ${error.message}")
                    onFailed?.invoke(error)
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

    /**
     * Shows a rewarded ad. This must ONLY be invoked as a direct result of a
     * user action (e.g. tapping an explicit "watch ad to unlock" button) to stay
     * compliant with AdMob's rewarded-ad policy. A 3-minute cooldown applies.
     *
     * [onRewarded] fires exactly when the user earns the reward (AdMob calls it
     * only after the ad is fully watched). [onDismissed] fires when the user
     * does NOT earn the reward: cooldown skip, no ad ready, ad failed to show,
     * or the user closed the ad early.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        showRewardedInternal(
            activity = activity,
            bypassCooldown = false,
            onRewarded = onRewarded,
            onEarnedAndDismissed = { },
            onDismissed = onDismissed
        )
    }

    private fun showRewardedInternal(
        activity: Activity,
        bypassCooldown: Boolean,
        onRewarded: () -> Unit,
        onEarnedAndDismissed: () -> Unit,
        onDismissed: () -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (!bypassCooldown && now - lastRewardedTime < MIN_REWARDED_INTERVAL) {
            Log.d(TAG, "Rewarded skipped - within cooldown window")
            onDismissed()
            return
        }
        if (rewardedAd != null) {
            lastRewardedTime = now
            var rewardGranted = false
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    if (rewardGranted) onEarnedAndDismissed() else onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    if (rewardGranted) onEarnedAndDismissed() else onDismissed()
                }
            }
            rewardedAd?.show(activity) {
                rewardGranted = true
                onRewarded()
            }
        } else {
            onDismissed()
        }
    }

    enum class AdPhase { Loading, Showing }

    /**
     * Shows [count] rewarded ads sequentially. The cooldown is enforced for ad 1
     * and bypassed for ads 2..N. Each ad's reward grants [grantPerAdMs] incrementally
     * (AdMob policy: every completed ad must receive a reward).
     *
     * [onProgress] is called with the 1-based ad index, total count, and current phase.
     * [onAllRewarded] fires after the final ad's reward is earned and the ad is dismissed.
     * [onDismissed] fires if any ad fails, is skipped, or is closed early without earning.
     */
    fun showRewardedSequence(
        activity: Activity,
        context: Context,
        count: Int = 2,
        grantPerAdMs: Long = 15 * 60 * 1000L,
        onProgress: (adIndex: Int, total: Int, phase: AdPhase) -> Unit,
        onAllRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        var adsCompleted = 0

        fun showAd(index: Int) {
            onProgress(index, count, AdPhase.Showing)
            showRewardedInternal(
                activity = activity,
                bypassCooldown = index > 1,
                onRewarded = {
                    grantAdFree(grantPerAdMs * (index))
                    adsCompleted++
                },
                onEarnedAndDismissed = {
                    if (index < count) {
                        onProgress(index + 1, count, AdPhase.Loading)
                        loadRewarded(context, onLoaded = { _ ->
                            showAd(index + 1)
                        }, onFailed = {
                            Log.w(TAG, "Ad $index+1 load failed in sequence")
                            onDismissed()
                        })
                    } else {
                        onAllRewarded()
                    }
                },
                onDismissed = {
                    if (adsCompleted == 0) onDismissed()
                }
            )
        }

        showAd(1)
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
            // Use the Application context to avoid leaking an Activity for the
            // lifetime of this process-lifetime singleton AdView.
            val view = com.google.android.gms.ads.AdView(context.applicationContext)
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
        return requireNotNull(bannerAd)
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
        // Per AdMob UMP guidance, ad requests may only fire once the user's
        // consent has resolved. If consent is still pending, the refresh
        // loop is deferred; SDK consent helpers re-trigger banner loads
        // once _isConsentObtained flips true.
        if (!_isConsentObtained.value) {
            Log.d(TAG, "startBannerRefresh: consent pending, deferring initial load")
            return
        }

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
                if (!_isConsentObtained.value) continue
                current.loadAd(AdRequest.Builder().build())
                lastBannerLoadTime = System.currentTimeMillis()
                Log.d(TAG, "Banner auto-refresh (interval=${BANNER_REFRESH_INTERVAL_MS / 1000}s)")
            }
        }
    }

    /**
     * Triggers the first banner load and starts the refresh loop if consent
     * has just become obtained. Called by [com.dhanuk.lofiga.LofigaApplication]
     * once consent resolves — without this, a banner that was already attached
     * before consent was obtained would wait indefinitely for a detach+re-attach
     * cycle to trigger [startBannerRefresh].
     */
    fun onConsentResolved(context: Context) {
        if (!_isConsentObtained.value) return
        if (bannerAd?.isAttachedToWindow == true && lastBannerLoadTime == 0L) {
            bannerAd?.loadAd(AdRequest.Builder().build())
            lastBannerLoadTime = System.currentTimeMillis()
            Log.d(TAG, "Banner initial load (post-consent)")
        }
        if (bannerRefreshJob?.isActive != true) startBannerRefresh()
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
        if (adFree) {
            grantAdFree(AD_FREE_DURATION_MS)
        } else {
            revokeAdFree()
        }
    }

    /**
     * Grants an ad-free window for [durationMs], suppressing interstitial,
     * rewarded and banner ads until it expires. The banner is destroyed while
     * ad-free and recreated on expiry (callers observing [isAdFree] should
     * recreate their AndroidView via a `key(isAdFree)` to re-attach it).
     */
    fun grantAdFree(durationMs: Long) {
        adFreeUntilMs = System.currentTimeMillis() + durationMs
        _isAdFree.value = true
        destroyBanner()
        adFreeExpiryJob?.cancel()
        adFreeExpiryJob = refreshScope.launch {
            delay(durationMs)
            if (System.currentTimeMillis() >= adFreeUntilMs) {
                _isAdFree.value = false
                // Force a fresh banner load on the next attach.
                lastBannerLoadTime = 0L
                Log.d(TAG, "Ad-free window expired")
            }
        }
        Log.d(TAG, "Ad-free granted for ${durationMs / 60_000L} minutes")
    }

    /** Remaining ad-free time in ms (0 if not ad-free). */
    fun adFreeRemainingMs(): Long {
        val remaining = adFreeUntilMs - System.currentTimeMillis()
        return if (_isAdFree.value && remaining > 0) remaining else 0L
    }

    fun revokeAdFree() {
        adFreeExpiryJob?.cancel()
        adFreeExpiryJob = null
        adFreeUntilMs = 0L
        _isAdFree.value = false
    }

    fun extendAdFree(deltaMs: Long) {
        if (!_isAdFree.value) {
            grantAdFree(deltaMs)
            return
        }
        adFreeUntilMs += deltaMs
        adFreeExpiryJob?.cancel()
        val remaining = adFreeUntilMs - System.currentTimeMillis()
        if (remaining <= 0) {
            _isAdFree.value = false
            lastBannerLoadTime = 0L
            return
        }
        adFreeExpiryJob = refreshScope.launch {
            delay(remaining)
            if (System.currentTimeMillis() >= adFreeUntilMs) {
                _isAdFree.value = false
                lastBannerLoadTime = 0L
                Log.d(TAG, "Ad-free window expired")
            }
        }
        Log.d(TAG, "Ad-free extended by ${deltaMs / 60_000L} minutes")
    }
}
