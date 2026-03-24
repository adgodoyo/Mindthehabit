package com.example.mindthehabit.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HealthConnectManager"
    }

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    // Core required permissions
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    // Optional permissions (nice to have, but app works without them)
    val optionalPermissions = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        val hasAllRequired = granted.containsAll(permissions)

        // Check optional permissions
        val optionalGranted = optionalPermissions.filter { it in granted }

        // Diagnostic logging
        android.util.Log.d("HealthConnectManager", "=== Permission Check ===")
        android.util.Log.d("HealthConnectManager", "Required permissions: ${permissions.size}")
        android.util.Log.d("HealthConnectManager", "Required granted: ${permissions.count { it in granted }}")
        android.util.Log.d("HealthConnectManager", "Optional permissions: ${optionalPermissions.size}")
        android.util.Log.d("HealthConnectManager", "Optional granted: ${optionalGranted.size}")
        android.util.Log.d("HealthConnectManager", "Has all REQUIRED permissions: $hasAllRequired")

        // Log missing required permissions
        val missingRequired = permissions - granted
        if (missingRequired.isNotEmpty()) {
            android.util.Log.w("HealthConnectManager", "Missing REQUIRED permissions:")
            missingRequired.forEach {
                android.util.Log.w("HealthConnectManager", "  - $it")
            }
        }

        // Log missing optional permissions (info only)
        val missingOptional = optionalPermissions - granted
        if (missingOptional.isNotEmpty()) {
            android.util.Log.i("HealthConnectManager", "Missing optional permissions (app works without these):")
            missingOptional.forEach {
                android.util.Log.i("HealthConnectManager", "  - $it")
            }
        }

        return hasAllRequired
    }

    suspend fun getGrantedPermissions(): Set<String> {
        return healthConnectClient.permissionController.getGrantedPermissions()
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): List<StepsRecord> {
        return try {
            android.util.Log.d(TAG, "Reading steps from $startTime to $endTime")
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            android.util.Log.d(TAG, "Found ${response.records.size} step records, total: ${response.records.sumOf { it.count }} steps")
            response.records
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading steps: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun readSleepSessions(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        return try {
            android.util.Log.d(TAG, "Reading sleep sessions from $startTime to $endTime")
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val totalMinutes = response.records.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }
            android.util.Log.d(TAG, "Found ${response.records.size} sleep sessions, total: $totalMinutes minutes")
            response.records
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading sleep sessions: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Extract Samsung Health sleep quality score from metadata if available
     * Samsung Health stores calculated scores in title/notes fields
     */
    fun extractSamsungSleepScore(sleepSession: SleepSessionRecord): Int? {
        // Check title field for patterns like "Sleep Score: 85" or "Quality: 85"
        sleepSession.title?.let { title ->
            val scorePattern = Regex("""(?:sleep score|quality|score):\s*(\d+)""", RegexOption.IGNORE_CASE)
            scorePattern.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        // Check notes field for similar patterns
        sleepSession.notes?.let { notes ->
            val scorePattern = Regex("""(?:sleep score|quality|score):\s*(\d+)""", RegexOption.IGNORE_CASE)
            scorePattern.find(notes)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        return null
    }

    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        return try {
            android.util.Log.d(TAG, "Reading heart rate from $startTime to $endTime")
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val totalSamples = response.records.sumOf { it.samples.size }
            android.util.Log.d(TAG, "Found ${response.records.size} heart rate records with $totalSamples samples")
            response.records
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading heart rate: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<ExerciseSessionRecord> {
        return try {
            android.util.Log.d(TAG, "Reading exercise sessions from $startTime to $endTime")
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val totalMinutes = response.records.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }
            android.util.Log.d(TAG, "Found ${response.records.size} exercise sessions, total: $totalMinutes minutes")
            response.records
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading exercise sessions: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get detailed sleep stages for a sleep session
     * Returns breakdown of AWAKE, LIGHT, DEEP, REM, UNKNOWN stages
     */
    suspend fun getSleepStagesDetailed(sleepSession: SleepSessionRecord): List<SleepStage> {
        val stages = mutableListOf<SleepStage>()

        sleepSession.stages.forEach { stage ->
            val durationMinutes = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().toInt()

            stages.add(
                SleepStage(
                    startTime = stage.startTime,
                    endTime = stage.endTime,
                    stage = stage.stage,
                    durationMinutes = durationMinutes
                )
            )
        }

        return stages
    }

    /**
     * Calculate Heart Rate Variability from heart rate samples
     * Returns mean and standard deviation of RR intervals (RMSSD approximation)
     */
    suspend fun calculateHeartRateVariability(startTime: Instant, endTime: Instant): HRVMetrics? {
        val heartRateRecords = readHeartRate(startTime, endTime)

        if (heartRateRecords.isEmpty()) return null

        // Extract all heart rate samples
        val heartRateSamples = heartRateRecords.flatMap { record ->
            record.samples.map { sample ->
                HeartRateSample(
                    time = sample.time,
                    beatsPerMinute = sample.beatsPerMinute
                )
            }
        }.sortedBy { it.time }

        if (heartRateSamples.size < 2) return null

        // Calculate RR intervals (time between beats in milliseconds)
        val rrIntervals = heartRateSamples.zipWithNext { current, next ->
            // Convert BPM to RR interval (milliseconds between beats)
            60000.0 / current.beatsPerMinute
        }

        if (rrIntervals.isEmpty()) return null

        // Calculate RMSSD (Root Mean Square of Successive Differences)
        val successiveDifferences = rrIntervals.zipWithNext { rr1: Double, rr2: Double ->
            (rr2 - rr1).pow(2.0)
        }

        val rmssd = sqrt(successiveDifferences.average())

        // Calculate mean and std dev of RR intervals
        val meanRR = rrIntervals.average()
        val variance = rrIntervals.map { rr: Double -> (rr - meanRR).pow(2.0) }.average()
        val stdDev = sqrt(variance)

        return HRVMetrics(
            rmssd = rmssd.toFloat(),
            meanRR = meanRR.toFloat(),
            stdDevRR = stdDev.toFloat(),
            sampleCount = rrIntervals.size
        )
    }

    /**
     * Calculate resting heart rate from morning heart rate samples
     */
    suspend fun getRestingHeartRate(startTime: Instant, endTime: Instant): Float? {
        val heartRateRecords = readHeartRate(startTime, endTime)

        if (heartRateRecords.isEmpty()) return null

        val allSamples = heartRateRecords.flatMap { record ->
            record.samples.map { it.beatsPerMinute.toDouble() }
        }

        if (allSamples.isEmpty()) return null

        // Resting HR is typically the lowest 10th percentile of morning readings
        val sorted = allSamples.sorted()
        val percentile10Index = (sorted.size * 0.1).toInt().coerceAtMost(sorted.size - 1)

        return sorted[percentile10Index].toFloat()
    }

    /**
     * Get total steps for a day
     */
    suspend fun getTotalSteps(startTime: Instant, endTime: Instant): Long {
        val stepsRecords = readSteps(startTime, endTime)
        return stepsRecords.sumOf { it.count }
    }

    /**
     * Get morning exercise sessions (6 AM - 10 AM)
     */
    suspend fun getMorningExerciseSessions(startTime: Instant, endTime: Instant): List<ExerciseSessionRecord> {
        val allSessions = readExerciseSessions(startTime, endTime)

        return allSessions.filter { session ->
            val hour = session.startTime.atZone(java.time.ZoneId.systemDefault()).hour
            hour in 6..9
        }
    }

    /**
     * Calculate total sleep duration in minutes
     */
    fun calculateSleepDuration(sleepSession: SleepSessionRecord): Int {
        return java.time.Duration.between(sleepSession.startTime, sleepSession.endTime).toMinutes().toInt()
    }

    /**
     * Get deep sleep duration from sleep stages
     */
    fun getDeepSleepDuration(sleepStages: List<SleepStage>): Int {
        return sleepStages
            .filter { it.stage == 4 } // DEEP sleep stage
            .sumOf { it.durationMinutes }
    }

    /**
     * Read active calories burned (energy expenditure from activity)
     * OPTIONAL: Returns empty list if permission not granted
     */
    suspend fun readActiveCalories(startTime: Instant, endTime: Instant): List<ActiveCaloriesBurnedRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            android.util.Log.i("HealthConnectManager", "Active calories permission not granted (optional)")
            emptyList()
        }
    }

    /**
     * Read total calories burned (includes basal + active)
     */
    suspend fun readTotalCalories(startTime: Instant, endTime: Instant): List<TotalCaloriesBurnedRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    /**
     * Read resting heart rate records (typically one per day)
     * OPTIONAL: Returns empty list if permission not granted
     */
    suspend fun readRestingHeartRateRecords(startTime: Instant, endTime: Instant): List<RestingHeartRateRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            android.util.Log.i("HealthConnectManager", "Resting heart rate permission not granted (optional)")
            emptyList()
        }
    }

    /**
     * Calculate total energy expenditure for a day
     */
    suspend fun getTotalEnergyExpenditure(startTime: Instant, endTime: Instant): EnergyMetrics {
        val activeCalories = readActiveCalories(startTime, endTime)
        val totalCalories = readTotalCalories(startTime, endTime)

        val totalActive = activeCalories.sumOf { it.energy.inKilocalories }
        val totalAll = totalCalories.sumOf { it.energy.inKilocalories }

        return EnergyMetrics(
            activeCalories = totalActive,
            totalCalories = totalAll,
            basalCalories = totalAll - totalActive
        )
    }

    /**
     * Calculate comprehensive sleep quality score (0-100)
     *
     * Factors:
     * - Duration quality (30 points)
     * - Deep sleep % (25 points)
     * - REM sleep % (20 points)
     * - Sleep efficiency (15 points)
     * - Wake episodes (10 points)
     */
    fun calculateSleepQualityScore(
        sleepSession: SleepSessionRecord,
        sleepStages: List<SleepStage>
    ): SleepQualityMetrics {
        val totalMinutes = java.time.Duration.between(sleepSession.startTime, sleepSession.endTime).toMinutes()

        // Calculate stage percentages
        val deepMinutes = sleepStages.filter { it.stage == 4 }.sumOf { it.durationMinutes }
        val remMinutes = sleepStages.filter { it.stage == 3 }.sumOf { it.durationMinutes }
        val lightMinutes = sleepStages.filter { it.stage == 2 }.sumOf { it.durationMinutes }
        val awakeMinutes = sleepStages.filter { it.stage == 1 }.sumOf { it.durationMinutes }

        val asleepMinutes = totalMinutes - awakeMinutes
        val sleepEfficiency = if (totalMinutes > 0) (asleepMinutes.toFloat() / totalMinutes * 100) else 0f

        val deepPercent = if (totalMinutes > 0) deepMinutes.toFloat() / totalMinutes else 0f
        val remPercent = if (totalMinutes > 0) remMinutes.toFloat() / totalMinutes else 0f

        // Score components
        var score = 0f

        // 1. Duration quality (30 points): 7-9 hours optimal
        val durationHours = totalMinutes / 60f
        score += when {
            durationHours >= 7f && durationHours <= 9f -> 30f
            durationHours >= 6f && durationHours < 7f -> 25f
            durationHours > 9f && durationHours <= 10f -> 25f
            durationHours >= 5f && durationHours < 6f -> 15f
            durationHours > 10f && durationHours <= 11f -> 15f
            else -> 5f
        }

        // 2. Deep sleep % (25 points): 15-20% is ideal
        score += when {
            deepPercent >= 0.15f && deepPercent <= 0.25f -> 25f
            deepPercent >= 0.10f && deepPercent < 0.15f -> 20f
            deepPercent >= 0.08f && deepPercent < 0.10f -> 15f
            deepPercent > 0.25f && deepPercent <= 0.30f -> 20f
            else -> 10f
        }

        // 3. REM sleep % (20 points): 20-25% is ideal
        score += when {
            remPercent >= 0.20f && remPercent <= 0.28f -> 20f
            remPercent >= 0.15f && remPercent < 0.20f -> 15f
            remPercent >= 0.10f && remPercent < 0.15f -> 10f
            remPercent > 0.28f && remPercent <= 0.35f -> 15f
            else -> 5f
        }

        // 4. Sleep efficiency (15 points): >85% is excellent
        score += when {
            sleepEfficiency >= 90f -> 15f
            sleepEfficiency >= 85f -> 12f
            sleepEfficiency >= 80f -> 10f
            sleepEfficiency >= 75f -> 7f
            else -> 3f
        }

        // 5. Wake episodes (10 points): Fewer is better
        val awakeStages = sleepStages.filter { it.stage == 1 }
        score += when {
            awakeStages.size <= 2 -> 10f
            awakeStages.size <= 4 -> 7f
            awakeStages.size <= 6 -> 5f
            else -> 2f
        }

        return SleepQualityMetrics(
            overallScore = score.coerceIn(0f, 100f).toInt(),
            durationMinutes = totalMinutes.toInt(),
            deepSleepPercent = (deepPercent * 100).toInt(),
            remSleepPercent = (remPercent * 100).toInt(),
            sleepEfficiency = sleepEfficiency.toInt(),
            wakeEpisodes = awakeStages.size,
            samsungScore = null  // Will be populated separately
        )
    }

    /**
     * Calculate energy level score (0-100) based on recovery indicators
     *
     * Factors:
     * - Sleep quality (40 points)
     * - HRV recovery (30 points)
     * - Resting heart rate (20 points)
     * - Previous day activity (10 points)
     */
    fun calculateEnergyLevel(
        sleepQuality: Int,
        hrvMean: Float?,
        restingHR: Float?,
        previousDayActiveMinutes: Int = 0
    ): EnergyLevelMetrics {
        var score = 0f

        // 1. Sleep quality component (40 points)
        score += (sleepQuality / 100f) * 40f

        // 2. HRV component (30 points) - Higher HRV = better recovery
        hrvMean?.let { hrv ->
            score += when {
                hrv >= 60f -> 30f
                hrv >= 50f -> 25f
                hrv >= 40f -> 20f
                hrv >= 30f -> 15f
                else -> 10f
            }
        } ?: run {
            // No HRV data, use neutral 15 points
            score += 15f
        }

        // 3. Resting HR component (20 points) - Lower is better
        restingHR?.let { rhr ->
            score += when {
                rhr <= 55f -> 20f
                rhr <= 60f -> 17f
                rhr <= 65f -> 14f
                rhr <= 70f -> 11f
                rhr <= 75f -> 8f
                else -> 5f
            }
        } ?: run {
            // No RHR data, use neutral 10 points
            score += 10f
        }

        // 4. Recovery from previous day activity (10 points)
        // More active yesterday = may need more recovery today
        score += when {
            previousDayActiveMinutes <= 30 -> 10f // Light day, good recovery
            previousDayActiveMinutes <= 60 -> 8f  // Moderate
            previousDayActiveMinutes <= 90 -> 6f  // Heavy
            else -> 4f // Very heavy, may be fatigued
        }

        val finalScore = score.coerceIn(0f, 100f).toInt()

        // Determine energy level category
        val energyLevel = when {
            finalScore >= 80 -> "Excellent"
            finalScore >= 65 -> "Good"
            finalScore >= 50 -> "Fair"
            finalScore >= 35 -> "Low"
            else -> "Very Low"
        }

        return EnergyLevelMetrics(
            score = finalScore,
            level = energyLevel,
            sleepContribution = (sleepQuality / 100f) * 40f,
            hrvContribution = hrvMean?.let { (it / 100f) * 30f } ?: 15f,
            restingHRContribution = restingHR?.let { 20f - (it - 50f) / 3f } ?: 10f
        )
    }
}

/**
 * Sleep stage with duration
 */
data class SleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val stage: Int, // 1=AWAKE, 2=LIGHT, 3=REM, 4=DEEP, 5=UNKNOWN
    val durationMinutes: Int
)

/**
 * Heart rate sample
 */
data class HeartRateSample(
    val time: Instant,
    val beatsPerMinute: Long
)

/**
 * Heart Rate Variability metrics
 */
data class HRVMetrics(
    val rmssd: Float,        // Root Mean Square of Successive Differences (primary HRV metric)
    val meanRR: Float,       // Mean RR interval
    val stdDevRR: Float,     // Standard deviation of RR intervals
    val sampleCount: Int     // Number of samples used
)

/**
 * Energy expenditure metrics
 */
data class EnergyMetrics(
    val activeCalories: Double,  // Calories from physical activity
    val totalCalories: Double,   // Total calories (basal + active)
    val basalCalories: Double    // Basal metabolic rate calories
)

/**
 * Sleep quality assessment metrics
 */
data class SleepQualityMetrics(
    val overallScore: Int,        // 0-100 calculated score
    val durationMinutes: Int,     // Total sleep duration
    val deepSleepPercent: Int,    // % of time in deep sleep
    val remSleepPercent: Int,     // % of time in REM sleep
    val sleepEfficiency: Int,     // % of time actually asleep
    val wakeEpisodes: Int,        // Number of times woken up
    val samsungScore: Int?        // Samsung Health's official score if available
)

/**
 * Energy level assessment metrics
 */
data class EnergyLevelMetrics(
    val score: Int,                 // 0-100 energy level score
    val level: String,              // Excellent, Good, Fair, Low, Very Low
    val sleepContribution: Float,   // Points from sleep quality
    val hrvContribution: Float,     // Points from HRV
    val restingHRContribution: Float // Points from resting HR
)
