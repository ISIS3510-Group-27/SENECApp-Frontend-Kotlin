package com.senecapp.data

import org.junit.Assert.*
import org.junit.Test

class ItineraryJsonTest {
    @Test fun preservesPlanOrderAndSerializesOnlyPresentationFields() {
        val event = CampusEvent(2, "Lab", "Robotics", "2026-12-01T10:00:00Z", "ML", "Long description",
            false, "2026-12-01T11:00:00Z", checkedIn = true, attendeeCount = 30, color = "#123456")
        val second = event.copy(id = 1, building = null, color = null)
        val actual = ItineraryJson.decode(ItineraryJson.encode(listOf(event, second)))
        assertEquals(listOf(2, 1), actual.map { it.id })
        assertEquals(event.copy(description = null, checkedIn = false, attendeeCount = 0), actual[0])
        assertNull(actual[1].building)
        assertNull(actual[1].color)
    }

    @Test fun usesSeparateFileNamesAndNeverInterpretsUidAsAPath() {
        val one = itineraryFileName("../../one")
        assertTrue(one.matches(Regex("itinerary-[0-9a-f]{64}\\.json")))
        assertNotEquals(one, itineraryFileName("../../two"))
        assertEquals(one, itineraryFileName("../../one"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUnsupportedFileVersion() {
        ItineraryJson.decode("""{"version":2,"items":[]}""")
    }
}
