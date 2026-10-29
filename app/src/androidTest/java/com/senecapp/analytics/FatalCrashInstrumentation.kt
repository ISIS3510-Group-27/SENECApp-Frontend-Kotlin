package com.senecapp.analytics

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.senecapp.SENECApplication

// Platform instrumentation: no test dependency or production crash trigger is needed.
// adb shell am instrument -w com.senecapp.test/com.senecapp.analytics.FatalCrashInstrumentation
// To verify persistence across separate processes, run with -e mode seed, force-stop, then -e mode recover.
class FatalCrashInstrumentation : Instrumentation() {
    private var mode: String? = null
    private val original = PendingCrash("b2970e07-1052-43d8-b1e7-d918715ee609", "2026-10-29T12:00:00Z",
        AnalyticsScreen.UNKNOWN, "IllegalStateException")

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mode = arguments?.getString("mode")
        start()
    }

    override fun onStart() {
        val tests = when (mode) {
            "seed" -> listOf("seedForNextProcess" to ::seed)
            "recover" -> listOf("recoverFromPreviousProcess" to ::recover)
            else -> listOf("applicationAndFirebaseInitialization" to ::initialization,
                "synchronousPersistenceAndRecovery" to ::persistence,
                "delegationAndMinimalCrashRecord" to ::delegation)
        }
        val report = StringBuilder()
        var failures = 0
        tests.forEach { (name, test) ->
            try { test(); report.append("PASS $name\n") }
            catch (_: Throwable) { failures++; report.append("FAIL $name\n") }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()); putInt("failures", failures); putInt("tests", tests.size) })
    }

    private fun initialization() {
        check(targetContext.applicationContext is SENECApplication)
        check(AppTelemetry.get(targetContext) != null)
        check(Thread.getDefaultUncaughtExceptionHandler() is TelemetryCrashHandler)
        // Observe existing initialization only; never initialize Firebase or change an authenticated session.
        if (FirebaseOptions.fromResource(targetContext) != null) {
            check(FirebaseApp.getApps(targetContext).any { it.name == FirebaseApp.DEFAULT_APP_NAME })
        }
    }

    private fun crashPreferences() = targetContext.getSharedPreferences("fatal_instrumentation_markers", Context.MODE_PRIVATE)
    private fun outboxPreferences() = targetContext.getSharedPreferences("fatal_instrumentation_outbox", Context.MODE_PRIVATE)
    private fun event(crash: PendingCrash) = AnalyticsEvent.crash(crash,
        "db28897f-9653-411b-83b5-a17914770ed4", AnalyticsMetadata("1.0", "Test model", "Test OS"))

    private fun seed() {
        check(crashPreferences().edit().clear().commit())
        check(outboxPreferences().edit().clear().commit())
        check(PendingCrashStore(crashPreferences()).save(original))
    }

    private fun recover() {
        val store = PendingCrashStore(crashPreferences())
        check(store.read() == listOf(original))
        val outbox = AnalyticsOutboxStore { outboxPreferences() }
        check(store.recoverInto(outbox, ::event) == 1)
        check(store.read().isEmpty())
        val restored = outbox.read().single().event
        check(restored.id == original.id && restored.occurredAt == original.occurredAt && restored.fatal)
        check(AnalyticsEvent.read(restored.json()) == restored)
        check(store.recoverInto(outbox, ::event) == 0)
        check(outboxPreferences().edit().clear().commit())
    }

    private fun persistence() { seed(); recover() }

    private fun delegation() {
        check(crashPreferences().edit().clear().commit())
        val store = PendingCrashStore(crashPreferences())
        val failure = IllegalStateException("Do not collect this private message")
        var delegated = false
        val previous = Thread.UncaughtExceptionHandler { _, received ->
            check(received === failure)
            check(store.read().size == 1) // commit completed before the previous handler runs.
            delegated = true
        }
        TelemetryCrashHandler(previous, store, { AnalyticsScreen.UNKNOWN })
            .uncaughtException(Thread.currentThread(), failure)
        check(delegated)
        check(!store.read().single().json().toString().contains("private"))
        check(crashPreferences().edit().clear().commit())
    }
}
