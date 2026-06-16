# Zemer — Play Console "Data Safety" form answers

> **DRAFT for review**, derived from the app's real data flows. Use it to fill Play Console →
> App content → **Data safety**. Verify each line against current behavior before submitting — Play
> cross-checks declarations against observed traffic, and mismatches cause rejection.
> "Collected" = sent off the device. "Shared" = sent to a separate company (a processor like Firebase
> is usually *collected*, not *shared*; a genuine third party like Shazam is *shared*).

## Overall
- **Does your app collect or share any required user data types?** → **Yes**
- **Is all collected data encrypted in transit?** → **Yes** (all endpoints are HTTPS)
- **Do you provide a way to request data deletion?** → **Yes** (in-app + account-deletion URL — see privacy policy)

## Data types to declare as COLLECTED

| Category → Type | Collected | Shared | Optional? | Ephemeral | Purposes |
|---|---|---|---|---|---|
| **Personal info → Email address** | Yes | No | Optional (only if you sign in) | No | Account management, App functionality (cross-device filter sync) |
| **Personal info → User IDs** | Yes | No | Optional (only if you sign in) | No | Account management, App functionality |
| **Audio → Voice or sound recordings** | Yes | **Yes (Shazam)** | Optional (only when using "Recognize music") | Raw audio processed ephemerally; a derived fingerprint is sent | App functionality (song recognition) |
| **App activity → App interactions** | Yes | No | — | No | Analytics, App functionality (Firebase Analytics) |
| **App info & performance → Crash logs** | Yes | No | — | No | App functionality, Diagnostics (Crashlytics) |
| **App info & performance → Diagnostics** | Yes | No | — | No | App functionality, Diagnostics |
| **Device or other IDs → Device or other IDs** | Yes | No | — | No | Analytics, App functionality (Crashlytics/Analytics IDs + sync device id) |

Notes:
- The content-filter **device info** synced to Firestore (manufacturer/model/OS/app version + a device
  id) maps to **Device or other IDs**; the settings themselves aren't a Play "user data" category.
- The parental passcode is stored only as a **hash** — not a declarable credential.
- Google sign-in may also expose your **name/photo**; declare **Personal info → Name** if the app
  displays/stores it (confirm). If not used, leave it out.

## Data types to declare as NOT collected
Location, Financial info, Health & fitness, Messages, Photos & videos, Files & docs, Calendar,
Contacts, Web browsing history.

- **Listening/search/recognition history, library, playlists, downloads** are **local-only** (Room/
  device storage) → **not** declared (not sent off device).

## Third parties (for your own reference; informs "Shared")
- **Apple / Shazam** (`amp.shazam.com`) — receives the audio fingerprint → **Shared**.
- **Google / Firebase** (Auth, Firestore, Crashlytics, Analytics) — processor → **Collected**.
- **YouTube / Google** — streaming requests with a session id → functional, not a data-broker share.

## Cross-checks before submitting
- [ ] Confirm whether Google **Name/photo** is stored (affects Personal info → Name).
- [ ] Confirm the account-deletion URL is live and matches the privacy policy.
- [ ] Re-confirm the Play (`play`) flavor's permission set matches what's declared (no install/overlay/
      accessibility/boot — already stripped).
- [ ] Target audience **13+ / content rating Everyone** set in App content (keeps you out of COPPA).
