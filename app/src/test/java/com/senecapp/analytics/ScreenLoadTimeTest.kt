package com.senecapp.analytics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ScreenLoadTimeTest {
    private var now = 1_000L
    private val samples = mutableListOf<Pair<AnalyticsScreen, Long?>>()
    private val tracker = ScreenVisitTracker({ screen, ms -> samples.add(screen to ms) }, { _, _ -> }, { now })

    private fun enter(owner: String = "owner", key: String = "discover", visible: Boolean = true) =
        tracker.attach(owner, key, AnalyticsScreen.DISCOVER, visible, measureLoad = true)

    @Test fun measuresFromScreenEntryUntilContentWithAMonotonicClock() {
        enter()
        now += 842
        tracker.content("owner", true, null)
        assertEquals(listOf(AnalyticsScreen.DISCOVER to 842L), samples)
    }

    @Test fun cacheHitsAndSuccessfulEmptyResultsAreStillRealSamples() {
        enter()
        now += 16
        tracker.content("owner", true, null)
        assertEquals(16L, samples.single().second)
    }

    @Test fun refreshRecompositionAndForegroundResumeDoNotDuplicateSamples() {
        enter()
        now += 300
        tracker.content("owner", true, null)
        tracker.content("owner", false, null)
        now += 8_000
        tracker.content("owner", true, null)
        tracker.visible("owner", false)
        now += 20_000
        tracker.visible("owner", true)
        assertEquals(listOf(AnalyticsScreen.DISCOVER to 300L), samples)
    }

    @Test fun excludesBackgroundTimeDuringLoading() {
        enter()
        now += 120
        tracker.visible("owner", false)
        now += 60_000
        tracker.visible("owner", true)
        now += 80
        tracker.content("owner", true, null)
        assertEquals(200L, samples.single().second)
    }

    @Test fun backgroundPreloadsWaitForVisibilityAndDoNotMeasureTimeBeforeEntry() {
        enter(visible = false)
        now += 60_000
        assertTrue(samples.isEmpty())
        tracker.visible("owner", true)
        now += 16
        tracker.content("owner", true, null)
        assertEquals(16L, samples.single().second)
    }

    @Test fun contentReadyWhileHiddenDoesNotAccumulateFurtherTime() {
        enter()
        now += 120
        tracker.visible("owner", false)
        now += 60_000
        tracker.content("owner", true, null)
        assertTrue(samples.isEmpty())
        tracker.visible("owner", true)
        assertEquals(120L, samples.single().second)
    }

    @Test fun rotationRetainsUnfinishedTimingAndExcludesRecreationGap() {
        enter("old")
        now += 100
        tracker.detach("old", rotating = true)
        now += 5_000
        enter("new")
        now += 50
        tracker.content("new", true, null)
        tracker.content("old", true, null)
        tracker.detach("new", rotating = true)
        enter("third")
        tracker.content("third", true, null)
        assertEquals(listOf(AnalyticsScreen.DISCOVER to 150L), samples)
    }

    @Test fun navigatingAwayBeforeDataDoesNotEmitAndReturningStartsAgain() {
        enter("old")
        now += 900
        tracker.detach("old", rotating = false)
        tracker.content("old", true, null)
        assertTrue(samples.isEmpty())
        enter("new")
        now += 60
        tracker.content("new", true, null)
        assertEquals(60L, samples.single().second)
    }

    @Test fun anotherGroupStartsItsOwnTimerAndIgnoresLateOldUpdates() {
        tracker.attach("first", "group:1", AnalyticsScreen.GROUP_DETAIL, true, measureLoad = true)
        now += 500
        tracker.attach("second", "group:2", AnalyticsScreen.GROUP_DETAIL, true, measureLoad = true)
        tracker.content("first", true, null)
        tracker.detach("first", false)
        now += 200
        tracker.content("second", true, null)
        assertEquals(listOf(AnalyticsScreen.GROUP_DETAIL to 200L), samples)
    }

    @Test fun failedLoadAndManualRetryDoNotFabricateALongSuccessfulSample() {
        enter()
        now += 500
        tracker.content("owner", false, null, loadFailed = true)
        now += 60_000
        tracker.content("owner", true, null)
        assertEquals(listOf(AnalyticsScreen.DISCOVER to null), samples)
        tracker.detach("owner", false)
        enter("new")
        now += 400
        tracker.content("new", true, null)
        assertEquals(400L, samples.last().second)
    }

    @Test fun caughtFailuresAlsoInvalidateTheTimingButStillAllowAView() {
        enter()
        tracker.content("owner", false, TelemetryFailure.from(IOException()))
        now += 3_000
        tracker.content("owner", true, null)
        assertNull(samples.single().second)
    }

    @Test fun existingUntimedScreensDoNotGetInventedDurations() {
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, true)
        now += 800
        tracker.content("owner", true, null)
        assertEquals(listOf(AnalyticsScreen.EVENTS to null), samples)
    }
}
