package com.dhanuk.lofiga.ads

import android.content.Context

object DebugFlags {
    private const val PREFS_NAME = "debug_flags"
    private const val KEY_AD_TEST_MODE = "ad_test_mode"

    fun isAdTestModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AD_TEST_MODE, false)
    }

    fun setAdTestModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AD_TEST_MODE, enabled)
            .apply()
    }
}
