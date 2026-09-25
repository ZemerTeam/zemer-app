# Recognize music - Shazam-style identification, whitelist-locked

Hand-authored docset (not owned by `docs/generate.py`; update it by hand when the feature changes).
Tap the mic (in-app FAB or the home-screen widget), the app listens for 12 s, identifies the song, and
shows it - but **only ever** a song by a whitelisted artist.

1. **Capture + fingerprint on-device** (pure-Kotlin FFT, no API key, no native lib). Only the
   fingerprint leaves the device, never raw audio.
2. **Identify** by POSTing the fingerprint to the unofficial public `amp.shazam.com` endpoint →
   `(title, artist)`.
3. **Resolve**: use `(title, artist)` *only as a search query* into YouTube-Music search, force
   `filterWhitelisted` on, then a second config-independent hard gate against `artist_whitelist`. The
   Shazam metadata is **never shown**.
4. **Show / play / remember** the whitelisted `SongItem` (saved to recognition history).

> **The guarantee:** this feature must never surface a song outside the whitelist - see
> [02-whitelist-guarantee.md](02-whitelist-guarantee.md). If you change the resolver, keep both gates.

## Index

1. [Architecture & the recognition pipeline](01-architecture-and-pipeline.md)
2. [The whitelist guarantee (resolver gates + history re-check)](02-whitelist-guarantee.md)
3. [Entry points, UI, history & the widget](03-entry-points-and-ui.md)

## File map (`app/src/main/kotlin/com/jtech/zemer/`)

| File | Role |
|---|---|
| `recognition/RecognitionAudioCapture.kt` | Records 12 s mono PCM16 @ 44.1 kHz → resamples → fingerprints; `Fingerprint(signature, sampleDurationMs)`. |
| `recognition/AudioResampler.kt` | Linear-interpolation resampler to 16 kHz. |
| `recognition/ShazamSignatureGenerator.kt`, `VibraSignature.kt` | Pure-Kotlin port of the vibra (SongRec) FFT fingerprinter; `VibraSignature.fromI16` is the thin entry. |
| `recognition/shazam/Shazam.kt`, `ShazamModels.kt` | Ktor (CIO) client to `amp.shazam.com` (throttle/concurrency/retry); `@Serializable` DTOs + `RecognitionResult`. |
| `recognition/RecognitionResolver.kt` | The single shared "recognized → whitelisted `SongItem`" bridge (both gates + history write). |
| `recognition/RecognitionMatcher.kt`, `RecognitionMatchSelector.kt` | Accuracy matcher; `select(...)` + the fail-closed `isWhitelistedResult(...)` hard gate. |
| `recognition/RecognitionHistoryFilter.kt`, `RecognitionHistoryPlayback.kt` | History whitelist re-check; history entry → `MediaMetadata` seed. |
| `viewmodels/RecognizeMusicViewModel.kt`, `RecognitionHistoryViewModel.kt` | Popup state machine (`RecognizeUiState`); whitelist-filtered history. |
| `ui/screens/recognition/RecognizeMusicDialogActivity.kt`, `RecognitionHistoryScreen.kt` | The popup; the history list. |
| `ui/component/RecognizeMusicFab.kt` | In-app entry point. |
| `widget/MusicWidget.kt`, `widget/WidgetLayout.kt` | Combined Glance player + recognize widget. |
| `db/entities/RecognitionHistoryEntity.kt` | `recognition_history` Room table. |

## Tests and verification

Pure-JVM unit tests in `app/src/test/kotlin/com/jtech/zemer/recognition/` (`./gradlew :app:testDebugUnitTest`):

| Test | Guards |
|---|---|
| `ShazamSignatureGeneratorTest` | Signature URI shape, header magics, self-consistent CRC32, determinism, odd-length rejection. |
| `AudioResamplerTest` | 44.1 → 16 kHz output length, no-op at equal rates, little-endian order kept on the no-op. |
| `RecognitionMatcherTest` | Exact match, different-artist and shared-title-word rejection, normalization, tightest-wins, blank artist → no match. |
| `RecognitionMatchSelectorTest` | Result is always a member of the candidate list; the hard gate passes/rejects and fails closed. |
| `RecognitionHistoryFilterTest` | History gate against the *current* whitelist, fails closed; `joinIds` round-trip. |
| `RecognitionHistoryPlaybackTest` | `-1` duration sentinel, name↔id pairing, id + thumbnail carry-over. |

`widget/WidgetLayoutTest` covers the widget's seek-row threshold. Not unit-tested (verify on-device):
real `AudioRecord` capture, the live Shazam call, the popup, Glance rendering. Logcat tags:
`RecognitionCapture`, `ShazamApi`, `ShazamSigGen`, `RecognizeMusicVM`, `RecognitionResolver`.

Build both debug and release: release runs R8, and the Shazam `@Serializable` DTOs need the
kotlinx-serialization plugin (applied to `:app`) - without it they compile but throw
`SerializationException` at runtime.

## Maintenance notes

- If recognition starts failing with HTTP errors, check `Shazam.kt` (endpoint, headers, throttle)
  first - the endpoint is unofficial and can change without notice.
- Recognition never touches the streaming/cipher path (only YouTube-Music search + the whitelist DB),
  so player rotation does not affect it.
- All recognition code is `com.jtech.zemer.*`; don't introduce new `com.metrolist` symbols (only the
  existing `:innertube` types are used).
- Not implemented: a Quick Settings tile (would be a `TileService` launching
  `RecognizeMusicDialogActivity`, which already handles permission + recording) and draggable widget
  seek (a Glance/RemoteViews limitation).
