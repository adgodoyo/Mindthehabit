package com.example.mindthehabit.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mindthehabit.data.dsp.FeatureExtractor
import com.example.mindthehabit.data.dsp.MissingDataHandler
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.modeling.AdaptiveTimeSeriesEngine
import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Data Processing Worker - Runs daily DSP pipeline
 *
 * Pipeline:
 * 1. Collect raw sensor data from yesterday
 * 2. Extract behavioral features
 * 3. Handle missing data with imputation
 * 4. Store DailySummary in database
 * 5. Trigger time-series modeling (future phase)
 */
@HiltWorker
class DataProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val behaviorDao: BehaviorDao,
    private val featureExtractor: FeatureExtractor,
    private val missingDataHandler: MissingDataHandler,
    private val timeSeriesEngine: AdaptiveTimeSeriesEngine
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DataProcessingWorker"
        const val WORK_NAME = "daily_data_processing"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting daily data processing pipeline...")

            // Process yesterday's data (today might still be in progress)
            val yesterday = LocalDate.now().minusDays(1)

            // Check if we already processed this date
            val existingSummary = behaviorDao.getSummaryForDate(yesterday.toString())
            if (existingSummary != null) {
                Log.d(TAG, "Summary for $yesterday already exists, skipping...")
                return Result.success()
            }

            // STEP 1: Extract features from raw sensor data
            Log.d(TAG, "Extracting behavioral features for $yesterday...")
            val features = featureExtractor.extractFeatures(yesterday)

            // STEP 2: Handle missing data
            Log.d(TAG, "Handling missing data with imputation...")
            val imputedFeatures = missingDataHandler.imputeAllFeatures(features, yesterday)

            // STEP 3: Store in database
            Log.d(TAG, "Storing daily summary...")
            val summary = DailySummaryEntity(
                date = imputedFeatures.features.date,
                sleepScore = imputedFeatures.features.sleepScore,
                morningActivityScore = imputedFeatures.features.morningActivityScore,
                lateNightPhoneIndex = imputedFeatures.features.lateNightPhoneIndex,
                totalSpending = imputedFeatures.features.totalSpending,
                recoveryScore = imputedFeatures.features.recoveryScore,
                behaviorScore = imputedFeatures.features.behaviorScore,
                rawMetricsJson = buildRawMetricsJson(imputedFeatures),
                hrvMean = imputedFeatures.features.hrvMean,
                hrvStdDev = imputedFeatures.features.hrvStdDev,
                restingHR = imputedFeatures.features.restingHR,
                sleepQualityScore = imputedFeatures.features.sleepQualityScore,
                samsungSleepScore = imputedFeatures.features.samsungSleepScore,
                energyLevelScore = imputedFeatures.features.energyLevelScore,
                energyLevel = imputedFeatures.features.energyLevel
            )

            behaviorDao.insertDailySummary(summary)

            Log.d(TAG, "Daily summary for $yesterday stored successfully!")
            Log.d(TAG, "  - Late-Night Index: ${summary.lateNightPhoneIndex}")
            Log.d(TAG, "  - Morning Activity: ${summary.morningActivityScore}")
            Log.d(TAG, "  - Sleep Score: ${summary.sleepScore}")
            Log.d(TAG, "  - Recovery Score: ${summary.recoveryScore}")
            Log.d(TAG, "  - Behavior Score: ${summary.behaviorScore}")
            Log.d(TAG, "  - Data Quality: ${imputedFeatures.dataQuality}")

            // STEP 4: Run time-series modeling for predictions
            try {
                Log.d(TAG, "Running time-series modeling...")
                val predictions = timeSeriesEngine.generatePredictions(LocalDate.now())
                Log.d(TAG, "Predictions generated: ${predictions.modelType}, Confidence: ${predictions.confidence}")
            } catch (e: Exception) {
                Log.w(TAG, "Time-series modeling failed (non-critical)", e)
                // Don't fail the entire job if predictions fail
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing daily data", e)

            // Retry up to 3 times
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Build detailed raw metrics JSON with imputation metadata
     */
    private fun buildRawMetricsJson(imputedFeatures: com.example.mindthehabit.data.dsp.ImputedBehavioralFeatures): String {
        val metrics = mutableMapOf<String, Any>()

        // Add imputation metadata
        metrics["imputations"] = imputedFeatures.imputations.mapValues { it.value.name }
        metrics["dataQuality"] = imputedFeatures.dataQuality.name
        metrics["imputationRate"] = imputedFeatures.imputations.size.toFloat() / 6 // 6 total features

        // Add original raw metrics if available
        try {
            val originalMetrics = com.google.gson.Gson().fromJson(
                imputedFeatures.features.rawMetrics,
                Map::class.java
            )
            if (originalMetrics != null) {
                metrics.putAll(originalMetrics as Map<String, Any>)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse original raw metrics", e)
        }

        return com.google.gson.Gson().toJson(metrics)
    }

    /**
     * Process multiple days at once (for backfilling)
     */
    suspend fun processDateRange(startDate: LocalDate, endDate: LocalDate): Int {
        var processedCount = 0
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            try {
                val existingSummary = behaviorDao.getSummaryForDate(currentDate.toString())
                if (existingSummary == null) {
                    val features = featureExtractor.extractFeatures(currentDate)
                    val imputedFeatures = missingDataHandler.imputeAllFeatures(features, currentDate)

                    val summary = DailySummaryEntity(
                        date = imputedFeatures.features.date,
                        sleepScore = imputedFeatures.features.sleepScore,
                        morningActivityScore = imputedFeatures.features.morningActivityScore,
                        lateNightPhoneIndex = imputedFeatures.features.lateNightPhoneIndex,
                        totalSpending = imputedFeatures.features.totalSpending,
                        recoveryScore = imputedFeatures.features.recoveryScore,
                        behaviorScore = imputedFeatures.features.behaviorScore,
                        rawMetricsJson = buildRawMetricsJson(imputedFeatures),
                        hrvMean = imputedFeatures.features.hrvMean,
                        hrvStdDev = imputedFeatures.features.hrvStdDev,
                        restingHR = imputedFeatures.features.restingHR,
                        sleepQualityScore = imputedFeatures.features.sleepQualityScore,
                        samsungSleepScore = imputedFeatures.features.samsungSleepScore,
                        energyLevelScore = imputedFeatures.features.energyLevelScore,
                        energyLevel = imputedFeatures.features.energyLevel
                    )

                    behaviorDao.insertDailySummary(summary)
                    processedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $currentDate", e)
            }

            currentDate = currentDate.plusDays(1)
        }

        return processedCount
    }
}
