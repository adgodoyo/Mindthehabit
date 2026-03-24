package com.example.mindthehabit.ui.prediction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindthehabit.ui.theme.*
import com.example.mindthehabit.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun PredictionScreen(viewModel: MainViewModel) {
    var days by remember { mutableFloatStateOf(7f) }
    var latePhoneActive by remember { mutableStateOf(true) }
    var morningGymActive by remember { mutableStateOf(false) }
    var cumulativeImpact by remember { mutableStateOf<com.example.mindthehabit.data.modeling.CumulativeImpact?>(null) }

    // Calculate cumulative impact whenever parameters change
    LaunchedEffect(days, latePhoneActive, morningGymActive) {
        cumulativeImpact = viewModel.calculateCumulativeImpact(
            latePhoneActive,
            morningGymActive,
            days.toInt()
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CreamBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(48.dp)) }

            item {
                Text(
                    text = "Future Lens",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            // Multi-Selection Habit Query
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(24.dp)
                ) {
                    Column {
                        Text("SIMULATION PARAMETERS", color = YouthPurple, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Active habits in simulation:", color = TextSecondary, fontSize = 14.sp)
                        
                        Row(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HabitToggleChip(label = "Late Phone", isSelected = latePhoneActive) { latePhoneActive = !latePhoneActive }
                            HabitToggleChip(label = "Morning Gym", isSelected = morningGymActive) { morningGymActive = !morningGymActive }
                        }
                        
                        Text("Projection period: ${days.toInt()} days", color = TextSecondary, fontSize = 14.sp)
                        
                        Slider(
                            value = days,
                            onValueChange = { days = it },
                            valueRange = 1f..90f,
                            colors = SliderDefaults.colors(thumbColor = YouthPurple, activeTrackColor = YouthPurple)
                        )
                    }
                }
            }

            // Combined Impact Result
            item {
                val totalCost = cumulativeImpact?.totalSpendingImpact ?: 0.0
                val sleepImpact = cumulativeImpact?.totalSleepImpact ?: 0.0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(YouthPurple, YouthPink)))
                        .padding(28.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("PROJECTED OUTCOME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val costValue = if (totalCost >= 0) {
                            "$" + String.format(Locale.US, "%.2f", totalCost)
                        } else {
                            "-$" + String.format(Locale.US, "%.2f", -totalCost)
                        }

                        val costSub = if (totalCost >= 0) "extra spending" else "potential savings"

                        PredictionMetric(
                            label = "Financial Impact",
                            value = costValue,
                            sub = costSub
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val sleepValue = (if (sleepImpact >= 0) "+" else "") + sleepImpact.toInt().toString() + " pts"

                        PredictionMetric(
                            label = "Sleep Quality",
                            value = sleepValue,
                            sub = "cumulative sleep score impact"
                        )

                        // Show model info if available
                        cumulativeImpact?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Based on your behavior patterns",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun HabitToggleChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .border(1.dp, if (isSelected) YouthPurple else BorderColor, CircleShape),
        color = if (isSelected) YouthPurple else Color.White,
        contentColor = if (isSelected) Color.White else TextPrimary
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PredictionMetric(label: String, value: String, sub: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}
