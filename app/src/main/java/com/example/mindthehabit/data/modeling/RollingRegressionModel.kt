package com.example.mindthehabit.data.modeling

import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Rolling Regression Model - For medium data (14-20 days)
 *
 * Uses 14-day rolling window to fit multiple linear regression models
 * Captures short-term trends and relationships
 *
 * Best for: Established patterns, adapts to recent behavior changes
 */
@Singleton
class RollingRegressionModel @Inject constructor() : TimeSeriesModel {

    companion object {
        private const val ROLLING_WINDOW_DAYS = 14
    }

    override suspend fun predict(
        historicalData: List<DailySummaryEntity>,
        targetDate: LocalDate
    ): ModelPredictions {
        if (historicalData.size < ROLLING_WINDOW_DAYS) {
            return ModelPredictions.empty(targetDate)
        }

        // Use the most recent 14 days
        val window = historicalData.takeLast(ROLLING_WINDOW_DAYS)

        // Fit regression models for each outcome
        val spendingModel = fitSpendingRegression(window)
        val sleepModel = fitSleepRegression(window)
        val recoveryModel = fitRecoveryRegression(window)

        // Use yesterday's features to predict tomorrow
        val yesterday = window.last()

        val predictedSpending = spendingModel.predict(listOf(
            yesterday.lateNightPhoneIndex.toDouble(),
            yesterday.morningActivityScore.toDouble() / 100,
            yesterday.sleepScore.toDouble() / 100
        ))

        val predictedSleep = sleepModel.predict(listOf(
            yesterday.lateNightPhoneIndex.toDouble(),
            yesterday.morningActivityScore.toDouble() / 100
        ))

        val predictedRecovery = recoveryModel.predict(listOf(
            yesterday.sleepScore.toDouble() / 100,
            yesterday.lateNightPhoneIndex.toDouble()
        ))

        // Build insights
        val insights = buildInsights(spendingModel, sleepModel, yesterday, window)

        // Build coefficients map
        val coefficients = mapOf(
            "spending_intercept" to spendingModel.coefficients.getOrElse(0) { 0.0 },
            "spending_lateNight" to spendingModel.coefficients.getOrElse(1) { 0.0 },
            "spending_morningActivity" to spendingModel.coefficients.getOrElse(2) { 0.0 },
            "spending_sleep" to spendingModel.coefficients.getOrElse(3) { 0.0 },
            "sleep_intercept" to sleepModel.coefficients.getOrElse(0) { 0.0 },
            "sleep_lateNight" to sleepModel.coefficients.getOrElse(1) { 0.0 },
            "sleep_morningActivity" to sleepModel.coefficients.getOrElse(2) { 0.0 }
        )

        return ModelPredictions(
            targetDate = targetDate.toString(),
            nextDaySpending = predictedSpending.toFloat().coerceAtLeast(0f),
            nextDaySleepScore = predictedSleep.toFloat().coerceIn(0f, 100f),
            nextDayRecovery = predictedRecovery.toFloat().coerceIn(0f, 100f),
            confidence = ConfidenceLevel.MEDIUM,
            modelType = "Rolling Regression (14d)",
            coefficients = coefficients,
            insights = insights,
            dataQualityUsed = "MEDIUM"
        )
    }

    /**
     * Fit multiple regression for spending
     * spending = b0 + b1*lateNight + b2*morningActivity + b3*sleepScore
     */
    private fun fitSpendingRegression(window: List<DailySummaryEntity>): MultipleRegressionResult {
        val outcomes = window.map { it.totalSpending }
        val predictors = window.map { listOf(
            it.lateNightPhoneIndex.toDouble(),
            it.morningActivityScore.toDouble() / 100,
            it.sleepScore.toDouble() / 100
        )}

        return Statistics.multipleLinearRegression(outcomes, predictors)
    }

    /**
     * Fit multiple regression for sleep
     * sleep = b0 + b1*lateNight + b2*morningActivity
     */
    private fun fitSleepRegression(window: List<DailySummaryEntity>): MultipleRegressionResult {
        val outcomes = window.map { it.sleepScore.toDouble() }
        val predictors = window.map { listOf(
            it.lateNightPhoneIndex.toDouble(),
            it.morningActivityScore.toDouble() / 100
        )}

        return Statistics.multipleLinearRegression(outcomes, predictors)
    }

    /**
     * Fit multiple regression for recovery
     * recovery = b0 + b1*sleepScore + b2*lateNight
     */
    private fun fitRecoveryRegression(window: List<DailySummaryEntity>): MultipleRegressionResult {
        val outcomes = window.map { it.recoveryScore.toDouble() }
        val predictors = window.map { listOf(
            it.sleepScore.toDouble() / 100,
            it.lateNightPhoneIndex.toDouble()
        )}

        return Statistics.multipleLinearRegression(outcomes, predictors)
    }

    /**
     * Build insights from regression models
     */
    private fun buildInsights(
        spendingModel: MultipleRegressionResult,
        sleepModel: MultipleRegressionResult,
        yesterday: DailySummaryEntity,
        window: List<DailySummaryEntity>
    ): List<String> {
        val insights = mutableListOf<String>()

        insights.add("Based on 14-day regression model")
        insights.add("Spending model fit: R²=${String.format("%.2f", spendingModel.rSquared)}")
        insights.add("Sleep model fit: R²=${String.format("%.2f", sleepModel.rSquared)}")

        // Interpret coefficients
        if (spendingModel.coefficients.size >= 4) {
            val lateNightCoef = spendingModel.coefficients[1]
            val morningActivityCoef = spendingModel.coefficients[2]

            if (abs(lateNightCoef) > 5.0) {
                val impact = if (lateNightCoef > 0) "increases" else "decreases"
                insights.add("Late-night phone $impact spending by $${String.format("%.2f", abs(lateNightCoef))} per index point")
            }

            if (abs(morningActivityCoef) > 5.0) {
                val impact = if (morningActivityCoef > 0) "increases" else "decreases"
                insights.add("Morning exercise $impact spending by $${String.format("%.2f", abs(morningActivityCoef))} per 10 points")
            }
        }

        if (sleepModel.coefficients.size >= 3) {
            val lateNightCoef = sleepModel.coefficients[1]

            if (abs(lateNightCoef) > 5.0) {
                val impact = if (lateNightCoef > 0) "improves" else "reduces"
                insights.add("Late-night phone $impact sleep by ${String.format("%.1f", abs(lateNightCoef))} points")
            }
        }

        // Trend analysis
        val recentSpending = window.takeLast(7).map { it.totalSpending }.average()
        val earlierSpending = window.take(7).map { it.totalSpending }.average()

        if (abs(recentSpending - earlierSpending) > 10.0) {
            val trend = if (recentSpending > earlierSpending) "increasing" else "decreasing"
            insights.add("Spending trend: $trend (${String.format("%.1f", abs(recentSpending - earlierSpending))} change)")
        }

        return insights
    }
}
