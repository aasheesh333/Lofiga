package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class LofigaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AdManager.initialize(this)

        val onesignalId = BuildConfig.ONESIGNAL_APP_ID
        if (onesignalId.isNotEmpty() && onesignalId != "placeholder-dev-id") {
            OneSignal.Debug.logLevel = LogLevel.NONE
            OneSignal.initWithContext(this, onesignalId)
        }
    }
}
