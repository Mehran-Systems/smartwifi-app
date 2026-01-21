package com.smartwifi.data.model

data class AppUiState(
    val isServiceRunning: Boolean = false,
    val currentSsid: String = "Searching...",
    val signalStrength: Int = 0,
    val frequencyBand: String = "2.4GHz",
    val internetStatus: String = "Checking...",
    val activeMode: String = "Stationary (Home/Office)",
    // Smart Logic States
    val isZombieDetected: Boolean = false,
    val isDataFallback: Boolean = false,
    val lastAction: String = "Monitoring...",
    // UI fields
    val connectionSource: ConnectionSource = ConnectionSource.WIFI_ROUTER,
    val isGamingMode: Boolean = false,
    val probationList: List<ProbationItem> = emptyList(),
    val availableNetworks: List<AvailableNetworkItem> = emptyList(),
    
    // Manual Switch Prompt
    val pendingSwitchNetwork: AvailableNetworkItem? = null,

    // Advanced Settings & Metrics
    val linkSpeed: Int = 0, // Mbps
    val currentUsage: String = "0 KB/s", // Throughput
    val sensitivity: Int = 50, // 0-100 
    val mobileDataThreshold: Int = 5, // Mbps
    val isGeofencingEnabled: Boolean = false,
    val is5GhzPriorityEnabled: Boolean = true,
    val fiveGhzThreshold: Int = -75, 
    val minSignalDiff: Int = 5, 
    val isHotspotSwitchingEnabled: Boolean = false,
    val themeMode: String = "SYSTEM", 
    val themeBackground: Long = 0xFF121212, 
    val themeAccent: Long = 0xFFBB86FC,
    val badgeSensitivity: Int = 50 // Separate threshold for visual warning
)

data class ProbationItem(val bssid: String, val secondsRemaining: Long)
data class AvailableNetworkItem(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String,
    val channelWidth: Int,
    val isConnected: Boolean = false
)

enum class ConnectionSource {
    WIFI_ROUTER,
    WIFI_HOTSPOT,
    MOBILE_DATA
}
