package com.example.mindthehabit.data.modeling

import android.util.Log
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.google.gson.Gson
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptive Time-Series Engine - Intelligently selects the best prediction model
 *
 * Model Selection Strategy:
 * 1. Assess data quality (completeness, days available, imputation rate)
 * 2. Select appropriate model:
 *    - Lag Correlation: <14 days OR poor quality
 *    - Rolling Regression: 14-20 days with good quality
 *    - Change-Point Detection: 21+ days with excellent quality
 * 3. Generate predictions and store results
 *
 * Gracefully degrades to simpler models when data is sparse
 */
@Singleton
class AdaptiveTimeSeriesEngine @Inject constructor(
    private val dao: BehaviorDao,
    private val lagCorrelationModel: LagCorrelationModel,
    private val rollingRegressionModel: RollingRegressionModel,
    private val changePointModel: ChangePointDetectionModel,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "AdaptiveTimeSeriesEngine"
    }

    /**
     * Generate predictions for the target date
     * Automatically selects the best model based on data quality
     */
    suspend fun generatePredictions(targetDate: LocalDate = LocalDate.now()): ModelPredictions {
        Log.d(TAG, "Generating predictions for $targetDate...")

        // Get historical data (last 30 days)
        val startDate = targetDate.minusDays(30)
        val historicalData = dao.getDailySummariesBetween(startDate.toString(), targetDate.minusDays(1).toString())

        if (historicalData.isEmpty()) {
            Log.w(TAG, "No historical data available")
            return ModelPredictions.empty(targetDate)
        }

        // Assess data quality
        val quality = assessDataQuality(historicalData)
        Log.d(TAG, "Data quality: ${quality.overallQuality()}")
        Log.d(TAG, "  - Days available: ${quality.daysAvailable}")
        Log.d(TAG, "  - Completeness: ${(quality.completeness * 100).toInt()}%")
        Log.d(TAG, "  - Avg imputation rate: ${(quality.avgImputationRate * 100).toInt()}%")

        // Select appropriate model
        val model = selectModel(quality)
        Log.d(TAG, "Selected model: ${model::class.simpleName}")

        // Generate predictions
        val predictions = model.predict(historicalData, targetDate)

        Log.d(TAG, "Predictions generated:")
        Log.d(TAG, "  - Next-day spending: $${predictions.nextDaySpending}")
        Log.d(TAG, "  - Next-day sleep: ${predictions.nextDaySleepScore}")
        Log.d(TAG, "  - Confidence: ${predictions.confidence}")

        return predictions
    }

    /**
     * Assess data quality to inform model selection
     */
    private fun assessDataQuality(historicalData: List<com.example.mindthehabit.data.local.entity.DailySummaryEntity>): DataQualityMetrics {
        val daysAvailable = historicalData.size
        val totalExpected = 30
        val completeness = daysAvailable.toFloat() / totalExpected

        // Calculate average imputation rate from rawMetricsJson
        val avgImputationRate = historicalData.mapNotNull { summary ->
            try {
                val metrics = gson.fromJson(summary.rawMetricsJson, Map::class.java)
                (metrics["imputationRate"] as? Number)?.toFloat()
            } catch (e: Exception) {
                null
            }
        }.average().toFloat().takeIf { !it.isNaN() } ?: 0f

        // Check if we have recent data (last 2 days)
        val hasRecentData = historicalData.isNotEmpty() &&
                LocalDate.parse(historicalData.last().date).isAfter(LocalDate.now().minusDays(3))

        return DataQualityMetrics(
            daysAvailable = daysAvailable,
            completeness = completeness,
            avgImputationRate = avgImputationRate,
            hasRecentData = hasRecentData
        )
    }

    /**
     * Select the most appropriate model based on data quality
     */
    private fun selectModel(quality: DataQualityMetrics): TimeSeriesModel {
        return when (quality.overallQuality()) {
            DataQualityLevel.EXCELLENT -> {
                // 21+ days, high quality -> Change-Point Detection
                changePointModel
            }

            DataQualityLevel.GOOD -> {
                // 14-20 days, good quality -> Rolling Regression
                if (quality.daysAvailable >= 14) {
                    rollingRegressionModel
                } else {
                    lagCorrelationModel
                }
            }

            DataQualityLevel.FAIR, DataQualityLevel.POOR, DataQualityLevel.INSUFFICIENT -> {
                // <14 days or poor quality -> Lag Correlation
                lagCorrelationModel
            }
        }
    }

    /**
     * Get predictions for multiple future days
     */
    suspend fun generateForecast(startDate: LocalDate, days: Int): List<ModelPredictions> {
        val forecasts = mutableListOf<ModelPredictions>()

        repeat(days) { i ->
            val targetDate = startDate.plusDays(i.toLong())
            val prediction = generatePredictions(targetDate)
            forecasts.add(prediction)
        }

        return forecasts
    }

    /**
     * Calculate cumulative impact over time
     * Used for the Prediction screen "what if" scenarios
     */
    suspend fun calculateCumulativeImpact(
        latePhoneActive: Boolean,
        morningGymActive: Boolean,
        days: Int
    ): CumulativeImpact {
        // Get recent data to estimate coefficients
        val historicalData = dao.getDailySummariesBetween(
            LocalDate.now().minusDays(30).toString(),
            LocalDate.now().toString()
        )

        if (historicalData.isEmpty()) {
            return CumulativeImpact(0.0, 0.0, 0.0, 0.0)
        }

        // Use rolling regression to estimate coefficients
        val model = if (historicalData.size >= 14) {
            rollingRegressionModel
        } else {
            lagCorrelationModel
        }

        val predictions = model.predict(historicalData, LocalDate.now())

        // Extract coefficients (or use defaults if not available)
        val lateNightSpendingCoef = predictions.coefficients["spending_lateNight"] ?: 12.0
        val morningActivitySpendingCoef = predictions.coefficients["spending_morningActivity"] ?: -8.0

        val lateNightSleepCoef = predictions.coefficients["sleep_lateNight"] ?: -15.0
        val morningActivitySleepCoef = predictions.coefficients["sleep_morningActivity"] ?: 8.0

        // Calculate cumulative impacts
        val spendingFromLatePhone = if (latePhoneActive) days * lateNightSpendingCoef * 0.7 else 0.0
        val spendingFromMorningGym = if (morningGymActive) days * morningActivitySpendingCoef * 0.5 else 0.0

        val totalSpendingImpact = spendingFromLatePhone + spendingFromMorningGym

        val sleepFromLatePhone = if (latePhoneActive) days * lateNightSleepCoef * 0.7 else 0.0
        val sleepFromMorningGym = if (morningGymActive) days * morningActivitySleepCoef * 0.5 else 0.0

        val totalSleepImpact = sleepFromLatePhone + sleepFromMorningGym

        return CumulativeImpact(
            totalSpendingImpact = totalSpendingImpact,
            spendingFromLatePhone = spendingFromLatePhone,
            spendingFromMorningGym = spendingFromMorningGym,
            totalSleepImpact = totalSleepImpact
        )
    }
}

/**
 * Cumulative impact over time
 */
data class CumulativeImpact(
    val totalSpendingImpact: Double,
    val spendingFromLatePhone: Double,
    val spendingFromMorningGym: Double,
    val totalSleepImpact: Double
)
