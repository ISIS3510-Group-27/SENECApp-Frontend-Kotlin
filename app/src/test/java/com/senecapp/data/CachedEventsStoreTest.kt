package com.senecapp.data

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class CachedEventsStoreTest {
    private val event = CampusEvent(8, "Meetup", "AI Club", "2026-11-01T14:00:00Z", "ML", "About",
        false, "2026-11-01T15:00:00Z", 12, "#123456", true, 8, 30, "Room 101")
    private val snapshot = CachedEvents(listOf(event, event.copy(id = 9, building = null, description = null,
        cancelled = true, color = null, checkedIn = false, capacity = null, locationDetail = null)), 2, 1234L)

    @Test fun newStoreInstanceReadsEveryFieldIncludingNulls() {
        val preferences = EventsTestPreferences()
        assertTrue(CachedEventsStore(preferences.value).save("a", false, snapshot))
        assertEquals(snapshot, CachedEventsStore(preferences.value).read("a", false))
    }

    @Test fun accountsAndQueriesHaveSeparateSnapshots() {
        val store = CachedEventsStore(EventsTestPreferences().value)
        val empty = CachedEvents(emptyList(), 0, 2345L)
        store.save("a", false, snapshot)
        store.save("a", true, empty)
        store.save("b", false, empty)
        assertEquals(snapshot, store.read("a", false))
        assertEquals(empty, store.read("a", true))
        assertEquals(empty, store.read("b", false))
        assertNull(store.read("b", true))
        assertNull(store.read("c", false))
    }

    @Test fun missingCorruptWrongOwnerWrongQueryAndUnsupportedSnapshotsAreMisses() {
        val preferences = EventsTestPreferences()
        val store = CachedEventsStore(preferences.value)
        assertNull(store.read("a", false))
        store.save("a", false, snapshot)
        val key = preferences.strings.keys.single()
        val valid = preferences.strings.getValue(key)
        listOf("not json", "{}", JSONObject(valid).put("uid", "b").toString(),
            JSONObject(valid).put("mine", true).toString(), JSONObject(valid).put("version", 2).toString(),
            JSONObject(valid).put("total", 99).toString(), JSONObject(valid).put("updatedAt", 0).toString()).forEach {
            preferences.strings[key] = it
            assertNull(store.read("a", false))
        }
    }

    @Test fun incompleteSnapshotCannotOverwriteExistingData() {
        val store = CachedEventsStore(EventsTestPreferences().value)
        store.save("a", false, snapshot)
        assertFalse(store.save("a", false, snapshot.copy(total = 3)))
        assertEquals(snapshot, store.read("a", false))
    }

    @Test fun failedDiskCommitIsReported() {
        val preferences = EventsTestPreferences().apply { commitSucceeds = false }
        assertFalse(CachedEventsStore(preferences.value).save("a", false, snapshot))
    }
}

// Implements only the preference operations the store uses, without an Android runtime dependency.
internal class EventsTestPreferences {
    val strings = mutableMapOf<String, String>()
    var commitSucceeds = true
    val value: SharedPreferences = proxy(SharedPreferences::class.java) { method, args ->
        when (method) {
            "getString" -> strings[args[0]] ?: args[1]
            "edit" -> editor()
            else -> error("Unexpected preference operation: $method")
        }
    }

    private fun editor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, String>()
        lateinit var editor: SharedPreferences.Editor
        editor = proxy(SharedPreferences.Editor::class.java) { method, args ->
            when (method) {
                "putString" -> { pending[args[0] as String] = args[1] as String; editor }
                "commit" -> { if (commitSucceeds) strings.putAll(pending); commitSucceeds }
                else -> error("Unexpected editor operation: $method")
            }
        }
        return editor
    }

    private fun <T : Any> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T =
        requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        }))
}
