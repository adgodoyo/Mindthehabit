package com.example.mindthehabit.data.models

/**
 * Shared data models for behavioral analysis
 */

/**
 * Complete behavioral features for a date
 */
data class BehavioralFeatures(
    val date: String,
    val lateNightPhoneIndex: Float,
    val morningActivityScore: Float,
    val sleepScore: Float,
    val recoveryScore: Float,
    val totalSpending: Double,
    val behaviorScore: Float,
    val hrvMean: Float?,
    val hrvStdDev: Float?,
    val restingHR: Float?,
    val rawMetrics: String,
    val dataQuality: DataQuality,
    // New comprehensive sleep and energy metrics
    val sleepQualityScore: Int? = null,
    val samsungSleepScore: Int? = null,
    val energyLevelScore: Int? = null,
    val energyLevel: String? = null
)

data class LateNightPhoneMetrics(
    val score: Float,
    val totalMinutes: Int,
    val unlockCount: Int,
    val inBedMinutes: Float,
    val socialMinutes: Float,
    val topApps: List<String>
)

data class MorningActivityMetrics(
    val score: Float,
    val exerciseCount: Int,
    val exerciseMinutes: Int,
    val morningSteps: Int,
    val exerciseTypes: List<String>
)

data class SleepRecoveryMetrics(
    val score: Float,
    val totalMinutes: Int,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val awakeMinutes: Int,
    val efficiency: Float,
    // New comprehensive sleep quality metrics
    val sleepQualityScore: Int? = null,
    val samsungSleepScore: Int? = null,
    val deepSleepPercent: Int? = null,
    val remSleepPercent: Int? = null,
    val wakeEpisodes: Int? = null
)

data class HRVRecoveryMetrics(
    val score: Float,
    val hrvMean: Float?,
    val hrvStdDev: Float?,
    val restingHR: Float?
)

enum class DataQuality {
    HIGH,   // 80%+ data available
    MEDIUM, // 50-80% data available
    LOW     // <50% data available
}
