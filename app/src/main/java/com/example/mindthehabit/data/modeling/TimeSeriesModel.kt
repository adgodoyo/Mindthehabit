package com.example.mindthehabit.data.modeling

import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import java.time.LocalDate

/**
 * Base interface for time-series prediction models
 */
interface TimeSeriesModel {
    /**
     * Generate predictions for the target date based on historical data
     */
    suspend fun predict(
        historicalData: List<DailySummaryEntity>,
        targetDate: LocalDate
    ): ModelPredictions
}

/**
 * Model predictions with metadata
 */
data class ModelPredictions(
    val targetDate: String,
    val nextDaySpending: Float,
    val nextDaySleepScore: Float,
    val nextDayRecovery: Float,
    val confidence: ConfidenceLevel,
    val modelType: String,
    val coefficients: Map<String, Double> = emptyMap(),
    val correlations: Map<String, Float> = emptyMap(),
    val insights: List<String> = emptyList(),
    val dataQualityUsed: String = "UNKNOWN"
) {
    companion object {
        fun empty(date: LocalDate = LocalDate.now()): ModelPredictions {
            return ModelPredictions(
                targetDate = date.toString(),
                nextDaySpending = 0f,
                nextDaySleepScore = 70f,
                nextDayRecovery = 70f,
                confidence = ConfidenceLevel.NONE,
                modelType = "None",
                insights = listOf("Insufficient data for predictions")
            )
        }
    }
}

/**
 * Confidence levels for predictions
 */
enum class ConfidenceLevel {
    NONE,       // No data available
    LOW,        // <7 days of data, using simple correlation
    MEDIUM,     // 7-20 days, using rolling regression
    HIGH        // 21+ days with good quality, using change-point detection
}

/**
 * Data quality assessment for modeling
 */
data class DataQualityMetrics(
    val daysAvailable: Int,
    val completeness: Float, // 0-1 scale
    val avgImputationRate: Float, // 0-1 scale (lower is better)
    val hasRecentData: Boolean = true
) {
    fun overallQuality(): DataQualityLevel {
        val qualityScore = (completeness * 0.6f + (1 - avgImputationRate) * 0.4f)

        return when {
            daysAvailable < 7 -> DataQualityLevel.INSUFFICIENT
            qualityScore >= 0.8f && daysAvailable >= 21 -> DataQualityLevel.EXCELLENT
            qualityScore >= 0.6f && daysAvailable >= 14 -> DataQualityLevel.GOOD
            qualityScore >= 0.4f -> DataQualityLevel.FAIR
            else -> DataQualityLevel.POOR
        }
    }
}

enum class DataQualityLevel {
    INSUFFICIENT,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}

/**
 * Correlation matrix between behaviors and outcomes
 */
data class CorrelationMatrix(
    val lateNightToSpending: Float,
    val morningActivityToSpending: Float,
    val lateNightToSleep: Float,
    val morningActivityToSleep: Float = 0f,
    val sleepToRecovery: Float = 0f
)

/**
 * Change point in time series
 */
data class ChangePoint(
    val index: Int,
    val date: String,
    val feature: String,
    val beforeMean: Float,
    val afterMean: Float,
    val percentChange: Float
)
