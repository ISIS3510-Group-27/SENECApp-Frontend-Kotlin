package com.senecapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.senecapp.auth.SessionRepository
import com.senecapp.data.ProfileRepository
import com.senecapp.data.UserProfile
import com.senecapp.sensors.AdaptiveContrastPolicy
import com.senecapp.ui.*
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity(), SensorEventListener {
    private val sessionViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SessionViewModel(SessionRepository(application), ProfileRepository()) as T
        })[SessionViewModel::class.java]
    }
    private val registrationViewModel by lazy { ViewModelProvider(this)[RegistrationViewModel::class.java] }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val lightSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private val contrastPolicy = AdaptiveContrastPolicy()
    private val highContrast = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuthTheme {
                val session = sessionViewModel.state
                var registering by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(session.phase, session.uid) {
                    if (session.phase == SessionPhase.SIGNED_OUT) {
                        registering = false
                        registrationViewModel.clear()
                    } else {
                        registering = false
                        if (session.phase == SessionPhase.VERIFICATION) registrationViewModel.restoreAccount()
                    }
                }
                LaunchedEffect(registrationViewModel.state.verified, session.phase) {
                    if (session.phase == SessionPhase.VERIFICATION && registrationViewModel.state.verified) {
                        sessionViewModel.checkSession()
                    }
                }
                fun leaveRegistration() {
                    registrationViewModel.useAnotherAccount()
                    sessionViewModel.signOut()
                    registering = false
                }
                BackHandler(enabled = (registering && session.phase == SessionPhase.SIGNED_OUT) || session.phase == SessionPhase.VERIFICATION) {
                    if (!registrationViewModel.state.busy) leaveRegistration()
                }
                when (session.phase) {
                    SessionPhase.SIGNED_OUT, SessionPhase.VERIFICATION -> {
                        if (registering || session.phase == SessionPhase.VERIFICATION) {
                            RegistrationScreen(registrationViewModel.state, registrationViewModel::create,
                                registrationViewModel::resend, registrationViewModel::checkVerification,
                                onAnotherAccount = ::leaveRegistration, onClose = ::leaveRegistration)
                        } else SignInScreen(session, sessionViewModel::signIn, sessionViewModel::resetPassword,
                            onCreateAccount = { registrationViewModel.clear(); registering = true },
                            onClearMessage = sessionViewModel::clearMessage)
                    }
                    SessionPhase.CHECKING -> SessionStatus("Checking your session")
                    SessionPhase.SESSION_ERROR -> SessionStatus("Check your session", session.error,
                        onRetry = sessionViewModel::checkSession, onSignOut = { sessionViewModel.signOut() })
                    else -> LocalBackendAccess(onSignOut = { sessionViewModel.signOut() }) {
                        when (session.phase) {
                            SessionPhase.PROFILE_LOADING -> {
                                LaunchedEffect(session.uid) { sessionViewModel.loadProfile() }
                                SessionStatus("Loading your profile", onSignOut = { sessionViewModel.signOut() })
                            }
                            SessionPhase.PROFILE_ERROR -> SessionStatus("Could not load your profile", session.error,
                                onRetry = sessionViewModel::loadProfile, onSignOut = { sessionViewModel.signOut() })
                            SessionPhase.SIGNED_IN -> key(session.uid) {
                                ProtectedContent(requireNotNull(session.uid), requireNotNull(session.profile),
                                    highContrast.value, session.busy, session.error,
                                    sessionViewModel::loadProfile, onSignOut = { sessionViewModel.signOut() })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionViewModel.state.phase in setOf(SessionPhase.SIGNED_IN, SessionPhase.VERIFICATION,
                SessionPhase.PROFILE_ERROR, SessionPhase.SESSION_ERROR) && !registrationViewModel.state.busy) {
            sessionViewModel.checkSession()
        }
        val registered = lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } == true
        if (!registered) { contrastPolicy.reset(); highContrast.value = false }
    }

    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || event.values.isEmpty()) return
        val lux = event.values[0]
        if (lux.isFinite() && lux >= 0f) highContrast.value = contrastPolicy.update(lux)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
private fun SessionStatus(title: String, error: String? = null, onRetry: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null) {
    DemoView(title, showSampleLabel = false) {
        if (error == null) CircularProgressIndicator(color = viewAccent)
        else ViewText(error)
        onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
        onSignOut?.let { TextButton(onClick = it) { Text("Sign out") } }
    }
}

@Composable
private fun ProtectedContent(uid: String, profile: UserProfile, highContrast: Boolean, profileLoading: Boolean,
    profileError: String?, onRefreshProfile: () -> Unit, onSignOut: () -> Unit) {
    // This store is disposed on logout or session checks, cancelling requests and clearing user data.
    val owner = remember(uid) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val provider = remember(owner) { ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory()) }
    val organizations = remember(owner) { provider[OrganizationsViewModel::class.java] }
    val recommendations = remember(owner) { provider[GroupRecommendationsViewModel::class.java] }
    val freeNow = remember(owner) { provider[FreeNowViewModel::class.java] }
    val events = remember(owner) { provider[CampusEventsViewModel::class.java] }
    val profileRecommendations = remember(owner) { provider["profileRecommendations", GroupRecommendationsViewModel::class.java] }
    var selectedTab by rememberSaveable(uid) { mutableStateOf("Discover") }
    BackHandler(enabled = selectedTab != "Discover") { selectedTab = "Discover" }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                "Events" -> EventsScreen(freeNowState = freeNow.state, onLoadFreeNow = freeNow::load,
                    onDemoNoon = freeNow::loadDemoNoon, onOpenSuggestedEvent = freeNow::openEvent,
                    onLocationConsent = freeNow::setLocationConsent, onRetryLocationConsent = freeNow::refreshConsent,
                    onUseLocation = freeNow::useLocation, onLocationUnavailable = freeNow::locationUnavailable,
                    campusEventsState = events.state, onLoadEvents = events::load,
                    onOpenEvent = events::open, onCloseEvent = events::close)
                "Profile" -> ProfileScreen(profile = profile, loading = profileLoading, error = profileError,
                    onRefresh = onRefreshProfile, onSignOut = onSignOut,
                    recommendationsState = profileRecommendations.state, onLoadRecommendations = profileRecommendations::load,
                    onOpenRecommendedGroup = profileRecommendations::openGroup, onJoinRecommendedGroup = profileRecommendations::joinGroup)
                else -> DiscoverScreen(showBottomNavigation = false, highContrast = highContrast,
                    organizationsState = organizations.state, onSearch = organizations::search,
                    onOpenOrganization = organizations::open, onCloseOrganization = organizations::close,
                    recommendationsState = recommendations.state, onLoadRecommendations = recommendations::load,
                    onOpenRecommendedGroup = recommendations::openGroup, onJoinRecommendedGroup = recommendations::joinGroup)
            }
        }
        AppBottomNavigation(selectedTab, highContrast) { selectedTab = it }
    }
}
