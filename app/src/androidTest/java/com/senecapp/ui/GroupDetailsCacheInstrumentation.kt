package com.senecapp.ui

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.senecapp.data.GroupDetails
import com.senecapp.data.Rso

// adb shell am instrument -w com.senecapp.test/com.senecapp.ui.GroupDetailsCacheInstrumentation
class GroupDetailsCacheInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val tests = listOf("hitAndMiss" to ::hitAndMiss, "expiration" to ::expiration,
            "lruEviction" to ::lruEviction, "invalidationAndClear" to ::invalidationAndClear)
        val report = StringBuilder()
        var failures = 0
        for ((name, test) in tests) {
            try { test(); report.append("PASS $name\n") }
            catch (failure: Throwable) { failures++; report.append("FAIL $name: $failure\n") }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()); putInt("tests", tests.size); putInt("failures", failures) })
    }

    private fun group(id: Int = 1) = GroupDetails(Rso(id, "Club $id", "Technology", "technology", 10,
        null, null, "approved", null, false, false, null), "About", emptyList(), emptyList(), null, null, null, null, null, null)

    private fun hitAndMiss() {
        val logs = mutableListOf<String>()
        val cache = GroupDetailsMemoryCache(clock = { 1000L }, log = logs::add)
        check(cache.get(1) == null)
        cache.put(group())
        check(cache.get(1) == group())
        check(logs == listOf("MISS", "HIT"))
    }
    private fun expiration() {
        var now = 1000L
        val cache = GroupDetailsMemoryCache(clock = { now }, log = {})
        cache.put(group())
        now += 30_000L; check(cache.get(1) == group())
        now += 29_999L; check(cache.get(1) == group())
        now++; check(cache.get(1) == null)
    }
    private fun lruEviction() {
        val logs = mutableListOf<String>()
        val cache = GroupDetailsMemoryCache(clock = { 1000L }, log = logs::add)
        (1..20).forEach { cache.put(group(it)) }
        check(cache.get(1) != null)
        cache.put(group(21))
        check(cache.get(2) == null)
        check(cache.get(1) != null)
        check(logs.count { it == "EVICTION" } == 1)
        (3..21).forEach { check(cache.get(it) != null) }
    }
    private fun invalidationAndClear() {
        val cache = GroupDetailsMemoryCache(clock = { 1000L }, log = {})
        cache.put(group(1)); cache.put(group(2)); cache.invalidate(1)
        check(cache.get(1) == null); check(cache.get(2) != null)
        cache.clear(); check(cache.get(2) == null)
    }
}
