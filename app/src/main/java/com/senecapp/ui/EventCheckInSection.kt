package com.senecapp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.senecapp.data.CampusEvent
import com.senecapp.data.EventCoordinates
import com.senecapp.data.parseCheckInPayload
import com.senecapp.sensors.hasEventLocationPermission
import com.senecapp.sensors.readSingleEventLocation
import kotlinx.coroutines.launch



@Composable
internal fun EventCheckInSection(
    event: CampusEvent,
    state: CheckInUiState,
    onStartScan: (Int) -> Unit,
    onScanFailed: (Int, String) -> Unit,
    onScanCancelled: (Int) -> Unit,
    onCheckIn: (Int, String, EventCoordinates?, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val forThisEvent = state.eventId == event.id
    // Held between the permission callback and the request, since the launcher cannot carry it.
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    val currentEvent by rememberUpdatedState(event)
    val currentCheckIn by rememberUpdatedState(onCheckIn)

    fun submit(code: String, withLocation: Boolean) {
        locating = withLocation
        scope.launch {
            try {
                val fix = if (withLocation) readSingleEventLocation(context) else null
                val note = when {
                    !withLocation -> "Checked in without location. Allow it to record the distance."
                    fix == null -> "Could not get a location fix, so the distance was not recorded."
                    else -> null
                }
                currentCheckIn(currentEvent.id, code, fix, note)
            } finally {
                locating = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val code = pendingCode ?: return@rememberLauncherForActivityResult
        pendingCode = null
        submit(code, hasEventLocationPermission(context))
    }

    fun onCodeScanned(raw: String) {
        val payload = parseCheckInPayload(raw)
        when {
            payload == null ->
                onScanFailed(currentEvent.id, "That is not a SENECApp check-in code.")
            // The event id travels inside the QR, so a poster from another event is caught here
            // instead of being sent to the backend as a bad code.
            payload.eventId != currentEvent.id ->
                onScanFailed(currentEvent.id, "That code belongs to a different event.")
            hasEventLocationPermission(context) -> submit(payload.code, withLocation = true)
            else -> {
                pendingCode = payload.code
                permission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        }
    }

    fun launchScanner() {
        val activity = context.findActivity()
        if (activity == null) {
            onScanFailed(currentEvent.id, "The scanner could not be opened on this screen.")
            return
        }
        onStartScan(currentEvent.id)
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) onScanFailed(currentEvent.id, "The code could not be read.")
                else onCodeScanned(raw)
            }
            .addOnCanceledListener { onScanCancelled(currentEvent.id) }
            .addOnFailureListener { failure ->
                onScanFailed(
                    currentEvent.id,
                    failure.message ?: "The scanner is not available on this device.",
                )
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val busy = forThisEvent && (state.submitting || locating)
        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !event.cancelled && !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = viewAccent, contentColor = viewBackground,
                disabledContainerColor = viewSecondary, disabledContentColor = viewMuted,
            ),
            onClick = { launchScanner() },
        ) {
            Text(
                when {
                    event.cancelled -> "Event cancelled"
                    locating && forThisEvent -> "Getting your location…"
                    state.submitting && forThisEvent -> "Checking in…"
                    forThisEvent && state.result != null -> "Scan again"
                    else -> "Scan QR to check in"
                },
                fontFamily = viewNunito,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        if (!forThisEvent) return@Column

        state.result?.let { result ->
            ViewText(
                if (result.alreadyCheckedIn) "You had already checked in to this event."
                else "Checked in.",
                color = viewAccent,
            )
            result.distanceM?.let { ViewText("About ${it.toInt()} m from the venue.", color = viewMuted) }
            state.locationNote?.let { ViewText(it, color = viewMuted) }
        }

        state.error?.let { error ->
            ViewText(error)
            TextButton(onClick = { launchScanner() }) { Text("Try again", color = viewAccent) }
        }
    }
}

/** The code scanner needs an Activity; Compose hands out a themed wrapper around it. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
