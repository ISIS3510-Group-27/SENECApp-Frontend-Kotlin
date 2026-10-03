package com.senecapp.data

import android.net.Uri


data class CheckInPayload(val eventId: Int, val code: String)

fun parseCheckInPayload(raw: String): CheckInPayload? {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("senecapp", ignoreCase = true)) return null
    // Uri treats "senecapp://check-in?..." as a hierarchical URI with "check-in" as the authority.
    if (!uri.host.equals("check-in", ignoreCase = true)) return null
    val eventId = uri.getQueryParameter("event_id")?.toIntOrNull() ?: return null
    val code = uri.getQueryParameter("code")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return CheckInPayload(eventId, code)
}