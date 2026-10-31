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

## Sprint 2

### Camilo Castilla

| Area | Contribution |
| --- | --- |
| Views | Discover, Organization Detail, Sign in and password recovery |
| Sensor | Ambient light adaptive contrast |
| Type 2 BQ | BQ3 interaction tracking for suggested events |
| Context Aware | Free-now event suggestions |
| Smart Feature | Personalized group recommendations |
| Authentication | Firebase login, session and password recovery |
| External Services | Backend organization search |

### Daniel Vergara

| Area | Contribution |
| --- | --- |
| Views | Events, Profile, Registration and email verification |
| Sensor | Shake to refresh event suggestions |
| Type 2 BQ | Search filter and organization-open tracking for BQ12 |
| Context Aware | Location aware nearby event suggestions |
| Smart Feature | On-demand profile group recommendation |
| Authentication | Firebase registration and email verification |
| External Services | Live upcoming events and membership filter |

### Luciana

| Area | Contribution |
| --- | --- |
| Views | My RSOs, Create RSO and event check-in |
| Sensor | QR event check-in with optional location |
| Smart Feature | Interest suggestions from the RSO name and description |
| External Services | RSO creation, memberships, saved groups and event check-in through the backend |
| Local Storage | My RSOs and saved groups snapshots |

## Sprint 3

### Camilo Castilla

| Area | Contribution |
| --- | --- |
| Type 1 BQ | BQ11 screen load time tracking for Discover and Organization Detail |
| Concurrency | Discover results load and process on the IO dispatcher; previous searches are cancelled when a new query starts |
| Local Storage | Complete Discover results are saved by user and search filters for use after closing the app |
| Eventual Connectivity | Discover shows matching saved results when the connection fails, or an error when none exist, with a Retry option; failed save actions are reverted |
| Caching | LRU memory cache for organization detail previews, limited to 20 entries with a 60-second expiration |

### Daniel Vergara

| Area | Contribution |
| --- | --- |
| Local Storage, Eventual Connectivity and Concurrency | Events offline storage with user-specific snapshots and retry |
| Caching | Event details LRU memory cache |
| Type 1 BQ | BQ1 error and crash tracking by screen and feature |
| Analytics Pipeline | Persistent event queue, batched delivery, retries and fatal crash recovery |
| Concurrency | Profile loading and JSON parsing on the IO dispatcher |
| UI | Dark theme and navigation updates aligned with the Flutter prototype |
