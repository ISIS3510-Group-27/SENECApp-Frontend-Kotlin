package com.senecapp.data

import org.json.JSONObject

data class UserProfile(val id: Int, val email: String, val fullName: String?, val program: String?,
    val semester: Int?, val interests: List<String>, val locationOptIn: Boolean, val notificationsOptIn: Boolean)

internal fun interface ProfileSource { suspend fun load(): UserProfile }

internal class ProfileRepository : ProfileSource {
    private val api = backendClient()
    override suspend fun load(): UserProfile = parseProfile(JSONObject(api.request("/me")))
}

internal fun parseProfile(root: JSONObject): UserProfile {
    fun optional(name: String) = root.optString(name).takeUnless { root.isNull(name) || it.isBlank() }
    val interests = root.optJSONArray("interests")
    return UserProfile(id = root.getInt("id"), email = root.getString("email"),
        fullName = optional("full_name"), program = optional("program"),
        semester = root.optInt("semester").takeUnless { root.isNull("semester") || it <= 0 },
        interests = List(interests?.length() ?: 0) { interests!!.getJSONObject(it).getString("name") },
        locationOptIn = root.optBoolean("location_opt_in"), notificationsOptIn = root.optBoolean("notifications_opt_in"))
}
