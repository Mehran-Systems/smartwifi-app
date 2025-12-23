package com.smartwifi.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_history")
data class SpeedTestResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val downloadSpeed: Double,
    val uploadSpeed: Double,
    val clientIp: String?,
    val serverLocation: String?,
    val ping: Int?,
    val jitter: Int?,
    val packetLoss: Double?
)
