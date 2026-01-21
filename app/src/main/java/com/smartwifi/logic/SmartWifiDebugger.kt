package com.smartwifi.logic

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized debugger for SmartWifi events.
 * Logs critical app decisions and state changes to Logcat.
 */
@Singleton
class SmartWifiDebugger @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    companion object {
        private const val TAG = "SmartWifiDebugger"
        private const val LOG_FILE_NAME = "smartwifi_debug.txt"
    }

    private fun appendToFile(tag: String, msg: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val logLine = "$timestamp [$tag] $msg\n"
            
            // Append to file
            context.openFileOutput(LOG_FILE_NAME, android.content.Context.MODE_APPEND).use {
                it.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogContent(): String {
        return try {
            context.openFileInput(LOG_FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "No logs found."
        }
    }
    
    fun clearLogs() {
        try {
            context.deleteFile(LOG_FILE_NAME)
        } catch (e: Exception) {}
    }

    fun logOsSuggestion(ssid: String, bssid: String) {
        val msg = "App suggested network to OS -> SSID: $ssid, BSSID: $bssid"
        Log.d(TAG, "[OS Suggestion] $msg")
        appendToFile("OS_SUGGEST", msg)
    }

    fun logSettingsChange(details: String) {
        val msg = details
        Log.i(TAG, "[Settings Changed] $msg")
        appendToFile("SETTINGS", msg)
    }

    fun logUserSuggestion(ssid: String, reason: String) {
        val msg = "App suggested user switch to -> SSID: $ssid. Reason: $reason"
        Log.i(TAG, "[User Suggestion] $msg")
        appendToFile("BADGE_POPUP", msg)
    }

    fun logDecision(message: String) {
        Log.d(TAG, "[Decision] $message")
        appendToFile("DECISION", message)
    }
}
