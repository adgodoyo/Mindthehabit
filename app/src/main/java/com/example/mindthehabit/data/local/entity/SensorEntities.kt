package com.example.mindthehabit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "light_readings",
    indices = [Index(value = ["timestamp"])]
)
data class LightReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val lux: Float
)

@Entity(
    tableName = "wifi_events",
    indices = [Index(value = ["timestamp"])]
)
data class WifiEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val ssid: String?,
    val bssid: String?,
    val signalStrength: Int,
    val eventType: String // CONNECTED, DISCONNECTED, STRENGTH_CHANGE
)

@Entity(
    tableName = "screen_events",
    indices = [Index(value = ["timestamp"])]
)
data class ScreenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String, // ON, OFF, UNLOCK
    val sessionContext: String? = null // LATE_NIGHT_HOME, LATE_NIGHT_SOCIAL, DAYTIME, MORNING_ACTIVE
)

@Entity(
    tableName = "spending_entries",
    indices = [Index(value = ["timestamp"])]
)
data class SpendingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amount: Double,
    val category: String,
    val note: String?
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val sleepScore: Float,
    val morningActivityScore: Float,
    val lateNightPhoneIndex: Float,
    val totalSpending: Double,
    val recoveryScore: Float,
    val behaviorScore: Float,
    val rawMetricsJson: String, // For flexible storage of additional DSP results
    val hrvMean: Float? = null,
    val hrvStdDev: Float? = null,
    val restingHR: Float? = null,
    // New comprehensive sleep and energy metrics
    val sleepQualityScore: Int? = null,      // Our calculated sleep quality (0-100)
    val samsungSleepScore: Int? = null,      // Samsung Health's official score if available
    val energyLevelScore: Int? = null,       // Energy level score (0-100)
    val energyLevel: String? = null          // Energy category: Excellent, Good, Fair, Low, Very Low
)

@Entity(
    tableName = "app_usage_events",
    indices = [Index(value = ["timestamp"])]
)
data class AppUsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val eventType: String // SESSION_START, SESSION_END
)

@Entity(
    tableName = "sleep_stages",
    indices = [Index(value = ["date"]), Index(value = ["startTime"])]
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val startTime: Long,
    val endTime: Long,
    val stage: String, // AWAKE, LIGHT, DEEP, REM, UNKNOWN
    val durationMinutes: Int
)

@Entity(
    tableName = "exercise_sessions",
    indices = [Index(value = ["timestamp"]), Index(value = ["date"])]
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String, // YYYY-MM-DD
    val exerciseType: String, // WALKING, RUNNING, CYCLING, etc.
    val durationMinutes: Int,
    val calories: Float?
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "gps_locations",
    indices = [Index(value = ["timestamp"])]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String  // GPS, NETWORK, FUSED, etc.
)
