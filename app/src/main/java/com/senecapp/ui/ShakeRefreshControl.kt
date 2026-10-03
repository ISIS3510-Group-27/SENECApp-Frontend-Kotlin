package com.senecapp.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.senecapp.sensors.ShakeDetector
import kotlinx.coroutines.delay

@Composable
internal fun ShakeRefreshControl(state: FreeNowUiState, active: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember(manager) { manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val detector = remember { ShakeDetector() }
    var enabled by rememberSaveable { mutableStateOf(false) }
    var available by remember(accelerometer) { mutableStateOf(accelerometer != null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var waitingForRefresh by remember { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    val refresh by rememberUpdatedState(onRefresh)
    val lifecycle = (context as? LifecycleOwner)?.lifecycle

    DisposableEffect(manager, accelerometer, enabled, active, lifecycle) {
        var registered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
                if (currentState.loading || currentState.consentLoading) {
                    detector.resetMotion()
                    return
                }
                if (detector.sample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)) {
                    feedback = "Shake detected · Refreshing suggestions…"
                    waitingForRefresh = true
                    refresh()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun stop() {
            if (registered) manager.unregisterListener(listener)
            registered = false
            detector.resetMotion()
        }
        fun start() {
            if (enabled && active && accelerometer != null && !registered) {
                registered = manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
                available = registered
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) start()
        onDispose {
            lifecycle?.removeObserver(observer)
            stop()
        }
    }

    LaunchedEffect(state.loading, state.error) {
        if (waitingForRefresh && !state.loading) {
            feedback = if (state.error == null) "Suggestions refreshed" else "Could not refresh · Try the button"
            waitingForRefresh = false
        }
    }
    LaunchedEffect(feedback) {
        if (feedback != null && !waitingForRefresh) {
            delay(4_000)
            feedback = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            ViewText("Shake to refresh")
            if (enabled || !available) {
                Text(if (available) "A quick shake updates suggestions." else "Sensor unavailable · Use Refresh suggestions",
                    color = viewMuted, fontFamily = viewNunito, fontSize = 12.sp)
            }
        }
        Switch(checked = enabled, enabled = available, onCheckedChange = { enabled = it },
            colors = SwitchDefaults.colors(checkedThumbColor = viewBackground, checkedTrackColor = viewAccent,
                uncheckedThumbColor = viewMuted, uncheckedTrackColor = viewSecondary, uncheckedBorderColor = viewMuted))
    }
    feedback?.let { ViewText(it, color = viewAccent) }
}
