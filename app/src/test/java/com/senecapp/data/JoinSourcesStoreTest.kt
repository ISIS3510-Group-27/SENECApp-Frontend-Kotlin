package com.senecapp.data

import io.objectbox.BoxStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JoinSourcesStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = JoinSourcesKey("leader", 7)
    private val data = JoinSources("Question", "Answer", "Campus", 90, 10, 1, listOf(
        JoinSource("recommendation", 7, 1, 1.0 / 7, 1.0, null),
        JoinSource("direct", 3, 0, 0.0, null, 0.25),
    ))

    @Test fun successiveLoadsReplaceWithinSixHoursAndRoundTripInOrder() {
        withStore { store, box ->
            val first = JoinSourcesVisit(1L, data)
            val latest = JoinSourcesVisit(JOIN_VISIT_INTERVAL_MS, data.copy(joins = 2))
            val next = JoinSourcesVisit(2 * JOIN_VISIT_INTERVAL_MS, data)
            store.save(key, first)
            store.save(key, latest)
            assertEquals(listOf(latest), store.history(key))
            assertEquals(2L, box.boxFor(JoinSnapshotSourceRecord::class.java).count())
            store.save(key, next)
            assertEquals(listOf(next, latest), store.history(key))
            assertEquals(latest, previousJoinVisit(store.history(key), next.takenAt))
        }
    }

    @Test fun keepsTwelvePerAccountAndGroupAndRemovesTheirSourceRows() {
        withStore { store, box ->
            val otherAccount = key.copy(account = "different")
            val otherGroup = key.copy(groupId = 8)
            val first = JoinSourcesVisit(1, data)
            store.save(otherAccount, first)
            store.save(otherGroup, first)
            repeat(14) { index ->
                store.save(key, JoinSourcesVisit((index + 1L) * JOIN_VISIT_INTERVAL_MS, data))
            }
            assertEquals((14 downTo 3).map { it * JOIN_VISIT_INTERVAL_MS }, store.history(key).map { it.takenAt })
            assertEquals(listOf(first), store.history(otherAccount))
            assertEquals(listOf(first), store.history(otherGroup))
            assertEquals(28L, box.boxFor(JoinSnapshotSourceRecord::class.java).count())
            store.clear(key)
            assertTrue(store.history(key).isEmpty())
            assertEquals(4L, box.boxFor(JoinSnapshotSourceRecord::class.java).count())
        }
    }

    private fun withStore(test: (JoinSourcesStore, BoxStore) -> Unit) {
        MyObjectBox.builder().directory(folder.newFolder()).build().use { box ->
            test(JoinSourcesStore { box }, box)
        }
    }
}
