package com.senecapp.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal sealed interface AnalyticsDelivery {
    data class Acknowledged(val accepted: Int, val duplicates: Int) : AnalyticsDelivery
    data class Permanent(val status: Int) : AnalyticsDelivery
    data object Retry : AnalyticsDelivery
}

internal fun interface AnalyticsTransport {
    suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery
}

internal class AnonymousAnalyticsTransport(private val endpoint: String,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) : AnalyticsTransport {
    override suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery {
        val connection = connect(URL(endpoint))
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-App", "kotlin")
            connection.setRequestProperty("X-Platform", "android")
            val first = events.first()
            connection.setRequestProperty("X-Session-Id", first.sessionId)
            connection.setRequestProperty("X-App-Version", first.metadata.appVersion)
            connection.setRequestProperty("X-Device-Model", first.metadata.deviceModel)
            connection.setRequestProperty("X-OS-Version", first.metadata.osVersion)
            connection.doOutput = true
            val array = JSONArray()
            events.forEach { array.put(it.json()) }
            connection.outputStream.use { it.write(JSONObject().put("events", array).toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            return when {
                status in 200..299 -> {
                    val ack = try {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(body).let { AnalyticsDelivery.Acknowledged(it.getInt("accepted"), it.getInt("duplicates")) }
                    } catch (_: Exception) { return AnalyticsDelivery.Retry }
                    if (ack.accepted >= 0 && ack.duplicates >= 0 && ack.accepted.toLong() + ack.duplicates == events.size.toLong()) ack
                    else AnalyticsDelivery.Retry
                }
                status in setOf(408, 425, 429) || status >= 500 -> AnalyticsDelivery.Retry
                else -> AnalyticsDelivery.Permanent(status)
            }
        } finally { connection.disconnect() }
    }
}

internal class AnalyticsPipeline(private val store: AnalyticsOutboxStore, private val transport: AnalyticsTransport,
    private val clock: () -> Long = System::currentTimeMillis, private val log: (String) -> Unit = {}) {
    private val queueLock = Mutex()
    private val flushLock = Mutex()
    private var nextAttempt = 0L

    suspend fun enqueue(event: AnalyticsEvent) {
        try {
            val persisted = queueLock.withLock {
                val rows = store.read()
                if (rows.any { it.event.id == event.id }) false else store.write(rows + QueuedAnalyticsEvent(event))
            }
            if (persisted) {
                log("QUEUED ${event.name} screen=${event.screen.wireName} event_id=${event.id}")
                flush()
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Telemetry must never fail the application. */ }
    }

    suspend fun flush(force: Boolean = false) {
        if (!flushLock.tryLock()) return
        try {
            if (!force && clock() < nextAttempt) return
            // Bound a flush even if new events are being continuously inserted.
            repeat(10) {
                val batch = queueLock.withLock { store.read().filterNot { it.quarantined }.take(50).map { it.event } }
                if (batch.isEmpty()) return
                if (!deliver(batch)) { nextAttempt = clock() + 30_000L; return }
            }
            nextAttempt = 0L
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { nextAttempt = clock() + 30_000L
        } finally { flushLock.unlock() }
    }

    private suspend fun deliver(batch: List<AnalyticsEvent>): Boolean {
        val result = transport.send(batch)
        val ids = batch.map { it.id }.toSet()
        return when (result) {
            is AnalyticsDelivery.Acknowledged -> {
                if (result.accepted < 0 || result.duplicates < 0 || result.accepted.toLong() + result.duplicates != batch.size.toLong()) return false
                val saved = queueLock.withLock { store.write(store.read().filterNot { it.event.id in ids }) }
                if (saved) log("ACK accepted=${result.accepted} duplicates=${result.duplicates}")
                saved
            }
            is AnalyticsDelivery.Permanent -> {
                if (result.status in setOf(413, 422) && batch.size > 1) {
                    val halves = batch.chunked((batch.size + 1) / 2)
                    deliver(halves[0]) && deliver(halves[1])
                } else {
                    // Keep rejected events locally but exclude them from automatic retries.
                    val saved = queueLock.withLock {
                        store.write(store.read().map { if (it.event.id in ids) it.copy(quarantined = true) else it })
                    }
                    if (saved) log("QUARANTINED count=${batch.size} status=${result.status}")
                    saved
                }
            }
            AnalyticsDelivery.Retry -> false
        }
    }
}
