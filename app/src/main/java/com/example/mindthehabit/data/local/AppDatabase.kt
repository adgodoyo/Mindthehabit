package com.example.mindthehabit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.*

@Database(
    entities = [
        LightReadingEntity::class,
        WifiEventEntity::class,
        ScreenEventEntity::class,
        SpendingEntryEntity::class,
        DailySummaryEntity::class,
        AppUsageEventEntity::class,
        SleepStageEntity::class,
        ExerciseSessionEntity::class,
        SettingsEntity::class,
        LocationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun behaviorDao(): BehaviorDao
}
