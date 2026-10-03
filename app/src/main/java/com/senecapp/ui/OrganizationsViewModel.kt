package com.senecapp.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.Organization
import com.senecapp.data.OrganizationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

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
)

class OrganizationsViewModel : ViewModel() {
    private val repository = OrganizationsRepository()
    var state by mutableStateOf(OrganizationsUiState())
        private set
    private var request: Job? = null
    private var detailRequest: Job? = null
    private var saveRequest: Job? = null

    fun search(query: String, categorySlug: String?, upcomingOnly: Boolean = false) {
        request?.cancel()
        request = viewModelScope.launch {
            state = state.copy(loading = true, items = emptyList(), error = null)
            try {
                val items = repository.search(query, categorySlug, upcomingOnly)
                val categories = if (query.isBlank() && categorySlug == null && !upcomingOnly) {
                    items.map { it.categorySlug to it.category }.distinctBy { it.first }
                } else state.categories
                state = OrganizationsUiState(loading = false, items = items, categories = categories,
                    searchFiltered = query.isNotBlank() || categorySlug != null || upcomingOnly)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                Log.e("Organizations", "Organization request failed", failure)
                state = state.copy(loading = false, error = "Cannot reach the backend. Start it and tap Retry.")
            } catch (failure: Exception) {
                state = state.copy(loading = false, error = failure.message ?: "Could not load organizations")
            }
        }
    }

    fun open(id: Int, fromSearch: Boolean) {
        detailRequest?.cancel()
        state = state.copy(detailLoading = true, detailDescription = null, detailError = null)
        detailRequest = viewModelScope.launch {
            try {
                val description = repository.detail(id, fromSearch)
                state = state.copy(detailLoading = false, detailDescription = description)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                state = state.copy(detailLoading = false, detailError = "Could not load organization details. Tap Retry.")
            }
        }
    }

    fun close() {
        detailRequest?.cancel()
        state = state.copy(detailLoading = false, detailDescription = null, detailError = null,
            saveError = null)
    }

    /**
     * BQ13 — the save half of the question. The view half is already recorded by [open], which
     * sends `entry_point=explore` whenever the list is not filtered by a search.
     *
     * The flag flips before the request so the button answers immediately, and flips back if the
     * backend refuses: both calls are idempotent, so a retry after a rollback is safe.
     */
    fun toggleSave(id: Int) {
        if (state.savingId != null) return
        val organization = state.items.firstOrNull { it.id == id } ?: return
        val target = !organization.isSaved
        saveRequest?.cancel()
        state = state.copy(items = state.items.withSaved(id, target), savingId = id, saveError = null)
        saveRequest = viewModelScope.launch {
            try {
                if (target) repository.save(id) else repository.unsave(id)
                state = state.copy(savingId = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("Organizations", "Could not toggle save for group $id", failure)
                state = state.copy(
                    items = state.items.withSaved(id, !target),
                    savingId = null,
                    saveError = failure.message ?: "Could not update your saved list.",
                )
            }
        }
    }

    private fun List<Organization>.withSaved(id: Int, saved: Boolean): List<Organization> =
        map { if (it.id == id) it.copy(isSaved = saved) else it }
}
