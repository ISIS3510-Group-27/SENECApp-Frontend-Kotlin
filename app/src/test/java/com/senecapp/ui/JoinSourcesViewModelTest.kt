package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinSourcesViewModelTest {
    @Test fun reconnectingForcesAReloadAfterShowingTheSavedCopy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val key = JoinSourcesKey("leader", 7)
            val connection = MutableStateFlow(false)
            val calls = mutableListOf<Pair<Boolean, Boolean>>()
            val data = JoinSources("Question", "Answer", "Campus", 90, 20, 1, emptyList())
            val saved = JoinSourcesVisit(100, data)
            val repository = JoinSourcesWatch { askedKey, online, force ->
                assertEquals(key, askedKey)
                calls += online to force
                flowOf(if (online) JoinSourcesView(saved.copy(takenAt = 200), null, fromPhone = false)
                    else JoinSourcesView(saved, null, fromPhone = true))
            }
            val model = JoinSourcesViewModel(Application(), repository, { connection.value }, connection)
            models.put("joinSources", model)
            model.open(key)
            backgroundScope.launch { model.watchConnection(key) }
            runCurrent()
            assertEquals(listOf(false to false), calls)
            assertTrue(model.state.value.view!!.fromPhone)
            assertFalse(model.refresh())
            connection.value = true
            runCurrent()
            assertEquals(listOf(false to false, true to true), calls)
            assertFalse(model.state.value.view!!.fromPhone)
            assertFalse(model.state.value.loading)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}
