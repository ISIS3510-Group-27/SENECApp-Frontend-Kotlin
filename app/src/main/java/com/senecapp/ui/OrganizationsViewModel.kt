package com.senecapp.ui

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
)

class OrganizationsViewModel : ViewModel() {
    private val repository = OrganizationsRepository()
    var state by mutableStateOf(OrganizationsUiState())
        private set
    private var request: Job? = null

    fun search(query: String, categorySlug: String?) {
        request?.cancel()
        request = viewModelScope.launch {
            state = state.copy(loading = true, items = emptyList(), error = null)
            try {
                val items = repository.search(query, categorySlug)
                val categories = if (query.isBlank() && categorySlug == null) {
                    items.map { it.categorySlug to it.category }.distinctBy { it.first }
                } else state.categories
                state = OrganizationsUiState(items = items, categories = categories)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = state.copy(loading = false, error = "Cannot reach the backend. Start it and tap Retry.")
            } catch (failure: Exception) {
                state = state.copy(loading = false, error = failure.message ?: "Could not load organizations")
            }
        }
    }
}
