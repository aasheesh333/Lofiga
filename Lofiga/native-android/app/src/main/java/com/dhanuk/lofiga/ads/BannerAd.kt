package com.dhanuk.lofiga.ads

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.lofiga.BuildConfig

/**
 * Singleton banner ad. The AdView is created once on first composition
 * and persists for the lifetime of the app. After the first load, a
 * coroutine refresh loop runs at BANNER_REFRESH_INTERVAL_MS (60s,
 * above AdMob's 30s minimum) while the banner is visible and the app
 * is in foreground.
 *
 * Visibility (VISIBLE/GONE) is controlled via [visible], which the
 * caller toggles based on which screen the user is on. The AdView
 * itself is never destroyed or recreated during normal navigation;
 * the refresh loop pauses itself when visibility is GONE.
 *
 * Manual "Reload Banner" from the diagnostic dialog bypasses the
 * interval (user-initiated action).
 *
 * Should be placed at the Scaffold level (not inside screen content)
 * so that screen switches don't recompose it.
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
            AdManager.startBannerRefresh()
        }
    )
}
