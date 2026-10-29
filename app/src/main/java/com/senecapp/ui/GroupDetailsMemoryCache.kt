package com.senecapp.ui

import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.senecapp.BuildConfig
import com.senecapp.data.GroupDetails

internal interface GroupDetailsCache {
    fun get(id: Int): GroupDetails?
    fun put(detail: GroupDetails)
    fun invalidate(id: Int)
    fun clear()
}

// Session-owned previews; permission-sensitive actions still need a fresh server response.
internal class GroupDetailsMemoryCache(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val log: (String) -> Unit = { if (BuildConfig.DEBUG) Log.d("GroupDetailsCache", it) },
) : GroupDetailsCache {
    private data class Entry(val detail: GroupDetails, val fetchedAt: Long)
    private val entries = object : LruCache<Int, Entry>(20) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Entry, newValue: Entry?) {
            if (evicted) log("EVICTION")
        }
    }

    override fun get(id: Int): GroupDetails? {
        val entry = entries.get(id)
        if (entry == null) { log("MISS"); return null }
        if (clock() - entry.fetchedAt >= 60_000L) {
            entries.remove(id); log("EXPIRED"); return null
        }
        log("HIT")
        return entry.detail
    }

    override fun put(detail: GroupDetails) { entries.put(detail.group.id, Entry(detail, clock())) }
    override fun invalidate(id: Int) { entries.remove(id) }
    override fun clear() { entries.evictAll() }
}
