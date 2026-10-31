package com.senecapp.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase


// DAO for the rsos table. Callers run it on Dispatchers.IO; the rest of the app never writes SQL.
internal class RsosLocalStore(private val database: RSODatabase) {

    // The previous lists of this user are replaced in one transaction, so a failed write never leaves half a list.
    fun replace(uid: String, mine: List<Rso>, saved: List<Rso>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("rsos", "uid = ?", arrayOf(uid))
            insert(db, uid, "mine", mine)
            insert(db, uid, "saved", saved)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun read(uid: String): Pair<List<Rso>, List<Rso>> {
        val mine = mutableListOf<Rso>()
        val saved = mutableListOf<Rso>()
        database.readableDatabase.rawQuery(
            "SELECT * FROM rsos WHERE uid = ? ORDER BY list, position", arrayOf(uid),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rso = cursor.toRso()
                if (cursor.getString(cursor.getColumnIndexOrThrow("list")) == "mine") mine += rso else saved += rso
            }
        }
        return mine to saved
    }

    private fun insert(db: SQLiteDatabase, uid: String, list: String, groups: List<Rso>) {
        groups.forEachIndexed { position, group ->
            val values = ContentValues().apply {
                put("uid", uid)
                put("list", list)
                put("group_id", group.id)
                put("position", position)
                put("name", group.name)
                put("category", group.category)
                put("category_slug", group.categorySlug)
                put("member_count", group.memberCount)
                put("next_event_title", group.nextEventTitle)
                put("next_event_starts_at", group.nextEventStartsAt)
                put("review_status", group.reviewStatus)
                put("rejection_reason", group.rejectionReason)
                put("is_member", group.isMember)
                put("is_saved", group.isSaved)
                put("color", group.color)
                put("image_url", group.imageUrl)
                put("verified", group.verified)
                put("my_role", group.role)
            }
            db.insertWithOnConflict("rsos", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun Cursor.toRso() = Rso(
        id = getInt(getColumnIndexOrThrow("group_id")),
        name = getString(getColumnIndexOrThrow("name")),
        category = getString(getColumnIndexOrThrow("category")),
        categorySlug = getString(getColumnIndexOrThrow("category_slug")),
        memberCount = getInt(getColumnIndexOrThrow("member_count")),
        nextEventTitle = text("next_event_title"),
        nextEventStartsAt = text("next_event_starts_at"),
        reviewStatus = getString(getColumnIndexOrThrow("review_status")),
        rejectionReason = text("rejection_reason"),
        isMember = getInt(getColumnIndexOrThrow("is_member")) == 1,
        isSaved = getInt(getColumnIndexOrThrow("is_saved")) == 1,
        color = text("color"),
        imageUrl = text("image_url"),
        verified = getInt(getColumnIndexOrThrow("verified")) == 1,
        role = text("my_role"),
    )

    private fun Cursor.text(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
}
