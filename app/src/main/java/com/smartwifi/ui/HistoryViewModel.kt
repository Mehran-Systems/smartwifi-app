package com.smartwifi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwifi.data.db.SpeedTestDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: SpeedTestDao
) : ViewModel() {
    val history = dao.getAllResults()

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }
}
