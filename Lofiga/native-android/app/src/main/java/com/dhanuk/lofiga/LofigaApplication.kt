package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager
import com.onesignal.OneSignal

class LofigaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        OneSignal.initWithContext(this)
        OneSignal.setAppId(BuildConfig.ONESIGNAL_APP_ID)

        AdManager.initialize(this)
    }
}
