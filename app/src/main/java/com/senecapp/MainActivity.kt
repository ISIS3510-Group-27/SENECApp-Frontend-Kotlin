package com.senecapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.senecapp.ui.EventsScreen
import com.senecapp.ui.ProfileScreen
import com.senecapp.ui.AppBottomNavigation
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.senecapp.sensors.AdaptiveContrastPolicy
import com.senecapp.ui.DiscoverScreen
import com.senecapp.ui.OrganizationsViewModel

class MainActivity : ComponentActivity(), SensorEventListener {
    private val organizationsViewModel by lazy { ViewModelProvider(this)[OrganizationsViewModel::class.java] }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val lightSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private val contrastPolicy = AdaptiveContrastPolicy()
    private val ambientLux = mutableStateOf<Float?>(null)
    private val sensorAvailable = mutableStateOf(false)
    private val highContrast = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorAvailable.value = lightSensor != null

        setContent {
            var selectedTab by rememberSaveable { mutableStateOf("Discover") }
            BackHandler(enabled = selectedTab != "Discover") { selectedTab = "Discover" }
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        "Events" -> EventsScreen()
                        "Profile" -> ProfileScreen()
                        else -> DiscoverScreen(
                            showBottomNavigation = false,
                            highContrast = highContrast.value,
                            ambientLux = ambientLux.value,
                            sensorAvailable = sensorAvailable.value,
                            organizationsState = organizationsViewModel.state,
                            onSearch = organizationsViewModel::search,
                        )
                    }
                }
                AppBottomNavigation(selectedTab, highContrast.value) { selectedTab = it }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorAvailable.value = lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } == true
        if (!sensorAvailable.value) {
            contrastPolicy.reset()
            ambientLux.value = null
            highContrast.value = false
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || event.values.isEmpty()) return
        val lux = event.values[0]
        if (!lux.isFinite() || lux < 0f) return

        ambientLux.value = lux
        highContrast.value = contrastPolicy.update(lux)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
