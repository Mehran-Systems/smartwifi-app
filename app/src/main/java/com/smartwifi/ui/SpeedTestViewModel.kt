package com.smartwifi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwifi.logic.FastSpeedTestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    val testManager: FastSpeedTestManager,
    private val dao: com.smartwifi.data.db.SpeedTestDao
) : ViewModel() {

    private var testJob: Job? = null
    private var lastSavedTimestamp = 0L

    init {
        // Reset state when entering the screen (as requested by user)
        // But wait, if configuration change happens we don't want to reset running test?
        // User said: "when i go back to the app and comeback to the speedtest page it still shows...".
        // This implies navigation return. ViewModel might survive if scoped to NavGraph properly.
        // Usually safe to reset if Idle or Finished. If Running, keep it.
        if (testManager.testState.value !is FastSpeedTestManager.TestState.Running) {
             testManager.reset()
        }
        
        // Auto-save logic
        viewModelScope.launch {
            testManager.testState.collect { state ->
                if (state is FastSpeedTestManager.TestState.Finished) {
                    val metrics = testManager.metricData.value
                    // Avoid duplicate saves if flow re-emits same state object
                    val now = System.currentTimeMillis()
                    if (now - lastSavedTimestamp > 2000) { // Simple debounce
                        lastSavedTimestamp = now
                        val result = com.smartwifi.data.db.SpeedTestResult(
                            timestamp = now,
                            downloadSpeed = state.downloadSpeed,
                            uploadSpeed = state.uploadSpeed,
                            clientIp = metrics.clientIp,
                            serverLocation = metrics.serverLocation,
                            ping = metrics.idlePing,
                            jitter = metrics.jitter,
                            packetLoss = metrics.packetLoss
                        )
                        dao.insertResult(result)
                    }
                }
            }
        }
    }

    fun startTest() {
        testJob?.cancel() // Ensure strict single run
        testJob = viewModelScope.launch {
            try {
                testManager.startSpeedTest()
            } catch (e: Exception) {
                // Handled in Manager, but good safety
            }
        }
    }
    
    fun cancelTest() {
        testJob?.cancel()
        testManager.reset()
    }
    
    fun reset() {
        testManager.reset()
    }
}
