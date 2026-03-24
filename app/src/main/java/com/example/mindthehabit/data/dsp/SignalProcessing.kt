package com.example.mindthehabit.data.dsp

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Digital Signal Processing utilities for sensor data
 *
 * Implements:
 * - Exponential Moving Average (EMA) for smoothing
 * - Median filtering for outlier removal
 * - Resampling irregular data to fixed intervals
 * - Outlier detection (IQR method)
 * - Variance and standard deviation calculations
 */
object SignalProcessing {

    /**
     * Exponential Moving Average filter for smoothing noisy sensor data
     *
     * Use cases:
     * - Ambient light sensor readings (reduce flickering)
     * - Heart rate smoothing
     *
     * @param values Input signal
     * @param alpha Smoothing factor (0-1). Higher = more responsive, lower = smoother
     *              Typical: 0.3 for light, 0.2 for HR
     */
    fun emaFilter(values: List<Float>, alpha: Float = 0.3f): List<Float> {
        if (values.isEmpty()) return emptyList()

        val smoothed = mutableListOf<Float>()
        var ema = values.first()
        smoothed.add(ema)

        for (i in 1 until values.size) {
            ema = alpha * values[i] + (1 - alpha) * ema
            smoothed.add(ema)
        }

        return smoothed
    }

    /**
     * Median filter for removing outliers and spikes
     *
     * Use cases:
     * - Heart rate outlier removal (sensor errors)
     * - WiFi signal strength smoothing
     *
     * @param values Input signal
     * @param windowSize Size of the median window (odd number recommended)
     */
    fun medianFilter(values: List<Float>, windowSize: Int = 5): List<Float> {
        if (values.isEmpty()) return emptyList()
        if (windowSize <= 1) return values

        return values.mapIndexed { index, _ ->
            val windowStart = (index - windowSize / 2).coerceAtLeast(0)
            val windowEnd = (index + windowSize / 2 + 1).coerceAtMost(values.size)

            val window = values.subList(windowStart, windowEnd).sorted()
            window[window.size / 2]
        }
    }

    /**
     * Resample irregular timestamped data into fixed time buckets
     *
     * Use cases:
     * - Convert sporadic sensor readings into hourly averages
     * - Create consistent time-series for modeling
     *
     * @param timestampedValues List of (timestamp, value) pairs
     * @param bucketSize Size of each bucket in milliseconds
     * @return Map of bucket index to aggregated value
     */
    fun resampleToBuckets(
        timestampedValues: List<Pair<Long, Float>>,
        bucketSize: Long
    ): Map<Long, Float> {
        if (timestampedValues.isEmpty()) return emptyMap()

        return timestampedValues
            .groupBy { (timestamp, _) -> timestamp / bucketSize }
            .mapValues { (_, samples) ->
                samples.map { it.second }.average().toFloat()
            }
    }

    /**
     * Resample data to hourly buckets (convenience method)
     */
    fun resampleToHourly(timestampedValues: List<Pair<Long, Float>>): Map<Int, Float> {
        if (timestampedValues.isEmpty()) return emptyMap()

        return timestampedValues
            .groupBy { (timestamp, _) ->
                // Extract hour of day (0-23)
                val instant = java.time.Instant.ofEpochMilli(timestamp)
                instant.atZone(java.time.ZoneId.systemDefault()).hour
            }
            .mapValues { (_, samples) ->
                samples.map { it.second }.average().toFloat()
            }
    }

    /**
     * Remove outliers using Interquartile Range (IQR) method
     *
     * Identifies values outside of [Q1 - 1.5*IQR, Q3 + 1.5*IQR] range
     *
     * @param values Input signal
     * @param multiplier IQR multiplier (default 1.5 for standard outlier detection)
     * @return Filtered signal with outliers removed
     */
    fun removeOutliers(values: List<Float>, multiplier: Float = 1.5f): List<Float> {
        if (values.size < 4) return values

        val sorted = values.sorted()
        val q1Index = sorted.size / 4
        val q3Index = 3 * sorted.size / 4

        val q1 = sorted[q1Index]
        val q3 = sorted[q3Index]
        val iqr = q3 - q1

        val lowerBound = q1 - multiplier * iqr
        val upperBound = q3 + multiplier * iqr

        return values.filter { it in lowerBound..upperBound }
    }

    /**
     * Identify outlier indices (returns indices of outliers, not filtered values)
     */
    fun identifyOutlierIndices(values: List<Float>, multiplier: Float = 1.5f): List<Int> {
        if (values.size < 4) return emptyList()

        val sorted = values.sorted()
        val q1Index = sorted.size / 4
        val q3Index = 3 * sorted.size / 4

        val q1 = sorted[q1Index]
        val q3 = sorted[q3Index]
        val iqr = q3 - q1

        val lowerBound = q1 - multiplier * iqr
        val upperBound = q3 + multiplier * iqr

        return values.mapIndexedNotNull { index, value ->
            if (value < lowerBound || value > upperBound) index else null
        }
    }

    /**
     * Calculate variance of a signal
     */
    fun variance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f

        val mean = values.average().toFloat()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }

    /**
     * Calculate standard deviation of a signal
     */
    fun standardDeviation(values: List<Float>): Float {
        return sqrt(variance(values))
    }

    /**
     * Calculate moving average
     *
     * @param values Input signal
     * @param windowSize Window size for averaging
     */
    fun movingAverage(values: List<Float>, windowSize: Int = 5): List<Float> {
        if (values.isEmpty() || windowSize <= 0) return values

        return values.windowed(windowSize, partialWindows = true) { window ->
            window.average().toFloat()
        }
    }

    /**
     * Downsample signal by taking every nth sample
     *
     * @param values Input signal
     * @param factor Downsampling factor (take every nth sample)
     */
    fun downsample(values: List<Float>, factor: Int): List<Float> {
        if (factor <= 1) return values
        return values.filterIndexed { index, _ -> index % factor == 0 }
    }

    /**
     * Calculate percentile of a signal
     *
     * @param values Input signal
     * @param percentile Percentile to calculate (0-100)
     */
    fun percentile(values: List<Float>, percentile: Float): Float? {
        if (values.isEmpty()) return null
        if (percentile < 0 || percentile > 100) return null

        val sorted = values.sorted()
        val index = ((percentile / 100) * (sorted.size - 1)).toInt()
        return sorted[index]
    }

    /**
     * Normalize signal to 0-1 range (min-max normalization)
     */
    fun normalize(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()

        val min = values.minOrNull() ?: return values
        val max = values.maxOrNull() ?: return values

        if (min == max) return values.map { 0.5f } // All values same

        return values.map { (it - min) / (max - min) }
    }

    /**
     * Z-score normalization (standardization)
     */
    fun standardize(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()

        val mean = values.average().toFloat()
        val stdDev = standardDeviation(values)

        if (stdDev == 0f) return values.map { 0f }

        return values.map { (it - mean) / stdDev }
    }

    /**
     * Calculate signal-to-noise ratio
     */
    fun signalToNoiseRatio(signal: List<Float>, noise: List<Float>): Float {
        if (signal.isEmpty() || noise.isEmpty()) return 0f

        val signalPower = signal.map { it.pow(2) }.average().toFloat()
        val noisePower = noise.map { it.pow(2) }.average().toFloat()

        return if (noisePower > 0) {
            10 * kotlin.math.log10(signalPower / noisePower)
        } else {
            Float.POSITIVE_INFINITY
        }
    }

    /**
     * Detect zero crossings in signal (useful for periodicity detection)
     */
    fun detectZeroCrossings(values: List<Float>): List<Int> {
        if (values.size < 2) return emptyList()

        return values.zipWithNext()
            .mapIndexedNotNull { index, (current, next) ->
                if ((current >= 0 && next < 0) || (current < 0 && next >= 0)) {
                    index
                } else {
                    null
                }
            }
    }

    /**
     * Calculate autocorrelation for lag detection
     *
     * @param values Input signal
     * @param maxLag Maximum lag to compute
     * @return List of autocorrelation values for lags 0 to maxLag
     */
    fun autocorrelation(values: List<Float>, maxLag: Int): List<Float> {
        if (values.isEmpty() || maxLag < 0) return emptyList()

        val mean = values.average().toFloat()
        val variance = variance(values)

        if (variance == 0f) return List(maxLag + 1) { 1f }

        return (0..maxLag).map { lag ->
            val sum = (0 until values.size - lag).sumOf { i ->
                ((values[i] - mean) * (values[i + lag] - mean)).toDouble()
            }
            (sum / ((values.size - lag) * variance)).toFloat()
        }
    }

    /**
     * Simple peak detection
     *
     * @param values Input signal
     * @param threshold Minimum value to be considered a peak
     * @return Indices of detected peaks
     */
    fun detectPeaks(values: List<Float>, threshold: Float = 0f): List<Int> {
        if (values.size < 3) return emptyList()

        return values.windowed(3).mapIndexedNotNull { index, window ->
            if (window[1] > window[0] &&
                window[1] > window[2] &&
                window[1] >= threshold) {
                index + 1
            } else {
                null
            }
        }
    }
}

/**
 * Extension functions for easier list operations
 */
fun List<Float>.emaFilter(alpha: Float = 0.3f) = SignalProcessing.emaFilter(this, alpha)
fun List<Float>.medianFilter(windowSize: Int = 5) = SignalProcessing.medianFilter(this, windowSize)
fun List<Float>.removeOutliers(multiplier: Float = 1.5f) = SignalProcessing.removeOutliers(this, multiplier)
fun List<Float>.variance() = SignalProcessing.variance(this)
fun List<Float>.standardDeviation() = SignalProcessing.standardDeviation(this)
fun List<Float>.normalize() = SignalProcessing.normalize(this)
fun List<Float>.standardize() = SignalProcessing.standardize(this)
