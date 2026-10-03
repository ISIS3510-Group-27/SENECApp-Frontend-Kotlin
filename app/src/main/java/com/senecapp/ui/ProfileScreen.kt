package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    recommendationsState: GroupRecommendationsUiState = GroupRecommendationsUiState(loading = false),
    onLoadRecommendations: () -> Unit = {},
    onOpenRecommendedGroup: (Int) -> Unit = {},
    onJoinRecommendedGroup: (Int) -> Unit = {},
) {
    DemoView("Profile", showSampleLabel = false) {
        ViewCard {
            Box(Modifier.size(66.dp).background(viewPrimary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) { ViewText("DV", heading = true) }
            ViewText("Daniel Vergara", heading = true)
            ViewText("Sample student profile", color = viewAccent)
            ViewText("daniel@example.com", color = viewMuted)
            ViewText("Systems Engineering · Semester 6", color = viewMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("2" to "RSOs", "11" to "Events", "2" to "Years").forEach { (value, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ViewText(value, heading = true)
                        ViewText(label, color = viewMuted)
                    }
                }
            }
        }
        ViewText("INTERESTS", color = viewMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tennis", "AI / ML", "Startups", "Travel", "Cars").forEach {
                Box(Modifier.background(viewSecondary, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    ViewText(it)
                }
            }
        }
        ProfileRecommendationSection(recommendationsState, onLoadRecommendations,
            onOpenRecommendedGroup, onJoinRecommendedGroup)
        ViewText("ACCOUNT · PREVIEW", color = viewMuted)
        ViewCard {
            listOf("Notifications" to "Event alerts",
                "Privacy Settings" to "Profile visibility",
                "University Verification" to "Sample status: not verified",
                "Help & Support" to "Campus support information").forEach { (title, subtitle) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    ViewText(title)
                    ViewText(subtitle, color = viewMuted)
                }
            }
        }
        ViewText("Account settings are display-only in this demo.", color = viewMuted)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfilePreview() { ProfileScreen() }
