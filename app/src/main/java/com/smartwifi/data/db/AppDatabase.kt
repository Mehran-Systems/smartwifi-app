package com.smartwifi.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpeedTestResult::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speedTestDao(): SpeedTestDao
}
