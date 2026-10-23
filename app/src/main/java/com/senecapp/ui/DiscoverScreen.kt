package com.senecapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.senecapp.R
import com.senecapp.data.Organization
import com.senecapp.data.GroupRecommendation
import kotlinx.coroutines.delay

private data class DiscoverPalette(val background: Color, val foreground: Color, val primary: Color,
    val accent: Color, val secondary: Color, val card: Color, val muted: Color, val selectedText: Color,
    val border: Color, val highContrast: Boolean)
private val standardPalette = DiscoverPalette(viewBackground, viewForeground, viewPrimary, viewAccent,
    viewSecondary, viewCard, viewMuted, Color.White, viewBorder, false)
private val brightLightPalette = DiscoverPalette(Color.Black, Color.White, Color(0xFFFFD54F),
    Color(0xFFFFD54F), Color(0xFF243550), Color(0xFF101A2A), Color(0xFFE3E7F2),
    Color.Black, Color.White.copy(alpha = .55f), true)
private val previewOrganizations = listOf(
    Organization(1, "Tennis Uniandes", "Sports", "sports", 142, "#A50104", true),
    Organization(2, "Emprendedores Uniandes", "Business", "business", 318, "#FF6B35", true),
    Organization(3, "AI & Machine Learning", "Technology", "technology", 256, "#3B82F6", true))

@Composable
fun DiscoverScreen(
    showBottomNavigation: Boolean = true, highContrast: Boolean = false,
    organizationsState: OrganizationsUiState = OrganizationsUiState(loading = false, items = previewOrganizations,
        categories = previewOrganizations.map { it.categorySlug to it.category }),
    onSearch: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    onOpenOrganization: (Int, Boolean) -> Unit = { _, _ -> }, onCloseOrganization: () -> Unit = {},
    onToggleSave: (Int) -> Unit = {},
    recommendationsState: GroupRecommendationsUiState = GroupRecommendationsUiState(loading = false),
    onLoadRecommendations: () -> Unit = {}, onOpenRecommendedGroup: (Int) -> Unit = {},
    onJoinRecommendedGroup: (Int) -> Unit = {},
    externalDetails: Boolean = false,
    unreadCount: Int = 0, onNotifications: () -> Unit = {},
    onFilters: () -> Unit = {}, onSeeAll: () -> Unit = {}, onCreateGroup: () -> Unit = {},
) {
    val palette = if (highContrast) brightLightPalette else standardPalette
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var upcomingOnly by rememberSaveable { mutableStateOf(false) }
    var selectedOrganization by remember { mutableStateOf<Organization?>(null) }
    val fromSearch = organizationsState.searchFiltered
    LaunchedEffect(query, category, upcomingOnly) { delay(350); onSearch(query, category, upcomingOnly) }
    LaunchedEffect(query.isBlank() && category == null && !upcomingOnly) {
        if (query.isBlank() && category == null && !upcomingOnly && recommendationsState.result == null) onLoadRecommendations()
    }
    Column(Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header(palette, unreadCount, onNotifications)
            SearchField(query, { query = it }, palette)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onFilters) { Text(if (organizationsState.filters.active) "Filters · Active" else "Filters", color = palette.accent) }
                TextButton(onClick = onCreateGroup) { Text("+ New RSO", color = palette.accent) }
            }
            if (query.isBlank() && category == null && !upcomingOnly) {
                GroupRecommendationsSection(recommendationsState, palette, onLoadRecommendations,
                    onOpenRecommendedGroup, onJoinRecommendedGroup, externalDetails, onSeeAll)
                if (recommendationsState.result?.items.isNullOrEmpty() && organizationsState.items.isNotEmpty()) {
                    FeedLabel("FEATURED", palette)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        organizationsState.items.take(5).forEach { organization ->
                            CoverCard(organization.name, organization.category, organization.members,
                                organizationColor(organization.color), null, palette, organization.imageUrl, organization.verified) {
                                if (!externalDetails) selectedOrganization = organization
                                onOpenOrganization(organization.id, fromSearch)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(null to "All") + organizationsState.categories).forEach { (slug, label) ->
                    CategoryChip(label, slug == category, palette) { category = slug }
                }
                CategoryChip("Upcoming events", upcomingOnly, palette) { upcomingOnly = !upcomingOnly }
            }
            FeedLabel(if (organizationsState.loading) "ORGANIZATIONS"
                else "${organizationsState.items.size} ORGANIZATIONS", palette)
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                organizationsState.saveError?.let { InfoNotice(it, error = true) }
                when {
                    organizationsState.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)
                        .padding(24.dp).size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                    organizationsState.error != null -> {
                        InfoNotice(organizationsState.error, error = true)
                        TextButton(onClick = { onSearch(query, category, upcomingOnly) }) { Text("Retry", color = palette.accent) }
                    }
                    organizationsState.items.isEmpty() -> {
                        ViewText("No organizations match", heading = true)
                        ViewText("Try another category, or a different search term.", color = palette.muted)
                    }
                    else -> organizationsState.items.forEach { organization ->
                        OrganizationCard(organization, palette, organizationsState.savingId == null,
                            onSave = { onToggleSave(organization.id) }) {
                            if (!externalDetails) selectedOrganization = organization
                            onOpenOrganization(organization.id, fromSearch)
                        }
                    }
                }
            }
        }
        selectedOrganization?.let { organization ->
            val live = organizationsState.items.firstOrNull { it.id == organization.id } ?: organization
            GroupDetailPresentation(organization.name, organization.category, organization.members,
                organizationColor(organization.color), palette,
                onClose = { selectedOrganization = null; onCloseOrganization() }) {
                if (organizationsState.detailLoading) ViewText("Loading details…", color = palette.muted)
                organizationsState.detailDescription?.let { ViewText(it, color = palette.foreground) }
                organizationsState.detailError?.let {
                    InfoNotice(it, error = true)
                    TextButton(onClick = { onOpenOrganization(organization.id, fromSearch) }) { Text("Retry", color = palette.accent) }
                }
                organizationsState.saveError?.let { InfoNotice(it, error = true) }
                BrandButton(if (live.isSaved) "Saved ✓" else "Save organization",
                    onClick = { onToggleSave(organization.id) }, enabled = organizationsState.savingId == null, secondary = live.isSaved)
            }
        }
        if (showBottomNavigation) BottomNavigation(palette)
    }
}

@Composable
private fun GroupRecommendationsSection(state: GroupRecommendationsUiState, palette: DiscoverPalette,
    onRetry: () -> Unit, onOpenGroup: (Int) -> Unit, onJoinGroup: (Int) -> Unit,
    externalDetails: Boolean = false, onSeeAll: () -> Unit = {}) {
    var selected by remember { mutableStateOf<GroupRecommendation?>(null) }
    val groups = state.result?.items.orEmpty()
    if (state.loading || state.error != null || groups.isNotEmpty()) {
        FeedLabel("RECOMMENDED FOR YOU", palette)
        if (groups.isNotEmpty()) TextButton(onClick = onSeeAll, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("See all →", color = palette.accent)
        }
        when {
            state.loading -> Text("Finding groups for you…", color = palette.muted, fontFamily = viewNunito,
                fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
            state.error != null -> Column(Modifier.padding(horizontal = 24.dp)) {
                Text(state.error, color = palette.foreground, fontFamily = viewNunito, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("Retry", color = palette.accent) }
            }
            else -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.take(5).forEach { group ->
                    CoverCard(group.name, group.category, group.memberCount, organizationColor(group.color),
                        group.reasons.firstOrNull(), palette, group.imageUrl, group.verified) {
                        if (!externalDetails) selected = group
                        onOpenGroup(group.id)
                    }
                }
            }
        }
    }
    selected?.let { group ->
        val joined = group.isMember || group.id in state.joinedGroupIds
        GroupDetailPresentation(group.name, group.category, group.memberCount, categoryColor(group.category),
            palette, onClose = { selected = null }) {
            ViewText(group.description, color = palette.foreground)
            group.reasons.forEach { reason -> StyleChip(reason, palette.accent, palette.secondary) }
            state.actionError?.let { InfoNotice(it, error = true) }
            BrandButton(if (joined) "Joined" else "Join organization", onClick = { onJoinGroup(group.id) },
                enabled = !joined && state.joiningGroupId == null, busy = state.joiningGroupId == group.id, secondary = joined)
        }
    }
}

@Composable
private fun Header(palette: DiscoverPalette, unread: Int = 0, onNotifications: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.senecapp_logo), contentDescription = "SENECApp",
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("UNIANDES · BOGOTÁ", color = palette.muted, fontFamily = viewNunito,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Text("SENECApp", color = palette.foreground, fontFamily = viewBricolage,
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(palette.secondary).clickable(onClick = onNotifications),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = "Notifications",
                tint = palette.foreground, modifier = Modifier.size(18.dp))
            if (unread > 0) Text(if (unread > 9) "9+" else "$unread", color = Color.White,
                fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).background(viewPrimary, RoundedCornerShape(50)).padding(3.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, palette: DiscoverPalette) {
    BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
        textStyle = TextStyle(color = palette.foreground, fontFamily = viewNunito, fontSize = 14.sp),
        cursorBrush = SolidColor(palette.foreground),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp)
            .clip(RoundedCornerShape(16.dp)).background(palette.secondary).padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null,
                    tint = palette.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search organizations...", color = palette.muted, fontFamily = viewNunito, fontSize = 14.sp)
                    inner()
                }
            }
        })
}

@Composable
private fun CoverCard(name: String, category: String, members: Int, color: Color, reason: String?,
                      palette: DiscoverPalette, imageUrl: String? = null, verified: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.width(208.dp).height(144.dp).clip(RoundedCornerShape(24.dp))
        .background(palette.card).clickable(onClick = onClick)) {
        OrganizationImage(name, category, color, Modifier.fillMaxSize(), imageUrl)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,
            if (palette.highContrast) Color.Black else color.copy(alpha = .8f)))))
        if (verified) Text("✓ Official", color = Color.White, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp)).padding(6.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(reason ?: category.uppercase(), color = if (reason != null) Color.White.copy(alpha = .9f) else palette.accent,
                fontFamily = viewNunito, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(name, color = Color.White, fontFamily = viewBricolage, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 17.sp, overflow = TextOverflow.Ellipsis)
            Text("$members members", color = Color.White.copy(alpha = .75f), fontFamily = viewNunito, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OrganizationCard(group: Organization, palette: DiscoverPalette, canSave: Boolean,
                             onSave: () -> Unit, onOpen: () -> Unit) {
    val color = organizationColor(group.color)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.card)
        .border(1.dp, palette.border, RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        OrganizationImage(group.name, group.category, color, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), group.imageUrl)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.name + if (group.verified) " ✓" else "", color = palette.foreground, fontFamily = viewBricolage, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.members} members · ${group.category}", color = palette.muted, fontFamily = viewNunito,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group.hasUpcomingEvent) {
                Spacer(Modifier.height(4.dp))
                StyleChip(group.nextEventStartsAt?.let { com.senecapp.data.eventLabel(it) } ?: "Upcoming events", if (palette.highContrast) palette.accent else color,
                    if (palette.highContrast) palette.secondary else color.copy(alpha = .13f))
            }
        }
        IconButton(onClick = onSave, enabled = canSave, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(34.dp).background(if (group.isSaved) palette.primary.copy(alpha = .13f)
                else palette.secondary, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (group.isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                    contentDescription = if (group.isSaved) "Remove ${group.name} from saved" else "Save ${group.name}",
                    tint = if (group.isSaved) palette.primary else palette.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, palette: DiscoverPalette, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(12.dp))
        .background(if (selected) palette.primary else palette.secondary).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) palette.selectedText else palette.muted,
            fontFamily = viewNunito, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedLabel(text: String, palette: DiscoverPalette) {
    Text(text, color = palette.muted, fontFamily = viewNunito, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp))
}

@Composable
private fun GroupDetailPresentation(name: String, category: String, members: Int, color: Color,
    palette: DiscoverPalette, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                OrganizationImage(name, category, color, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, palette.background))))
                IconButton(onClick = onClose, modifier = Modifier.padding(18.dp).size(40.dp)
                    .background(palette.secondary, RoundedCornerShape(16.dp))) {
                    Icon(painterResource(R.drawable.ic_detail_back), contentDescription = "Back", tint = palette.foreground)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(category.uppercase(), color = palette.accent, fontFamily = viewNunito,
                        fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = palette.foreground, fontFamily = viewBricolage, fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                }
            }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StyleChip("$members members", palette.muted, palette.card)
                SectionHeading("ABOUT")
                content()
            }
        }
    }
}

internal fun categoryColor(category: String): Color = when {
    category.contains("sport", true) -> viewPrimary
    category.contains("business", true) -> Color(0xFFFF6B35)
    category.contains("art", true) -> Color(0xFFA78BFA)
    category.contains("social", true) -> viewTeal
    else -> Color(0xFF3B82F6)
}

@Composable
private fun BottomNavigation(palette: DiscoverPalette, selectedTab: String = "Discover", onNavigate: (String) -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(palette.background)) {
        HorizontalDivider(color = palette.border)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            listOf(R.drawable.ic_home to "Discover", R.drawable.ic_event to "Events",
                R.drawable.ic_group to "My RSOs", R.drawable.ic_person to "Profile").forEach { (icon, label) ->
                val selected = label == selectedTab
                val color = if (selected) palette.primary else palette.muted
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onNavigate(label) }
                    .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(26.dp), contentAlignment = Alignment.TopCenter) {
                        val displayedIcon = if (selected) icon else when (label) {
                            "Discover" -> R.drawable.ic_home_outline
                            "My RSOs" -> R.drawable.ic_group_outline
                            "Profile" -> R.drawable.ic_person_outline
                            else -> icon
                        }
                        Icon(painterResource(displayedIcon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        if (selected) Box(Modifier.align(Alignment.BottomCenter).size(4.dp)
                            .background(palette.primary, RoundedCornerShape(50)))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = color, fontFamily = viewNunito, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AppBottomNavigation(selectedTab: String, highContrast: Boolean, onNavigate: (String) -> Unit) {
    BottomNavigation(if (highContrast) brightLightPalette else standardPalette, selectedTab, onNavigate)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiscoverStandardPreview() { AuthTheme { DiscoverScreen() } }
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiscoverHighContrastPreview() { AuthTheme { DiscoverScreen(highContrast = true) } }
