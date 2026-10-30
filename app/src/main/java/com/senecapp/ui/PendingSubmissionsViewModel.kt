package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.CreateGroupRepository
import com.senecapp.data.EventCheckInRepository
import com.senecapp.data.PendingCheckIn
import com.senecapp.data.PendingGroup
import com.senecapp.data.isOnline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PendingSubmissionsUiState(
    val loading: Boolean = true,
    val groups: List<PendingGroup> = emptyList(),
    val checkIns: List<PendingCheckIn> = emptyList(),
    val sending: Boolean = false,
    val message: String? = null,
)

class PendingSubmissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val groupsRepository = CreateGroupRepository(application)
    private val checkInsRepository = EventCheckInRepository(application)
    var state by mutableStateOf(PendingSubmissionsUiState())
        private set
    private var request: Job? = null

    fun load() {
        request?.cancel()
        request = viewModelScope.launch {
            try {
                state = state.copy(loading = false, groups = groupsRepository.pending(),
                    checkIns = checkInsRepository.pending())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(loading = false, message = "Could not read the submissions saved on this phone.")
            }
        }
    }

    // "Send now": a failed item goes back to the queue and both queues are sent right away.
    fun sendNow(group: PendingGroup? = null, checkIn: PendingCheckIn? = null, onSent: () -> Unit) {
        if (state.sending) return
        if (!isOnline(getApplication<Application>())) {
            state = state.copy(message = "You are still offline. It will be sent when you are back online.")
            return
        }
        viewModelScope.launch {
            state = state.copy(sending = true, message = null)
            try {
                group?.let { groupsRepository.retry(it.localId) }
                checkIn?.let { checkInsRepository.retry(it.localId) }
                val sent = groupsRepository.sendPending() + checkInsRepository.sendPending()
                if (sent > 0) onSent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(message = "Could not send now. It stays saved and will be retried.")
            } finally {
                state = state.copy(sending = false)
                load()
            }
        }
    }

    fun discard(group: PendingGroup? = null, checkIn: PendingCheckIn? = null, onDiscarded: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                group?.let { groupsRepository.discard(it.localId) }
                checkIn?.let { checkInsRepository.discard(it.localId) }
                onDiscarded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(message = "Could not discard it. Try again.")
            }
            load()
        }
    }
}
