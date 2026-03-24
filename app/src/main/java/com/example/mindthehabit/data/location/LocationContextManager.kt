package com.example.mindthehabit.data.location

import android.location.Location
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.ScreenEventEntity
import com.example.mindthehabit.data.local.entity.SettingsEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class LocationContextManager @Inject constructor(
    private val dao: BehaviorDao,
    private val gson: Gson
) {
    companion object {
        private const val SETTINGS_KEY_HOME_WIFI = "home_wifi_ssids"
        private const val SETTINGS_KEY_HOME_LOCATION = "home_location"
        private const val SETTINGS_KEY_HOME_RADIUS_METERS = "home_radius_meters"
        private const val DEFAULT_HOME_RADIUS_METERS = 100.0 // 100 meters
        private const val WIFI_LOOKUP_WINDOW_MINUTES = 5L
    }

    /**
     * Check if user is at home based on WiFi and GPS
     */
    suspend fun isAtHome(timestamp: Long): Boolean {
        // First check WiFi - faster and more accurate for indoor detection
        if (isConnectedToHomeWiFi(timestamp)) {
            return true
        }

        // Fallback to GPS geofence if WiFi not available
        return isWithinHomeGeoFence(timestamp)
    }

    /**
     * Check if connected to a known home WiFi network
     */
    private suspend fun isConnectedToHomeWiFi(timestamp: Long): Boolean {
        val homeSSIDs = getHomeWiFiNetworks()
        if (homeSSIDs.isEmpty()) return false

        // Look for WiFi events within 5 minutes of the timestamp
        val windowStart = timestamp - (WIFI_LOOKUP_WINDOW_MINUTES * 60 * 1000)
        val windowEnd = timestamp + (WIFI_LOOKUP_WINDOW_MINUTES * 60 * 1000)

        val recentWiFiEvents = dao.getWiFiEventsBetween(windowStart, windowEnd)

        return recentWiFiEvents.any { event ->
            event.ssid?.let { ssid ->
                homeSSIDs.any { homeSSID ->
                    ssid.contains(homeSSID, ignoreCase = true) ||
                    ssid.replace("\"", "") == homeSSID
                }
            } ?: false
        }
    }

    /**
     * Check if location is within home geofence
     */
    private suspend fun isWithinHomeGeoFence(timestamp: Long): Boolean {
        val homeLocation = getHomeLocation() ?: return false

        // Look for GPS location within 10 minutes of the timestamp
        val windowStart = timestamp - (10 * 60 * 1000)
        val windowEnd = timestamp + (10 * 60 * 1000)

        val nearestLocation = dao.getLocationNear(windowStart, windowEnd)
            ?: return false

        // Calculate distance between current location and home
        val distanceMeters = calculateDistance(
            nearestLocation.latitude,
            nearestLocation.longitude,
            homeLocation.latitude,
            homeLocation.longitude
        )

        val homeRadius = getHomeRadiusMeters()
        return distanceMeters <= homeRadius
    }

    /**
     * Classify phone session based on context (time + location)
     */
    suspend fun classifyPhoneSession(screenEvent: ScreenEventEntity): SessionContext {
        val isHome = isAtHome(screenEvent.timestamp)
        val localTime = Instant.ofEpochMilli(screenEvent.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val hour = localTime.hour

        return when {
            // Late night hours (11 PM - 4 AM)
            hour >= 23 || hour < 4 -> {
                if (isHome) {
                    SessionContext.LATE_NIGHT_HOME // Target behavior - in bed scrolling
                } else {
                    SessionContext.LATE_NIGHT_SOCIAL // Party, out with friends
                }
            }
            // Morning hours (6 AM - 10 AM)
            hour in 6..9 -> {
                SessionContext.MORNING_ACTIVE
            }
            // Daytime
            else -> {
                SessionContext.DAYTIME
            }
        }
    }

    /**
     * Batch classify screen events with session context
     */
    suspend fun classifyScreenEvents(events: List<ScreenEventEntity>): List<ScreenEventEntity> {
        return events.map { event ->
            val context = classifyPhoneSession(event)
            event.copy(sessionContext = context.name)
        }
    }

    /**
     * Get configured home WiFi networks
     */
    suspend fun getHomeWiFiNetworks(): List<String> {
        val setting = dao.getSetting(SETTINGS_KEY_HOME_WIFI)
        return if (setting != null) {
            gson.fromJson(setting.value, object : TypeToken<List<String>>() {}.type)
        } else {
            emptyList()
        }
    }

    /**
     * Add a WiFi network to the home list
     */
    suspend fun addHomeWiFiNetwork(ssid: String) {
        val current = getHomeWiFiNetworks().toMutableList()
        if (!current.contains(ssid)) {
            current.add(ssid)
            val json = gson.toJson(current)
            dao.insertSetting(SettingsEntity(SETTINGS_KEY_HOME_WIFI, json))
        }
    }

    /**
     * Remove a WiFi network from the home list
     */
    suspend fun removeHomeWiFiNetwork(ssid: String) {
        val current = getHomeWiFiNetworks().toMutableList()
        current.remove(ssid)
        val json = gson.toJson(current)
        dao.insertSetting(SettingsEntity(SETTINGS_KEY_HOME_WIFI, json))
    }

    /**
     * Set home location for geofencing
     */
    suspend fun setHomeLocation(latitude: Double, longitude: Double, radiusMeters: Double = DEFAULT_HOME_RADIUS_METERS) {
        val locationData = HomeLocationData(latitude, longitude)
        val json = gson.toJson(locationData)
        dao.insertSetting(SettingsEntity(SETTINGS_KEY_HOME_LOCATION, json))
        dao.insertSetting(SettingsEntity(SETTINGS_KEY_HOME_RADIUS_METERS, radiusMeters.toString()))
    }

    /**
     * Get configured home location
     */
    private suspend fun getHomeLocation(): HomeLocationData? {
        val setting = dao.getSetting(SETTINGS_KEY_HOME_LOCATION)
        return if (setting != null) {
            gson.fromJson(setting.value, HomeLocationData::class.java)
        } else {
            null
        }
    }

    /**
     * Get home geofence radius in meters
     */
    private suspend fun getHomeRadiusMeters(): Double {
        val setting = dao.getSetting(SETTINGS_KEY_HOME_RADIUS_METERS)
        return setting?.value?.toDoubleOrNull() ?: DEFAULT_HOME_RADIUS_METERS
    }

    /**
     * Calculate distance between two locations in meters (Haversine formula)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

/**
 * Session context types for phone usage
 */
enum class SessionContext {
    LATE_NIGHT_HOME,    // 11 PM - 4 AM at home (in bed scrolling) - TARGET BEHAVIOR
    LATE_NIGHT_SOCIAL,  // 11 PM - 4 AM away from home (party, social event)
    MORNING_ACTIVE,     // 6 AM - 10 AM (morning routine)
    DAYTIME            // Other times
}

/**
 * Home location data for geofencing
 */
data class HomeLocationData(
    val latitude: Double,
    val longitude: Double
)
