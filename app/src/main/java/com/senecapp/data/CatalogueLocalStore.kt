package com.senecapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

// DAO for the categories and interests tables, the catalogue the Create RSO form needs offline.
// The catalogue is public data, so it is shared by every account on the phone.
internal class CatalogueLocalStore(private val database: RSODatabase) {

    // Update-or-insert instead of delete-and-insert: pending proposals reference these rows with foreign keys,
    // so a row that is still used can never be removed by a refresh.
    fun save(categories: List<Category>, interests: List<Interest>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            categories.forEach { category ->
                upsert(db, "categories", "slug = ?", arrayOf(category.slug), ContentValues().apply {
                    put("slug", category.slug)
                    put("id", category.id)
                    put("label", category.label)
                })
            }
            interests.forEach { interest ->
                upsert(db, "interests", "id = ?", arrayOf(interest.id.toString()), ContentValues().apply {
                    put("id", interest.id)
                    put("slug", interest.slug)
                    put("name", interest.name)
                    // An interest whose category is not stored yet keeps a null link instead of breaking the key.
                    put("category_slug", interest.categorySlug?.takeIf { slug -> categories.any { it.slug == slug } })
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun categories(): List<Category> = database.readableDatabase
        .rawQuery("SELECT id, slug, label FROM categories ORDER BY label", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Category(cursor.getInt(0), cursor.getString(1), cursor.getString(2)))
            }
        }

    fun interests(): List<Interest> = database.readableDatabase
        .rawQuery("SELECT id, slug, name, category_slug FROM interests ORDER BY name", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Interest(cursor.getInt(0), cursor.getString(1).orEmpty(), cursor.getString(2),
                        if (cursor.isNull(3)) null else cursor.getString(3)))
                }
            }
        }

    private fun upsert(db: SQLiteDatabase, table: String, where: String, args: Array<String>, values: ContentValues) {
        if (db.update(table, values, where, args) == 0) db.insertOrThrow(table, null, values)
    }
}
