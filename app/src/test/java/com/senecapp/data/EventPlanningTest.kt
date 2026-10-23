package com.senecapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

class EventPlanningTest {
    private val now = ZonedDateTime.parse("2026-10-23T08:00:00-05:00[America/Bogota]")
    private fun event(id: Int, start: String, end: String, cancelled: Boolean = false) = CampusEvent(
        id, "Campus meetup", "AI Club", start, "ML", null, cancelled, endsAt = end)

    @Test fun `search matches every term across title group and building`() {
        val events = listOf(event(1, "2026-10-23T14:00:00Z", "2026-10-23T15:00:00Z"))
        assertEquals(listOf(1), filterEvents(events, " AI  ML ", "Any date", now).map { it.id })
        assertTrue(filterEvents(events, "AI tennis", "Any date", now).isEmpty())
    }
    @Test fun `today uses Bogota date and omits cancelled events`() {
        val events = listOf(event(1, "2026-10-24T02:00:00Z", "2026-10-24T03:00:00Z"),
            event(2, "2026-10-24T05:00:00Z", "2026-10-24T06:00:00Z"),
            event(3, "2026-10-23T14:00:00Z", "2026-10-23T15:00:00Z", true))
        assertEquals(listOf(1), filterEvents(events, "", "Today", now).map { it.id })
        assertEquals(3, filterEvents(events, "", "Any date", now).size)
    }
    @Test fun `seven day filter ends at midnight after seventh campus day`() {
        val events = listOf(event(1, "2026-10-29T23:00:00-05:00", "2026-10-30T00:00:00-05:00"),
            event(2, "2026-10-30T00:00:00-05:00", "2026-10-30T01:00:00-05:00"))
        assertEquals(listOf(1), filterEvents(events, "", "Next 7 days", now).map { it.id })
    }
    @Test fun `itinerary maximizes full events rather than selecting longest first`() {
        val events = listOf(event(1, "2026-10-23T09:00:00-05:00", "2026-10-23T13:00:00-05:00"),
            event(2, "2026-10-23T09:00:00-05:00", "2026-10-23T10:00:00-05:00"),
            event(3, "2026-10-23T10:00:00-05:00", "2026-10-23T11:00:00-05:00"),
            event(4, "2026-10-23T11:00:00-05:00", "2026-10-23T12:00:00-05:00"))
        assertEquals(listOf(2, 3, 4), buildItinerary(events, now.toInstant()).map { it.id })
    }
    @Test fun `itinerary omits ongoing cancelled and invalid events with stable ties`() {
        val events = listOf(event(1, "2026-10-23T07:00:00-05:00", "2026-10-23T10:00:00-05:00"),
            event(2, "2026-10-23T09:00:00-05:00", "2026-10-23T10:00:00-05:00", true),
            event(3, "bad", "bad"), event(4, "2026-10-23T09:00:00-05:00", "2026-10-23T09:00:00-05:00"),
            event(6, "2026-10-23T10:00:00-05:00", "2026-10-23T11:00:00-05:00"),
            event(5, "2026-10-23T10:00:00-05:00", "2026-10-23T11:00:00-05:00"))
        assertEquals(listOf(5), buildItinerary(events, now.toInstant()).map { it.id })
    }
    @Test fun `class conflict includes overnight events and allows adjacent times`() {
        val block = ClassBlock(weekday = 4, start = "08:00", end = "09:00", title = "Math")
        assertTrue(conflictsWithClass(event(1, "2026-10-22T23:00:00-05:00", "2026-10-23T08:30:00-05:00"), listOf(block)))
        assertFalse(conflictsWithClass(event(2, "2026-10-23T09:00:00-05:00", "2026-10-23T10:00:00-05:00"), listOf(block)))
        assertFalse(conflictsWithClass(event(3, "2026-10-23T08:30:00-05:00", "2026-10-23T08:30:00-05:00"), listOf(block)))
    }
    @Test fun `schedule rejects bad time ranges and overlapping classes`() {
        val a = ClassBlock(weekday = 0, start = "08:00", end = "09:00", title = "A")
        assertNull(scheduleError(listOf(a, a.copy(start = "09:00", end = "10:00"))))
        assertNull(scheduleError(listOf(a, a.copy(weekday = 1))))
        assertNotNull(scheduleError(listOf(a, a.copy(start = "08:30", end = "10:00"))))
        assertNotNull(scheduleError(listOf(a.copy(end = "07:00"))))
        assertNotNull(scheduleError(listOf(a.copy(start = "invalid"))))
    }
    @Test fun `semester counters reset in January and July`() {
        assertEquals("Sem II", semesterLabel(now))
        assertEquals(Instant.parse("2026-07-01T05:00:00Z"), campusDate(semesterStart(now))!!.toInstant())
        assertEquals("Sem I", semesterLabel(now.withMonth(6)))
    }
}
