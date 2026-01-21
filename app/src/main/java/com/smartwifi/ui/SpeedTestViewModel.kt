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
        // Reset state if not running to clean up previous results
        if (testManager.testState.value !is FastSpeedTestManager.TestState.Running) {
             testManager.reset()
        }
        
        // Pre-fetch metadata (Client IP, Server Info) so it's visible before test starts
        viewModelScope.launch {
            testManager.fetchMetadata()
        }
        
        // Auto-save logic
        viewModelScope.launch {
            testManager.testState.collect { state ->
                if (state is FastSpeedTestManager.TestState.Finished) {
                    val metrics = testManager.metricData.value
                    val now = System.currentTimeMillis()
                    if (now - lastSavedTimestamp > 2000) {
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
        testJob?.cancel() 
        testJob = viewModelScope.launch {
            try {
                testManager.startSpeedTest()
            } catch (e: Exception) {
                // Error handled in manager
            }
        }
    }
    
    fun cancelTest() {
        testJob?.cancel()
        testManager.reset()
    }
}
