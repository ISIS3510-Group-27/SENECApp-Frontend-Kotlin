package com.senecapp.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.data.EventCoordinates
import com.senecapp.sensors.hasEventLocationPermission
import com.senecapp.sensors.readSingleEventLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun NearbyEventsControls(
    state: FreeNowUiState,
    onConsent: (Boolean) -> Unit,
    onRetryConsent: () -> Unit,
    onLocation: (EventCoordinates?, Boolean) -> Unit,
    onFallback: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locationJob by remember { mutableStateOf<Job?>(null) }
    val currentState by rememberUpdatedState(state)
    val currentLocation by rememberUpdatedState(onLocation)
    val currentFallback by rememberUpdatedState(onFallback)

    fun readLocation() {
        if (currentState.locationOptIn != true || currentState.consentLoading) return
        locating = true
        locationJob = scope.launch {
            try {
                val fix = readSingleEventLocation(context)
                if (currentState.locationOptIn == true && !currentState.consentLoading) {
                    currentLocation(fix, hasEventLocationPermission(context))
                }
            } finally { locating = false }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locating = false
        if (currentState.locationOptIn == true && !currentState.consentLoading) {
            if (hasEventLocationPermission(context)) readLocation()
            else currentFallback("Location permission denied. Using your schedule.")
        }
    }

    LaunchedEffect(state.locationOptIn, state.consentLoading) {
        if (state.locationOptIn != true || state.consentLoading) {
            locationJob?.cancel()
            locating = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = viewAccent, contentColor = viewBackground,
                disabledContainerColor = viewSecondary, disabledContentColor = viewMuted),
            enabled = state.locationOptIn == true && !state.consentLoading && !locating && !state.loading,
            onClick = {
                if (hasEventLocationPermission(context)) readLocation()
                else {
                    locating = true
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
        ) {
            Text(when {
                locating -> "Getting your location…"
                state.result?.locationSource == "gps" -> "Update my location"
                else -> "Use my location"
            }, fontFamily = viewNunito, modifier = Modifier.padding(vertical = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ViewText("Allow location-based suggestions")
                Text("Your location is read only when you tap the button.",
                    color = viewMuted, fontFamily = viewNunito, fontSize = 12.sp)
            }
            Switch(checked = state.locationOptIn == true,
                enabled = state.locationOptIn != null && !state.consentLoading,
                colors = SwitchDefaults.colors(checkedThumbColor = viewBackground,
                    checkedTrackColor = viewAccent, uncheckedThumbColor = viewMuted,
                    uncheckedTrackColor = viewSecondary, uncheckedBorderColor = viewMuted),
                onCheckedChange = onConsent)
        }
        if (state.consentLoading) ViewText("Updating location preference…", color = viewMuted)
        if (state.locationOptIn == null && !state.consentLoading) {
            TextButton(onClick = onRetryConsent) { Text("Retry profile consent", color = viewAccent) }
        }
        if (state.locationOptIn == false) ViewText("Turn on location suggestions to use the button. Your schedule is still used.", color = viewMuted)
        state.locationMessage?.let { ViewText(it, color = viewMuted) }
    }
}
