package com.senecapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senecapp.data.PendingCheckIn
import com.senecapp.data.PendingGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Everything written without connection that has not reached the backend yet, read only from SQLite.
@Composable
internal fun PendingSubmissionsScreen(state: PendingSubmissionsUiState, offline: Boolean, eventTitle: (Int) -> String?,
                                      onLoad: () -> Unit, onSendGroup: (PendingGroup) -> Unit, onDiscardGroup: (PendingGroup) -> Unit,
                                      onSendCheckIn: (PendingCheckIn) -> Unit, onDiscardCheckIn: (PendingCheckIn) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    DemoView("Pending submissions", showSampleLabel = false, header = {
        BackLink(onBack)
        ViewText("Pending submissions", heading = true)
    }) {
        if (offline) InfoNotice("You are offline. Everything here is saved on this phone and is sent automatically " +
                "when you are back online.")
        state.message?.let { InfoNotice(it, error = true) }
        if (!state.loading && state.groups.isEmpty() && state.checkIns.isEmpty()) {
            ViewText("Nothing waiting to be sent.", color = viewMuted)
            return@DemoView
        }
        SectionHeading("GROUP PROPOSALS")
        if (state.groups.isEmpty()) ViewText("No proposals waiting.", color = viewMuted)
        state.groups.forEach { group ->
            PendingCard(group.name, "Saved ${savedTime(group.createdAt)}", group.status, group.lastError, state.sending,
                { onSendGroup(group) }, { onDiscardGroup(group) })
        }
        SectionHeading("CHECK-INS")
        if (state.checkIns.isEmpty()) ViewText("No check-ins waiting.", color = viewMuted)
        state.checkIns.forEach { checkIn ->
            PendingCard(eventTitle(checkIn.eventId) ?: "Event #${checkIn.eventId}",
                "Scanned ${savedTime(checkIn.createdAt)}" + if (checkIn.coordinates == null) " · without location" else "",
                checkIn.status, checkIn.lastError, state.sending, { onSendCheckIn(checkIn) }, { onDiscardCheckIn(checkIn) })
        }
    }
}

@Composable
private fun PendingCard(title: String, detail: String, status: String, lastError: String?, sending: Boolean,
                        onSend: () -> Unit, onDiscard: () -> Unit) {
    ViewCard {
        ViewText(title, heading = true)
        StyleChip(when (status) {
            "sending" -> "Sending…"
            "failed" -> "Not sent"
            else -> "Waiting for connection"
        }, if (status == "failed") viewAccent else viewMuted)
        ViewText(detail, color = viewMuted)
        lastError?.let { ViewText(it, color = viewAccent) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                BrandButton("Send now", onSend, enabled = status != "sending", busy = sending)
            }
            Box(Modifier.weight(1f)) {
                BrandButton("Discard", onDiscard, enabled = status != "sending" && !sending, secondary = true)
            }
        }
    }
}

private fun savedTime(millis: Long): String = runCatching {
    SimpleDateFormat("MMM d · h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Bogota") }
        .format(Date(millis))
}.getOrDefault("recently")
