package com.senecapp.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

// Small interface for unit tests without SQLite.
internal interface ProfileTipsStorage {
    fun read(uid: String, groupId: Int): ProfileTipsSnapshot?
    fun save(uid: String, snapshot: ProfileTipsSnapshot)
}

internal class ProfileTipsStore(private val database: RSODatabase) : ProfileTipsStorage {

    override fun save(uid: String, snapshot: ProfileTipsSnapshot) {
        val db = database.writableDatabase
        val tips = snapshot.tips
        db.beginTransaction()
        try {
            db.delete("profile_tips", "uid = ? AND group_id = ?", arrayOf(uid, snapshot.groupId.toString()))
            db.insertOrThrow("profile_tips", null, ContentValues().apply {
                put("uid", uid)
                put("group_id", snapshot.groupId)
                put("days", tips.days)
                put("filled", tips.filled)
                put("total", tips.total)
                put("question", tips.question)
                put("answer", tips.answer)
                put("synced_at", snapshot.syncedAt)
            })
            tips.fields.forEachIndexed { position, field ->
                db.insertOrThrow("profile_tip_fields", null, ContentValues().apply {
                    put("uid", uid)
                    put("group_id", snapshot.groupId)
                    put("field", field.field)
                    put("position", position)
                    put("filled", field.filled)
                    put("views", field.views)
                    putNullable("correlation", field.correlation)
                    putNullable("lift", field.lift)
                    putNullable("conversion_with", field.conversionWith)
                    putNullable("conversion_without", field.conversionWithout)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun read(uid: String, groupId: Int): ProfileTipsSnapshot? {
        val db = database.readableDatabase
        val args = arrayOf(uid, groupId.toString())
        val parent = db.rawQuery(
            "SELECT * FROM profile_tips WHERE uid = ? AND group_id = ?", args,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toParent() else null } ?: return null

        val fields = mutableListOf<ProfileTipField>()
        db.rawQuery(
            "SELECT * FROM profile_tip_fields WHERE uid = ? AND group_id = ? ORDER BY position", args,
        ).use { cursor ->
            while (cursor.moveToNext()) fields += cursor.toField()
        }
        return ProfileTipsSnapshot(
            groupId = groupId,
            tips = parent.tips.copy(fields = fields),
            syncedAt = parent.syncedAt,
            fromPhone = true,
        )
    }

    private class Parent(val tips: ProfileTips, val syncedAt: Long)

    private fun Cursor.toParent() = Parent(
        tips = ProfileTips(
            question = getString(getColumnIndexOrThrow("question")),
            answer = getString(getColumnIndexOrThrow("answer")),
            days = getInt(getColumnIndexOrThrow("days")),
            filled = getInt(getColumnIndexOrThrow("filled")),
            total = getInt(getColumnIndexOrThrow("total")),
            fields = emptyList(),
        ),
        syncedAt = getLong(getColumnIndexOrThrow("synced_at")),
    )

    private fun Cursor.toField() = ProfileTipField(
        field = getString(getColumnIndexOrThrow("field")),
        filled = getInt(getColumnIndexOrThrow("filled")) == 1,
        views = getInt(getColumnIndexOrThrow("views")),
        correlation = real("correlation"),
        lift = real("lift"),
        conversionWith = real("conversion_with"),
        conversionWithout = real("conversion_without"),
    )

    private fun Cursor.real(column: String): Double? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

    private fun ContentValues.putNullable(key: String, value: Double?) {
        if (value == null) putNull(key) else put(key, value)
    }
}

internal fun createProfileTipsTables(db: SQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS profile_tips (
            uid TEXT NOT NULL,
            group_id INTEGER NOT NULL,
            days INTEGER NOT NULL,
            filled INTEGER NOT NULL,
            total INTEGER NOT NULL,
            question TEXT NOT NULL,
            answer TEXT NOT NULL,
            synced_at INTEGER NOT NULL,
            PRIMARY KEY (uid, group_id)
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS profile_tip_fields (
            uid TEXT NOT NULL,
            group_id INTEGER NOT NULL,
            field TEXT NOT NULL,
            position INTEGER NOT NULL,
            filled INTEGER NOT NULL,
            views INTEGER NOT NULL,
            correlation REAL,
            lift REAL,
            conversion_with REAL,
            conversion_without REAL,
            PRIMARY KEY (uid, group_id, field),
            FOREIGN KEY (uid, group_id) REFERENCES profile_tips(uid, group_id) ON DELETE CASCADE
        )
        """.trimIndent()
    )
}
