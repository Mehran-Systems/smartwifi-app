package com.smartwifi.logic

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiActionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugger: SmartWifiDebugger
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lastSuggestions: List<WifiNetworkSuggestion> = emptyList()

    fun getConnectedBssid(): String? = wifiManager.connectionInfo.bssid
    
    fun getConnectedSsid(): String? {
        val ssid = wifiManager.connectionInfo.ssid
        if (ssid == "<unknown ssid>" || ssid == null) return null
        return ssid.replace("\"", "")
    }

    fun startScan() {
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    }

    private fun calculatePriority(scan: android.net.wifi.ScanResult): Int {
        // Base score: Map RSSI (-100 to -50) to (0 to 50)
        // Formula: (RSSI + 100). Example: -50 -> 50. -90 -> 10.
        val baseScore = (scan.level + 100).coerceAtLeast(0)
        
        // 5GHz Bonus: Weight higher than 2.4GHz
        val is5Ghz = scan.frequency > 4900
        val bonus = if (is5Ghz) 20 else 0
        
        // Final Score: 0 to 100
        return (baseScore + bonus).coerceIn(0, 100)
    }

    /**
     * Submits a batch of network suggestions to the OS.
     * Handles removal of old suggestions and strict error checking.
     */
    fun submitNetworkSuggestions(results: List<android.net.wifi.ScanResult>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestions = results.map { scan ->
                val priority = calculatePriority(scan)
                val ssid = scan.SSID.replace("\"", "")
                
                WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setBssid(android.net.MacAddress.fromString(scan.BSSID))
                    .setPriority(priority)
                    .setIsAppInteractionRequired(false)
                    .build()
            }

            // Lifecycle: Remove previously added suggestions to prevent bloat
            if (lastSuggestions.isNotEmpty()) {
                wifiManager.removeNetworkSuggestions(lastSuggestions)
            }

            // Add new batch
            if (suggestions.isNotEmpty()) {
                val status = wifiManager.addNetworkSuggestions(suggestions)
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    lastSuggestions = suggestions
                    debugger.logOsSuggestion("Batch", "${suggestions.size} networks submitted")
                    Log.d("WifiActionManager", "Batch Suggestion Success. Count: ${suggestions.size}")
                } else if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {
                    Log.e("WifiActionManager", "Error: Duplicate network suggestions.")
                } else {
                    Log.e("WifiActionManager", "Error adding suggestions. Status Code: $status")
                }
            } else {
                 lastSuggestions = emptyList()
            }
        }
    }

    /**
     * Helper for single network suggestion (internal use for forced connection).
     */
    private fun suggestSingleNetwork(ssid: String, bssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setBssid(android.net.MacAddress.fromString(bssid))
                .setPriority(100)
                .setIsAppInteractionRequired(true) // Crucial: Triggers OS prompt for user confirmation
                .build()

            val list = listOf(suggestion)
            if (lastSuggestions.isNotEmpty()) {
                wifiManager.removeNetworkSuggestions(lastSuggestions)
            }
            val status = wifiManager.addNetworkSuggestions(list)
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.e("WifiActionManager", "Failed to add manual suggestion: $status")
            }
            lastSuggestions = list
            debugger.logOsSuggestion(ssid, "Manual Connect Triggered")
        }
    }



    /**
     * Forcefully attempts to connect to the suggested network.
     */
    fun connectToNetwork(ssid: String, targetBssid: String) {
        Log.i("WifiActionManager", "Manual Switch: Opening Internet Panel for user selection")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (Q): Direct WiFi control is restricted.
            // WifiNetworkSpecifier (previous attempt) is for IoT/Local-Only and blocks Internet.
            // The standard, supported way is the Internet Connectivity Panel.
            try {
                val panelIntent = android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                panelIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(panelIntent)
                // We can also toast a hint? "Please select $ssid"
            } catch (e: Exception) {
                Log.e("WifiActionManager", "Failed to open Internet Panel", e)
                // Fallback to strict Settings
                val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                wifiIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(wifiIntent)
            }
        } else {
            @Suppress("DEPRECATION")
            val conf = android.net.wifi.WifiConfiguration()
            conf.SSID = "\"$ssid\""
            conf.BSSID = targetBssid
            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
            }
        }
    }
    
    fun getScanResults(): List<android.net.wifi.ScanResult> = wifiManager.scanResults ?: emptyList()
    fun openInternetPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                panelIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(panelIntent)
            } catch (e: Exception) {
                Log.e("WifiActionManager", "Failed to open Internet Panel", e)
                val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                wifiIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(wifiIntent)
            }
        } else {
             val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
             wifiIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
             context.startActivity(wifiIntent)
        }
    }
}
