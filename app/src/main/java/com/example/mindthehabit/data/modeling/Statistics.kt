package com.example.mindthehabit.data.modeling

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical utilities for time-series analysis
 */
object Statistics {

    /**
     * Pearson correlation coefficient
     * Measures linear relationship between two variables (-1 to 1)
     */
    fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) return 0.0

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - meanX) * (yi - meanY) }
        val denomX = sqrt(x.sumOf { (it - meanX).pow(2) })
        val denomY = sqrt(y.sumOf { (it - meanY).pow(2) })

        return if (denomX == 0.0 || denomY == 0.0) {
            0.0
        } else {
            numerator / (denomX * denomY)
        }
    }

    /**
     * Lag-1 cross-correlation
     * Correlates yesterday's X with today's Y
     */
    fun lagCorrelation(x: List<Double>, y: List<Double>, lag: Int = 1): Double {
        if (x.size <= lag || y.size <= lag) return 0.0

        val xLagged = x.dropLast(lag)
        val yShifted = y.drop(lag)

        return pearsonCorrelation(xLagged, yShifted)
    }

    /**
     * Simple Linear Regression: y = a + bx
     * Returns (intercept, slope, r-squared)
     */
    fun linearRegression(x: List<Double>, y: List<Double>): RegressionResult {
        if (x.size != y.size || x.isEmpty()) {
            return RegressionResult(0.0, 0.0, 0.0)
        }

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - meanX) * (yi - meanY) }
        val denominator = x.sumOf { (it - meanX).pow(2) }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = meanY - slope * meanX

        // Calculate R-squared
        val yPredicted = x.map { intercept + slope * it }
        val ssResidual = y.zip(yPredicted).sumOf { (actual, predicted) -> (actual - predicted).pow(2) }
        val ssTotal = y.sumOf { (it - meanY).pow(2) }
        val rSquared = if (ssTotal != 0.0) 1 - (ssResidual / ssTotal) else 0.0

        return RegressionResult(intercept, slope, rSquared.coerceIn(0.0, 1.0))
    }

    /**
     * Multiple Linear Regression: y = b0 + b1*x1 + b2*x2 + ... + bn*xn
     * Uses normal equation: β = (X^T X)^(-1) X^T y
     */
    fun multipleLinearRegression(
        outcomes: List<Double>,
        predictors: List<List<Double>>
    ): MultipleRegressionResult {
        if (outcomes.isEmpty() || predictors.isEmpty() || outcomes.size != predictors.size) {
            return MultipleRegressionResult(listOf(), 0.0)
        }

        val n = outcomes.size
        val k = predictors.first().size

        // Add intercept term (column of 1s)
        val X = predictors.map { listOf(1.0) + it }

        // Calculate X^T X
        val XtX = multiplyMatrices(transpose(X), X)

        // Calculate X^T y
        val Xty = multiplyMatrixVector(transpose(X), outcomes)

        // Solve for coefficients using Gaussian elimination
        val coefficients = solveLinearSystem(XtX, Xty)

        // Calculate R-squared
        val meanY = outcomes.average()
        val predictions = predictors.map { predictRow ->
            coefficients[0] + predictRow.mapIndexed { idx, value ->
                coefficients[idx + 1] * value
            }.sum()
        }

        val ssResidual = outcomes.zip(predictions).sumOf { (actual, predicted) ->
            (actual - predicted).pow(2)
        }
        val ssTotal = outcomes.sumOf { (it - meanY).pow(2) }
        val rSquared = if (ssTotal != 0.0) 1 - (ssResidual / ssTotal) else 0.0

        return MultipleRegressionResult(coefficients, rSquared.coerceIn(0.0, 1.0))
    }

    /**
     * Predict value using regression coefficients
     */
    fun predict(coefficients: List<Double>, predictors: List<Double>): Double {
        if (coefficients.isEmpty()) return 0.0
        return coefficients[0] + predictors.mapIndexed { idx, value ->
            if (idx + 1 < coefficients.size) coefficients[idx + 1] * value else 0.0
        }.sum()
    }

    /**
     * Matrix multiplication: A * B
     */
    private fun multiplyMatrices(A: List<List<Double>>, B: List<List<Double>>): List<List<Double>> {
        val m = A.size
        val n = B[0].size
        val p = B.size

        return List(m) { i ->
            List(n) { j ->
                (0 until p).sumOf { k -> A[i][k] * B[k][j] }
            }
        }
    }

    /**
     * Matrix transpose
     */
    private fun transpose(matrix: List<List<Double>>): List<List<Double>> {
        val rows = matrix.size
        val cols = matrix[0].size
        return List(cols) { j ->
            List(rows) { i -> matrix[i][j] }
        }
    }

    /**
     * Matrix-vector multiplication
     */
    private fun multiplyMatrixVector(matrix: List<List<Double>>, vector: List<Double>): List<Double> {
        return matrix.map { row ->
            row.zip(vector).sumOf { (a, b) -> a * b }
        }
    }

    /**
     * Solve linear system Ax = b using Gaussian elimination
     */
    private fun solveLinearSystem(A: List<List<Double>>, b: List<Double>): List<Double> {
        val n = b.size
        val augmented = A.mapIndexed { i, row -> (row.toMutableList() + b[i]).toMutableList() }.toMutableList()

        // Forward elimination
        for (i in 0 until n) {
            // Find pivot
            var maxRow = i
            for (k in i + 1 until n) {
                if (kotlin.math.abs(augmented[k][i]) > kotlin.math.abs(augmented[maxRow][i])) {
                    maxRow = k
                }
            }

            // Swap rows
            val temp = augmented[i]
            augmented[i] = augmented[maxRow]
            augmented[maxRow] = temp

            // Make all rows below this one 0 in current column
            for (k in i + 1 until n) {
                if (augmented[i][i] != 0.0) {
                    val factor = augmented[k][i] / augmented[i][i]
                    for (j in i until n + 1) {
                        augmented[k][j] -= factor * augmented[i][j]
                    }
                }
            }
        }

        // Back substitution
        val x = MutableList(n) { 0.0 }
        for (i in n - 1 downTo 0) {
            x[i] = augmented[i][n]
            for (j in i + 1 until n) {
                x[i] -= augmented[i][j] * x[j]
            }
            if (augmented[i][i] != 0.0) {
                x[i] /= augmented[i][i]
            }
        }

        return x
    }

    /**
     * Calculate mean absolute error
     */
    fun meanAbsoluteError(actual: List<Double>, predicted: List<Double>): Double {
        if (actual.size != predicted.size || actual.isEmpty()) return 0.0
        return actual.zip(predicted).sumOf { (a, p) -> kotlin.math.abs(a - p) } / actual.size
    }

    /**
     * Calculate root mean squared error
     */
    fun rootMeanSquaredError(actual: List<Double>, predicted: List<Double>): Double {
        if (actual.size != predicted.size || actual.isEmpty()) return 0.0
        val mse = actual.zip(predicted).sumOf { (a, p) -> (a - p).pow(2) } / actual.size
        return sqrt(mse)
    }
}

/**
 * Simple regression result
 */
data class RegressionResult(
    val intercept: Double,
    val slope: Double,
    val rSquared: Double
)

/**
 * Multiple regression result
 */
data class MultipleRegressionResult(
    val coefficients: List<Double>, // [intercept, coef1, coef2, ...]
    val rSquared: Double
) {
    fun predict(predictors: List<Double>): Double {
        return Statistics.predict(coefficients, predictors)
    }
}
