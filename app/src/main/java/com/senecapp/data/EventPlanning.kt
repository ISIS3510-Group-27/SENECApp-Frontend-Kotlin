package com.senecapp.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val campusZone: ZoneId = ZoneId.of("America/Bogota")
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
    val terms = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val today = now.toLocalDate()
    return events.filter { event ->
        val haystack = "${event.title} ${event.group} ${event.building.orEmpty()} ${event.locationDetail.orEmpty()}".lowercase()
        val day = campusDate(event.startsAt)?.toLocalDate()
        terms.all { it in haystack } && when (date) {
            "Today" -> !event.cancelled && day == today
            "Next 7 days" -> !event.cancelled && day != null && !day.isBefore(today) && day.isBefore(today.plusDays(7))
            else -> true
        }
    }.sortedWith(compareBy({ campusDate(it.startsAt)?.toInstant() ?: Instant.MAX }, { it.id }))
}

// Choose the largest set of events that can be attended without overlapping.
internal fun buildItinerary(events: List<CampusEvent>, now: Instant = Instant.now()): List<CampusEvent> {
    var available = now
    return events.filter { !it.cancelled && campusDate(it.startsAt)?.toInstant()?.isAfter(now) == true &&
        campusDate(it.endsAt)?.toInstant()?.isAfter(campusDate(it.startsAt)?.toInstant()) == true }
        .sortedWith(compareBy({ campusDate(it.endsAt)?.toInstant() }, { campusDate(it.startsAt)?.toInstant() }, { it.id }))
        .filter { event ->
            val start = campusDate(event.startsAt)!!.toInstant()
            if (start < available) false else { available = campusDate(event.endsAt)!!.toInstant(); true }
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
