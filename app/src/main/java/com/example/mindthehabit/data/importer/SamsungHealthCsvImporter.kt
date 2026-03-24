package com.example.mindthehabit.data.importer

import android.content.Context
import android.util.Log
import com.example.mindthehabit.data.health.HealthConnectManager
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import com.example.mindthehabit.data.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * One-time utility to import historical Samsung Health CSV export data
 * This processes the last 30 days of data from Samsung Health export files
 */
@Singleton
class SamsungHealthCsvImporter @Inject constructor(
    private val behaviorDao: BehaviorDao,
    private val healthConnectManager: HealthConnectManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SamsungHealthCsvImporter"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private val DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    data class DailyHealthData(
        val date: LocalDate,
        val steps: Int = 0,
        val sleepMinutes: Int = 0,
        val sleepStart: LocalDateTime? = null,
        val sleepEnd: LocalDateTime? = null,
        val sleepStages: List<SleepStageData> = emptyList(),
        val samsungSleepScore: Int? = null,
        val sleepEfficiency: Int? = null,
        val heartRates: List<Float> = emptyList(),
        val hrvValues: List<Float> = emptyList(),
        val exerciseSessions: List<ExerciseData> = emptyList(),
        val activeMinutes: Int = 0
    )

    data class SleepStageData(
        val stage: Int, // 40001=awake, 40002=light, 40003=deep, 40004=REM
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    )

    data class ExerciseData(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val duration: Long, // milliseconds
        val calories: Float,
        val distance: Float
    )

    suspend fun importLast30Days(exportFolderPath: String): ImportResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting Samsung Health CSV Import ===")
        Log.d(TAG, "Export folder: $exportFolderPath")

        val exportFolder = File(exportFolderPath)
        if (!exportFolder.exists() || !exportFolder.isDirectory) {
            Log.e(TAG, "Export folder not found: $exportFolderPath")
            return@withContext ImportResult(success = false, message = "Export folder not found", daysImported = 0)
        }

        try {
            // Parse all CSV files
            val stepsData = parseStepsData(exportFolder)
            val sleepData = parseSleepData(exportFolder)
            val sleepStages = parseSleepStages(exportFolder)
            val heartRates = parseHeartRates(exportFolder)
            val hrvData = parseHrvData(exportFolder)
            val exercises = parseExercises(exportFolder)

            // Group by date for last 30 days
            val endDate = LocalDate.now().minusDays(1) // Yesterday
            val startDate = endDate.minusDays(29) // 30 days total

            Log.d(TAG, "Processing date range: $startDate to $endDate")

            val dailyData = mutableMapOf<LocalDate, DailyHealthData>()

            // Initialize all dates
            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                dailyData[currentDate] = DailyHealthData(date = currentDate)
                currentDate = currentDate.plusDays(1)
            }

            // Aggregate steps by date
            stepsData.forEach { (date, steps) ->
                if (date in dailyData) {
                    dailyData[date] = dailyData[date]!!.copy(steps = dailyData[date]!!.steps + steps)
                }
            }

            // Aggregate sleep data
            sleepData.forEach { sleep ->
                val date = sleep.startTime.toLocalDate()
                if (date in dailyData) {
                    val stages = sleepStages[sleep.sleepId] ?: emptyList()
                    dailyData[date] = dailyData[date]!!.copy(
                        sleepMinutes = sleep.durationMinutes,
                        sleepStart = sleep.startTime,
                        sleepEnd = sleep.endTime,
                        sleepStages = stages,
                        samsungSleepScore = sleep.sleepScore,
                        sleepEfficiency = sleep.efficiency
                    )
                }
            }

            // Aggregate heart rates by date
            heartRates.forEach { (time, hr) ->
                val date = time.toLocalDate()
                if (date in dailyData) {
                    dailyData[date] = dailyData[date]!!.copy(
                        heartRates = dailyData[date]!!.heartRates + hr
                    )
                }
            }

            // Aggregate HRV by date
            hrvData.forEach { (time, hrv) ->
                val date = time.toLocalDate()
                if (date in dailyData) {
                    dailyData[date] = dailyData[date]!!.copy(
                        hrvValues = dailyData[date]!!.hrvValues + hrv
                    )
                }
            }

            // Aggregate exercises by date
            exercises.forEach { exercise ->
                val date = exercise.startTime.toLocalDate()
                if (date in dailyData) {
                    dailyData[date] = dailyData[date]!!.copy(
                        exerciseSessions = dailyData[date]!!.exerciseSessions + exercise,
                        activeMinutes = dailyData[date]!!.activeMinutes + (exercise.duration / 60000).toInt()
                    )
                }
            }

            // Calculate behavioral features and insert into database
            var successCount = 0
            var failCount = 0

            dailyData.entries.sortedBy { it.key }.forEach { (date, data) ->
                try {
                    val features = calculateBehavioralFeatures(date, data, dailyData)
                    val summary = createDailySummary(date, features, data)

                    behaviorDao.insertDailySummary(summary)
                    Log.d(TAG, "✓ Imported $date: steps=${data.steps}, sleep=${data.sleepMinutes}min, score=${features.behaviorScore.toInt()}")
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to import $date: ${e.message}", e)
                    failCount++
                }
            }

            Log.d(TAG, "=== Import Complete ===")
            Log.d(TAG, "Success: $successCount days, Failed: $failCount days")

            ImportResult(
                success = true,
                message = "Imported $successCount days successfully",
                daysImported = successCount,
                daysFailed = failCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            ImportResult(success = false, message = "Import failed: ${e.message}", daysImported = 0)
        }
    }

    private fun parseStepsData(exportFolder: File): Map<LocalDate, Int> {
        val stepsFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("pedometer_day_summary") && it.extension == "csv"
        } ?: return emptyMap()

        Log.d(TAG, "Parsing steps from: ${stepsFile.name}")

        val stepsMap = mutableMapOf<LocalDate, Int>()
        stepsFile.bufferedReader().useLines { lines ->
            lines.drop(2).forEach { line -> // Skip header rows
                try {
                    val parts = line.split(",")
                    if (parts.size >= 22) {
                        val dayTimeMillis = parts[21].toLongOrNull()
                        val steps = parts[1].toIntOrNull() ?: 0

                        if (dayTimeMillis != null && steps > 0) {
                            val date = Instant.ofEpochMilli(dayTimeMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            stepsMap[date] = (stepsMap[date] ?: 0) + steps
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }

        Log.d(TAG, "Found ${stepsMap.size} days with step data")
        return stepsMap
    }

    private data class SleepSession(
        val sleepId: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val durationMinutes: Int,
        val sleepScore: Int?,
        val efficiency: Int?
    )

    private fun parseSleepData(exportFolder: File): List<SleepSession> {
        val sleepFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("com.samsung.shealth.sleep.") && it.extension == "csv"
        } ?: return emptyList()

        Log.d(TAG, "Parsing sleep from: ${sleepFile.name}")

        val sleepSessions = mutableListOf<SleepSession>()
        sleepFile.bufferedReader().useLines { lines ->
            lines.drop(2).forEach { line -> // Skip header rows
                try {
                    val parts = line.split(",")
                    if (parts.size >= 57) {
                        val startTimeStr = parts[49]
                        val endTimeStr = parts[56]
                        val datauuid = parts[57]
                        val sleepScore = parts[44].toIntOrNull()
                        val efficiency = parts[43].toIntOrNull()
                        val duration = parts[45].toIntOrNull()

                        if (startTimeStr.isNotBlank() && endTimeStr.isNotBlank() && datauuid.isNotBlank()) {
                            val startTime = parseDateTime(startTimeStr)
                            val endTime = parseDateTime(endTimeStr)

                            if (startTime != null && endTime != null) {
                                val durationMinutes = duration ?: java.time.Duration.between(startTime, endTime).toMinutes().toInt()

                                sleepSessions.add(
                                    SleepSession(
                                        sleepId = datauuid,
                                        startTime = startTime,
                                        endTime = endTime,
                                        durationMinutes = durationMinutes,
                                        sleepScore = sleepScore,
                                        efficiency = efficiency
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }

        Log.d(TAG, "Found ${sleepSessions.size} sleep sessions")
        return sleepSessions
    }

    private fun parseSleepStages(exportFolder: File): Map<String, List<SleepStageData>> {
        val stagesFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("sleep_stage") && it.extension == "csv"
        } ?: return emptyMap()

        Log.d(TAG, "Parsing sleep stages from: ${stagesFile.name}")

        val stagesMap = mutableMapOf<String, MutableList<SleepStageData>>()
        stagesFile.bufferedReader().useLines { lines ->
            lines.drop(2).forEach { line -> // Skip header rows
                try {
                    val parts = line.split(",")
                    if (parts.size >= 13) {
                        val startTimeStr = parts[1]
                        val sleepId = parts[2]
                        val endTimeStr = parts[11]
                        val stage = parts[7].toIntOrNull()

                        if (startTimeStr.isNotBlank() && endTimeStr.isNotBlank() && sleepId.isNotBlank() && stage != null) {
                            val startTime = parseDateTime(startTimeStr)
                            val endTime = parseDateTime(endTimeStr)

                            if (startTime != null && endTime != null) {
                                val stageData = SleepStageData(stage, startTime, endTime)
                                stagesMap.getOrPut(sleepId) { mutableListOf() }.add(stageData)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }

        Log.d(TAG, "Found sleep stages for ${stagesMap.size} sessions")
        return stagesMap
    }

    private fun parseHeartRates(exportFolder: File): Map<LocalDateTime, Float> {
        val hrFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("tracker.heart_rate") && it.extension == "csv"
        } ?: return emptyMap()

        Log.d(TAG, "Parsing heart rates from: ${hrFile.name}")

        val hrMap = mutableMapOf<LocalDateTime, Float>()
        hrFile.bufferedReader().useLines { lines ->
            lines.drop(2).forEach { line -> // Skip header rows
                try {
                    val parts = line.split(",")
                    if (parts.size >= 21) {
                        val timeStr = parts[4]
                        val hr = parts[20].toFloatOrNull()

                        if (timeStr.isNotBlank() && hr != null && hr > 0) {
                            val time = parseDateTime(timeStr)
                            if (time != null) {
                                hrMap[time] = hr
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }

        Log.d(TAG, "Found ${hrMap.size} heart rate records")
        return hrMap
    }

    private fun parseHrvData(exportFolder: File): Map<LocalDateTime, Float> {
        val hrvFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("com.samsung.health.hrv") && it.extension == "csv"
        } ?: return emptyMap()

        Log.d(TAG, "Parsing HRV from: ${hrvFile.name}")

        val hrvMap = mutableMapOf<LocalDateTime, Float>()

        // Note: HRV values are in binning_data JSON files
        // For simplicity, we'll use heart rate variability calculation from HR data
        // In a real implementation, you'd parse the JSON binning files

        Log.d(TAG, "HRV parsing from binning data not implemented - using HR-based calculation")
        return hrvMap
    }

    private fun parseExercises(exportFolder: File): List<ExerciseData> {
        val exerciseFile = exportFolder.listFiles()?.firstOrNull {
            it.name.contains("com.samsung.shealth.exercise.") && it.extension == "csv"
        } ?: return emptyList()

        Log.d(TAG, "Parsing exercises from: ${exerciseFile.name}")

        val exercises = mutableListOf<ExerciseData>()
        exerciseFile.bufferedReader().useLines { lines ->
            lines.drop(2).forEach { line -> // Skip header rows
                try {
                    val parts = line.split(",")
                    if (parts.size >= 72) {
                        val startTimeStr = parts[32]
                        val endTimeStr = parts[70]
                        val duration = parts[27].toLongOrNull()
                        val calories = parts[53].toFloatOrNull() ?: 0f
                        val distance = parts[51].toFloatOrNull() ?: 0f

                        if (startTimeStr.isNotBlank() && endTimeStr.isNotBlank() && duration != null) {
                            val startTime = parseDateTime(startTimeStr)
                            val endTime = parseDateTime(endTimeStr)

                            if (startTime != null && endTime != null) {
                                exercises.add(
                                    ExerciseData(
                                        startTime = startTime,
                                        endTime = endTime,
                                        duration = duration,
                                        calories = calories,
                                        distance = distance
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }

        Log.d(TAG, "Found ${exercises.size} exercise sessions")
        return exercises
    }

    private fun parseDateTime(dateStr: String): LocalDateTime? {
        return try {
            if (dateStr.contains(".")) {
                LocalDateTime.parse(dateStr, DATE_FORMATTER)
            } else {
                LocalDateTime.parse(dateStr, DATE_FORMATTER_SHORT)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateBehavioralFeatures(
        date: LocalDate,
        data: DailyHealthData,
        allDailyData: Map<LocalDate, DailyHealthData>
    ): BehavioralFeatures {
        // Calculate morning activity score
        val morningActivityScore = calculateMorningActivityScore(data)

        // Calculate sleep recovery score
        val sleepRecoveryScore = calculateSleepRecoveryScore(data)

        // Calculate HRV metrics
        val hrvMean = if (data.hrvValues.isNotEmpty()) {
            data.hrvValues.average().toFloat()
        } else if (data.heartRates.isNotEmpty()) {
            // Calculate RMSSD from heart rates as approximation
            calculateRMSSD(data.heartRates)
        } else null

        val hrvStdDev = if (data.hrvValues.isNotEmpty()) {
            calculateStdDev(data.hrvValues)
        } else null

        // Calculate resting HR (lowest HR of the day, typically during sleep)
        val restingHR = data.heartRates.minOrNull()

        // Calculate sleep quality score
        val sleepQualityScore = calculateSleepQuality(data)

        // Calculate energy level
        val energyMetrics = if (sleepQualityScore != null) {
            val previousDayActiveMinutes = allDailyData[date.minusDays(1)]?.activeMinutes ?: 0
            healthConnectManager.calculateEnergyLevel(
                sleepQuality = sleepQualityScore,
                hrvMean = hrvMean,
                restingHR = restingHR,
                previousDayActiveMinutes = previousDayActiveMinutes
            )
        } else null

        // Calculate recovery score
        val recoveryScore = calculateRecoveryScore(sleepRecoveryScore, hrvMean, restingHR)

        // Late night phone index - not available from Samsung Health export, use default
        val lateNightPhoneIndex = 50f // Neutral default

        // Spending - not available from Samsung Health export
        val totalSpending = 0.0

        // Calculate overall behavior score
        val behaviorScore = (morningActivityScore * 0.3f +
                            sleepRecoveryScore * 0.3f +
                            recoveryScore * 0.2f +
                            (100 - lateNightPhoneIndex) * 0.2f)

        // Calculate data quality based on available metrics
        val availableMetrics = listOf(
            data.steps > 0,
            data.sleepMinutes > 0,
            data.heartRates.isNotEmpty(),
            data.exerciseSessions.isNotEmpty()
        ).count { it }

        val dataQuality = when {
            availableMetrics >= 3 -> DataQuality.HIGH
            availableMetrics >= 2 -> DataQuality.MEDIUM
            else -> DataQuality.LOW
        }

        val rawMetrics = """
            {"steps":${data.steps},"sleep_min":${data.sleepMinutes},"exercises":${data.exerciseSessions.size},"hr_samples":${data.heartRates.size},"source":"csv_import"}
        """.trimIndent()

        return BehavioralFeatures(
            date = date.toString(),
            sleepScore = sleepRecoveryScore,
            morningActivityScore = morningActivityScore,
            lateNightPhoneIndex = lateNightPhoneIndex,
            totalSpending = totalSpending,
            recoveryScore = recoveryScore,
            behaviorScore = behaviorScore,
            hrvMean = hrvMean,
            hrvStdDev = hrvStdDev,
            restingHR = restingHR,
            rawMetrics = rawMetrics,
            dataQuality = dataQuality,
            sleepQualityScore = sleepQualityScore,
            samsungSleepScore = data.samsungSleepScore,
            energyLevelScore = energyMetrics?.score,
            energyLevel = energyMetrics?.level
        )
    }

    private fun calculateMorningActivityScore(data: DailyHealthData): Float {
        var score = 0f

        // Morning exercise (6am-10am)
        val morningExercises = data.exerciseSessions.filter {
            val hour = it.startTime.hour
            hour in 6..9
        }

        if (morningExercises.isNotEmpty()) {
            score += 50f // Base score for any morning exercise
            val totalDuration = morningExercises.sumOf { it.duration } / 60000 // minutes
            score += (totalDuration.toFloat() * 2f).coerceAtMost(30f) // Up to 30 more points
        }

        // Morning steps boost (assume first 25% of daily steps are morning)
        val morningSteps = (data.steps * 0.25).toInt()
        score += (morningSteps / 30f).coerceAtMost(20f)

        return score.coerceIn(0f, 100f)
    }

    private fun calculateSleepRecoveryScore(data: DailyHealthData): Float {
        if (data.sleepMinutes == 0) return 50f // Default if no sleep data

        var score = 0f

        // Duration score (35 points): 7-9 hours optimal
        val hours = data.sleepMinutes / 60f
        score += when {
            hours >= 7f && hours <= 9f -> 35f
            hours >= 6f && hours < 7f -> 28f
            hours > 9f && hours <= 10f -> 28f
            hours >= 5f && hours < 6f -> 20f
            else -> 10f
        }

        // Sleep stages score (40 points)
        if (data.sleepStages.isNotEmpty()) {
            val totalMinutes = data.sleepMinutes
            val deepMinutes = data.sleepStages.filter { it.stage == 40003 }
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            val remMinutes = data.sleepStages.filter { it.stage == 40004 }
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }

            val deepPercent = deepMinutes.toFloat() / totalMinutes
            val remPercent = remMinutes.toFloat() / totalMinutes

            // Deep sleep: 15-20% ideal
            score += when {
                deepPercent >= 0.15f && deepPercent <= 0.20f -> 20f
                deepPercent >= 0.10f && deepPercent < 0.15f -> 15f
                deepPercent > 0.20f && deepPercent <= 0.25f -> 15f
                else -> 8f
            }

            // REM sleep: 20-25% ideal
            score += when {
                remPercent >= 0.20f && remPercent <= 0.25f -> 20f
                remPercent >= 0.15f && remPercent < 0.20f -> 15f
                remPercent > 0.25f && remPercent <= 0.30f -> 15f
                else -> 8f
            }
        } else {
            score += 20f // Default if no stage data
        }

        // Efficiency score (25 points)
        val efficiency = data.sleepEfficiency ?: 85
        score += when {
            efficiency >= 85 -> 25f
            efficiency >= 75 -> 20f
            efficiency >= 65 -> 15f
            else -> 10f
        }

        return score.coerceIn(0f, 100f)
    }

    private fun calculateSleepQuality(data: DailyHealthData): Int? {
        if (data.sleepMinutes == 0) return null

        // Use Samsung's score if available
        data.samsungSleepScore?.let { return it }

        // Otherwise calculate our own
        var score = 0f

        val hours = data.sleepMinutes / 60f

        // Duration (30 points)
        score += when {
            hours >= 7f && hours <= 9f -> 30f
            hours >= 6f && hours < 7f -> 25f
            hours > 9f && hours <= 10f -> 25f
            else -> 15f
        }

        // Efficiency (30 points)
        val efficiency = data.sleepEfficiency ?: 85
        score += (efficiency / 100f * 30f)

        // Sleep stages (40 points)
        if (data.sleepStages.isNotEmpty()) {
            val totalMinutes = data.sleepMinutes
            val deepMinutes = data.sleepStages.filter { it.stage == 40003 }
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            val remMinutes = data.sleepStages.filter { it.stage == 40004 }
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }

            val deepPercent = deepMinutes.toFloat() / totalMinutes
            val remPercent = remMinutes.toFloat() / totalMinutes

            score += (deepPercent / 0.20f * 20f).coerceAtMost(20f)
            score += (remPercent / 0.25f * 20f).coerceAtMost(20f)
        } else {
            score += 20f
        }

        return score.coerceIn(0f, 100f).toInt()
    }

    private fun calculateRecoveryScore(sleepScore: Float, hrvMean: Float?, restingHR: Float?): Float {
        var score = sleepScore * 0.5f // Sleep is 50% of recovery

        // HRV component (30%)
        hrvMean?.let { hrv ->
            val hrvScore = when {
                hrv >= 60f -> 30f
                hrv >= 50f -> 25f
                hrv >= 40f -> 20f
                hrv >= 30f -> 15f
                else -> 10f
            }
            score += hrvScore
        } ?: run { score += 15f }

        // Resting HR component (20%)
        restingHR?.let { hr ->
            val hrScore = when {
                hr <= 60f -> 20f
                hr <= 70f -> 15f
                hr <= 80f -> 10f
                else -> 5f
            }
            score += hrScore
        } ?: run { score += 10f }

        return score.coerceIn(0f, 100f)
    }

    private fun calculateRMSSD(heartRates: List<Float>): Float {
        if (heartRates.size < 2) return 50f

        val differences = heartRates.zipWithNext { a, b -> (b - a) * (b - a) }
        val meanSquaredDiff = differences.average()
        return sqrt(meanSquaredDiff).toFloat()
    }

    private fun calculateStdDev(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }

    private fun createDailySummary(
        date: LocalDate,
        features: BehavioralFeatures,
        data: DailyHealthData
    ): DailySummaryEntity {
        return DailySummaryEntity(
            date = date.toString(),
            sleepScore = features.sleepScore,
            morningActivityScore = features.morningActivityScore,
            lateNightPhoneIndex = features.lateNightPhoneIndex,
            totalSpending = features.totalSpending,
            recoveryScore = features.recoveryScore,
            behaviorScore = features.behaviorScore,
            rawMetricsJson = buildRawMetricsJson(data),
            hrvMean = features.hrvMean,
            hrvStdDev = features.hrvStdDev,
            restingHR = features.restingHR,
            sleepQualityScore = features.sleepQualityScore,
            samsungSleepScore = features.samsungSleepScore,
            energyLevelScore = features.energyLevelScore,
            energyLevel = features.energyLevel
        )
    }

    private fun buildRawMetricsJson(data: DailyHealthData): String {
        return """
        {
            "steps": ${data.steps},
            "sleepMinutes": ${data.sleepMinutes},
            "activeMinutes": ${data.activeMinutes},
            "exerciseSessions": ${data.exerciseSessions.size},
            "avgHeartRate": ${data.heartRates.average().takeIf { !it.isNaN() } ?: 0},
            "source": "samsung_health_csv_import"
        }
        """.trimIndent()
    }

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val daysImported: Int,
        val daysFailed: Int = 0
    )
}
