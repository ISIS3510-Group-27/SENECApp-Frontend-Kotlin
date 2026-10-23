package com.senecapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.senecapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.senecapp.R
import com.senecapp.sensors.hasEventLocationPermission
import com.senecapp.sensors.readSingleEventLocation
import java.time.Instant

@Composable
internal fun EventFeedScreen(state: CampusEventsUiState, saved: Set<Int>, memberIds: Set<Int>,
    onRefresh: () -> Unit, onSave: (Int) -> Unit, onOpen: (CampusEvent) -> Unit,
    onFreeNow: () -> Unit, onItinerary: () -> Unit,
    onScan: (Int, String, EventCoordinates?) -> Unit = { _, _, _ -> }) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All Events") }
    var date by rememberSaveable { mutableStateOf("Any date") }
    var searching by remember { mutableStateOf(false) }
    var dateMenu by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (state.items.isEmpty()) onRefresh() }
    val source = when (filter) {
        "My RSOs" -> state.items.filter { it.groupId in memberIds }
        "Saved" -> state.items.filter { it.id in saved }
        else -> state.items
    }
    val events = filterEvents(source, query, date)
    val now = java.time.ZonedDateTime.now(campusZone)
    val weekEnd = now.toLocalDate().plusDays(7).atStartOfDay(campusZone)
    val week = state.items.count { campusDate(it.startsAt)?.isBefore(weekEnd) == true }
    DemoView("SENECApp Events", showSampleLabel = false, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { SectionHeading("UPCOMING")
                Text("SENECApp Events", color = viewForeground, fontFamily = viewBricolage, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
            FeedAction(R.drawable.ic_search, "Search events") { draft = query; searching = true }
            Box {
                FeedAction(R.drawable.ic_filter, "Filter by date") { dateMenu = true }
                DropdownMenu(dateMenu, { dateMenu = false }) {
                    listOf("Any date", "Today", "Next 7 days").forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) }, onClick = { date = choice; dateMenu = false })
                    }
                }
            }
            ScanEventButton(onScan)
        }
    }) {
        Row(Modifier.fillMaxWidth().background(viewCard, RoundedCornerShape(16.dp))
            .border(1.dp, viewAccent.copy(alpha = .35f), RoundedCornerShape(16.dp)).clickable(onClick = onFreeNow).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(R.drawable.ic_location), null, tint = viewAccent, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text("Free right now?", color = viewForeground, fontFamily = viewBricolage, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Events nearby that fit before your next class", color = viewMuted, fontFamily = viewNunito, fontSize = 12.sp)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = viewMuted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(viewPrimary, Color(0xFFFF6B35))), RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("THIS WEEK", color = viewAccent, fontFamily = viewNunito, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
            Text("$week events from your\ncampus organizations", color = Color.White, fontFamily = viewBricolage, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            val dateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
            Text("${now.format(dateFormat)} – ${weekEnd.minusDays(1).format(dateFormat)}", color = Color.White.copy(alpha = .8f), fontFamily = viewNunito, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All Events", "My RSOs", "Saved").forEach {
                FilterChip(filter == it, { filter = it }, label = { Text(it) })
            }
        }
        if (query.isNotBlank() || date != "Any date") ViewCard {
            ViewText(listOf(query.takeIf { it.isNotBlank() }, date.takeUnless { it == "Any date" }).filterNotNull().joinToString(" · "))
            TextButton(onClick = { query = ""; date = "Any date" }) { Text("Clear filters", color = viewAccent) }
        }
        if (filter == "Saved") BrandButton("Build itinerary", onItinerary, enabled = events.isNotEmpty(), secondary = true)
        SectionHeading("${events.size} EVENTS")
        if (state.loading) CircularProgressIndicator(color = viewAccent)
        state.error?.let { InfoNotice(it, true) }
        if (!state.loading && events.isEmpty()) ViewText("No events match these filters.", color = viewMuted)
        events.forEach { EventRow(it, it.id in saved, { onSave(it.id) }, { onOpen(it) }) }
        TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh events", color = viewAccent) }
    }
    if (searching) AlertDialog(onDismissRequest = { searching = false }, containerColor = viewCard,
        title = { ViewText("Search events", true) }, text = { BrandField(draft, { draft = it }, "Title, organization or place") },
        confirmButton = { TextButton(onClick = { query = draft; searching = false }) { Text("Apply", color = viewAccent) } },
        dismissButton = { TextButton(onClick = { query = ""; searching = false }) { Text("Clear", color = viewMuted) } })
}

@Composable
private fun FeedAction(icon: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).background(viewSecondary, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, tint = viewForeground, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ScanEventButton(onScan: (Int, String, EventCoordinates?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun failed(message: String) { busy = false; Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    FeedAction(R.drawable.ic_qr_scan, "Scan check-in code") {
        val activity = context.findActivity()
        if (!busy && activity != null) {
            busy = true
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { barcode ->
                    val payload = barcode.rawValue?.let(::parseCheckInPayload)
                    if (payload == null) failed("That is not a SENECApp check-in code.")
                    else scope.launch {
                        try {
                            val fix = if (hasEventLocationPermission(context)) readSingleEventLocation(context) else null
                            onScan(payload.eventId, payload.code, fix)
                        } finally { busy = false }
                    }
                }.addOnCanceledListener { busy = false }
                .addOnFailureListener { failed(it.message ?: "Scanner unavailable. Please retry.") }
        }
    }
}

@Composable
internal fun EventRow(event: CampusEvent, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    StyledEventCard(event.title, event.group, eventLabel(event.startsAt), event.building ?: "Campus",
        event.cancelled, event.color, saved, onSave, onOpen)
}

@Composable
internal fun ChoiceMenu(selected: String, options: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$selected ▾", color = viewAccent) }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onChoose(option) }) }
        }
    }
}

@Composable
internal fun EventDetailsScreen(state: CampusEventsUiState, eventId: Int, saved: Boolean, schedule: List<ClassBlock>,
    admin: Boolean, checkIn: EventCheckInViewModel, onBack: () -> Unit, onRetry: () -> Unit,
    onSave: () -> Unit, onGroup: (Int) -> Unit) {
    val event = state.selected?.takeIf { it.id == eventId }
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    DemoView("Event details", showSampleLabel = false, header = { BackLink(onBack) }) {
        if (state.detailLoading) CircularProgressIndicator(color = viewAccent)
        state.detailError?.let { InfoNotice(it, true); BrandButton("Retry", onRetry) }
        event?.let {
            val checkedIn = it.checkedIn || (checkIn.state.eventId == it.id && checkIn.state.result != null)
            val opens = campusDate(it.startsAt)?.minusMinutes(30)?.toInstant()
            val closes = campusDate(it.endsAt)?.plusMinutes(15)?.toInstant()
            val now = Instant.now()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(it.group.uppercase(), color = organizationColor(it.color), fontFamily = viewNunito, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                Text(it.title, color = viewForeground, fontFamily = viewBricolage, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
            }
            if (it.cancelled) InfoNotice("This event has been cancelled.")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EventInfo(R.drawable.ic_event, campusDate(it.startsAt)?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)) ?: it.startsAt)
                EventInfo(R.drawable.ic_clock, listOf(it.startsAt, it.endsAt).joinToString(" – ") { value ->
                    campusDate(value)?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)) ?: value })
                EventInfo(R.drawable.ic_location, listOfNotNull(it.building, it.locationDetail).joinToString(" · ").ifBlank { "Campus" })
                EventInfo(R.drawable.ic_group, "${it.attendeeCount}" + (it.capacity?.let { capacity -> " of $capacity" } ?: "") + " checked in")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Box(Modifier.weight(1f)) { BrandButton(if (saved) "Saved" else "Save event", onSave, secondary = true) }
              IconButton(onClick = {
                val text = listOfNotNull(it.title, "Hosted by ${it.group}", eventLabel(it.startsAt),
                    "Until ${eventLabel(it.endsAt)}", it.building, it.locationDetail,
                    if (it.cancelled) "Cancelled" else null, it.description).joinToString("\n")
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Event", text))
                Toast.makeText(context, "Event details copied", Toast.LENGTH_SHORT).show()
              }, modifier = Modifier.size(54.dp).background(viewSecondary, RoundedCornerShape(16.dp))) {
                  Icon(painterResource(R.drawable.ic_copy), "Copy event details", tint = viewForeground, modifier = Modifier.size(20.dp))
              }
            }
            if (conflictsWithClass(it, schedule)) InfoNotice("This event overlaps with a class in your schedule.")
            when {
                checkedIn -> InfoNotice("✓ You have already checked in to this event.")
                !it.cancelled && !state.detailLoading && state.detailError == null -> {
                    when {
                        opens != null && now < opens -> ViewText("Check-in opens ${eventLabel(opens.atZone(campusZone).toOffsetDateTime().toString())}.", color = viewMuted)
                        closes != null && now > closes -> ViewText("Check-in for this event has closed.", color = viewMuted)
                        else -> EventCheckInSection(it, checkIn.state, checkIn::startScan, checkIn::scanFailed,
                            checkIn::scanCancelled, checkIn::checkIn)
                    }
                }
            }
            if (admin && !it.cancelled) BrandButton("Show check-in QR", { showQr = true })
            it.description?.let { text -> SectionHeading("About"); ViewText(text) }
            SectionHeading("Hosted by")
            Box(Modifier.fillMaxWidth().clickable { onGroup(it.groupId) }) {
                ViewCard { ViewText("${it.group} →", heading = true, color = organizationColor(it.color)) }
            }
        }
    }
    if (showQr && event != null) CheckInQr(event.id) { showQr = false }
}

@Composable
private fun EventInfo(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, tint = viewMuted, modifier = Modifier.size(20.dp))
        ViewText(label, color = viewBody)
    }
}

@Composable
private fun CheckInQr(eventId: Int, onClose: () -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(eventId) {
        try {
            val payload = CampusRepository().checkInCode(eventId)
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 600, 600)
            bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
                val pixels = IntArray(600 * 600) { index -> if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                setPixels(pixels, 0, 600, 0, 0, 600, 600)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not load the check-in code." }
    }
    AlertDialog(onDismissRequest = onClose, containerColor = viewCard, title = { ViewText("Check-in QR", true) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), "Event check-in QR code", Modifier.fillMaxWidth().aspectRatio(1f)) }
                ?: ViewText(error ?: "Loading code…")
            ViewText("Attendees can scan this code in the event details.", color = viewMuted)
        } }, confirmButton = { TextButton(onClick = onClose) { Text("Close", color = viewAccent) } })
}

@Composable
internal fun ItineraryScreen(events: List<CampusEvent>, saved: Set<Int>, onSave: (Int) -> Unit,
    onOpen: (CampusEvent) -> Unit, onBack: () -> Unit) {
    val plan = buildItinerary(events.filter { it.id in saved })
    val skipped = events.filter { it.id in saved && it.id !in plan.map { item -> item.id } }
    DemoView("Saved itinerary", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Saved itinerary", heading = true)
        ViewText("A plan from your saved events, with no overlapping times.", color = viewMuted)
        if (plan.isEmpty()) InfoNotice("Save upcoming events to build your itinerary.")
        plan.forEach { EventRow(it, true, { onSave(it.id) }, { onOpen(it) }) }
        if (skipped.isNotEmpty()) {
            SectionHeading("Not included")
            ViewText("These events overlap, have started, or were cancelled.", color = viewMuted)
            skipped.forEach { EventRow(it, true, { onSave(it.id) }, { onOpen(it) }) }
        }
    }
}
