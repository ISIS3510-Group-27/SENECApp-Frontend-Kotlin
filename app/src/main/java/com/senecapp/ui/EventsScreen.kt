package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.senecapp.R

private data class SampleEvent(val title: String, val organization: String, val date: String,
    val location: String, val joined: Boolean)

// Static fixtures for the UI demo; no backend or registration is involved.
private val sampleEvents = listOf(
    SampleEvent("Round Robin Tournament", "Tennis Uniandes", "Oct 5, 2026 · 8:00 AM", "Campus tennis courts", true),
    SampleEvent("LLM Agent Building", "AI & Machine Learning", "Oct 7, 2026 · 5:00 PM", "ML building · Room 204", true),
    SampleEvent("Pitch Night", "Emprendedores Uniandes", "Oct 8, 2026 · 6:00 PM", "Innovation center", false),
)

@Composable
fun EventsScreen() {
    var onlyJoined by rememberSaveable { mutableStateOf(false) }
    DemoView("SENECApp Events") {
        Column(Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(viewPrimary, Color(0xFFFF6B35))), RoundedCornerShape(20.dp)
        ).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ViewText("THIS WEEK · SAMPLE SCHEDULE", color = viewAccent)
            ViewText("${sampleEvents.size} events from your\ncampus organizations", heading = true, color = Color.White)
            ViewText("Oct 5 – Oct 11, 2026", color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "All Events", true to "My RSOs").forEach { (joined, label) ->
                FilterChip(selected = onlyJoined == joined, onClick = { onlyJoined = joined },
                    label = { Text(label, fontFamily = viewNunito) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = viewSecondary,
                        labelColor = viewForeground, selectedContainerColor = viewAccent,
                        selectedLabelColor = viewBackground))
            }
        }
        val visibleEvents = sampleEvents.filter { !onlyJoined || it.joined }
        ViewText("${visibleEvents.size} EVENTS", color = viewMuted)
        visibleEvents.forEach { event ->
            ViewCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_event), contentDescription = null,
                        tint = viewAccent, modifier = Modifier.size(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ViewText(event.title, heading = true)
                        ViewText(event.organization, color = viewAccent)
                        ViewText(event.date, color = viewMuted)
                        ViewText(event.location, color = viewMuted)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EventsPreview() { EventsScreen() }
