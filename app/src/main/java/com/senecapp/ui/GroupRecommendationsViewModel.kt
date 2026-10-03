package com.senecapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.GroupRecommendations
import com.senecapp.data.GroupRecommendationsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GroupRecommendationsUiState(
    val loading: Boolean = true,
    val result: GroupRecommendations? = null,
    val error: String? = null,
    val actionError: String? = null,
    val joiningGroupId: Int? = null,
    val joinedGroupIds: Set<Int> = emptySet(),
)

class GroupRecommendationsViewModel : ViewModel() {
    private val repository = GroupRecommendationsRepository()
    var state by mutableStateOf(GroupRecommendationsUiState())
        private set
    private var loadRequest: Job? = null

    fun load() {
        loadRequest?.cancel()
        loadRequest = viewModelScope.launch {
            state = GroupRecommendationsUiState()
            try {
                state = GroupRecommendationsUiState(loading = false, result = repository.load())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = GroupRecommendationsUiState(loading = false,
                    error = "Cannot reach the backend. Start it and tap Retry.")
            } catch (failure: Exception) {
                state = GroupRecommendationsUiState(loading = false,
                    error = failure.message ?: "Could not load recommendations")
            }
        }
    }

    fun openGroup(groupId: Int) {
        val requestId = state.result?.requestId ?: return
        state = state.copy(actionError = null)
        viewModelScope.launch {
            try {
                repository.openGroup(groupId, requestId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(actionError = "Could not record the group view.")
            }
        }
    }

    fun joinGroup(groupId: Int) {
        val requestId = state.result?.requestId ?: return
        if (state.joiningGroupId != null || groupId in state.joinedGroupIds) return
        state = state.copy(joiningGroupId = groupId, actionError = null)
        viewModelScope.launch {
            try {
                repository.joinGroup(groupId, requestId)
                state = state.copy(joiningGroupId = null,
                    joinedGroupIds = state.joinedGroupIds + groupId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(joiningGroupId = null,
                    actionError = failure.message ?: "Could not join this group")
            }
        }
    }
}
