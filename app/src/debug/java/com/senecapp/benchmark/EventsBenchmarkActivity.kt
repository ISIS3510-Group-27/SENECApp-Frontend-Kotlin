package com.senecapp.benchmark

import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.lifecycleScope
import com.senecapp.data.CampusEvent
import com.senecapp.data.buildItinerary
import com.senecapp.data.campusDate
import com.senecapp.data.campusZone
import com.senecapp.data.filterEvents
import com.senecapp.ui.AppBottomNavigation
import com.senecapp.ui.AuthTheme
import com.senecapp.ui.CampusEventsUiState
import com.senecapp.ui.DemoView
import com.senecapp.ui.EventFeedScreen
import com.senecapp.ui.ViewText
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Debug-only fixture. No repositories, auth, persisted fixtures or synthetic telemetry. */
class EventsBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = CampusEventsUiState(loading = false, items = events, total = events.size)
        val compute = intent.getBooleanExtra("compute", false)
        setContent {
            AuthTheme {
                var tab by remember { mutableStateOf("Discover") }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                    Box(Modifier.weight(1f)) {
                        if (tab == "Events") {
                            CompositionLocalProvider(LocalInspectionMode provides true) {
                                EventFeedScreen(state, emptySet(), emptySet(), {}, {}, {}, {}, {})
                            }
                        } else {
                            DemoView("Events benchmark", showSampleLabel = false) {
                                ViewText("Deterministic fixture • ${events.size} events")
                            }
                        }
                    }
                    AppBottomNavigation(tab, false) { if (!compute && it in setOf("Discover", "Events")) tab = it }
                }
            }
        }
        if (compute) lifecycleScope.launch(Dispatchers.Default) { measureAlgorithms() }
    }

    private fun measureAlgorithms() {
        val now = anchor.atZone(campusZone)
        val tasks = listOf<Pair<String, () -> Int>>(
            "filter_all" to { filterEvents(events, "", "Any date", now).size },
            "filter_robotics" to { filterEvents(events, "Robotics", "Any date", now).size },
            "build_itinerary" to { buildItinerary(events, anchor).size },
            "parse_160_dates" to { events.count { campusDate(it.startsAt) != null } },
        )
        for ((name, task) in tasks) {
            repeat(12) { task() }
            repeat(40) { sample ->
                val cpuStart = Debug.threadCpuTimeNanos()
                val start = System.nanoTime()
                val size = task()
                val wallNanos = System.nanoTime() - start
                val cpuNanos = Debug.threadCpuTimeNanos() - cpuStart
                Log.i("Sprint4EventsBench", "$name,$sample,$wallNanos,$cpuNanos,$size")
            }
        }
        Log.i("Sprint4EventsBench", "COMPLETE")
    }

    private companion object {
        val anchor: Instant = Instant.parse("2030-03-01T14:00:00Z")
        val events = List(160) { index ->
            val slot = index * 73 % 160
            val start = anchor.plusSeconds(slot * 1800L + 60)
            val group = listOf("Robotics", "Literature", "Photography", "Music")[index % 4]
            CampusEvent(index + 1, "$group Event ${index + 1}", group, start.toString(),
                "ML", "Benchmark event", false, start.plusSeconds(2400).toString(),
                groupId = index % 4 + 1)
        }
    }
}
