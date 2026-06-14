# Recognize music — Shazam-style identification, whitelist-locked

Hand-authored docset for Zemer's "Recognize music" feature: tap a button (in-app or on the
home-screen widget), it listens for ~12 s, identifies the song, and shows it — but **only ever**
a song by a whitelisted artist. Everything here is derived from the code as of zemer-app
`2ab706d`; every claim cites the file/symbol that proves it.

## TL;DR

1. **Capture** ~12 s of mic audio and generate a **Shazam-compatible fingerprint on-device** —
   pure-Kotlin FFT, **no API key, no native lib**. Only the fingerprint (a few hundred bytes)
   leaves the device, never raw audio.
2. **Identify** by POSTing the fingerprint to the public `amp.shazam.com` endpoint → a
   `(title, artist)` string.
3. **Resolve to a whitelisted song**: use `(title, artist)` *only as a search query* into the
   app's existing YouTube-Music search, then run results through the **same `filterWhitelisted`**
   that gates the rest of the app, plus a **second, config-independent hard gate** against the
   `artist_whitelist` table. The raw Shazam metadata is **never shown**.
4. **Show / play / remember**: the result is a whitelisted `SongItem` — play it, and it's saved to
   recognition history.

> **The guarantee:** it is *impossible* for this feature to surface a song outside the whitelist —
> see [02-whitelist-guarantee.md](02-whitelist-guarantee.md). This is the whole point; if you change
> the resolver, preserve both gates.

## One UI everywhere

Both entry points open the **same** Zemer-branded popup (`RecognizeMusicDialogActivity`) — a
transparent, pure-black card over the current screen (like Google's Sound Search):

| Entry point | Trigger | Code |
|---|---|---|
| In-app FAB | Floating mic button on main screens (toggle in Settings → Appearance, default on) | `ui/component/RecognizeMusicFab.kt`, wired in `MainActivity` (`RecognizeMusicFabKey`) |
| Home-screen widget | Mic button on the combined player widget | `widget/MusicWidget.kt` (`RecognizeButton`) |

## File map

| Area | File | Role |
|---|---|---|
| DSP (ported, pure JVM) | `recognition/AudioResampler.kt` | 44.1 kHz → 16 kHz linear-interpolation resampler (`DecodedAudio`). |
| | `recognition/ShazamSignatureGenerator.kt` | FFT + Shazam signature encoder (uses `java.util.Base64`, so it is JVM-testable). |
| | `recognition/VibraSignature.kt` | Thin wrapper: `fromI16(pcm) → signature URI`. |
| Capture | `recognition/RecognitionAudioCapture.kt` | Records 12 s mono PCM16 → resamples → fingerprints. `Fingerprint(signature, sampleDurationMs)`. |
| Network | `recognition/shazam/Shazam.kt` | ktor (CIO) client to `amp.shazam.com`; rate-limit/retry/cache; response → `RecognitionResult`. |
| | `recognition/shazam/ShazamModels.kt` | `@Serializable` request/response DTOs + `RecognitionResult`. |
| Whitelist bridge | `recognition/RecognitionResolver.kt` | The single shared "recognized → whitelisted `SongItem`" bridge (both gates + history save). |
| | `recognition/RecognitionMatcher.kt` | Pure accuracy matcher (token recall gates + Jaccard ranking). |
| | `recognition/RecognitionMatchSelector.kt` | `select(...)` (picks from filtered candidates) + `isWhitelistedResult(...)` (the hard gate). |
| ViewModel | `viewmodels/RecognizeMusicViewModel.kt` | Drives the popup state machine; `RecognizeUiState`. |
| | `viewmodels/RecognitionHistoryViewModel.kt` | Exposes history; delete/clear. |
| UI | `ui/screens/recognition/RecognizeMusicDialogActivity.kt` | The branded popup (transparent activity). |
| | `ui/screens/recognition/RecognitionHistoryScreen.kt` | History list (play / remove / clear). |
| Widget | `widget/MusicWidget.kt` | Combined player + recognize widget (Glance), with the live seek bar. |
| Persistence | `db/entities/RecognitionHistoryEntity.kt` | `recognition_history` Room table (added in DB v33). |
| Tests | `app/src/test/.../recognition/*.kt` | DSP + matcher + hard-gate (fail-closed) unit tests. |

## Index

1. [Architecture & the recognition pipeline](01-architecture-and-pipeline.md)
2. [The whitelist guarantee (why non-kosher results are impossible)](02-whitelist-guarantee.md)
3. [Entry points, UI & navigation](03-entry-points-and-ui.md)
4. [Recognition history (Room v33)](04-recognition-history.md)
5. [The combined widget (player + recognize + seek bar)](05-widget.md)
6. [Testing, limitations & maintenance](06-testing-and-maintenance.md)

## Provenance

Ported from the sibling Metrolist fork's recognition feature (`~/zemer-fix/Metrolist`), then
adapted for Zemer: renamed into the `com.jtech.zemer.*` namespace, the Shazam metadata is never
displayed (whitelist bridge instead), the widget/popup are Zemer-built (Glance + a transparent
activity, no foreground microphone service), and recognition history stores only whitelisted songs.
