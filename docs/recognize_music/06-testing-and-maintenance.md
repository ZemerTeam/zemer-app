# 6 · Testing, limitations & maintenance

## Unit tests (`app/src/test/.../recognition/`)

Pure-JVM tests (no Android runtime) — run with `./gradlew :app:testDebugUnitTest`:

| Test | Guards |
|---|---|
| `ShazamSignatureGeneratorTest` | The ported FFT/encoder: valid `data:audio/vnd.shazam.sig;base64,…` shape, correct header magics, **self-consistent CRC32**, determinism, input-sensitivity, odd-length rejection. (JVM-testable because the port uses `java.util.Base64`.) |
| `AudioResamplerTest` | 44.1 kHz → 16 kHz output length, no-op when rates match, little-endian round-trip. |
| `RecognitionMatcherTest` | Accuracy matcher: exact match, **different-artist rejection**, shared-title-word rejection, diacritics/feat/brackets normalization, tightest-wins, blank-artist → no match. |
| `RecognitionMatchSelectorTest` | The whitelist invariant + **hard gate**: result is always a member of the candidate list; `isWhitelistedResult` passes when whitelisted, rejects when not, and **fails closed** (empty artists / null ids). |

What is **not** unit-tested (would need instrumentation/heavy infra; verify on-device):
`RecognitionAudioCapture` (real `AudioRecord`), the live `Shazam` network call, the Room migration at
runtime, the Glance widget rendering, and the popup activity. On-device, watch logcat tags
`RecognitionCapture`, `ShazamApi`, `ShazamSigGen`, `RecognizeMusicVM`, `RecognitionResolver`.

## Build / verify

- `./gradlew :app:assembleDebug` and `:app:assembleRelease` (release runs R8 — the Shazam
  `@Serializable` DTOs and Glance both need correct keep rules; release is the gate before any push).
- `bash scripts/ui-audit.sh` (the recognition screens/popup must keep passing it).

> Note: `:app` applies the **kotlinx-serialization plugin** (added for this feature). Without it the
> Shazam request/response models compile but have no runtime serializer → `SerializationException`.
> If you move the Shazam models, keep the plugin on whatever module hosts them.

## Limitations / not implemented

- **No draggable seek** in the widget (Glance/RemoteViews limitation) — progress + time are
  read-only; see [05](05-widget.md).
- **No Quick Settings tile** (Metrolist has one; deferred). Adding one would be a `TileService`
  launching `RecognizeMusicDialogActivity` — the popup already handles permission + recording.
- **Live widget seek** updates ~1×/s while playing (`MusicService` ticker) — a deliberate
  battery/jank trade-off chosen over a smoother but heavier cadence.
- Recognition sends an audio **fingerprint** (not raw audio) to a public endpoint with no API key.

## Maintenance notes

- **Preserve the whitelist guarantee** when editing `RecognitionResolver` — all three properties in
  [02](02-whitelist-guarantee.md) (never display Shazam metadata; force `filtersEnabled=true`; keep
  the fail-closed `isArtistWhitelisted` hard gate).
- **Shazam endpoint risk**: `amp.shazam.com` is unofficial. If recognition starts failing with HTTP
  errors, check `Shazam.kt` (endpoint, headers, rate limits) first; the request shape mirrors the
  Metrolist/SongRec approach.
- **Player rotation does not affect this feature** — recognition does not touch the streaming/cipher
  path; it only uses YouTube-Music **search** + the whitelist DB.
- **Namespace**: all recognition code is `com.jtech.zemer.*`. The only `com.metrolist.*` references
  are pre-existing `:innertube` types (`YouTube`, `SearchFilter`, `SongItem`, `WatchEndpoint`) already
  used across the app — don't introduce new `com.metrolist` symbols.
- These docs are **hand-authored** — `docs/generate.py` does not own this folder, so update them by
  hand when the feature changes. (The generator only rewrites `repository-map.md`,
  `reference/*.md`, `build-release.md`.)
