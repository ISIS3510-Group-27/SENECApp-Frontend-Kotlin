package com.senecapp.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class SavedItineraryStore(private val directory: File) {
    // AtomicFile recovers interrupted writes. A shared lock also excludes concurrent readers/writers.
    suspend fun save(uid: String, plan: List<CampusEvent>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = file(uid)
            val bytes = ItineraryJson.encode(plan).toByteArray(Charsets.UTF_8)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    suspend fun read(uid: String): List<CampusEvent>? = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = file(uid)
            val encoded = try { file.readFully() } catch (_: FileNotFoundException) { return@withLock null }
            ItineraryJson.decode(encoded.toString(Charsets.UTF_8))
        }
    }

    private fun file(uid: String) = AtomicFile(File(directory, itineraryFileName(uid)))

    private companion object { val lock = Mutex() }
}

internal fun itineraryFileName(uid: String): String {
    require(uid.isNotBlank())
    val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
    return "itinerary-${digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }}.json"
}

// Only fields needed to display the saved plan. The order is the order produced by buildItinerary().
internal object ItineraryJson {
    fun encode(plan: List<CampusEvent>): String = JSONObject().put("version", 1)
        .put("items", JSONArray(plan.map { event ->
            JSONObject().put("id", event.id).put("title", event.title).put("group", event.group)
                .put("startsAt", event.startsAt).put("endsAt", event.endsAt)
                .put("building", event.building ?: JSONObject.NULL)
                .put("color", event.color ?: JSONObject.NULL)
        })).toString()

    fun decode(encoded: String): List<CampusEvent> {
        val root = JSONObject(encoded)
        require(root.getInt("version") == 1)
        val items = root.getJSONArray("items")
        return List(items.length()) { index ->
            val row = items.getJSONObject(index)
            CampusEvent(row.getInt("id"), row.getString("title"), row.getString("group"),
                row.getString("startsAt"), if (row.isNull("building")) null else row.getString("building"),
                description = null, cancelled = false, endsAt = row.getString("endsAt"),
                color = if (row.isNull("color")) null else row.getString("color"))
        }
    }
}
