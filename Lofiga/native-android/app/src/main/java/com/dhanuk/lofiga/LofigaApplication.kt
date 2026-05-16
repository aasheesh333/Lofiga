package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager
import com.onesignal.OneSignal
import kotlinx.coroutines.runBlocking

class LofigaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        runBlocking {
            OneSignal.initWithContext(this@LofigaApplication)
        }

        AdManager.initialize(this)
    }
}
