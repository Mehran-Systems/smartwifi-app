package com.smartwifi.data

import com.smartwifi.data.model.AppUiState
import com.smartwifi.data.model.AvailableNetworkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartWifiRepository @Inject constructor(
    private val debugger: com.smartwifi.logic.SmartWifiDebugger
) {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun getDebugLogs(): String = debugger.getLogContent()
    fun clearDebugLogs() = debugger.clearLogs()

    fun updateServiceStatus(isRunning: Boolean) {
        _uiState.update { it.copy(isServiceRunning = isRunning) }
    }

    fun updateNetworkInfo(ssid: String, signalStrength: Int, frequencyBand: String = "2.4GHz") {
        _uiState.update { 
            it.copy(
                currentSsid = ssid,
                signalStrength = signalStrength,
                frequencyBand = frequencyBand
            ) 
        }
    }

    fun updateInternetStatus(status: String) {
        _uiState.update { it.copy(internetStatus = status) }
    }
    
    fun updateActiveMode(mode: String) {
        _uiState.update { it.copy(activeMode = mode) }
    }

    fun updateLastAction(action: String) {
        _uiState.update { it.copy(lastAction = action) }
    }

    fun updateConnectionSource(source: com.smartwifi.data.model.ConnectionSource) {
        _uiState.update { it.copy(connectionSource = source) }
    }

    fun setGamingMode(enabled: Boolean) {
        _uiState.update { it.copy(isGamingMode = enabled) }
    }

    fun updateTrafficStats(linkSpeed: Int, currentUsage: String) {
        _uiState.update { 
            it.copy(
                linkSpeed = linkSpeed,
                currentUsage = currentUsage
            ) 
        }
    }

    fun setPendingSwitch(network: AvailableNetworkItem?) {
        _uiState.update { it.copy(pendingSwitchNetwork = network) }
    }

    fun clearPendingSwitch() {
        _uiState.update { it.copy(pendingSwitchNetwork = null) }
    }

    fun updateAvailableNetworks(list: List<AvailableNetworkItem>) {
        _uiState.update { it.copy(availableNetworks = list) }
    }

    fun setSensitivity(value: Int) { _uiState.update { it.copy(sensitivity = value) } }
    fun setMobileDataThreshold(value: Int) { _uiState.update { it.copy(mobileDataThreshold = value) } }
    fun setMinSignalDiff(diff: Int) { _uiState.update { it.copy(minSignalDiff = diff) } }
    fun set5GhzPriorityEnabled(enabled: Boolean) { _uiState.update { it.copy(is5GhzPriorityEnabled = enabled) } }
    fun setFiveGhzThreshold(value: Int) { _uiState.update { it.copy(fiveGhzThreshold = value) } }
    fun setHotspotSwitchingEnabled(enabled: Boolean) { _uiState.update { it.copy(isHotspotSwitchingEnabled = enabled) } }
    fun setThemeMode(mode: String) { _uiState.update { it.copy(themeMode = mode) } }
    fun setThemeColors(bg: Long, accent: Long) { _uiState.update { it.copy(themeBackground = bg, themeAccent = accent) } }
    fun setGeofencing(enabled: Boolean) { _uiState.update { it.copy(isGeofencingEnabled = enabled) } }
    fun setBadgeSensitivity(value: Int) { _uiState.update { it.copy(badgeSensitivity = value) } }

    private val _notificationEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val notificationEvents = _notificationEvents.receiveAsFlow()

    suspend fun sendNotificationEvent(message: String) {
        _notificationEvents.send(message)
    }

    fun resetToDefaults() {
        val defaults = AppUiState()
        _uiState.update { current ->
            current.copy(
                sensitivity = defaults.sensitivity,
                mobileDataThreshold = defaults.mobileDataThreshold,
                minSignalDiff = defaults.minSignalDiff,
                is5GhzPriorityEnabled = defaults.is5GhzPriorityEnabled,
                fiveGhzThreshold = defaults.fiveGhzThreshold,
                isHotspotSwitchingEnabled = defaults.isHotspotSwitchingEnabled,
                themeMode = defaults.themeMode,
                themeBackground = defaults.themeBackground,
                themeAccent = defaults.themeAccent,
                badgeSensitivity = defaults.badgeSensitivity
            )
        }
    }
}
