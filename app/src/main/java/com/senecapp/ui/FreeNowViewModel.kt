package com.senecapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.FreeNowRepository
import com.senecapp.data.FreeNowSuggestion
import com.senecapp.data.EventCoordinates
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
    val locationOptIn: Boolean? = null,
    val consentLoading: Boolean = false,
    val locationMessage: String? = null,
)

class FreeNowViewModel : ViewModel() {
    private val repository = FreeNowRepository()
    var state by mutableStateOf(FreeNowUiState())
        private set
    private var request: Job? = null
    private var consentRequest: Job? = null
    private var selectedTime: String? = null
    private var scheduleResult: FreeNowSuggestion? = null

    fun load() {
        if (state.locationOptIn == null && !state.consentLoading) refreshConsent()
        loadAt(null)
    }

    fun refreshConsent() {
        if (consentRequest?.isActive == true) return
        consentRequest = viewModelScope.launch {
            state = state.copy(consentLoading = true)
            try {
                state = state.copy(locationOptIn = repository.locationConsent(), locationMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(locationOptIn = null, locationMessage = "Could not read location consent. Schedule suggestions remain available.")
            } finally {
                state = state.copy(consentLoading = false)
            }
        }
    }

    fun setLocationConsent(enabled: Boolean) {
        if (state.consentLoading) return
        request?.cancel()
        // Stop using coordinates immediately, even if saving consent fails.
        state = state.copy(locationOptIn = null, consentLoading = true, locationMessage = null,
            result = scheduleResult, loading = false)
        consentRequest = viewModelScope.launch {
            try {
                state = state.copy(locationOptIn = repository.setLocationConsent(enabled))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(locationMessage = "Could not save consent. Retry reading the profile before using GPS.")
            } finally {
                state = state.copy(consentLoading = false)
            }
            loadAt(selectedTime)
        }
    }

    fun useLocation(coordinates: EventCoordinates?, permissionGranted: Boolean) {
        if (coordinates == null || !permissionGranted || state.locationOptIn != true || state.consentLoading) {
            locationUnavailable("Location unavailable or not allowed. Using your schedule.")
            return
        }
        loadAt(selectedTime, coordinates)
    }

    fun locationUnavailable(message: String) {
        state = state.copy(locationMessage = message)
        loadAt(selectedTime)
    }

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

    private fun loadAt(at: String?, coordinates: EventCoordinates? = null) {
        if (at != selectedTime) scheduleResult = null
        selectedTime = at
        request?.cancel()
        request = viewModelScope.launch {
            state = state.copy(loading = true, error = null, openError = null, demoMode = at != null,
                result = if (coordinates == null) scheduleResult else state.result)
            try {
                val result = try {
                    repository.suggestions(at, coordinates)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (coordinates == null) throw failure
                    state = state.copy(locationMessage = "GPS suggestions failed. Using your schedule instead.")
                    state = state.copy(result = scheduleResult)
                    repository.suggestions(at)
                }
                if (result.locationSource != "gps") scheduleResult = result
                state = state.copy(loading = false, result = result, demoMode = at != null,
                    locationMessage = if (coordinates != null && result.locationSource == "gps") null else state.locationMessage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = state.copy(loading = false, demoMode = at != null,
                    error = "Cannot reach the backend. Start it and tap Retry.")
            } catch (failure: Exception) {
                state = state.copy(loading = false, demoMode = at != null,
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
