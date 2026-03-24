package com.example.mindthehabit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mindthehabit.data.worker.DataCleanupWorker
import com.example.mindthehabit.data.worker.DataProcessingWorker
import com.example.mindthehabit.service.SensorForegroundService
import com.example.mindthehabit.ui.MainScreen
import com.example.mindthehabit.ui.theme.MIndTheHabitTheme
import com.example.mindthehabit.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start sensor service
        startForegroundService(Intent(this, SensorForegroundService::class.java))

        // Schedule daily data processing
        scheduleDailyProcessing()

        // Schedule weekly data cleanup
        scheduleWeeklyCleanup()

        setContent {
            MIndTheHabitTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    /**
     * Schedule daily data processing worker
     * Runs once per day to aggregate sensor data and calculate behavioral metrics
     */
    private fun scheduleDailyProcessing() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Can run offline
            .setRequiresBatteryNotLow(true) // Only run when battery is not low
            .build()

        val dailyWorkRequest = PeriodicWorkRequestBuilder<DataProcessingWorker>(
            24, TimeUnit.HOURS, // Repeat every 24 hours
            15, TimeUnit.MINUTES // Flex interval (can run within 15-min window)
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataProcessingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            dailyWorkRequest
        )
    }

    /**
     * Schedule weekly data cleanup worker
     * Runs once per week to delete old sensor data and prevent database bloat
     */
    private fun scheduleWeeklyCleanup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Can run offline
            .setRequiresBatteryNotLow(true) // Only run when battery is not low
            .build()

        val weeklyWorkRequest = PeriodicWorkRequestBuilder<DataCleanupWorker>(
            7, TimeUnit.DAYS, // Repeat every 7 days
            1, TimeUnit.HOURS // Flex interval (can run within 1-hour window)
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            weeklyWorkRequest
        )
    }
}
