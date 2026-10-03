package com.senecapp.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationValidationTest {
    @Test fun rejectsLookalikeAndNonUniversityDomains() {
        listOf("student@uniandes.edu.co.evil.com", "student@notuniandes.edu.co",
            "student@gmail.com", "student@uniandes.edu.co@evil.com", "student @uniandes.edu.co").forEach {
            assertNotNull(registrationError(it, "StrongPass1!", "StrongPass1!"))
        }
    }

    @Test fun acceptsTrimmedUppercaseUniversityEmail() {
        assertEquals("student@uniandes.edu.co", normalizedUniversityEmail(" Student@UNIANDES.EDU.CO "))
        assertNull(registrationError(" Student@UNIANDES.EDU.CO ", "StrongPass1!", "StrongPass1!"))
    }

    @Test fun requiresConfirmationWithoutTrimmingPasswords() {
        assertNotNull(registrationError("student@uniandes.edu.co", "StrongPass1! ", "StrongPass1!"))
        assertNotNull(registrationError("student@uniandes.edu.co", "12345", "12345"))
        assertNull(registrationError("student@uniandes.edu.co", "StrongPass1! ", "StrongPass1! "))
    }
}
