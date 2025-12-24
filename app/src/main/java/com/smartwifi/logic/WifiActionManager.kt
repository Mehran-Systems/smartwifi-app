package com.smartwifi.logic

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiActionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getConnectedBssid(): String? {
        return wifiManager.connectionInfo.bssid
    }
    
    fun getConnectedSsid(): String? {
        // SSID usually comes with quotes, remove them
        val ssid = wifiManager.connectionInfo.ssid
        if (ssid == "<unknown ssid>" || ssid == null) return null
        return ssid.replace("\"", "")
    }

    fun disconnectNetwork() {
        Log.i("WifiActionManager", "Executing Disconnect Action")
        wifiManager.disconnect()
    }

    fun disableWifi() {
        Log.i("WifiActionManager", "Executing Disable Wifi Action")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = false
        } else {
            // Android 10+ restricts programmatic toggle. 
            // For a user app, we can guide them to settings or use an Intent Panel.
            // For this implementation, we will log the limitation and try Intent if context allows.
            Log.w("WifiActionManager", "Cannot programmatically disable WiFi on Android 10+. Requesting user action.")
            
            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Note: Service Context requires FLAG_ACTIVITY_NEW_TASK, but Panels might need Activity Context.
            // Failing gracefully with a log for now as per "Concept" requirements often implying older/system privileges
            // or expecting best-effort on newer OS.
        }
    }
    
    // Placeholder for scanning - scanning is restricted and throttled in newer Android
    fun startScan() {
        Log.i("WifiActionManager", "Requesting Wifi Scan")
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    }

    // Attempt to switch to a specific 5GHz BSSID of the same SSID
    fun connectTo5GhzNetwork(ssid: String, targetBssid: String) {
        Log.i("WifiActionManager", "Attempting switch to 5GHz: $ssid ($targetBssid)")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val conf = android.net.wifi.WifiConfiguration()
            conf.SSID = "\"$ssid\""
            conf.BSSID = targetBssid
            conf.status = android.net.wifi.WifiConfiguration.Status.ENABLED
            
            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                Log.d("WifiActionManager", "Legacy Connect Command Sent for ID: $netId")
            }
        } else {
             // Android 10+: Use WifiNetworkSuggestion API
             Log.i("WifiActionManager", "Android 10+: Adding 5GHz Suggestion for $ssid ($targetBssid)")
             
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                 val suggestion = android.net.wifi.WifiNetworkSuggestion.Builder()
                     .setSsid(ssid)
                     .setBssid(android.net.MacAddress.fromString(targetBssid))
                     .setPriority(1000) // High priority
                     .setIsAppInteractionRequired(true) // Treat as "Connect via App"
                     .build()
                 
                 val suggestions = listOf(suggestion)
                 val status = wifiManager.addNetworkSuggestions(suggestions)
                 
                 if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                     Log.i("WifiActionManager", "Suggestion Added Successfully. Disconnecting to force re-eval...")
                     // Force re-evaluation by disconnecting current
                     wifiManager.disconnect()
                 } else {
                     Log.w("WifiActionManager", "Suggestion Failed: $status")
                     // Fallback check
                     wifiManager.disconnect()
                     wifiManager.reconnect()
                 }
             }
        }

    }
    
    fun getScanResults(): List<android.net.wifi.ScanResult> {
        @Suppress("DEPRECATION")
        return wifiManager.scanResults
    }

    fun getSavedNetworks(): List<android.net.wifi.WifiConfiguration> {
        try {
            @Suppress("DEPRECATION")
            return wifiManager.configuredNetworks ?: emptyList() 
        } catch (e: SecurityException) {
            Log.e("WifiActionManager", "Permission denied for saved networks", e)
            return emptyList()
        }
    }
}
