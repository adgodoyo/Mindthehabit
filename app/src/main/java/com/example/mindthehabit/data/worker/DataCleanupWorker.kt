package com.example.mindthehabit.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mindthehabit.data.repository.BehaviorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Data Cleanup Worker - Periodically removes old sensor data
 *
 * Purpose:
 * - Prevents database bloat from high-frequency sensor data
 * - Runs weekly to delete data older than 90 days
 * - Keeps daily summaries indefinitely (they're already aggregated)
 *
 * Cleaned Tables:
 * - light_readings (high frequency)
 * - wifi_events (high frequency)
 * - screen_events (high frequency)
 * - app_usage_events (moderate frequency)
 * - sleep_stages (daily)
 * - exercise_sessions (daily)
 * - gps_locations (high frequency)
 */
@HiltWorker
class DataCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: BehaviorRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DataCleanupWorker"
        const val WORK_NAME = "weekly_data_cleanup"
        const val DEFAULT_RETENTION_DAYS = 90
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting weekly data cleanup...")

            // Get retention days from input data (default to 90 days)
            val retentionDays = inputData.getInt("retention_days", DEFAULT_RETENTION_DAYS)

            Log.d(TAG, "Deleting sensor data older than $retentionDays days...")

            // Clean up old sensor data
            repository.cleanupOldSensorData(retentionDays)

            Log.d(TAG, "Data cleanup completed successfully!")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during data cleanup", e)

            // Retry up to 3 times
            if (runAttemptCount < 3) {
                Log.w(TAG, "Retrying cleanup (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Log.e(TAG, "Cleanup failed after 3 attempts, giving up")
                Result.failure()
            }
        }
    }
}
