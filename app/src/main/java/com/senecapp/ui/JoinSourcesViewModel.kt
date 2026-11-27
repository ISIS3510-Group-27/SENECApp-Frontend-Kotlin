package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.analytics.TelemetryFailure
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.data.CampusRepository
import com.senecapp.data.JoinSourcesKey
import com.senecapp.data.JoinSourcesOffline
import com.senecapp.data.JoinSourcesRepository
import com.senecapp.data.JoinSourcesStore
import com.senecapp.data.JoinSourcesView
import com.senecapp.data.JoinSourcesWatch
import com.senecapp.data.isOnline
import com.senecapp.data.joinSourcesAccessLost
import com.senecapp.data.networkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class JoinSourcesState(
    val key: JoinSourcesKey? = null,
    val view: JoinSourcesView? = null,
    val online: Boolean = true,
    val loading: Boolean = true,
    val error: String? = null,
    val failure: TelemetryFailure? = null,
)

internal class JoinSourcesViewModel internal constructor(
    application: Application,
    private val repository: JoinSourcesWatch,
    private val onlineNow: () -> Boolean,
    private val connectivity: Flow<Boolean>,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        JoinSourcesRepository(JoinSourcesStore(application), CampusRepository()::joinSources,
            FirebaseIdTokenProvider::userId),
        { isOnline(application) },
        networkStatus(application),
    )

    private val mutableState = MutableStateFlow(JoinSourcesState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun open(key: JoinSourcesKey) {
        mutableState.value = JoinSourcesState(key = key, online = onlineNow())
        load(key, force = false)
    }

    suspend fun watchConnection(key: JoinSourcesKey) {
        connectivity.collect { online ->
            val current = mutableState.value
            if (current.key != key || current.online == online) return@collect
            mutableState.value = current.copy(online = online)
            // A load may still be in flight when the default network becomes unavailable.
            load(key, force = online)
        }
    }

    fun refresh(): Boolean {
        val current = mutableState.value
        val key = current.key ?: return true
        if (!current.online && current.view != null) return false
        load(key, force = true)
        return true
    }

    fun close(key: JoinSourcesKey) {
        if (mutableState.value.key != key) return
        request?.cancel()
        mutableState.value = JoinSourcesState()
    }

    private fun load(key: JoinSourcesKey, force: Boolean) {
        request?.cancel()
        val online = mutableState.value.online
        mutableState.value = mutableState.value.copy(loading = true, error = null, failure = null)
        request = viewModelScope.launch {
            try {
                repository.watch(key, online, force).collect { view ->
                    mutableState.value = mutableState.value.copy(view = view, loading = view.fromPhone && online)
                }
                mutableState.value = mutableState.value.copy(loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val current = mutableState.value
                val denied = joinSourcesAccessLost(failure)
                mutableState.value = current.copy(
                    view = current.view.takeUnless { denied },
                    loading = false,
                    error = when {
                        denied -> "Only group administrators can view these insights."
                        failure is JoinSourcesOffline || !current.online ->
                            "This is worked out from what students do in the app, so the first look needs a connection. After that it opens without one."
                        else -> "Could not update"
                    },
                    failure = TelemetryFailure.from(failure),
                )
            }
        }
    }
}
