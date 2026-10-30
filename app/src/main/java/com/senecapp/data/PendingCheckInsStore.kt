package com.senecapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class PendingCheckIn(
    val localId: Long,
    val eventId: Int,
    val code: String,
    val coordinates: EventCoordinates?,
    val createdAt: Long,
    val status: String,
    val lastError: String?,
)


// Rows are deleted as soon as the backend accepts them, so the code and location are not kept longer than needed.
internal class PendingCheckInsStore(private val database: RSODatabase) {

    fun save(uid: String, eventId: Int, code: String, coordinates: EventCoordinates?) {
        database.writableDatabase.insertWithOnConflict("pending_checkins", null, ContentValues().apply {
            put("uid", uid)
            put("event_id", eventId)
            put("code", code)
            put("latitude", coordinates?.latitude)
            put("longitude", coordinates?.longitude)
            put("created_at", System.currentTimeMillis())
            put("status", "pending")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Oldest first, so check-ins are sent in the order they were scanned.
    fun all(uid: String): List<PendingCheckIn> = database.readableDatabase.rawQuery(
        """
        SELECT local_id, event_id, code, latitude, longitude, created_at, status, last_error
        FROM pending_checkins WHERE uid = ? ORDER BY created_at, local_id
        """.trimIndent(), arrayOf(uid),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val coordinates = if (cursor.isNull(3) || cursor.isNull(4)) null
                else runCatching { EventCoordinates(cursor.getDouble(3), cursor.getDouble(4)) }.getOrNull()
                add(PendingCheckIn(
                    localId = cursor.getLong(0),
                    eventId = cursor.getInt(1),
                    code = cursor.getString(2),
                    coordinates = coordinates,
                    createdAt = cursor.getLong(5),
                    status = cursor.getString(6),
                    lastError = if (cursor.isNull(7)) null else cursor.getString(7),
                ))
            }
        }
    }

    fun count(uid: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM pending_checkins WHERE uid = ? AND status != 'failed'", arrayOf(uid),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun setStatus(localId: Long, status: String, lastError: String? = null) {
        database.writableDatabase.update("pending_checkins", ContentValues().apply {
            put("status", status)
            put("last_error", lastError)
        }, "local_id = ?", arrayOf(localId.toString()))
    }

    fun resetSending(uid: String) {
        database.writableDatabase.update("pending_checkins", ContentValues().apply { put("status", "pending") },
            "uid = ? AND status = 'sending'", arrayOf(uid))
    }

    fun delete(localId: Long) {
        database.writableDatabase.delete("pending_checkins", "local_id = ?", arrayOf(localId.toString()))
    }
}
