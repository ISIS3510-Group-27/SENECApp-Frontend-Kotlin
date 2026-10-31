package com.senecapp.data

import android.util.Log
import android.util.LruCache
import android.util.SparseArray
import com.senecapp.BuildConfig


internal class InterestSuggestionCache(
    private val log: (String) -> Unit = { if (BuildConfig.DEBUG) Log.d("InterestSuggestions", it) },
) {
    // Replaced as a whole (never edited in place), so a reader never sees half an index.
    @Volatile private var index = SparseArray<NormalizedInterest>()
    @Volatile private var catalogueIds = emptyList<Int>()
    private val results = object : LruCache<String, List<Int>>(32) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: List<Int>, newValue: List<Int>?) {
            if (evicted) log("EVICTION")
        }
    }

    // Called when the catalogue arrives. A different catalogue invalidates every stored result.
    fun setCatalogue(interests: List<Interest>) {
        val ids = interests.map { it.id }
        if (ids == catalogueIds) return
        val next = SparseArray<NormalizedInterest>(interests.size)
        interests.forEach { next.put(it.id, InterestInference.prepare(it)) }
        index = next
        catalogueIds = ids
        results.evictAll()
    }

    // CPU work: the caller runs it on Dispatchers.Default.
    fun suggest(name: String, description: String): List<Interest> {
        val current = index
        if (current.size() == 0) return emptyList()
        val key = InterestInference.normalize("$name $description")
        val ids = results.get(key)?.also { log("HIT") } ?: run {
            log("MISS")
            val candidates = List(current.size()) { current.valueAt(it) }
            InterestInference.rank(key, candidates).map { it.id }.also { results.put(key, it) }
        }
        return ids.mapNotNull { current.get(it)?.interest }
    }
}
