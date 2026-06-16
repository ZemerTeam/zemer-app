# Zemer — Privacy Policy

> **DRAFT for review.** This is generated from the app's actual data flows (code-derived), not legal
> advice. Review it, fill the `{{PLACEHOLDERS}}`, have someone qualified check it, then host it at a
> public URL and paste that URL into Play Console (App content → Privacy policy).

**App:** Zemer (`com.jtech.zemer`)
**Effective date:** {{EFFECTIVE_DATE}}
**Contact:** {{CONTACT_EMAIL}}
**Publisher:** {{PUBLISHER_NAME}}

Zemer is a music player with content filtering ("Kosher" whitelist, KidZone, per-artist controls).
This policy explains what data the app handles, where it goes, and your choices.

## Summary
- Zemer is **free** and shows **no ads**. We do **not** sell your data.
- Most of your data (library, likes, playlists, listening/search history, downloads) stays **on your
  device**.
- Some data is sent to **Google/Firebase**, **Apple's Shazam service**, and **YouTube** to provide
  specific features, described below.

## Data we collect and why

### 1. Account information (only if you sign in)
If you sign in with a Google account, we receive your **email address** and a **Google user ID** via
Google/Firebase Authentication. These are used to **sync your content-filter settings across your
devices** (e.g. KidZone / parental controls). If you use the app without signing in, or with the
anonymous option, no personal account profile is created for you.

### 2. Content-filter settings and device information (sync)
When sign-in is active, your content-filter preferences (filtering on/off, allow-female-singers,
block-videos, the parental passcode **hash**) and **device information** (device name, manufacturer,
model, Android version, app version, and a device identifier) are stored in **Google Firebase
Firestore** so your settings stay consistent across your devices. The passcode is stored only as a
one-way hash.

### 3. Microphone — only when you use "Recognize music"
When you tap **Recognize music**, the app records a **short audio sample** from your microphone,
converts it into an **acoustic fingerprint**, and sends that fingerprint to **Apple's Shazam service
(`amp.shazam.com`)** to identify the song. The microphone is used **only** during recognition; the
raw recording is **not stored** and is **not** sent anywhere except as the fingerprint described.

### 4. Crash and diagnostics data
We use **Google Firebase Crashlytics** (including native crash symbols) and **Firebase Analytics**
to diagnose crashes and understand basic, aggregate usage so we can fix bugs and improve the app.
This includes crash logs, device state at the time of a crash, app-interaction events, and device or
other identifiers.

### 5. Streaming
To play music, the app sends playback requests to **YouTube / Google** using a session identifier
(visitor data) and, if you are signed in, your session cookie. This is required to retrieve audio
streams.

## Data stored only on your device
Your saved songs, likes, subscriptions, playlists, listening and search history, recognition history,
and downloaded files are stored **locally on your device** and are not collected by us (they may be
removed by clearing the app's data or uninstalling).

## Third-party services
- **Google / Firebase** (Authentication, Firestore, Crashlytics, Analytics) — see Google's Privacy
  Policy: https://policies.google.com/privacy
- **YouTube / Google** (music streaming) — https://policies.google.com/privacy
- **Apple / Shazam** (song recognition) — https://www.apple.com/legal/privacy/

## Data sharing
We do not sell personal data. Audio fingerprints are shared with Shazam solely to identify songs.
Data sent to Google/Firebase and YouTube is processed to provide the features above.

## Children
Zemer is intended for a general audience **age 13 and older** and is **not directed to children
under 13**. The KidZone feature is a parental curation tool for use under adult supervision.

## Data retention and deletion
- Locally stored data is removed when you clear the app's data or uninstall the app.
- To delete synced data and your account record: {{DELETION_INSTRUCTIONS — in-app path + the public
  account-deletion URL required by Play, e.g. https://{{HOST}}/delete-account}}.
- Crash/analytics data is retained per Google Firebase's standard retention.

## Permissions (why each is requested)
- **Microphone** — song recognition (section 3), only during use.
- **Notifications** — playback controls and (GitHub build) update notices.
- **Network / network state** — streaming and sync.
- **Nearby Wi-Fi devices** — {{CONFIRM feature, e.g. local device discovery}}.
- (The Google Play build does **not** request install-packages, system-overlay, accessibility, or
  boot permissions.)

## Changes
We may update this policy; the "Effective date" above will change accordingly.

## Contact
{{CONTACT_EMAIL}}
