package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.analytics.TelemetryFailure
import com.senecapp.data.MyRsosRepository
import com.senecapp.data.Rso
import com.senecapp.data.networkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MyRsosUiState(
    val loading: Boolean = true,
    val mine: List<Rso> = emptyList(),
    val saved: List<Rso> = emptyList(),
    val error: String? = null,
    val fromCache: Boolean = false,
    val cachedAt: Long? = null,
    val offline: Boolean = false,
    val failure: TelemetryFailure? = null,
)

class MyRSOsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MyRsosRepository(application)
    var state by mutableStateOf(MyRsosUiState())
        private set
    private var request: Job? = null
    private var loaded = false
    fun ensureLoaded() { if (!loaded || state.error != null) load() }

    fun load() {
        request?.cancel()
        request = viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val result = repository.load()
                state = MyRsosUiState(
                    loading = false,
                    mine = result.mine,
                    saved = result.saved,
                    fromCache = result.fromCache,
                    cachedAt = result.cachedAt ?: System.currentTimeMillis(),
                    offline = result.fromCache && !repository.online(),
                )
                loaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val offline = !repository.online()
                state = state.copy(
                    loading = false,
                    offline = offline,
                    error = when {
                        offline -> "You are offline and nothing is saved yet. Connect and tap Retry."
                        else -> failure.message ?: "Could not load your groups. Tap Retry."
                    },
                    failure = TelemetryFailure.from(failure),
                )
            }
        }
    }

    fun refresh() = load()

    suspend fun watchConnection() {
        networkStatus(getApplication<Application>()).collect { online ->
            val wasOffline = state.offline
            state = state.copy(offline = !online)
            // Back online after showing saved data or an error: refresh without asking the user.
            if (online && wasOffline && (state.fromCache || state.error != null)) load()
        }
    }
}
