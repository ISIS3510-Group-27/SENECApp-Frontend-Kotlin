package com.senecapp.analytics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.UUID

@Composable
internal fun TelemetryScreenEffect(screen: AnalyticsScreen, meaningful: Boolean,
    failure: TelemetryFailure? = null, identity: String = screen.wireName) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val activity = remember(context) { context.telemetryActivity() }
    val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    val telemetry = remember(context, preview) { if (preview) null else AppTelemetry.get(context) }
    val owner = remember(screen, identity) { UUID.randomUUID().toString() }
    DisposableEffect(telemetry, lifecycle, owner) {
        try {
            telemetry?.attach(owner, "${screen.wireName}:$identity", screen,
                lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
        } catch (_: Exception) { }
        val observer = LifecycleEventObserver { _, event ->
            try {
                if (event == Lifecycle.Event.ON_RESUME) telemetry?.visible(owner, true)
                if (event == Lifecycle.Event.ON_PAUSE) telemetry?.visible(owner, false)
            } catch (_: Exception) { }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            try { telemetry?.detach(owner, activity?.isChangingConfigurations == true) } catch (_: Exception) { }
        }
    }
    SideEffect {
        try { telemetry?.content(owner, meaningful, failure) } catch (_: Exception) { }
    }
}

private fun Context.telemetryActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
