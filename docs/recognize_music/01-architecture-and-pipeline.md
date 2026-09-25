# 1 · Architecture & the recognition pipeline

`RecognizeMusicViewModel.start()` runs the whole pipeline in `viewModelScope`; both entry points (FAB
and widget) open the same popup, so they share it.

```
mic ─▶ RecognitionAudioCapture.capture(context)
        │  AudioRecord: 12 s, mono, PCM-16, 44.1 kHz
        │  AudioResampler.resample(...)        → 16 kHz
        │  VibraSignature.fromI16(pcm)         → "data:audio/vnd.shazam.sig;base64,…"
        ▼
     Fingerprint(signature, sampleDurationMs)
        ▼  Shazam.recognize(signature, sampleDurationMs)        [network: amp.shazam.com]
     Shazam.Outcome.NoMatch → RecognizeUiState.NoMatch  |  Failed → Error   (return: no resolver call)
     Shazam.Outcome.Found(RecognitionResult)                      ← never shown to the user
        ▼  RecognitionResolver.resolveWhitelisted(database, title, artist)
     YouTube.search("<title> <artist>", FILTER_SONG)
        │  .filterWhitelisted(database, forced filtersEnabled=true)   ── Gate 1
        │  .filterIsInstance<SongItem>()
        │  RecognitionMatchSelector.select(...)                       ── accuracy match
        │  isWhitelistedResult { database.isArtistWhitelisted(id) }   ── Gate 2 (fail-closed)
        │  → records to recognition_history
        ▼
     RecognitionResolver.Outcome.Resolved(SongItem) | NoMatch | Error
        ▼
     RecognizeUiState → popup
```

## Stage 1 - capture & fingerprint (`recognition/`)

`RecognitionAudioCapture.capture(context)` checks `RECORD_AUDIO` (`hasRecordPermission`), records
`RECORDING_DURATION_MS` (12 s) from `AudioSource.MIC` at `RECORDING_SAMPLE_RATE` (44 100 Hz), mono
PCM-16, honoring coroutine cancellation, then resamples to `VibraSignature.REQUIRED_SAMPLE_RATE`
(16 kHz) and fingerprints. Raw audio never leaves `capture()`.

`ShazamSignatureGenerator` is a pure-Kotlin port of the vibra FFT fingerprinter (Hanning window,
2048-point FFT, peak detection, CRC32-checked binary signature). It base64-encodes with
**`java.util.Base64`**, deliberately not `android.util.Base64`, so it is unit-testable on the JVM.

## Stage 2 - identify (`recognition/shazam/Shazam.kt`)

`Shazam.recognize` POSTs only the fingerprint to
`https://amp.shazam.com/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}` with a randomized
geolocation + timezone and a random Android `User-Agent`; no API key. Limits: a `Semaphore` of
`MAX_CONCURRENT_REQUESTS` (2), a `Mutex`-guarded `MIN_REQUEST_INTERVAL_MS` (1 s) throttle, and retry on
429/5xx up to `MAX_RETRIES` (3) with exponential backoff from `INITIAL_RETRY_DELAY_MS` (2 s). No result
cache. It returns a typed `Shazam.Outcome` (`Found` / `NoMatch` / `Failed`); Zemer uses only the
result's `title` and `artist`.

## Stage 3 - resolve to a whitelisted song

`RecognitionResolver.resolveWhitelisted` - see [02-whitelist-guarantee.md](02-whitelist-guarantee.md).
A blank query is `NoMatch`; a failed YouTube search is `Error`.

## Stage 4 - presentation

```
Idle → (no permission ⇒ PermissionRequired)
     → Listening → Identifying → Searching
     → Result(SongItem) | NoMatch | Error
```

`Result` is the **only** state that carries content, and it is always a whitelist-confirmed
`SongItem`. Unexpected exceptions are logged, sent to `reportException`, and shown as `Error`;
`CancellationException` is rethrown. See [03-entry-points-and-ui.md](03-entry-points-and-ui.md).
