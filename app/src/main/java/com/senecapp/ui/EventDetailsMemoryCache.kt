package com.senecapp.ui

import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.senecapp.BuildConfig
import com.senecapp.data.CampusEvent

internal interface EventDetailsCache {
    fun get(id: Int): CampusEvent?
    fun put(event: CampusEvent)
    fun invalidate(id: Int)
    fun clear()
}

// One instance belongs to one session's Events ViewModel. Entries are informational only.
internal class EventDetailsMemoryCache(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val log: (String) -> Unit = { status ->
        if (BuildConfig.DEBUG) Log.d("EventDetailsCache", status)
    },
) : EventDetailsCache {
    private data class Entry(val event: CampusEvent, val fetchedAt: Long)

    private val entries = object : LruCache<Int, Entry>(20) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Entry, newValue: Entry?) {
            if (evicted) log("EVICTION")
        }
    }

    override fun get(id: Int): CampusEvent? {
        val entry = entries.get(id)
        if (entry == null) {
            log("MISS")
            return null
        }
        if (clock() - entry.fetchedAt >= 60_000L) {
            entries.remove(id)
            log("EXPIRED")
            return null
        }
        log("HIT")
        return entry.event
    }

    override fun put(event: CampusEvent) {
        entries.put(event.id, Entry(event, clock()))
    }

    override fun invalidate(id: Int) { entries.remove(id) }
    override fun clear() { entries.evictAll() }
}
