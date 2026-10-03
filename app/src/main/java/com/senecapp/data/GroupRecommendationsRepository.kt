package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GroupRecommendationsRepository {
    private val api = backendClient()

    suspend fun load(): GroupRecommendations = withContext(Dispatchers.IO) {
        val root = JSONObject(api.request("/recommendations/groups?limit=3"))
        val items = root.getJSONArray("items")
        GroupRecommendations(requestId = root.getString("request_id"), items = List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val group = item.getJSONObject("group")
            val reasons = item.getJSONArray("reasons")
            GroupRecommendation(id = group.getInt("id"), name = group.getString("name"),
                category = group.getJSONObject("category").getString("label"), description = group.getString("description"),
                memberCount = group.getInt("member_count"), reasons = List(reasons.length()) { reasons.getString(it) },
                isMember = group.getBoolean("is_member"))
        })
    }

    suspend fun openGroup(groupId: Int, requestId: String) {
        api.request("/groups/$groupId?entry_point=recommendation&rec_request_id=$requestId")
    }

    suspend fun joinGroup(groupId: Int, requestId: String) {
        val body = JSONObject().put("entry_point", "recommendation").put("rec_request_id", requestId).toString()
        try { api.request("/groups/$groupId/join", "POST", body)
        } catch (failure: ApiHttpException) {
            if (failure.status == 409) error("You are already a member of this group.")
            throw failure
        }
    }
}
