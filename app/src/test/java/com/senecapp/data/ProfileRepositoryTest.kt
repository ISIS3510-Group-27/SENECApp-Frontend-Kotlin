package com.senecapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileRepositoryTest {
    @Test fun nullProfileFieldsStayEmpty() {
        val profile = parseProfile(JSONObject("""{"id":1,"email":"student@uniandes.edu.co","full_name":null,
            "program":null,"semester":null,"interests":[],"location_opt_in":false,"notifications_opt_in":false}"""))
        assertNull(profile.fullName)
        assertNull(profile.program)
        assertNull(profile.semester)
        assertTrue(profile.interests.isEmpty())
    }

    @Test fun parsesOnlyTheReturnedProfileData() {
        val profile = parseProfile(JSONObject("""{"id":2,"email":"student@uniandes.edu.co","full_name":"Student",
            "program":"Engineering","semester":3,"interests":[{"id":4,"name":"Music"}],
            "location_opt_in":true,"notifications_opt_in":false}"""))
        assertEquals("Student", profile.fullName)
        assertEquals(3, profile.semester)
        assertEquals(listOf("Music"), profile.interests)
        assertTrue(profile.locationOptIn)
        assertFalse(profile.notificationsOptIn)
    }
}
