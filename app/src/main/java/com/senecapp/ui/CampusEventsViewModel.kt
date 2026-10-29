package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import com.senecapp.auth.invalidFirebaseSession
import com.senecapp.data.ApiHttpException
import com.senecapp.data.CachedEventsStore
import com.senecapp.data.CampusEvent
import com.senecapp.data.CampusEventsRepository
import com.senecapp.data.CampusEventsSource
import com.senecapp.data.isEventsConnectivityFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class CampusEventsUiState(val loading: Boolean = true, val items: List<CampusEvent> = emptyList(),
    val total: Int = 0, val error: String? = null, val selected: CampusEvent? = null,
    val detailLoading: Boolean = false, val detailError: String? = null,
    val fromCache: Boolean = false, val cachedAt: Long? = null, val refreshWarning: String? = null)

class CampusEventsViewModel internal constructor(application: Application,
    private val repository: CampusEventsSource, private val detailsCache: EventDetailsCache,
    private val userId: () -> String?) : AndroidViewModel(application) {
    constructor(application: Application) : this(application,
        CampusEventsRepository(CachedEventsStore(application)), EventDetailsMemoryCache(), FirebaseIdTokenProvider::userId)

    private val ownerUid = userId()
    var state by mutableStateOf(CampusEventsUiState())
        private set
    private var listRequest: Job? = null
    private var detailRequest: Job? = null
    private var detailEventId: Int? = null
    private var attendanceVersion = 0L
    private var listMine: Boolean? = null
    private var hasSnapshot = false

    fun load(mine: Boolean) {
        if (ownerUid == null || userId() != ownerUid) {
            clearSessionData()
            return
        }
        if (listRequest?.isActive == true && listMine == mine) return
        listRequest?.cancel()
        if (listMine != mine) {
            hasSnapshot = false
            state = state.copy(items = emptyList(), total = 0, fromCache = false, cachedAt = null)
        }
        listMine = mine
        listRequest = viewModelScope.launch {
            state = state.copy(loading = true, error = null, refreshWarning = null)
            try {
                val result = repository.load(mine)
                currentCoroutineContext().ensureActive()
                if (userId() != ownerUid) { clearSessionData(); return@launch }
                // Keep the newer in-memory snapshot, including server-confirmed check-ins.
                state = if (result.fromCache && hasSnapshot) state.copy(loading = false, fromCache = true)
                else state.copy(loading = false, items = result.items, total = result.total,
                    fromCache = result.fromCache, cachedAt = result.cachedAt,
                    refreshWarning = if (result.cacheWriteFailed) "Events loaded, but could not be saved for offline use." else null)
                hasSnapshot = true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                when {
                    userId() != ownerUid || failure is SessionChangedException || failure is SessionRequiredException ||
                        invalidFirebaseSession(failure) || (failure is ApiHttpException && failure.status in setOf(401, 403)) ->
                        clearSessionData(failure.message ?: "Your session has ended. Sign in again.")
                    isEventsConnectivityFailure(failure) && hasSnapshot ->
                        state = state.copy(loading = false, fromCache = true)
                    else -> state = state.copy(loading = false, error = if (isEventsConnectivityFailure(failure))
                        "Cannot reach the backend and no saved events are available. Connect and tap Retry."
                        else failure.message ?: "Could not load events. Tap Retry.")
                }
            }
        }
    }

    private fun clearSessionData(message: String = "The signed-in account changed. Please retry.") {
        listRequest?.cancel()
        detailRequest?.cancel()
        detailsCache.clear()
        detailEventId = null
        hasSnapshot = false
        state = CampusEventsUiState(loading = false, error = message)
    }

    fun open(event: CampusEvent) {
        openId(event.id, "events", event)
    }

    fun openId(id: Int, source: String = "events", preview: CampusEvent? = null, requestId: String? = null) {
        detailRequest?.cancel()
        if (ownerUid == null || userId() != ownerUid) {
            clearSessionData()
            return
        }
        detailEventId = id
        val cached = if (source == "events") detailsCache.get(id) else null
        if (cached != null) {
            state = state.copy(selected = cached, detailLoading = false, detailError = null)
            return
        }
        state = state.copy(selected = preview, detailLoading = true, detailError = null)
        detailRequest = viewModelScope.launch {
            try {
                var fresh: CampusEvent
                do {
                    val version = attendanceVersion
                    fresh = repository.detail(id, source, requestId)
                    currentCoroutineContext().ensureActive()
                    if (userId() != ownerUid) { clearSessionData(); return@launch }
                    // A response started before a confirmed check-in may have old attendance counts.
                } while (version != attendanceVersion)
                if (source == "events") detailsCache.put(fresh)
                state = state.copy(selected = fresh, detailLoading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (userId() != ownerUid) { clearSessionData(); return@launch }
                if (failure is SessionChangedException || failure is SessionRequiredException || invalidFirebaseSession(failure) ||
                    (failure is ApiHttpException && failure.status in setOf(401, 403))) {
                    detailsCache.clear()
                }
                state = state.copy(detailLoading = false, detailError = "Could not update event details. Tap Retry.")
            }
        }
    }

    fun checkedIn(id: Int) {
        detailsCache.invalidate(id)
        if (detailEventId == id) attendanceVersion++
        state = state.copy(selected = state.selected?.let { if (it.id == id) it.copy(checkedIn = true) else it },
            items = state.items.map { if (it.id == id) it.copy(checkedIn = true) else it })
    }

    fun close() {
        detailRequest?.cancel()
        detailEventId = null
        state = state.copy(selected = null, detailLoading = false, detailError = null)
    }

    override fun onCleared() {
        detailsCache.clear()
        super.onCleared()
    }
}
