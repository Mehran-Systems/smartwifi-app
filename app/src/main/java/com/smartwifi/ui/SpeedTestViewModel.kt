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
    val testManager: FastSpeedTestManager
) : ViewModel() {

    private var testJob: Job? = null

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
