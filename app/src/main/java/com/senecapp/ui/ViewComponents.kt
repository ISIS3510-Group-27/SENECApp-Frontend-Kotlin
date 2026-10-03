package com.senecapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.R

internal val viewBackground = Color(0xFF171A21)
internal val viewForeground = Color(0xFFF0E2E7)
internal val viewMuted = Color(0xFF8B94B0)
internal val viewAccent = Color(0xFFFFBA08)
internal val viewCard = Color(0xFF1E2633)
internal val viewSecondary = Color(0xFF1D3557)
internal val viewPrimary = Color(0xFFA50104)
internal val viewNunito = FontFamily(Font(R.font.nunito))
internal val viewBricolage = FontFamily(Font(R.font.bricolage_grotesque))

@Composable
internal fun ViewText(text: String, heading: Boolean = false, color: Color = viewForeground) {
    Text(text, color = color, fontFamily = if (heading) viewBricolage else viewNunito,
        fontSize = if (heading) 22.sp else 14.sp,
        fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal)
}

@Composable
internal fun DemoView(
    title: String,
    showSampleLabel: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(viewBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ViewText("SENECAPP · UNIANDES", color = viewMuted)
        ViewText(title, heading = true)
        if (showSampleLabel) {
            Text("DEMO · Sample data only", color = viewAccent, fontFamily = viewNunito, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
internal fun ViewCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(viewCard, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
