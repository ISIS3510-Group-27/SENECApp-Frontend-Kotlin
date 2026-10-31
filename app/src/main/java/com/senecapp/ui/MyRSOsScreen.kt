package com.senecapp.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.analytics.AnalyticsScreen
import com.senecapp.analytics.TelemetryScreenEffect
import com.senecapp.data.Rso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MyRSOsScreen(state: MyRsosUiState = MyRsosUiState(loading = false),
                 onLoad: () -> Unit = {}, onCreateGroup: () -> Unit = {},
                 attended: Int? = null, onOpenGroup: (Int) -> Unit = {}) {
    TelemetryScreenEffect(AnalyticsScreen.MY_RSOS, meaningful = !state.loading && state.error == null, measureLoad = true,
        failure = state.failure.takeIf { state.error != null })
    LaunchedEffect(Unit) { onLoad() }
    DemoView("My RSOs", showSampleLabel = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(if (state.loading) "—" else state.mine.size.toString(), "Joined", viewPrimary, Modifier.weight(1f))
            StatTile(attended?.toString() ?: "—", "Events attended", viewAccent, Modifier.weight(1f))
            StatTile(com.senecapp.data.semesterLabel(), "This semester", viewTeal, Modifier.weight(1f))
        }
        if (state.fromCache) OfflineNotice(state)
        BrandButton("Propose a new group", onCreateGroup)
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp),
                color = viewAccent, strokeWidth = 2.dp)
            state.error != null -> {
                InfoNotice(state.error, error = true)
                TextButton(onClick = onLoad) { Text("Retry", color = viewAccent) }
            }
            else -> {
                RsoSection("Active Memberships", state.mine, "You have not joined a group yet. Find one in Discover.", onOpenGroup)
                RsoSection("Saved from Explore", state.saved, "Nothing saved yet. Tap Save on a group in Discover.", onOpenGroup)
            }
        }
    }
}

@Composable
private fun OfflineNotice(state: MyRsosUiState) {
    ViewCard {
        ViewText(if (state.offline) "No connection · showing your last saved list"
            else "Backend unreachable · showing your last saved list", color = viewAccent)
        state.cachedAt?.let { ViewText("Last updated ${cacheTime(it)}", color = viewMuted) }
    }
}

@Composable
private fun RsoSection(title: String, groups: List<Rso>, empty: String, onOpen: (Int) -> Unit) {
    SectionHeading(title)
    if (groups.isEmpty()) ViewText(empty, color = viewMuted)
    else groups.forEach { RsoCard(it) { onOpen(it.id) } }
}

@Composable
private fun RsoCard(group: Rso, onOpen: () -> Unit) {
    val color = organizationColor(group.color)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(viewCard)
        .border(1.dp, viewBorder, RoundedCornerShape(16.dp)).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            OrganizationImage(group.name, group.category, color, Modifier.fillMaxSize(), group.imageUrl)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                androidx.compose.ui.graphics.Color.Transparent, color.copy(alpha = .6f)))))
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                StyleChip(when {
                    group.isPendingReview -> "Pending review"
                    group.isRejected -> "Not approved"
                    group.isMember -> "Member"
                    else -> "Saved"
                }, if (group.isPendingReview) viewBackground else viewForeground,
                    when { group.isPendingReview -> viewAccent; group.isRejected -> viewPrimary
                        else -> viewBackground.copy(alpha = .7f) })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.name + if (group.verified) " ✓" else "", color = viewForeground, fontFamily = viewBricolage,
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${group.memberCount} members · ${group.category}", color = viewMuted,
                        fontFamily = viewNunito, fontSize = 12.sp)
                }
            }
            group.nextEventTitle?.let { title ->
                val time = group.nextEventStartsAt?.let { eventTime(it) }
                StyleChip(if (time != null) "Next · $title · $time" else "Next · $title", color, color.copy(alpha = .13f))
            }
            if (group.nextEventTitle == null) ViewText("No upcoming events", color = viewMuted)
            if (group.isPendingReview) ViewText("An administrator still has to approve this group.", color = viewMuted)
            group.rejectionReason?.let { ViewText("Reason: $it", color = viewMuted) }
        }
    }
}

private fun cacheTime(millis: Long): String = runCatching {
    SimpleDateFormat("MMM d · h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Bogota") }
        .format(Date(millis))
}.getOrDefault("recently")

private fun eventTime(value: String): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }
    val normalized = value.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:\\d{2})$)"), "")
    val date = requireNotNull(input.parse(normalized))
    SimpleDateFormat("MMM d · h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Bogota") }.format(date)
}.getOrDefault(value)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyRSOsPreview() {
    AuthTheme { MyRSOsScreen(MyRsosUiState(loading = false, mine = listOf(
        Rso(1, "Tennis Uniandes", "Sports", "sports", 142, "Weekly match", null,
            "approved", null, true, false, "#A50104")))) }
}
