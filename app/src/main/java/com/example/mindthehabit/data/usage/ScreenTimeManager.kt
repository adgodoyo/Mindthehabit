package com.example.mindthehabit.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenTimeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        ?: throw IllegalStateException("UsageStatsManager not available")
    private val packageManager = context.packageManager

    /**
     * Get detailed app usage events for a specific time window
     */
    fun getAppUsageEvents(startTime: Long, endTime: Long): List<AppUsageEvent> {
        val events = mutableListOf<AppUsageEvent>()
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {

                val appName = getAppName(event.packageName)

                events.add(
                    AppUsageEvent(
                        timestamp = event.timeStamp,
                        packageName = event.packageName ?: "unknown",
                        appName = appName,
                        eventType = if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                            "RESUMED" else "PAUSED"
                    )
                )
            }
        }

        return events
    }

    /**
     * Calculate total screen time for a specific time window
     */
    fun getTotalScreenTime(startTime: Long, endTime: Long): Long {
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return usageStats?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    /**
     * Get late-night phone usage (11 PM - 4 AM)
     * Returns aggregated metrics for the specified date
     */
    fun getLateNightUsage(date: LocalDate): LateNightSession {
        val lateNightStart = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lateNightEnd = date.plusDays(1).atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getAppUsageEvents(lateNightStart, lateNightEnd)

        // Group by package to calculate per-app usage
        val appUsageMap = mutableMapOf<String, Long>()
        var currentApp: String? = null
        var currentStartTime: Long = 0
        val sessionDurations = mutableListOf<Long>()

        events.forEach { event ->
            when (event.eventType) {
                "RESUMED" -> {
                    currentApp = event.packageName
                    currentStartTime = event.timestamp
                }
                "PAUSED" -> {
                    if (currentApp == event.packageName && currentStartTime > 0) {
                        val duration = event.timestamp - currentStartTime
                        appUsageMap[event.packageName] = (appUsageMap[event.packageName] ?: 0L) + duration
                        sessionDurations.add(duration)
                        currentApp = null
                        currentStartTime = 0
                    }
                }
            }
        }

        // Get top 5 apps by usage time
        val topApps = appUsageMap.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { getAppName(it.key) }

        val totalMinutes = appUsageMap.values.sum() / 60_000 // Convert to minutes
        val unlockCount = events.count { it.eventType == "RESUMED" }

        return LateNightSession(
            totalMinutes = totalMinutes.toInt(),
            unlockCount = unlockCount,
            topApps = topApps,
            sessionDurations = sessionDurations.map { (it / 60_000).toInt() } // Convert to minutes
        )
    }

    /**
     * Get top apps by usage for a specific time period
     */
    fun getTopApps(startTime: Long, endTime: Long, limit: Int = 10): List<AppUsageSummary> {
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return emptyList()

        return usageStats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { stats ->
                AppUsageSummary(
                    packageName = stats.packageName,
                    appName = getAppName(stats.packageName),
                    totalTimeMs = stats.totalTimeInForeground,
                    firstTimestamp = stats.firstTimeStamp,
                    lastTimestamp = stats.lastTimeStamp
                )
            }
    }

    /**
     * Check if usage stats permission is granted
     */
    fun hasUsageStatsPermission(): Boolean {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000,
            time
        )
        return stats != null && stats.isNotEmpty()
    }

    /**
     * Get human-readable app name from package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.split(".").lastOrNull() ?: packageName
        }
    }
}

/**
 * Data class representing a single app usage event
 */
data class AppUsageEvent(
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val eventType: String // RESUMED or PAUSED
)

/**
 * Data class for late-night phone usage session
 */
data class LateNightSession(
    val totalMinutes: Int,
    val unlockCount: Int,
    val topApps: List<String>,
    val sessionDurations: List<Int> // Duration of each session in minutes
)

/**
 * Summary of app usage over a time period
 */
data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val firstTimestamp: Long,
    val lastTimestamp: Long
)
