# SENECApp

## Backend connection (AWS)

Debug and release builds use `https://35-170-175-16.sslip.io/api/v1` by default.
Run the app from Android Studio, or build an installable debug APK from PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Release uses the same backend URL; distributing a release APK also requires your signing configuration.

Sign in with a verified `@uniandes.edu.co` account using Firebase email/password authentication.
`app/google-services.json` must belong to Firebase project `senecapp` and include Android package
`com.senecapp` (the included file does). An account created only through Microsoft sign-in needs
an email/password provider linked in Firebase before it can use this client's sign-in form.

The app refreshes the Firebase ID token when validating the session, sends
`Authorization: Bearer <ID token>` on API requests, and refreshes/retries once on HTTP 401.
HTTP 403 means access was denied (for example an unverified email or a disallowed domain).
There is no dev-token authentication mode; `-PauthProvider` is not used.

### Optional local settings

No backend `.env`, AWS credentials or Firebase service-account key is needed on the phone or in this repo.
`local.properties` is for the Android SDK location. To change the backend on your machine:

1. Copy `backend.local.properties.example` to `backend.local.properties` (git-ignored).
2. Set `apiBaseUrl` to the full API base URL, including `/api/v1`.
3. Rebuild/reinstall the app; the URL is compiled into the APK.

Gradle project properties override that file, and the AWS URL is the fallback for both build types:

```powershell
.\gradlew.bat :app:assembleDebug "-PapiBaseUrl=https://35-170-175-16.sslip.io/api/v1"
# Local backend on the emulator; it must also use AUTH_PROVIDER=firebase:
.\gradlew.bat :app:assembleDebug "-PapiBaseUrl=http://10.0.2.2:8000/api/v1"
```

Use HTTPS for AWS and release builds. Plain HTTP is allowed only by the debug manifest.
The optional local override affects both debug and release.

Public checks: [database health](https://35-170-175-16.sslip.io/api/v1/health/db),
[Swagger](https://35-170-175-16.sslip.io/docs).
Loading protected data in Swagger or the app requires a real Firebase ID token; `dev:<email>` does not work.

## Views

- Discover — Camilo Castilla
- Organization Detail — Camilo Castilla
- Events — Daniel Vergara
- Profile — Daniel Vergara
- Registration and email verification — Daniel Vergara
- Sign in and password recovery — Camilo Castilla

## Features

- Ambient light adaptive contrast (Sensor) — Camilo Castilla
- BQ3 interaction tracking (Type 2 BQ) — Camilo Castilla
- Free-now event suggestions (Context Aware) — Camilo Castilla
- Personalized group recommendations (Smart Feature) — Camilo Castilla
- Firebase login, session and password recovery (Authentication) — Camilo Castilla
- Backend organization search (External Services) — Camilo Castilla

- Shake to refresh event suggestions (Sensor) — Daniel Vergara
- Search filter and organization-open tracking for BQ12 (Type 2 BQ) — Daniel Vergara
- Location aware nearby event suggestions (Context Aware) — Daniel Vergara
- On-demand profile group recommendation (Smart Feature) — Daniel Vergara
- Firebase registration and email verification (Authentication) — Daniel Vergara
- Live upcoming events and membership filter (External Services) — Daniel Vergara

## Sprint 3 Features

- Discover offline storage (Local Storage, Eventual Connectivity and Concurrency) — Camilo Castilla
- Organization details LRU memory cache (Caching) — Camilo Castilla
- BQ11 screen load time tracking (Type 1 BQ) — Camilo Castilla
