package com.smartwifi.data.model

data class AppUiState(
    val isServiceRunning: Boolean = false,
    val currentSsid: String = "Searching...",
    val signalStrength: Int = 0,
    val frequencyBand: String = "2.4GHz", // New field for Bandwidth
    val internetStatus: String = "Checking...",
    val activeMode: String = "Stationary (Home/Office)",
    // Smart Logic States
    val isZombieDetected: Boolean = false,
    val isDataFallback: Boolean = false,
    val lastAction: String = "Monitoring...",
    // UI.txt specific fields
    val connectionSource: ConnectionSource = ConnectionSource.WIFI_ROUTER,
    val isGamingMode: Boolean = false,
    val probationList: List<ProbationItem> = emptyList(),
    val availableNetworks: List<AvailableNetworkItem> = emptyList(),
    // Advanced Settings & Metrics
    val linkSpeed: Int = 0, // Mbps
    val currentUsage: String = "0 KB/s", // Throughput
    val sensitivity: Int = 50, // 0-100 (Mapped to dBm range in UI)
    val mobileDataThreshold: Int = 5, // Mbps
    val isGeofencingEnabled: Boolean = false,
    val is5GhzPriorityEnabled: Boolean = true, // Default Enabled
    val fiveGhzThreshold: Int = -75, // Configurable Threshold (dBm)
    val minSignalDiff: Int = 10, // dB
    val isHotspotSwitchingEnabled: Boolean = false, // Default to allowing hotspots
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val themeBackground: Long = 0xFF121212, // Default Dark Background
    val themeAccent: Long = 0xFFBB86FC // Default Accent (Purple)
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
