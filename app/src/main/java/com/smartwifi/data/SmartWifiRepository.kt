package com.smartwifi.data

import com.smartwifi.data.model.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartWifiRepository @Inject constructor() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

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

    fun setZombieDetected(detected: Boolean) {
        _uiState.update { it.copy(isZombieDetected = detected) }
    }

    fun setDataFallback(active: Boolean) {
        _uiState.update { it.copy(isDataFallback = active) }
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

    fun updateProbationList(list: List<com.smartwifi.data.model.ProbationItem>) {
        _uiState.update { it.copy(probationList = list) }
    }

    fun updateSavedNetworks(list: List<com.smartwifi.data.model.SavedNetworkItem>) {
        _uiState.update { it.copy(savedNetworks = list) }
    }

    fun updateLinkSpeed(speed: Int) {
        _uiState.update { it.copy(linkSpeed = speed) }
    }
    
    fun setSensitivity(value: Int) {
        _uiState.update { it.copy(sensitivity = value) }
    }
    
    fun setMobileDataThreshold(value: Int) {
        _uiState.update { it.copy(mobileDataThreshold = value) }
    }
    
    fun setGeofencing(enabled: Boolean) {
        _uiState.update { it.copy(isGeofencingEnabled = enabled) }
    }

    fun setMinSignalDiff(diff: Int) {
        _uiState.update { it.copy(minSignalDiff = diff) }
    }

    fun updateTrafficStats(linkSpeed: Int, currentUsage: String) {
        _uiState.update { 
            it.copy(
                linkSpeed = linkSpeed,
                currentUsage = currentUsage
            ) 
        }
    }

    fun setHotspotSwitchingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isHotspotSwitchingEnabled = enabled) }
    }

    fun set5GhzPriorityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(is5GhzPriorityEnabled = enabled) }
    }

    fun setFiveGhzThreshold(value: Int) {
        _uiState.update { it.copy(fiveGhzThreshold = value) }
    }
}
