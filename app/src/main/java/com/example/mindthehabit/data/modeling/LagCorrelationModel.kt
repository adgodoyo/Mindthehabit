package com.example.mindthehabit.data.modeling

import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lag Correlation Model - For sparse data (< 14 days)
 *
 * Simple approach: Find correlations between yesterday's behavior and today's outcomes
 * Uses Pearson correlation with lag-1 to predict next-day effects
 *
 * Best for: Early stage data collection, minimal complexity
 */
@Singleton
class LagCorrelationModel @Inject constructor() : TimeSeriesModel {

    override suspend fun predict(
        historicalData: List<DailySummaryEntity>,
        targetDate: LocalDate
    ): ModelPredictions {
        if (historicalData.size < 3) {
            return ModelPredictions.empty(targetDate)
        }

        // Calculate lag-1 correlations
        val correlations = calculateLagCorrelations(historicalData)

        // Get yesterday's features (most recent data)
        val yesterday = historicalData.lastOrNull() ?: return ModelPredictions.empty(targetDate)

        // Predict spending based on correlation
        val baselineSpending = historicalData.map { it.totalSpending }.average()
        val predictedSpending = baselineSpending +
                correlations.lateNightToSpending * yesterday.lateNightPhoneIndex * 15.0 -
                correlations.morningActivityToSpending * yesterday.morningActivityScore * 0.1

        // Predict sleep score
        val baselineSleep = historicalData.map { it.sleepScore.toDouble() }.average()
        val predictedSleepScore = baselineSleep -
                correlations.lateNightToSleep * yesterday.lateNightPhoneIndex * 20.0

        // Recovery typically follows sleep - use baseline with proportional adjustment
        val baselineRecovery = historicalData.map { it.recoveryScore.toDouble() }.average()
        val sleepChange = predictedSleepScore - yesterday.sleepScore
        // Cap sleep change impact to ±30 points to prevent extreme swings
        val recoveryAdjustment = (sleepChange * 0.4).coerceIn(-30.0, 30.0)
        val predictedRecovery = (baselineRecovery + recoveryAdjustment).toFloat()

        // Generate insights
        val insights = buildInsights(correlations, yesterday, historicalData.size)

        return ModelPredictions(
            targetDate = targetDate.toString(),
            nextDaySpending = predictedSpending.toFloat().coerceAtLeast(0f),
            nextDaySleepScore = predictedSleepScore.toFloat().coerceIn(0f, 100f),
            nextDayRecovery = predictedRecovery.coerceIn(0f, 100f),
            confidence = ConfidenceLevel.LOW,
            modelType = "Lag Correlation",
            correlations = mapOf(
                "lateNightToSpending" to correlations.lateNightToSpending,
                "morningActivityToSpending" to correlations.morningActivityToSpending,
                "lateNightToSleep" to correlations.lateNightToSleep
            ),
            insights = insights,
            dataQualityUsed = "SPARSE"
        )
    }

    /**
     * Calculate lag-1 cross-correlations
     * Correlates yesterday's X with today's Y
     */
    private fun calculateLagCorrelations(data: List<DailySummaryEntity>): CorrelationMatrix {
        if (data.size < 2) {
            return CorrelationMatrix(0f, 0f, 0f)
        }

        // Create lag pairs: [day_i, day_i+1]
        val pairs = data.zipWithNext()

        // Late-night phone → next-day spending
        val lateNightToSpending = Statistics.pearsonCorrelation(
            pairs.map { it.first.lateNightPhoneIndex.toDouble() },
            pairs.map { it.second.totalSpending }
        ).toFloat()

        // Morning activity → next-day spending (inverse relationship expected)
        val morningActivityToSpending = Statistics.pearsonCorrelation(
            pairs.map { it.first.morningActivityScore.toDouble() },
            pairs.map { it.second.totalSpending }
        ).toFloat()

        // Late-night phone → next-day sleep quality (inverse relationship)
        val lateNightToSleep = Statistics.pearsonCorrelation(
            pairs.map { it.first.lateNightPhoneIndex.toDouble() },
            pairs.map { it.second.sleepScore.toDouble() }
        ).toFloat()

        // Morning activity → next-day sleep
        val morningActivityToSleep = Statistics.pearsonCorrelation(
            pairs.map { it.first.morningActivityScore.toDouble() },
            pairs.map { it.second.sleepScore.toDouble() }
        ).toFloat()

        return CorrelationMatrix(
            lateNightToSpending = lateNightToSpending,
            morningActivityToSpending = morningActivityToSpending,
            lateNightToSleep = lateNightToSleep,
            morningActivityToSleep = morningActivityToSleep
        )
    }

    /**
     * Build human-readable insights from correlations
     */
    private fun buildInsights(
        correlations: CorrelationMatrix,
        yesterday: DailySummaryEntity,
        dataPoints: Int
    ): List<String> {
        val insights = mutableListOf<String>()

        insights.add("Based on $dataPoints days of data")

        // Late-night phone insights
        if (kotlin.math.abs(correlations.lateNightToSpending) > 0.3f) {
            val direction = if (correlations.lateNightToSpending > 0) "increases" else "decreases"
            insights.add("Late-night phone use $direction next-day spending (r=${String.format("%.2f", correlations.lateNightToSpending)})")
        }

        if (kotlin.math.abs(correlations.lateNightToSleep) > 0.3f) {
            val direction = if (correlations.lateNightToSleep < 0) "reduces" else "improves"
            insights.add("Late-night phone use $direction next-day sleep quality (r=${String.format("%.2f", correlations.lateNightToSleep)})")
        }

        // Morning activity insights
        if (kotlin.math.abs(correlations.morningActivityToSpending) > 0.3f) {
            val direction = if (correlations.morningActivityToSpending < 0) "reduces" else "increases"
            insights.add("Morning exercise $direction next-day spending (r=${String.format("%.2f", correlations.morningActivityToSpending)})")
        }

        // Yesterday's behavior warning
        if (yesterday.lateNightPhoneIndex > 0.6f) {
            insights.add("⚠️ High late-night phone use yesterday (${(yesterday.lateNightPhoneIndex * 100).toInt()}%)")
        }

        if (yesterday.morningActivityScore < 30f) {
            insights.add("⚠️ Low morning activity yesterday (${yesterday.morningActivityScore.toInt()}/100)")
        }

        if (insights.size == 1) {
            insights.add("Collect more data to identify patterns")
        }

        return insights
    }
}
