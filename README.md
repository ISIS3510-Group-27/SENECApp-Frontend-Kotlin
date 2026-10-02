# SENECApp

Android app built with Jetpack Compose.

## Views

- Discover — Camilo Castilla
- Organization Detail — Camilo Castilla
- Events — Daniel Vergara
- Profile — Daniel Vergara

Events and Profile use clearly labeled sample data for a UI-only demo. Events supports
All Events / My RSOs filtering; Profile settings are display-only. Open both views from
the bottom navigation or their Compose Previews. No authentication or backend calls
are implemented for these views.

## Features

- Ambient light adaptive contrast — Camilo Castilla
- Backend organization search — Camilo Castilla

For the local demo, run the backend with `AUTH_PROVIDER=dev` and use an Android emulator.
The debug app connects to `http://10.0.2.2:8000/api/v1` with the demo account.
