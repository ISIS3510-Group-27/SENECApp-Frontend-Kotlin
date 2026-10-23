package com.senecapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.senecapp.R
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
    SectionHeading("Upcoming events")
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
            SectionHeading(if (state.total > state.items.size) "Showing ${state.items.size} of ${state.total} events" else "${state.total} EVENTS")
            state.items.forEach { event ->
                StyledEventCard(event.title, event.group, eventTime(event.startsAt), event.building ?: "Campus",
                    cancelled = event.cancelled, onClick = { onOpen(event) })
            }
        }
    }
    state.selected?.let { event ->
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DemoView("Event details", showSampleLabel = false) {
                TextButton(onClick = onClose) { Text("← Back", color = viewAccent) }
                ViewCard {
                    StyleChip(event.group, viewAccent)
                    ViewText(event.title, heading = true)
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
            }
        }
    }
}

@Composable
internal fun StyledEventCard(title: String, group: String, time: String, location: String,
                             cancelled: Boolean = false, color: String? = null,
                             saved: Boolean = false, onSave: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(viewCard)
        .border(1.dp, viewBorder, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(48.dp).background(viewSecondary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_event), contentDescription = null, tint = viewAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = viewForeground, fontFamily = viewBricolage, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(group, color = organizationColor(color), fontFamily = viewNunito, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            EventMeta(R.drawable.ic_event, time)
            EventMeta(R.drawable.ic_location, location)
            if (cancelled) StyleChip("Cancelled", viewAccent)
        }
        if (onSave != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(if (saved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline),
                    contentDescription = if (saved) "Unsave event" else "Save event", tint = if (saved) viewAccent else viewMuted, modifier = Modifier.size(20.dp))
            }
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = viewMuted, modifier = Modifier.size(20.dp))
        } else Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = viewMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EventMeta(icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = viewMuted, modifier = Modifier.size(12.dp))
        Text(text, color = viewMuted, fontFamily = viewNunito, fontSize = 12.sp)
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
