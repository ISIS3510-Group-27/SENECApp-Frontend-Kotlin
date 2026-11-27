package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import com.senecapp.auth.invalidFirebaseSession
import com.senecapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class OrganizationsUiState(
    val loading: Boolean = true,
    val items: List<Organization> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val error: String? = null,
    val detailLoading: Boolean = false,
    val detailDescription: String? = null,
    val detailError: String? = null,
    val searchFiltered: Boolean = false,
    val savingId: Int? = null,
    val saveError: String? = null,
    val filters: GroupFilters = GroupFilters(),
    val fromCache: Boolean = false,
    val cachedAt: Long? = null,
    val refreshWarning: String? = null,
    val localList: List<Organization>? = null,
    val localListBusy: Boolean = false,
    val localListMessage: String? = null,
)

class OrganizationsViewModel internal constructor(application: Application,
    private val repository: OrganizationsSource,
    private val localListStore: SavedOrganizationListStore? = null,
    private val userId: () -> String?,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application,
        OrganizationsRepository(SqliteOrganizationsStore(application)),
        SavedOrganizationListStore(application.noBackupFilesDir.resolve("discover")),
        FirebaseIdTokenProvider::userId)

    private val ownerUid = userId()
    var state by mutableStateOf(OrganizationsUiState())
        private set
    private var request: Job? = null
    private var detailRequest: Job? = null
    private var saveRequest: Job? = null
    private var localListRequest: Job? = null
    private var activeQuery: OrganizationsQuery? = null
    private var loaded = false
    private var hasSnapshot = false

    fun filter(filters: GroupFilters) {
        state = state.copy(filters = filters.copy(interests = filters.interests.toSet(), buildings = filters.buildings.toSet()))
        refresh()
    }

    fun refresh() {
        loaded = false
        search(activeQuery?.text.orEmpty(), activeQuery?.category, activeQuery?.upcomingOnly ?: false)
    }

    fun search(query: String, categorySlug: String?, upcomingOnly: Boolean = false) {
        if (!ownsSession()) { clearSessionData(); return }
        val selection = OrganizationsQuery(query.trim(), categorySlug, upcomingOnly,
            state.filters.copy(interests = state.filters.interests.toSet(), buildings = state.filters.buildings.toSet()))
        if (request?.isActive == true && activeQuery == selection) return
        if (loaded && state.error == null && !state.fromCache && activeQuery == selection) return
        request?.cancel()
        if (activeQuery != selection) {
            hasSnapshot = false
            state = state.copy(items = emptyList(), fromCache = false, cachedAt = null)
        }
        activeQuery = selection
        request = viewModelScope.launch {
            state = state.copy(loading = true, error = null, refreshWarning = null, searchFiltered = selection.filtered)
            try {
                val result = repository.load(selection)
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearSessionData(); return@launch }
                // A disk fallback must not undo a save confirmed during this session.
                state = if (result.fromCache && hasSnapshot) state.copy(loading = false, fromCache = true)
                else state.copy(loading = false, items = result.items, categories = result.categories,
                    fromCache = result.fromCache, cachedAt = result.cachedAt,
                    refreshWarning = if (result.cacheWriteFailed) "Organizations loaded, but could not be saved for offline use." else null)
                hasSnapshot = true
                loaded = true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                when {
                    !ownsSession() || isSessionFailure(failure) -> clearSessionData(failure.message)
                    isOrganizationsConnectivityFailure(failure) && hasSnapshot ->
                        state = state.copy(loading = false, fromCache = true)
                    else -> state = state.copy(loading = false, error = if (isOrganizationsConnectivityFailure(failure))
                        "Cannot reach the backend and no saved results match this search. Connect and tap Retry."
                        else failure.message ?: "Could not load organizations. Tap Retry.")
                }
            }
        }
    }

    fun open(id: Int, fromSearch: Boolean) {
        if (!ownsSession()) { clearSessionData(); return }
        detailRequest?.cancel()
        state = state.copy(detailLoading = true, detailDescription = null, detailError = null)
        detailRequest = viewModelScope.launch {
            try {
                val description = repository.detail(id, fromSearch)
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearSessionData(); return@launch }
                state = state.copy(detailLoading = false, detailDescription = description)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!ownsSession() || isSessionFailure(failure)) clearSessionData(failure.message)
                else state = state.copy(detailLoading = false, detailError = "Could not load organization details. Tap Retry.")
            }
        }
    }

    fun close() {
        detailRequest?.cancel()
        state = state.copy(detailLoading = false, detailDescription = null, detailError = null, saveError = null)
    }

    fun saveLocalList() {
        val items = state.items
        if (state.loading || state.savingId != null || items.isEmpty()) return
        updateLocalList("Could not save the organization list.") { store, uid ->
            store.save(uid, items)
            state.copy(localListMessage = "List saved on this phone.")
        }
    }

    fun openLocalList() {
        updateLocalList("Could not open the saved list.") { store, uid ->
            val items = store.read(uid)
            state.copy(localList = items,
                localListMessage = if (items == null) "No list is saved on this phone yet." else null)
        }
    }

    fun closeLocalList() {
        state = state.copy(localList = null)
    }

    private fun updateLocalList(
        errorMessage: String,
        operation: suspend (SavedOrganizationListStore, String) -> OrganizationsUiState,
    ) {
        val store = localListStore ?: return
        val uid = ownerUid
        if (uid == null || !ownsSession()) { clearSessionData(); return }
        if (state.localListBusy) return
        state = state.copy(localListBusy = true, localListMessage = null)
        localListRequest = viewModelScope.launch {
            try {
                val updated = operation(store, uid)
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearSessionData(); return@launch }
                state = updated.copy(localListBusy = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearSessionData(); return@launch }
                state = state.copy(localListBusy = false, localListMessage = errorMessage)
            }
        }
    }

    fun toggleSave(id: Int, onSaved: () -> Unit = {}) {
        if (!ownsSession()) { clearSessionData(); return }
        if (state.savingId != null) return
        val organization = state.items.firstOrNull { it.id == id } ?: return
        val target = !organization.isSaved
        state = state.copy(items = state.items.withSaved(id, target), savingId = id, saveError = null)
        saveRequest = viewModelScope.launch {
            try {
                if (target) repository.save(id) else repository.unsave(id)
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearSessionData(); return@launch }
                state = state.copy(savingId = null)
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!ownsSession() || isSessionFailure(failure)) clearSessionData(failure.message)
                else state = state.copy(items = state.items.withSaved(id, !target), savingId = null,
                    saveError = failure.message ?: "Could not update your saved list.")
            }
        }
    }

    private fun ownsSession() = ownerUid != null && userId() == ownerUid

    private fun clearSessionData(message: String? = null) {
        request?.cancel(); detailRequest?.cancel(); saveRequest?.cancel()
        localListRequest?.cancel()
        hasSnapshot = false
        loaded = false
        state = OrganizationsUiState(loading = false, error = message ?: "Your session has ended. Sign in again.")
    }

    private fun isSessionFailure(failure: Exception) = failure is SessionChangedException ||
        failure is SessionRequiredException || invalidFirebaseSession(failure) ||
        (failure is ApiHttpException && failure.status in setOf(401, 403))

    private fun List<Organization>.withSaved(id: Int, saved: Boolean): List<Organization> =
        map { if (it.id == id) it.copy(isSaved = saved) else it }
}
