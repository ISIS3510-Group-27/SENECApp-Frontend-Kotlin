package com.senecapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.R
import java.text.Normalizer
import java.util.Locale

// Mirrors Flutter's AppColors, AppRadius and AppTheme.
internal val viewBackground = Color(0xFF171A21)
internal val viewForeground = Color(0xFFF0E2E7)
internal val viewMuted = Color(0xFF8B94B0)
internal val viewBody = Color(0xFFC8CDE0)
internal val viewAccent = Color(0xFFFFBA08)
internal val viewCard = Color(0xFF1E2633)
internal val viewSecondary = Color(0xFF1D3557)
internal val viewPrimary = Color(0xFFA50104)
internal val viewBorder = Color.White.copy(alpha = .06f)
internal val viewTeal = Color(0xFF00C9A7)
internal val viewNunito = FontFamily(Font(R.font.nunito))
internal val viewBricolage = FontFamily(Font(R.font.bricolage_grotesque))

@Composable
internal fun ViewText(text: String, heading: Boolean = false, color: Color = viewForeground) {
    Text(text, color = color, fontFamily = if (heading) viewBricolage else viewNunito,
        fontSize = if (heading) 18.sp else 14.sp, lineHeight = if (heading) 23.sp else 20.sp,
        fontWeight = if (heading) FontWeight.ExtraBold else FontWeight.Normal)
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = viewMuted, fontFamily = viewNunito,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
}

@Composable
internal fun PageHeader(title: String, overline: String, mascot: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionHeading(overline)
            Text(title, color = viewForeground, fontFamily = viewBricolage,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 29.sp)
        }
        if (mascot != null) Image(painterResource(mascot), contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun DemoView(title: String, showSampleLabel: Boolean = true,
                      header: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(viewBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState())
        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (header != null) header() else PageHeader(title, when (title) {
            "My RSOs" -> "MY ORGANIZATIONS"
            "Profile" -> "SENECAPP · STUDENT"
            "SENECApp Events" -> "UPCOMING"
            else -> "SENECAPP · UNIANDES"
        }, when (title) {
            "My RSOs" -> R.drawable.senecapp_67
            "Profile" -> R.drawable.senecapp_hat
            else -> null
        })
        if (showSampleLabel) SectionHeading("DEMO · Sample data only")
        content()
    }
}

@Composable
internal fun ViewCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(viewCard)
        .border(1.dp, viewBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun BrandButton(text: String, onClick: () -> Unit, enabled: Boolean = true,
                         secondary: Boolean = false, busy: Boolean = false) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) viewSecondary else viewPrimary, contentColor = viewForeground,
            disabledContainerColor = viewSecondary, disabledContentColor = viewMuted)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = viewForeground)
        else Text(text, fontFamily = viewBricolage, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BrandField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String = "",
                        enabled: Boolean = true, singleLine: Boolean = true, isError: Boolean = false,
                        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
                        visualTransformation: VisualTransformation = VisualTransformation.None,
                        trailingIcon: @Composable (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(label)
        OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled,
            singleLine = singleLine, minLines = if (singleLine) 1 else 4, isError = isError,
            placeholder = { Text(placeholder, color = viewMuted, fontFamily = viewNunito, fontSize = 14.sp) },
            textStyle = TextStyle(color = viewForeground, fontFamily = viewNunito, fontSize = 14.sp),
            keyboardOptions = keyboardOptions, visualTransformation = visualTransformation, trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = viewCard, unfocusedContainerColor = viewCard, disabledContainerColor = viewCard,
                focusedBorderColor = viewForeground.copy(alpha = .5f), unfocusedBorderColor = Color.White.copy(alpha = .08f),
                errorBorderColor = viewPrimary, cursorColor = viewForeground))
    }
}

@Composable
internal fun AuthPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(viewBackground).windowInsetsPadding(WindowInsets.safeDrawing)
        .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Image(painterResource(R.drawable.senecapp_logo), contentDescription = "SENECApp",
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(20.dp))
            SectionHeading("UNIANDES · BOGOTÁ")
            Text(title, color = viewForeground, fontFamily = viewBricolage,
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontFamily = viewNunito, fontSize = 14.sp, lineHeight = 20.sp, color = viewBody)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
internal fun InfoNotice(text: String, error: Boolean = false) {
    val color = if (error) viewPrimary else viewAccent
    Box(Modifier.fillMaxWidth().background(color.copy(alpha = if (error) .18f else .08f), RoundedCornerShape(16.dp))
        .border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(text, color = if (error) viewForeground else viewAccent, fontFamily = viewNunito,
            fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StyleChip(text: String, color: Color = viewMuted, background: Color = viewSecondary) {
    Text(text, color = color, fontFamily = viewNunito, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(background, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
}

internal fun organizationColor(value: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(value.orEmpty()))
}.getOrDefault(Color(0xFF3B82F6))

// Same bundled art and name-derived slugs as Flutter. Remote URLs need model changes, deferred.
@Composable
internal fun OrganizationImage(name: String, category: String, color: Color, modifier: Modifier = Modifier) {
    val slug = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT).replace("&", "").replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val image = when (slug) {
        "tennis_uniandes" -> R.drawable.tennis_uniandes
        "emprendedores_uniandes" -> R.drawable.emprendedores_uniandes
        "ai_machine_learning" -> R.drawable.ai_machine_learning
        "auto_enthusiasts" -> R.drawable.auto_enthusiasts
        "finance_society" -> R.drawable.finance_society
        "fotografia_uniandes" -> R.drawable.fotografia_uniandes
        "teatro_los_andes" -> R.drawable.teatro_los_andes
        "viajeros_uniandes" -> R.drawable.viajeros_uniandes
        else -> null
    }
    if (image != null) Image(painterResource(image), contentDescription = null, modifier = modifier,
        contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(color.copy(alpha = .55f), color.copy(alpha = .18f), viewCard))),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(if (category.lowercase().contains("art")) R.drawable.ic_star else R.drawable.ic_group),
            contentDescription = null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(28.dp))
    }
}

@Composable
internal fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(viewCard, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = color, fontFamily = viewBricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(label, color = viewMuted, fontFamily = viewNunito, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
