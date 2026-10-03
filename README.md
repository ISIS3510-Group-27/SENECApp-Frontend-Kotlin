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



4. Disable the switch, open another tab, or background the app and repeat: no refresh.
5. Save a suggested event, restart the app, and open Saved: it should remain.
   Stop the backend and check Saved again; then remove the event and restart to
   confirm its removal. An empty suggestions list is valid; use the noon preview
   under **•••** to find example events when needed.
