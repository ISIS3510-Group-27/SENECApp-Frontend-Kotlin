package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
class CreateGroupRepository {
    private val api = backendClient()

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
}