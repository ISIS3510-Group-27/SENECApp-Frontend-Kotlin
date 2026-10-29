package com.senecapp.analytics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ScreenVisitTrackerTest {
    private val views = mutableListOf<AnalyticsScreen>()
    private val errors = mutableListOf<TelemetryFailure>()
    private val tracker = ScreenVisitTracker(views::add, { _, failure -> errors.add(failure) })

    @Test fun crashAttributionNeedsAVisibleAttachedScreenButNotLoadedContent() {
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, false)
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
        tracker.visible("owner", true)
        assertEquals(AnalyticsScreen.EVENTS, tracker.crashScreen)
        assertTrue(views.isEmpty())
        tracker.visible("owner", false)
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
        tracker.visible("owner", true)
        tracker.detach("owner", false)
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
        tracker.content("owner", true, null)
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
    }

    @Test fun rotationAndLateDetachCannotMisattributeACrash() {
        tracker.attach("old", "events", AnalyticsScreen.EVENTS, true)
        tracker.detach("old", true)
        assertEquals(AnalyticsScreen.UNKNOWN, tracker.crashScreen)
        tracker.attach("new", "events", AnalyticsScreen.EVENTS, true)
        tracker.attach("detail", "event_detail", AnalyticsScreen.EVENT_DETAIL, true)
        tracker.detach("new", false)
        assertEquals(AnalyticsScreen.EVENT_DETAIL, tracker.crashScreen)
    }

    @Test fun visibleMeaningfulContentCreatesOneView() {
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, true)
        assertTrue(views.isEmpty())
        tracker.content("owner", true, null)
        repeat(5) { tracker.content("owner", true, null) }
        assertEquals(listOf(AnalyticsScreen.EVENTS), views)
    }

    @Test fun preloadingAndNonVisibleContentAreNotViews() {
        tracker.content("preload", true, null)
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, false)
        tracker.content("owner", true, null)
        assertTrue(views.isEmpty())
        tracker.visible("owner", true)
        assertEquals(1, views.size)
    }

    @Test fun rotationRetainsTheVisit() {
        tracker.attach("old", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("old", true, null)
        tracker.detach("old", rotating = true)
        tracker.attach("new", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("new", true, null)
        assertEquals(1, views.size)
    }

    @Test fun refreshAndForegroundResumeDoNotCreateAnotherView() {
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("owner", true, null)
        tracker.content("owner", false, null)
        tracker.content("owner", true, null)
        tracker.visible("owner", false); tracker.visible("owner", true)
        assertEquals(1, views.size)
    }

    @Test fun leavingAndReturningToTheSameRouteIsANewVisit() {
        tracker.attach("first", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("first", true, null)
        tracker.detach("first", false)
        tracker.attach("second", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("second", true, null)
        assertEquals(2, views.size)
    }

    @Test fun registrationAndVerificationAreDifferentScreens() {
        tracker.attach("form", "registration", AnalyticsScreen.REGISTRATION, true)
        tracker.content("form", true, null)
        tracker.attach("verification", "email_verification", AnalyticsScreen.EMAIL_VERIFICATION, true)
        tracker.content("verification", true, null)
        tracker.detach("form", false)
        assertEquals(listOf(AnalyticsScreen.REGISTRATION, AnalyticsScreen.EMAIL_VERIFICATION), views)
    }

    @Test fun errorsAreReportedOnlyWhenVisibleAndOncePerFailure() {
        val failure = TelemetryFailure.from(IOException())!!
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, false)
        tracker.content("owner", false, failure)
        assertTrue(errors.isEmpty())
        tracker.visible("owner", true)
        repeat(3) { tracker.content("owner", false, failure) }
        tracker.detach("owner", true)
        tracker.attach("rotated", "events", AnalyticsScreen.EVENTS, true)
        tracker.content("rotated", false, failure)
        assertEquals(listOf(failure), errors)
        assertTrue(views.isEmpty())
    }

    @Test fun separateFailuresWithTheSameMessageAreBothReported() {
        tracker.attach("owner", "events", AnalyticsScreen.EVENTS, true)
        repeat(2) { tracker.content("owner", false, TelemetryFailure.from(IOException("same message"))) }
        assertEquals(2, errors.size)
        assertNotEquals(errors[0].id, errors[1].id)
    }

    @Test fun lateUpdatesFromAnUnrelatedOrDisposedScreenAreIgnored() {
        tracker.attach("old", "events", AnalyticsScreen.EVENTS, true)
        tracker.detach("old", false)
        tracker.content("old", true, TelemetryFailure.from(IOException()))
        assertTrue(views.isEmpty())
        assertTrue(errors.isEmpty())
    }
}
