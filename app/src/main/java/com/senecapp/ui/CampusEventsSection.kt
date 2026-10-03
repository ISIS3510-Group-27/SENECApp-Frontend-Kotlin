package com.senecapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senecapp.data.CampusEvent
import com.senecapp.data.EventCoordinates
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun CampusEventsSection(state: CampusEventsUiState, onLoad: (Boolean) -> Unit,
                                 onOpen: (CampusEvent) -> Unit, onClose: () -> Unit,
                                 checkInState: CheckInUiState = CheckInUiState(),
                                 onStartScan: (Int) -> Unit = {}, onScanFailed: (Int, String) -> Unit = { _, _ -> },
                                 onScanCancelled: (Int) -> Unit = {},
                                 onCheckIn: (Int, String, EventCoordinates?, String?) -> Unit = { _, _, _, _ -> }) {
    var mine by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mine) { onLoad(mine) }
    ViewText("UPCOMING EVENTS", color = viewMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "All Events", true to "My RSOs").forEach { (filter, label) ->
            FilterChip(selected = mine == filter, onClick = { mine = filter },
                label = { Text(label, fontFamily = viewNunito) },
                colors = FilterChipDefaults.filterChipColors(containerColor = viewSecondary,
                    labelColor = viewForeground, selectedContainerColor = viewAccent,
                    selectedLabelColor = viewBackground))
        }
    }
    when {
        state.loading -> ViewText("Loading events…", color = viewMuted)
        state.error != null -> {
            ViewText(state.error)
            TextButton(onClick = { onLoad(mine) }) { Text("Retry", color = viewAccent) }
        }
        state.items.isEmpty() -> ViewText(if (mine) "No upcoming events from your RSOs." else "No upcoming events.", color = viewMuted)
        else -> {
            ViewText(if (state.total > state.items.size) "Showing ${state.items.size} of ${state.total} events" else "${state.total} EVENTS", color = viewMuted)
            state.items.forEach { event ->
                Box(Modifier.fillMaxWidth().clickable { onOpen(event) }) {
                    ViewCard {
                        ViewText(event.title, heading = true)
                        ViewText(event.group, color = viewAccent)
                        ViewText("${eventTime(event.startsAt)} · ${event.building ?: "Campus"}", color = viewMuted)
                        if (event.cancelled) ViewText("Cancelled", color = viewAccent)
                    }
                }
            }
        }
    }
    state.selected?.let { event ->
        AlertDialog(onDismissRequest = onClose, containerColor = viewBackground,
            titleContentColor = viewForeground, textContentColor = viewForeground,
            title = { ViewText(event.title, heading = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewText(event.group, color = viewAccent)
                    ViewText("${eventTime(event.startsAt)} · ${event.building ?: "Campus"}")
                    if (event.cancelled) ViewText("This event is cancelled.")
                    event.description?.let { ViewText(it) }
                    if (state.detailLoading) ViewText("Updating details…", color = viewMuted)
                    state.detailError?.let {
                        ViewText(it)
                        TextButton(onClick = { onOpen(event) }) { Text("Retry", color = viewAccent) }
                    }
                    EventCheckInSection(event, checkInState, onStartScan, onScanFailed,
                        onScanCancelled, onCheckIn)
                }
            }, confirmButton = { TextButton(onClick = onClose) { Text("Close", color = viewAccent) } })
    }
}

private fun eventTime(value: String): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }
    val normalized = value.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:\\d{2})$)"), "")
    val date = requireNotNull(input.parse(normalized))
    SimpleDateFormat("MMM d · h:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Bogota")
    }.format(date)
}.getOrDefault(value)
