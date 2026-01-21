package com.smartwifi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_history ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<SpeedTestResult>>

    @Insert
    suspend fun insertResult(result: SpeedTestResult)

    @Query("DELETE FROM speed_test_history")
    suspend fun clearHistory()
}
