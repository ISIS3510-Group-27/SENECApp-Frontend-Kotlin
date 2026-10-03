package com.senecapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senecapp.BuildConfig
import com.senecapp.data.FreeNowEvent
import com.senecapp.data.EventCoordinates
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun FreeNowSection(
    state: FreeNowUiState,
    onRefresh: () -> Unit,
    onDemoNoon: () -> Unit,
    onOpenEvent: (Int) -> Unit,
    onConsent: (Boolean) -> Unit,
    onRetryConsent: () -> Unit,
    onLocation: (EventCoordinates?, Boolean) -> Unit,
    onFallback: (String) -> Unit,
) {
    var selectedEvent by remember { mutableStateOf<FreeNowEvent?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ViewText("FREE RIGHT NOW", color = viewAccent)
        ViewText("Event suggestions based on your class schedule and current time", color = viewMuted)
        NearbyEventsControls(state, onConsent, onRetryConsent, onLocation, onFallback)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Refresh now", color = viewAccent, fontFamily = viewNunito,
                modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = 4.dp))
            if (BuildConfig.DEBUG) {
                Text("Demo: noon today", color = viewAccent, fontFamily = viewNunito,
                    modifier = Modifier.clickable(onClick = onDemoNoon).padding(vertical = 4.dp))
            }
        }
        if (state.demoMode) ViewText("Showing suggestions for noon today", color = viewMuted)

        if (state.loading) ViewText("Finding a free block...", color = viewMuted)
        state.error?.let { ViewText(it, color = viewForeground) }
        state.result?.let { result ->
                ViewText(when (result.locationSource) {
                    "gps" -> "Location source: GPS"
                    "schedule" -> "Location source: schedule building"
                    else -> "Location source: unavailable"
                }, color = viewAccent)
                if (result.freeMinutes != null) {
                    ViewText("Free for ${result.freeMinutes} min · until ${campusTime(result.freeEndsAt)}")
                }
                if (result.locationName != null) {
                    ViewText("Near ${result.locationName}", color = viewMuted)
                }
                if (!result.scheduleKnown) {
                    ViewText("Add a class schedule for more precise suggestions.", color = viewMuted)
                }
                if (result.events.isEmpty()) {
                    ViewText(result.message ?: "No events fit your free time right now.", color = viewMuted)
                }
                result.events.forEach { event ->
                    Box(Modifier.fillMaxWidth().clickable {
                        selectedEvent = event
                        onOpenEvent(event.id)
                    }) {
                        ViewCard {
                            ViewText(event.title, heading = true)
                            ViewText(event.groupName, color = viewAccent)
                            ViewText("${campusTime(event.startsAt)} · ${event.buildingName ?: "Campus"}", color = viewMuted)
                            event.walkingMinutes?.let { ViewText("About $it min walk", color = viewMuted) }
                            event.reasons.firstOrNull()?.let { ViewText(it, color = viewForeground) }
                        }
                    }
                }
            }
        state.openError?.let { ViewText(it, color = viewMuted) }
    }

    selectedEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { ViewText(event.title, heading = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewText(event.groupName, color = viewAccent)
                    ViewText("${campusTime(event.startsAt)} · ${event.buildingName ?: "Campus"}")
                    event.description?.let { ViewText(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEvent = null }) { Text("Close") }
            },
        )
    }
}

private fun campusTime(timestamp: String?): String {
    if (timestamp == null) return "later today"
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            isLenient = false
        }
        val normalized = timestamp.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:\\d{2})$)"), "")
        val date = requireNotNull(input.parse(normalized))
        SimpleDateFormat("EEE h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Bogota")
        }.format(date)
    }.getOrDefault(timestamp)
}
