package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.data.Rso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MyRSOsScreen(
    state: MyRsosUiState = MyRsosUiState(loading = false),
    onLoad: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onLoad() }

    DemoView("My RSOs", showSampleLabel = false) {
        if (state.fromCache) OfflineNotice(state)

        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = viewAccent, contentColor = viewBackground),
            onClick = onCreateGroup,
        ) {
            Text("Propose a new group", fontFamily = viewNunito, modifier = Modifier.padding(vertical = 4.dp))
        }

        when {
            state.loading -> ViewText("Loading your groups…", color = viewMuted)
            state.error != null -> {
                ViewText(state.error)
                TextButton(onClick = onLoad) { Text("Retry", color = viewAccent) }
            }
            else -> {
                RsoSection(
                    title = "MY GROUPS",
                    groups = state.mine,
                    empty = "You have not joined a group yet. Find one in Discover.",
                )
                RsoSection(
                    title = "SAVED FROM EXPLORE",
                    groups = state.saved,
                    empty = "Nothing saved yet. Tap Save on a group in Discover.",
                )
            }
        }
    }
}

@Composable
private fun OfflineNotice(state: MyRsosUiState) {
    ViewCard {
        ViewText(
            if (state.offline) "No connection · showing your last saved list"
            else "Backend unreachable · showing your last saved list",
            color = viewAccent,
        )
        state.cachedAt?.let { ViewText("Last updated ${cacheTime(it)}", color = viewMuted) }
    }
}

@Composable
private fun RsoSection(title: String, groups: List<Rso>, empty: String) {
    ViewText(title, color = viewMuted)
    if (groups.isEmpty()) {
        ViewText(empty, color = viewMuted)
        return
    }
    groups.forEach { group -> RsoCard(group) }
}

@Composable
private fun RsoCard(group: Rso) {
    ViewCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ViewText(group.name, heading = true)
                ViewText(group.category, color = viewAccent)
            }
            if (group.isPendingReview) StatusBadge("Pending review")
            if (group.isRejected) StatusBadge("Rejected")
        }
        ViewText("${group.memberCount} members", color = viewMuted)
        group.nextEventTitle?.let { title ->
            val time = group.nextEventStartsAt?.let { eventTime(it) }
            ViewText(if (time != null) "Next · $title · $time" else "Next · $title", color = viewMuted)
        }
        if (group.nextEventTitle == null) ViewText("No upcoming events", color = viewMuted)
        if (group.isPendingReview) {
            ViewText("An administrator still has to approve this group.", color = viewMuted)
        }
        group.rejectionReason?.let { ViewText("Reason: $it", color = viewMuted) }
    }
}

@Composable
private fun StatusBadge(label: String) {
    Text(
        label,
        color = viewBackground,
        fontFamily = viewNunito,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(viewAccent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun cacheTime(millis: Long): String = runCatching {
    SimpleDateFormat("MMM d · h:mm a", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("America/Bogota") }
        .format(Date(millis))
}.getOrDefault("recently")

private fun eventTime(value: String): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }
    val normalized = value.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:\\d{2})$)"), "")
    val date = requireNotNull(input.parse(normalized))
    SimpleDateFormat("MMM d · h:mm a", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("America/Bogota") }
        .format(date)
}.getOrDefault(value)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyRSOsPreview() {
    MyRSOsScreen(
        state = MyRsosUiState(
            loading = false,
            mine = listOf(
                Rso(1, "Tennis Uniandes", "Sports", "sports", 142, "Weekly match", null,
                    "approved", null, isMember = true, isSaved = false, color = "#A50104"),
                Rso(2, "Club de Robótica", "Technology", "technology", 1, null, null,
                    "pending", null, isMember = true, isSaved = false, color = null),
            ),
            saved = listOf(
                Rso(3, "AI & Machine Learning", "Technology", "technology", 256, "Intro to LLMs", null,
                    "approved", null, isMember = false, isSaved = true, color = "#3B82F6"),
            ),
        ),
    )
}
