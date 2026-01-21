package com.smartwifi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartWifiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.google.android.gms.ads.MobileAds.initialize(this) {}
    }
}
