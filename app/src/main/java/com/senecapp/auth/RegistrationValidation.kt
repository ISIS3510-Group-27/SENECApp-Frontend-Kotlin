package com.senecapp.auth

import java.util.Locale

internal fun normalizedUniversityEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

internal fun registrationError(email: String, password: String, confirmation: String): String? = when {
    !normalizedUniversityEmail(email).matches(Regex("[^\\s@]+@uniandes\\.edu\\.co")) ->
        "Use your @uniandes.edu.co email address."
    password.length < 6 -> "Use a password with at least 6 characters."
    password != confirmation -> "The passwords do not match."
    else -> null
}
