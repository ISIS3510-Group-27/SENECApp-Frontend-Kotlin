package com.senecapp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.senecapp.data.FreeNowEvent
import com.senecapp.data.SavedEventsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SavedEventsController(private val store: SavedEventsStore, private val scope: CoroutineScope) {
    var events by mutableStateOf<List<FreeNowEvent>>(emptyList())
        private set
    var busy by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load() {
        try {
            events = store.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not read saved events."
        } finally { busy = false }
    }

    fun toggle(event: FreeNowEvent) {
        if (busy) return
        busy = true
        error = null
        val updated = if (events.any { it.id == event.id }) events.filterNot { it.id == event.id }
            else events + event.copy(walkingMinutes = null, reasons = emptyList())
        scope.launch {
            try {
                store.save(updated)
                events = updated
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not save events. Please try again."
            } finally { busy = false }
        }
    }
}

@Composable
internal fun rememberSavedEvents(): SavedEventsController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) { SavedEventsController(SavedEventsStore(context), scope) }
    LaunchedEffect(controller) { controller.load() }
    return controller
}
