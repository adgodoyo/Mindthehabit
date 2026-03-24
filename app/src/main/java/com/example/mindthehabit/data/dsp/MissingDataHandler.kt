package com.example.mindthehabit.data.dsp

import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.models.BehavioralFeatures
import com.example.mindthehabit.data.models.DataQuality
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Missing Data Handler - Imputes missing behavioral features
 *
 * Strategies:
 * 1. Rolling mean (7-day window)
 * 2. Forward fill (last known value)
 * 3. Default neutral values
 * 4. Linear interpolation
 *
 * Tracks imputation quality for model confidence adjustment
 */
@Singleton
class MissingDataHandler @Inject constructor(
    private val dao: BehaviorDao
) {

    /**
     * Impute a single missing feature value
     *
     * Priority:
     * 1. If value exists -> return as-is
     * 2. If 7+ days of history -> rolling mean
     * 3. If some history -> forward fill
     * 4. Otherwise -> default value
     */
    suspend fun imputeFeature(
        featureName: String,
        value: Float?,
        date: LocalDate
    ): ImputedValue {
        // If value exists, no imputation needed
        // Note: 0 is a valid value (e.g., behavior score of 0 = worst possible)
        if (value != null) {
            return ImputedValue(value, ImputationMethod.OBSERVED)
        }

        // Get historical values (last 30 days)
        val historicalValues = getHistoricalValues(featureName, date, days = 30)

        return when {
            // Strategy 1: Rolling mean (requires 7+ data points)
            historicalValues.size >= 7 -> {
                val meanValue = historicalValues.takeLast(7).average().toFloat()
                ImputedValue(meanValue, ImputationMethod.ROLLING_MEAN)
            }

            // Strategy 2: Forward fill (last known value)
            historicalValues.isNotEmpty() -> {
                ImputedValue(historicalValues.last(), ImputationMethod.FORWARD_FILL)
            }

            // Strategy 3: Default neutral value
            else -> {
                val defaultValue = getDefaultValue(featureName)
                ImputedValue(defaultValue, ImputationMethod.DEFAULT)
            }
        }
    }

    /**
     * Impute all features in a BehavioralFeatures object
     */
    suspend fun imputeAllFeatures(
        features: BehavioralFeatures,
        date: LocalDate
    ): ImputedBehavioralFeatures {
        val imputations = mutableMapOf<String, ImputationMethod>()

        // Impute each feature
        val lateNightImputed = imputeFeature("lateNightPhoneIndex", features.lateNightPhoneIndex, date)
        if (lateNightImputed.method != ImputationMethod.OBSERVED) {
            imputations["lateNightPhoneIndex"] = lateNightImputed.method
        }

        val morningActivityImputed = imputeFeature("morningActivityScore", features.morningActivityScore, date)
        if (morningActivityImputed.method != ImputationMethod.OBSERVED) {
            imputations["morningActivityScore"] = morningActivityImputed.method
        }

        val sleepScoreImputed = imputeFeature("sleepScore", features.sleepScore, date)
        if (sleepScoreImputed.method != ImputationMethod.OBSERVED) {
            imputations["sleepScore"] = sleepScoreImputed.method
        }

        val recoveryScoreImputed = imputeFeature("recoveryScore", features.recoveryScore, date)
        if (recoveryScoreImputed.method != ImputationMethod.OBSERVED) {
            imputations["recoveryScore"] = recoveryScoreImputed.method
        }

        val hrvMeanImputed = features.hrvMean?.let {
            imputeFeature("hrvMean", it, date)
        } ?: ImputedValue(null, ImputationMethod.DEFAULT)
        if (hrvMeanImputed.method != ImputationMethod.OBSERVED) {
            imputations["hrvMean"] = hrvMeanImputed.method
        }

        val restingHRImputed = features.restingHR?.let {
            imputeFeature("restingHR", it, date)
        } ?: ImputedValue(null, ImputationMethod.DEFAULT)
        if (restingHRImputed.method != ImputationMethod.OBSERVED) {
            imputations["restingHR"] = restingHRImputed.method
        }

        // Calculate data quality
        val quality = calculateDataQuality(imputations, totalFeatures = 6)

        return ImputedBehavioralFeatures(
            features = features.copy(
                lateNightPhoneIndex = lateNightImputed.value ?: features.lateNightPhoneIndex,
                morningActivityScore = morningActivityImputed.value ?: features.morningActivityScore,
                sleepScore = sleepScoreImputed.value ?: features.sleepScore,
                recoveryScore = recoveryScoreImputed.value ?: features.recoveryScore,
                hrvMean = hrvMeanImputed.value,
                restingHR = restingHRImputed.value
            ),
            imputations = imputations,
            dataQuality = quality
        )
    }

    /**
     * Get historical values for a specific feature
     */
    private suspend fun getHistoricalValues(
        featureName: String,
        currentDate: LocalDate,
        days: Int
    ): List<Float> {
        val startDate = currentDate.minusDays(days.toLong())
        val summaries = dao.getDailySummariesBetween(startDate.toString(), currentDate.minusDays(1).toString())

        return summaries.mapNotNull { summary ->
            when (featureName) {
                "lateNightPhoneIndex" -> summary.lateNightPhoneIndex
                "morningActivityScore" -> summary.morningActivityScore
                "sleepScore" -> summary.sleepScore
                "recoveryScore" -> summary.recoveryScore
                "hrvMean" -> summary.hrvMean
                "restingHR" -> summary.restingHR
                else -> null
            }
        }
    }

    /**
     * Get default neutral value for a feature
     */
    private fun getDefaultValue(featureName: String): Float {
        return when (featureName) {
            "lateNightPhoneIndex" -> 0.3f  // Assume moderate late-night usage
            "morningActivityScore" -> 30f  // Assume low morning activity
            "sleepScore" -> 60f            // Assume moderate sleep quality
            "recoveryScore" -> 50f         // Neutral recovery
            "behaviorScore" -> 60f         // Neutral behavior
            "hrvMean" -> 50f               // Middle HRV range
            "restingHR" -> 65f             // Average resting HR
            else -> 0f
        }
    }

    /**
     * Calculate overall data quality based on imputation rate
     */
    private fun calculateDataQuality(
        imputations: Map<String, ImputationMethod>,
        totalFeatures: Int
    ): DataQuality {
        val imputedCount = imputations.size
        val imputationRate = imputedCount.toFloat() / totalFeatures

        // Weight by imputation method quality
        val qualityWeightedScore = imputations.values.sumOf { method ->
            when (method) {
                ImputationMethod.OBSERVED -> 1.0
                ImputationMethod.ROLLING_MEAN -> 0.8
                ImputationMethod.LINEAR_INTERPOLATION -> 0.7
                ImputationMethod.FORWARD_FILL -> 0.5
                ImputationMethod.DEFAULT -> 0.3
            }
        } / totalFeatures

        return when {
            qualityWeightedScore >= 0.8 -> DataQuality.HIGH
            qualityWeightedScore >= 0.5 -> DataQuality.MEDIUM
            else -> DataQuality.LOW
        }
    }

    /**
     * Linear interpolation for gaps in time series
     */
    suspend fun linearInterpolate(
        featureName: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, Float> {
        val allDates = mutableListOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            allDates.add(current)
            current = current.plusDays(1)
        }

        val summaries = dao.getDailySummariesBetween(startDate.toString(), endDate.toString())
        val existingValues = summaries.associate { summary ->
            LocalDate.parse(summary.date) to when (featureName) {
                "lateNightPhoneIndex" -> summary.lateNightPhoneIndex
                "morningActivityScore" -> summary.morningActivityScore
                "sleepScore" -> summary.sleepScore
                "recoveryScore" -> summary.recoveryScore
                else -> 0f
            }
        }

        val interpolated = mutableMapOf<LocalDate, Float>()

        var lastKnownDate: LocalDate? = null
        var lastKnownValue: Float? = null

        allDates.forEach { date ->
            when {
                existingValues.containsKey(date) -> {
                    // Value exists
                    val value = existingValues[date] ?: return@forEach
                    interpolated[date] = value
                    lastKnownDate = date
                    lastKnownValue = value
                }

                lastKnownDate != null && lastKnownValue != null -> {
                    // Find next known value
                    val nextKnownEntry = existingValues.entries
                        .filter { it.key.isAfter(date) }
                        .minByOrNull { it.key }

                    val knownValue = lastKnownValue ?: return@forEach

                    if (nextKnownEntry != null) {
                        // Interpolate
                        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lastKnownDate, nextKnownEntry.key).toFloat()
                        val currentDayOffset = java.time.temporal.ChronoUnit.DAYS.between(lastKnownDate, date).toFloat()
                        val ratio = currentDayOffset / daysBetween

                        val interpolatedValue = knownValue + (nextKnownEntry.value - knownValue) * ratio
                        interpolated[date] = interpolatedValue
                    } else {
                        // Forward fill
                        interpolated[date] = knownValue
                    }
                }

                else -> {
                    // No known values yet, use default
                    interpolated[date] = getDefaultValue(featureName)
                }
            }
        }

        return interpolated
    }

    /**
     * Detect and flag anomalous values
     */
    suspend fun detectAnomalies(
        featureName: String,
        date: LocalDate,
        value: Float
    ): Boolean {
        val historicalValues = getHistoricalValues(featureName, date, days = 14)

        if (historicalValues.size < 7) return false // Not enough data

        val mean = historicalValues.average().toFloat()
        val stdDev = SignalProcessing.standardDeviation(historicalValues)

        // Flag if value is > 3 standard deviations from mean
        val zScore = kotlin.math.abs((value - mean) / stdDev)
        return zScore > 3.0f
    }
}

/**
 * Imputed value with method tracking
 */
data class ImputedValue(
    val value: Float?,
    val method: ImputationMethod
)

/**
 * Behavioral features with imputation metadata
 */
data class ImputedBehavioralFeatures(
    val features: BehavioralFeatures,
    val imputations: Map<String, ImputationMethod>,
    val dataQuality: DataQuality
)

/**
 * Imputation methods in order of quality
 */
enum class ImputationMethod {
    OBSERVED,               // Real data (no imputation)
    ROLLING_MEAN,          // 7-day rolling average
    LINEAR_INTERPOLATION,  // Interpolated between known values
    FORWARD_FILL,          // Last known value
    DEFAULT                // Default neutral value
}
