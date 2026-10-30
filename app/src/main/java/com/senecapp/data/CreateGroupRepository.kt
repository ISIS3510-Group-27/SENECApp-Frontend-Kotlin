package com.senecapp.data

import android.content.Context
import android.database.SQLException
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Category(val id: Int, val slug: String, val label: String)

data class Interest(val id: Int, val slug: String, val name: String, val categorySlug: String?)

/** What the backend answers after a proposal is filed. */
data class CreatedGroup(
    val id: Int,
    val name: String,
    val reviewStatus: String,
    /** The interests attached to the group: the ones sent, or the ones the backend inferred. */
    val tags: List<String>,
)

/** Categories and interests for the form, and whether they came from the copy saved on the phone. */
data class Catalogue(val categories: List<Category>, val interests: List<Interest>, val fromCache: Boolean = false)

/** A refusal the form can show next to the right field. */
class GroupRejectedException(message: String) : IllegalStateException(message)

/**
 * Catalogues and group creation.
 *
 * POST /groups is strict in three ways (app/schemas/group.py in the backend):
 *  - the schema is extra="forbid", so an unknown key is a 422, not an ignored field;
 *  - exactly one of `category` (slug) or `category_id` may be sent, never both and never neither;
 *  - `description` has a 20 character floor and `name` a 3 character one.
 *
 * Interests go as `tag_ids`. There is no `interests` field.
 *
 * The shared client throws ApiHttpException(status) and never exposes the error body, so the
 * refusals are mapped from the status code instead of from the backend's own wording.
 */
class CreateGroupRepository(context: Context) {
    private val api = backendClient()
    private val applicationContext = context.applicationContext
    private val catalogueStore = CatalogueLocalStore(RSODatabase.get(applicationContext))
    private val pendingStore = PendingGroupsStore(RSODatabase.get(applicationContext))

    fun online(): Boolean = isOnline(applicationContext)

    // Network falling back to cache: both lists are requested in parallel and saved to SQLite;
    // without connection the form uses the last saved copy.
    suspend fun catalogue(): Catalogue = withContext(Dispatchers.IO) {
        try {
            val (categories, interests) = coroutineScope {
                val categoriesRequest = async { categories() }
                val interestsRequest = async { interests() }
                categoriesRequest.await() to interestsRequest.await()
            }
            try { catalogueStore.save(categories, interests) } catch (_: SQLException) { }
            Catalogue(categories, interests)
        } catch (failure: IOException) {
            val saved = try {
                Catalogue(catalogueStore.categories(), catalogueStore.interests(), fromCache = true)
            } catch (_: SQLException) { null }
            saved?.takeIf { it.categories.isNotEmpty() } ?: throw failure
        }
    }

    // Saves a proposal that could not be sent, together with its interests, in one transaction.
    suspend fun queue(name: String, categorySlug: String, description: String, contactEmail: String?,
                      tagIds: List<Int>): Long = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: throw SessionRequiredException()
        pendingStore.add(uid, name, categorySlug, description, contactEmail, tagIds)
    }

    suspend fun pending(): List<PendingGroup> = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: return@withContext emptyList()
        pendingStore.all(uid)
    }

    suspend fun retry(localId: Long) = withContext(Dispatchers.IO) { pendingStore.setStatus(localId, "pending") }

    suspend fun discard(localId: Long) = withContext(Dispatchers.IO) { pendingStore.delete(localId) }

    // Sends the saved proposals in order and returns how many the backend accepted.
    // Each one is marked 'sending' first so two passes never send it twice; a refusal (409/422) is marked
    // 'failed' with its reason and not retried; no connection stops the pass and keeps the rest pending.
    suspend fun sendPending(): Int = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: return@withContext 0
        if (!sendLock.tryLock()) return@withContext 0
        try {
            pendingStore.resetSending(uid)
            var sent = 0
            for (group in pendingStore.all(uid).filter { it.status == "pending" }) {
                pendingStore.setStatus(group.localId, "sending")
                try {
                    create(group.name, group.categorySlug, group.description, group.contactEmail, group.interestIds)
                    pendingStore.delete(group.localId)
                    sent++
                } catch (cancelled: CancellationException) {
                    pendingStore.setStatus(group.localId, "pending")
                    throw cancelled
                } catch (rejected: GroupRejectedException) {
                    pendingStore.setStatus(group.localId, "failed", rejected.message)
                } catch (_: Exception) {
                    pendingStore.setStatus(group.localId, "pending")
                    break
                }
            }
            sent
        } finally {
            sendLock.unlock()
        }
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        val array = JSONArray(api.request("/categories"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Category(item.getInt("id"), item.getString("slug"), item.getString("label"))
        }
    }

    suspend fun interests(): List<Interest> = withContext(Dispatchers.IO) {
        val array = JSONArray(api.request("/interests"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Interest(
                id = item.getInt("id"),
                slug = item.getString("slug"),
                name = item.getString("name"),
                categorySlug = item.optJSONObject("category")?.optString("slug"),
            )
        }
    }

    suspend fun create(
        name: String,
        categorySlug: String,
        description: String,
        contactEmail: String?,
        tagIds: List<Int>,
    ): CreatedGroup = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("category", categorySlug)
            .put("description", description.trim())
            .put("tag_ids", JSONArray(tagIds))
        // Only send contact_email when there is one: an empty string fails EmailStr validation
        // with a 422 that is confusing to read on a phone.
        contactEmail?.trim()?.takeIf { it.isNotBlank() }?.let { body.put("contact_email", it) }

        val raw = try {
            api.request("/groups", "POST", body.toString())
        } catch (failure: ApiHttpException) {
            when (failure.status) {
                409 -> throw GroupRejectedException("A group with this name already exists.")
                422 -> throw GroupRejectedException(
                    "Check the details: name of 3+ characters, description of 20+ characters and a valid email.",
                )
                else -> throw failure
            }
        }

        val group = JSONObject(raw)
        val tags = group.optJSONArray("tags") ?: JSONArray()
        CreatedGroup(
            id = group.getInt("id"),
            name = group.getString("name"),
            reviewStatus = group.optString("review_status", "pending"),
            tags = List(tags.length()) { tags.getJSONObject(it).getString("name") },
        )
    }

    private companion object {
        // One pass at a time for the whole app, even if the screen asks again while a pass is running.
        val sendLock = Mutex()
    }
}
