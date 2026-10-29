package com.senecapp.ui

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.senecapp.data.CampusEvent

// Uses only platform instrumentation so these tests need no additional Gradle dependencies.
// Run: adb shell am instrument -w com.senecapp.test/com.senecapp.ui.EventDetailsCacheInstrumentation
class EventDetailsCacheInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val tests = listOf("hitAndMiss" to ::hitAndMiss, "expiration" to ::expiration,
            "hitsDoNotExtendExpiration" to ::hitsDoNotExtendExpiration, "lruEviction" to ::lruEviction,
            "invalidationAndClear" to ::invalidationAndClear)
        val report = StringBuilder()
        var failures = 0
        tests.forEach { (name, test) ->
            try { test(); report.append("PASS $name\n")
            } catch (failure: Throwable) {
                failures++
                report.append("FAIL $name: $failure\n")
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()); putInt("failures", failures); putInt("tests", tests.size) })
    }

    private fun event(id: Int = 1) = CampusEvent(id, "Event", "Club", "2026-11-01T14:00:00Z", null, null, false)

    private fun hitAndMiss() {
        val logs = mutableListOf<String>()
        val cache = EventDetailsMemoryCache(clock = { 1000L }, log = logs::add)
        check(cache.get(1) == null)
        cache.put(event())
        check(cache.get(1) == event())
        check(logs == listOf("MISS", "HIT"))
    }

    private fun expiration() {
        var now = 1000L
        val logs = mutableListOf<String>()
        val cache = EventDetailsMemoryCache(clock = { now }, log = logs::add)
        cache.put(event())
        now += 59_999L
        check(cache.get(1) == event())
        now++
        check(cache.get(1) == null)
        check(logs.last() == "EXPIRED")
        check(cache.get(1) == null)
        check(logs.last() == "MISS")
        cache.put(event())
        check(cache.get(1) == event())
    }

    private fun hitsDoNotExtendExpiration() {
        var now = 1000L
        val cache = EventDetailsMemoryCache(clock = { now }, log = {})
        cache.put(event())
        now += 30_000L
        check(cache.get(1) == event())
        now += 30_000L
        check(cache.get(1) == null)
    }

    private fun lruEviction() {
        val logs = mutableListOf<String>()
        val cache = EventDetailsMemoryCache(clock = { 1000L }, log = logs::add)
        (1..20).forEach { cache.put(event(it)) }
        check(cache.get(1) == event(1))
        cache.put(event(21))
        check(logs.count { it == "EVICTION" } == 1)
        check(cache.get(2) == null)
        (3..21).forEach { check(cache.get(it) == event(it)) }
        check(cache.get(1) == event(1))
    }

    private fun invalidationAndClear() {
        val cache = EventDetailsMemoryCache(clock = { 1000L }, log = {})
        cache.put(event(1)); cache.put(event(2))
        cache.invalidate(1)
        check(cache.get(1) == null)
        check(cache.get(2) == event(2))
        cache.clear()
        check(cache.get(2) == null)
    }
}
