package com.senecapp.data

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

// adb shell am instrument -w com.senecapp.test/com.senecapp.data.SprintStorageInstrumentation
class SprintStorageInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val tests = listOf("SQLite reopen, keys and rollback" to ::sqlite,
            "ObjectBox reopen, isolation, TTL and capacity" to ::objectBox,
            "Private itinerary and interrupted write" to ::itinerary)
        var failures = 0
        val report = StringBuilder()
        for ((name, test) in tests) {
            try {
                runBlocking(Dispatchers.IO) { test() }
                report.appendLine("PASS $name")
            } catch (failure: Throwable) {
                failures++
                report.appendLine("FAIL $name: ${failure.stackTraceToString()}")
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()); putInt("failures", failures); putInt("tests", tests.size) })
    }

    private val event = CampusEvent(3, "Demo", "Club", "2026-12-01T12:00:00Z", "ML", "About", false,
        groupId = 9, checkedIn = true, capacity = 12, locationDetail = "Room 5", attendeeCount = 6)

    private suspend fun sqlite() {
        val name = "test-event-details-${UUID.randomUUID()}.db"
        try {
            EventDetailsDatabase(targetContext, name).use { helper ->
                val store = SavedEventDetailsStore(helper)
                store.save("userA", event)
                store.save("userB", event.copy(title = "Other", group = "Other club"))
                val db = helper.writableDatabase
                val orphan = runCatching {
                    db.execSQL("UPDATE event_details SET group_id = -1 WHERE uid = 'userA'")
                }
                check(orphan.exceptionOrNull() is SQLiteConstraintException)
                db.execSQL("""
                    CREATE TRIGGER reject_event BEFORE INSERT ON event_details WHEN NEW.event_id = 99
                    BEGIN SELECT RAISE(ABORT, 'test rollback'); END
                """.trimIndent())
                check(runCatching { store.save("userA", event.copy(id = 99, group = "Not committed")) }.isFailure)
                check(store.read("userA", 99) == null)
                check(store.read("userA", event.id)?.group == "Club")
            }
            EventDetailsDatabase(targetContext, name).use { helper ->
                val store = SavedEventDetailsStore(helper)
                check(store.read("userA", event.id) == event.copy(checkedIn = false))
                check(store.read("userB", event.id)?.title == "Other")
                check(store.read("userC", event.id) == null)
            }
        } finally {
            targetContext.deleteDatabase(name)
        }
    }

    private suspend fun objectBox() = inTestDirectory { directory ->
        val item = GroupRecommendation(7, "Club", "Tech", "Description", 30, listOf("Interests"), false,
            imageUrl = "/media/club.png")
        var now = 5_000L
        ProfileRecommendationBox.open(targetContext, directory).use { db ->
            val store = ProfileRecommendationsStore({ db }, { now })
            store.save("userA", GroupRecommendations("not persisted", listOf(item)))
            store.save("userB", GroupRecommendations("not persisted", listOf(item.copy(id = 8))))
        }
        ProfileRecommendationBox.open(targetContext, directory).use { db ->
            val store = ProfileRecommendationsStore({ db }, { now })
            check(store.read("userA") == GroupRecommendations("", listOf(item)))
            check(store.read("userB")?.items?.single()?.id == 8)
            check(store.read("usera") == null)
            now += 24 * 60 * 60 * 1000L
            check(store.read("userA") == null)
            repeat(9) { i ->
                now++
                store.save("recent$i", GroupRecommendations("", List(12) { item }))
            }
            check(store.read("recent0") == null)
            check(store.read("recent8")?.items?.size == 10)
            check(db.boxFor(ProfileRecommendationRecord::class.java).count() == 8L)
        }
    }

    private suspend fun itinerary() = inTestDirectory { directory ->
        SavedItineraryStore(directory).save("userA", listOf(event))
        val restored = SavedItineraryStore(directory)
        check(restored.read("userB") == null)
        check(restored.read("userA")?.single()?.title == event.title)
        val atomic = AtomicFile(File(directory, itineraryFileName("userA")))
        // Simulate interruption after a partial write without a successful finishWrite.
        atomic.startWrite().use { it.write("{partial".toByteArray()) }
        check(restored.read("userA")?.single()?.title == event.title)
        restored.save("userA", listOf(event.copy(title = "Updated")))
        check(restored.read("userA")?.single()?.title == "Updated")
    }

    private suspend fun inTestDirectory(block: suspend (File) -> Unit) {
        val dir = File(targetContext.cacheDir, "sprint-storage-${UUID.randomUUID()}").apply { mkdirs() }
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
