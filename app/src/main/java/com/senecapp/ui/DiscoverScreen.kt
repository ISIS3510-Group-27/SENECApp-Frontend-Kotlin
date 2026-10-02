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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.R

private val background = Color(0xFF171A21)
private val foreground = Color(0xFFF0E2E7)
private val primary = Color(0xFFA50104)
private val accent = Color(0xFFFFBA08)
private val secondary = Color(0xFF1D3557)
private val card = Color(0xFF1E2633)
private val muted = Color(0xFF8B94B0)
private val nunito = FontFamily(Font(R.font.nunito))
private val bricolage = FontFamily(Font(R.font.bricolage_grotesque))

private data class Organization(
    val name: String,
    val category: String,
    val members: Int,
    val image: Int,
    val color: Color,
)

private val organizations = listOf(
    Organization("Tennis Uniandes", "Sports", 142, R.drawable.tennis_uniandes, primary),
    Organization("Emprendedores Uniandes", "Business", 318, R.drawable.emprendedores_uniandes, Color(0xFFFF6B35)),
    Organization("AI & Machine Learning", "Technology", 256, R.drawable.ai_machine_learning, Color(0xFF3B82F6)),
)

@Composable
fun DiscoverScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    val visible = organizations.filter {
        (category == "All" || it.category == category) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Header()
            SearchField(query = query, onQueryChange = { query = it })

            if (query.isBlank() && category == "All") {
                SectionLabel("FEATURED", Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.width(12.dp))
                    organizations.forEach { FeaturedCard(it) }
                    Spacer(Modifier.width(12.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 20.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Sports", "Business", "Technology").forEach { option ->
                    CategoryChip(option, selected = option == category) { category = option }
                }
                Spacer(Modifier.width(16.dp))
            }

            SectionLabel("${visible.size} ORGANIZATIONS", Modifier.padding(start = 24.dp, bottom = 10.dp))
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) {
                    Text("No organizations match your search.", color = muted, fontFamily = nunito, fontSize = 14.sp)
                }
                visible.forEach { OrganizationCard(it) }
            }
        }
        BottomNavigation()
    }
}

@Composable
private fun Header() {
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
            Text("UNIANDES - BOGOTA", color = muted, fontFamily = nunito, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("SENECApp", color = foreground, fontFamily = bricolage, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = "Notifications", tint = foreground, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = foreground, fontFamily = nunito, fontSize = 14.sp),
        cursorBrush = SolidColor(accent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp)
            .clip(RoundedCornerShape(16.dp)).background(secondary).padding(horizontal = 16.dp),
        decorationBox = { innerTextField ->
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box {
                    if (query.isEmpty()) Text("Search organizations...", color = muted, fontFamily = nunito, fontSize = 14.sp)
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun FeaturedCard(organization: Organization) {
    Box(
        modifier = Modifier.width(220.dp).height(145.dp).clip(RoundedCornerShape(20.dp)).background(card),
    ) {
        Image(
            painter = painterResource(organization.image),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, organization.color.copy(alpha = 0.9f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(organization.category.uppercase(), color = accent, fontFamily = nunito, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                organization.name, color = Color.White, fontFamily = bricolage, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${organization.members} members", color = Color(0xFFD7D8DE), fontFamily = nunito, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(38.dp).clip(RoundedCornerShape(11.dp))
            .background(if (selected) primary else secondary).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else muted, fontFamily = nunito, fontSize = 12.sp)
    }
}

@Composable
private fun OrganizationCard(organization: Organization) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(organization.image),
            contentDescription = null,
            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                organization.name, color = foreground, fontFamily = bricolage, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${organization.members} members · ${organization.category}", color = muted, fontFamily = nunito, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                "Upcoming events", color = organization.color, fontFamily = nunito, fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(organization.color.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(label, color = muted, fontFamily = nunito, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun BottomNavigation() {
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(R.drawable.ic_home, "Discover", true),
            Triple(R.drawable.ic_event, "Events", false),
            Triple(R.drawable.ic_group, "My RSOs", false),
            Triple(R.drawable.ic_person, "Profile", false),
        ).forEach { (icon, label, selected) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = if (selected) primary else muted, modifier = Modifier.size(24.dp))
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) primary else Color.Transparent))
                Text(label, color = if (selected) primary else muted, fontFamily = nunito, fontSize = 10.sp)
            }
        }
    }
}
