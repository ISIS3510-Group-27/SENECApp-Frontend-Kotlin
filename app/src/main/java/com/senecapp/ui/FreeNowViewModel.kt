package com.senecapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.FreeNowRepository
import com.senecapp.data.FreeNowSuggestion
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class FreeNowUiState(
    val loading: Boolean = true,
    val result: FreeNowSuggestion? = null,
    val demoMode: Boolean = false,
    val error: String? = null,
    val openError: String? = null,
)

class FreeNowViewModel : ViewModel() {
    private val repository = FreeNowRepository()
    var state by mutableStateOf(FreeNowUiState())
        private set
    private var request: Job? = null

    fun load() = loadAt(null)

    fun loadDemoNoon() {
        val campusZone = TimeZone.getTimeZone("America/Bogota")
        val noon = Calendar.getInstance(campusZone).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = campusZone
        }.format(noon.time)
        loadAt(timestamp)
    }

    private fun loadAt(at: String?) {
        request?.cancel()
        request = viewModelScope.launch {
            state = FreeNowUiState(demoMode = at != null)
            try {
                state = FreeNowUiState(loading = false, result = repository.suggestions(at), demoMode = at != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = FreeNowUiState(loading = false, demoMode = at != null,
                    error = "Cannot reach the backend. Start it and tap Retry.")
            } catch (failure: Exception) {
                state = FreeNowUiState(loading = false, demoMode = at != null,
                    error = failure.message ?: "Could not load suggestions")
            }
        }
    }

    fun openEvent(eventId: Int) {
        val requestId = state.result?.requestId ?: return
        viewModelScope.launch {
            state = state.copy(openError = null)
            try {
                repository.openEvent(eventId, requestId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(openError = "Could not record the event view.")
            }
        }
    }
}
