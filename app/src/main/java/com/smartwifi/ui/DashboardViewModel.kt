package com.smartwifi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwifi.data.SmartWifiRepository
import com.smartwifi.data.model.AppUiState
import com.smartwifi.logic.WifiActionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SmartWifiRepository,
    private val signalSensor: com.smartwifi.logic.SignalDirectionSensor,
    private val speedTestManager: com.smartwifi.logic.FastSpeedTestManager,
    private val wifiActionManager: WifiActionManager
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = repository.uiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppUiState()
        )

    val compassHeading = signalSensor.azimuth
    val targetBearing = signalSensor.targetBearing

    private val _showStartupDialog = MutableStateFlow(false)
    val showStartupDialog: StateFlow<Boolean> = _showStartupDialog.asStateFlow()
    
    val notificationEvents = repository.notificationEvents

    private val _openPanelEvent = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    val openPanelEvent = _openPanelEvent.receiveAsFlow()

    init {
        // Trigger initial scan
        wifiActionManager.startScan()
        viewModelScope.launch {
            repository.uiState.collect { state ->
                signalSensor.onRssiUpdate(state.signalStrength)
            }
        }

        viewModelScope.launch {
            try {
                speedTestManager.fetchMetadata()
            } catch (e: Exception) {
            }
        }
        
        // Trigger a scan on startup
        wifiActionManager.startScan()
    }

    override fun onCleared() {
        super.onCleared()
        signalSensor.stopListening()
    }

    fun dismissStartupDialog() {
        _showStartupDialog.value = false
    }

    fun showAvailableNetworks() {
        // Trigger UI event to open panel from Activity context
        _openPanelEvent.trySend(Unit)
    }

    fun connectToNetwork(ssid: String, bssid: String) {
        viewModelScope.launch {
            wifiActionManager.connectToNetwork(ssid, bssid)
            dismissStartupDialog()
        }
    }

    fun performPendingSwitch() {
        val network = uiState.value.pendingSwitchNetwork ?: return
        viewModelScope.launch {
            wifiActionManager.connectToNetwork(network.ssid, network.bssid)
            repository.clearPendingSwitch()
        }
    }

    fun clearPendingSwitch() {
        repository.clearPendingSwitch()
    }

    fun setSensitivity(value: Int) { repository.setSensitivity(value) }
    fun setMinSignalDiff(value: Int) { repository.setMinSignalDiff(value) }
    fun setMobileDataThreshold(value: Int) { repository.setMobileDataThreshold(value) }
    fun setGeofencing(enabled: Boolean) { repository.setGeofencing(enabled) }
    fun toggleService(enabled: Boolean) { repository.updateServiceStatus(enabled) }
    
    fun toggleGamingMode() {
        val current = uiState.value.isGamingMode
        repository.setGamingMode(!current)
    }

    fun set5GhzPriorityEnabled(enabled: Boolean) { repository.set5GhzPriorityEnabled(enabled) }
    fun setFiveGhzThreshold(value: Int) { repository.setFiveGhzThreshold(value) }
    fun setThemeMode(mode: String) { repository.setThemeMode(mode) }
    fun setThemeColors(bg: Long, accent: Long) { repository.setThemeColors(bg, accent) }
    fun resetSettings() { repository.resetToDefaults() }
    fun setHotspotSwitchingEnabled(enabled: Boolean) { repository.setHotspotSwitchingEnabled(enabled) }
    fun setBadgeSensitivity(value: Int) { repository.setBadgeSensitivity(value) }

    fun getLogs(): String = repository.getDebugLogs()
    fun clearLogs() = repository.clearDebugLogs()
}
