package com.senecapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.R
import com.senecapp.data.Organization
import com.senecapp.data.GroupRecommendation
import kotlinx.coroutines.delay

private data class DiscoverPalette(
    val background: Color,
    val foreground: Color,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
    val card: Color,
    val muted: Color,
    val selectedText: Color,
    val border: Color,
    val highContrast: Boolean,
)

private val standardPalette = DiscoverPalette(
    background = Color(0xFF171A21),
    foreground = Color(0xFFF0E2E7),
    primary = Color(0xFFA50104),
    accent = Color(0xFFFFBA08),
    secondary = Color(0xFF1D3557),
    card = Color(0xFF1E2633),
    muted = Color(0xFF8B94B0),
    selectedText = Color.White,
    border = Color.White.copy(alpha = 0.06f),
    highContrast = false,
)

private val brightLightPalette = DiscoverPalette(
    background = Color.Black,
    foreground = Color.White,
    primary = Color(0xFFFFD54F),
    accent = Color(0xFFFFD54F),
    secondary = Color(0xFF243550),
    card = Color(0xFF101A2A),
    muted = Color(0xFFE3E7F2),
    selectedText = Color.Black,
    border = Color.White.copy(alpha = 0.55f),
    highContrast = true,
)

private val nunito = FontFamily(Font(R.font.nunito))
private val bricolage = FontFamily(Font(R.font.bricolage_grotesque))

private val previewOrganizations = listOf(
    Organization(1, "Tennis Uniandes", "Sports", "sports", 142, "#A50104", true),
    Organization(2, "Emprendedores Uniandes", "Business", "business", 318, "#FF6B35", true),
    Organization(3, "AI & Machine Learning", "Technology", "technology", 256, "#3B82F6", true),
)

@Composable
fun DiscoverScreen(
    showBottomNavigation: Boolean = true,
    highContrast: Boolean = false,
    organizationsState: OrganizationsUiState = OrganizationsUiState(
        loading = false,
        items = previewOrganizations,
        categories = previewOrganizations.map { it.categorySlug to it.category },
    ),
    onSearch: (String, String?) -> Unit = { _, _ -> },
    recommendationsState: GroupRecommendationsUiState = GroupRecommendationsUiState(loading = false),
    onLoadRecommendations: () -> Unit = {},
    onOpenRecommendedGroup: (Int) -> Unit = {},
    onJoinRecommendedGroup: (Int) -> Unit = {},
) {
    val palette = if (highContrast) brightLightPalette else standardPalette
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(query, category) {
        delay(350)
        onSearch(query, category)
    }
    LaunchedEffect(query.isBlank() && category == null) {
        if (query.isBlank() && category == null) onLoadRecommendations()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Header(palette)
            SearchField(query = query, onQueryChange = { query = it }, palette = palette)

            if (query.isBlank() && category == null) {
                GroupRecommendationsSection(
                    recommendationsState, palette, onLoadRecommendations,
                    onOpenRecommendedGroup, onJoinRecommendedGroup,
                )
            }

            if (query.isBlank() && category == null && organizationsState.items.isNotEmpty()) {
                SectionLabel("FEATURED", palette, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.width(12.dp))
                    organizationsState.items.take(5).forEach { FeaturedCard(it, palette) }
                    Spacer(Modifier.width(12.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 20.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf(null to "All") + organizationsState.categories).forEach { (slug, label) ->
                    CategoryChip(label, selected = slug == category, palette = palette) { category = slug }
                }
                Spacer(Modifier.width(16.dp))
            }

            SectionLabel(
                if (organizationsState.loading) "LOADING ORGANIZATIONS" else "${organizationsState.items.size} ORGANIZATIONS",
                palette, Modifier.padding(start = 24.dp, bottom = 10.dp),
            )
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (organizationsState.loading) {
                    Text("Loading organizations...", color = palette.muted, fontFamily = nunito, fontSize = 14.sp)
                } else if (organizationsState.error != null) {
                    Text(organizationsState.error, color = palette.foreground, fontFamily = nunito, fontSize = 14.sp)
                    Text("Retry", color = palette.accent, fontFamily = nunito, fontSize = 14.sp,
                        modifier = Modifier.clickable { onSearch(query, category) })
                } else if (organizationsState.items.isEmpty()) {
                    Text("No organizations match your search.", color = palette.muted, fontFamily = nunito, fontSize = 14.sp)
                }
                if (!organizationsState.loading && organizationsState.error == null) {
                    organizationsState.items.forEach { OrganizationCard(it, palette) }
                }
            }
        }
        if (showBottomNavigation) BottomNavigation(palette)
    }
}

@Composable
private fun GroupRecommendationsSection(
    state: GroupRecommendationsUiState,
    palette: DiscoverPalette,
    onRetry: () -> Unit,
    onOpenGroup: (Int) -> Unit,
    onJoinGroup: (Int) -> Unit,
) {
    var selected by remember { mutableStateOf<GroupRecommendation?>(null) }
    val groups = state.result?.items.orEmpty()
    SectionLabel("FOR YOU", palette, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
    when {
        state.loading -> Text("Finding groups for you...", color = palette.muted, fontFamily = nunito,
            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
        state.error != null -> Column(Modifier.padding(horizontal = 24.dp)) {
            Text(state.error, color = palette.foreground, fontFamily = nunito, fontSize = 13.sp)
            Text("Retry", color = palette.accent, fontFamily = nunito, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 6.dp))
        }
        groups.isEmpty() -> Text("No suggestions available yet.", color = palette.muted,
            fontFamily = nunito, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
        else -> Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.width(12.dp))
            groups.forEach { group ->
                Column(
                    modifier = Modifier.width(225.dp).clip(RoundedCornerShape(16.dp))
                        .background(palette.card).border(1.dp, palette.border, RoundedCornerShape(16.dp))
                        .clickable { selected = group; onOpenGroup(group.id) }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(group.category.uppercase(), color = palette.accent, fontFamily = nunito,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(group.name, color = palette.foreground, fontFamily = bricolage,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(group.reasons.firstOrNull() ?: "Suggested for you", color = palette.muted,
                        fontFamily = nunito, fontSize = 12.sp, maxLines = 2)
                    Text("${group.memberCount} members", color = palette.muted, fontFamily = nunito,
                        fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
    }

    selected?.let { group ->
        val joined = group.isMember || group.id in state.joinedGroupIds
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(group.name, color = palette.foreground, fontFamily = bricolage) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.description, color = palette.foreground, fontFamily = nunito,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                    group.reasons.forEach { reason ->
                        Text("• $reason", color = palette.muted, fontFamily = nunito)
                    }
                    state.actionError?.let { error ->
                        Text(error, color = palette.accent, fontFamily = nunito)
                    }
                    if (joined) Text("You joined this group.", color = palette.accent, fontFamily = nunito)
                }
            },
            confirmButton = {
                TextButton(onClick = { onJoinGroup(group.id) }, enabled = !joined && state.joiningGroupId == null) {
                    Text(if (joined) "Joined" else if (state.joiningGroupId == group.id) "Joining..." else "Join group")
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            },
            containerColor = palette.card,
        )
    }
}

@Composable
private fun Header(palette: DiscoverPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.senecapp_logo),
            contentDescription = "SENECApp",
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("UNIANDES - BOGOTA", color = palette.muted, fontFamily = nunito, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("SENECApp", color = palette.foreground, fontFamily = bricolage, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(palette.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = "Notifications", tint = palette.foreground, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, palette: DiscoverPalette) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = palette.foreground, fontFamily = nunito, fontSize = 14.sp),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp)
            .clip(RoundedCornerShape(16.dp)).background(palette.secondary).padding(horizontal = 16.dp),
        decorationBox = { innerTextField ->
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = palette.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box {
                    if (query.isEmpty()) Text("Search organizations...", color = palette.muted, fontFamily = nunito, fontSize = 14.sp)
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun FeaturedCard(organization: Organization, palette: DiscoverPalette) {
    val organizationColor = organization.displayColor()
    Box(
        modifier = Modifier.width(220.dp).height(145.dp).clip(RoundedCornerShape(20.dp)).background(palette.card),
    ) {
        OrganizationArtwork(organization, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        if (palette.highContrast) Color.Black else organizationColor.copy(alpha = 0.9f),
                    ),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(organization.category.uppercase(), color = palette.accent, fontFamily = nunito, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                organization.name, color = Color.White, fontFamily = bricolage, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${organization.members} members", color = Color(0xFFD7D8DE), fontFamily = nunito, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, palette: DiscoverPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(38.dp).clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.primary else palette.secondary).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) palette.selectedText else palette.muted, fontFamily = nunito, fontSize = 12.sp)
    }
}

@Composable
private fun OrganizationCard(organization: Organization, palette: DiscoverPalette) {
    val organizationColor = organization.displayColor()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrganizationArtwork(organization, Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                organization.name, color = palette.foreground, fontFamily = bricolage, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${organization.members} members · ${organization.category}", color = palette.muted, fontFamily = nunito, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                if (organization.hasUpcomingEvent) "Upcoming events" else "No upcoming events",
                color = if (palette.highContrast) palette.accent else organizationColor,
                fontFamily = nunito, fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (palette.highContrast) palette.secondary else organizationColor.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

private fun Organization.displayColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(color ?: "")) }.getOrDefault(Color(0xFF3B82F6))

@Composable
private fun OrganizationArtwork(organization: Organization, modifier: Modifier) {
    val image = when (organization.name) {
        "Tennis Uniandes" -> R.drawable.tennis_uniandes
        "Emprendedores Uniandes" -> R.drawable.emprendedores_uniandes
        "AI & Machine Learning" -> R.drawable.ai_machine_learning
        else -> null
    }
    if (image != null) {
        Image(painterResource(image), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(organization.displayColor()), contentAlignment = Alignment.Center) {
            Text(organization.name.take(1).uppercase(), color = Color.White, fontFamily = bricolage,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(label: String, palette: DiscoverPalette, modifier: Modifier = Modifier) {
    Text(label, color = palette.muted, fontFamily = nunito, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun BottomNavigation(palette: DiscoverPalette, selectedTab: String = "Discover", onNavigate: (String) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).background(palette.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(R.drawable.ic_home, "Discover", selectedTab == "Discover"),
            Triple(R.drawable.ic_event, "Events", selectedTab == "Events"),
            Triple(R.drawable.ic_group, "My RSOs", false),
            Triple(R.drawable.ic_person, "Profile", selectedTab == "Profile"),
        ).forEach { (icon, label, selected) ->
            Column(
                modifier = Modifier.weight(1f).clickable(enabled = label != "My RSOs") { onNavigate(label) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = if (selected) palette.primary else palette.muted, modifier = Modifier.size(24.dp))
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) palette.primary else Color.Transparent))
                Text(label, color = if (selected) palette.primary else palette.muted, fontFamily = nunito, fontSize = 10.sp)
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
private fun DiscoverStandardPreview() {
    DiscoverScreen()
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiscoverHighContrastPreview() {
    DiscoverScreen(highContrast = true)
}
