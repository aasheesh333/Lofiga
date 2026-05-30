package com.dhanuk.lofiga.ads

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.lofiga.BuildConfig

@Composable
fun BannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adView = AdManager.getOrCreateBannerAd(context, BuildConfig.ADMOB_BANNER_ID)

    DisposableEffect(adView) {
        onDispose {
            (adView.parent as? ViewGroup)?.removeView(adView)
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier
    )
}
