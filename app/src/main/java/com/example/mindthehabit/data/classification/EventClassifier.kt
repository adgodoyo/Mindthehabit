package com.example.mindthehabit.data.classification

import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.ScreenEventEntity
import com.example.mindthehabit.data.location.LocationContextManager
import com.example.mindthehabit.data.location.SessionContext
import com.example.mindthehabit.data.usage.ScreenTimeManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event Classifier - Distinguishes between different types of phone usage contexts
 *
 * Key distinction: Scrolling at home late night vs being at a party/social event
 */
@Singleton
class EventClassifier @Inject constructor(
    private val dao: BehaviorDao,
    private val locationContextManager: LocationContextManager,
    private val screenTimeManager: ScreenTimeManager
) {

    /**
     * Classify a single screen event with full context
     */
    suspend fun classifyEvent(event: ScreenEventEntity): ClassifiedEvent {
        val sessionContext = locationContextManager.classifyPhoneSession(event)

        val instant = Instant.ofEpochMilli(event.timestamp)
        val localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()

        // Get WiFi signal strength at the time (indicator of being stationary vs moving)
        val wifiSignal = getWiFiSignalStrength(event.timestamp)

        // Determine activity type based on multiple signals
        val activityType = when (sessionContext) {
            SessionContext.LATE_NIGHT_HOME -> {
                // At home late at night - likely in bed scrolling
                determineHomeActivityType(event.timestamp, wifiSignal)
            }
            SessionContext.LATE_NIGHT_SOCIAL -> {
                // Away from home late at night
                determineSocialActivityType(event.timestamp, wifiSignal)
            }
            SessionContext.MORNING_ACTIVE -> {
                ActivityType.MORNING_ROUTINE
            }
            SessionContext.DAYTIME -> {
                ActivityType.DAYTIME_USE
            }
        }

        return ClassifiedEvent(
            event = event,
            sessionContext = sessionContext,
            activityType = activityType,
            signalStrength = wifiSignal,
            localTime = localTime.toString(),
            date = date.toString()
        )
    }

    /**
     * Determine specific activity type when at home
     */
    private suspend fun determineHomeActivityType(timestamp: Long, wifiSignal: Int?): ActivityType {
        // Strong WiFi signal + late night = in bed scrolling
        return if (wifiSignal != null && wifiSignal > -70) {
            ActivityType.IN_BED_SCROLLING
        } else {
            ActivityType.HOME_LATE_NIGHT
        }
    }

    /**
     * Determine specific activity type when away from home
     */
    private suspend fun determineSocialActivityType(timestamp: Long, wifiSignal: Int?): ActivityType {
        // Check for frequent WiFi changes (moving between locations)
        val wifiChanges = getWiFiChangeCount(timestamp, windowMinutes = 30)

        return when {
            wifiChanges >= 3 -> ActivityType.MOVING_AROUND // Bar hopping, walking
            wifiSignal == null -> ActivityType.UNDERGROUND_NO_SIGNAL // Underground transport, basement
            else -> ActivityType.PARTY_SOCIAL // Stationary at a social venue
        }
    }

    /**
     * Get WiFi signal strength at a specific timestamp
     */
    private suspend fun getWiFiSignalStrength(timestamp: Long): Int? {
        val windowStart = timestamp - (2 * 60 * 1000) // 2 minutes before
        val windowEnd = timestamp + (2 * 60 * 1000) // 2 minutes after

        val wifiEvents = dao.getWiFiEventsBetween(windowStart, windowEnd)

        return wifiEvents
            .filter { it.signalStrength != 0 }
            .minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }
            ?.signalStrength
    }

    /**
     * Count WiFi network changes in a time window
     */
    private suspend fun getWiFiChangeCount(timestamp: Long, windowMinutes: Long): Int {
        val windowStart = timestamp - (windowMinutes * 60 * 1000)
        val windowEnd = timestamp + (windowMinutes * 60 * 1000)

        val wifiEvents = dao.getWiFiEventsBetween(windowStart, windowEnd)

        // Count unique SSIDs
        return wifiEvents
            .mapNotNull { it.ssid }
            .distinct()
            .size
    }

    /**
     * Classify all unclassified screen events for a date
     */
    suspend fun classifyEventsForDate(date: LocalDate): List<ClassifiedEvent> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = dao.getScreenEventsBetween(startOfDay, endOfDay)

        return events.map { event ->
            classifyEvent(event)
        }
    }

    /**
     * Update screen events with classification in database
     */
    suspend fun updateEventClassifications(classifiedEvents: List<ClassifiedEvent>) {
        classifiedEvents.forEach { classified ->
            val updatedEvent = classified.event.copy(
                sessionContext = classified.sessionContext.name + "|" + classified.activityType.name
            )
            dao.insertScreenEvent(updatedEvent)
        }
    }

    /**
     * Get late-night scrolling statistics for a date
     */
    suspend fun getLateNightScrollingStats(date: LocalDate): LateNightStats {
        val lateNightStart = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lateNightEnd = date.plusDays(1).atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = dao.getScreenEventsBetween(lateNightStart, lateNightEnd)
        val classified = events.map { classifyEvent(it) }

        val inBedScrolling = classified.filter { it.activityType == ActivityType.IN_BED_SCROLLING }
        val socialEvents = classified.filter {
            it.activityType in listOf(
                ActivityType.PARTY_SOCIAL,
                ActivityType.MOVING_AROUND,
                ActivityType.UNDERGROUND_NO_SIGNAL
            )
        }

        // Calculate session durations
        val sessions = calculateSessionDurations(events)

        return LateNightStats(
            totalEvents = events.size,
            inBedScrollingEvents = inBedScrolling.size,
            socialEvents = socialEvents.size,
            inBedScrollingMinutes = sessions.filter { it.isAtHome }.sumOf { it.durationMinutes },
            socialMinutes = sessions.filter { !it.isAtHome }.sumOf { it.durationMinutes },
            unlockCount = events.count { it.eventType == "UNLOCK" },
            longestSessionMinutes = sessions.maxOfOrNull { it.durationMinutes } ?: 0
        )
    }

    /**
     * Calculate session durations from screen events
     */
    private fun calculateSessionDurations(events: List<ScreenEventEntity>): List<PhoneSession> {
        val sessions = mutableListOf<PhoneSession>()
        var sessionStart: Long? = null
        var isAtHome = false

        events.sortedBy { it.timestamp }.forEach { event ->
            when (event.eventType) {
                "ON", "UNLOCK" -> {
                    if (sessionStart == null) {
                        sessionStart = event.timestamp
                        isAtHome = event.sessionContext?.contains("HOME") ?: false
                    }
                }
                "OFF" -> {
                    sessionStart?.let { start ->
                        val duration = ((event.timestamp - start) / 60_000).toInt() // Convert to minutes
                        if (duration > 0) {
                            sessions.add(PhoneSession(start, event.timestamp, duration, isAtHome))
                        }
                    }
                    sessionStart = null
                }
            }
        }

        return sessions
    }
}

/**
 * Detailed activity types for behavioral analysis
 */
enum class ActivityType {
    IN_BED_SCROLLING,        // Late night at home with strong WiFi - target behavior
    HOME_LATE_NIGHT,         // Late night at home, general
    PARTY_SOCIAL,            // At a social venue (stable WiFi, not home)
    MOVING_AROUND,           // Moving between locations (frequent WiFi changes)
    UNDERGROUND_NO_SIGNAL,   // No WiFi signal (underground transport, basement)
    MORNING_ROUTINE,         // Morning hours (6-10 AM)
    DAYTIME_USE             // Regular daytime usage
}

/**
 * Fully classified event with context
 */
data class ClassifiedEvent(
    val event: ScreenEventEntity,
    val sessionContext: SessionContext,
    val activityType: ActivityType,
    val signalStrength: Int?,
    val localTime: String,
    val date: String
)

/**
 * Phone usage session
 */
data class PhoneSession(
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val isAtHome: Boolean
)

/**
 * Statistics for late-night phone usage
 */
data class LateNightStats(
    val totalEvents: Int,
    val inBedScrollingEvents: Int,
    val socialEvents: Int,
    val inBedScrollingMinutes: Int,
    val socialMinutes: Int,
    val unlockCount: Int,
    val longestSessionMinutes: Int
)
