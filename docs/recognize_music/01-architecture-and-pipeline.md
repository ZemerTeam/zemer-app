# 1 · Architecture & the recognition pipeline

The pipeline is four stages. Stages 1–2 are device-local DSP + one network call; stage 3 is the
Zemer-specific whitelist bridge; stage 4 is presentation. The orchestration lives in
`RecognizeMusicViewModel.start()` (popup) and is identical for the widget, because both share the
bridge in `RecognitionResolver`.

```
mic ─▶ RecognitionAudioCapture.capture(context)
        │  AudioRecord: 12 s, mono, PCM-16, 44.1 kHz
        │  AudioResampler.resample(...)        → 16 kHz
        │  VibraSignature.fromI16(pcm)         → "data:audio/vnd.shazam.sig;base64,…"
        ▼
     Fingerprint(signature, sampleDurationMs)
        │
        ▼  Shazam.recognize(signature, sampleDurationMs)        [network: amp.shazam.com]
     RecognitionResult(title, artist, …)        ← never shown to the user
        │
        ▼  RecognitionResolver.resolveWhitelisted(database, title, artist)
     YouTube.search("<title> <artist>", FILTER_SONG)
        │  .filterWhitelisted(database, forced filtersEnabled=true)   ── Gate 1
        │  .filterIsInstance<SongItem>()
        │  RecognitionMatchSelector.select(...)                       ── accuracy match
        │  isWhitelistedResult { database.isArtistWhitelisted(id) }   ── Gate 2 (hard, fail-closed)
        │  → records to recognition_history
        ▼
     Outcome.Resolved(SongItem) | NoMatch | Error
        │
        ▼  RecognizeUiState  →  popup card / widget
     a whitelisted SongItem (play it), or "No match"
```

## Stage 1 — capture & fingerprint (`recognition/`)

`RecognitionAudioCapture.capture(context)` (`RecognitionAudioCapture.kt`):

- Checks `RECORD_AUDIO` (`hasRecordPermission`), then records **12 s** of `AudioSource.MIC`,
  `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT` at **44 100 Hz** into a `ByteArrayOutputStream`. The loop
  honors coroutine cancellation (`coroutineContext.isActive`).
- `AudioResampler.resample(decoded, 16_000)` downsamples to **16 kHz** (linear interpolation;
  `AudioResampler.kt`).
- `VibraSignature.fromI16(resampled.data)` produces the signature. The actual work is in
  `ShazamSignatureGenerator` — a **pure-Kotlin port** of the vibra (SongRec) FFT fingerprinter:
  Hanning window, 2048-pt radix-2 FFT, peak detection across 4 frequency bands, CRC32-checked binary
  signature, base64 via **`java.util.Base64`** (deliberately not `android.util.Base64`, so the math
  is unit-testable on the JVM — see [06](06-testing-and-maintenance.md)).
- Returns `Fingerprint(signature, sampleDurationMs)`.

This stage needs **no API key and no native code**. Only the fingerprint string is produced; raw
audio never leaves `capture()`.

## Stage 2 — identify (`recognition/shazam/`)

`Shazam.recognize(signature, sampleDurationMs)` (`Shazam.kt`) POSTs to:

```
https://amp.shazam.com/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}
```

with a randomized geolocation + timezone and a spoofed Android `User-Agent`. No auth/API key. The
body carries only the fingerprint (`signature.uri`). Built-in **rate limiting** (1 req/s,
2 concurrent), **retry** with exponential backoff on 429, and a 5-minute **result cache**. The
response is parsed (`ShazamResponseJson.toRecognitionResult`) into `RecognitionResult` — of which
Zemer uses essentially only `title` and `artist`.

> ⚠️ This is an **unofficial public endpoint**. It can rate-limit or change without notice — the
> harness-style risk note is in [06](06-testing-and-maintenance.md).

## Stage 3 — resolve to a whitelisted song (`RecognitionResolver`)

`RecognitionResolver.resolveWhitelisted(database, title, artist)` is the heart of the Zemer
adaptation and is covered in full in [02-whitelist-guarantee.md](02-whitelist-guarantee.md). In
short: search YouTube Music for the recognized text, keep only whitelisted `SongItem`s (Gate 1),
pick the best match (`RecognitionMatchSelector` / `RecognitionMatcher`), confirm it against the
`artist_whitelist` table (Gate 2, fail-closed), record it to history, and return
`Outcome.Resolved(song)` / `NoMatch` / `Error`. **The Shazam metadata is never returned to the UI.**

## Stage 4 — presentation (`RecognizeMusicViewModel` → popup / widget)

`RecognizeMusicViewModel.start()` runs stages 1–3 in `viewModelScope`, publishing a `RecognizeUiState`
StateFlow as it goes:

```
Idle → (no permission ⇒ PermissionRequired)
     → Listening → Identifying → Searching
     → Result(SongItem) | NoMatch | Error
```

`Result` is the **only** state that carries content, and that content is always a whitelist-confirmed
`SongItem`. The popup (`RecognizeMusicDialogActivity`) renders the state; tapping the result plays it
via the existing whitelist-guarded `watch?v=` deep link. See
[03-entry-points-and-ui.md](03-entry-points-and-ui.md).
