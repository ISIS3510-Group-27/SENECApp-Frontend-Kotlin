package com.senecapp.analytics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.UUID

@Composable
internal fun TelemetryScreenEffect(screen: AnalyticsScreen, meaningful: Boolean,
    failure: TelemetryFailure? = null, identity: String = screen.wireName,
    measureLoad: Boolean = false, loadFailed: Boolean = false) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val activity = remember(context) { context.telemetryActivity() }
    val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    val telemetry = remember(context, preview) { if (preview) null else AppTelemetry.get(context) }
    val owner = remember(screen, identity) { UUID.randomUUID().toString() }
    var rendered by remember(owner, meaningful, measureLoad) { mutableStateOf(false) }
    LaunchedEffect(owner, meaningful, measureLoad) {
        if (measureLoad && meaningful) {
            // Wait for a content frame instead of timing only the HTTP response.
            withFrameNanos { }
            rendered = true
        }
    }
    DisposableEffect(telemetry, lifecycle, owner, measureLoad) {
        try {
            telemetry?.attach(owner, "${screen.wireName}:$identity", screen,
                lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true, measureLoad)
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
    val ready = meaningful && (!measureLoad || rendered)
    SideEffect {
        try { telemetry?.content(owner, ready, failure, loadFailed) } catch (_: Exception) { }
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
