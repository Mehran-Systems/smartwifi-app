package com.smartwifi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwifi.data.SmartWifiRepository
import com.smartwifi.data.model.AppUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SmartWifiRepository,
    private val signalSensor: com.smartwifi.logic.SignalDirectionSensor,
    private val speedTestManager: com.smartwifi.logic.FastSpeedTestManager
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = repository.uiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppUiState()
        )

    // Combining flows or exposing separately. For speed, exposing flow for UI to combine.
    val compassHeading = signalSensor.azimuth
    val targetBearing = signalSensor.targetBearing

    init {
        // We should ideally control this based on lifecycle,
        // but for this demo context we start on init.
        signalSensor.startListening()

        viewModelScope.launch {
            repository.uiState.collect { state ->
                // Pass RSSI to sensor
                signalSensor.onRssiUpdate(state.signalStrength)
            }
        }

        // Silent metadata fetch on app start
        viewModelScope.launch {
            try {
                speedTestManager.fetchMetadata()
            } catch (e: Exception) {
                // Ignore silent fetch errors
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        signalSensor.stopListening()
    }

    fun setSensitivity(value: Int) {
        repository.setSensitivity(value)
    }

    fun setMinSignalDiff(value: Int) {
        repository.setMinSignalDiff(value)
    }

    fun setMobileDataThreshold(value: Int) {
        repository.setMobileDataThreshold(value)
    }

    fun setGeofencing(enabled: Boolean) {
        repository.setGeofencing(enabled)
    }

    fun toggleService(enabled: Boolean) {
        repository.updateServiceStatus(enabled)
    }

    fun toggleGamingMode() {
        // Toggle current state
        val current = uiState.value.isGamingMode
        repository.setGamingMode(!current)
    }

    fun toggleDataFallback() {
        val current = uiState.value.isDataFallback
        repository.setDataFallback(!current)
    }

    fun setHotspotSwitchingEnabled(enabled: Boolean) {
        repository.setHotspotSwitchingEnabled(enabled)
    }

    fun set5GhzPriorityEnabled(enabled: Boolean) {
        repository.set5GhzPriorityEnabled(enabled)
    }

    fun setFiveGhzThreshold(value: Int) {
        repository.setFiveGhzThreshold(value)
    }
}
