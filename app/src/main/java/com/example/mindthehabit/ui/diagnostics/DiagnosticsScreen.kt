package com.example.mindthehabit.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.mindthehabit.data.backfill.BackfillProgress
import com.example.mindthehabit.ui.components.ScoreExplanationDialog
import com.example.mindthehabit.ui.components.ScoreType
import com.example.mindthehabit.ui.theme.*
import com.example.mindthehabit.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DiagnosticsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val dailySummaries by viewModel.dailySummaries.collectAsState(initial = emptyList())
    val todaySummary by viewModel.todaySummary.collectAsState()
    val backfillProgress by viewModel.backfillProgress.collectAsState()

    var exportMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CreamBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(48.dp)) }

            // Header
            item {
                Text(
                    text = "System Diagnostics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                exportMessage = exportDataToCSV(context, dailySummaries)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = YouthPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Data", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                exportMessage = exportDebugLogs(context, viewModel)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MacBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Logs", fontSize = 12.sp)
                    }
                }

                exportMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        fontSize = 11.sp,
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Historical Data Backfill
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("RECOVER HISTORICAL DATA", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Backfill past data from Health Connect and Usage Stats",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }

            item {
                HistoricalBackfillCard(
                    backfillProgress = backfillProgress,
                    onBackfill = { days -> viewModel.backfillLastNDays(days) },
                    onReset = { viewModel.resetBackfillProgress() }
                )
            }

            // Data Collection Status
            item {
                Text("DATA SOURCES", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }

            item {
                DataSourceStatusCard(
                    "Samsung Health",
                    permissionStatus.healthConnect,
                    if (permissionStatus.healthConnect) "Connected - reading sleep, HRV, steps, exercise" else "Not connected - tap Health card on home to enable"
                )
            }

            // Detailed Health Connect Permission Breakdown
            item {
                HealthConnectDetailCard(viewModel)
            }

            item {
                DataSourceStatusCard(
                    "GPS Location",
                    permissionStatus.gps,
                    if (permissionStatus.gps) "Active - tracking home/gym locations" else "Disabled - tap GPS card on home to enable"
                )
            }

            item {
                DataSourceStatusCard(
                    "WiFi Networks",
                    permissionStatus.wifi,
                    if (permissionStatus.wifi) "Active - detecting home networks" else "WiFi state access not available"
                )
            }

            item {
                DataSourceStatusCard(
                    "Usage Statistics",
                    permissionStatus.usageStats,
                    if (permissionStatus.usageStats) "Active - tracking late-night phone usage" else "Disabled - tap Usage card on home to enable"
                )
            }

            // Database Status
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("DATABASE STATUS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }

            item {
                DatabaseStatusCard(
                    totalDays = dailySummaries.size,
                    todayProcessed = todaySummary != null,
                    latestDate = dailySummaries.maxByOrNull { it.date }?.date
                )
            }

            // Today's Data Preview
            todaySummary?.let { summary ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TODAY'S PROCESSED DATA", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }

                item {
                    DataPreviewCard(summary)
                }
            }

            // System Logs
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("SYSTEM STATUS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }

            item {
                SystemLogsCard(permissionStatus, dailySummaries.size)
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun DataSourceStatusCard(name: String, isActive: Boolean, details: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) SuccessGreen.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isActive) SuccessGreen else ErrorRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun DatabaseStatusCard(totalDays: Int, todayProcessed: Boolean, latestDate: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Days", fontSize = 12.sp, color = TextSecondary)
                Text(totalDays.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Latest Entry", fontSize = 12.sp, color = TextSecondary)
                Text(latestDate ?: "None", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Today Processed", fontSize = 12.sp, color = TextSecondary)
                Text(
                    if (todayProcessed) "Yes" else "Pending",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (todayProcessed) SuccessGreen else YouthPurple
                )
            }
        }
    }
}

@Composable
fun DataPreviewCard(summary: com.example.mindthehabit.data.local.entity.DailySummaryEntity) {
    var showingDialog by remember { mutableStateOf<ScoreType?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = YouthPurple.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DataRowWithInfo("Late Night Phone", "${(summary.lateNightPhoneIndex * 100).toInt()}%", ScoreType.LATE_NIGHT_PHONE) { showingDialog = it }
            DataRowWithInfo("Morning Activity", "${summary.morningActivityScore.toInt()}/100", ScoreType.MORNING_ACTIVITY) { showingDialog = it }
            DataRowWithInfo("Sleep Score", "${summary.sleepScore.toInt()}/100", ScoreType.SLEEP_RECOVERY) { showingDialog = it }
            DataRowWithInfo("Recovery Score", "${summary.recoveryScore.toInt()}/100", ScoreType.HRV_RECOVERY) { showingDialog = it }
            DataRowWithInfo("Behavior Score", "${summary.behaviorScore.toInt()}/100", ScoreType.BEHAVIOR_SCORE) { showingDialog = it }
            DataRow("Total Spending", "$${String.format("%.2f", summary.totalSpending)}")
        }
    }

    // Show explanation dialog
    showingDialog?.let { scoreType ->
        ScoreExplanationDialog(
            scoreType = scoreType,
            onDismiss = { showingDialog = null }
        )
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun DataRowWithInfo(
    label: String,
    value: String,
    scoreType: ScoreType,
    onInfoClick: (ScoreType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
            IconButton(
                onClick = { onInfoClick(scoreType) },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info about $label",
                    tint = MacBlue,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SystemLogsCard(permissionStatus: com.example.mindthehabit.ui.viewmodel.PermissionStatus, daysCollected: Int) {
    val logs = buildList {
        if (!permissionStatus.healthConnect) {
            add(LogEntry("WARNING", "Samsung Health not connected - sleep/HRV data unavailable"))
        }
        if (!permissionStatus.gps) {
            add(LogEntry("WARNING", "GPS disabled - home/gym detection unavailable"))
        }
        if (!permissionStatus.usageStats) {
            add(LogEntry("WARNING", "Usage Stats disabled - late-night phone tracking unavailable"))
        }
        if (daysCollected == 0) {
            add(LogEntry("INFO", "No data collected yet - run app for 24h to see first results"))
        } else if (daysCollected < 7) {
            add(LogEntry("INFO", "Collecting baseline data ($daysCollected/7 days)"))
        } else {
            add(LogEntry("SUCCESS", "System fully operational - $daysCollected days collected"))
        }
        add(LogEntry("INFO", "Data processing runs daily at midnight via WorkManager"))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            logs.forEach { log ->
                LogEntryItem(log)
            }
        }
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    Row(verticalAlignment = Alignment.Top) {
        val (icon, color) = when (log.level) {
            "SUCCESS" -> Icons.Default.CheckCircle to SuccessGreen
            "WARNING" -> Icons.Default.Warning to YouthPink
            else -> Icons.Default.CheckCircle to MacBlue
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = log.message,
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 14.sp
        )
    }
}

data class LogEntry(val level: String, val message: String)

@Composable
fun HealthConnectDetailCard(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    var permissionDetails by remember { mutableStateOf<List<String>>(emptyList()) }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Health Connect Details",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = YouthPurple
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                val requiredPerms = viewModel.getHealthPermissions()
                                val optionalPerms = viewModel.getOptionalHealthPermissions()
                                val granted = try {
                                    viewModel.getGrantedHealthPermissions()
                                } catch (e: Exception) {
                                    emptySet()
                                }

                                val requiredGranted = requiredPerms.filter { it in granted }
                                val optionalGranted = optionalPerms.filter { it in granted }

                                permissionDetails = listOf(
                                    "REQUIRED Permissions: ${requiredPerms.size}",
                                    "  Granted: ${requiredGranted.size}/${requiredPerms.size}",
                                    "",
                                    "Required permissions:"
                                ) + requiredPerms.map {
                                    val status = if (it in granted) "✓" else "✗"
                                    "  $status ${it.toString().substringAfterLast(".")}"
                                } +
                                listOf("", "OPTIONAL Permissions: ${optionalPerms.size}",
                                    "  Granted: ${optionalGranted.size}/${optionalPerms.size}",
                                    "",
                                    "Optional permissions:"
                                ) + optionalPerms.map {
                                    val status = if (it in granted) "✓" else "○"
                                    "  $status ${it.toString().substringAfterLast(".")}"
                                } +
                                listOf("", "Overall Status:") +
                                if (granted.containsAll(requiredPerms)) {
                                    listOf("  ✓ All REQUIRED permissions granted!",
                                           "  (App will work perfectly)")
                                } else {
                                    val missing = requiredPerms - granted
                                    listOf("  ✗ Missing ${missing.size} REQUIRED permissions",
                                           "  (App needs these to function)")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MacBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check Permission Details", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val yesterday = java.time.LocalDate.now().minusDays(1)
                                    val start = yesterday.atTime(0, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
                                    val end = yesterday.plusDays(1).atTime(0, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()

                                    val steps = viewModel.testFetchSteps(start, end)
                                    val sleep = viewModel.testFetchSleep(start, end)
                                    val heartRate = viewModel.testFetchHeartRate(start, end)

                                    val diagnosis = if (steps.isEmpty() && sleep.isEmpty() && heartRate.isEmpty()) {
                                        listOf("  ✗ No data in Samsung Health OR", "  ✗ Permissions not actually granted")
                                    } else {
                                        listOf("  ✓ Health Connect is working!")
                                    }

                                    permissionDetails = listOf(
                                        "=== HEALTH DATA TEST ===",
                                        "Testing yesterday's data:",
                                        "",
                                        "Steps Records: ${steps.size}",
                                        if (steps.isNotEmpty()) "  Total steps: ${steps.sumOf { it.count }}" else "  No step data found",
                                        "",
                                        "Sleep Sessions: ${sleep.size}",
                                        if (sleep.isNotEmpty()) "  Total sleep: ${sleep.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }} min" else "  No sleep data found",
                                        "",
                                        "Heart Rate Records: ${heartRate.size}",
                                        if (heartRate.isNotEmpty()) "  Samples: ${heartRate.sumOf { it.samples.size }}" else "  No HR data found",
                                        "",
                                        "Diagnosis:"
                                    ) + diagnosis
                                } catch (e: Exception) {
                                    permissionDetails = listOf(
                                        "ERROR testing Health Connect:",
                                        e.message ?: "Unknown error",
                                        "",
                                        "This usually means permissions",
                                        "are not properly granted."
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Health Connect Data", fontSize = 12.sp)
                    }

                    if (permissionDetails.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SoftCream),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                permissionDetails.forEach { detail ->
                                    if (detail.isNotBlank()) {
                                        Text(
                                            detail,
                                            fontSize = 10.sp,
                                            color = TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalBackfillCard(
    backfillProgress: BackfillProgress,
    onBackfill: (Int) -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (backfillProgress) {
                is BackfillProgress.Idle -> {
                    Text(
                        "Select how many days to backfill:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    // Preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(7, 14, 30, 90).forEach { days ->
                            OutlinedButton(
                                onClick = { onBackfill(days) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = YouthPurple
                                )
                            ) {
                                Text("$days days", fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        "💡 Backfills sleep, exercise, HRV, and app usage data",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        lineHeight = 13.sp
                    )
                }

                is BackfillProgress.InProgress -> {
                    Text(
                        "Processing historical data...",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    LinearProgressIndicator(
                        progress = { backfillProgress.percentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = YouthPurple,
                        trackColor = SoftCream
                    )

                    Text(
                        "${backfillProgress.completed} / ${backfillProgress.total} days (${backfillProgress.percentage}%)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                is BackfillProgress.Completed -> {
                    val result = backfillProgress.result

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (result.success) SuccessGreen else YouthPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (result.success) "Backfill Complete!" else "Backfill Completed with Issues",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                result.getSummary(),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    result.errorMessage?.let { error ->
                        Text(
                            "⚠️ $error",
                            fontSize = 10.sp,
                            color = ErrorRed
                        )
                    }

                    Button(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MacBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Backfill More Data", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Export Functions
fun exportDataToCSV(context: Context, summaries: List<com.example.mindthehabit.data.local.entity.DailySummaryEntity>): String {
    if (summaries.isEmpty()) {
        return "No data to export"
    }

    val file = File(context.getExternalFilesDir(null), "habits_data_${System.currentTimeMillis()}.csv")

    file.bufferedWriter().use { writer ->
        // Header
        writer.write("Date,LateNightPhone,MorningActivity,SleepScore,RecoveryScore,BehaviorScore,Spending,HRV_Mean,HRV_StdDev,RestingHR\n")

        // Data rows
        summaries.forEach { summary ->
            writer.write("${summary.date},")
            writer.write("${summary.lateNightPhoneIndex},")
            writer.write("${summary.morningActivityScore},")
            writer.write("${summary.sleepScore},")
            writer.write("${summary.recoveryScore},")
            writer.write("${summary.behaviorScore},")
            writer.write("${summary.totalSpending},")
            writer.write("${summary.hrvMean ?: ""},")
            writer.write("${summary.hrvStdDev ?: ""},")
            writer.write("${summary.restingHR ?: ""}\n")
        }
    }

    // Share the file
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "hA/Bits Behavioral Data Export")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export Data"))

    return "✓ Exported ${summaries.size} days to ${file.name}"
}

fun exportDebugLogs(context: Context, viewModel: MainViewModel): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val file = File(context.getExternalFilesDir(null), "habits_debug_$timestamp.txt")

    file.bufferedWriter().use { writer ->
        writer.write("=== hA/Bits Debug Log ===\n")
        writer.write("Generated: $timestamp\n")
        writer.write("App Version: 1.0.0\n")
        writer.write("Android Version: ${android.os.Build.VERSION.SDK_INT}\n\n")

        writer.write("=== Permissions Status ===\n")
        writer.write("Health Connect: ${viewModel.permissionStatus.value.healthConnect}\n")
        writer.write("GPS: ${viewModel.permissionStatus.value.gps}\n")
        writer.write("WiFi: ${viewModel.permissionStatus.value.wifi}\n")
        writer.write("Usage Stats: ${viewModel.permissionStatus.value.usageStats}\n")
        writer.write("Notifications: ${viewModel.permissionStatus.value.notifications}\n\n")

        writer.write("=== System Logs ===\n")
        try {
            val process = Runtime.getRuntime().exec("logcat -d -t 200 *:W")
            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    if (line.contains("habits", ignoreCase = true) ||
                        line.contains("behavior", ignoreCase = true)) {
                        writer.write("$line\n")
                    }
                }
            }
        } catch (e: Exception) {
            writer.write("Could not read logcat: ${e.message}\n")
        }
    }

    // Share the file
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "hA/Bits Debug Logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export Debug Logs"))

    return "✓ Debug logs saved to ${file.name}"
}
