package com.senecapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    private val highContrast = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DiscoverScreen(
                highContrast = highContrast.value,
                organizationsState = organizationsViewModel.state,
                onSearch = organizationsViewModel::search,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val registered = lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } == true
        if (!registered) {
            contrastPolicy.reset()
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

        highContrast.value = contrastPolicy.update(lux)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
