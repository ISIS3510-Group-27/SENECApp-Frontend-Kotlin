package com.senecapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement

internal class DiscoverDatabase(context: Context, name: String = "discover.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE discover_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, query_key TEXT NOT NULL,
                updated_at INTEGER NOT NULL, total INTEGER NOT NULL,
                UNIQUE(uid, query_key)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE discover_organizations (
                snapshot_id INTEGER NOT NULL REFERENCES discover_snapshots(id) ON DELETE CASCADE,
                position INTEGER NOT NULL, organization_id INTEGER NOT NULL,
                name TEXT NOT NULL, category TEXT NOT NULL, category_slug TEXT NOT NULL,
                members INTEGER NOT NULL, color TEXT, has_upcoming_event INTEGER NOT NULL,
                is_saved INTEGER NOT NULL, image_url TEXT, verified INTEGER NOT NULL,
                next_event_title TEXT, next_event_starts_at TEXT,
                PRIMARY KEY(snapshot_id, position), UNIQUE(snapshot_id, organization_id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE discover_categories (
                snapshot_id INTEGER NOT NULL REFERENCES discover_snapshots(id) ON DELETE CASCADE,
                position INTEGER NOT NULL, slug TEXT NOT NULL, label TEXT NOT NULL,
                PRIMARY KEY(snapshot_id, position), UNIQUE(snapshot_id, slug)
            )
        """.trimIndent())
        db.execSQL("CREATE TABLE discover_migrations (uid TEXT NOT NULL PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No Discover database migration from version $oldVersion to $newVersion")
    }
}

internal class SqliteOrganizationsStore(
    private val helper: DiscoverDatabase,
    private val legacySnapshots: (String) -> List<Pair<String, CachedOrganizations>>,
) : OrganizationsCache {
    constructor(context: Context) : this(DiscoverDatabase(context), CachedOrganizationsStore(context)::snapshots)

    override fun read(uid: String, query: OrganizationsQuery): CachedOrganizations? {
        if (uid.isBlank()) return null
        return try {
            helper.writableDatabase.transaction {
                migrate(uid)
                readSnapshot(uid, query.key())
            }
        } catch (_: SQLiteException) {
            null
        }
    }

    override fun save(uid: String, query: OrganizationsQuery, snapshot: CachedOrganizations): Boolean {
        if (uid.isBlank() || !snapshot.isValid()) return false
        return try {
            helper.writableDatabase.transaction {
                migrate(uid)
                writeSnapshot(uid, query.key(), snapshot)
                execSQL("""
                    DELETE FROM discover_snapshots WHERE uid = ? AND id NOT IN (
                        SELECT id FROM discover_snapshots WHERE uid = ? ORDER BY id DESC LIMIT ?
                    )
                """.trimIndent(), arrayOf<Any>(uid, uid, CachedOrganizationsStore.MAX_QUERIES))
            }
            true
        } catch (_: SQLiteException) {
            false
        }
    }

    private fun SQLiteDatabase.migrate(uid: String) {
        val migrated = rawQuery("SELECT 1 FROM discover_migrations WHERE uid = ?", arrayOf(uid))
            .use { it.moveToFirst() }
        if (migrated) return
        legacySnapshots(uid).forEach { (key, snapshot) -> writeSnapshot(uid, key, snapshot) }
        execSQL("INSERT INTO discover_migrations(uid) VALUES (?)", arrayOf(uid))
    }

    private fun SQLiteDatabase.readSnapshot(uid: String, key: String): CachedOrganizations? {
        val (id, total, updatedAt) = rawQuery("""
            SELECT id, total, updated_at FROM discover_snapshots WHERE uid = ? AND query_key = ?
        """.trimIndent(), arrayOf(uid, key)).use {
            if (!it.moveToFirst()) return null
            Triple(it.getLong(0), it.getInt(1), it.getLong(2))
        }
        val args = arrayOf(id.toString())
        val items = rawQuery("""
            SELECT organization_id, name, category, category_slug, members, color, has_upcoming_event,
                is_saved, image_url, verified, next_event_title, next_event_starts_at
            FROM discover_organizations WHERE snapshot_id = ? ORDER BY position
        """.trimIndent(), args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Organization(
                    cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getInt(4), cursor.getString(5), cursor.getInt(6) != 0, cursor.getInt(7) != 0,
                    cursor.getString(8), cursor.getInt(9) != 0, cursor.getString(10), cursor.getString(11),
                ))
            }
        }
        val categories = rawQuery("""
            SELECT slug, label FROM discover_categories WHERE snapshot_id = ? ORDER BY position
        """.trimIndent(), args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
        }
        return CachedOrganizations(items, categories, total, updatedAt).takeIf(CachedOrganizations::isValid)
    }

    private fun SQLiteDatabase.writeSnapshot(uid: String, key: String, snapshot: CachedOrganizations) {
        // Deleting the parent cascades to its children, and the new ID records the write order.
        delete("discover_snapshots", "uid = ? AND query_key = ?", arrayOf(uid, key))
        val id = insertOrThrow("discover_snapshots", null, ContentValues().apply {
            put("uid", uid)
            put("query_key", key)
            put("updated_at", snapshot.updatedAt)
            put("total", snapshot.total)
        })
        compileStatement("""
            INSERT INTO discover_organizations (
                snapshot_id, position, organization_id, name, category, category_slug, members,
                color, has_upcoming_event, is_saved, image_url, verified, next_event_title, next_event_starts_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { insert ->
            snapshot.items.forEachIndexed { position, item ->
                insert.bindLong(1, id)
                insert.bindLong(2, position.toLong())
                insert.bindLong(3, item.id.toLong())
                insert.bindString(4, item.name)
                insert.bindString(5, item.category)
                insert.bindString(6, item.categorySlug)
                insert.bindLong(7, item.members.toLong())
                insert.bindText(8, item.color)
                insert.bindLong(9, if (item.hasUpcomingEvent) 1 else 0)
                insert.bindLong(10, if (item.isSaved) 1 else 0)
                insert.bindText(11, item.imageUrl)
                insert.bindLong(12, if (item.verified) 1 else 0)
                insert.bindText(13, item.nextEventTitle)
                insert.bindText(14, item.nextEventStartsAt)
                insert.executeInsert()
            }
        }
        snapshot.categories.forEachIndexed { position, (slug, label) ->
            execSQL("INSERT INTO discover_categories(snapshot_id, position, slug, label) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(id, position, slug, label))
        }
    }

    private fun SQLiteStatement.bindText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindString(index, value)
    }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            return block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }
}
