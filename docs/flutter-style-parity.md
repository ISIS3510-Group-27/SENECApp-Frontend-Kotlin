# Flutter / Kotlin visual parity

Flutter is the visual reference. Keep the native presentation aligned with:

- `lib/core/theme/app_colors.dart` and `app_theme.dart`.
- `lib/core/widgets/org_image.dart`, `rso_list_tile.dart` and `surfaces.dart`.
- `lib/features/discover/discover_screen.dart`, `shell/home_shell.dart`, `my_rsos/my_rsos_screen.dart`, and `profile/profile_screen.dart`.

## Shared visual foundations

| Role | Value |
| --- | --- |
| Background | `#171A21` |
| Foreground | `#F0E2E7` |
| Brand / primary action | `#A50104` |
| Accent | `#FFBA08` |
| Secondary surface | `#1D3557` |
| Card | `#1E2633` |
| Muted text | `#8B94B0` |
| Body text | `#C8CDE0` |
| Headings / body | Bricolage Grotesque / Nunito |
| Page gutter | 24 dp |
| Chip / card / hero radius | 12 / 16 / 24 dp |
| Feed thumbnail / carousel card | 56 square / 208 x 144 dp |

`ui/ViewComponents.kt` and `AuthTheme` hold the shared native styling. Discover retains its existing outdoor/high-contrast palette. Screen headers, forms, cards, chips and buttons use these foundations.

Bundled group photos, the logo and both mascots are copied from Flutter without image modifications. The launcher uses the SENECApp goat-and-chess logo on a black adaptive background; the installed app is named `SENECApp Kotlin` to distinguish the clients. `OrganizationImage` resolves bundled photos from normalized group names, with a colored gradient fallback. When adding Flutter artwork, copy it into `res/drawable-nodpi` and extend that mapping.

## Scope

Updated the presentation of sign-in, registration/verification, Discover, group details, My RSOs, Profile, Events, event details and group proposals. Existing data sources, authentication, API contracts, validation and ViewModels remain unchanged. Existing save/join callbacks are reused by the styled controls. Group and event details use a full-screen presentation of the existing state.

Events retains its existing location, shake, demo and QR controls. Location and shake preferences sit below the event feed; Flutter instead opens its free-now tools in a separate route. No new route or sensor behavior was introduced.

## Differences that require a later logic/data change

| Visual difference | Missing integration |
| --- | --- |
| User-uploaded cover images | Kotlin models do not expose `image_url`; add the field, URL resolution and remote-image loading. Bundled art works now. |
| Exact colors in recommendation/event cards | Some Kotlin models omit the server's group color. Presentation currently falls back to a category accent. |
| Official badges and precise next-event pills in Discover | The current organization model omits `verified` and the next event's date/title. Do not fabricate these badges or timestamps. |
| Profile RSO/event counters and attendance in My RSOs | Load and share membership/attendance statistics. Unavailable counters display an em dash. My RSOs shows the existing saved-group count instead of a semester statistic. |
| Full group details | Tags, links, organizers, upcoming group events, membership state and actions need a richer detail model and wiring. The current description, member count and existing save/join actions are presented. |
| Notification inbox and unread badge | Requires notification state and navigation. The existing decorative bell remains without an unread dot. |
| Discover advanced filters, See all and New RSO placement | Requires filter/navigation wiring. Existing category/upcoming filters and the My RSOs create action are preserved. |
| Events search/date filters, bookmarks, itinerary and weekly summary | These Flutter features are not implemented in Kotlin. No nonfunctional controls or invented weekly totals were added. |
| Profile interest/schedule editors and additional settings | Requires the corresponding repositories, state and navigation. Existing profile fields/preferences remain visible. |
| Microsoft sign-in | Requires Firebase provider integration; the password sign-in layout is aligned now. |
| Independent tab navigation stacks and retained scroll positions | Requires navigation/state changes; this styling pass keeps Kotlin's existing tab behavior. |

## Verification

Use the existing `:app:testDebugUnitTest` checks for regression coverage and assemble debug/release. Visual inspection uses a temporary emulator and screen fixtures without accounts or backend requests. The fixture Activity and its manifest entry are removed before the final build; the real application flow remains the entry point.
