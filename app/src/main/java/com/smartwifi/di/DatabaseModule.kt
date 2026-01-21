package com.smartwifi.di

import android.content.Context
import androidx.room.Room
import com.smartwifi.data.db.AppDatabase
import com.smartwifi.data.db.SpeedTestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartwifi_db"
        ).build()
    }

    @Provides
    fun provideSpeedTestDao(database: AppDatabase): SpeedTestDao {
        return database.speedTestDao()
    }
}
