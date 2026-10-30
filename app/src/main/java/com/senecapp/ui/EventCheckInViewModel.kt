package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.analytics.TelemetryFailure
import com.senecapp.data.CheckInResult
import com.senecapp.data.EventCheckInRepository
import com.senecapp.data.EventCoordinates
import com.senecapp.data.networkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

data class CheckInUiState(
    val eventId: Int? = null,
    val scanning: Boolean = false,
    val submitting: Boolean = false,
    val result: CheckInResult? = null,
    val error: String? = null,
    val locationNote: String? = null,
    val failure: TelemetryFailure? = null,
    val queued: Boolean = false,
)

class EventCheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EventCheckInRepository(application)
    var state by mutableStateOf(CheckInUiState())
        private set
    private var request: Job? = null

    fun startScan(eventId: Int) {
        request?.cancel()
        state = CheckInUiState(eventId = eventId, scanning = true)
    }

    fun scanFailed(eventId: Int, message: String) {
        if (state.eventId != eventId) return
        state = state.copy(scanning = false, error = message)
    }

    fun scanCancelled(eventId: Int) {
        if (state.eventId != eventId) return
        state = state.copy(scanning = false)
    }

    fun checkIn(eventId: Int, code: String, coordinates: EventCoordinates?, locationNote: String?) {
        request?.cancel()
        request = viewModelScope.launch {
            state = CheckInUiState(eventId = eventId, submitting = true, locationNote = locationNote)
            try {
                // Without connection the request is not tried, so the student does not wait for a timeout.
                if (!repository.online()) { queue(eventId, code, coordinates); return@launch }
                val result = repository.checkIn(eventId, code, coordinates)
                state = state.copy(submitting = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                queue(eventId, code, coordinates)
            } catch (failure: Exception) {
                state = state.copy(submitting = false, error = failure.message ?: "Could not check in.",
                    failure = TelemetryFailure.from(failure))
            }
        }
    }

    // Collected while the user is signed in: when the connection returns, saved check-ins are sent.
    suspend fun watchPending(onSent: () -> Unit) {
        networkStatus(getApplication<Application>()).collect { online ->
            if (online) {
                try {
                    if (repository.sendPending() > 0) onSent()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun queue(eventId: Int, code: String, coordinates: EventCoordinates?) {
        try {
            repository.queue(eventId, code, coordinates)
            state = state.copy(submitting = false, queued = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state = state.copy(submitting = false, error = "Could not save your check-in on this phone. Try again.",
                failure = TelemetryFailure.from(failure))
        }
    }

    fun clear() {
        request?.cancel()
        state = CheckInUiState()
    }
}