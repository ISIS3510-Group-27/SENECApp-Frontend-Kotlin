package com.senecapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CachedOrganizationsStoreTest {
    private val query = OrganizationsQuery()
    private val organization = Organization(7, "AI Club", "Technology", "technology", 30,
        "#123456", true, true, "/cover.jpg", true, "Talk", "2026-11-01T14:00:00Z")
    private val snapshot = CachedOrganizations(listOf(organization, organization.copy(id = 8, color = null,
        imageUrl = null, nextEventTitle = null, nextEventStartsAt = null, isSaved = false)),
        listOf("technology" to "Technology"), 2, 1234L)

    @Test fun newStoreRestoresEveryFieldAndCategories() {
        val preferences = EventsTestPreferences()
        assertTrue(CachedOrganizationsStore(preferences.value).save("a", query, snapshot))
        assertEquals(snapshot, CachedOrganizationsStore(preferences.value).read("a", query))
    }

    @Test fun accountsAndEveryFilterHaveIndependentSnapshots() {
        val store = CachedOrganizationsStore(EventsTestPreferences().value)
        store.save("a", query, snapshot)
        val variants = listOf(query.copy(text = "AI"), query.copy(category = "technology"),
            query.copy(upcomingOnly = true), query.copy(filters = GroupFilters(sort = "name")),
            query.copy(filters = GroupFilters(verified = true)), query.copy(filters = GroupFilters(interests = setOf(1))),
            query.copy(filters = GroupFilters(buildings = setOf("ML"))))
        variants.forEach { assertNull(store.read("a", it)) }
        assertNull(store.read("b", query))
        store.save("b", query, snapshot.copy(items = emptyList(), total = 0))
        assertEquals(snapshot, store.read("a", query))
        assertTrue(store.read("b", query)!!.items.isEmpty())
    }

    @Test fun trimmedSearchAndReorderedSetsUseTheSameSnapshot() {
        val store = CachedOrganizationsStore(EventsTestPreferences().value)
        val first = query.copy(text = " AI ", filters = GroupFilters(interests = linkedSetOf(2, 1), buildings = linkedSetOf("SD", "ML")))
        val second = first.copy(text = "AI", filters = GroupFilters(interests = linkedSetOf(1, 2), buildings = linkedSetOf("ML", "SD")))
        store.save("a", first, snapshot)
        assertEquals(snapshot, store.read("a", second))
    }

    @Test fun invalidOwnerVersionCountsAndCorruptDataAreCacheMisses() {
        val preferences = EventsTestPreferences()
        val store = CachedOrganizationsStore(preferences.value)
        assertNull(store.read("a", query))
        store.save("a", query, snapshot)
        val valid = preferences.strings.getValue("discover.a")
        val wrongCount = JSONObject(valid).apply { getJSONArray("snapshots").getJSONObject(0).put("total", 99) }
        val wrongTime = JSONObject(valid).apply { getJSONArray("snapshots").getJSONObject(0).put("updatedAt", 0) }
        val wrongQuery = JSONObject(valid).apply { getJSONArray("snapshots").getJSONObject(0).put("query", "other") }
        for (raw in listOf("not JSON", "{}", JSONObject(valid).put("uid", "b").toString(),
            JSONObject(valid).put("version", 2).toString(), wrongCount.toString(), wrongTime.toString(), wrongQuery.toString())) {
            preferences.strings["discover.a"] = raw
            assertNull(store.read("a", query))
        }
    }

    @Test fun incompleteOrDuplicateSnapshotsCannotReplaceGoodData() {
        val store = CachedOrganizationsStore(EventsTestPreferences().value)
        store.save("a", query, snapshot)
        assertFalse(store.save("a", query, snapshot.copy(total = 3)))
        assertFalse(store.save("a", query, snapshot.copy(items = listOf(organization, organization))))
        assertFalse(store.save("a", query, snapshot.copy(categories = snapshot.categories + snapshot.categories)))
        assertEquals(snapshot, store.read("a", query))
    }

    @Test fun emptyResultsReplacePreviousResults() {
        val store = CachedOrganizationsStore(EventsTestPreferences().value)
        val empty = snapshot.copy(items = emptyList(), total = 0, updatedAt = 2345L)
        store.save("a", query, snapshot)
        assertTrue(store.save("a", query, empty))
        assertEquals(empty, store.read("a", query))
    }

    @Test fun recentSearchesAreBoundedAndUpdatingOneDoesNotDuplicateIt() {
        val preferences = EventsTestPreferences()
        val store = CachedOrganizationsStore(preferences.value)
        repeat(CachedOrganizationsStore.MAX_QUERIES + 1) { store.save("a", query.copy(text = "$it"), snapshot) }
        assertNull(store.read("a", query.copy(text = "0")))
        assertNotNull(store.read("a", query.copy(text = "1")))
        store.save("a", query.copy(text = "1"), snapshot.copy(updatedAt = 2345L))
        assertEquals(CachedOrganizationsStore.MAX_QUERIES,
            JSONObject(preferences.strings.getValue("discover.a")).getJSONArray("snapshots").length())
        assertEquals(2345L, store.read("a", query.copy(text = "1"))!!.updatedAt)
    }

    @Test fun failedPersistenceKeepsThePreviousSnapshot() {
        val preferences = EventsTestPreferences()
        val store = CachedOrganizationsStore(preferences.value)
        store.save("a", query, snapshot)
        preferences.commitSucceeds = false
        assertFalse(store.save("a", query, snapshot.copy(updatedAt = 2345L)))
        assertEquals(snapshot, store.read("a", query))
    }
}
