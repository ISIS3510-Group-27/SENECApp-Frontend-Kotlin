package com.senecapp.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.CampusEvent
import com.senecapp.data.CampusEventsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

data class CampusEventsUiState(val loading: Boolean = true, val items: List<CampusEvent> = emptyList(),
    val total: Int = 0, val error: String? = null, val selected: CampusEvent? = null,
    val detailLoading: Boolean = false, val detailError: String? = null)

class CampusEventsViewModel : ViewModel() {
    private val repository = CampusEventsRepository()
    var state by mutableStateOf(CampusEventsUiState())
        private set
    private var listRequest: Job? = null
    private var detailRequest: Job? = null

    fun load(mine: Boolean) {
        listRequest?.cancel()
        listRequest = viewModelScope.launch {
            state = state.copy(loading = true, items = emptyList(), error = null)
            try {
                val (items, total) = repository.load(mine)
                state = state.copy(loading = false, items = items, total = total)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: IOException) {
                state = state.copy(loading = false, error = "Cannot reach the backend. Tap Retry.")
            } catch (failure: Exception) {
                state = state.copy(loading = false, error = failure.message ?: "Could not load events.")
            }
        }
    }

    fun open(event: CampusEvent) {
        openId(event.id, "events", event)
    }

    fun openId(id: Int, source: String = "events", preview: CampusEvent? = null, requestId: String? = null) {
        detailRequest?.cancel()
        state = state.copy(selected = preview, detailLoading = true, detailError = null)
        detailRequest = viewModelScope.launch {
            try {
                val fresh = repository.detail(id, source, requestId)
                state = state.copy(selected = fresh, detailLoading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                state = state.copy(detailLoading = false, detailError = "Could not update event details. Tap Retry.")
            }
        }
    }

    fun checkedIn(id: Int) {
        state = state.copy(selected = state.selected?.let { if (it.id == id) it.copy(checkedIn = true) else it },
            items = state.items.map { if (it.id == id) it.copy(checkedIn = true) else it })
    }

    fun close() {
        detailRequest?.cancel()
        state = state.copy(selected = null, detailLoading = false, detailError = null)
    }
}
