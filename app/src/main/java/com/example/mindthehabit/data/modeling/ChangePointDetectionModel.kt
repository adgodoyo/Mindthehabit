package com.example.mindthehabit.data.modeling

import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Change-Point Detection Model - For rich data (21+ days, high quality)
 *
 * Detects regime changes in behavior patterns (e.g., "started morning workouts on day 15")
 * Fits separate regression models for each behavioral regime
 * Provides deepest insights into behavior-outcome relationships
 *
 * Best for: Long-term tracking, understanding behavior shifts
 */
@Singleton
class ChangePointDetectionModel @Inject constructor() : TimeSeriesModel {

    companion object {
        private const val MIN_REGIME_LENGTH = 7
        private const val CHANGE_THRESHOLD = 0.25f // 25% change to be considered significant
    }

    override suspend fun predict(
        historicalData: List<DailySummaryEntity>,
        targetDate: LocalDate
    ): ModelPredictions {
        if (historicalData.size < 21) {
            return ModelPredictions.empty(targetDate)
        }

        // Detect change points in key behaviors
        val lateNightChangePoints = detectChangePoints(
            historicalData.map { it.lateNightPhoneIndex },
            historicalData.map { it.date },
            "Late-Night Phone"
        )

        val morningActivityChangePoints = detectChangePoints(
            historicalData.map { it.morningActivityScore },
            historicalData.map { it.date },
            "Morning Activity"
        )

        // Identify current behavioral regime
        val lastLateNightChange = lateNightChangePoints.lastOrNull()?.index ?: 0
        val lastMorningChange = morningActivityChangePoints.lastOrNull()?.index ?: 0
        val regimeStart = maxOf(lastLateNightChange, lastMorningChange)

        val currentRegime = if (regimeStart > 0) {
            historicalData.drop(regimeStart)
        } else {
            historicalData
        }

        // Fit regression on current regime only
        val spendingModel = fitRegressionOnRegime(currentRegime, OutcomeType.SPENDING)
        val sleepModel = fitRegressionOnRegime(currentRegime, OutcomeType.SLEEP)

        // Predict
        val yesterday = historicalData.last()

        val predictedSpending = spendingModel.predict(listOf(
            yesterday.lateNightPhoneIndex.toDouble(),
            yesterday.morningActivityScore.toDouble() / 100
        ))

        val predictedSleep = sleepModel.predict(listOf(
            yesterday.lateNightPhoneIndex.toDouble(),
            yesterday.morningActivityScore.toDouble() / 100
        ))

        // Recovery prediction using baseline + proportional adjustment (same as LagCorrelationModel)
        val baselineRecovery = currentRegime.map { it.recoveryScore.toDouble() }.average()
        val sleepChange = predictedSleep - yesterday.sleepScore
        val recoveryAdjustment = (sleepChange * 0.4).coerceIn(-30.0, 30.0)
        val predictedRecovery = (baselineRecovery + recoveryAdjustment).toFloat()

        // Build insights from change points
        val insights = buildChangePointInsights(
            lateNightChangePoints,
            morningActivityChangePoints,
            currentRegime.size,
            spendingModel,
            sleepModel
        )

        val coefficients = mapOf(
            "spending_intercept" to spendingModel.coefficients.getOrElse(0) { 0.0 },
            "spending_lateNight" to spendingModel.coefficients.getOrElse(1) { 0.0 },
            "spending_morningActivity" to spendingModel.coefficients.getOrElse(2) { 0.0 },
            "sleep_intercept" to sleepModel.coefficients.getOrElse(0) { 0.0 },
            "sleep_lateNight" to sleepModel.coefficients.getOrElse(1) { 0.0 },
            "sleep_morningActivity" to sleepModel.coefficients.getOrElse(2) { 0.0 }
        )

        return ModelPredictions(
            targetDate = targetDate.toString(),
            nextDaySpending = predictedSpending.toFloat().coerceAtLeast(0f),
            nextDaySleepScore = predictedSleep.toFloat().coerceIn(0f, 100f),
            nextDayRecovery = predictedRecovery.coerceIn(0f, 100f),
            confidence = ConfidenceLevel.HIGH,
            modelType = "Change-Point Detection",
            coefficients = coefficients,
            insights = insights,
            dataQualityUsed = "HIGH"
        )
    }

    /**
     * Detect change points using PELT-like algorithm
     * Simplified version: looks for significant mean shifts in 7-day windows
     */
    private fun detectChangePoints(
        series: List<Float>,
        dates: List<String>,
        featureName: String
    ): List<ChangePoint> {
        if (series.size < MIN_REGIME_LENGTH * 2) return emptyList()

        val changePoints = mutableListOf<ChangePoint>()
        val windowSize = MIN_REGIME_LENGTH

        // Scan for change points
        for (i in windowSize until series.size - windowSize) {
            val beforeWindow = series.subList(maxOf(0, i - windowSize), i)
            val afterWindow = series.subList(i, minOf(series.size, i + windowSize))

            val beforeMean = beforeWindow.average().toFloat()
            val afterMean = afterWindow.average().toFloat()

            // Calculate percent change
            val percentChange = if (beforeMean != 0f) {
                abs(afterMean - beforeMean) / beforeMean
            } else {
                0f
            }

            // Significant change detected
            if (percentChange > CHANGE_THRESHOLD) {
                changePoints.add(
                    ChangePoint(
                        index = i,
                        date = dates[i],
                        feature = featureName,
                        beforeMean = beforeMean,
                        afterMean = afterMean,
                        percentChange = percentChange
                    )
                )

                // Skip ahead to avoid detecting multiple points for same change
                // (We've modified the loop variable approach)
            }
        }

        // Filter to keep only most significant changes (no consecutive change points)
        return filterConsecutiveChangePoints(changePoints)
    }

    /**
     * Filter out consecutive change points, keeping only the most significant
     */
    private fun filterConsecutiveChangePoints(changePoints: List<ChangePoint>): List<ChangePoint> {
        if (changePoints.isEmpty()) return emptyList()

        val filtered = mutableListOf<ChangePoint>()
        var lastIndex = -MIN_REGIME_LENGTH

        changePoints.sortedBy { it.index }.forEach { cp ->
            if (cp.index - lastIndex >= MIN_REGIME_LENGTH) {
                filtered.add(cp)
                lastIndex = cp.index
            }
        }

        return filtered
    }

    /**
     * Fit regression on a specific behavioral regime
     */
    private fun fitRegressionOnRegime(
        regime: List<DailySummaryEntity>,
        outcomeType: OutcomeType
    ): MultipleRegressionResult {
        val outcomes = when (outcomeType) {
            OutcomeType.SPENDING -> regime.map { it.totalSpending }
            OutcomeType.SLEEP -> regime.map { it.sleepScore.toDouble() }
            OutcomeType.RECOVERY -> regime.map { it.recoveryScore.toDouble() }
        }

        val predictors = regime.map { listOf(
            it.lateNightPhoneIndex.toDouble(),
            it.morningActivityScore.toDouble() / 100
        )}

        return Statistics.multipleLinearRegression(outcomes, predictors)
    }

    /**
     * Build insights from detected change points
     */
    private fun buildChangePointInsights(
        lateNightChangePoints: List<ChangePoint>,
        morningActivityChangePoints: List<ChangePoint>,
        currentRegimeLength: Int,
        spendingModel: MultipleRegressionResult,
        sleepModel: MultipleRegressionResult
    ): List<String> {
        val insights = mutableListOf<String>()

        insights.add("Advanced change-point detection model")
        insights.add("Current behavioral regime: $currentRegimeLength days")

        // Late-night phone change points
        lateNightChangePoints.forEach { cp ->
            val direction = if (cp.afterMean > cp.beforeMean) "increased" else "decreased"
            insights.add("📊 Late-night phone $direction ${(cp.percentChange * 100).toInt()}% on ${cp.date}")
        }

        // Morning activity change points
        morningActivityChangePoints.forEach { cp ->
            val direction = if (cp.afterMean > cp.beforeMean) "increased" else "decreased"
            insights.add("📊 Morning activity $direction ${(cp.percentChange * 100).toInt()}% on ${cp.date}")
        }

        // Model quality
        insights.add("Spending model R²=${String.format("%.2f", spendingModel.rSquared)}")
        insights.add("Sleep model R²=${String.format("%.2f", sleepModel.rSquared)}")

        // Regime-specific insights
        if (spendingModel.coefficients.size >= 3) {
            val lateNightCoef = spendingModel.coefficients[1]
            if (abs(lateNightCoef) > 5.0) {
                insights.add("In current regime: Late-night phone impacts spending by $${String.format("%.2f", abs(lateNightCoef))} per point")
            }
        }

        if (insights.size == 2) {
            insights.add("No significant behavior changes detected - stable pattern")
        }

        return insights
    }

    private enum class OutcomeType {
        SPENDING, SLEEP, RECOVERY
    }
}
