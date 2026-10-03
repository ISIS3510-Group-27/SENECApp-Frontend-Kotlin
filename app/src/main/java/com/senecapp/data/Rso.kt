package com.senecapp.data

import org.json.JSONObject

data class Rso(
    val id: Int,
    val name: String,
    val category: String,
    val categorySlug: String,
    val memberCount: Int,
    val nextEventTitle: String?,
    val nextEventStartsAt: String?,
    val reviewStatus: String,
    val rejectionReason: String?,
    val isMember: Boolean,
    val isSaved: Boolean,
    val color: String?,
) {
    val isPendingReview: Boolean get() = reviewStatus == "pending"
    val isRejected: Boolean get() = reviewStatus == "rejected"
}

internal fun parseRso(item: JSONObject): Rso {
    val category = item.getJSONObject("category")
    val nextEvent = item.optJSONObject("next_event")
    return Rso(
        id = item.getInt("id"),
        name = item.getString("name"),
        category = category.getString("label"),
        categorySlug = category.getString("slug"),
        memberCount = item.optInt("member_count", 0),
        nextEventTitle = nextEvent?.optString("title")?.takeIf { it.isNotBlank() },
        nextEventStartsAt = nextEvent?.optString("starts_at")?.takeIf { it.isNotBlank() },
        reviewStatus = item.optString("review_status", "approved"),
        rejectionReason = item.optString("rejection_reason")
            .takeUnless { item.isNull("rejection_reason") || it.isBlank() },
        isMember = item.optBoolean("is_member", false),
        isSaved = item.optBoolean("is_saved", false),
        color = item.optString("color").takeIf { it.startsWith("#") },
    )
}