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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
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
    var previewMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ViewCard {
            ViewText("Events for your free time", heading = true)
            if (state.demoMode) {
                Text("Demo · Today at noon", color = viewAccent, fontFamily = viewNunito,
                    fontSize = 12.sp, modifier = Modifier.background(viewSecondary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
            state.result?.let { result ->
                result.freeEndsAt?.let { ViewText("Free until ${campusTime(it)}") }
                result.locationName?.let { ViewText("Near $it", color = viewMuted) }
                ViewText(when (result.locationSource) {
                    "gps" -> "Using your location"
                    "schedule" -> "Using your schedule"
                    else -> "No location available"
                }, color = viewMuted)
                if (!result.scheduleKnown) {
                    ViewText("Add your class schedule for more precise suggestions.", color = viewMuted)
                }
            }
            if (state.loading) ViewText("Finding suggestions…", color = viewMuted)
            state.error?.let { ViewText(it, color = viewForeground) }
            NearbyEventsControls(state, onConsent, onRetryConsent, onLocation, onFallback)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRefresh) {
                    Text("Refresh suggestions", color = viewAccent, fontFamily = viewNunito)
                }
                if (BuildConfig.DEBUG) {
                    Box {
                        TextButton(onClick = { previewMenu = true },
                            modifier = Modifier.semantics { contentDescription = "Preview options" }) {
                            Text("•••", color = viewMuted)
                        }
                        DropdownMenu(expanded = previewMenu, onDismissRequest = { previewMenu = false }) {
                            DropdownMenuItem(text = { Text("Demo: noon today") }, onClick = {
                                previewMenu = false
                                onDemoNoon()
                            })
                            DropdownMenuItem(text = { Text("Use current time") }, onClick = {
                                previewMenu = false
                                onRefresh()
                            })
                        }
                    }
                }
            }
        }
        state.result?.let { result ->
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
