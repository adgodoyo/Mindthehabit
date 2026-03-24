package com.example.mindthehabit.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mindthehabit.ui.components.BehavioralTimeline
import com.example.mindthehabit.ui.theme.*
import com.example.mindthehabit.ui.viewmodel.MainViewModel
import java.time.LocalTime

@Composable
fun DashboardScreen(viewModel: MainViewModel, onAddExpense: () -> Unit) {
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val todaySummary by viewModel.todaySummary.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val allSpending by viewModel.recentSpending.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val greeting = remember {
        val hour = LocalTime.now().hour
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Refresh permissions when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { viewModel.updateAllPermissions() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> viewModel.updateAllPermissions() }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Personalized Greeting - Reduced top padding
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        "$greeting!",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Here's your behavioral pulse for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Today's Pulse
            item {
                val behaviorScore = todaySummary?.behaviorScore ?: 0f
                val benefits = buildBenefitsText(todaySummary, predictions)
                val habitsSummary = buildHabitsSummary(todaySummary)
                val outcomeLabel = buildOutcomeLabel(behaviorScore)

                PulseCard(
                    benefits = benefits,
                    habitsSummary = habitsSummary,
                    outcomeLabel = outcomeLabel,
                    progress = behaviorScore / 100f
                )
            }

            // Compact Data Source Status
            item {
                Text("DATA SOURCES", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MicroSourceCard(
                        name = "Health",
                        isActive = permissionStatus.healthConnect,
                        onEnable = { healthLauncher.launch(viewModel.getHealthPermissions()) },
                        modifier = Modifier.weight(1f)
                    )
                    MicroSourceCard(
                        name = "GPS",
                        isActive = permissionStatus.gps,
                        onEnable = {
                            locationLauncher.launch(arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MicroSourceCard(
                        name = "WiFi",
                        isActive = permissionStatus.wifi,
                        onEnable = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MicroSourceCard(
                        name = "Usage",
                        isActive = permissionStatus.usageStats,
                        onEnable = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Behavioral Timeline
            item {
                BehavioralTimeline()
            }

            // Today's Spending
            item {
                val today = java.time.LocalDate.now()
                val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todaySpending = allSpending.filter { it.timestamp in startOfDay until endOfDay }

                if (todaySpending.isNotEmpty()) {
                    Text("TODAY'S SPENDING", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    TodaySpendingCard(todaySpending)
                }
            }

            // AI Recommendations
            item {
                Text("INSIGHTS & RECOMMENDATIONS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                RecommendationsCard(todaySummary, predictions)
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = onAddExpense,
            containerColor = YouthPurple,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Expense")
        }
    }
}

@Composable
fun MicroSourceCard(name: String, isActive: Boolean, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) SuccessGreen.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.05f))
            .border(1.dp, if (isActive) SuccessGreen.copy(alpha = 0.3f) else BorderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !isActive) { onEnable() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SuccessGreen else ErrorRed)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun RecommendationsCard(
    summary: com.example.mindthehabit.data.local.entity.DailySummaryEntity?,
    predictions: com.example.mindthehabit.data.modeling.ModelPredictions?
) {
    val recommendations = buildList {
        if (summary == null) {
            add("📱 Late-night phone usage affects your sleep quality and next-day spending")
            add("🏃 Morning exercise improves recovery scores by up to 30%")
            add("😴 Consistent sleep reduces impulsive spending by 25%")
            add("📊 Start logging habits to unlock personalized AI insights")
        } else {
            if (summary.lateNightPhoneIndex > 0.5f) {
                add("📱 Reduce late-night screen time → could save $${(summary.lateNightPhoneIndex * 20).toInt()} tomorrow")
            }
            if (summary.morningActivityScore < 40f && predictions != null) {
                add("🏃 Morning workout could improve sleep score by ${(100 - summary.sleepScore).toInt() / 4} points")
            }
            if (summary.sleepScore < 60f) {
                add("😴 Earlier bedtime could boost behavior score by ${(100 - summary.behaviorScore).toInt() / 3} points")
            }
            if (summary.totalSpending > 50.0 && summary.lateNightPhoneIndex > 0.4f) {
                add("💰 Late-night scrolling correlates with higher spending")
            }
            if (summary.behaviorScore > 70f) {
                add("⭐ Great habits today! Keep it up")
            }
            if (isEmpty()) {
                add("📊 Keep tracking to see personalized insights")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MacBlue.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            recommendations.forEach { recommendation ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MacBlue).padding(top = 6.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = recommendation, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun PulseCard(benefits: String, habitsSummary: String, outcomeLabel: String, progress: Float) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
        .background(Color.White).border(2.dp, YouthPurple.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
        .padding(24.dp)
    ) {
        Column {
            Text(benefits, color = SuccessGreen, style = MaterialTheme.typography.headlineSmall)
            Text(habitsSummary, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                color = YouthPurple,
                trackColor = SoftCream
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(outcomeLabel, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun buildBenefitsText(
    summary: com.example.mindthehabit.data.local.entity.DailySummaryEntity?,
    predictions: com.example.mindthehabit.data.modeling.ModelPredictions?
): String {
    if (summary == null) return "Collecting data..."
    val spendingImpact = predictions?.let {
        val saved = summary.totalSpending - it.nextDaySpending
        if (saved > 0) "+$${"%.2f".format(saved)} Saved" else "$${"%.2f".format(-saved)} Extra"
    } ?: "Score: ${summary.behaviorScore.toInt()}/100"
    return spendingImpact
}

private fun buildHabitsSummary(summary: com.example.mindthehabit.data.local.entity.DailySummaryEntity?): String {
    if (summary == null) return "Start tracking your habits"
    val habits = mutableListOf<String>()
    if (summary.lateNightPhoneIndex < 0.3f) habits.add("Good Sleep")
    if (summary.morningActivityScore > 50f) habits.add("Morning Exercise")
    if (summary.sleepScore > 70f) habits.add("Quality Rest")
    return if (habits.isNotEmpty()) habits.joinToString(" • ") else "Building habits..."
}

private fun buildOutcomeLabel(score: Float): String {
    return when {
        score >= 80f -> "Behavior Score: Excellent (${score.toInt()}/100)"
        score >= 60f -> "Behavior Score: Good (${score.toInt()}/100)"
        score >= 40f -> "Behavior Score: Fair (${score.toInt()}/100)"
        score > 0f -> "Behavior Score: Needs Work (${score.toInt()}/100)"
        else -> "Behavior Score: Collecting Data"
    }
}

@Composable
fun TodaySpendingCard(transactions: List<com.example.mindthehabit.data.local.entity.SpendingEntryEntity>) {
    val totalSpent = transactions.sumOf { it.amount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Today", color = TextSecondary, fontSize = 12.sp)
                    Text("$${String.format("%.2f", totalSpent)}", color = YouthPurple, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text("${transactions.size} transactions", color = TextSecondary, fontSize = 12.sp)
            }
            HorizontalDivider(color = BorderColor)
            transactions.take(3).forEach { transaction ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(transaction.category, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        transaction.note?.let { note -> if (note.isNotBlank()) Text(note, fontSize = 11.sp, color = TextSecondary, maxLines = 1) }
                    }
                    Text("$${String.format("%.2f", transaction.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                }
            }
        }
    }
}
