package com.dhanuk.lofiga

import android.app.Application
import com.dhanuk.lofiga.ads.AdManager

class LofigaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
    }
}
