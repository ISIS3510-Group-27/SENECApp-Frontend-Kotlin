package com.senecapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.R

private val detailBackground = Color(0xFF171A21)
private val detailForeground = Color(0xFFF0E2E7)
private val detailMuted = Color(0xFF8B94B0)
private val detailAccent = Color(0xFFFFBA08)
private val detailCard = Color(0xFF1E2633)
private val detailSecondary = Color(0xFF1D3557)
private val detailNunito = FontFamily(Font(R.font.nunito))
private val detailBricolage = FontFamily(Font(R.font.bricolage_grotesque))

internal data class OrganizationEventUiModel(
    val title: String,
    val date: String,
    val time: String,
    val location: String,
)

internal data class OrganizationDetailUiModel(
    val name: String,
    val category: String,
    val members: Int,
    val description: String,
    val tags: List<String>,
    val image: Int,
    val color: Color,
    val verified: Boolean,
    val events: List<OrganizationEventUiModel>,
)

private val sampleOrganization = OrganizationDetailUiModel(
    name = "Tennis Uniandes",
    category = "Sports",
    members = 142,
    description = "Competitive and recreational tennis for all levels. Weekly matches, tournaments, and coaching sessions on our campus courts.",
    tags = listOf("Sports", "Competitive", "Outdoor"),
    image = R.drawable.tennis_uniandes,
    color = Color(0xFFA50104),
    verified = true,
    events = listOf(
        OrganizationEventUiModel("Round Robin Tournament", "Sat, Aug 22", "8:00 AM", "Campus Courts"),
    ),
)

@Composable
internal fun OrganizationDetailScreen(
    organization: OrganizationDetailUiModel = sampleOrganization,
    onBack: () -> Unit = {},
) {
    var joined by rememberSaveable(organization.name) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(detailBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
    ) {
        DetailCover(organization, onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailStat(organization.members.toString(), "Members", organization.color, Modifier.weight(1f))
                DetailStat("8", "Events/sem", detailAccent, Modifier.weight(1f))
                DetailStat("4.8", "Rating", Color(0xFF00C9A7), Modifier.weight(1f))
            }

            DetailSectionLabel("ABOUT", Modifier.padding(top = 22.dp, bottom = 8.dp))
            Text(
                organization.description,
                color = Color(0xFFC8CDE0), fontFamily = detailNunito, fontSize = 14.sp, lineHeight = 21.sp,
            )
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                organization.tags.forEach { tag -> DetailTag(tag, organization.color) }
            }

            if (organization.events.isNotEmpty()) {
                DetailSectionLabel("UPCOMING EVENTS", Modifier.padding(top = 24.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    organization.events.forEach { event -> DetailEventCard(event, organization.color) }
                }
            }

            DetailSectionLabel("MEMBERS", Modifier.padding(top = 24.dp, bottom = 10.dp))
            Text(
                "MR    JC    AP    DB    LG    +${(organization.members - 5).coerceAtLeast(0)} more",
                color = detailMuted, fontFamily = detailNunito, fontSize = 13.sp,
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (joined) detailSecondary else organization.color)
                    .clickable { joined = !joined },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (joined) "✓ Joined - Welcome!" else "Join RSO",
                    color = if (joined) detailMuted else Color.White,
                    fontFamily = detailBricolage, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DetailCover(organization: OrganizationDetailUiModel, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(230.dp)) {
        Image(
            painter = painterResource(organization.image), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(detailBackground.copy(alpha = 0.45f), detailBackground)),
            ),
        )
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp).size(42.dp)
                .clip(RoundedCornerShape(13.dp)).background(detailSecondary).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_detail_back), contentDescription = "Back",
                tint = detailForeground, modifier = Modifier.size(22.dp),
            )
        }
        if (organization.verified) {
            Text(
                "✓ Official RSO", color = detailBackground, fontFamily = detailNunito,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
                    .clip(RoundedCornerShape(10.dp)).background(detailAccent)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            Text(
                organization.category.uppercase(), color = detailAccent,
                fontFamily = detailNunito, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                organization.name, color = detailForeground, fontFamily = detailBricolage,
                fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp)).background(detailCard),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = color, fontFamily = detailBricolage, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = detailMuted, fontFamily = detailNunito, fontSize = 10.sp)
    }
}

@Composable
private fun DetailTag(label: String, color: Color) {
    Text(
        label, color = color, fontFamily = detailNunito, fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun DetailSectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label, color = detailMuted, fontFamily = detailNunito,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier,
    )
}

@Composable
private fun DetailEventCard(event: OrganizationEventUiModel, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(detailCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("◷", color = color, fontSize = 27.sp)
        }
        Column(Modifier.padding(start = 13.dp)) {
            Text(
                event.title, color = detailForeground, fontFamily = detailBricolage,
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text("${event.date} · ${event.time}", color = detailMuted, fontFamily = detailNunito, fontSize = 12.sp)
            Text(event.location, color = detailMuted, fontFamily = detailNunito, fontSize = 12.sp)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OrganizationDetailPreview() {
    OrganizationDetailScreen()
}
