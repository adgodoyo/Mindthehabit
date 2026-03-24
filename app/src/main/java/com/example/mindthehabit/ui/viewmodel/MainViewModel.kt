package com.example.mindthehabit.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindthehabit.data.backfill.BackfillProgress
import com.example.mindthehabit.data.backfill.BackfillResult
import com.example.mindthehabit.data.backfill.HistoricalDataBackfillService
import com.example.mindthehabit.data.local.entity.DailySummaryEntity
import com.example.mindthehabit.data.modeling.AdaptiveTimeSeriesEngine
import com.example.mindthehabit.data.modeling.ModelPredictions
import com.example.mindthehabit.data.repository.BehaviorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PermissionStatus(
    val healthConnect: Boolean = false,
    val gps: Boolean = false,
    val wifi: Boolean = false,
    val usageStats: Boolean = false,
    val notifications: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: BehaviorRepository,
    private val timeSeriesEngine: AdaptiveTimeSeriesEngine,
    private val backfillService: HistoricalDataBackfillService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    // Backward compatibility for existing UI
    val hasPermissions: StateFlow<Boolean> = MutableStateFlow(false) // Will update this in sync

    // Today's summary for Dashboard
    private val _todaySummary = MutableStateFlow<DailySummaryEntity?>(null)
    val todaySummary: StateFlow<DailySummaryEntity?> = _todaySummary.asStateFlow()

    // Predictions for Dashboard and Prediction screen
    private val _predictions = MutableStateFlow<ModelPredictions?>(null)
    val predictions: StateFlow<ModelPredictions?> = _predictions.asStateFlow()

    // All daily summaries (for Timeline)
    val dailySummaries = repository.getDailySummaries()

    // Recent spending
    val recentSpending = repository.getRecentSpending()

    // Backfill progress
    val backfillProgress: StateFlow<BackfillProgress> = backfillService.backfillProgress

    init {
        updateAllPermissions()
        loadTodaySummary()
        loadPredictions()
    }

    fun updateAllPermissions() {
        viewModelScope.launch {
            val health = repository.hasHealthPermissions()

            // GPS permission (fine or coarse location)
            val gps = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            // WiFi - check if WiFi access state permission is granted (auto-granted)
            val wifi = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED

            // Usage stats permission
            val usage = hasUsageStatsPermission()

            // Notification permission (Android 13+)
            val notif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Auto-granted on older versions
            }

            _permissionStatus.value = PermissionStatus(
                healthConnect = health,
                gps = gps,
                wifi = wifi,
                usageStats = usage,
                notifications = notif
            )
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun checkPermissions() = updateAllPermissions()

    fun getHealthPermissions() = repository.getHealthPermissions()

    fun getOptionalHealthPermissions() = repository.getOptionalHealthPermissions()

    suspend fun getGrantedHealthPermissions() = repository.getGrantedHealthPermissions()

    // Test functions to verify Health Connect data access
    suspend fun testFetchSteps(start: java.time.Instant, end: java.time.Instant) =
        repository.fetchSteps(start, end)

    suspend fun testFetchSleep(start: java.time.Instant, end: java.time.Instant) =
        repository.fetchSleep(start, end)

    suspend fun testFetchHeartRate(start: java.time.Instant, end: java.time.Instant) =
        repository.fetchHeartRate(start, end)

    fun addSpending(amount: Double, category: String, note: String?, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insertSpending(amount, category, note, timestamp)
        }
    }

    suspend fun updateSpending(id: Long, amount: Double, category: String, note: String?, timestamp: Long) {
        repository.updateSpending(id, amount, category, note, timestamp)
    }

    suspend fun deleteSpending(id: Long) {
        repository.deleteSpending(id)
    }

    /**
     * Load today's summary (or yesterday's if today not processed yet)
     */
    private fun loadTodaySummary() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val yesterday = LocalDate.now().minusDays(1).toString()

            val summary = repository.getSummaryForDate(today)
                ?: repository.getSummaryForDate(yesterday)

            _todaySummary.value = summary
        }
    }

    /**
     * Load predictions from time-series engine
     */
    private fun loadPredictions() {
        viewModelScope.launch {
            try {
                val predictions = timeSeriesEngine.generatePredictions(LocalDate.now())
                _predictions.value = predictions
            } catch (e: Exception) {
                // Predictions might fail with insufficient data
                _predictions.value = null
            }
        }
    }

    /**
     * Calculate cumulative impact for "what-if" scenarios
     * Used by Prediction screen
     */
    suspend fun calculateCumulativeImpact(
        latePhoneActive: Boolean,
        morningGymActive: Boolean,
        days: Int
    ) = timeSeriesEngine.calculateCumulativeImpact(latePhoneActive, morningGymActive, days)

    /**
     * Refresh all data
     */
    fun refreshData() {
        loadTodaySummary()
        loadPredictions()
    }

    // ==================== Historical Data Backfill ====================

    /**
     * Backfill historical data for the last N days
     * Retrieves data from Health Connect and Usage Stats
     *
     * @param days Number of days to backfill (max 90)
     */
    fun backfillLastNDays(days: Int) {
        viewModelScope.launch {
            try {
                val result = backfillService.backfillLastNDays(days)
                // After backfill, refresh data and predictions
                loadTodaySummary()
                loadPredictions()
            } catch (e: Exception) {
                // Error is captured in backfill progress
            }
        }
    }

    /**
     * Backfill historical data for a specific date range
     *
     * @param startDate Earliest date (inclusive)
     * @param endDate Latest date (inclusive, should be yesterday or earlier)
     */
    fun backfillDateRange(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            try {
                val result = backfillService.backfillDateRange(startDate, endDate)
                // After backfill, refresh data and predictions
                loadTodaySummary()
                loadPredictions()
            } catch (e: Exception) {
                // Error is captured in backfill progress
            }
        }
    }

    /**
     * Reset backfill progress state
     */
    fun resetBackfillProgress() {
        backfillService.resetProgress()
    }
}
