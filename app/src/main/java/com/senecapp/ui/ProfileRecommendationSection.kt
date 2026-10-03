package com.senecapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.senecapp.data.GroupRecommendation

/** Reuses the server's trained ranking; does not invent a second client-side model. */
@Composable
internal fun ProfileRecommendationSection(state: GroupRecommendationsUiState, onLoad: () -> Unit,
    onOpen: (Int) -> Unit, onJoin: (Int) -> Unit) {
    var requested by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GroupRecommendation?>(null) }
    ViewCard {
        ViewText("Find your next organization", heading = true)
        ViewText("Personalized suggestions for the connected demo account", color = viewMuted)
        TextButton(enabled = !requested || (!state.loading && state.joiningGroupId == null), onClick = {
            requested = true
            onLoad()
        }) { Text("Find a group for me", color = viewAccent) }
        if (requested) {
            when {
                state.loading -> ViewText("Finding a match…", color = viewMuted)
                state.error != null -> ViewText(state.error)
                else -> {
                    val group = state.result?.items?.firstOrNull { !it.isMember && it.id !in state.joinedGroupIds }
                    if (group == null) ViewText("No new suggestions right now.", color = viewMuted)
                    else {
                        ViewText(group.name, heading = true)
                        ViewText(group.category, color = viewAccent)
                        group.reasons.forEach { ViewText(it, color = viewMuted) }
                        TextButton(onClick = { selected = group; onOpen(group.id) }) {
                            Text("View organization", color = viewAccent)
                        }
                    }
                }
            }
            state.actionError?.let { ViewText(it) }
        }
    }
    selected?.let { group ->
        val joined = group.isMember || group.id in state.joinedGroupIds
        AlertDialog(onDismissRequest = { selected = null }, containerColor = viewBackground,
            titleContentColor = viewForeground, textContentColor = viewForeground,
            title = { ViewText(group.name, heading = true) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewText(group.description)
                group.reasons.forEach { ViewText(it, color = viewMuted) }
                state.actionError?.let { ViewText(it) }
            } },
            confirmButton = {
                TextButton(enabled = !joined && state.joiningGroupId == null, onClick = { onJoin(group.id) }) {
                    Text(when { joined -> "Joined"; state.joiningGroupId == group.id -> "Joining…"; else -> "Join organization" },
                        color = if (joined || state.joiningGroupId != null) viewMuted else viewAccent)
                }
            }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Close", color = viewAccent) } })
    }
}
