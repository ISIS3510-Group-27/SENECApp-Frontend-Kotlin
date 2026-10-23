package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.data.UserProfile

@Composable
fun ProfileScreen(
    profile: UserProfile? = null, loading: Boolean = false, error: String? = null,
    onRefresh: () -> Unit = {}, onSignOut: () -> Unit = {},
    recommendationsState: GroupRecommendationsUiState = GroupRecommendationsUiState(loading = false),
    onLoadRecommendations: () -> Unit = {}, onOpenRecommendedGroup: (Int) -> Unit = {},
    onJoinRecommendedGroup: (Int) -> Unit = {},
    joinedCount: Int? = null, attended: Int? = null,
    outdoorMode: String = "Auto", onOutdoorMode: (String) -> Unit = {},
    onLocation: (Boolean) -> Unit = {}, onSchedule: () -> Unit = {},
    settingsBusy: Boolean = false, externalDetails: Boolean = false,
) {
    var comingSoon by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    DemoView("Profile", showSampleLabel = false) {
        if (profile == null) {
            ViewText("Your profile is not available yet.")
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh profile", color = viewAccent) }
            TextButton(onClick = onSignOut) { Text("Sign out", color = viewPrimary) }
        }
        else {
            Column(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(viewSecondary, viewCard)), RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    val initials = profile.fullName?.split(" ")?.filter { it.isNotBlank() }?.take(2)
                        ?.joinToString("") { it.take(1) }?.uppercase().orEmpty()
                    Box(Modifier.size(64.dp).background(viewPrimary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(initials.ifBlank { profile.email.take(1).uppercase() }, color = viewForeground,
                            fontFamily = viewBricolage, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ViewText(profile.fullName ?: "Name not set", heading = true)
                        Text(profile.email, color = viewMuted, fontFamily = viewNunito, fontSize = 12.sp)
                        StyleChip(profile.program ?: "Uniandes student", viewForeground, viewBackground)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    ProfileStat(joinedCount?.toString() ?: "—", "RSOs", Modifier.weight(1f))
                    ProfileStat(attended?.toString() ?: "—", "Events", Modifier.weight(1f))
                    ProfileStat(profile.semester?.toString() ?: "—", "Semester", Modifier.weight(1f))
                }
            }
            SectionHeading("Interests")
            if (profile.interests.isEmpty()) ViewText("No interests added yet.", color = viewMuted)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.interests.forEach { StyleChip(it, viewForeground) }
            }
            TextButton(onClick = { comingSoon = "Editing interests" }) { Text("+ Add interest", color = viewAccent) }
            SectionHeading("Account")
            ViewCard {
                Box(Modifier.fillMaxWidth().clickable(onClick = onSchedule)) { AccountRow("Class schedule →", "Improve suggestions around your classes") }
                HorizontalDivider(color = viewBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { AccountRow("Location sharing", "Nearby event suggestions") }
                    Switch(checked = profile.locationOptIn, onCheckedChange = onLocation, enabled = !settingsBusy,
                        colors = SwitchDefaults.colors(checkedThumbColor = viewAccent, checkedTrackColor = viewAccent.copy(alpha = .3f)))
                }
                HorizontalDivider(color = viewBorder)
                AccountRow("Outdoor mode", "Increase contrast in bright light")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "On", "Off").forEach { mode ->
                        FilterChip(selected = outdoorMode == mode, onClick = { onOutdoorMode(mode) }, label = { Text(mode) })
                    }
                }
                listOf("Notifications" to "Manage event alerts", "Privacy Settings" to "Control who sees your profile",
                    "University Verification" to "Verified via uniandes.edu.co", "Help & Support" to "Report an issue").forEach { (title, subtitle) ->
                    HorizontalDivider(color = viewBorder)
                    Box(Modifier.fillMaxWidth().clickable { comingSoon = title }) { AccountRow(title, subtitle) }
                }
                HorizontalDivider(color = viewBorder)
                TextButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh profile", color = viewAccent)
                }
                TextButton(onClick = onSignOut) { Text("Sign out", color = viewPrimary) }
            }
            ProfileRecommendationSection(recommendationsState, onLoadRecommendations,
                onOpenRecommendedGroup, onJoinRecommendedGroup, externalDetails)
        }
        error?.let { InfoNotice(it, error = true) }
    }
    comingSoon?.let { title ->
        AlertDialog(onDismissRequest = { comingSoon = null }, containerColor = viewCard,
            title = { ViewText(title, heading = true) }, text = { ViewText("$title is coming soon.") },
            confirmButton = { TextButton(onClick = { comingSoon = null }) { Text("OK", color = viewAccent) } })
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = viewForeground, fontFamily = viewBricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(label, color = viewMuted, fontFamily = viewNunito, fontSize = 11.sp)
    }
}

@Composable
private fun AccountRow(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = viewForeground, fontFamily = viewNunito, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subtitle, color = viewMuted, fontFamily = viewNunito, fontSize = 11.sp)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfilePreview() { AuthTheme { ProfileScreen() } }
