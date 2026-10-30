package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.analytics.TelemetryFailure
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.data.Category
import com.senecapp.data.CreateGroupDraft
import com.senecapp.data.CreateGroupDraftFile
import com.senecapp.data.CreateGroupRepository
import com.senecapp.data.CreatedGroup
import com.senecapp.data.Interest
import com.senecapp.data.InterestSuggestionCache
import com.senecapp.data.networkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val failure: TelemetryFailure? = null,
    val catalogueFromCache: Boolean = false,
    val queued: Boolean = false,
    val offline: Boolean = false,
    val draftRestored: Boolean = false,
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

class CreateGroupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CreateGroupRepository(application)
    var state by mutableStateOf(CreateGroupUiState())
        private set
    private var submitJob: Job? = null
    private val suggestionCache = InterestSuggestionCache()
    private var suggestionJob: Job? = null
    private val draftFile = CreateGroupDraftFile(application)
    private var draftJob: Job? = null
    private var draftChecked = false

    fun loadCatalogue() {
        viewModelScope.launch {
            state = state.copy(catalogueLoading = true, catalogueError = null, failure = null)
            if (!draftChecked) { draftChecked = true; restoreDraft() }
            try {
                val catalogue = repository.catalogue()
                // Normalizing the catalogue is CPU work, so it runs on Default, never on Main.
                withContext(Dispatchers.Default) { suggestionCache.setCatalogue(catalogue.interests) }
                state = state.copy(
                    catalogueLoading = false,
                    categories = catalogue.categories,
                    interests = catalogue.interests,
                    catalogueFromCache = catalogue.fromCache,
                )
                scheduleSuggestions(delayMs = 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                state = state.copy(
                    catalogueLoading = false,
                    catalogueError = "Cannot reach the backend and the categories are not saved on this phone yet. " +
                            "Open this form once with internet; after that it also works offline.",
                    failure = TelemetryFailure.from(failure),
                )
            } catch (failure: Exception) {
                state = state.copy(
                    catalogueLoading = false,
                    catalogueError = failure.message ?: "Could not load categories.",
                    failure = TelemetryFailure.from(failure),
                )
            }
        }
    }

    fun setName(value: String) {
        state = state.copy(name = value, error = null)
        scheduleSuggestions()
        scheduleDraftSave()
    }

    fun setDescription(value: String) {
        state = state.copy(description = value, error = null)
        scheduleSuggestions()
        scheduleDraftSave()
    }

    fun setCategory(slug: String) {
        state = state.copy(categorySlug = slug, error = null)
        scheduleDraftSave()
    }

    fun setContactEmail(value: String) {
        state = state.copy(contactEmail = value, error = null)
        scheduleDraftSave()
    }

    fun toggleInterest(id: Int) {
        val selected = state.selectedInterestIds
        val next = when {
            id in selected -> selected - id
            selected.size >= MAX_TAGS -> return
            else -> selected + id
        }
        state = state.copy(selectedInterestIds = next, interestsEdited = true)
        scheduleDraftSave()
    }

    fun submit() {
        if (!state.canSubmit) return
        val categorySlug = state.categorySlug ?: return
        submitJob?.cancel()
        draftJob?.cancel()
        submitJob = viewModelScope.launch {
            state = state.copy(submitting = true, error = null, failure = null)
            try {
                if (!repository.online()) { queue(categorySlug); return@launch }
                val created = repository.create(
                    name = state.name,
                    categorySlug = categorySlug,
                    description = state.description,
                    contactEmail = state.contactEmail,
                    tagIds = state.selectedInterestIds.toList(),
                )
                state = state.copy(submitting = false, created = created)
                discardDraft()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                queue(categorySlug)
            } catch (failure: Exception) {
                state = state.copy(submitting = false, error = failure.message ?: "Could not send your proposal.",
                    failure = TelemetryFailure.from(failure))
            }
        }
    }


    fun reset() {
        submitJob?.cancel()
        suggestionJob?.cancel()
        discardDraft()
        state = state.copy(
            name = "", categorySlug = null, description = "", contactEmail = "",
            suggested = emptyList(), selectedInterestIds = emptySet(), interestsEdited = false,
            submitting = false, error = null, created = null, failure = null, queued = false, draftRestored = false,
        )
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        val uid = FirebaseIdTokenProvider.userId() ?: return
        val draft = CreateGroupDraft(state.name, state.categorySlug, state.description, state.contactEmail,
            state.selectedInterestIds, state.interestsEdited)
        val empty = draft.name.isBlank() && draft.description.isBlank() && draft.contactEmail.isBlank() &&
                draft.categorySlug == null
        draftJob = viewModelScope.launch {
            delay(250)
            withContext(Dispatchers.IO) {
                try { if (empty) draftFile.delete(uid) else draftFile.write(uid, draft) } catch (_: Exception) { }
            }
        }
    }

    private suspend fun restoreDraft() {
        val uid = FirebaseIdTokenProvider.userId() ?: return
        if (state.name.isNotBlank() || state.description.isNotBlank()) return
        val draft = withContext(Dispatchers.IO) { draftFile.read(uid) } ?: return
        state = state.copy(name = draft.name, categorySlug = draft.categorySlug, description = draft.description,
            contactEmail = draft.contactEmail, selectedInterestIds = draft.interestIds,
            interestsEdited = draft.interestsEdited, draftRestored = true)
    }

    private fun discardDraft() {
        draftJob?.cancel()
        val uid = FirebaseIdTokenProvider.userId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { draftFile.delete(uid) } catch (_: Exception) { }
        }
    }

    suspend fun watchPending(onSent: () -> Unit) {
        networkStatus(getApplication<Application>()).collect { online ->
            state = state.copy(offline = !online)
            if (online) {
                try {
                    if (repository.sendPending() > 0) onSent()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // They stay pending and are tried again on the next reconnection.
                }
            }
        }
    }

    private suspend fun queue(categorySlug: String) {
        try {
            repository.queue(state.name, categorySlug, state.description, state.contactEmail,
                state.selectedInterestIds.toList())
            state = state.copy(submitting = false, queued = true)
            discardDraft()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state = state.copy(submitting = false, error = "Could not save your proposal on this phone. Try again.",
                failure = TelemetryFailure.from(failure))
        }
    }

    private fun scheduleSuggestions(delayMs: Long = 250) {
        suggestionJob?.cancel()
        if (state.interests.isEmpty()) return
        val name = state.name
        val description = state.description
        suggestionJob = viewModelScope.launch {
            delay(delayMs)
            val suggestions = withContext(Dispatchers.Default) { suggestionCache.suggest(name, description) }
            state = state.copy(
                suggested = suggestions,
                selectedInterestIds = if (state.interestsEdited) state.selectedInterestIds
                else suggestions.map { it.id }.toSet(),
            )
        }
    }
}
