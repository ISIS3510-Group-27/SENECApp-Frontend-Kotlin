package com.senecapp.analytics

import android.content.SharedPreferences
import android.os.Process
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

// Only these five fields survive the crash. No Throwable, thread name, message or stack is retained.
internal data class PendingCrash(val id: String, val occurredAt: String, val screen: AnalyticsScreen, val errorType: String) {
    fun json(): JSONObject = JSONObject().put("event_id", id).put("occurred_at", occurredAt)
        .put("screen", screen.wireName).put("error_type", errorType).put("fatal", true)

    companion object {
        fun read(json: JSONObject): PendingCrash {
            require(json.keys().asSequence().toSet() == setOf("event_id", "occurred_at", "screen", "error_type", "fatal"))
            require(json.get("fatal") == true)
            val id = json.getString("event_id").also { require(UUID.fromString(it).toString() == it) }
            val time = json.getString("occurred_at").also { Instant.parse(it) }
            val screen = AnalyticsScreen.entries.single { it.wireName == json.getString("screen") }
            val type = json.getString("error_type").also { require(it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,99}"))) }
            return PendingCrash(id, time, screen, type)
        }
    }
}

internal class PendingCrashStore(private val preferences: SharedPreferences) {
    fun save(crash: PendingCrash): Boolean =
        preferences.edit().putString(PREFIX + crash.id, crash.json().toString()).commit()

    fun read(): List<PendingCrash> = preferences.all.mapNotNull { (key, value) ->
        if (!key.startsWith(PREFIX) || value !is String) return@mapNotNull null
        try { PendingCrash.read(JSONObject(value)).takeIf { key == PREFIX + it.id } }
        catch (_: Exception) { null } // A corrupt marker cannot prevent startup or recovery of other records.
    }

    // Called on IO before ANY pipeline operation, including enqueue. This needs no second queue lock.
    // Even an existing UUID is committed again: a previous commit(false) can change in-memory prefs.
    fun recoverInto(outbox: AnalyticsOutboxStore, event: (PendingCrash) -> AnalyticsEvent): Int {
        var recovered = 0
        for (crash in read()) {
            try {
                val rows = outbox.read()
                val updated = if (rows.any { it.event.id == crash.id }) rows else rows + QueuedAnalyticsEvent(event(crash))
                if (outbox.write(updated)) {
                    recovered++
                    preferences.edit().remove(PREFIX + crash.id).commit()
                }
                // Failed writes/removals leave the marker for next startup; UUID deduplication is retained.
            } catch (_: Exception) { /* Keep the original record and continue starting the application. */ }
        }
        return recovered
    }

    private companion object { const val PREFIX = "crash." }
}

internal class TelemetryCrashHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val store: PendingCrashStore,
    private val screen: () -> AnalyticsScreen,
    private val clock: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val terminate: () -> Unit = { Process.killProcess(Process.myPid()); exitProcess(10) }
) : Thread.UncaughtExceptionHandler {
    private var recorded = false

    override fun uncaughtException(thread: Thread, failure: Throwable) {
        try {
            // Concurrent crashing threads wait for the first synchronous commit before delegating.
            synchronized(this) {
                if (!recorded) {
                    recorded = true
                    val current = try { screen() } catch (_: Throwable) { AnalyticsScreen.UNKNOWN }
                    val type = failure.javaClass.simpleName
                        .takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,99}")) } ?: "Throwable"
                    store.save(PendingCrash(newId(), clock().toString(), current, type))
                }
            }
        } catch (_: Throwable) {
            // Best effort even under memory/storage failure. Never throw another telemetry exception.
        } finally {
            if (previous != null) previous.uncaughtException(thread, failure) else terminate()
        }
    }

    companion object {
        fun install(store: PendingCrashStore, screen: () -> AnalyticsScreen) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            if (previous !is TelemetryCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(TelemetryCrashHandler(previous, store, screen))
            }
        }
    }
}
