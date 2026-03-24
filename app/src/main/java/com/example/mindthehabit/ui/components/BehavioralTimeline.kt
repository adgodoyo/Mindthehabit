package com.example.mindthehabit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BehavioralTimeline(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "24H CHRONO-LENS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Vertical Timeline logic
        val events = listOf(
            TimelineEvent("00:00", "Deep Sleep", Color(0xFF3F51B5), 0.8f),
            TimelineEvent("02:30", "Late Screen Use", Color(0xFFFF5252), 0.2f),
            TimelineEvent("07:00", "Cortisol Spike", Color(0xFFFFAB40), 0.1f),
            TimelineEvent("08:30", "Morning Exercise", Color(0xFF00E676), 0.4f),
            TimelineEvent("14:00", "Impulsive Spending", Color(0xFFFF5252), 0.1f)
        )

        events.forEach { event ->
            TimelineRow(event)
        }
    }
}

data class TimelineEvent(val time: String, val label: String, val color: Color, val intensity: Float)

@Composable
fun TimelineRow(event: TimelineEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.time,
            color = Color.DarkGray,
            fontSize = 12.sp,
            modifier = Modifier.width(50.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.DarkGray)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(event.color)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .clip(CircleShape)
                .background(event.color.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = event.label,
                color = event.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
