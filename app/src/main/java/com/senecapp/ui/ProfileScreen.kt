package com.senecapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.senecapp.data.UserProfile

@Composable
fun ProfileScreen(
    profile: UserProfile? = null,
    loading: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onSignOut: () -> Unit = {},
    recommendationsState: GroupRecommendationsUiState = GroupRecommendationsUiState(loading = false),
    onLoadRecommendations: () -> Unit = {},
    onOpenRecommendedGroup: (Int) -> Unit = {},
    onJoinRecommendedGroup: (Int) -> Unit = {},
) {
    LaunchedEffect(Unit) { onRefresh() }
    DemoView("Profile", showSampleLabel = false) {
        if (profile == null) {
            ViewText("Your profile is not available yet.")
        } else {
            ViewCard {
                ViewText(profile.fullName ?: "Name not set", heading = true)
                ViewText(profile.email)
                ViewText("Verified university email", color = viewAccent)
                ViewText(profile.program ?: "Program not set")
                ViewText(profile.semester?.let { "Semester $it" } ?: "Semester not set")
            }
            ViewText("INTERESTS", color = viewAccent)
            if (profile.interests.isEmpty()) ViewText("No interests added yet.")
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.interests.forEach { interest -> ViewCard { ViewText(interest) } }
            }
            ViewCard {
                ViewText("Preferences", heading = true)
                ViewText("Location consent: ${if (profile.locationOptIn) "On" else "Off"}")
                ViewText("Notifications preference: ${if (profile.notificationsOptIn) "On" else "Off"}")
                ViewText("You can change location consent in Events.")
            }
            ProfileRecommendationSection(recommendationsState, onLoadRecommendations,
                onOpenRecommendedGroup, onJoinRecommendedGroup)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Refreshing…" else "Refresh profile") }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfilePreview() { AuthTheme { ProfileScreen() } }
