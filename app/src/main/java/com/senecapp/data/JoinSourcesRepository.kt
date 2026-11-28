package com.senecapp.data

import android.util.LruCache
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal data class JoinSourcesView(
    val current: JoinSourcesVisit,
    val previous: JoinSourcesVisit?,
    val fromPhone: Boolean,
) {
    fun newJoins(source: JoinSource): Int? {
        val baseline = previous?.data?.takeIf { it.days == current.data.days } ?: return null
        return source.joins - (baseline.sources.firstOrNull { it.entryPoint == source.entryPoint }?.joins ?: 0)
    }
}

internal interface JoinSourcesMemory {
    fun get(key: JoinSourcesKey): JoinSourcesView?
    fun put(key: JoinSourcesKey, value: JoinSourcesView)
    fun remove(key: JoinSourcesKey)
}

internal class JoinSourcesLru : JoinSourcesMemory {
    private val entries = LruCache<JoinSourcesKey, JoinSourcesView>(8)
    override fun get(key: JoinSourcesKey): JoinSourcesView? = entries.get(key)
    override fun put(key: JoinSourcesKey, value: JoinSourcesView) { entries.put(key, value) }
    override fun remove(key: JoinSourcesKey) { entries.remove(key) }
}

internal fun interface JoinSourcesWatch {
    fun watch(key: JoinSourcesKey, online: Boolean, force: Boolean): Flow<JoinSourcesView>
}

internal class JoinSourcesOffline : IllegalStateException("A connection is needed to load join sources for the first time.")

internal class JoinSourcesRepository(
    private val storage: JoinSourcesStorage,
    private val fetch: suspend (Int) -> JoinSources,
    private val currentAccount: () -> String?,
    private val memory: JoinSourcesMemory = JoinSourcesLru(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : JoinSourcesWatch {
    override fun watch(key: JoinSourcesKey, online: Boolean, force: Boolean): Flow<JoinSourcesView> =
        updates(key, online, force)
            .catch { failure ->
                if (failure is CancellationException) throw failure
                // Carry the error through the IO channel so an already queued disk result is delivered first.
                emit(Result.failure(failure))
            }
            .flowOn(io)
            .map { it.getOrThrow() }

    private fun updates(key: JoinSourcesKey, online: Boolean, force: Boolean) = flow<Result<JoinSourcesView>> {
        checkSession(key)
        if (online && !force) {
            val kept = memory.get(key)?.takeIf { clock() - it.current.takenAt in 0 until 120_000L }
            if (kept != null) {
                emit(Result.success(kept))
                return@flow
            }
        }

        coroutineScope {
            // Capture failures until the disk copy has been emitted. Cancellation still propagates.
            val request = if (online) async {
                try {
                    Result.success(fetch(key.groupId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
            } else null

            // Both operations run on IO, even when getting the Firebase token does not suspend.
            val history = async { optionalStorage { storage.history(key) }.orEmpty() }.await()
            checkSession(key)
            val previous = previousJoinVisit(history, clock())
            val saved = history.firstOrNull()
            if (saved != null) emit(Result.success(JoinSourcesView(saved, previous, fromPhone = true)))
            if (request == null) {
                if (saved == null) throw JoinSourcesOffline()
                return@coroutineScope
            }

            val response = request.await()
            checkSession(key)
            val failure = response.exceptionOrNull()
            if (failure != null) {
                if (joinSourcesAccessLost(failure)) {
                    memory.remove(key)
                    optionalStorage { storage.clear(key) }
                }
                throw failure
            }
            val now = clock()
            val current = JoinSourcesVisit(now, response.getOrThrow())
            optionalStorage { storage.save(key, current) }
            checkSession(key)
            val view = JoinSourcesView(current, previousJoinVisit(history, now), fromPhone = false)
            memory.put(key, view)
            emit(Result.success(view))
        }
    }

    private suspend fun checkSession(key: JoinSourcesKey) {
        currentCoroutineContext().ensureActive()
        if (currentAccount() != key.account) throw SessionChangedException()
    }

    private inline fun <T> optionalStorage(action: () -> T): T? = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

internal fun joinSourcesAccessLost(error: Throwable) = error is SessionRequiredException ||
    error is SessionChangedException || (error is ApiHttpException && error.status in setOf(401, 403, 404))
