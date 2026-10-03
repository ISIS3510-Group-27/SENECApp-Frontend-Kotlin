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
import com.senecapp.ui.FreeNowViewModel
import com.senecapp.ui.ProfileScreen
import com.senecapp.ui.AppBottomNavigation
import com.senecapp.ui.LocalBackendAccess
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.senecapp.sensors.AdaptiveContrastPolicy
import com.senecapp.ui.DiscoverScreen
import com.senecapp.ui.GroupRecommendationsViewModel
import com.senecapp.ui.OrganizationsViewModel
import com.senecapp.ui.CampusEventsViewModel
import com.senecapp.ui.RegistrationScreen
import com.senecapp.ui.RegistrationViewModel

class MainActivity : ComponentActivity(), SensorEventListener {
    private val organizationsViewModel by lazy { ViewModelProvider(this)[OrganizationsViewModel::class.java] }
    private val recommendationsViewModel by lazy { ViewModelProvider(this)[GroupRecommendationsViewModel::class.java] }
    private val registrationViewModel by lazy { ViewModelProvider(this)[RegistrationViewModel::class.java] }
    private val freeNowViewModel by lazy { ViewModelProvider(this)[FreeNowViewModel::class.java] }
    private val campusEventsViewModel by lazy { ViewModelProvider(this)[CampusEventsViewModel::class.java] }
    // Keep request attribution independent from the Discover recommendation list.
    private val profileRecommendationsViewModel by lazy {
        ViewModelProvider(this)["profileRecommendations", GroupRecommendationsViewModel::class.java]
    }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val lightSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private val contrastPolicy = AdaptiveContrastPolicy()
    private val highContrast = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var selectedTab by rememberSaveable { mutableStateOf("Discover") }
            var registering by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = registering || selectedTab != "Discover") {
                if (registering) registering = false else selectedTab = "Discover"
            }
            if (registering) {
                RegistrationScreen(registrationViewModel.state, registrationViewModel::create,
                    registrationViewModel::resend, registrationViewModel::checkVerification,
                    registrationViewModel::useAnotherAccount, onClose = { registering = false })
            } else {
            LocalBackendAccess {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        "Events" -> EventsScreen(
                            freeNowState = freeNowViewModel.state,
                            onLoadFreeNow = freeNowViewModel::load,
                            onDemoNoon = freeNowViewModel::loadDemoNoon,
                            onOpenSuggestedEvent = freeNowViewModel::openEvent,
                            onLocationConsent = freeNowViewModel::setLocationConsent,
                            onRetryLocationConsent = freeNowViewModel::refreshConsent,
                            onUseLocation = freeNowViewModel::useLocation,
                            onLocationUnavailable = freeNowViewModel::locationUnavailable,
                            campusEventsState = campusEventsViewModel.state,
                            onLoadEvents = campusEventsViewModel::load,
                            onOpenEvent = campusEventsViewModel::open,
                            onCloseEvent = campusEventsViewModel::close,
                        )
                        "Profile" -> ProfileScreen(
                            onCreateAccount = { registrationViewModel.restoreAccount(); registering = true },
                            recommendationsState = profileRecommendationsViewModel.state,
                            onLoadRecommendations = profileRecommendationsViewModel::load,
                            onOpenRecommendedGroup = profileRecommendationsViewModel::openGroup,
                            onJoinRecommendedGroup = profileRecommendationsViewModel::joinGroup,
                        )
                        else -> DiscoverScreen(
                            showBottomNavigation = false,
                            highContrast = highContrast.value,
                            organizationsState = organizationsViewModel.state,
                            onSearch = organizationsViewModel::search,
                            onOpenOrganization = organizationsViewModel::open,
                            onCloseOrganization = organizationsViewModel::close,
                            recommendationsState = recommendationsViewModel.state,
                            onLoadRecommendations = recommendationsViewModel::load,
                            onOpenRecommendedGroup = recommendationsViewModel::openGroup,
                            onJoinRecommendedGroup = recommendationsViewModel::joinGroup,
                        )
                    }
                }
                AppBottomNavigation(selectedTab, highContrast.value) { selectedTab = it }
            }
            }
            }
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
