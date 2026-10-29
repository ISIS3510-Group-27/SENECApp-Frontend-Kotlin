package com.senecapp.data

import com.google.firebase.FirebaseNetworkException
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

internal data class OrganizationsResult(val items: List<Organization>, val categories: List<Pair<String, String>>,
    val fromCache: Boolean = false, val cachedAt: Long? = null, val cacheWriteFailed: Boolean = false)

internal interface OrganizationsSource {
    suspend fun load(query: OrganizationsQuery): OrganizationsResult
    suspend fun detail(id: Int, fromSearch: Boolean): String
    suspend fun save(id: Int)
    suspend fun unsave(id: Int)
}

internal fun isOrganizationsConnectivityFailure(failure: Exception): Boolean =
    failure is IOException || failure is FirebaseNetworkException

internal class OrganizationsRepository(
    private val cache: OrganizationsCache? = null,
    private val api: AuthenticatedApiClient = backendClient(),
    private val userId: () -> String? = FirebaseIdTokenProvider::userId,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : OrganizationsSource {
    override suspend fun load(query: OrganizationsQuery): OrganizationsResult = withContext(ioDispatcher) {
        val uid = userId() ?: throw SessionRequiredException()
        suspend fun checkSession() {
            currentCoroutineContext().ensureActive()
            if (userId() != uid) throw SessionChangedException()
        }
        val snapshot = try {
            val items = search(query, ::checkSession)
            checkSession()
            val categories = JSONArray(api.request("/categories"))
                .readItems { it.getString("slug") to it.getString("label") }
            check(categories.map { it.first }.distinct().size == categories.size) { "The categories response contained duplicates. Tap Retry." }
            checkSession()
            CachedOrganizations(items, categories, items.size, clock())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            if (!isOrganizationsConnectivityFailure(failure)) throw failure
            checkSession()
            val saved = cache?.read(uid, query) ?: throw failure
            checkSession()
            return@withContext OrganizationsResult(saved.items, saved.categories, fromCache = true, cachedAt = saved.updatedAt)
        }
        checkSession()
        val saved = cache?.save(uid, query, snapshot)
        checkSession()
        OrganizationsResult(snapshot.items, snapshot.categories, cachedAt = snapshot.updatedAt, cacheWriteFailed = saved == false)
    }

    private suspend fun search(query: OrganizationsQuery, checkSession: suspend () -> Unit): List<Organization> {
        val filters = query.filters
        val parameters = mutableListOf("limit=100")
        if (query.text.isNotBlank()) parameters += "q=${encode(query.text.trim())}"
        if (query.category != null) parameters += "category=${encode(query.category)}"
        if (query.upcomingOnly) parameters += "has_upcoming_events=true"
        parameters += "sort=${encode(filters.sort)}"
        if (filters.verified) parameters += "verified=true"
        filters.interests.sorted().forEach { parameters += "interest_id=$it" }
        filters.buildings.sorted().forEach { parameters += "building=${encode(it)}" }
        val result = mutableListOf<Organization>()
        var total: Int
        do {
          checkSession()
          val root = JSONObject(api.request("/groups?${parameters.joinToString("&")}&offset=${result.size}"))
          checkSession()
          val items = root.getJSONArray("items")
          result += List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val category = item.getJSONObject("category")
            Organization(id = item.getInt("id"), name = item.getString("name"),
                category = category.getString("label"), categorySlug = category.getString("slug"),
                members = item.getInt("member_count"), color = item.optString("color").takeIf { it.startsWith("#") },
                hasUpcomingEvent = !item.isNull("next_event"),
                isSaved = item.optBoolean("is_saved", false), imageUrl = item.textOrNull("image_url"),
                verified = item.optBoolean("verified"), nextEventTitle = item.optJSONObject("next_event")?.textOrNull("title"),
                nextEventStartsAt = item.optJSONObject("next_event")?.textOrNull("starts_at"))
          }
          total = root.getInt("total")
          check(total >= result.size && (items.length() > 0 || result.size == total)) {
              "The organizations response was incomplete. Tap Retry."
          }
        } while (result.size < total)
        check(result.map { it.id }.distinct().size == result.size) { "The organizations response contained duplicates. Tap Retry." }
        return result.toList()
    }


    override suspend fun detail(id: Int, fromSearch: Boolean): String = withContext(ioDispatcher) {
        val entryPoint = if (fromSearch) "search" else "explore"
        JSONObject(api.request("/groups/$id?entry_point=$entryPoint")).getString("description")
    }


    override suspend fun save(id: Int) = withContext(ioDispatcher) {
        api.request("/groups/$id/save?source=explore", "PUT")
        Unit
    }

    override suspend fun unsave(id: Int) = withContext(ioDispatcher) {
        api.request("/groups/$id/save", "DELETE")
        Unit
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
