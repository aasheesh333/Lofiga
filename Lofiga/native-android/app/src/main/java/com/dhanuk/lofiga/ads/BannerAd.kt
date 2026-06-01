package com.dhanuk.lofiga.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.lofiga.BuildConfig

@Composable
fun BannerAd(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdManager.createBannerView(ctx, BuildConfig.ADMOB_BANNER_ID)
        }
    )
}
