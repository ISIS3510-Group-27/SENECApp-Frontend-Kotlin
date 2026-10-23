package com.senecapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CampusParsingTest {
    private fun group() = JSONObject("""{
        "id":12,"name":"AI Club","description":"Meet students","category":{"slug":"technology","label":"Technology"},
        "member_count":20,"review_status":"approved","is_member":true,"is_saved":true,"my_role":"admin",
        "verified":true,"image_url":"/media/groups/12/cover.jpg","color":"#123456",
        "next_event":{"id":8,"title":"Meetup","starts_at":"2026-10-23T14:00:00Z"},
        "tags":[{"id":1,"name":"AI","slug":"ai"}],"founded_year":2020,
        "upcoming_events":[{"id":8,"group_id":12,"title":"Meetup","starts_at":"2026-10-23T14:00:00Z",
        "ends_at":"2026-10-23T15:00:00Z","building":{"id":3,"name":"ML"},"is_cancelled":false}],
        "contact_email":null,"website_url":null,"admin_request_status":"pending"
    }""")
    @Test fun `group detail preserves cover official badge membership and next event`() {
        val result = parseGroupDetails(group())
        assertTrue(result.group.verified)
        assertTrue(result.group.isSaved)
        assertEquals("admin", result.group.role)
        assertEquals("/media/groups/12/cover.jpg", result.group.imageUrl)
        assertEquals("Meetup", result.group.nextEventTitle)
        assertEquals(listOf("AI"), result.tags)
        assertEquals(2020, result.founded)
        assertNull(result.email)
    }
    @Test fun `nested group event uses parent name and color`() {
        val event = parseGroupDetails(group()).events.single()
        assertEquals("AI Club", event.group)
        assertEquals("#123456", event.color)
        assertEquals(12, event.groupId)
        assertEquals("ML", event.building)
    }
    @Test fun `event detail includes attendance capacity and location`() {
        val event = parseCampusEvent(JSONObject("""{"id":8,"group_id":12,"title":"Meetup",
            "starts_at":"2026-10-23T14:00:00Z","ends_at":"2026-10-23T15:00:00Z",
            "group":{"id":12,"name":"AI Club","color":"#123456"},"building":null,
            "location_detail":"Room 101","checked_in":true,"attendee_count":8,"capacity":30}"""))
        assertTrue(event.checkedIn)
        assertEquals(8, event.attendeeCount)
        assertEquals(30, event.capacity)
        assertEquals("Room 101", event.locationDetail)
        assertNull(event.building)
    }
    @Test fun `schedule round trip keeps building id and normalizes seconds`() {
        val result = parseClassBlock(JSONObject("""{"id":1,"weekday":0,"start_time":"08:00:00",
            "end_time":"09:30:00","title":null,"building":{"id":3,"name":"ML"}}"""))
        assertEquals("08:00", result.start)
        assertEquals("", result.title)
        assertEquals(3, result.buildingId)
        assertNull(scheduleError(listOf(result)))
    }
    @Test fun `dismissed notices stay read after refreshing from server`() {
        val json = JSONObject("""{"id":1,"title":"Event","body":"New event","created_at":"2026-10-23T14:00:00Z",
            "opened_at":null,"dismissed_at":"2026-10-23T15:00:00Z","group_id":12,"event_id":null}""")
        val notice = parseCampusNotice(json)
        assertFalse(notice.unread)
        assertTrue(notice.dismissed)
        assertEquals(12, notice.groupId)
        assertNull(notice.eventId)
        json.put("dismissed_at", JSONObject.NULL)
        assertTrue(parseCampusNotice(json).unread)
    }
}
