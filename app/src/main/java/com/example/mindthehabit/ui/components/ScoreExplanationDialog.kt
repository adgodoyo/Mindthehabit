package com.example.mindthehabit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mindthehabit.ui.theme.*

/**
 * Score types that can be explained
 */
enum class ScoreType {
    LATE_NIGHT_PHONE,
    MORNING_ACTIVITY,
    SLEEP_RECOVERY,
    HRV_RECOVERY,
    BEHAVIOR_SCORE,
    SPENDING
}

@Composable
fun ScoreExplanationDialog(
    scoreType: ScoreType,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getScoreTitle(scoreType),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    getScoreExplanation(scoreType)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = YouthPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Got it", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun getScoreTitle(scoreType: ScoreType): String {
    return when (scoreType) {
        ScoreType.LATE_NIGHT_PHONE -> "Late-Night Phone Index"
        ScoreType.MORNING_ACTIVITY -> "Morning Activity Score"
        ScoreType.SLEEP_RECOVERY -> "Sleep Recovery Score"
        ScoreType.HRV_RECOVERY -> "HRV Recovery Score"
        ScoreType.BEHAVIOR_SCORE -> "Overall Behavior Score"
        ScoreType.SPENDING -> "Spending Predictions"
    }
}

@Composable
private fun getScoreExplanation(scoreType: ScoreType) {
    when (scoreType) {
        ScoreType.LATE_NIGHT_PHONE -> LateNightPhoneExplanation()
        ScoreType.MORNING_ACTIVITY -> MorningActivityExplanation()
        ScoreType.SLEEP_RECOVERY -> SleepRecoveryExplanation()
        ScoreType.HRV_RECOVERY -> HRVRecoveryExplanation()
        ScoreType.BEHAVIOR_SCORE -> BehaviorScoreExplanation()
        ScoreType.SPENDING -> SpendingExplanation()
    }
}

@Composable
private fun LateNightPhoneExplanation() {
    ExplanationSection(
        title = "What it measures",
        content = "Screen time and phone usage between 11 PM and 4 AM"
    )

    ExplanationSection(
        title = "Scale",
        content = "0.0 to 1.0 (lower is better)\n• 0.0 = No late-night usage\n• 0.5 = Moderate usage (90 mins)\n• 1.0 = Heavy usage (3+ hours)"
    )

    ExplanationSection(
        title = "How it's calculated",
        content = """
            Weighted combination of:
            • Screen time duration (60%)
            • Number of unlocks (30%)
            • Session fragmentation (10%)

            In-bed scrolling is weighted higher than social events.
        """.trimIndent()
    )

    ExplanationSection(
        title = "Why it matters",
        content = "Late-night phone use disrupts sleep quality and is strongly correlated with next-day impulsive spending and lower energy levels."
    )

    ExplanationSection(
        title = "Data sources",
        content = "• Usage Statistics (Android)\n• Screen events\n• WiFi/GPS context (home vs social)"
    )
}

@Composable
private fun MorningActivityExplanation() {
    ExplanationSection(
        title = "What it measures",
        content = "Physical activity between 6 AM and 10 AM"
    )

    ExplanationSection(
        title = "Scale",
        content = "0 to 100 (higher is better)\n• 0 = No activity\n• 40 = Light movement\n• 60 = 15 min exercise\n• 100 = 30+ min exercise + 2000 steps"
    )

    ExplanationSection(
        title = "How it's calculated",
        content = """
            Two components:
            • Exercise time (60 points max)
              - 30+ minutes = 60 points
            • Morning steps (40 points max)
              - 2000+ steps = 40 points
        """.trimIndent()
    )

    ExplanationSection(
        title = "Why it matters",
        content = "Morning exercise improves recovery scores by up to 30% and reduces next-day impulsive spending by establishing early momentum."
    )

    ExplanationSection(
        title = "Data sources",
        content = "• Samsung Health (Exercise sessions)\n• Samsung Health (Step count)"
    )
}

@Composable
private fun SleepRecoveryExplanation() {
    ExplanationSection(
        title = "What it measures",
        content = "Sleep duration and quality from your primary sleep session"
    )

    ExplanationSection(
        title = "Scale",
        content = "0 to 100 (higher is better)\n• 0-40 = Poor sleep\n• 40-60 = Fair sleep\n• 60-80 = Good sleep\n• 80-100 = Excellent sleep"
    )

    ExplanationSection(
        title = "How it's calculated",
        content = """
            Two components:
            • Duration (60 points max)
              - 8 hours = 60 points
            • Quality (40 points max)
              - 15-20% deep sleep = 40 points
              - Less deep sleep = proportional
        """.trimIndent()
    )

    ExplanationSection(
        title = "Sleep stages tracked",
        content = "• Deep sleep (15-20% ideal)\n• REM sleep\n• Light sleep\n• Awake time"
    )

    ExplanationSection(
        title = "Why it matters",
        content = "Sleep quality affects cognitive function, emotional regulation, and next-day spending behavior. Consistent sleep schedules reduce impulsive decisions by 25%."
    )

    ExplanationSection(
        title = "Data sources",
        content = "• Samsung Health (Sleep sessions)\n• Samsung Health (Sleep stages)"
    )
}

@Composable
private fun HRVRecoveryExplanation() {
    ExplanationSection(
        title = "What it measures",
        content = "Heart Rate Variability (HRV) and resting heart rate as indicators of physiological recovery"
    )

    ExplanationSection(
        title = "Scale",
        content = "0 to 100 (higher is better)\n• 0-40 = Poor recovery\n• 40-60 = Fair recovery\n• 60-80 = Good recovery\n• 80-100 = Excellent recovery"
    )

    ExplanationSection(
        title = "How it's calculated",
        content = """
            Two components:
            • HRV (RMSSD) (60 points max)
              - 20-100 ms range
              - Higher = better recovery
            • Resting heart rate (40 points max)
              - 50 BPM = excellent (40 pts)
              - 80 BPM = poor (0 pts)
        """.trimIndent()
    )

    ExplanationSection(
        title = "Why it matters",
        content = "HRV is the gold standard for measuring physiological stress and recovery. Low HRV indicates stress, poor sleep, or overtraining."
    )

    ExplanationSection(
        title = "Data sources",
        content = "• Samsung Health (Heart rate)\n• Calculated RMSSD from R-R intervals"
    )

    ExplanationSection(
        title = "Note",
        content = "If HRV data is unavailable, behavior score uses adjusted weighting without recovery component."
    )
}

@Composable
private fun BehaviorScoreExplanation() {
    ExplanationSection(
        title = "What it measures",
        content = "Composite score combining all behavioral metrics into a single daily health index"
    )

    ExplanationSection(
        title = "Scale",
        content = "0 to 100 (higher is better)\n• 0-40 = Needs improvement\n• 40-60 = Fair habits\n• 60-80 = Good habits\n• 80-100 = Excellent habits"
    )

    ExplanationSection(
        title = "With HRV data available",
        content = """
            Weighted formula:
            • Late-night phone (30%, inverted)
            • Morning activity (25%)
            • Sleep quality (25%)
            • HRV recovery (20%)
        """.trimIndent()
    )

    ExplanationSection(
        title = "Without HRV data",
        content = """
            Adjusted weights:
            • Late-night phone (35%, inverted)
            • Morning activity (35%)
            • Sleep quality (30%)
        """.trimIndent()
    )

    ExplanationSection(
        title = "Why it matters",
        content = "The behavior score predicts next-day outcomes including spending behavior, energy levels, and decision-making quality."
    )
}

@Composable
private fun SpendingExplanation() {
    ExplanationSection(
        title = "What it predicts",
        content = "Next-day spending based on yesterday's behavioral patterns"
    )

    ExplanationSection(
        title = "Key correlations found",
        content = """
            Research shows:
            • Late-night phone use → 20-40% higher spending
            • Poor sleep → 25% more impulsive purchases
            • Morning exercise → 15-30% reduced spending
        """.trimIndent()
    )

    ExplanationSection(
        title = "How predictions work",
        content = """
            Uses adaptive time-series modeling:
            • <7 days: No predictions
            • 7-13 days: Simple correlation
            • 14-20 days: Rolling regression
            • 21+ days: Change-point detection
        """.trimIndent()
    )

    ExplanationSection(
        title = "Confidence levels",
        content = "Predictions include confidence rating:\n• LOW: Limited data (<14 days)\n• MEDIUM: Good data (14-20 days)\n• HIGH: Excellent data (21+ days)"
    )
}

@Composable
private fun ExplanationSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = YouthPurple
        )
        Text(
            text = content,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )
    }
}
