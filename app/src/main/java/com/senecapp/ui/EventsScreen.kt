package com.senecapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.senecapp.data.CampusEvent
import com.senecapp.data.EventCoordinates

@Composable
fun EventsScreen(
    freeNowState: FreeNowUiState = FreeNowUiState(),
    onLoadFreeNow: () -> Unit = {},
    onDemoNoon: () -> Unit = {},
    onOpenSuggestedEvent: (Int) -> Unit = {},
    onLocationConsent: (Boolean) -> Unit = {},
    onRetryLocationConsent: () -> Unit = {},
    onUseLocation: (EventCoordinates?, Boolean) -> Unit = { _, _ -> },
    onLocationUnavailable: (String) -> Unit = {},
    campusEventsState: CampusEventsUiState = CampusEventsUiState(),
    onLoadEvents: (Boolean) -> Unit = {},
    onOpenEvent: (CampusEvent) -> Unit = {},
    onCloseEvent: () -> Unit = {},
    checkInState: CheckInUiState = CheckInUiState(),
    onStartScan: (Int) -> Unit = {},
    onScanFailed: (Int, String) -> Unit = { _, _ -> },
    onScanCancelled: (Int) -> Unit = {},
    onCheckIn: (Int, String, EventCoordinates?, String?) -> Unit = { _, _, _, _ -> },
) {
    LaunchedEffect(Unit) { onLoadFreeNow() }
    DemoView("SENECApp Events", showSampleLabel = false) {
        FreeNowSection(freeNowState, onLoadFreeNow, onDemoNoon, onOpenSuggestedEvent,
            onLocationConsent, onRetryLocationConsent, onUseLocation, onLocationUnavailable)
        CampusEventsSection(campusEventsState, onLoadEvents, onOpenEvent, onCloseEvent,
            checkInState, onStartScan, onScanFailed, onScanCancelled, onCheckIn)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EventsPreview() { EventsScreen() }
