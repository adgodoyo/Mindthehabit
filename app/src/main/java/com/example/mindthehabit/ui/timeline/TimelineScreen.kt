package com.example.mindthehabit.ui.timeline

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindthehabit.ui.theme.*
import com.example.mindthehabit.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TimelineScreen(viewModel: MainViewModel, onEditExpense: (com.example.mindthehabit.data.local.entity.SpendingEntryEntity) -> Unit) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dailySummaries by viewModel.dailySummaries.collectAsState(initial = emptyList())
    val allSpending by viewModel.recentSpending.collectAsState(initial = emptyList())
    val todaySummary by viewModel.todaySummary.collectAsState()
    
    val calendarListState = rememberLazyListState()
    var containerWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Function to center selected day
    suspend fun centerSelectedDay(day: Int, listState: LazyListState, width: Int) {
        if (width <= 0) return
        val index = day - 1
        val itemWidthPx = with(density) { 45.dp.toPx() }
        val scrollOffset = (width / 2f) - (itemWidthPx / 2f)
        listState.animateScrollToItem(index, -scrollOffset.toInt())
    }

    // Reset to Today and center
    LaunchedEffect(Unit) {
        selectedDate = LocalDate.now()
        currentMonth = YearMonth.now()
        while(containerWidth == 0) delay(10)
        centerSelectedDay(selectedDate.dayOfMonth, calendarListState, containerWidth)
    }

    // Re-center when selection changes
    LaunchedEffect(selectedDate, currentMonth, containerWidth) {
        if (selectedDate.monthValue == currentMonth.monthValue && selectedDate.year == currentMonth.year) {
            centerSelectedDay(selectedDate.dayOfMonth, calendarListState, containerWidth)
        }
    }

    // Filter summaries for current month
    val monthSummaries = remember(dailySummaries, currentMonth) {
        dailySummaries.filter { summary ->
            val date = try { LocalDate.parse(summary.date) } catch(e: Exception) { null }
            date?.year == currentMonth.year && date?.monthValue == currentMonth.monthValue
        }
    }

    // Resolve which summary to show: Real past summary OR Today's live pulse
    val displaySummary = remember(selectedDate, dailySummaries, todaySummary) {
        if (selectedDate == LocalDate.now()) {
            todaySummary
        } else {
            dailySummaries.find { it.date == selectedDate.toString() }
        }
    }

    // Get spending for selected date
    val selectedDaySpending = remember(allSpending, selectedDate) {
        val startOfDay = selectedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = selectedDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        allSpending.filter { it.timestamp in startOfDay until endOfDay }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CreamBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Historical Lens",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // Month/Year Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = YouthPurple)
                }
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()} ${currentMonth.year}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { currentMonth = currentMonth.plusMonths(1) },
                    enabled = currentMonth < YearMonth.now()
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month",
                        tint = if (currentMonth < YearMonth.now()) YouthPurple else Color.LightGray)
                }
            }

            // Calendar Bar
            LazyRow(
                state = calendarListState,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    .onGloballyPositioned { containerWidth = it.size.width },
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val daysInMonth = currentMonth.lengthOfMonth()
                itemsIndexed((1..daysInMonth).toList()) { _, day ->
                    val date = currentMonth.atDay(day)
                    val isToday = date == LocalDate.now()
                    val hasSummary = monthSummaries.any { it.date == date.toString() } || isToday
                    val isSelected = date == selectedDate

                    CalendarDayItem(day = day, isSelected = isSelected, isHabitDone = hasSummary, onClick = { selectedDate = date })
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedDaySpending.isNotEmpty()) {
                    item {
                        SpendingSection(
                            title = "TRANSACTIONS (${selectedDaySpending.size})",
                            transactions = selectedDaySpending,
                            onEditTransaction = onEditExpense
                        )
                    }
                }

                if (displaySummary != null) {
                    item {
                        DetailSection(
                            title = if (selectedDate == LocalDate.now()) "REAL-TIME METRICS" else "BEHAVIORAL METRICS",
                            color = YouthPurple,
                            events = listOf(
                                TimelineEvent("Late Night Phone", "${(displaySummary.lateNightPhoneIndex * 100).toInt()}%", "Score"),
                                TimelineEvent("Morning Activity", "${displaySummary.morningActivityScore.toInt()}/100", "Score"),
                                TimelineEvent("Sleep Quality", "${displaySummary.sleepScore.toInt()}/100", "Score"),
                                TimelineEvent("Recovery", "${displaySummary.recoveryScore.toInt()}/100", "Score")
                            )
                        )
                    }

                    item {
                        DetailSection(
                            title = "OUTCOMES",
                            color = SuccessGreen,
                            events = buildList {
                                val totalSpent = if (selectedDate == LocalDate.now()) selectedDaySpending.sumOf { it.amount } else displaySummary.totalSpending
                                add(TimelineEvent("Spending", "$${String.format("%.2f", totalSpent)}", "Total"))
                                add(TimelineEvent("Behavior Score", "${displaySummary.behaviorScore.toInt()}/100", "Overall"))
                                displaySummary.energyLevelScore?.let { score ->
                                    val levelText = displaySummary.energyLevel ?: "Fair"
                                    add(TimelineEvent("Energy Level", "$score/100 ($levelText)", "Status"))
                                }
                            }
                        )
                    }
                } else if (selectedDaySpending.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (selectedDate.isAfter(LocalDate.now())) "Future date" else "No data for this date",
                                    color = TextSecondary, fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (selectedDate.isBefore(LocalDate.now())) "Historical summary is calculated at midnight" else "Add habits to see your score!",
                                    color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (monthSummaries.isNotEmpty() && monthSummaries.size >= 3) {
                    item { AverageStatsCard(monthSummaries) }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun CalendarDayItem(day: Int, isSelected: Boolean, isHabitDone: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(45.dp).clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) YouthPurple else Color.White)
            .clickable { onClick() }.padding(vertical = 12.dp)
    ) {
        Text(text = day.toString(), color = if (isSelected) Color.White else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape)
            .background(if (isHabitDone) SuccessGreen else if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent))
    }
}

@Composable
fun DetailSection(title: String, color: Color, events: List<TimelineEvent>) {
    var expanded by remember { mutableStateOf(true) }
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = color, style = MaterialTheme.typography.labelMedium)
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = color)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                events.forEach { event ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(event.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row {
                            Text(event.value, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(event.time, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AverageStatsCard(summaries: List<com.example.mindthehabit.data.local.entity.DailySummaryEntity>) {
    val avgSpending = summaries.map { it.totalSpending }.average()
    val avgSleep = summaries.map { it.sleepScore.toDouble() }.average()
    val avgBehavior = summaries.map { it.behaviorScore.toDouble() }.average()
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(YouthPink.copy(alpha = 0.1f))
        .border(1.dp, YouthPink.copy(alpha = 0.2f), RoundedCornerShape(24.dp)).padding(20.dp)) {
        Column {
            Text("MONTH AVERAGES (${summaries.size} days)", color = YouthPink, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Avg Spend", "$${String.format("%.2f", avgSpending)}")
                StatItem("Avg Sleep", "${avgSleep.toInt()}/100")
                StatItem("Avg Behavior", "${avgBehavior.toInt()}/100")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

data class TimelineEvent(val label: String, val value: String, val time: String)

@Composable
fun SpendingSection(title: String, transactions: List<com.example.mindthehabit.data.local.entity.SpendingEntryEntity>,
                    onEditTransaction: (com.example.mindthehabit.data.local.entity.SpendingEntryEntity) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = YouthPink, style = MaterialTheme.typography.labelMedium)
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = YouthPink)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                transactions.forEach { TransactionItem(it, onEditTransaction) }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: com.example.mindthehabit.data.local.entity.SpendingEntryEntity,
                    onEdit: (com.example.mindthehabit.data.local.entity.SpendingEntryEntity) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onEdit(transaction) }
        .background(CreamBackground).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.category, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            transaction.note?.let { if (it.isNotBlank()) Text(text = it, fontSize = 11.sp, color = TextSecondary) }
            Text(text = java.time.Instant.ofEpochMilli(transaction.timestamp).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.7f))
        }
        Text(text = "$${String.format("%.2f", transaction.amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = YouthPurple)
    }
}
