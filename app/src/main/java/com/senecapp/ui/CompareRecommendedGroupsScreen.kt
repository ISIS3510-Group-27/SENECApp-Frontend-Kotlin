package com.senecapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senecapp.data.GroupRecommendation
import com.senecapp.data.isOnline
import com.senecapp.data.networkStatus
import java.net.URI

@Composable
internal fun CompareRecommendedGroupsRoute(
    viewModel: GroupRecommendationsViewModel,
    onBack: () -> Unit,
    onOpen: (Int, String?) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var online by remember(context) { mutableStateOf(isOnline(context)) }
    LaunchedEffect(viewModel, context) {
        if (viewModel.state.result == null || (online && viewModel.state.fromCache)) {
            viewModel.loadForComparison()
        }
        networkStatus(context).collect { connected ->
            if (connected && !online) viewModel.loadForComparison()
            online = connected
        }
    }
    CompareRecommendedGroupsScreen(viewModel.state, online, onBack, viewModel::loadForComparison) { id ->
        // Read the current snapshot so navigation and its request ID stay paired.
        val state = viewModel.state
        val result = state.result
        if (state.canOpenComparisonGroup(isOnline(context)) && result?.items?.any { it.id == id } == true) {
            onOpen(id, result.requestId.takeIf { it.isNotBlank() })
        }
    }
}

@Composable
internal fun CompareRecommendedGroupsScreen(
    state: GroupRecommendationsUiState,
    online: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    var firstId by rememberSaveable { mutableStateOf<Int?>(null) }
    var secondId by rememberSaveable { mutableStateOf<Int?>(null) }
    val groups = state.result?.items.orEmpty().distinctBy { it.id }
    val first = groups.firstOrNull { it.id == firstId }
    val second = groups.firstOrNull { it.id == secondId }
    LaunchedEffect(state.result) {
        if (state.result != null) {
            firstId = first?.id
            secondId = second?.id
        }
    }

    DemoView("Compare recommended groups", showSampleLabel = false, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackLink(onBack)
            ViewText("Compare recommended groups", heading = true)
        }
    }) {
        if (state.fromCache || !online) {
            InfoNotice(if (state.result != null) "Showing saved recommendations. Reconnect and refresh to open an organization."
                else "You're offline. Connect to load recommendations if none are saved.")
        }
        if (state.loading) CircularProgressIndicator(color = viewAccent)
        if (state.error != null) {
            InfoNotice(if (online) "Could not load recommendations." else "No saved recommendations are available.", error = true)
        }
        if (!state.loading) {
            TextButton(onClick = onRetry) {
                Text(if (state.error != null || state.fromCache || !online) "Retry" else "Refresh recommendations", color = viewAccent)
            }
        }
        if (state.result != null && groups.size < 2) {
            ViewText("You need at least two recommendations to compare.", color = viewMuted)
        }
        if (groups.size >= 2) {
            ViewCard {
                GroupPicker("First organization", first, groups.filter { it.id != secondId }) { firstId = it }
                GroupPicker("Second organization", second, groups.filter { it.id != firstId }) { secondId = it }
            }
        }
        if (first != null && second != null) {
            val canOpen = state.canOpenComparisonGroup(online)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ComparedGroup(first, canOpen, { onOpen(first.id) }, Modifier.weight(1f))
                ComparedGroup(second, canOpen, { onOpen(second.id) }, Modifier.weight(1f))
            }
            ViewCard {
                ComparisonField("Category", listOf(first.category), listOf(second.category))
                ComparisonField("Members", listOf(first.memberCount.toString()), listOf(second.memberCount.toString()))
                ComparisonField("Description", listOf(first.description), listOf(second.description))
                ComparisonField("Recommendation reasons", first.reasons, second.reasons)
            }
        }
    }
}

internal fun GroupRecommendationsUiState.canOpenComparisonGroup(online: Boolean): Boolean =
    online && !loading && !fromCache && error == null && result != null

@Composable
private fun GroupPicker(label: String, selected: GroupRecommendation?, groups: List<GroupRecommendation>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        SectionHeading(label)
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected?.name ?: "Choose organization", color = viewAccent) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                groups.forEach { group ->
                    DropdownMenuItem(text = { Text(group.name) }, onClick = { expanded = false; onSelect(group.id) })
                }
            }
        }
    }
}

@Composable
private fun ComparedGroup(group: GroupRecommendation, canOpen: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val image = remember(group.imageUrl) { group.imageUrl?.takeIf { runCatching { URI(it) }.isSuccess } }
    ViewCard(modifier) {
        OrganizationImage(group.name, group.category, organizationColor(group.color),
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)), image)
        ViewText(group.name, heading = true)
        TextButton(onClick = onOpen, enabled = canOpen) { Text("View organization") }
    }
}

@Composable
private fun ComparisonField(label: String, first: List<String>, second: List<String>) {
    SectionHeading(label)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            first.ifEmpty { listOf("—") }.forEach { ViewText(it) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            second.ifEmpty { listOf("—") }.forEach { ViewText(it) }
        }
    }
}
