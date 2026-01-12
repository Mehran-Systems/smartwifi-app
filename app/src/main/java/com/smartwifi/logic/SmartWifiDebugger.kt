package com.smartwifi.logic

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized debugger for SmartWifi events.
 * Logs critical app decisions and state changes to Logcat.
 */
@Singleton
class SmartWifiDebugger @Inject constructor() {

    companion object {
        private const val TAG = "SmartWifiDebugger"
    }

    /**
     * Log when the app suggests a network to the Android OS.
     */
    fun logOsSuggestion(ssid: String, bssid: String) {
        Log.d(TAG, "[OS Suggestion] App suggested network to OS -> SSID: $ssid, BSSID: $bssid")
    }

    /**
     * Log when the user changes app settings.
     */
    fun logSettingsChange(details: String) {
        Log.i(TAG, "[Settings Changed] $details")
    }

    /**
     * Log when the app suggests a network switch to the User.
     */
    fun logUserSuggestion(ssid: String, reason: String) {
        Log.i(TAG, "[User Suggestion] App suggested user switch to -> SSID: $ssid. Reason: $reason")
    }

    /**
     * Log internal decision logic (e.g. why a network was skipped).
     */
    fun logDecision(message: String) {
        Log.d(TAG, "[Decision] $message")
    }
}
