package com.senecapp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senecapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun NotificationsScreen(home: CampusHomeViewModel, onBack: () -> Unit,
    onOpen: (CampusNotice) -> Unit) {
    DemoView("Notifications", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Notifications", heading = true)
        TextButton(onClick = home::markAllRead, enabled = !home.busy && home.unread > 0) { Text("Mark all as read", color = viewAccent) }
        if (home.loading) CircularProgressIndicator(color = viewAccent)
        home.error?.let { InfoNotice(it, true) }
        val notices = home.notices
        if (notices.isEmpty() && !home.loading) ViewText("You're all caught up!", color = viewMuted)
        notices.forEach { notice ->
            Box(Modifier.fillMaxWidth().clickable(enabled = !home.busy) { home.openNotice(notice) { onOpen(notice) } }) {
                ViewCard {
                    if (notice.unread) StyleChip("NEW", viewAccent)
                    ViewText(notice.title, heading = true)
                    ViewText(notice.body)
                    ViewText(eventLabel(notice.createdAt), color = viewMuted)
                    if (notice.unread) TextButton(onClick = { home.dismiss(notice) }, enabled = !home.busy) { Text("Dismiss", color = viewMuted) }
                }
            }
        }
        BrandButton("Refresh", home::refresh, enabled = !home.loading, secondary = true)
    }
}

@Composable
internal fun GroupFiltersScreen(current: GroupFilters, interests: List<CampusChoice>, buildings: List<CampusChoice>,
    onApply: (GroupFilters) -> Unit, onBack: () -> Unit) {
    var sort by rememberSaveable { mutableStateOf(current.sort) }
    var verified by rememberSaveable { mutableStateOf(current.verified) }
    var tags by remember { mutableStateOf(current.interests) }
    var places by remember { mutableStateOf(current.buildings) }
    DemoView("Filters", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Find your community", heading = true)
        SectionHeading("Sort by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("popular" to "Popular", "newest" to "Newest", "name" to "Name", "upcoming" to "Upcoming").forEach { (key, label) ->
                FilterChip(sort == key, { sort = key }, label = { Text(label) })
            }
        }
        Row { Checkbox(verified, { verified = it }); ViewText("Official RSOs only") }
        SectionHeading("Interests")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            interests.forEach { choice -> FilterChip(choice.id in tags,
                { tags = if (choice.id in tags) tags - choice.id else tags + choice.id }, label = { Text(choice.label) }) }
        }
        SectionHeading("Campus buildings")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            buildings.forEach { choice -> FilterChip(choice.code in places,
                { places = if (choice.code in places) places - choice.code else places + choice.code }, label = { Text(choice.code) }) }
        }
        if (interests.isEmpty() || buildings.isEmpty()) ViewText("Some filters are still loading. Reopen this page after refreshing.", color = viewMuted)
        BrandButton("Apply filters", { onApply(GroupFilters(sort, verified, tags, places)) })
        BrandButton("Reset", { sort = "popular"; verified = false; tags = emptySet(); places = emptySet() }, secondary = true)
    }
}

private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
internal fun ScheduleScreen(home: CampusHomeViewModel, onBack: () -> Unit) {
    var blocks by remember { mutableStateOf(home.schedule) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(home.scheduleLoaded) }
    var dirty by remember { mutableStateOf(false) }
    LaunchedEffect(home.scheduleLoaded) { if (!loaded && home.scheduleLoaded) { blocks = home.schedule; loaded = true } }
    DemoView("Class schedule", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Your weekly classes", heading = true)
        ViewText("Campus time · Bogotá. Your schedule helps us find events when you're free.", color = viewMuted)
        if (!loaded) {
            if (home.loading) CircularProgressIndicator(color = viewAccent)
            else BrandButton("Retry loading schedule", home::refresh)
        }
        home.error?.let { InfoNotice(it, true) }
        if (loaded && blocks.isEmpty()) InfoNotice("No classes added yet. Add your first class below.")
        blocks.withIndex().sortedWith(compareBy({ it.value.weekday }, { it.value.start })).forEach { (index, block) ->
            ViewCard {
                ViewText(block.title.ifBlank { "Class" }, heading = true)
                ViewText("${weekdays[block.weekday]} · ${block.start}–${block.end}", color = viewAccent)
                block.buildingName?.let { ViewText(it, color = viewMuted) }
                Row {
                    TextButton(onClick = { editing = index }, enabled = !home.busy) { Text("Edit", color = viewAccent) }
                    TextButton(onClick = { blocks = blocks.filterIndexed { i, _ -> i != index }; dirty = true }, enabled = !home.busy) { Text("Remove", color = viewPrimary) }
                }
            }
        }
        BrandButton("+ Add class", { editing = -1 }, enabled = loaded && !home.busy && blocks.size < 80, secondary = true)
        BrandButton("Save schedule", { home.saveSchedule(blocks, onBack) }, enabled = loaded && dirty, busy = home.busy)
    }
    editing?.let { index ->
        ClassEditor(blocks.getOrNull(index), home.buildings, onClose = { editing = null }) { block ->
            val updated = blocks.toMutableList().apply { if (index < 0) add(block) else set(index, block) }
            val issue = scheduleError(updated)
            if (issue == null) { blocks = updated; dirty = true; editing = null }
            issue
        }
    }
}

@Composable
private fun ClassEditor(block: ClassBlock?, buildings: List<CampusChoice>, onClose: () -> Unit,
    onSave: (ClassBlock) -> String?) {
    var title by rememberSaveable { mutableStateOf(block?.title.orEmpty()) }
    var day by rememberSaveable { mutableIntStateOf(block?.weekday ?: 0) }
    var start by rememberSaveable { mutableStateOf(block?.start ?: "08:00") }
    var end by rememberSaveable { mutableStateOf(block?.end ?: "09:00") }
    var buildingId by rememberSaveable { mutableStateOf(block?.buildingId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, containerColor = viewCard, title = { ViewText("Class", true) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandField(title, { title = it.take(150) }, "Class name (optional)")
            ChoiceMenu(weekdays[day], weekdays) { day = weekdays.indexOf(it) }
            TimeChoice("Starts", start) { start = it }
            TimeChoice("Ends", end) { end = it }
            BuildingChoice(buildingId, buildings) { buildingId = it }
            error?.let { InfoNotice(it, true) }
        } }, confirmButton = { TextButton(onClick = {
            error = onSave(ClassBlock(weekday = day, start = start, end = end, title = title.trim(), buildingId = buildingId,
                buildingName = buildings.firstOrNull { it.id == buildingId }?.label))
        }) { Text("Add", color = viewAccent) } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = viewMuted) } })
}

@Composable
private fun TimeChoice(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        val parts = value.split(":")
        TimePickerDialog(context, { _, hour, minute -> onChange("%02d:%02d".format(java.util.Locale.US, hour, minute)) },
            parts[0].toInt(), parts[1].toInt(), true).show()
    }) { Text("$label · $value", color = viewAccent) }
}

@Composable
private fun BuildingChoice(id: Int?, buildings: List<CampusChoice>, onChoose: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(buildings.firstOrNull { it.id == id }?.label ?: "Building (optional) ▾", color = viewAccent) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("No building") }, onClick = { onChoose(null); expanded = false })
            buildings.forEach { choice -> DropdownMenuItem(text = { Text("${choice.code} · ${choice.label}") },
                onClick = { onChoose(choice.id); expanded = false }) }
        }
    }
}

@Composable
internal fun CreateEventScreen(home: CampusHomeViewModel, onBack: () -> Unit, onCreated: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableLongStateOf(ZonedDateTime.now(campusZone).plusDays(1).withHour(12).withMinute(0).withSecond(0).toEpochSecond()) }
    var end by rememberSaveable { mutableLongStateOf(start + 3600) }
    var buildingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var location by rememberSaveable { mutableStateOf("") }
    var capacity by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    DemoView("Create event", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Bring your RSO together", heading = true)
        EventSuggestions(home.detail?.group?.id, ((end - start) / 60).toInt(), { suggestedStart, duration ->
            start = suggestedStart; end = start + duration * 60
        }, { id, hour ->
            if (id != null) buildingId = id
            if (hour != null) {
                var next = Instant.ofEpochSecond(start).atZone(campusZone).withHour(hour).withMinute(0)
                if (!next.isAfter(ZonedDateTime.now(campusZone))) next = next.plusDays(1)
                val duration = end - start; start = next.toEpochSecond(); end = start + duration
            }
        })
        BrandField(title, { title = it.take(200) }, "Event title")
        BrandField(description, { description = it.take(2000) }, "Description", singleLine = false)
        DateTimeChoice("Starts", start) { start = it; if (end <= start) end = start + 3600 }
        DateTimeChoice("Ends", end) { end = it }
        BuildingChoice(buildingId, home.buildings) { buildingId = it }
        BrandField(location, { location = it.take(200) }, "Room or meeting point (optional)")
        BrandField(capacity, { capacity = it.filter(Char::isDigit).take(6) }, "Capacity (optional)")
        (error ?: home.error)?.let { InfoNotice(it, true) }
        BrandButton("Create event", {
            error = when {
                title.trim().length < 3 -> "Enter a title with at least 3 characters."
                start <= Instant.now().epochSecond -> "Choose a start time in the future."
                end <= start -> "Choose an end time after the start time."
                capacity.isNotBlank() && (capacity.toIntOrNull() ?: 0) < 1 -> "Capacity must be at least 1."
                else -> null
            }
            if (error == null) home.createEvent(JSONObject().put("title", title.trim()).put("description", description.trim())
                .put("starts_at", Instant.ofEpochSecond(start).atZone(campusZone).toOffsetDateTime().toString())
                .put("ends_at", Instant.ofEpochSecond(end).atZone(campusZone).toOffsetDateTime().toString())
                .put("building_id", buildingId).put("location_detail", location.takeIf { it.isNotBlank() })
                .put("capacity", capacity.toIntOrNull()), onCreated)
        }, busy = home.busy, enabled = !home.detailLoading && home.detail?.group?.role == "admin")
    }
}

@Composable
private fun EventSuggestions(groupId: Int?, minutes: Int, onTime: (Long, Int) -> Unit,
    onPlace: (Int?, Int?) -> Unit) {
    val repository = remember { CampusRepository() }
    val duration = (minutes.coerceIn(30, 240) / 30) * 30
    var times by remember { mutableStateOf<JSONObject?>(null) }
    var audience by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(groupId, duration, refresh) {
        if (groupId == null) return@LaunchedEffect
        times = null; error = null
        try { times = repository.bestTimes(groupId, duration) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load suggested times." }
    }
    LaunchedEffect(groupId, refresh) {
        if (groupId == null) return@LaunchedEffect
        try { audience = repository.audience(groupId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { audience = null }
    }
    SectionHeading("Your members can make it")
    ViewText("Suggested times for a $duration minute event", color = viewMuted)
    times?.let { data ->
        ViewText("Based on ${data.optInt("members_with_schedule")} of ${data.optInt("members")} members' schedules", color = viewMuted)
        val slots = data.getJSONArray("slots").readItems { it }
        if (slots.isEmpty()) InfoNotice("Not enough member availability for suggestions at this duration.")
        slots.forEach { slot ->
            Box(Modifier.clickable {
                campusDate(slot.getString("next_starts_at"))?.let { onTime(it.toEpochSecond(), duration) }
            }) {
                ViewCard {
                    ViewText(eventLabel(slot.getString("next_starts_at")), true)
                    ViewText("${slot.optInt("free_members")} members free · Use →", color = viewTeal)
                }
            }
        }
    } ?: ViewText(error ?: "Loading suggestions…", color = viewMuted)
    if (error != null) TextButton(onClick = { refresh++ }) { Text("Retry", color = viewAccent) }
    SectionHeading("Where students engage")
    audience?.let { data ->
        ViewCard {
            ViewText(data.optString("answer"), color = viewBody)
            val choices = data.optJSONArray("best_time_and_place")?.takeIf { it.length() > 0 }
                ?: data.optJSONArray("by_building")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices?.readItems { it }?.take(3)?.forEach { cell ->
                    val building = cell.optJSONObject("building")
                    val hour = cell.optInt("hour").takeUnless { cell.isNull("hour") }
                    AssistChip(onClick = { onPlace(building?.getInt("id"), hour) }, label = {
                        Text(listOfNotNull(hour?.let { "$it:00" }, building?.optString("code")).joinToString(" · "))
                    })
                }
            }
        }
    } ?: ViewText("Campus activity suggestions are unavailable right now.", color = viewMuted)
}

@Composable
private fun DateTimeChoice(label: String, seconds: Long, onChoose: (Long) -> Unit) {
    val context = LocalContext.current
    val value = Instant.ofEpochSecond(seconds).atZone(campusZone)
    ViewCard {
        SectionHeading(label)
        TextButton(onClick = {
            DatePickerDialog(context, { _, year, month, day -> onChoose(LocalDate.of(year, month + 1, day).atTime(value.toLocalTime()).atZone(campusZone).toEpochSecond()) },
                value.year, value.monthValue - 1, value.dayOfMonth).show()
        }) { Text(value.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), color = viewAccent) }
        TimeChoice("Time", value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) {
            onChoose(value.with(LocalTime.parse(it)).toEpochSecond())
        }
    }
}

@Composable
internal fun AdminRequestsScreen(groupId: Int, onBack: () -> Unit) {
    val repository = remember { CampusRepository() }
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf(emptyList<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(groupId, refresh) {
        loading = true; error = null
        try { requests = repository.adminRequests(groupId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not load requests." }
        finally { loading = false }
    }
    DemoView("Admin requests", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Admin requests", heading = true)
        if (loading) CircularProgressIndicator(color = viewAccent)
        error?.let { InfoNotice(it, true) }
        if (requests.isEmpty() && !loading && error == null) ViewText("No pending requests.", color = viewMuted)
        requests.forEach { request ->
            ViewCard {
                val student = request.getJSONObject("requester")
                ViewText(student.textOrNull("full_name") ?: student.optString("email"), true)
                request.textOrNull("note")?.let { ViewText(it) }
                Row {
                    listOf(true to "Approve", false to "Reject").forEach { (approve, label) ->
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try { repository.resolveAdmin(groupId, request.getInt("id"), approve); refresh++ }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "Could not update the request." }
                                finally { busy = false }
                            }
                        }) { Text(label, color = if (approve) viewAccent else viewPrimary) }
                    }
                }
            }
        }
        TextButton(onClick = { refresh++ }, enabled = !busy && !loading) { Text("Refresh", color = viewAccent) }
    }
}
