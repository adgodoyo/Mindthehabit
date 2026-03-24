package com.example.mindthehabit.data.repository

import com.example.mindthehabit.data.health.HealthConnectManager
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BehaviorRepository @Inject constructor(
    private val behaviorDao: BehaviorDao,
    private val healthConnectManager: HealthConnectManager
) {
    // Local DB accessors
    fun getDailySummaries(): Flow<List<DailySummaryEntity>> = behaviorDao.getDailySummaries()

    suspend fun getSummaryForDate(date: String): DailySummaryEntity? =
        behaviorDao.getSummaryForDate(date)

    fun getRecentSpending(): Flow<List<SpendingEntryEntity>> = behaviorDao.getAllSpending()

    suspend fun insertSpending(amount: Double, category: String, note: String?, timestamp: Long = System.currentTimeMillis()) {
        behaviorDao.insertSpending(
            SpendingEntryEntity(
                timestamp = timestamp,
                amount = amount,
                category = category,
                note = note
            )
        )
    }

    suspend fun updateSpending(id: Long, amount: Double, category: String, note: String?, timestamp: Long) {
        behaviorDao.updateSpending(id, timestamp, amount, category, note)
    }

    suspend fun deleteSpending(id: Long) {
        behaviorDao.deleteSpending(id)
    }

    // Health Connect
    suspend fun fetchSteps(startTime: Instant, endTime: Instant) =
        healthConnectManager.readSteps(startTime, endTime)

    suspend fun fetchSleep(startTime: Instant, endTime: Instant) =
        healthConnectManager.readSleepSessions(startTime, endTime)

    suspend fun fetchHeartRate(startTime: Instant, endTime: Instant) =
        healthConnectManager.readHeartRate(startTime, endTime)
        
    suspend fun hasHealthPermissions() = healthConnectManager.hasAllPermissions()

    fun getHealthPermissions() = healthConnectManager.permissions

    fun getOptionalHealthPermissions() = healthConnectManager.optionalPermissions

    suspend fun getGrantedHealthPermissions() = healthConnectManager.getGrantedPermissions()

    /**
     * Delete old sensor data to prevent database bloat
     * @param retentionDays Number of days to keep (default: 90 days)
     */
    suspend fun cleanupOldSensorData(retentionDays: Int = 90) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        behaviorDao.deleteAllOldSensorData(cutoffTime)
    }
}
