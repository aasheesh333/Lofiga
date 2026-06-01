package com.dhanuk.lofiga.ads

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.lofiga.BuildConfig

/**
 * Singleton banner ad. The AdView is created once on first composition
 * and persists for the lifetime of the app. loadAd is called exactly
 * once via [AdManager.loadBannerOnce] — no auto-refresh, no per-screen
 * reload — to comply with AdMob's banner policy.
 *
 * Visibility (VISIBLE/GONE) is controlled via [visible], which the
 * caller toggles based on which screen the user is on. The AdView
 * itself is never destroyed or recreated during normal navigation.
 *
 * Should be placed at the Scaffold level (not inside screen content)
 * so that screen switches don't recompose it. The Composable should
 * stay in the composition tree even when hidden, with visibility
 * toggled via the [visible] parameter.
 */
@Composable
fun BannerAd(
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdManager.getOrCreateBannerAd(ctx, BuildConfig.ADMOB_BANNER_ID)
        },
        update = { view ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) {
                AdManager.loadBannerOnce()
            }
        }
    )
}
