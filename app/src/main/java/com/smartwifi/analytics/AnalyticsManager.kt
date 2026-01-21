package com.smartwifi.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val firebaseAnalytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun logServiceStarted() {
        firebaseAnalytics.logEvent("service_started", null)
    }

    fun logSuggestionBatch(count: Int) {
        val bundle = Bundle().apply {
            putInt("count", count)
        }
        firebaseAnalytics.logEvent("suggestion_batch_submitted", bundle)
    }

    fun logBadgeShown(ssid: String?) {
        val bundle = Bundle().apply {
            putString("ssid", ssid ?: "unknown")
        }
        firebaseAnalytics.logEvent("badge_shown", bundle)
    }

    fun logBetterNetworkFound(ssid: String, rssi: Int, is5Ghz: Boolean) {
        val bundle = Bundle().apply {
            putString("ssid", ssid)
            putInt("rssi", rssi)
            putBoolean("is_5ghz", is5Ghz)
        }
        firebaseAnalytics.logEvent("better_network_found", bundle)
    }

    fun logSpeedTest(downloadSpeed: Double, uploadSpeed: Double, idlePing: Int?) {
        val bundle = Bundle().apply {
            putDouble("download_speed_mbps", downloadSpeed)
            putDouble("upload_speed_mbps", uploadSpeed)
            if (idlePing != null) {
                putInt("idle_ping_ms", idlePing)
            }
        }
        firebaseAnalytics.logEvent("speed_test_result", bundle)
    }
}
