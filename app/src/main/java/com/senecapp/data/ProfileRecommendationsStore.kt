package com.senecapp.data

import android.content.Context
import io.objectbox.BoxStore
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal interface RecommendationStorage {
    suspend fun read(uid: String): GroupRecommendations?
    suspend fun save(uid: String, result: GroupRecommendations)
}

// One ObjectBox document per account. Only fields needed to render offline suggestions are stored.
internal class ProfileRecommendationsStore(
    private val database: () -> BoxStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : RecommendationStorage {
    constructor(context: Context) : this({ ProfileRecommendationDatabase.get(context.applicationContext) })

    override suspend fun read(uid: String): GroupRecommendations? = withContext(Dispatchers.IO) {
        val record = find(database(), uid) ?: return@withContext null
        if (!isFresh(record.savedAt, clock())) return@withContext null
        val items = JSONArray(record.payload)
        GroupRecommendations(requestId = "", items = List(items.length()) { i ->
            val item = items.getJSONObject(i)
            val reasons = item.getJSONArray("reasons")
            GroupRecommendation(item.getInt("id"), item.getString("name"), item.getString("category"),
                item.getString("description"), item.getInt("memberCount"),
                List(reasons.length()) { reasons.getString(it) }, item.getBoolean("isMember"),
                item.textOrNull("color"), item.textOrNull("imageUrl"), item.getBoolean("verified"))
        })
    }

    override suspend fun save(uid: String, result: GroupRecommendations) = withContext(Dispatchers.IO) {
        val encoded = JSONArray(result.items.take(10).map { item ->
            JSONObject().put("id", item.id).put("name", item.name).put("category", item.category)
                .put("description", item.description).put("memberCount", item.memberCount)
                .put("reasons", JSONArray(item.reasons)).put("isMember", item.isMember)
                .put("color", item.color).put("imageUrl", cacheableRecommendationImage(item.imageUrl))
                .put("verified", item.verified)
        }).toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= 128 * 1024)
        writes.withLock {
            val store = database()
            val box = store.boxFor(ProfileRecommendationRecord::class.java)
            store.runInTx {
                val now = clock()
                val record = (find(store, uid) ?: ProfileRecommendationRecord()).apply {
                    ownerUid = uid
                    savedAt = now
                    payload = encoded
                }
                // Prune expired snapshots and keep at most seven other recent accounts.
                val others = box.all.filter { it.ownerUid != uid }.sortedByDescending { it.savedAt }
                val retained = others.filter { isFresh(it.savedAt, now) }.take(7).map { it.id }.toSet()
                others.filter { it.id !in retained }.forEach { box.remove(it.id) }
                box.put(record)
            }
        }
    }

    private fun find(store: BoxStore, uid: String): ProfileRecommendationRecord? {
        require(uid.isNotBlank())
        return ProfileRecommendationBox.find(store, uid)
    }

    private fun isFresh(savedAt: Long, now: Long) = now - savedAt in 0 until 24 * 60 * 60 * 1000L

    private companion object { val writes = Mutex() }
}

// Persist public image references, leaving potentially signed URLs or credentials out of the document.
internal fun cacheableRecommendationImage(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        (uri.scheme == null || uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            !uri.rawPath.isNullOrEmpty()
    }
}

private object ProfileRecommendationDatabase {
    private var store: BoxStore? = null

    @Synchronized
    fun get(context: Context): BoxStore = store ?: ProfileRecommendationBox.open(
        context, context.noBackupFilesDir.resolve("profile-recommendations")
    ).also { store = it }
}
