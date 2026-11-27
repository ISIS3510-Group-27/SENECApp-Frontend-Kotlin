package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.senecapp.R
import com.senecapp.analytics.AnalyticsScreen
import com.senecapp.analytics.TelemetryScreenEffect
import com.senecapp.data.JoinSource
import com.senecapp.data.JoinSourcesKey
import com.senecapp.data.JoinSourcesView
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val JOIN_SOURCES_TITLE = "Where your members come from"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JoinSourcesScreen(key: JoinSourcesKey, groupName: String, model: JoinSourcesViewModel, onBack: () -> Unit) {
    val active by model.state.collectAsState()
    val state = active.takeIf { it.key == key } ?: JoinSourcesState()
    val snackbar = remember(key) { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var now by remember(key) { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(key, model) {
        model.open(key)
        onDispose { model.close(key) }
    }
    LaunchedEffect(key, model) { model.watchConnection(key) }
    LaunchedEffect(key) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    TelemetryScreenEffect(AnalyticsScreen.JOIN_SOURCES, meaningful = state.view != null || (!state.loading && state.error != null),
        failure = state.failure, identity = key.groupId.toString(), measureLoad = true,
        loadFailed = !state.loading && state.view == null && state.error != null)

    Box(Modifier.fillMaxSize().background(viewBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        PullToRefreshBox(
            isRefreshing = state.loading && state.view != null,
            onRefresh = {
                if (!model.refresh()) scope.launch {
                    snackbar.showSnackbar("You're offline. This is the copy saved on your phone; it updates when the connection is back.")
                }
            },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    BackLink(onBack)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ViewText(JOIN_SOURCES_TITLE, heading = true)
                        ViewText("How students found $groupName before joining it.", color = viewMuted)
                    }
                }
                val view = state.view
                when {
                    view != null -> JoinSourcesContent(view, state, now)
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = viewAccent)
                    else -> {
                        InfoNotice(state.error?.takeUnless { it == "Could not update" }
                            ?: "Could not load join sources. Tap Retry.", error = state.online)
                        BrandButton("Retry", { model.refresh() })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun JoinSourcesContent(view: JoinSourcesView, state: JoinSourcesState, now: Long) {
    val answer = view.current.data
    if (view.fromPhone || state.error != null || !state.online) {
        val age = visitAge(view.current.takenAt, now)
        ViewText(when {
            !state.online -> "Saved $age ago on this phone. New joins show up here once you are back online; it refreshes by itself."
            state.error != null -> "Saved $age ago · could not update"
            else -> "Saved $age ago · updating…"
        }, color = viewMuted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(answer.joins.toString(), "joins in ${answer.days} days", viewAccent, Modifier.weight(1f))
        StatTile(answer.views.toString(), "profile views", viewTeal, Modifier.weight(1f))
        StatTile(percentage(if (answer.views == 0) null else answer.joins.toDouble() / answer.views),
            "views that join", Color(0xFFB6A0FF), Modifier.weight(1f))
    }
    ViewCard {
        ViewText(answer.answer, heading = true)
        ViewText("On the whole campus: ${answer.campusAnswer}", color = viewMuted)
    }
    view.previous?.let { ViewText("Compared with your visit ${visitAge(it.takenAt, now)} ago.", color = viewMuted) }
    if (answer.sources.isEmpty()) {
        ViewText("Nobody has opened or joined the group from the app in the last ${answer.days} days yet.", color = viewMuted)
    } else {
        SectionHeading("By entry point")
        answer.sources.forEach { source -> JoinSourceCard(source, view.newJoins(source)) }
    }
    ViewText("A profile view counts each time a student opens the group page. The campus rate is the same entry point for every group.",
        color = viewMuted)
}

@Composable
private fun JoinSourceCard(source: JoinSource, newJoins: Int?) {
    val (label, icon) = when (source.entryPoint) {
        "recommendation" -> "Recommended for you" to R.drawable.ic_star
        "search" -> "Search and filters" to R.drawable.ic_search
        "explore" -> "Browsing Explore" to R.drawable.ic_filter
        "notification" -> "Notifications" to R.drawable.ic_notifications
        "event" -> "From an event page" to R.drawable.ic_event
        "direct" -> "Direct link or My RSOs" to R.drawable.ic_group_outline
        else -> source.entryPoint.replace('_', ' ') to R.drawable.ic_group_outline
    }
    ViewCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = viewAccent, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) { ViewText(label, heading = true) }
            ViewText("${source.joins} ${if (source.joins == 1) "join" else "joins"}")
            if (newJoins != null && newJoins > 0) ViewText("+$newJoins", color = Color(0xFF69D391))
        }
        LinearProgressIndicator(progress = { (source.shareOfJoins ?: 0.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth(), color = viewAccent, trackColor = viewSecondary)
        ViewText("${percentage(source.shareOfJoins ?: 0.0)} of your joins · ${source.views} views · " +
            "${percentage(source.viewToJoinRate)} join (campus ${percentage(source.campusViewToJoinRate)})",
            color = viewMuted)
    }
}

private fun percentage(rate: Double?): String =
    rate?.let { "${(it * 100).roundToInt()}%" } ?: "–"

private fun visitAge(takenAt: Long, now: Long): String {
    val minutes = ((now - takenAt).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "less than a minute"
        minutes < 60 -> if (minutes == 1L) "1 minute" else "$minutes minutes"
        minutes < 1_440 -> (minutes / 60).let { if (it == 1L) "1 hour" else "$it hours" }
        else -> (minutes / 1_440).let { if (it == 1L) "1 day" else "$it days" }
    }
}
