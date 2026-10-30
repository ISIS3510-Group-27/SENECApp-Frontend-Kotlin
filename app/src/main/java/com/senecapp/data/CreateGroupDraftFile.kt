package com.senecapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CreateGroupDraft(
    val name: String,
    val categorySlug: String?,
    val description: String,
    val contactEmail: String,
    val interestIds: Set<Int>,
    val interestsEdited: Boolean,
)

// Local file storage for what the user is still typing in Create RSO, one file per account in filesDir.
// SQLite keeps what the user already decided to send; this file keeps the unfinished form.
internal class CreateGroupDraftFile(context: Context) {
    private val directory = context.applicationContext.filesDir

    fun write(uid: String, draft: CreateGroupDraft) {
        val json = JSONObject()
            .put("name", draft.name)
            .put("category_slug", draft.categorySlug ?: JSONObject.NULL)
            .put("description", draft.description)
            .put("contact_email", draft.contactEmail)
            .put("interest_ids", JSONArray(draft.interestIds.toList()))
            .put("interests_edited", draft.interestsEdited)
        // Written to a temporary file first and then renamed, so a crash never leaves half a draft.
        val temporary = File(directory, "${fileName(uid)}.tmp")
        temporary.writeText(json.toString())
        if (!temporary.renameTo(file(uid))) temporary.delete()
    }

    fun read(uid: String): CreateGroupDraft? = try {
        val json = JSONObject(file(uid).takeIf { it.exists() }?.readText() ?: return null)
        val ids = json.optJSONArray("interest_ids") ?: JSONArray()
        CreateGroupDraft(
            name = json.optString("name"),
            categorySlug = json.optString("category_slug").takeUnless { json.isNull("category_slug") || it.isBlank() },
            description = json.optString("description"),
            contactEmail = json.optString("contact_email"),
            interestIds = List(ids.length()) { ids.getInt(it) }.toSet(),
            interestsEdited = json.optBoolean("interests_edited"),
        )
    } catch (_: Exception) {
        null
    }

    fun delete(uid: String) {
        file(uid).delete()
    }

    private fun file(uid: String) = File(directory, fileName(uid))

    private fun fileName(uid: String) = "create_rso_draft_${uid.filter { it.isLetterOrDigit() }}.json"
}
