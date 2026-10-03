package com.senecapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.CheckInResult
import com.senecapp.data.EventCheckInRepository
import com.senecapp.data.EventCoordinates
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
)

class EventCheckInViewModel : ViewModel() {
    private val repository = EventCheckInRepository()
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
                val result = repository.checkIn(eventId, code, coordinates)
                state = state.copy(submitting = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = state.copy(submitting = false, error = "Cannot reach the backend. Try again.")
            } catch (failure: Exception) {
                state = state.copy(submitting = false, error = failure.message ?: "Could not check in.")
            }
        }
    }

    fun clear() {
        request?.cancel()
        state = CheckInUiState()
    }
}