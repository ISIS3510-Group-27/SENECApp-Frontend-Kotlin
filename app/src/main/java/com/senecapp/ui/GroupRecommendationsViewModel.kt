package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.auth.SessionRequiredException
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.data.GroupRecommendations
import com.senecapp.data.GroupRecommendationsRepository
import com.senecapp.data.ProfileRecommendationsStore
import com.senecapp.data.RecommendationStorage
import com.senecapp.data.isEventsConnectivityFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class GroupRecommendationsUiState(
    val loading: Boolean = true,
    val result: GroupRecommendations? = null,
    val error: String? = null,
    val actionError: String? = null,
    val joiningGroupId: Int? = null,
    val joinedGroupIds: Set<Int> = emptySet(),
    val fromCache: Boolean = false,
)

open class GroupRecommendationsViewModel internal constructor(
    private val repository: GroupRecommendationsRepository,
    private val cache: RecommendationStorage?,
    private val userId: () -> String?,
    private val fetch: suspend () -> GroupRecommendations = repository::load,
) : ViewModel() {
    constructor() : this(GroupRecommendationsRepository(), null, { null })

    var state by mutableStateOf(GroupRecommendationsUiState())
        private set
    private var loadRequest: Job? = null
    private val ownerUid = if (cache != null) userId() else null

    internal fun loadForComparison() {
        if (loadRequest?.isActive != true) load()
    }

    fun load() {
        loadRequest?.cancel()
        loadRequest = viewModelScope.launch {
            state = GroupRecommendationsUiState()
            try {
                val loaded = if (cache == null) {
                    RecommendationLoad(fetch(), false)
                } else {
                    val uid = ownerUid?.takeIf { it == userId() } ?: throw SessionRequiredException()
                    loadProfileRecommendations(uid, cache, fetch) { userId() == uid }
                }
                currentCoroutineContext().ensureActive()
                if (cache != null && userId() != ownerUid) throw SessionRequiredException()
                state = GroupRecommendationsUiState(loading = false, result = loaded.result, fromCache = loaded.fromCache)
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
        if (state.fromCache || (cache != null && userId() != ownerUid)) return
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
        if (state.fromCache || (cache != null && userId() != ownerUid)) return
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

class ProfileRecommendationsViewModel(application: Application) : GroupRecommendationsViewModel(
    GroupRecommendationsRepository(), ProfileRecommendationsStore(application), FirebaseIdTokenProvider::userId
)

internal data class RecommendationLoad(val result: GroupRecommendations, val fromCache: Boolean)

internal suspend fun loadProfileRecommendations(
    uid: String,
    cache: RecommendationStorage,
    fetch: suspend () -> GroupRecommendations,
    ownsSession: () -> Boolean,
): RecommendationLoad {
    // Both operations start concurrently. A storage failure can never suppress a fresh result.
    val loaded = supervisorScope {
        val saved = async { optionalStorage { cache.read(uid) } }
        val network = async { fetch() }
        try {
            RecommendationLoad(network.await(), false).also { saved.cancel() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!isEventsConnectivityFailure(failure) || !ownsSession()) throw failure
            RecommendationLoad(saved.await() ?: throw failure, true)
        }
    }
    currentCoroutineContext().ensureActive()
    if (!ownsSession()) throw SessionRequiredException()
    if (!loaded.fromCache) optionalStorage { cache.save(uid, loaded.result) }
    if (!ownsSession()) throw SessionRequiredException()
    return loaded
}

private suspend fun <T> optionalStorage(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
} catch (_: LinkageError) {
    // Native database loading is optional when the backend is available.
    null
}
