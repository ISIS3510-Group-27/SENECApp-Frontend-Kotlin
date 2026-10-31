package com.senecapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


private const val DB_NAME = "senecapp_rso.db"
private const val DB_VERSION = 1

// Relational storage for My RSOs, the Create RSO catalogue and the offline queues.
// Separate file from the SharedPreferences caches of Discover and Events. Every row is scoped by Firebase uid.
internal class RSODatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE rsos (
                uid TEXT NOT NULL,
                list TEXT NOT NULL CHECK (list IN ('mine', 'saved')),
                group_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                category_slug TEXT NOT NULL,
                member_count INTEGER NOT NULL,
                next_event_title TEXT,
                next_event_starts_at TEXT,
                review_status TEXT NOT NULL,
                rejection_reason TEXT,
                is_member INTEGER NOT NULL,
                is_saved INTEGER NOT NULL,
                color TEXT,
                image_url TEXT,
                verified INTEGER NOT NULL,
                my_role TEXT,
                PRIMARY KEY (uid, list, group_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE categories (slug TEXT PRIMARY KEY, id INTEGER, label TEXT NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE interests (
                id INTEGER PRIMARY KEY,
                slug TEXT,
                name TEXT NOT NULL,
                category_slug TEXT REFERENCES categories(slug)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_groups (
                local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT NOT NULL,
                name TEXT NOT NULL,
                category_slug TEXT NOT NULL REFERENCES categories(slug),
                description TEXT NOT NULL,
                contact_email TEXT,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL CHECK (status IN ('pending', 'sending', 'failed')),
                last_error TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_group_interests (
                local_id INTEGER NOT NULL REFERENCES pending_groups(local_id) ON DELETE CASCADE,
                interest_id INTEGER NOT NULL REFERENCES interests(id),
                PRIMARY KEY (local_id, interest_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_checkins (
                local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT NOT NULL,
                event_id INTEGER NOT NULL,
                code TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL CHECK (status IN ('pending', 'sending', 'failed')),
                last_error TEXT,
                UNIQUE (uid, event_id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. Future versions add their ALTER TABLE / CREATE TABLE steps here.
    }

    companion object {
        @Volatile private var instance: RSODatabase? = null

        fun get(context: Context): RSODatabase = instance ?: synchronized(this) {
            instance ?: RSODatabase(context.applicationContext).also { instance = it }
        }
    }
}
