package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.analytics.TelemetryFailure
import com.senecapp.data.ProfileTipsAccessDenied
import com.senecapp.data.ProfileTipsOffline
import com.senecapp.data.ProfileTipsRepository
import com.senecapp.data.ProfileTipsSnapshot
import com.senecapp.data.ProfileTipsSummary
import com.senecapp.data.isOnline
import com.senecapp.data.networkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ProfileTipsState(
    val groupId: Int? = null,
    val days: Int = 90,
    val snapshot: ProfileTipsSnapshot? = null,
    val summary: ProfileTipsSummary? = null,
    val online: Boolean = true,
    val loading: Boolean = true,
    val error: String? = null,
    val denied: Boolean = false,
    val failure: TelemetryFailure? = null,
)


internal class ProfileTipsViewModel internal constructor(
    application: Application,
    private val loadTips: suspend (groupId: Int, days: Int, force: Boolean) -> ProfileTipsSnapshot,
    private val summarize: suspend (ProfileTipsSnapshot) -> ProfileTipsSummary,
    private val onlineNow: () -> Boolean,
    private val connectivity: Flow<Boolean>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, ProfileTipsRepository(application))

    private constructor(application: Application, repository: ProfileTipsRepository) : this(
        application, repository::load, repository::summarize, { isOnline(application) }, networkStatus(application),
    )

    private val mutableState = MutableStateFlow(ProfileTipsState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun open(groupId: Int) {
        val current = mutableState.value
        // Same group already shown or loading (e.g. after a rotation): keep it, no new request.
        if (current.groupId == groupId && (current.snapshot != null || current.loading)) return
        mutableState.value = ProfileTipsState(groupId = groupId, online = onlineNow())
        load(force = false)
    }

    fun selectDays(days: Int) {
        if (days == mutableState.value.days) return
        mutableState.value = mutableState.value.copy(days = days)
        load(force = false)
    }

    fun retry() = load(force = true)


    suspend fun watchConnection(groupId: Int) {
        connectivity.collect { online ->
            val current = mutableState.value
            if (current.groupId != groupId || current.online == online) return@collect
            mutableState.value = current.copy(online = online)
            val stale = current.snapshot?.fromPhone == true || (current.error != null && !current.denied)
            if (online && stale) load(force = true)
        }
    }

    private fun load(force: Boolean) {
        val groupId = mutableState.value.groupId ?: return
        val days = mutableState.value.days
        request?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, error = null, denied = false, failure = null)

        request = viewModelScope.launch {
            try {
                val snapshot = withContext(io) { loadTips(groupId, days, force) }
                val summary = summarize(snapshot)
                mutableState.value = mutableState.value.copy(
                    snapshot = snapshot, summary = summary, loading = false,
                    online = if (snapshot.fromPhone) onlineNow() else true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val current = mutableState.value
                val denied = failure is ProfileTipsAccessDenied
                mutableState.value = current.copy(
                    snapshot = current.snapshot.takeUnless { denied },
                    summary = current.summary.takeUnless { denied },
                    loading = false,
                    denied = denied,
                    online = onlineNow(),
                    error = when {
                        denied -> "Only group admins can see these tips."
                        failure is ProfileTipsOffline ->
                            "No connection and no tips saved on this phone yet. Connect and tap Retry."
                        current.snapshot != null -> "Could not update"
                        else -> "Could not load profile tips. Tap Retry."
                    },
                    failure = TelemetryFailure.from(failure),
                )
            }
        }
    }
}
