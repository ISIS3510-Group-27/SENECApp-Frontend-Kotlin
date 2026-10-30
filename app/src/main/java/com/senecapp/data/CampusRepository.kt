package com.senecapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import com.senecapp.BuildConfig

internal fun JSONObject.textOrNull(key: String): String? =
    optString(key).takeUnless { isNull(key) || it.isBlank() }

internal fun <T> JSONArray.readItems(parse: (JSONObject) -> T): List<T> =
    List(length()) { parse(getJSONObject(it)) }

internal fun mediaUrl(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let {
    URI(BuildConfig.API_BASE_URL + "/").resolve(it).toString()
}

data class CampusChoice(val id: Int, val label: String, val code: String = "")
data class GroupFilters(val sort: String = "popular", val verified: Boolean = false,
    val interests: Set<Int> = emptySet(), val buildings: Set<String> = emptySet()) {
    val active: Boolean get() = sort != "popular" || verified || interests.isNotEmpty() || buildings.isNotEmpty()
}
data class GroupDetails(val group: Rso, val description: String, val tags: List<String>,
    val events: List<CampusEvent>, val founded: Int?, val building: String?, val email: String?,
    val instagram: String?, val website: String?, val adminRequestStatus: String?)
data class CampusNotice(val id: Int, val title: String, val body: String, val createdAt: String,
    val groupId: Int?, val eventId: Int?, val unread: Boolean, val dismissed: Boolean)
data class ClassBlock(val id: Int = 0, val weekday: Int, val start: String, val end: String,
    val title: String, val buildingId: Int? = null, val buildingName: String? = null)

internal fun parseCampusNotice(root: JSONObject) = CampusNotice(root.getInt("id"), root.getString("title"),
    root.optString("body"), root.getString("created_at"),
    root.optInt("group_id").takeUnless { root.isNull("group_id") },
    root.optInt("event_id").takeUnless { root.isNull("event_id") },
    root.isNull("opened_at") && root.isNull("dismissed_at"), !root.isNull("dismissed_at"))

internal fun parseGroupDetails(root: JSONObject): GroupDetails {
    val rso = parseRso(root)
    return GroupDetails(rso, root.optString("description"),
        root.optJSONArray("tags")?.readItems { it.getString("name") }.orEmpty(),
        root.optJSONArray("upcoming_events")?.readItems { parseCampusEvent(it, rso.name, rso.color) }.orEmpty(),
        root.optInt("founded_year").takeIf { it > 0 }, root.optJSONObject("meeting_building")?.textOrNull("name"),
        root.textOrNull("contact_email"), root.textOrNull("instagram_url"), root.textOrNull("website_url"),
        root.textOrNull("admin_request_status"))
}

internal fun parseClassBlock(root: JSONObject) = ClassBlock(root.optInt("id"), root.getInt("weekday"),
    root.getString("start_time").take(5), root.getString("end_time").take(5), root.textOrNull("title").orEmpty(),
    root.optJSONObject("building")?.optInt("id"), root.optJSONObject("building")?.textOrNull("name"))

internal class CampusRepository(private val api: AuthenticatedApiClient = backendClient()) {
    suspend fun categories(): List<Pair<String, String>> = JSONArray(api.request("/categories"))
        .readItems { it.getString("slug") to it.getString("label") }
    suspend fun interests(): List<CampusChoice> = JSONArray(api.request("/interests"))
        .readItems { CampusChoice(it.getInt("id"), it.getString("name")) }
    suspend fun buildings(): List<CampusChoice> = JSONArray(api.request("/buildings"))
        .readItems { CampusChoice(it.getInt("id"), it.getString("name"), it.getString("code")) }

    suspend fun group(id: Int, source: String = "direct", requestId: String? = null): GroupDetails {
        val rec = requestId?.let { "&rec_request_id=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        return parseGroupDetails(JSONObject(api.request("/groups/$id?entry_point=$source$rec")))
    }
    suspend fun join(id: Int, motivation: String, source: String, requestId: String?, attemptId: String? = null) {
        val body = JSONObject().put("entry_point", source).put("motivation", motivation.takeIf { it.isNotBlank() })
        requestId?.let { body.put("rec_request_id", it) }
        attemptId?.let { body.put("join_attempt_id", it) }
        api.request("/groups/$id/join", "POST", body.toString())
    }
    suspend fun sendAnalytics(batch: String) { api.request("/analytics/events", "POST", batch) }
    suspend fun leave(id: Int) { api.request("/groups/$id/membership", "DELETE") }
    suspend fun save(id: Int, saved: Boolean) {
        api.request("/groups/$id/save" + if (saved) "?source=group_detail" else "", if (saved) "PUT" else "DELETE")
    }
    suspend fun requestAdmin(id: Int, note: String) {
        api.request("/groups/$id/admin-requests", "POST", JSONObject().put("note", note).toString())
    }
    suspend fun notices(): Pair<List<CampusNotice>, Int> {
        val items = mutableListOf<CampusNotice>()
        do {
            val root = JSONObject(api.request("/me/notifications?limit=100&offset=${items.size}"))
            val page = root.getJSONArray("items").readItems(::parseCampusNotice)
            items += page
        } while (page.isNotEmpty() && items.size < root.getInt("total"))
        // Match Flutter: a dismissed notification is also considered read.
        return items to items.count { it.unread }
    }
    suspend fun openNotice(id: Int) { api.request("/me/notifications/$id/open", "POST") }
    suspend fun dismissNotice(id: Int) { api.request("/me/notifications/$id/dismiss", "POST") }
    suspend fun schedule(): List<ClassBlock> = JSONArray(api.request("/me/schedule")).readItems(::parseClassBlock)
    suspend fun saveSchedule(blocks: List<ClassBlock>): List<ClassBlock> {
        val body = JSONArray()
        blocks.forEach { body.put(JSONObject().put("weekday", it.weekday).put("start_time", it.start)
            .put("end_time", it.end).put("title", it.title).put("building_id", it.buildingId)) }
        return JSONArray(api.request("/me/schedule", "PUT", JSONObject().put("blocks", body).toString()))
            .readItems(::parseClassBlock)
    }
    suspend fun location(enabled: Boolean) {
        api.request("/me", "PATCH", JSONObject().put("location_opt_in", enabled).toString())
    }
    suspend fun createEvent(id: Int, body: JSONObject) { api.request("/groups/$id/events", "POST", body.toString()) }
    suspend fun uploadCover(id: Int, image: ByteArray): GroupDetails =
        parseGroupDetails(JSONObject(api.request("/groups/$id/cover", "POST", image = image)))
    suspend fun checkInCode(id: Int): String = JSONObject(api.request("/events/$id/check-in-code")).getString("qr_payload")
    suspend fun adminRequests(id: Int): List<JSONObject> = JSONArray(api.request("/groups/$id/admin-requests")).readItems { it }
    suspend fun resolveAdmin(id: Int, requestId: Int, approve: Boolean) {
        api.request("/groups/$id/admin-requests/$requestId/" + if (approve) "approve" else "reject", "POST")
    }
    suspend fun bestTimes(id: Int, minutes: Int): JSONObject =
        JSONObject(api.request("/groups/$id/insights/best-times?duration_minutes=$minutes&limit=3"))
    suspend fun audience(id: Int): JSONObject = JSONObject(api.request("/groups/$id/insights/audience"))
}
