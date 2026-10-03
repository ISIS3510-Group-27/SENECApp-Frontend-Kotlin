package com.senecapp.ui


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.Category
import com.senecapp.data.CreateGroupRepository
import com.senecapp.data.CreatedGroup
import com.senecapp.data.Interest
import com.senecapp.data.InterestInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

private const val NAME_MINIMUM = 3
private const val DESCRIPTION_MINIMUM = 20
private const val DESCRIPTION_MAXIMUM = 2000
private const val MAX_TAGS = 8

data class CreateGroupUiState(
    val catalogueLoading: Boolean = true,
    val catalogueError: String? = null,
    val categories: List<Category> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val name: String = "",
    val categorySlug: String? = null,
    val description: String = "",
    val contactEmail: String = "",
    val suggested: List<Interest> = emptyList(),
    val selectedInterestIds: Set<Int> = emptySet(),
    val interestsEdited: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val created: CreatedGroup? = null,
) {
    val nameError: String?
        get() = if (name.isNotBlank() && name.trim().length < NAME_MINIMUM)
            "Use at least $NAME_MINIMUM characters." else null

    val descriptionError: String?
        get() = when {
            description.isBlank() -> null
            description.trim().length < DESCRIPTION_MINIMUM ->
                "The backend needs at least $DESCRIPTION_MINIMUM characters (${description.trim().length} so far)."
            description.trim().length > DESCRIPTION_MAXIMUM ->
                "Keep it under $DESCRIPTION_MAXIMUM characters."
            else -> null
        }

    val emailError: String?
        get() = if (contactEmail.isNotBlank() && !contactEmail.trim().contains("@"))
            "That does not look like an email address." else null

    val canSubmit: Boolean
        get() = !submitting &&
                name.trim().length >= NAME_MINIMUM &&
                categorySlug != null &&
                description.trim().length in DESCRIPTION_MINIMUM..DESCRIPTION_MAXIMUM &&
                emailError == null
}

class CreateGroupViewModel : ViewModel() {
    private val repository = CreateGroupRepository()
    var state by mutableStateOf(CreateGroupUiState())
        private set
    private var submitJob: Job? = null

    fun loadCatalogue() {
        viewModelScope.launch {
            state = state.copy(catalogueLoading = true, catalogueError = null)
            try {
                val categories = repository.categories()
                val interests = repository.interests()
                state = state.copy(
                    catalogueLoading = false,
                    categories = categories,
                    interests = interests,
                ).withSuggestions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = state.copy(
                    catalogueLoading = false,
                    catalogueError = "Cannot reach the backend. Tap Retry.",
                )
            } catch (failure: Exception) {
                state = state.copy(
                    catalogueLoading = false,
                    catalogueError = failure.message ?: "Could not load categories.",
                )
            }
        }
    }

    fun setName(value: String) {
        state = state.copy(name = value, error = null).withSuggestions()
    }

    fun setDescription(value: String) {
        state = state.copy(description = value, error = null).withSuggestions()
    }

    fun setCategory(slug: String) {
        state = state.copy(categorySlug = slug, error = null)
    }

    fun setContactEmail(value: String) {
        state = state.copy(contactEmail = value, error = null)
    }

    fun toggleInterest(id: Int) {
        val selected = state.selectedInterestIds
        val next = when {
            id in selected -> selected - id
            selected.size >= MAX_TAGS -> return
            else -> selected + id
        }
        state = state.copy(selectedInterestIds = next, interestsEdited = true)
    }

    fun submit() {
        if (!state.canSubmit) return
        val categorySlug = state.categorySlug ?: return
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            state = state.copy(submitting = true, error = null)
            try {
                val created = repository.create(
                    name = state.name,
                    categorySlug = categorySlug,
                    description = state.description,
                    contactEmail = state.contactEmail,
                    tagIds = state.selectedInterestIds.toList(),
                )
                state = state.copy(submitting = false, created = created)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                state = state.copy(submitting = false, error = "Cannot reach the backend. Try again.")
            } catch (failure: Exception) {
                state = state.copy(submitting = false, error = failure.message ?: "Could not send your proposal.")
            }
        }
    }


    fun reset() {
        submitJob?.cancel()
        state = state.copy(
            name = "", categorySlug = null, description = "", contactEmail = "",
            suggested = emptyList(), selectedInterestIds = emptySet(), interestsEdited = false,
            submitting = false, error = null, created = null,
        )
    }

    private fun CreateGroupUiState.withSuggestions(): CreateGroupUiState {
        if (interests.isEmpty()) return this
        val suggestions = InterestInference.suggest(name, description, interests)
        return copy(
            suggested = suggestions,
            selectedInterestIds = if (interestsEdited) selectedInterestIds
            else suggestions.map { it.id }.toSet(),
        )
    }
}
