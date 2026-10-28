package com.senecapp.data

import com.google.firebase.FirebaseNetworkException
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class CampusEvent(val id: Int, val title: String, val group: String, val startsAt: String,
    val building: String?, val description: String?, val cancelled: Boolean,
    val endsAt: String = startsAt, val groupId: Int = 0, val color: String? = null,
    val checkedIn: Boolean = false, val attendeeCount: Int = 0, val capacity: Int? = null,
    val locationDetail: String? = null)

internal fun parseCampusEvent(item: JSONObject, groupName: String = "", groupColor: String? = null) = CampusEvent(
    item.getInt("id"), item.getString("title"), item.optJSONObject("group")?.optString("name") ?: groupName,
    item.getString("starts_at"), item.optJSONObject("building")?.textOrNull("name"), item.textOrNull("description"),
    item.optBoolean("is_cancelled"), item.optString("ends_at", item.getString("starts_at")),
    item.optInt("group_id"), item.optJSONObject("group")?.textOrNull("color") ?: groupColor,
    item.optBoolean("checked_in"), item.optInt("attendee_count"),
    item.optInt("capacity").takeUnless { item.isNull("capacity") }, item.textOrNull("location_detail"))

internal data class CampusEventsResult(val items: List<CampusEvent>, val total: Int,
    val fromCache: Boolean = false, val cachedAt: Long? = null, val cacheWriteFailed: Boolean = false)

internal interface CampusEventsSource {
    suspend fun load(mine: Boolean): CampusEventsResult
    suspend fun detail(id: Int, source: String = "events", requestId: String? = null): CampusEvent
}

internal fun isEventsConnectivityFailure(failure: Exception): Boolean =
    failure is IOException || failure is FirebaseNetworkException

internal class CampusEventsRepository(
    private val cache: EventsCache? = null,
    private val api: AuthenticatedApiClient = backendClient(),
    private val userId: () -> String? = FirebaseIdTokenProvider::userId,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : CampusEventsSource {
    override suspend fun load(mine: Boolean): CampusEventsResult = withContext(ioDispatcher) {
        val uid = userId() ?: throw SessionRequiredException()
        suspend fun checkSession() {
            currentCoroutineContext().ensureActive()
            if (userId() != uid) throw SessionChangedException()
        }
        val snapshot = try {
            val events = mutableListOf<CampusEvent>()
            var total: Int
            do {
                checkSession()
                val root = JSONObject(api.request("/events?mine=$mine&limit=100&offset=${events.size}"))
                checkSession()
                val page = root.getJSONArray("items").readItems(::parseCampusEvent)
                events += page
                total = root.getInt("total")
                check(total >= events.size && (page.isNotEmpty() || events.size == total)) {
                    "The events response was incomplete. Tap Retry."
                }
            } while (events.size < total)
            check(events.map { it.id }.distinct().size == events.size) { "The events response contained duplicate events. Tap Retry." }
            CachedEvents(events.toList(), total, clock())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            if (!isEventsConnectivityFailure(failure)) throw failure
            checkSession()
            val saved = cache?.read(uid, mine) ?: throw failure
            checkSession()
            return@withContext CampusEventsResult(saved.items, saved.total, fromCache = true, cachedAt = saved.updatedAt)
        }
        checkSession()
        // Storage failures must not turn a successful network response into a connectivity failure.
        val saved = cache?.save(uid, mine, snapshot)
        checkSession()
        CampusEventsResult(snapshot.items, snapshot.total, cachedAt = snapshot.updatedAt, cacheWriteFailed = saved == false)
    }

    override suspend fun detail(id: Int, source: String, requestId: String?): CampusEvent = withContext(Dispatchers.IO) {
        val rec = requestId?.let { "&rec_request_id=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        parseCampusEvent(JSONObject(api.request("/events/$id?entry_point=$source$rec")))
    }

    suspend fun attendedSince(start: String): Int {
        var offset = 0
        var count = 0
        do {
            val root = JSONObject(api.request("/events?mine=true&starts_after=$start&limit=100&offset=$offset"))
            val page = root.getJSONArray("items").readItems(::parseCampusEvent)
            count += page.count { it.checkedIn }
            offset += page.size
        } while (page.isNotEmpty() && offset < root.getInt("total"))
        return count
    }
}
