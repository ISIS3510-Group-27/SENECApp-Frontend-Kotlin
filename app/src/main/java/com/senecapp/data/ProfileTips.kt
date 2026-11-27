package com.senecapp.data

import org.json.JSONObject

// BQ4 (type 3): which group profile fields are most related to students saving or joining a group.
// Response of GET /groups/{id}/insights/profile?days=N

internal data class ProfileTipField(
    val field: String,
    val filled: Boolean,
    val views: Int,
    // The backend sends null when there are not enough views to compute a number.
    val correlation: Double?,
    val lift: Double?,
    val conversionWith: Double?,
    val conversionWithout: Double?,
)

internal data class ProfileTips(
    val question: String,
    val answer: String,
    val days: Int,
    val filled: Int,
    val total: Int,
    val fields: List<ProfileTipField>,
)

// What the screen shows: the data, when it was synced and whether it came from the phone.
internal data class ProfileTipsSnapshot(
    val groupId: Int,
    val tips: ProfileTips,
    val syncedAt: Long,
    val fromPhone: Boolean = false,
)

// Result of the CPU step that runs on Dispatchers.Default.
internal data class ProfileTipsSummary(
    val groupId: Int,
    val ordered: List<ProfileTipField>,
    val filled: Int,
    val total: Int,
) {
    val missing: Int get() = total - filled
    val progress: Float get() = if (total == 0) 0f else filled.toFloat() / total
}

internal fun parseProfileTips(response: JSONObject): ProfileTips = ProfileTips(
    question = response.getString("question"),
    answer = response.getString("answer"),
    days = response.getInt("days"),
    filled = response.getInt("filled"),
    total = response.getInt("total"),
    fields = response.getJSONArray("fields").readItems {
        ProfileTipField(
            field = it.getString("field"),
            filled = it.getBoolean("filled"),
            views = it.optInt("views", 0),
            correlation = it.nullableDouble("correlation"),
            lift = it.nullableDouble("lift"),
            conversionWith = it.nullableDouble("conversion_with"),
            conversionWithout = it.nullableDouble("conversion_without"),
        )
    },
)

// Pure function so it is unit tested and the repository runs it on Dispatchers.Default.
internal fun summarizeProfileTips(snapshot: ProfileTipsSnapshot): ProfileTipsSummary {
    val ordered = snapshot.tips.fields.sortedWith(
        compareBy<ProfileTipField> { it.filled }
            .thenByDescending { it.correlation ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.field },
    )
    return ProfileTipsSummary(
        groupId = snapshot.groupId,
        ordered = ordered,
        filled = ordered.count { it.filled },
        total = ordered.size,
    )
}

private fun JSONObject.nullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)
