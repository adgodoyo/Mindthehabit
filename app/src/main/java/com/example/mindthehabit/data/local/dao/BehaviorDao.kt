package com.example.mindthehabit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mindthehabit.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLightReading(reading: LightReadingEntity)

    @Query("SELECT * FROM light_readings WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getLightReadings(startTime: Long): Flow<List<LightReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWifiEvent(event: WifiEventEntity)

    @Query("SELECT * FROM wifi_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentWifiEvents(): Flow<List<WifiEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenEvent(event: ScreenEventEntity)

    @Query("SELECT * FROM screen_events WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getScreenEvents(startTime: Long): Flow<List<ScreenEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpending(entry: SpendingEntryEntity)

    @Query("SELECT * FROM spending_entries ORDER BY timestamp DESC")
    fun getAllSpending(): Flow<List<SpendingEntryEntity>>

    @Query("UPDATE spending_entries SET timestamp = :timestamp, amount = :amount, category = :category, note = :note WHERE id = :id")
    suspend fun updateSpending(id: Long, timestamp: Long, amount: Double, category: String, note: String?)

    @Query("DELETE FROM spending_entries WHERE id = :id")
    suspend fun deleteSpending(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries ORDER BY date DESC")
    fun getDailySummaries(): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    suspend fun getSummaryForDate(date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summaries WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getDailySummariesBetween(startDate: String, endDate: String): List<DailySummaryEntity>

    // App Usage Events
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsageEvent(event: AppUsageEventEntity)

    @Query("SELECT * FROM app_usage_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getAppUsageEvents(startTime: Long, endTime: Long): List<AppUsageEventEntity>

    @Query("SELECT * FROM app_usage_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY totalTimeMs DESC LIMIT :limit")
    suspend fun getTopAppsByUsage(startTime: Long, endTime: Long, limit: Int): List<AppUsageEventEntity>

    // Sleep Stages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStage(stage: SleepStageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStages(stages: List<SleepStageEntity>)

    @Query("SELECT * FROM sleep_stages WHERE date = :date ORDER BY startTime ASC")
    suspend fun getSleepStagesForDate(date: String): List<SleepStageEntity>

    // Exercise Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSession(session: ExerciseSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSessions(sessions: List<ExerciseSessionEntity>)

    @Query("SELECT * FROM exercise_sessions WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getExerciseSessionsForDate(date: String): List<ExerciseSessionEntity>

    // Screen Events - additional queries
    @Query("SELECT * FROM screen_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getScreenEventsBetween(startTime: Long, endTime: Long): List<ScreenEventEntity>

    @Query("SELECT * FROM wifi_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getWiFiEventsBetween(startTime: Long, endTime: Long): List<WifiEventEntity>

    @Query("SELECT * FROM spending_entries WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getSpendingBetween(startTime: Long, endTime: Long): List<SpendingEntryEntity>

    // Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingsEntity)

    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): SettingsEntity?

    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingsEntity>>

    // GPS Locations
    @Insert
    suspend fun insertLocation(location: LocationEntity)

    @Query("SELECT * FROM gps_locations WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLocationNear(startTime: Long, endTime: Long): LocationEntity?

    @Query("DELETE FROM gps_locations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLocations(cutoffTime: Long)

    // Data Cleanup Queries - Remove old sensor data to prevent database bloat
    @Query("DELETE FROM light_readings WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLightReadings(cutoffTime: Long)

    @Query("DELETE FROM wifi_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldWifiEvents(cutoffTime: Long)

    @Query("DELETE FROM screen_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldScreenEvents(cutoffTime: Long)

    @Query("DELETE FROM app_usage_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldAppUsageEvents(cutoffTime: Long)

    @Query("DELETE FROM sleep_stages WHERE startTime < :cutoffTime")
    suspend fun deleteOldSleepStages(cutoffTime: Long)

    @Query("DELETE FROM exercise_sessions WHERE timestamp < :cutoffTime")
    suspend fun deleteOldExerciseSessions(cutoffTime: Long)

    // Delete all old sensor data at once (recommended for scheduled cleanup jobs)
    suspend fun deleteAllOldSensorData(cutoffTime: Long) {
        deleteOldLightReadings(cutoffTime)
        deleteOldWifiEvents(cutoffTime)
        deleteOldScreenEvents(cutoffTime)
        deleteOldAppUsageEvents(cutoffTime)
        deleteOldSleepStages(cutoffTime)
        deleteOldExerciseSessions(cutoffTime)
        deleteOldLocations(cutoffTime)
    }
}
