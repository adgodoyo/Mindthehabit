package com.example.mindthehabit.data.dsp

import com.example.mindthehabit.data.classification.EventClassifier
import com.example.mindthehabit.data.health.HealthConnectManager
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.SleepStageEntity
import com.example.mindthehabit.data.models.*
import com.example.mindthehabit.data.usage.ScreenTimeManager
import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Feature Extractor - Calculates behavioral metrics from raw sensor data
 *
 * Key Features:
 * - Late-Night Phone Index (0-1): In-bed scrolling intensity
 * - Morning Activity Score (0-100): Exercise and early activity
 * - Sleep Recovery Score (0-100): Sleep quality and duration
 * - Recovery Score (0-100): HRV-based physiological recovery
 */
@Singleton
class FeatureExtractor @Inject constructor(
    private val dao: BehaviorDao,
    private val healthConnectManager: HealthConnectManager,
    private val screenTimeManager: ScreenTimeManager,
    private val eventClassifier: EventClassifier,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "FeatureExtractor"
    }

    /**
     * Extract all behavioral features for a specific date
     */
    suspend fun extractFeatures(date: LocalDate): BehavioralFeatures {
        // Calculate individual features
        val lateNightIndex = calculateLateNightPhoneIndex(date)
        val morningActivityScore = calculateMorningActivityScore(date)
        val sleepScore = calculateSleepRecoveryScore(date)
        val recoveryScore = calculateHRVRecoveryScore(date)
        val behaviorScore = calculateOverallBehaviorScore(lateNightIndex, morningActivityScore, sleepScore, recoveryScore)

        // Calculate energy level based on sleep quality, HRV, and activity
        val energyMetrics = calculateEnergyLevel(
            sleepQuality = sleepScore.sleepQualityScore ?: 50,
            hrvMean = recoveryScore.hrvMean,
            restingHR = recoveryScore.restingHR,
            previousDayActiveMinutes = morningActivityScore.exerciseMinutes
        )

        // Get total spending
        val spending = getTotalSpending(date)

        // Build raw metrics JSON
        val rawMetrics = buildRawMetrics(date)

        return BehavioralFeatures(
            date = date.toString(),
            lateNightPhoneIndex = lateNightIndex.score,
            morningActivityScore = morningActivityScore.score,
            sleepScore = sleepScore.score,
            recoveryScore = recoveryScore.score,
            totalSpending = spending,
            behaviorScore = behaviorScore,
            hrvMean = recoveryScore.hrvMean,
            hrvStdDev = recoveryScore.hrvStdDev,
            restingHR = recoveryScore.restingHR,
            rawMetrics = rawMetrics,
            dataQuality = assessDataQuality(lateNightIndex, morningActivityScore, sleepScore, recoveryScore),
            sleepQualityScore = sleepScore.sleepQualityScore,
            samsungSleepScore = sleepScore.samsungSleepScore,
            energyLevelScore = energyMetrics?.score,
            energyLevel = energyMetrics?.level
        )
    }

    /**
     * Calculate Late-Night Phone Index (0-1 scale)
     *
     * Factors:
     * - Total screen time between 11 PM - 4 AM
     * - Number of unlocks
     * - Session fragmentation (many short sessions = worse)
     * - Context (in-bed scrolling weighted higher than social events)
     */
    private suspend fun calculateLateNightPhoneIndex(date: LocalDate): LateNightPhoneMetrics {
        val lateNightStart = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lateNightEnd = date.plusDays(1).atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Get usage stats
        val usageSession = screenTimeManager.getLateNightUsage(date)

        // Get classified events to distinguish in-bed vs social
        val lateNightStats = eventClassifier.getLateNightScrollingStats(date)

        // Calculate total minutes (weighted by context)
        val inBedMinutes = lateNightStats.inBedScrollingMinutes
        val socialMinutes = lateNightStats.socialMinutes * 0.3f // Social events less impactful

        val weightedTotalMinutes = inBedMinutes + socialMinutes

        // Normalize to 0-1 scale
        // 3 hours (180 min) of in-bed scrolling = index of 1.0
        val durationComponent = (weightedTotalMinutes / 180f).coerceIn(0f, 1f)

        // Unlock frequency component
        // 50 unlocks = score of 1.0
        val unlockComponent = (usageSession.unlockCount / 50f).coerceIn(0f, 1f)

        // Session fragmentation component
        // Many short sessions indicate restless scrolling
        val avgSessionDuration = if (usageSession.sessionDurations.isNotEmpty()) {
            usageSession.sessionDurations.average()
        } else {
            0.0
        }
        val fragmentationComponent = if (avgSessionDuration < 5) 0.3f else 0f // Sessions < 5 min

        // Combined index (weighted)
        val index = (durationComponent * 0.6f +
                     unlockComponent * 0.3f +
                     fragmentationComponent * 0.1f).coerceIn(0f, 1f)

        return LateNightPhoneMetrics(
            score = index,
            totalMinutes = weightedTotalMinutes.toInt(),
            unlockCount = usageSession.unlockCount,
            inBedMinutes = lateNightStats.inBedScrollingMinutes.toFloat(),
            socialMinutes = lateNightStats.socialMinutes.toFloat(),
            topApps = usageSession.topApps
        )
    }

    /**
     * Calculate Morning Activity Score (0-100 scale)
     *
     * Factors:
     * - Exercise sessions (6-10 AM)
     * - Step count in morning hours
     * - Intensity of workout (calories burned)
     */
    private suspend fun calculateMorningActivityScore(date: LocalDate): MorningActivityMetrics {
        val morningStart = date.atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant()
        val morningEnd = date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()

        android.util.Log.d(TAG, "=== Calculating Morning Activity for $date ===")

        // Get exercise sessions
        val exerciseSessions = healthConnectManager.getMorningExerciseSessions(morningStart, morningEnd)
        android.util.Log.d(TAG, "Found ${exerciseSessions.size} exercise sessions")

        // Get step count
        val morningSteps = healthConnectManager.getTotalSteps(morningStart, morningEnd)
        android.util.Log.d(TAG, "Found $morningSteps morning steps")

        // Calculate score
        var score = 0f

        // Exercise component (up to 60 points)
        if (exerciseSessions.isNotEmpty()) {
            val totalExerciseMinutes = exerciseSessions.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }

            // 30+ minutes of exercise = 60 points
            val exercisePoints = min(totalExerciseMinutes / 30.0, 1.0).toFloat() * 60f
            score += exercisePoints
            android.util.Log.d(TAG, "Exercise: $totalExerciseMinutes mins → $exercisePoints points")
        }

        // Step component (up to 40 points)
        // 2000 steps in morning = 40 points
        val stepPoints = (morningSteps / 2000f).coerceAtMost(1f) * 40f
        score += stepPoints
        android.util.Log.d(TAG, "Steps: $morningSteps → $stepPoints points")
        android.util.Log.d(TAG, "FINAL Morning Activity Score: $score/100")

        return MorningActivityMetrics(
            score = score.coerceIn(0f, 100f),
            exerciseCount = exerciseSessions.size,
            exerciseMinutes = exerciseSessions.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }.toInt(),
            morningSteps = morningSteps.toInt(),
            exerciseTypes = exerciseSessions.map { it.exerciseType.toString() }
        )
    }

    /**
     * Calculate Sleep Recovery Score (0-100 scale)
     *
     * Factors:
     * - Total sleep duration
     * - Sleep quality (deep sleep %)
     * - Sleep consistency
     *
     * NEW: Also extracts Samsung Health's sleep score and calculates comprehensive quality metrics
     */
    private suspend fun calculateSleepRecoveryScore(date: LocalDate): SleepRecoveryMetrics {
        val sleepStart = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()
        val sleepEnd = date.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

        val sleepSessions = healthConnectManager.readSleepSessions(sleepStart, sleepEnd)

        val primarySleep = sleepSessions.maxByOrNull {
            java.time.Duration.between(it.startTime, it.endTime).toMinutes()
        } ?: return SleepRecoveryMetrics(
            score = 0f,
            totalMinutes = 0,
            deepSleepMinutes = 0,
            remSleepMinutes = 0,
            awakeMinutes = 0,
            efficiency = 0f,
            sleepQualityScore = null,
            samsungSleepScore = null,
            deepSleepPercent = null,
            remSleepPercent = null,
            wakeEpisodes = null
        )

        val totalMinutes = healthConnectManager.calculateSleepDuration(primarySleep)
        val sleepStages = healthConnectManager.getSleepStagesDetailed(primarySleep)

        // Store sleep stages in database
        val sleepStageEntities = sleepStages.map { stage ->
            SleepStageEntity(
                date = date.toString(),
                startTime = stage.startTime.toEpochMilli(),
                endTime = stage.endTime.toEpochMilli(),
                stage = when (stage.stage) {
                    1 -> "AWAKE"
                    2 -> "LIGHT"
                    3 -> "REM"
                    4 -> "DEEP"
                    else -> "UNKNOWN"
                },
                durationMinutes = stage.durationMinutes
            )
        }
        dao.insertSleepStages(sleepStageEntities)

        val deepSleepMinutes = sleepStages.filter { it.stage == 4 }.sumOf { it.durationMinutes }
        val remSleepMinutes = sleepStages.filter { it.stage == 3 }.sumOf { it.durationMinutes }
        val awakeMinutes = sleepStages.filter { it.stage == 1 }.sumOf { it.durationMinutes }

        // Calculate score
        // Duration component (0-60 points): 8 hours = 60 points
        val durationScore = (totalMinutes / 480f).coerceIn(0f, 1f) * 60f

        // Quality component (0-40 points): Based on deep sleep %
        val deepSleepPercent = deepSleepMinutes.toFloat() / totalMinutes.coerceAtLeast(1)
        // 15-20% deep sleep is ideal
        val qualityScore = if (deepSleepPercent >= 0.15f) {
            40f
        } else {
            (deepSleepPercent / 0.15f) * 40f
        }

        val totalScore = durationScore + qualityScore

        val efficiency = if (totalMinutes > 0) {
            ((totalMinutes - awakeMinutes).toFloat() / totalMinutes) * 100
        } else {
            0f
        }

        // NEW: Try to extract Samsung Health's official sleep score from metadata
        val samsungScore = healthConnectManager.extractSamsungSleepScore(primarySleep)

        // NEW: Calculate our comprehensive sleep quality score
        val sleepQualityMetrics = healthConnectManager.calculateSleepQualityScore(primarySleep, sleepStages)

        return SleepRecoveryMetrics(
            score = totalScore.coerceIn(0f, 100f),
            totalMinutes = totalMinutes,
            deepSleepMinutes = deepSleepMinutes,
            remSleepMinutes = remSleepMinutes,
            awakeMinutes = awakeMinutes,
            efficiency = efficiency,
            sleepQualityScore = sleepQualityMetrics.overallScore,
            samsungSleepScore = samsungScore,
            deepSleepPercent = sleepQualityMetrics.deepSleepPercent,
            remSleepPercent = sleepQualityMetrics.remSleepPercent,
            wakeEpisodes = sleepQualityMetrics.wakeEpisodes
        )
    }

    /**
     * Calculate Energy Level Score (0-100 scale)
     *
     * Factors:
     * - Sleep quality (40%)
     * - HRV recovery (30%)
     * - Resting heart rate (20%)
     * - Previous day activity load (10%)
     */
    private fun calculateEnergyLevel(
        sleepQuality: Int,
        hrvMean: Float?,
        restingHR: Float?,
        previousDayActiveMinutes: Int
    ): com.example.mindthehabit.data.health.EnergyLevelMetrics? {
        return try {
            healthConnectManager.calculateEnergyLevel(
                sleepQuality = sleepQuality,
                hrvMean = hrvMean,
                restingHR = restingHR,
                previousDayActiveMinutes = previousDayActiveMinutes
            )
        } catch (e: Exception) {
            // If energy calculation fails, return null (non-critical)
            null
        }
    }

    /**
     * Calculate HRV-based Recovery Score (0-100 scale)
     *
     * Factors:
     * - Heart Rate Variability (RMSSD)
     * - Resting heart rate
     */
    private suspend fun calculateHRVRecoveryScore(date: LocalDate): HRVRecoveryMetrics {
        val morningStart = date.atTime(0, 0).atZone(ZoneId.systemDefault()).toInstant()
        val morningEnd = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant()

        // Get HRV metrics
        val hrvMetrics = healthConnectManager.calculateHeartRateVariability(morningStart, morningEnd)

        // Get resting heart rate
        val restingHR = healthConnectManager.getRestingHeartRate(morningStart, morningEnd)

        if (hrvMetrics == null || restingHR == null) {
            return HRVRecoveryMetrics(
                score = 50f, // Neutral default
                hrvMean = null,
                hrvStdDev = null,
                restingHR = null
            )
        }

        // HRV component (0-60 points)
        // RMSSD: 20-100 ms is typical range, higher = better recovery
        val hrvScore = ((hrvMetrics.rmssd - 20) / 80 * 60).coerceIn(0f, 60f)

        // Resting HR component (0-40 points)
        // Lower resting HR = better fitness/recovery
        // 50 BPM = excellent (40 pts), 80 BPM = poor (0 pts)
        val rhrScore = ((80 - restingHR) / 30 * 40).coerceIn(0f, 40f)

        val totalScore = hrvScore + rhrScore

        return HRVRecoveryMetrics(
            score = totalScore.coerceIn(0f, 100f),
            hrvMean = hrvMetrics.rmssd,
            hrvStdDev = hrvMetrics.stdDevRR,
            restingHR = restingHR
        )
    }

    /**
     * Calculate overall behavior score (composite)
     *
     * Weights adjusted based on whether HRV data is available:
     * - With HRV: Late Night 30%, Morning 25%, Sleep 25%, Recovery 20%
     * - Without HRV: Late Night 35%, Morning 35%, Sleep 30%
     */
    private fun calculateOverallBehaviorScore(
        lateNightIndex: LateNightPhoneMetrics,
        morningActivity: MorningActivityMetrics,
        sleep: SleepRecoveryMetrics,
        recovery: HRVRecoveryMetrics
    ): Float {
        // Invert late-night index (lower is better)
        val lateNightComponent = (1 - lateNightIndex.score) * 100

        // Check if HRV data is available
        val hasHRVData = recovery.hrvMean != null

        return if (hasHRVData) {
            // Include recovery score when HRV data available
            (lateNightComponent * 0.30f +
             morningActivity.score * 0.25f +
             sleep.score * 0.25f +
             recovery.score * 0.20f).coerceIn(0f, 100f)
        } else {
            // Fallback weights when no HRV data
            (lateNightComponent * 0.35f +
             morningActivity.score * 0.35f +
             sleep.score * 0.30f).coerceIn(0f, 100f)
        }
    }

    /**
     * Get total spending for a date
     */
    private suspend fun getTotalSpending(date: LocalDate): Double {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return dao.getSpendingBetween(startOfDay, endOfDay).sumOf { it.amount }
    }

    /**
     * Build raw metrics JSON for detailed analysis
     */
    private suspend fun buildRawMetrics(date: LocalDate): String {
        val metrics = mutableMapOf<String, Any>()

        // Add processed metric details
        // This can be expanded based on needs

        return gson.toJson(metrics)
    }

    /**
     * Assess data quality for this date
     */
    private fun assessDataQuality(
        lateNight: LateNightPhoneMetrics,
        morning: MorningActivityMetrics,
        sleep: SleepRecoveryMetrics,
        recovery: HRVRecoveryMetrics
    ): DataQuality {
        var availableDataPoints = 0
        val totalDataPoints = 4

        if (lateNight.totalMinutes >= 0) availableDataPoints++
        if (morning.score > 0) availableDataPoints++
        if (sleep.totalMinutes > 0) availableDataPoints++
        if (recovery.hrvMean != null) availableDataPoints++

        val completeness = availableDataPoints.toFloat() / totalDataPoints

        return when {
            completeness >= 0.8f -> DataQuality.HIGH
            completeness >= 0.5f -> DataQuality.MEDIUM
            else -> DataQuality.LOW
        }
    }
}
