package com.senecapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface EventDetailsStorage {
    suspend fun read(uid: String, id: Int): CampusEvent?
    suspend fun save(uid: String, event: CampusEvent)
}

// Organizers and their events share a composite owner key. This database is separate from RSODatabase.
internal class EventDetailsDatabase(context: Context, name: String = "event_details.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE organizers (
                uid TEXT NOT NULL, group_id INTEGER NOT NULL, name TEXT NOT NULL, color TEXT,
                PRIMARY KEY(uid, group_id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE event_details (
                uid TEXT NOT NULL, event_id INTEGER NOT NULL, group_id INTEGER NOT NULL,
                title TEXT NOT NULL, starts_at TEXT NOT NULL, ends_at TEXT NOT NULL,
                building TEXT, location_detail TEXT, description TEXT, cancelled INTEGER NOT NULL,
                attendee_count INTEGER NOT NULL, capacity INTEGER, saved_at INTEGER NOT NULL,
                PRIMARY KEY(uid, event_id),
                FOREIGN KEY(uid, group_id) REFERENCES organizers(uid, group_id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX event_organizer ON event_details(uid, group_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Missing event detail database migration from $oldVersion to $newVersion")
    }
}

internal class SavedEventDetailsStore(private val helper: EventDetailsDatabase) : EventDetailsStorage {
    constructor(context: Context) : this(ProductionEventDetailsDatabase.get(context))

    override suspend fun read(uid: String, id: Int): CampusEvent? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("""
            SELECT e.title, o.name, e.starts_at, e.building, e.description, e.cancelled,
                   e.ends_at, e.group_id, o.color, e.attendee_count, e.capacity, e.location_detail
            FROM event_details e JOIN organizers o ON e.uid = o.uid AND e.group_id = o.group_id
            WHERE e.uid = ? AND e.event_id = ?
        """.trimIndent(), arrayOf(uid, id.toString())).use { row ->
            if (!row.moveToFirst()) null else CampusEvent(
                id, row.getString(0), row.getString(1), row.getString(2), row.getString(3),
                row.getString(4), row.getInt(5) != 0, row.getString(6), row.getInt(7), row.getString(8),
                attendeeCount = row.getInt(9), capacity = if (row.isNull(10)) null else row.getInt(10),
                locationDetail = row.getString(11)
            )
        }
    }

    override suspend fun save(uid: String, event: CampusEvent) = withContext(Dispatchers.IO) {
        require(uid.isNotBlank())
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val organizer = ContentValues().apply {
                put("uid", uid)
                put("group_id", event.groupId)
                put("name", event.group)
                put("color", event.color)
            }
            val organizerKey = arrayOf(uid, event.groupId.toString())
            if (db.update("organizers", organizer, "uid = ? AND group_id = ?", organizerKey) == 0) {
                db.insertOrThrow("organizers", null, organizer)
            }
            // Persist display data only. A disk read never confers check-in status or permissions.
            db.insertWithOnConflict("event_details", null, ContentValues().apply {
                put("uid", uid)
                put("event_id", event.id)
                put("group_id", event.groupId)
                put("title", event.title)
                put("starts_at", event.startsAt)
                put("ends_at", event.endsAt)
                put("building", event.building)
                put("location_detail", event.locationDetail)
                put("description", event.description)
                put("cancelled", event.cancelled)
                put("attendee_count", event.attendeeCount)
                put("capacity", event.capacity)
                put("saved_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            db.execSQL("""
                DELETE FROM event_details WHERE rowid IN (
                    SELECT rowid FROM event_details ORDER BY saved_at DESC, rowid DESC LIMIT -1 OFFSET 100
                )
            """.trimIndent())
            db.execSQL("""
                DELETE FROM organizers WHERE NOT EXISTS (
                    SELECT 1 FROM event_details e WHERE e.uid = organizers.uid AND e.group_id = organizers.group_id
                )
            """.trimIndent())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

private object ProductionEventDetailsDatabase {
    private var helper: EventDetailsDatabase? = null

    @Synchronized
    fun get(context: Context): EventDetailsDatabase =
        helper ?: EventDetailsDatabase(context).also { helper = it }
}
