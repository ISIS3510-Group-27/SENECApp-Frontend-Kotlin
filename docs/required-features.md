# Daniel Vergara: feature integrations

Favorites and their local-storage UI have been removed. Accelerometer refresh and
location-aware event suggestions remain available.

## External Services: live events

The Events screen replaces its static sample schedule with
`GET /api/v1/events?mine=false/true&limit=100`. All Events shows upcoming campus
events; My RSOs shows events from organizations the connected account belongs to.
Opening a card fetches `/api/v1/events/{id}?entry_point=events` for current details.
Lists and details expose loading, empty, error and retry states. Times use Bogota's
time zone. At most 100 events are displayed, with the total shown when truncated.
No local favorites, event registration or backend changes are introduced.

## Type 2 BQ: BQ12 search filters

Business question: **Which search filters do students use most before opening or
joining a student group?** This is the existing backend's BQ12, explicitly type 2.

Discover adds an Upcoming events filter, sent as `has_upcoming_events=true` to
`GET /api/v1/groups`, alongside the existing query and category filters. The
backend logs `group_searched` with its active filters. Opening a result requests
`GET /api/v1/groups/{id}?entry_point=search`; unfiltered browsing uses `explore`.
The backend logs `group_viewed`. This integration covers the opening outcome;
it does not add a search-to-join flow or fabricate analytics on the device.

The existing BQ12 analysis links searches to the same user's subsequent opens
or joins within 30 minutes. That is a time-window association, not proof that a
particular filter caused an interaction. There is no student-facing analytics panel.

## Smart Feature: one recommendation on demand in Profile

Find a group for me calls `GET /api/v1/recommendations/groups?limit=3` and displays
the first eligible group in the server's ranking, with the returned reasons.
The backend already uses a trained logistic-regression recommender. This change
integrates that model into Profile; it does not implement or claim a new model.
The display-only sample profile is distinct from the connected demo account used
by the API, and the recommendation card labels this explicitly.

View and join actions reuse the existing endpoints and pass
`entry_point=recommendation` and the corresponding `rec_request_id`. A dedicated
ViewModel keeps Profile's request ID separate from Discover's. Loading, failure,
empty and joined states are shown. No automatic joining takes place.

## Emulator checks

Run the backend with `AUTH_PROVIDER=dev` and the debug app with the normal
`http://10.0.2.2:8000/api/v1` configuration. Grant local-network access if prompted.

1. Open Events and scroll to Upcoming events. Switch All Events / My RSOs;
   the list should reflect the connected account's memberships. Open an event
   and verify its description. Favorites stars and Saved tabs should be absent.
2. Open Discover and enable Upcoming events, optionally with a category. Open a
   result. Backend analytics should contain `group_searched` with
   `has_upcoming_events` and `group_viewed` with `entry_point=search`. The existing
   admin BQ12 endpoint can inspect the aggregate; no admin credentials belong in
   the Android app.
3. Open Profile and tap Find a group for me. Verify a real organization and its
   reasons. Open its details. Joining is optional and changes the demo account's
   membership; the button should then say Joined and prevent duplicate taps.
4. With the backend temporarily unavailable, retry each integration: errors
   should be visible and the app should remain usable. Restore it and retry.
5. Verify location suggestions and optional Shake to refresh still work.

Build: `./gradlew assembleDebug --offline`.
Detector checks: `./scripts/check-shake-detector.ps1` after the debug build.
