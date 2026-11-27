package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTipsViewModelTest {
    private val tips = ProfileTips("Question", "Answer", 90, 0, 1,
        listOf(ProfileTipField("has_image", false, 10, 0.5, 1.5, 0.3, 0.2)))

    @Test fun offlineShowsTheSavedCopyAndReloadsWhenTheConnectionReturns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val connection = MutableStateFlow(false)
            val calls = mutableListOf<Boolean>()
            val model = ProfileTipsViewModel(Application(), { id, _, force ->
                calls += force
                ProfileTipsSnapshot(id, tips, syncedAt = 100, fromPhone = !connection.value)
            }, ::summarizeProfileTips, { connection.value }, connection, dispatcher)
            models.put("profileTips", model)

            model.open(7)
            backgroundScope.launch { model.watchConnection(7) }
            runCurrent()
            assertTrue(model.state.value.snapshot!!.fromPhone)
            assertFalse(model.state.value.online)
            assertEquals(1, model.state.value.summary!!.missing)

            connection.value = true
            runCurrent()
            assertEquals(listOf(false, true), calls)
            assertFalse(model.state.value.snapshot!!.fromPhone)
            assertFalse(model.state.value.loading)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun reopeningTheSameGroupKeepsTheStateWithoutANewRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            var requests = 0
            val model = ProfileTipsViewModel(Application(), { id, _, _ -> requests++; ProfileTipsSnapshot(id, tips, 100) },
                ::summarizeProfileTips, { true }, MutableStateFlow(true), dispatcher)
            models.put("profileTips", model)
            model.open(7)
            runCurrent()
            model.open(7) // what happens after a rotation
            runCurrent()
            assertEquals(1, requests)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun offlineWithoutCopyEndsInAMessageNotAnEndlessSpinner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val model = ProfileTipsViewModel(Application(), { _, _, _ -> throw ProfileTipsOffline() },
                ::summarizeProfileTips, { false }, MutableStateFlow(false), dispatcher)
            models.put("profileTips", model)
            model.open(7)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertNull(model.state.value.snapshot)
            assertTrue(model.state.value.error!!.startsWith("No connection"))
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun forbiddenShowsTheAdminMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val model = ProfileTipsViewModel(Application(), { _, _, _ -> throw ProfileTipsAccessDenied() },
                ::summarizeProfileTips, { true }, MutableStateFlow(true), dispatcher)
            models.put("profileTips", model)
            model.open(7)
            runCurrent()
            assertTrue(model.state.value.denied)
            assertEquals("Only group admins can see these tips.", model.state.value.error)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}
