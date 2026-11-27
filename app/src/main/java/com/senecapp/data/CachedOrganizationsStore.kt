package com.senecapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal data class OrganizationsQuery(val text: String = "", val category: String? = null,
    val upcomingOnly: Boolean = false, val filters: GroupFilters = GroupFilters()) {
    val filtered: Boolean get() = text.isNotBlank() || category != null || upcomingOnly || filters.active
    fun key(): String = JSONObject().put("text", text.trim()).put("category", category ?: JSONObject.NULL)
        .put("upcomingOnly", upcomingOnly).put("sort", filters.sort).put("verified", filters.verified)
        .put("interests", JSONArray(filters.interests.sorted())).put("buildings", JSONArray(filters.buildings.sorted())).toString()
}

internal data class CachedOrganizations(val items: List<Organization>, val categories: List<Pair<String, String>>,
    val total: Int, val updatedAt: Long)

internal interface OrganizationsCache {
    fun read(uid: String, query: OrganizationsQuery): CachedOrganizations?
    fun save(uid: String, query: OrganizationsQuery, snapshot: CachedOrganizations): Boolean
}

// Complete snapshots are written on IO. Keep at most ten searches per account.
internal class CachedOrganizationsStore(private val preferences: SharedPreferences) : OrganizationsCache {
    constructor(context: Context) : this(context.applicationContext
        .getSharedPreferences("discover_organizations_cache", Context.MODE_PRIVATE))

    override fun read(uid: String, query: OrganizationsQuery): CachedOrganizations? {
        if (uid.isBlank()) return null
        return try {
            val rows = rows(uid)
            val row = (0 until rows.length()).map { rows.getJSONObject(it) }
                .lastOrNull { it.getString("query") == query.key() } ?: return null
            decodeSnapshot(row).takeIf(CachedOrganizations::isValid)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { null }
    }

    override fun save(uid: String, query: OrganizationsQuery, snapshot: CachedOrganizations): Boolean {
        if (uid.isBlank() || !snapshot.isValid()) return false
        return try {
            val items = JSONArray(snapshot.items.map(OrganizationJson::encode))
            val categories = JSONArray()
            snapshot.categories.forEach { (slug, label) -> categories.put(JSONObject().put("slug", slug).put("label", label)) }
            val row = JSONObject().put("query", query.key()).put("items", items).put("categories", categories)
                .put("total", snapshot.total).put("updatedAt", snapshot.updatedAt)
            val previous = runCatching { rows(uid) }.getOrElse { JSONArray() }
            val retained = (0 until previous.length()).map { previous.getJSONObject(it) }
                .filter { it.optString("query") != query.key() }.takeLast(MAX_QUERIES - 1)
            val snapshots = JSONArray()
            retained.forEach(snapshots::put)
            snapshots.put(row)
            val root = JSONObject().put("version", 1).put("uid", uid).put("snapshots", snapshots)
            preferences.edit().putString("discover.$uid", root.toString()).commit()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { false }
    }

    private fun rows(uid: String): JSONArray {
        val raw = preferences.getString("discover.$uid", null) ?: return JSONArray()
        val root = JSONObject(raw)
        if (root.getInt("version") != 1 || root.getString("uid") != uid) return JSONArray()
        return root.getJSONArray("snapshots")
    }

    fun snapshots(uid: String): List<Pair<String, CachedOrganizations>> {
        val previous = try {
            rows(uid)
        } catch (_: org.json.JSONException) {
            return emptyList()
        }
        return (0 until previous.length()).mapNotNull { index ->
            try {
                val row = previous.getJSONObject(index)
                val snapshot = decodeSnapshot(row)
                if (snapshot.isValid()) row.getString("query") to snapshot else null
            } catch (_: org.json.JSONException) {
                null
            }
        }.takeLast(MAX_QUERIES)
    }

    private fun decodeSnapshot(row: JSONObject): CachedOrganizations {
        val items = row.getJSONArray("items").readItems(OrganizationJson::decode)
        val categories = row.getJSONArray("categories").readItems { it.getString("slug") to it.getString("label") }
        return CachedOrganizations(items, categories, row.getInt("total"), row.getLong("updatedAt"))
    }

    companion object { const val MAX_QUERIES = 10 }
}

internal fun CachedOrganizations.isValid(): Boolean = updatedAt > 0L &&
    total == items.size && items.distinctBy(Organization::id).size == items.size &&
    categories.distinctBy { it.first }.size == categories.size

internal object OrganizationJson {
    fun encode(item: Organization): JSONObject = JSONObject().put("id", item.id).put("name", item.name)
        .put("category", item.category).put("categorySlug", item.categorySlug).put("members", item.members)
        .put("color", item.color ?: JSONObject.NULL).put("hasUpcomingEvent", item.hasUpcomingEvent)
        .put("isSaved", item.isSaved).put("imageUrl", item.imageUrl ?: JSONObject.NULL)
        .put("verified", item.verified).put("nextEventTitle", item.nextEventTitle ?: JSONObject.NULL)
        .put("nextEventStartsAt", item.nextEventStartsAt ?: JSONObject.NULL)

    fun decode(item: JSONObject): Organization = Organization(
        item.getInt("id"), item.getString("name"), item.getString("category"),
        item.getString("categorySlug"), item.getInt("members"), item.textOrNull("color"),
        item.getBoolean("hasUpcomingEvent"), item.getBoolean("isSaved"), item.textOrNull("imageUrl"),
        item.getBoolean("verified"), item.textOrNull("nextEventTitle"), item.textOrNull("nextEventStartsAt"),
    )
}
