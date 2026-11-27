package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.senecapp.analytics.AnalyticsScreen
import com.senecapp.analytics.TelemetryScreenEffect
import com.senecapp.data.ProfileTipField
import com.senecapp.data.ProfileTipsSnapshot
import com.senecapp.data.ProfileTipsSummary
import com.senecapp.data.Rso
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal const val PROFILE_TIPS_TITLE = "Profile tips"

@Composable
internal fun ProfileTipsScreen(groupId: Int, groupName: String, group: Rso?, model: ProfileTipsViewModel, onBack: () -> Unit) {
    val active by model.state.collectAsState()
    val state = active.takeIf { it.groupId == groupId } ?: ProfileTipsState(groupId = groupId)
    LaunchedEffect(groupId, model) { model.open(groupId) }
    LaunchedEffect(groupId, model) { model.watchConnection(groupId) }

    TelemetryScreenEffect(AnalyticsScreen.PROFILE_TIPS, meaningful = state.summary != null || (!state.loading && state.error != null),
        failure = state.failure, identity = groupId.toString(), measureLoad = true,
        loadFailed = !state.loading && state.summary == null && state.error != null)

    Column(Modifier.fillMaxSize().background(viewBackground).windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            BackLink(onBack)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ViewText(PROFILE_TIPS_TITLE, heading = true)
                ViewText("What makes students save or join ${group?.name ?: groupName}.", color = viewMuted)
            }
        }
        // Cover image through Coil 3 (memory + disk cache): opens without network if it was seen before.
        val color = organizationColor(group?.color)
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))) {
            OrganizationImage(group?.name ?: groupName, group?.category.orEmpty(), color, Modifier.fillMaxSize(), group?.imageUrl)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = .6f)))))
        }
        DaysSelector(state.days, enabled = !state.loading, onSelect = model::selectDays)

        val snapshot = state.snapshot
        val summary = state.summary
        when {
            snapshot != null && summary != null -> ProfileTipsContent(snapshot, summary, state, model::retry)
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = viewAccent)
            // Never an endless spinner: without data there is always a message and, unless it is a 403, Retry.
            else -> {
                InfoNotice(state.error ?: "Could not load profile tips. Tap Retry.", error = state.online || state.denied)
                if (!state.denied) BrandButton("Retry", model::retry)
            }
        }
    }
}

@Composable
private fun DaysSelector(days: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ViewText("Window", color = viewMuted)
        listOf(30, 90).forEach { option ->
            Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { onSelect(option) }) {
                StyleChip("$option days", if (option == days) viewBackground else viewForeground,
                    if (option == days) viewAccent else viewSecondary)
            }
        }
    }
}

@Composable
private fun ProfileTipsContent(snapshot: ProfileTipsSnapshot, summary: ProfileTipsSummary, state: ProfileTipsState, onRetry: () -> Unit) {
    if (snapshot.fromPhone || state.error != null) {
        ViewCard {
            ViewText(when {
                !state.online -> "No connection · showing tips from ${syncedLabel(snapshot.syncedAt)}"
                snapshot.fromPhone -> "Backend unreachable · showing tips from ${syncedLabel(snapshot.syncedAt)}"
                else -> "Could not update · showing tips from ${syncedLabel(snapshot.syncedAt)}"
            }, color = viewAccent)
            ViewText(if (!state.online) "They refresh by themselves when the connection is back."
                else "Tap Retry to try again.", color = viewMuted)
            if (state.online) BrandButton("Retry", onRetry)
        }
    }
    if (snapshot.tips.days != state.days) {
        ViewText("Showing the last ${snapshot.tips.days}-day window saved on this phone.", color = viewMuted)
    }
    ViewCard {
        ViewText("${summary.filled} of ${summary.total} profile fields complete", heading = true)
        LinearProgressIndicator(progress = { summary.progress }, modifier = Modifier.fillMaxWidth(),
            color = viewAccent, trackColor = viewSecondary)
    }
    ViewCard {
        ViewText(snapshot.tips.question, color = viewMuted)
        ViewText(snapshot.tips.answer, heading = true)
    }
    val missing = summary.ordered.filter { !it.filled }
    val done = summary.ordered.filter { it.filled }
    if (missing.isNotEmpty()) {
        SectionHeading("Missing on your profile")
        missing.forEach { FieldCard(it) }
    }
    if (done.isNotEmpty()) {
        SectionHeading("Already on your profile")
        done.forEach { FieldCard(it) }
    }
    ViewText("Conversion = students who saved or joined after opening a group, in groups with and without that field. " +
        "It shows a relation, not a guarantee.", color = viewMuted)
}

@Composable
private fun FieldCard(field: ProfileTipField) {
    val (label, advice) = fieldText(field.field)
    ViewCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ViewText(label, heading = true) }
            StyleChip(if (field.filled) "Done" else "Missing",
                if (field.filled) viewTeal else viewBackground, if (field.filled) viewSecondary else viewAccent)
        }
        if (field.conversionWith != null && field.conversionWithout != null) {
            ViewText("${percentage(field.conversionWith)} save or join with it · ${percentage(field.conversionWithout)} without it",
                color = viewBody)
        } else {
            ViewText("Not enough views yet to compare.", color = viewMuted)
        }
        if (!field.filled) ViewText(advice, color = viewMuted)
    }
}

// Plain-language label and tip for each field the backend can send.
private fun fieldText(field: String): Pair<String, String> = when (field) {
    "has_image" -> "Cover image" to "Upload a cover photo so your group stands out in Explore."
    "has_contact_email" -> "Contact email" to "Add an email so students can ask questions before joining."
    "has_instagram" -> "Instagram" to "Link your Instagram so students can see what you do."
    "has_website" -> "Website" to "Add a website with more information about the group."
    "has_meeting_building" -> "Meeting place" to "Say which building you meet in so students know where to go."
    "has_founded_year" -> "Founded year" to "Add the year the group started; it shows the group is established."
    "has_upcoming_event" -> "Upcoming event" to "Publish your next event so students have a reason to join now."
    else -> field.removePrefix("has_").replace('_', ' ').replaceFirstChar { it.uppercase() } to
        "Completing this field can help students decide."
}

private fun percentage(rate: Double): String = "${(rate * 100).roundToInt()}%"


private val SYNCED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)
    .withZone(ZoneId.of("America/Bogota"))

private fun syncedLabel(millis: Long): String = SYNCED_FORMAT.format(Instant.ofEpochMilli(millis))
