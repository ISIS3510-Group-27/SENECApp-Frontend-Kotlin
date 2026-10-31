package com.senecapp.data

import android.content.ContentValues


data class PendingGroup(
    val localId: Long,
    val name: String,
    val categorySlug: String,
    val description: String,
    val contactEmail: String?,
    val interestIds: List<Int>,
    val createdAt: Long,
    val status: String,
    val lastError: String?,
)


internal class PendingGroupsStore(private val database: RSODatabase) {

    fun add(uid: String, name: String, categorySlug: String, description: String, contactEmail: String?,
            interestIds: List<Int>): Long {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val localId = db.insertOrThrow("pending_groups", null, ContentValues().apply {
                put("uid", uid)
                put("name", name.trim())
                put("category_slug", categorySlug)
                put("description", description.trim())
                put("contact_email", contactEmail?.trim()?.takeIf { it.isNotBlank() })
                put("created_at", System.currentTimeMillis())
                put("status", "pending")
            })
            interestIds.distinct().forEach { interestId ->
                db.insertOrThrow("pending_group_interests", null, ContentValues().apply {
                    put("local_id", localId)
                    put("interest_id", interestId)
                })
            }
            db.setTransactionSuccessful()
            return localId
        } finally {
            db.endTransaction()
        }
    }

    // Oldest first, so proposals reach the backend in the order they were written.
    fun all(uid: String): List<PendingGroup> {
        val db = database.readableDatabase
        val groups = db.rawQuery(
            """
            SELECT local_id, name, category_slug, description, contact_email, created_at, status, last_error
            FROM pending_groups WHERE uid = ? ORDER BY created_at, local_id
            """.trimIndent(), arrayOf(uid),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(PendingGroup(
                        localId = cursor.getLong(0),
                        name = cursor.getString(1),
                        categorySlug = cursor.getString(2),
                        description = cursor.getString(3),
                        contactEmail = if (cursor.isNull(4)) null else cursor.getString(4),
                        interestIds = emptyList(),
                        createdAt = cursor.getLong(5),
                        status = cursor.getString(6),
                        lastError = if (cursor.isNull(7)) null else cursor.getString(7),
                    ))
                }
            }
        }
        return groups.map { group ->
            group.copy(interestIds = db.rawQuery(
                "SELECT interest_id FROM pending_group_interests WHERE local_id = ?", arrayOf(group.localId.toString()),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) } })
        }
    }

    fun count(uid: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM pending_groups WHERE uid = ? AND status != 'failed'", arrayOf(uid),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun setStatus(localId: Long, status: String, lastError: String? = null) {
        database.writableDatabase.update("pending_groups", ContentValues().apply {
            put("status", status)
            put("last_error", lastError)
        }, "local_id = ?", arrayOf(localId.toString()))
    }


    fun resetSending(uid: String) {
        database.writableDatabase.update("pending_groups", ContentValues().apply { put("status", "pending") },
            "uid = ? AND status = 'sending'", arrayOf(uid))
    }

    fun delete(localId: Long) {
        database.writableDatabase.delete("pending_groups", "local_id = ?", arrayOf(localId.toString()))
    }
}
