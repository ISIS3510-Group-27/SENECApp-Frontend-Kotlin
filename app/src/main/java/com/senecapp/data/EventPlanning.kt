package com.senecapp.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val campusZone: ZoneId = ZoneId.of("America/Bogota")
private val searchWhitespace = Regex("\\s+")
internal fun campusDate(value: String): ZonedDateTime? = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(campusZone)
}.getOrNull()
internal fun eventLabel(value: String): String = campusDate(value)
    ?.format(DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)) ?: value
internal fun semesterLabel(now: ZonedDateTime = ZonedDateTime.now(campusZone)) = if (now.monthValue < 7) "Sem I" else "Sem II"
internal fun semesterStart(now: ZonedDateTime = ZonedDateTime.now(campusZone)): String =
    LocalDate.of(now.year, if (now.monthValue < 7) 1 else 7, 1).atStartOfDay(campusZone).toOffsetDateTime().toString()

internal fun filterEvents(events: List<CampusEvent>, query: String, date: String,
    now: ZonedDateTime = ZonedDateTime.now(campusZone)): List<CampusEvent> {
    val terms = query.lowercase().trim().split(searchWhitespace).filter { it.isNotBlank() }
    val today = now.toLocalDate()
    val weekEnd = today.plusDays(7)
    val dateRestricted = date == "Today" || date == "Next 7 days"
    val matches = ArrayList<Pair<CampusEvent, Instant>>()
    for (event in events) {
        if (dateRestricted && event.cancelled) continue
        if (terms.isNotEmpty()) {
            val haystack = "${event.title} ${event.group} ${event.building.orEmpty()} ${event.locationDetail.orEmpty()}".lowercase()
            if (terms.any { it !in haystack }) continue
        }
        val start = campusDate(event.startsAt)
        if (dateRestricted) {
            val day = start?.toLocalDate() ?: continue
            if (date == "Today" && day != today) continue
            if (date == "Next 7 days" && (day.isBefore(today) || !day.isBefore(weekEnd))) continue
        }
        matches.add(event to (start?.toInstant() ?: Instant.MAX))
    }
    matches.sortWith(compareBy({ it.second }, { it.first.id }))
    return matches.map { it.first }
}

private data class ItineraryCandidate(val event: CampusEvent, val start: Instant, val end: Instant)

// Choose the largest set of events that can be attended without overlapping.
internal fun buildItinerary(events: List<CampusEvent>, now: Instant = Instant.now()): List<CampusEvent> {
    val candidates = ArrayList<ItineraryCandidate>()
    for (event in events) {
        if (event.cancelled) continue
        val start = campusDate(event.startsAt)?.toInstant() ?: continue
        if (!start.isAfter(now)) continue
        val end = campusDate(event.endsAt)?.toInstant() ?: continue
        if (end.isAfter(start)) candidates.add(ItineraryCandidate(event, start, end))
    }
    candidates.sortWith(compareBy({ it.end }, { it.start }, { it.event.id }))

    var available = now
    return buildList {
        for (candidate in candidates) {
            if (candidate.start < available) continue
            add(candidate.event)
            available = candidate.end
        }
    }
}

internal fun conflictsWithClass(event: CampusEvent, blocks: List<ClassBlock>): Boolean {
    val start = campusDate(event.startsAt) ?: return false
    val end = campusDate(event.endsAt) ?: return false
    if (!end.isAfter(start)) return false
    var day = start.toLocalDate()
    while (!day.isAfter(end.toLocalDate())) {
        if (blocks.any { block ->
            block.weekday == day.dayOfWeek.value - 1 && runCatching {
                val classStart = day.atTime(LocalTime.parse(block.start)).atZone(campusZone)
                val classEnd = day.atTime(LocalTime.parse(block.end)).atZone(campusZone)
                start < classEnd && classStart < end
            }.getOrDefault(false)
        }) return true
        day = day.plusDays(1)
    }
    return false
}

internal fun scheduleError(blocks: List<ClassBlock>): String? {
    if (blocks.size > 80) return "You can add up to 80 classes."
    for ((index, block) in blocks.withIndex()) {
        val start = runCatching { LocalTime.parse(block.start) }.getOrNull()
        val end = runCatching { LocalTime.parse(block.end) }.getOrNull()
        if (block.weekday !in 0..6 || start == null || end == null || !end.isAfter(start))
            return "Choose a valid day and an end time after the start time."
        if (blocks.drop(index + 1).any { it.weekday == block.weekday && it.start < block.end && block.start < it.end })
            return "Two classes overlap. Adjust their times before saving."
    }
    return null
}
