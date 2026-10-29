package com.senecapp.analytics

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TelemetryCrashHandlerTest {
    private val prefs = CrashTestPreferences()
    private val store = PendingCrashStore(prefs.value)
    private val original = PendingCrash(UUID.randomUUID().toString(), "2026-10-29T12:00:00Z", AnalyticsScreen.EVENTS, "IllegalStateException")
    private fun event(crash: PendingCrash) = AnalyticsEvent.crash(crash, testSession, testMetadata)
    private fun handler(previous: Thread.UncaughtExceptionHandler?, screen: () -> AnalyticsScreen = { AnalyticsScreen.EVENTS }) =
        TelemetryCrashHandler(previous, store, screen, clock = { Instant.parse(original.occurredAt) }, newId = { original.id })

    @Test fun commitsMinimalRecordBeforeCallingPreviousHandlerWithTheOriginalFailure() {
        val failure = IllegalStateException("password=secret token=private email=person@example.com")
        var called = false
        handler({ thread, received ->
            assertSame(Thread.currentThread(), thread)
            assertSame(failure, received)
            assertEquals(original, PendingCrashStore(prefs.restart().value).read().single())
            called = true
        }).uncaughtException(Thread.currentThread(), failure)
        assertTrue(called)
        val json = store.read().single().json()
        assertEquals(setOf("event_id", "occurred_at", "screen", "error_type", "fatal"), json.keys().asSequence().toSet())
        listOf("password", "secret", "token", "email", "stack", "message", "person").forEach { assertFalse(json.toString().contains(it)) }
    }

    @Test fun threadUncaughtExceptionUsesTheHandlerAndCapturesErrorsToo() {
        var received: Throwable? = null
        val thread = Thread { throw AssertionError("private contents") }
        thread.uncaughtExceptionHandler = handler({ _, failure -> received = failure })
        thread.start(); thread.join(2_000)
        assertFalse(thread.isAlive)
        assertTrue(received is AssertionError)
        assertEquals("AssertionError", store.read().single().errorType)
    }

    @Test fun throwingScreenSupplierStillRecordsUnknown() {
        handler({ _, _ -> }, screen = { throw AssertionError("broken supplier") })
            .uncaughtException(Thread.currentThread(), IllegalStateException())
        assertEquals(AnalyticsScreen.UNKNOWN, store.read().single().screen)
    }

    @Test fun storageExceptionsAndErrorsCannotPreventDelegation() {
        listOf(IllegalStateException("storage"), OutOfMemoryError("storage")).forEach { error ->
            prefs.editFailure = error
            var called = false
            handler({ _, _ -> called = true }).uncaughtException(Thread.currentThread(), IllegalStateException())
            assertTrue(called)
        }
    }

    @Test fun failedCommitStillInvokesPreviousHandler() {
        prefs.commitSucceeds = false
        var called = false
        handler({ _, _ -> called = true }).uncaughtException(Thread.currentThread(), IllegalStateException())
        assertTrue(called)
        assertTrue(PendingCrashStore(prefs.restart().value).read().isEmpty())
    }

    @Test fun previousHandlerExceptionsAreNotSwallowed() {
        val previousFailure = AssertionError("previous")
        try {
            handler({ _, _ -> throw previousFailure }).uncaughtException(Thread.currentThread(), IllegalStateException())
            fail("Previous handler must run normally")
        } catch (received: AssertionError) { assertSame(previousFailure, received) }
        assertEquals(original, store.read().single())
    }

    @Test fun missingPreviousHandlerUsesTerminationFallback() {
        var terminated = false
        TelemetryCrashHandler(null, store, { AnalyticsScreen.UNKNOWN }, terminate = { terminated = true })
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(terminated)
        assertEquals(1, store.read().size)
    }

    @Test fun concurrentCrashesCommitOnceBeforeEitherThreadDelegates() {
        val committing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delegated = AtomicInteger()
        prefs.beforeCommit = { committing.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
        val handler = handler({ _, _ -> delegated.incrementAndGet() })
        val first = Thread { handler.uncaughtException(Thread.currentThread(), IllegalStateException()) }
        val second = Thread { handler.uncaughtException(Thread.currentThread(), IllegalStateException()) }
        first.start()
        try {
            assertTrue(committing.await(2, TimeUnit.SECONDS))
            second.start()
            assertEquals(0, delegated.get())
        } finally { release.countDown(); first.join(2_000); second.join(2_000) }
        assertEquals(2, delegated.get())
        assertEquals(1, prefs.commits)
        assertEquals(listOf(original), store.read())
    }

    @Test fun installationWrapsPreviousOnlyOnce() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val delegate = Thread.UncaughtExceptionHandler { _, _ -> }
        try {
            Thread.setDefaultUncaughtExceptionHandler(delegate)
            TelemetryCrashHandler.install(store) { AnalyticsScreen.UNKNOWN }
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            assertTrue(installed is TelemetryCrashHandler)
            TelemetryCrashHandler.install(store) { AnalyticsScreen.PROFILE }
            assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
    }

    @Test fun restartTransfersOriginalIdentityAndTimeOnlyAfterDurableOutboxCommit() {
        assertTrue(store.save(original))
        val restored = PendingCrashStore(prefs.restart().value)
        val outboxPrefs = CrashTestPreferences()
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        outboxPrefs.beforeCommit = { assertEquals(listOf(original), restored.read()) }
        assertEquals(1, restored.recoverInto(outbox, ::event))
        assertTrue(restored.read().isEmpty())
        val durable = AnalyticsOutboxStore { outboxPrefs.restart().value }.read().single().event
        assertEquals(event(original), durable)
        assertTrue(durable.fatal)
        assertEquals(0, restored.recoverInto(outbox, ::event))
    }

    @Test fun failedOutboxCommitRetainsMarkerAndIsDurablyRetriedEvenIfUuidExistsInMemory() {
        store.save(original)
        val outboxPrefs = CrashTestPreferences().apply { commitSucceeds = false }
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        assertEquals(0, store.recoverInto(outbox, ::event))
        assertEquals(listOf(original), store.read())
        assertEquals(1, outbox.read().size) // Android may update memory despite commit(false).
        assertTrue(AnalyticsOutboxStore { outboxPrefs.restart().value }.read().isEmpty())
        outboxPrefs.commitSucceeds = true
        assertEquals(1, store.recoverInto(outbox, ::event))
        assertTrue(store.read().isEmpty())
        assertEquals(listOf(event(original)), AnalyticsOutboxStore { outboxPrefs.restart().value }.read().map { it.event })
    }

    @Test fun failureDeletingMarkerIsSafeAndRecoveryDoesNotDuplicateQueuedUuid() {
        store.save(original)
        prefs.commitSucceeds = false
        val outboxPrefs = CrashTestPreferences()
        val stableOutbox = AnalyticsOutboxStore { outboxPrefs.value }
        assertEquals(1, store.recoverInto(stableOutbox, ::event))
        val restored = PendingCrashStore(prefs.restart().value)
        assertEquals(listOf(original), restored.read())
        assertEquals(1, restored.recoverInto(stableOutbox, ::event))
        assertEquals(listOf(event(original)), stableOutbox.read().map { it.event })
        assertTrue(restored.read().isEmpty())
    }

    @Test fun repeatedDeliveryUsesOriginalUuidAndBackendDuplicateAcknowledgment() = runTest {
        store.save(original)
        prefs.commitSucceeds = false // Simulate termination after outbox commit, before marker removal.
        val outboxPrefs = CrashTestPreferences()
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        store.recoverInto(outbox, ::event)
        val databaseIds = mutableSetOf<String>()
        val received = mutableListOf<AnalyticsEvent>()
        val transport = object : AnalyticsTransport {
            override suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery {
                received.addAll(events)
                val accepted = events.count { databaseIds.add(it.id) }
                return AnalyticsDelivery.Acknowledged(accepted, events.size - accepted)
            }
        }
        AnalyticsPipeline(outbox, transport).flush(force = true)
        PendingCrashStore(prefs.restart().value).recoverInto(outbox, ::event)
        AnalyticsPipeline(outbox, transport).flush(force = true)
        assertEquals(listOf(original.id, original.id), received.map { it.id })
        assertEquals(setOf(original.id), databaseIds)
        assertTrue(received.all { it.occurredAt == original.occurredAt && it.fatal })
        assertTrue(outbox.read().isEmpty())
    }

    @Test fun legacyNonfatalOutboxSurvivesRecoveryAndBothKindsAreDelivered() = runTest {
        store.save(original)
        val outboxPrefs = CrashTestPreferences()
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        val nonfatal = AnalyticsEvent.error(AnalyticsScreen.EVENTS, testSession, testMetadata, TelemetryFailure.from(IllegalStateException())!!)
        outbox.write(listOf(QueuedAnalyticsEvent(nonfatal)))
        store.recoverInto(outbox, ::event)
        val received = mutableListOf<AnalyticsEvent>()
        AnalyticsPipeline(outbox, object : AnalyticsTransport {
            override suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery {
                received.addAll(events)
                return AnalyticsDelivery.Acknowledged(events.size, 0)
            }
        }).flush(force = true)
        assertEquals(listOf(nonfatal, event(original)), received)
        assertFalse(received.first().fatal)
    }

    @Test fun offlineRecoveredCrashStaysQueuedUntilDeliveryAfterReconnect() = runTest {
        store.save(original)
        val outboxPrefs = CrashTestPreferences()
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        store.recoverInto(outbox, ::event)
        var connected = false
        val received = mutableListOf<AnalyticsEvent>()
        val transport = object : AnalyticsTransport {
            override suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery {
                received.addAll(events)
                if (!connected) throw java.io.IOException("Offline")
                return AnalyticsDelivery.Acknowledged(events.size, 0)
            }
        }
        AnalyticsPipeline(outbox, transport).flush(force = true)
        assertEquals(event(original), AnalyticsOutboxStore { outboxPrefs.restart().value }.read().single().event)
        connected = true
        AnalyticsPipeline(outbox, transport).flush(force = true)
        assertEquals(listOf(event(original), event(original)), received)
        assertTrue(outbox.read().isEmpty())
    }

    @Test fun malformedMarkersAreSkippedWithoutDeletingThemOrBlockingValidRecords() {
        prefs.value.edit().putString("crash.bad", "broken").commit()
        prefs.value.edit().putString("crash.wrong", original.json().toString()).commit()
        store.save(original)
        val outboxPrefs = CrashTestPreferences()
        assertEquals(1, store.recoverInto(AnalyticsOutboxStore { outboxPrefs.value }, ::event))
        assertTrue(store.read().isEmpty())
        assertEquals(setOf("crash.bad", "crash.wrong"), prefs.value.all.keys)
    }

    @Test fun multipleCrashesAreRetainedSeparatelyAndRecoveryHonorsExistingOutboxCapacity() {
        val second = original.copy(id = UUID.randomUUID().toString())
        store.save(original); store.save(second)
        val outboxPrefs = CrashTestPreferences()
        val outbox = AnalyticsOutboxStore { outboxPrefs.value }
        outbox.write(List(500) { QueuedAnalyticsEvent(testEvent()) })
        assertEquals(2, store.recoverInto(outbox, ::event))
        assertEquals(500, outbox.read().size)
        assertEquals(setOf(original.id, second.id), outbox.read().takeLast(2).map { it.event.id }.toSet())
    }

    @Test fun crashParserRejectsSensitiveFieldsAndInvalidShapes() {
        val invalid = listOf(original.json().put("message", "private"), original.json().put("fatal", false),
            original.json().put("fatal", "true"), original.json().put("error_type", "has spaces"),
            original.json().put("event_id", "invalid"), original.json().put("occurred_at", "invalid"),
            original.json().put("screen", "not tracked"))
        invalid.forEach { json ->
            try { PendingCrash.read(json); fail("Invalid crash accepted") } catch (_: Exception) { }
        }
        assertEquals(original, PendingCrash.read(JSONObject(original.json().toString())))
    }
}

// Models Android's memory-vs-disk behavior: even commit(false) changes memory, but not durable data.
internal class CrashTestPreferences(private val disk: MutableMap<String, String> = mutableMapOf()) {
    private val memory = disk.toMutableMap()
    var commitSucceeds = true
    var editFailure: Throwable? = null
    var beforeCommit: () -> Unit = {}
    var commits = 0
    fun restart() = CrashTestPreferences(disk)
    val value: SharedPreferences = proxy(SharedPreferences::class.java) { method, args ->
        when (method) {
            "getAll" -> memory.toMap()
            "getString" -> memory[args[0]] ?: args[1]
            "edit" -> { editFailure?.let { throw it }; editor() }
            else -> error("Unexpected preference operation: $method")
        }
    }
    private fun editor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, String?>()
        lateinit var editor: SharedPreferences.Editor
        editor = proxy(SharedPreferences.Editor::class.java) { method, args ->
            when (method) {
                "putString" -> { pending[args[0] as String] = args[1] as String?; editor }
                "remove" -> { pending[args[0] as String] = null; editor }
                "commit" -> {
                    beforeCommit(); commits++
                    pending.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
                    if (commitSucceeds) { disk.clear(); disk.putAll(memory) }
                    commitSucceeds
                }
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
