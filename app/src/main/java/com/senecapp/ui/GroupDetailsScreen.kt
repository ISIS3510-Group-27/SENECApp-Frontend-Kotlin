package com.senecapp.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.senecapp.data.*
import com.senecapp.R
import com.senecapp.analytics.AnalyticsScreen
import com.senecapp.analytics.TelemetryScreenEffect
import java.io.File
import java.util.UUID

@Composable
internal fun BackLink(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(36.dp).background(viewSecondary, RoundedCornerShape(12.dp))) {
        Icon(painterResource(R.drawable.ic_detail_back), "Back", tint = viewForeground, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun GroupDetailsScreen(home: CampusHomeViewModel, expectedGroupId: Int, onBack: () -> Unit,
                                onChanged: () -> Unit, onEvent: (CampusEvent) -> Unit, onCreateEvent: () -> Unit, onAdminRequests: () -> Unit) {
    val detail = home.detail?.takeIf { it.group.id == expectedGroupId }
    TelemetryScreenEffect(AnalyticsScreen.GROUP_DETAIL, meaningful = detail != null,
        identity = expectedGroupId.toString(), measureLoad = true,
        loadFailed = detail == null && !home.detailLoading && home.error != null)
    val canAct = !home.busy && !home.detailFromCache
    var prompt by remember { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var joinAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) home.uploadCover(it, onChanged)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cameraUri?.let { home.uploadCover(Uri.parse(it), onChanged) }
    }
    GroupPage(cover = {
        if (detail != null) GroupCover(detail.group, onBack, canAct) { home.toggleGroupSave(onChanged) }
        else Box(Modifier.padding(24.dp)) { BackLink(onBack) }
    }, footer = {
        detail?.group?.takeIf { !it.isPendingReview && !it.isRejected }?.let { group ->
            BrandButton(if (group.isMember) "✓ Joined – Welcome!" else "Join RSO",
                {
                    note = ""
                    joinAttemptId = UUID.randomUUID().toString().also(home::joinFormOpened)
                    prompt = "Join RSO"
                }, enabled = canAct && !group.isMember,
                secondary = group.isMember, color = organizationColor(group.color))
        }
    }) {
        if (home.detailLoading) CircularProgressIndicator(color = viewAccent)
        if (home.busy) InfoNotice("Updating…")
        home.error?.let { InfoNotice(it, error = true) }
        if (home.detailFromCache && !home.detailLoading) InfoNotice("Showing recent details. Connect and retry to update them.")
        if ((detail == null || home.detailFromCache) && !home.detailLoading) BrandButton("Retry", home::retryGroup)
        detail?.let {
            val group = it.group
            val color = organizationColor(group.color)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(group.memberCount.toString(), "Members", color, Modifier.weight(1f))
                StatTile(it.events.size.toString(), "Upcoming", viewAccent, Modifier.weight(1f))
                StatTile(it.founded?.toString() ?: "—", "Founded", viewTeal, Modifier.weight(1f))
            }
            if (group.isPendingReview) InfoNotice("This group is awaiting review. Joining and event creation will be available once approved.")
            if (group.isRejected) InfoNotice(group.rejectionReason ?: "This group was not approved.", error = true)
            SectionHeading("About")
            ViewText(it.description)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                it.tags.forEach { tag -> StyleChip(tag, color, color.copy(alpha = .13f)) }
            }
            listOfNotNull(it.building, it.email, it.instagram, it.website).takeIf { info -> info.isNotEmpty() }?.let { info ->
                ViewCard { info.forEach { line -> ViewText(line, color = viewMuted) } }
            }
            if (group.role == "admin" && !group.isPendingReview && !group.isRejected) {
                SectionHeading("Leader tools")
                ViewCard {
                    BrandButton("+ Create event", onCreateEvent, enabled = canAct)
                    BrandButton("Change cover photo", { prompt = "Cover photo" }, enabled = canAct, secondary = true)
                    BrandButton("Admin requests", onAdminRequests, enabled = canAct, secondary = true)
                }
            } else if (group.isMember && !group.isPendingReview && !group.isRejected) {
                BrandButton(if (it.adminRequestStatus == "pending") "Admin request pending" else "Request admin access",
                    { note = ""; prompt = "Request admin access" },
                    enabled = canAct && it.adminRequestStatus != "pending", secondary = true)
            }
            SectionHeading("Upcoming events")
            if (it.events.isEmpty()) ViewText("No upcoming events yet.", color = viewMuted)
            it.events.forEach { event ->
                StyledEventCard(event.title, group.name, eventLabel(event.startsAt), event.building ?: "Campus",
                    event.cancelled, group.color, onClick = { onEvent(event) })
            }
            SectionHeading("Members")
            ViewText("${group.memberCount} members in this community", color = viewMuted)
            if (group.isMember) TextButton(onClick = { prompt = "Leave RSO" }, enabled = canAct) { Text("Leave RSO", color = viewMuted) }
        }
    }
    prompt?.let { title ->
        AlertDialog(onDismissRequest = { if (!home.busy) prompt = null }, containerColor = viewCard,
            title = { ViewText(title, heading = true) },
            text = {
                when (title) {
                    "Cover photo" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandButton("Choose from gallery", { prompt = null; gallery.launch("image/*") })
                        BrandButton("Take photo", {
                            try {
                                val directory = File(context.cacheDir, "covers").apply { mkdirs() }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", File(directory, "new-cover.jpg"))
                                cameraUri = uri.toString(); prompt = null; camera.launch(uri)
                            } catch (_: Exception) { prompt = "Camera unavailable" }
                        }, secondary = true)
                    }
                    "Camera unavailable" -> ViewText("No camera app is available. Choose a photo from the gallery.")
                    "Leave RSO" -> ViewText("Leave this organization? If you are its only administrator, another administrator must be assigned first.")
                    else -> BrandField(note, { note = it.take(if (title == "Join RSO") 500 else 300) },
                        if (title == "Join RSO") "Why do you want to join? (optional)" else "Note (optional)", singleLine = false)
                }
            },
            confirmButton = {
                if (title == "Join RSO" || title == "Leave RSO" || title == "Request admin access")
                    TextButton(enabled = canAct, onClick = {
                        when (title) {
                            "Join RSO" -> home.join(note, onChanged, joinAttemptId)
                            "Leave RSO" -> home.leave(onChanged)
                            else -> home.requestAdmin(note)
                        }
                        prompt = null
                    }) { Text("Confirm", color = viewAccent) }
            }, dismissButton = { TextButton(onClick = { prompt = null }) { Text("Close", color = viewMuted) } })
    }
}

@Composable
private fun GroupPage(cover: @Composable () -> Unit, footer: @Composable () -> Unit,
                      content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(viewBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            cover()
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(
            listOf(Color.Transparent, viewBackground, viewBackground))).padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) { footer() }
    }
}

@Composable
private fun GroupCover(group: Rso, onBack: () -> Unit, enabled: Boolean, onSave: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(224.dp)) {
        OrganizationImage(group.name, group.category, organizationColor(group.color), Modifier.fillMaxSize(), group.imageUrl)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66080D28), Color(0xF2171A21)))))
        Box(Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 12.dp)) { BackLink(onBack) }
        Row(Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (group.verified) StyleChip("✓ Official RSO", viewForeground, viewBackground.copy(alpha = .6f))
            IconButton(onClick = onSave, enabled = enabled, modifier = Modifier.size(36.dp).background(viewBackground.copy(alpha = .6f), RoundedCornerShape(12.dp))) {
                Icon(painterResource(if (group.isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                    if (group.isSaved) "Remove from saved" else "Save RSO", tint = if (group.isSaved) viewPrimary else viewForeground, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(group.category.uppercase(), color = viewAccent.copy(alpha = .75f), fontFamily = viewNunito,
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
            Text(group.name, color = viewForeground, fontFamily = viewBricolage, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
        }
    }
}