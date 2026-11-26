package com.senecapp.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import coil3.decode.DataSource
import com.senecapp.BuildConfig
import com.senecapp.data.GroupRecommendation
import java.net.URI

/** Reuses the server's trained ranking; does not invent a second client-side model. */
@Composable
internal fun ProfileRecommendationSection(state: GroupRecommendationsUiState, onLoad: () -> Unit,
    onOpen: (Int) -> Unit, onJoin: (Int) -> Unit, externalDetails: Boolean = false) {
    var requested by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GroupRecommendation?>(null) }
    ViewCard {
        ViewText("Find your next organization", heading = true)
        ViewText("Personalized suggestions for your interests", color = viewMuted)
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
                    if (state.fromCache) InfoNotice("Saved recommendations. Reconnect and refresh to open or join an organization.")
                    if (group == null) ViewText("No new suggestions right now.", color = viewMuted)
                    else {
                        val image = remember(group.imageUrl) {
                            group.imageUrl?.takeIf { runCatching { URI(it) }.isSuccess }
                        }
                        OrganizationImage(group.name, group.category, organizationColor(group.color),
                            modifier = Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(16.dp)),
                            imageUrl = image, onSuccess = { loaded ->
                                if (BuildConfig.DEBUG) {
                                    val source = if (loaded.result.dataSource == DataSource.MEMORY_CACHE) "MEMORY"
                                        else loaded.result.dataSource.name
                                    Log.d("ProfileRecommendationImage", "source=$source")
                                }
                            })
                        ViewText(group.name, heading = true)
                        ViewText(group.category, color = viewAccent)
                        group.reasons.forEach { ViewText(it, color = viewMuted) }
                        TextButton(enabled = !state.fromCache, onClick = { if (!externalDetails) selected = group; onOpen(group.id) }) {
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
                TextButton(enabled = !state.fromCache && !joined && state.joiningGroupId == null, onClick = { onJoin(group.id) }) {
                    Text(when { joined -> "Joined"; state.joiningGroupId == group.id -> "Joining…"; else -> "Join organization" },
                        color = if (joined || state.joiningGroupId != null) viewMuted else viewAccent)
                }
            }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Close", color = viewAccent) } })
    }
}
