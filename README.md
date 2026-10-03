# SENECApp

Android app built with Jetpack Compose.

## Views

- Discover — Camilo Castilla
- Organization Detail — Camilo Castilla
- Events — Daniel Vergara
- Profile — Daniel Vergara

## Features

- Ambient light adaptive contrast (Sensor) — Camilo Castilla
- Backend organization search (External Services) — Camilo Castilla
- Free-now event suggestions (Context Aware) — Camilo Castilla
- BQ3 interaction tracking (Type 2 BQ) — Camilo Castilla
- Location aware nearby event suggestions (Context Aware) — Daniel Vergara
- Personalized group recommendations (Smart Feature) — Camilo Castilla
- BQ2 recommendation-to-join tracking (Type 2 BQ) — Camilo Castilla
- Shake to refresh event suggestions (Accelerometer Sensor) — Daniel Vergara
- Saved event favorites on this device (Local Storage) — Daniel Vergara

For the local demo, run the backend with `AUTH_PROVIDER=dev` and use an Android emulator.
The debug app connects to `http://10.0.2.2:8000/api/v1` with the demo account.
In Events, Free Now uses the student's schedule and current time; debug builds include a noon demo option.
In Discover, For You shows three backend-ranked groups and explains each suggestion. Opening or joining one sends the recommendation ID back to the backend for BQ2.

In Events, **Shake to refresh** is off by default. When enabled, a deliberate shake
refreshes the same suggestions as the button, using the schedule and current time.
It does not request GPS or change profile consent. The accelerometer is registered
only while Events suggestions are visible and the app is resumed. Two distinct
acceleration peaks of at least 2.7 g within one second trigger one refresh, followed
by a 10-second cooldown. Ordinary orientation changes remain near 1 g and do not
trigger it. The manual refresh button is always available, including on devices
without an accelerometer. Thresholds should be validated on a real phone before release.

Tap a suggestion's star to save a local snapshot, then open **Saved** to read it even
if it disappears from recommendations or the backend is offline. Tap the star again
to remove it. Favorites survive app restarts and are stored only on this device;
clearing app data or uninstalling removes them. Saved details may become outdated.
No GPS coordinates, walking estimates, auth tokens, or recommendation IDs are stored.
Opening a saved snapshot does not send recommendation analytics.

### Testing without a physical phone

After the offline debug build, run `./scripts/check-shake-detector.ps1` to verify
that tilting, isolated bumps, sustained peaks, cooldown, lifecycle resets and invalid
readings behave correctly. These checks require only the local JDK and no new libraries.

1. Run the backend and debug app. On Android 17, allow local network access if prompted.
2. In Events, enable **Shake to refresh**. In the emulator's Extended Controls →
   Virtual Sensors → Device Pose, select Move and move the virtual phone quickly
   back and forth. Expect a brief refresh confirmation. Repeat within 10 seconds:
   no additional refresh should occur. Tilting slowly should not trigger a refresh.
3. For repeatable sensor input, use the emulator console through ADB. Send two
   acceleration peaks with a return to gravity between them (X:Y:Z in m/s²):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb emu sensor set acceleration 0:0:9.80665
& $adb emu sensor set acceleration 30:0:9.80665
Start-Sleep -Milliseconds 200
& $adb emu sensor set acceleration 0:0:9.80665
Start-Sleep -Milliseconds 200
& $adb emu sensor set acceleration -30:0:9.80665
Start-Sleep -Milliseconds 200
& $adb emu sensor set acceleration 0:0:9.80665
```

4. Disable the switch, open another tab, or background the app and repeat: no refresh.
5. Save a suggested event, restart the app, and open Saved: it should remain.
   Stop the backend and check Saved again; then remove the event and restart to
   confirm its removal. An empty suggestions list is valid; use the noon preview
   under **•••** to find example events when needed.
