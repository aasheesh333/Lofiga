package com.dhanuk.lofiga.ads

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.lofiga.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    context: Context
) {
    val isAdFree by AdManager.isAdFree.collectAsState()
    if (isAdFree) return

    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                // Anchored adaptive banner: sized per-device for a higher fill
                // rate and eCPM than a fixed 320x50 banner.
                setAdSize(adaptiveAdSize(ctx))
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier
    )
}

private fun adaptiveAdSize(context: Context): AdSize {
    val metrics = context.resources.displayMetrics
    val adWidthDp = (metrics.widthPixels / metrics.density).toInt().coerceAtLeast(320)
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
}
