package com.example.mindthehabit.data.backfill

import android.util.Log
import com.example.mindthehabit.data.dsp.FeatureExtractor
import com.example.mindthehabit.data.dsp.MissingDataHandler
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historical Data Backfill Service
 *
 * Retrieves and processes historical data from:
 * - Health Connect (sleep, exercise, heart rate, steps)
 * - Usage Stats (app usage, screen time)
 *
 * Note: Sensor data (light, WiFi, GPS) is only available from when the app started running.
 */
@Singleton
class HistoricalDataBackfillService @Inject constructor(
    private val behaviorDao: BehaviorDao,
    private val featureExtractor: FeatureExtractor,
    private val missingDataHandler: MissingDataHandler
) {

    companion object {
        private const val TAG = "HistoricalBackfill"
        const val MAX_BACKFILL_DAYS = 90 // Limit to prevent excessive processing
    }

    private val _backfillProgress = MutableStateFlow<BackfillProgress>(BackfillProgress.Idle)
    val backfillProgress: StateFlow<BackfillProgress> = _backfillProgress.asStateFlow()

    /**
     * Backfill historical data for a date range
     *
     * @param startDate Earliest date to backfill (inclusive)
     * @param endDate Latest date to backfill (inclusive, typically yesterday)
     * @return Number of days successfully processed
     */
    suspend fun backfillDateRange(startDate: LocalDate, endDate: LocalDate): BackfillResult {
        Log.d(TAG, "Starting backfill from $startDate to $endDate")

        // Validate date range
        val today = LocalDate.now()
        if (endDate.isAfter(today.minusDays(1))) {
            Log.w(TAG, "End date cannot be today or future, adjusting to yesterday")
            return BackfillResult(
                success = false,
                daysProcessed = 0,
                daysSkipped = 0,
                daysFailed = 0,
                errorMessage = "Cannot backfill today's data (still in progress). Use yesterday or earlier."
            )
        }

        if (startDate.isAfter(endDate)) {
            return BackfillResult(
                success = false,
                daysProcessed = 0,
                daysSkipped = 0,
                daysFailed = 0,
                errorMessage = "Start date must be before end date"
            )
        }

        // Calculate total days
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

        if (totalDays > MAX_BACKFILL_DAYS) {
            return BackfillResult(
                success = false,
                daysProcessed = 0,
                daysSkipped = 0,
                daysFailed = 0,
                errorMessage = "Cannot backfill more than $MAX_BACKFILL_DAYS days at once. Break into smaller ranges."
            )
        }

        _backfillProgress.value = BackfillProgress.InProgress(0, totalDays)

        var daysProcessed = 0
        var daysSkipped = 0
        var daysFailed = 0
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            try {
                // Check if we already have a summary for this date
                val existingSummary = behaviorDao.getSummaryForDate(currentDate.toString())

                if (existingSummary != null) {
                    Log.d(TAG, "Skipping $currentDate - already processed")
                    daysSkipped++
                } else {
                    // Extract features from historical data
                    Log.d(TAG, "Processing $currentDate...")
                    val features = featureExtractor.extractFeatures(currentDate)

                    // Handle missing data
                    val imputedFeatures = missingDataHandler.imputeAllFeatures(features, currentDate)

                    // Store daily summary
                    val summary = DailySummaryEntity(
                        date = imputedFeatures.features.date,
                        sleepScore = imputedFeatures.features.sleepScore,
                        morningActivityScore = imputedFeatures.features.morningActivityScore,
                        lateNightPhoneIndex = imputedFeatures.features.lateNightPhoneIndex,
                        totalSpending = imputedFeatures.features.totalSpending,
                        recoveryScore = imputedFeatures.features.recoveryScore,
                        behaviorScore = imputedFeatures.features.behaviorScore,
                        rawMetricsJson = imputedFeatures.features.rawMetrics,
                        hrvMean = imputedFeatures.features.hrvMean,
                        hrvStdDev = imputedFeatures.features.hrvStdDev,
                        restingHR = imputedFeatures.features.restingHR,
                        sleepQualityScore = imputedFeatures.features.sleepQualityScore,
                        samsungSleepScore = imputedFeatures.features.samsungSleepScore,
                        energyLevelScore = imputedFeatures.features.energyLevelScore,
                        energyLevel = imputedFeatures.features.energyLevel
                    )

                    behaviorDao.insertDailySummary(summary)
                    daysProcessed++

                    Log.d(TAG, "✓ $currentDate processed: Behavior Score = ${summary.behaviorScore.toInt()}/100, Quality = ${imputedFeatures.dataQuality}")
                }

                // Update progress
                val completedDays = daysProcessed + daysSkipped + daysFailed
                _backfillProgress.value = BackfillProgress.InProgress(completedDays, totalDays)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process $currentDate", e)
                daysFailed++
            }

            currentDate = currentDate.plusDays(1)
        }

        val result = BackfillResult(
            success = daysFailed == 0,
            daysProcessed = daysProcessed,
            daysSkipped = daysSkipped,
            daysFailed = daysFailed,
            errorMessage = if (daysFailed > 0) "$daysFailed days failed to process" else null
        )

        _backfillProgress.value = BackfillProgress.Completed(result)

        Log.d(TAG, "Backfill complete: $daysProcessed processed, $daysSkipped skipped, $daysFailed failed")

        return result
    }

    /**
     * Backfill last N days of historical data
     */
    suspend fun backfillLastNDays(days: Int): BackfillResult {
        val endDate = LocalDate.now().minusDays(1) // Yesterday
        val startDate = endDate.minusDays(days.toLong() - 1)
        return backfillDateRange(startDate, endDate)
    }

    /**
     * Reset backfill progress state
     */
    fun resetProgress() {
        _backfillProgress.value = BackfillProgress.Idle
    }
}

/**
 * Backfill progress states
 */
sealed class BackfillProgress {
    object Idle : BackfillProgress()
    data class InProgress(val completed: Int, val total: Int) : BackfillProgress() {
        val percentage: Int get() = if (total > 0) (completed * 100 / total) else 0
    }
    data class Completed(val result: BackfillResult) : BackfillProgress()
}

/**
 * Backfill result summary
 */
data class BackfillResult(
    val success: Boolean,
    val daysProcessed: Int,
    val daysSkipped: Int,
    val daysFailed: Int,
    val errorMessage: String? = null
) {
    val totalDays: Int get() = daysProcessed + daysSkipped + daysFailed

    fun getSummary(): String {
        return buildString {
            append("Backfill complete: ")
            append("$daysProcessed new")
            if (daysSkipped > 0) append(", $daysSkipped skipped")
            if (daysFailed > 0) append(", $daysFailed failed")
        }
    }
}
