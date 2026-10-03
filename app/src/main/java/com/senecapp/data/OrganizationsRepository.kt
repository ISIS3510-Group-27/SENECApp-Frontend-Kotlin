package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class OrganizationsRepository {
    private val api = backendClient()

    suspend fun search(query: String, categorySlug: String?, upcomingOnly: Boolean = false): List<Organization> = withContext(Dispatchers.IO) {
        val parameters = mutableListOf("limit=100")
        if (query.isNotBlank()) parameters += "q=${encode(query.trim())}"
        if (categorySlug != null) parameters += "category=${encode(categorySlug)}"
        if (upcomingOnly) parameters += "has_upcoming_events=true"
        val root = JSONObject(api.request("/groups?${parameters.joinToString("&")}"))
        val items = root.getJSONArray("items")
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val category = item.getJSONObject("category")
            Organization(id = item.getInt("id"), name = item.getString("name"),
                category = category.getString("label"), categorySlug = category.getString("slug"),
                members = item.getInt("member_count"), color = item.optString("color").takeIf { it.startsWith("#") },
                hasUpcomingEvent = !item.isNull("next_event"),
                isSaved = item.optBoolean("is_saved", false))
        }
    }


    suspend fun detail(id: Int, fromSearch: Boolean): String = withContext(Dispatchers.IO) {
        val entryPoint = if (fromSearch) "search" else "explore"
        JSONObject(api.request("/groups/$id?entry_point=$entryPoint")).getString("description")
    }


    suspend fun save(id: Int) = withContext(Dispatchers.IO) {
        api.request("/groups/$id/save?source=explore", "PUT")
        Unit
    }

    suspend fun unsave(id: Int) = withContext(Dispatchers.IO) {
        api.request("/groups/$id/save", "DELETE")
        Unit
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}