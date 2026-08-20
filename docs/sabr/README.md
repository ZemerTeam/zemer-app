# SABR playback (`playback/sabr/`)

SABR (**S**erver-side **A**daptive **B**it**R**ate, over the **UMP** framing) is an alternative media
transport to progressive stream URLs. It is **experimental and OFF by default**, fully isolated behind a
Stream Sources toggle; with it off the DIRECT playback path is byte-for-byte unchanged.

This is the deep guide. The one-paragraph version lives in `AGENTS.md` (sec SABR playback); the field numbers
and the ground-truth behavior live in the harness (`tests/sabr-stream.mjs`, `tests/sabr-clients.mjs`),
which is where any change must be proven against the live CDN first.

---

## 1. Why SABR exists

YouTube is migrating clients **off progressive delivery**. For a migrated client the `/player`
response's `adaptiveFormats[].url` is now only a **~1-MiB preview stub**: it serves the first ~1 MiB and
then `403`s every further range (verified against the live CDN - see the streaming investigation). The
real, whole media lives behind a single field, `streamingData.serverAbrStreamingUrl`, which only speaks
SABR:

> POST a binary `VideoPlaybackAbrRequest` -> receive a **UMP**-framed body -> decode it -> advance the
> player time + buffered ranges -> POST again -> repeat until every segment has arrived.

Today the app streams its clients progressively and that still works. SABR is the **fallback for when
progressive gets walled for those clients too** - the industry trend (see yt-dlp #12482). It is landed
now, isolated and off, so the transport is ready and validated before it is ever needed.

---

## 2. What actually works (validated, live)

Validated whole-song over SABR with **the pot the app can mint** (the WebView BotGuard pot), across
multiple videos, by `tests/sabr-clients.mjs`:

| Client | SABR result | In the app roster? |
|---|---|---|
| **WEB_REMIX** (the app's main client) | yes whole song, every video | yes (tried first) |
| **VISIONOS** / VISIONOS_0_1 | yes whole song, pot-less direct client | yes |
| **TVHTML5_SIMPLY** | yes whole song, no sign-in | yes |
| **MWEB** | partial whole song on most content, context-challenge stall on some | yes (last) |
| IOS / IPADOS / WEB_CREATOR / ANDROID_VR | no throttled to ~60s on most content (whole only on rare unrestricted videos) | **no** |
| WEB (desktop) | no needs browser-grade attestation | no |

Two hard-won facts behind that table:

- **The web family's `serverAbrStreamingUrl` is CIPHERED** - it carries `n`/`sig` params, unlike the
  direct clients' URLs. It must be **n-transformed** (the same cipher the app runs for web progressive
  URLs) before POSTing, and the **videoId-bound pot appended** as `&pot=`. This n-transform was the single
  missing piece that unlocked WEB_REMIX/TVHTML5_SIMPLY/MWEB; without it they `403`.
- **The ~60s cap on IOS/IPADOS/WEB_CREATOR/ANDROID_VR is server-side, keyed on client identity, and NOT a
  bug in this code** - the *identical* loop and pot drain VISIONOS/WEB_REMIX whole. YouTube throttles the
  sensitive clients to ~60s on most (esp. premium/label) content; those clients require their native
  attestation (DroidGuard / iOS BotGuard) that an Android WebView cannot produce. They are excluded from
  the roster.

---

## 3. The protocol

### 3.1 The request - `VideoPlaybackAbrRequest`

Built by `SabrMessages.abrRequest`. Top-level fields (transcribed from the reverse-engineered protos,
pinned to the Node reference):

| # | field | notes |
|---|---|---|
| 1 | `clientAbrState` | `{ playerTimeMs=28, enabledTrackTypesBitfield=40 }` - 40=1 selects audio only |
| 2 | `selectedFormatId` | follow-up requests only (once a format is locked) |
| 3 | `bufferedRange` (repeated) | what we already have: `{ formatId, startTimeMs, durationMs, startSegmentIndex, endSegmentIndex }` |
| 4 | `playerTimeMs` | follow-up only |
| 5 | `videoPlaybackUstreamerConfig` | the base64 blob from `playerConfig.mediaCommonConfig.mediaUstreamerRequestConfig` |
| 16 | `preferredAudioFormatId` | `{ itag, lastModified }` |
| 19 | `streamerContext` | `{ clientInfo=1, poToken=2, playbackCookie=3, sabrContexts=5 }` |

`clientInfo` (inside `streamerContext`) carries `{ deviceMake=12, deviceModel=13, clientName=16 (the
InnerTube client id), clientVersion=17, osName=18, osVersion=19, androidSdkVersion=64 }`.

### 3.2 The response - UMP frames

A UMP body is a sequence of parts, each = `umpVarint(partType) + umpVarint(partSize) + payload`. **The UMP
varint is NOT the protobuf varint** - the leading byte's high bits encode the total width (like UTF-8);
`SabrUmp.readVarint` implements it. Parts we act on:

| type | name | what we do |
|---|---|---|
| 20 | `MEDIA_HEADER` | `{ header_id=1, itag=3, start_range=6, is_init_seg=8, sequence_number=9, content_length=14, time_range=15 }` - maps a `header_id` to a segment's byte offset + time span |
| 21 | `MEDIA` | `varint(header_id) + media bytes` - the actual audio, written at the header's offset |
| 42 | `FORMAT_INITIALIZATION_METADATA` | `end_segment_number=4` - the total segment count (completion test) |
| 35 | `NEXT_REQUEST_POLICY` | `playback_cookie=7` - echoed back in the next request |
| 43 | `SABR_REDIRECT` | a new url (field 1) to continue against |
| 57 | `SABR_CONTEXT_UPDATE` | `{ type=1, value=3 }` - echoed back as a `streamerContext.sabrContext { type, value }` |
| 58 | `STREAM_PROTECTION_STATUS` | `status` (1=OK, 2=pending, 3=attestation-required) - the attestation signal, logged |
| 44 | `SABR_ERROR` | the server rejected the request |

### 3.3 The continuation loop

`SabrSession.loop()` - a faithful port of `tests/sabr-stream.mjs`:

1. POST the request (cold start: no `bufferedRange`, no `selectedFormatId`).
2. Parse the UMP response; collect `MEDIA_HEADER`s, `end_segment_number`, the playback cookie, any
   `SABR_CONTEXT_UPDATE`s, a redirect, `SABR_ERROR`, `STREAM_PROTECTION_STATUS`.
3. For each **new** segment (init once, each `sequence_number` once - resends skipped), write its `MEDIA`
   bytes at its **absolute `start_range`** (see sec 4).
4. Advance `playerTimeMs` = the buffered end time; set `bufferedRange` = `[0, bufEnd]`, `endSegmentIndex` =
   the last sequence. Echo the cookie + context updates. POST again.
5. Stop when `lastSeq >= end_segment_number`.

---

## 4. Reassembly correctness (the crash that was)

**Segments are written at their absolute byte offset, never sequentially appended.** `SabrSession` tracks a
per-`header_id` write cursor starting at `start_range`; `SabrBuffer.writeAt(offset, ...)` copies into a
fixed-size buffer and maintains a **contiguous-from-0 watermark** that `read()` blocks on. `available()` is
that watermark; `SabrDataSource.read()` serves bytes below it.

Why it matters: the first implementation appended segment bytes in arrival order, assuming perfect
ordering. That corrupted the webm/opus container, so ExoPlayer's extractor reported a **garbage
duration/position**, which overflowed media3's `Util.percentInt` inside `getBufferedPercentage` and
**crashed the app on every launch** (the media session polls it on restore - a poisoned persisted track
crash-looped with no recovery short of clearing data). The positional write makes reassembly **byte-exact
regardless of arrival order** - `SabrBufferTest` pins out-of-order writes (segment 3 before 2 before init)
reassembling to the exact bytes, gaps holding the watermark back, and over-length writes ignored.

### 4.1 The companion hardening - `CastAwarePlayer.getBufferedPercentage`

Overridden to compute the percentage in **double math, clamped 0..100**, guarding `TIME_UNSET`/zero/NaN.
media3's default throws `IllegalArgumentException: Out of range` on a pathological duration/position, and
the session polls it on every player-info change **including restore**. This makes it *structurally
impossible* for a buffered-percentage read to crash the media session - for SABR **or any current/future
source**. It is a general robustness fix, not SABR-specific, and must not regress.

---

## 5. The engine (`playback/sabr/`)

Pure, isolated, JVM-tested where possible:

| File | Responsibility | Test |
|---|---|---|
| `SabrProto.kt` | protobuf wire writer/reader (varint, length-delimited, fixed32) | `SabrProtoTest` |
| `SabrUmp.kt` | UMP frame parser (the custom leading-bits varint + part types) | `SabrUmpTest` |
| `SabrMessages.kt` | request builder + response part parsers; field numbers | `SabrMessagesTest` |
| `SabrBuffer.kt` | DISK-backed positional reassembly, region coverage, demand pacing | `SabrBufferTest` |
| `SabrSpool.kt` | the spool dir + the persistent replay cache (LRU, meta sidecars) | `SabrStreamLifecycleTest` |
| `SabrSession.kt` | the continuation state machine (seek start, pacing) -> fills the buffer | (network; proven by the harness) |
| `SabrDataSource.kt` | the ExoPlayer `DataSource` + `SabrAudioStream` + the `sabr://<mediaId>` registry | `SabrStreamLifecycleTest` |
| `SabrStreamResolver.kt` | builds a `SabrConfig`; the SABR OkHttp client; the audio download | - |
| `SabrPlayerResolver.kt` | roster resolve (`/player` + pot + cipher), resolve cache, stall fallback | (network) |

**`SabrBuffer` is disk-backed** (a spool file under `cacheDir/sabr-spool/`, managed by `SabrSpool`):
a multi-hour podcast episode or a 2160p video track never risks an OutOfMemoryError, and reads serve
any **covered region**, not just a prefix — a seek-restarted session fills a tail while the head stays
a gap. `SabrConfig` is everything one session needs; `SabrStreamRegistry` passes it from the resolver
to the DataSource keyed by media id (the DataSpec carries only a `sabr://<id>` uri) AND owns the id's
live `SabrAudioStream` — the same explicit lifecycle as the video registry (sec 9.4): media3
closes/reopens the DataSource on every seek outside its sample buffer, so the stream must survive
close (`close()` drops only the source's refs) or every seek re-resolved (/player + poToken) and
re-drained the whole track from byte 0. Replace/evict (a small cap keeps current + gapless-next)
/`clear` (service destroy) are the explicit ends of a stream, and `destroy()` **marks the buffer
errored** so a parked reader is always woken, never left hanging.

**The streams ORCHESTRATE sessions around reader demand** (`SabrAudioStream` / `SabrVideoStream`, one
shared mechanism): a covered read serves from the spool; a read just past the drain frontier waits for
the catch-up; a far/backward read **seek-restarts** the session at the estimated `playerTimeMs` for
that byte — proven live (`tests/sabr-seek.mjs`, dual-track via `sabr-video.mjs START_S`): the server
serves the segment containing T with absolute offsets, the range echo anchors at OUR first segment,
and a seeked session gets NO `end_segment_number` (completion is judged by byte coverage). The
estimate converges with a growing back-margin when it lands past the target. Playback sessions are
**demand-paced** (`SabrBuffer.awaitDemand`): the drain follows consumption — a skipped track stops the
spend — and the server session survives the idle gaps (proven live with 90-second pauses); downloads
drain at full speed.

Engine-level honesty rules (all JVM-pinned): the `MEDIA` part's header-id prefix is read with the
**UMP leading-bits varint** (`SabrUmp.readVarint` — the harness `umpVar`; the protobuf LEB128 agrees
only below 128, so it mis-routed/mis-sized past that), an **incomplete drain marks ERROR, never
complete** — the buffer still serves everything it reassembled first, so playback reaches the stall
point and then surfaces a real player error instead of a silent truncation — and `SabrBuffer` refuses
an out-of-range contentLength at construction (`lengthValid`) — the old degenerate zero-length buffer
silently dropped every write while the session drained the whole stream anyway.

---

## 6. The resolver + client roster

`SabrPlayerResolver.resolve(videoId, enabled)` tries the **enabled** clients in priority order and the
first that exposes SABR inputs wins:

```
WEB_REMIX (web)  ->  VISIONOS (direct)  ->  TVHTML5_SIMPLY (web)  ->  MWEB (web)
```

Per client:
- **web** (WEB_REMIX / TVHTML5_SIMPLY / MWEB): `/player` sent with the signature timestamp + the session
  web pot; the SABR url is **n-transformed** and the **videoId pot appended**.
- **direct** (VISIONOS): `/player` with no pot/sts; the SABR url is used **as-is** (identity transform, no
  url-pot).
- The `streamerContext.poToken` is the **session (visitorData-bound)** token for all of them
  (`PoTokenResult.playerRequestPoToken`); the url-pot is the **videoId-bound** token
  (`streamingDataPoToken`).

**Pot decoding is tolerant** (`SabrStreamResolver.decodeBase64`): the app's `PoTokenGenerator` emits
**standard** base64 (`+`/`/`), bgutils emits url-safe (`-`/`_`); both normalize to standard, pad, decode. A
strict `URL_SAFE` decode threw `bad base-64` on the app's tokens - the first on-device failure.

Each client is individually toggleable (`StreamSabr{WebRemix,VisionOS,TVHTML5,MWEB}Key`, all default on),
exposed as the **"SABR clients"** sub-list in Stream Sources. Only clients validated to deliver a whole
song are in the roster - the ~60s-capped ones are deliberately absent.

---

## 7. Integration (`MusicService`, the RELAY pattern)

- A per-open `sabrDataSourceFactory` - a `ResolvingDataSource` whose callback `runBlocking`s
  `SabrPlayerResolver.resolve` and returns a `sabr://<id>` uri - is selected in the dispatcher **only when
  `StreamSabrKey` is on**. Otherwise the DIRECT factory is used verbatim. A downloaded file still plays
  from disk (same as DIRECT/RELAY) — the SABR upstream is wrapped in a **`DefaultDataSource.Factory`**
  (the RELAY pattern) so the resolved `content://`/`file://` uri routes to the platform sources;
  `SabrDataSource` itself accepts nothing but `sabr://`, so an unwrapped factory made every downloaded
  track a player error while SABR mode was on.
- A **live registry stream is reused as-is** (a seek's close→reopen, a repeat-one replay): the resolver
  callback returns the `sabr://` uri straight away — no second /player + poToken round-trip, no
  watch-time/telemetry re-seed, no duplicate `FormatEntity` upsert; the reopen just re-reads the
  accumulating spool. A FAILED stream is torn down there (registry + resolve cache, and a bad spool
  replay evicts its cache entry) so a fresh resolve never replays a dead config.
- **The persistent spool REPLAY CACHE** (`SabrSpool`, DIRECT's playerCache parity): a drain that
  completed from byte 0 is promoted on stream destroy (`.done` + a meta sidecar, 512 MiB LRU) and a
  later play of the same id — hours or sessions later — is served entirely from disk: zero network.
  Stats parity holds on every path: a fresh resolve seeds the watch-time reporter live; a spool replay
  rides the reporter's one metadata fetch (exactly DIRECT's cached-play behavior); an OFFLINE replay
  lands in the reporter's offline branch and is re-pushed on reconnect by the deferred stats queue.
- **The audio resolve cache** (`SabrPlayerResolver`, 45 min TTL — DIRECT's songUrlCache parity): a
  replay of a not-yet-cached id within the TTL skips the /player + poToken round-trip. Playback-only
  (downloads must not inherit a playback config, whose cpn stamps the listen's nonce); a playback
  error invalidates.
- **Stall fallback**: a client whose session drained INCOMPLETE for an id is recorded
  (`SabrPlayerResolver.recordStall`, fed by the sessions' `onIncomplete`) and deprioritized on the
  next resolve of that id — a /player that succeeds gives the roster loop no failure signal, so
  without this a stalling client truncated the same track identically forever. Shared by the audio
  and video resolvers.
- On a successful (fresh) resolve it **persists a `FormatEntity`** (overwriting any stale DIRECT one) with
  `streamClient = "WEB_REMIX (SABR)"` (etc.) + the format's itag/mime/codecs/bitrate/contentLength **and
  the /player response's `loudnessDb`** (DIRECT parity — audio normalization works under SABR, and a SABR
  play never nulls the loudness a DIRECT play stored), so the
  **song-details sheet** (`ShowMediaInfo`) shows the SABR client + format exactly like a normal play.
  `ShowMediaInfo` strips the ` (SABR)` suffix before the web-client check, so a web SABR client still
  resolves its player hash + cipher date (the cipher n-transform ran); **VISIONOS (SABR)** stays N/A -
  correct, it runs no cipher.
- innertube exposes the inputs **additively**: `StreamingData.serverAbrStreamingUrl` +
  `PlayerConfig.mediaCommonConfig.mediaUstreamerRequestConfig.videoPlaybackUstreamerConfig` (defaulted
  null, ignored by clients that omit them).

### 7.2 Full DIRECT parity (not one feature missing)

SABR reaches googlevideo exactly like DIRECT (serverAbrStreamingUrl IS a googlevideo host), so every
DIRECT feature applies and is wired:

- **Stats, views, watch time.** The SABR resolve seeds the watch-time reporter from THIS /player
  response (`watchTimeReporter.onTrackingResolved` - no second round-trip, truthful `fmt`) and every SABR
  media POST is stamped with the SAME `cpn` the stats-beacon session uses (`MusicService.sabrCpnFor` ->
  `SabrConfig.cpn` / `SabrVideoConfig.cpn`, applied in `prepared()`). That is DIRECT's `stampCpn` CDN
  correlation - so a SABR listen credits a real VIEW and real WATCH TIME on the YouTube video, same as
  DIRECT. Proven CDN-safe by the harness `CPN=` knob (a cpn-stamped whole-song and whole-video drain both
  still pass byte-exact). Telemetry attributes the SABR client (+ the player hash for web SABR clients,
  whose cipher n-transform ran) via `Tracker.onStreamResolved`.
- **Audio-quality preference.** `SabrPlayerResolver.pickAudio` mirrors YTPlayerUtils: bitrate weighted by
  the `AudioQuality` setting (AUTO follows the metered state), same opus/webm streaming bonus. JVM-tested
  (`SabrAudioPickTest`).
- **Instant switching + prefetch.** ONE /player resolution serves EVERY video rung (the SABR request pins
  the itag per REQUEST against the same serverAbrStreamingUrl), cached (`SabrVideoResolver` resolve cache,
  itag -> wire format). A quality switch and a prefetched entry skip the network - DIRECT's
  `videoRungUrls` / `prefetchVideoRendition` contract. `MusicService.prefetchVideoRendition` warms the SABR
  cache (never the DIRECT /player, which fails when DIRECT clients are off) while the Song/Video pill shows.
- **Metered AUTO cap.** The AUTO video pick is capped at 720p AND the metered-aware bitrate
  (`VideoRendition.defaultMaxBitrateKbps`), exactly like DIRECT's automatic pick; an EXPLICIT quality
  label is never capped (honoured on every connection). Same for downloads.
- **Error handling.** A video-mode error invalidates the SABR resolve cache (`SabrVideoResolver.invalidate`)
  alongside the DIRECT stream caches, so a re-entry re-resolves fresh; a failed audio stream tears down
  its registry entry + resolve cache on the next open.
- **Replays.** The spool replay cache (whole plays served from disk across sessions — playerCache
  parity) + the 45 min audio resolve cache (songUrlCache parity). See sec 7's bullets.
- **Data usage.** Demand pacing keeps the drain within a bounded window of what playback consumes —
  a skipped track stops the spend, like DIRECT's ranged chunking.
- **Seeking.** Covered-spool serves (backward free) + seek-restart at the estimated playerTimeMs for
  uncovered targets — no more full-drain waits; a resumed long episode starts near its resume point.
- **Stats on every path, online AND offline — accepted at the wire, live.** A fresh resolve seeds the
  reporter live (views + watch time, cpn-stamped POSTs); a spool replay rides the reporter's
  metadata-fetch fallback (DIRECT cached-play behavior); an offline replay is captured by the
  reporter's offline branch and re-pushed on reconnect by the deferred stats queue. The reporter
  gates only RELAY and cast — never SABR. `tests/sabr-watchtime.mjs` proves the full flow end to end
  against live YouTube: ONE cpn stamped on every media POST of a whole WEB_REMIX SABR drain, then the
  SAME cpn's playback + scheduled watchtime + final=1 beacons — every ping HTTP 204 (the exact
  acceptance bar the DIRECT watchtime replica set).

---

## 7.1 Downloads (the SABR download path)

A migrated client's progressive download URL is walled at ~1 MiB exactly like its stream URL, so when
SABR mode is on, **downloads must run over SABR too** - otherwise a device that can only stream via SABR
could never save a track. `MediaStoreDownloadManager.performDownload` mirrors the RELAY branch:

- **SABR mode is derived like `relayMode`**, from the same prefs the player reads
  (`StreamSabrKey` + the four client toggles), split into `sabrAudioMode` (one track) and
  `sabrVideoMode` (dual-track + on-device remux — sec 9.4).
- When SABR, `playbackData` is **null** (no `/player`-for-download round-trip, same as relay); the audio
  download runs through **`SabrStreamResolver.download(id, enabled, file, onProgress)`**,
  which resolves over the roster (**without registering** — a download of the currently-playing id must
  never clobber its live playback stream), runs a `SabrSession` to completion, and writes the
  **byte-exact reassembled** audio. It returns null (-> the attempt throws and retries) on an **incomplete**
  drain, so a truncated/capped stream is **never saved as a finished download**.
- **Cancel + progress are wired like DIRECT**: both SABR drains run under
  `kotlinx.coroutines.runInterruptible`, so cancelling the download Job **interrupts the in-flight OkHttp
  call immediately** (the old plain blocking `run()` drained the whole file before the cancellation
  landed), and the sessions report per-response byte counts through a throttled
  `updateDownloadState` bridge (`sabrProgressReporter`) so the ring moves instead of freezing.
- The null-`playbackData` tail is shared with relay verbatim: the real container is **sniffed**
  (`sniffAudioExtension` - WebM/Opus labelled `.opus`, MP4 `.m4a`, both MediaStore-accepted), the
  **duration** comes from the saved file (`durationSecFromFile`), and `isVideo` is forced **false** for an
  audio download (true for a muxed `sabrVideoMode` file).
- The DIRECT and RELAY download paths are byte-for-byte unchanged; every SABR branch is a strict no-op
  while SABR mode is off.

---

## 8. The harness - the proof + validator

Both live in `tests/` (Node >=20, deps vendored; needs `innertube_cookie.txt` at the repo root):

- **`node tests/sabr-stream.mjs [videoId] [VISIONOS|ANDROID_VR|IOS|IPADOS]`** - streams a whole song over
  SABR and proves byte-exact reassembly by **full distinct-segment coverage** (init + every segment
  1..N summing exactly to `contentLength` - a resend can't inflate a distinct-segment set). This is the
  reference the Kotlin engine is a port of.
- **`node tests/sabr-seek.mjs [videoId] [seekSeconds] [client]`** - the SEEK proof: a session
  cold-started at `playerTimeMs = T` serves the segment containing T (absolute startRange) and the
  tail drains whole + byte-contiguous to contentLength. Proven on VISIONOS (30s/100s/200s/310s) AND
  WEB_REMIX (the ciphered web path). `PACE_PAUSE_S`/`PACE_EVERY` add long idle gaps between POSTs —
  the DEMAND-PACING proof (the session survived three 90s pauses and kept serving). The dual-track
  variant is `START_S=<s> node tests/sabr-video.mjs` (both tracks land at T; NO end_segment_number on
  a seeked session — completion is byte coverage).
- **`node tests/sabr-watchtime.mjs [videoId]`** - the STATS proof: a whole WEB_REMIX SABR drain with
  one cpn stamped on every media POST, then the SAME cpn's playback/watchtime/final beacons — every
  ping must 204 (a SABR-transported listen is accepted by the stats ingestion exactly like DIRECT).
- **`node tests/sabr-clients.mjs [videoId]`** - runs the whole client roster and reports, per client,
  whether it delivers a whole song over SABR with the app's pot. This produced the sec 2 table and the
  n-transform / context-update discoveries.

**Streaming is the danger zone.** Any change to the SABR path is proven against the live CDN in the
harness first, then on-device. When the app's client constants / pot / cipher change, keep the harness
mirrors (`tests/clients.mjs`, etc.) in step, exactly as for the DIRECT harness.

---

## 9. Video over SABR (dual-track, quality-pinnable)

SABR is not audio-only. One SABR stream can carry **video + audio interleaved**, and - the key finding -
the **exact video itag is pinnable**, so the app's progressive-style quality ladder carries straight over
to SABR. Proven end-to-end in `tests/sabr-video.mjs` (single client) and `tests/sabr-video-clients.mjs`
(whole roster), live against the CDN.

### 9.1 The dual-track request

A video listen requests **two** adaptive formats in one SABR session - a **video-only** format and an
**audio** format - and the server interleaves both tracks' segments in each UMP response. The differences
from the audio-only request (sec 3.1):

- `clientAbrState.enabledTrackTypesBitfield` = **0** (video + audio), not 1 (audio only).
- **`preferredAudioFormatId` = field 16** AND **`preferredVideoFormatId` = field 17** - both carry a
  `FormatId { itag, lastModified }`.
- `selected_format_ids` (field 2, repeated) echoes **both** locked formats once streaming.
- `bufferedRange` (field 3) is sent **per track** (each format has its own segment sequence + buffered
  end time); `playerTimeMs` advances to the **minimum** buffered end across the two tracks (you can't play
  past the least-buffered track).

Each response part is routed to its track by the **`MediaHeader.itag` (field 3)**: a `MEDIA` part names a
`header_id`, whose `MEDIA_HEADER` carries the itag, so video and audio bytes land in separate reassembly
buffers. Reassembly is positional (sec 4) **per track**, so each stream is byte-exact independent of interleave
order.

### 9.2 Quality is pinnable - field 17 is the lever (the full story)

The **critical** finding, because it decides whether the app can offer a video quality ladder over SABR at
all. Initially the server appeared to ignore any requested video format and serve its own pick (av01 720p,
itag 398) regardless of what we asked - which looked like uncontrollable server-side ABR. That was a
**wrong field number**, not a real limitation:

- Sweeping `clientAbrState` sub-fields with a max-height value (12/16/17/18/21/23/37/38/46/55/60): **no
  effect**. `selected_format_ids` (field 2) from the first request: **no effect**.
- Sweeping the **top-level preferred-video field number** with a forced target itag: **only field 17**
  made the server obey. (`preferredAudioFormatId` is field 16 - so video sits right beside it, which is
  why 15 - the earlier guess - silently missed.)
- Verified across the ladder on VISIONOS: request itag **133/134/135/136/137** -> the server serves
  **exactly** that itag (240p/360p/480p/720p/1080p), each **whole and byte-exact**. So the app can pin
  **avc1 720p (broadly decodable)** instead of the server's av01 default, and map its existing quality
  targets to SABR itags directly.

The lesson matches the DIRECT resolver's: **never reason from convention against this CDN - prove the field
against live bytes.** The wrong-guess -> "looks like server-ABR" -> field-sweep -> field-17 path is preserved
in the harness header comment so the reasoning is not lost.

### 9.3 What works - the roster (live, two videos, pinned avc1 <=720p)

`node tests/sabr-video-clients.mjs <videoId> 720` pins itag 136 on every client (apples-to-apples) and
drains **both** tracks. Reliable = whole video **and** whole audio on **both** `dQw4w9WgXcQ` and
`JTF9fLJvniI`:

| Client | Video+Audio over SABR | In the app's SABR roster? |
|---|---|---|
| **WEB_REMIX** (main client) | yes whole, both videos | yes |
| **TVHTML5_SIMPLY** | yes whole, both videos | yes |
| **VISIONOS** / VISIONOS_0_1 | yes whole, both videos | yes |
| **MWEB** | yes whole, both videos | yes (last) |
| WEB_CREATOR / IOS / IPADOS | partial whole on unrestricted, ~60s cap on some | no |
| ANDROID_VR | no ~60s cap | no |
| WEB (desktop) / TVHTML5 7.x | no no SABR inputs / unplayable | no |

This is the **same reliable set as SABR audio** (sec 2) - video adds no new usable/unusable clients, so the
app's existing SABR roster (WEB_REMIX -> VISIONOS -> TVHTML5_SIMPLY -> MWEB) covers video unchanged. The cap
on the sensitive clients is the same server-side identity throttle as audio, content-dependent.

### 9.4 App integration (RELAY/SABR isolation pattern)

Video-over-SABR reuses the audio engine primitives (`SabrBuffer`/`SabrProto`/`SabrUmp`/`SabrMessages`)
and adds an isolated dual-track layer in `playback/sabr/`:

- **`SabrMessages.abrRequestVideo`** - the dual-track request (bitfield 0, `preferredAudioFormatId`=16 +
  `preferredVideoFormatId`=17, per-track ranges). `MediaHeader.itag` routes each interleaved MEDIA.
- **`SabrVideoSession`** - one loop draining video + audio into two `SabrBuffer`s, advancing to the
  least-buffered track (a faithful port of `tests/sabr-video.mjs`).
- **`SabrVideoStream` / `SabrVideoRegistry` / `SabrVideoDataSource`** - ONE shared session feeding two
  ExoPlayer `DataSource`s (`sabrvideo://<id>` + `sabraudio://<id>`), surfaced as a **`MergingMediaSource`**
  (the same merge shape the DIRECT adaptive rungs use). **Stream lifetime is explicit, never tied to
  DataSource open/close** (hard-won on-device): entering video mode seeks mid-track, and once the period
  prepares media3 CANCELS the in-flight loads and re-opens both children at the seek offset - so there is
  always a close->reopen gap with zero DataSources open while playback continues. The first, ref-counted
  design (cancel + unregister at zero refs) killed the session and wiped the registry entry inside exactly
  that gap, and the reopen failed with "no session". Now the session starts on the first attach and is
  destroyed only by the registry - on `remove` (VideoModeController's `clearState`, the one chokepoint
  every video-mode exit funnels through) or on `put` replacing it (a committed new resolve) - so reopens
  just re-read the accumulating buffers. `destroy()` **marks both buffers errored** so readers parked in
  `SabrBuffer.read` are always woken (a cancelled session's exits deliberately skip marking) — a destroy
  with no accompanying media-item change otherwise left ExoPlayer's loading thread waiting forever.
- **`SabrVideoResolver`** - dual-format resolve over the same client roster, pinning the exact video itag
  for the quality target via field 17 (best audio too), cipher n-transform for web clients. Reuses the
  DIRECT `VideoQualityLogic.rungs` ladder (minus progressive + undecodable rungs + rungs whose
  contentLength the buffer can't hold) and returns it + the pinned rung, so the switcher offers the same
  rungs as the DIRECT path. **The resolve returns a READY, unregistered stream**: `VideoModeController`
  installs it in the registry only at the swap COMMIT on the main thread, after the `stillOurs` guard —
  registering from the resolve (IO) thread destroyed the CURRENTLY-PLAYING stream before the guard could
  veto, and an abandoned resolve (queue moved / toggled off mid-resolve) then parked playback on dead
  buffers; now the abandoned branch destroys only the new stream. The swap also captures
  `position`/`playWhenReady` **at commit time** (DIRECT's `swapToVideoKey` discipline) — values captured
  before the seconds-long resolve rewound playback and force-resumed over a user pause.

**Wiring** (all gated behind `StreamSabrKey`, RELAY takes priority, DIRECT byte-for-byte unchanged):

- `MusicService.createMediaSourceFactory` gains a branch: a `sabrvideo://` URI builds the
  `MergingMediaSource` from two isolated `SabrVideoDataSourceFactory` children (never the DIRECT/relay
  factory). Detected by URI scheme, which nothing but `SabrVideoResolver` produces.
- `VideoModeController.enterVideoModeSabr` resolves the dual-track session **asynchronously** (network),
  then swaps to an item whose **CACHE KEY is `video:<id>:q<itag>`** (so the existing exit / own-swap /
  listen classification machinery recognises it AND each rung is a distinct item that forces a re-prepare)
  but whose **URI is `sabrvideo://<id>`** (which routes it to the merge).
- **Live quality switcher** - SABR pins an exact itag (field 17), so unlike RELAY's fixed rendition the
  in-player picker IS offered. `SabrVideoResolver.resolve` returns the **same ladder the DIRECT switcher
  renders** (`VideoQualityLogic.rungs`, minus progressive since SABR video is dual-track, minus rungs the
  device can't decode) plus the pinned rung; the controller publishes it to `_videoQualities`. A pick
  (`setVideoQuality`) **re-resolves** the dual-track session at the new target and swaps under a fresh
  `:q<itag>` cache key - there is no DIRECT-style cache re-key because each SABR rung is a different
  server-pinned stream. AUTO caps at 720p. The rebuffer guard (`downgradeForStall`) likewise re-resolves
  one rung down on repeated stalls. Downloads take the same quality target label.
- `MusicService.isSabrPlaybackMode()` mirrors `StreamSabrKey` synchronously (a `@Volatile`, collector-fed),
  so the user's video toggle reads it on the main thread without a blocking DataStore read.

**Downloads** are wired too: a SABR video download runs the dual-track session to completion
(`SabrVideoResolver.download` -> two byte-exact temp files) and remuxes on-device
(`VideoMuxer.mux`, mp4 for avc1 / webm for vp9), exactly like a DIRECT adaptive video download - the
saved file plays as an ordinary video. DIRECT's download gates apply (`SabrVideoRungPickTest`): the rung
pick is restricted to **remux-capable** rungs (`VideoQualityLogic.isDownloadableRung` — no av01, and
webm/vp9 only on API 29+ where the framework muxer accepts Opus-in-WebM), and the **audio partner is
container-matched** to the chosen rung (mp4/avc -> AAC, webm/vp9 -> Opus) so the mux inputs always
agree — an ungated pick drained hundreds of MB into a deterministic INCOMPATIBLE mux.
`MediaStoreDownloadManager` splits SABR into `sabrAudioMode`
(one track) and `sabrVideoMode` (dual-track + remux); an incomplete SABR drain throws (retryable), and
the mux-result handling mirrors the DIRECT adaptive path (INCOMPATIBLE clears the requested quality so a
retry falls back; TRANSIENT preserves it). Cancel + progress ride the sec 7.1 wiring (interruptible
drain, throttled progress bridge).

**DataSource lifecycle note:** the SABR `DataSource`s (audio + the two video children) fire
`transferEnded()` only after `transferStarted()` ran - media3's `DefaultBandwidthMeter` NPEs on a null
`dataSpec` otherwise, and `closeQuietly` swallows only `IOException`, so an unguarded `transferEnded()`
surfaced as a "Source error" when a `MergingMediaSource` tore down a sibling mid-open (found on-device).

On-device soak of SABR video playback + downloads is the remaining validation gate before promotion.

---

## 10. Known limitations / future work

- **Seeking** is served from the covered spool when possible; an uncovered seek RESTARTS the session at
  the estimated `playerTimeMs` (sec 5) — a resumed 2-hour episode starts near its resume point instead
  of draining 2 hours of bytes first. The byte->time estimate is linear (approxDurationMs), so a highly
  VBR track may need a convergence restart or two (bounded, then errors loudly).
- **MWEB** is inconsistent (context-challenge stall on some content) - it sits last, and the stall
  fallback (sec 7) deprioritizes it per-id after an incomplete drain.
- **Casting** cannot ride SABR: the cast receiver fetches its own URL and cannot speak UMP — a cast
  session still needs a progressive URL (the DIRECT pipeline).
- **A WebView poToken is required** for every fresh resolve (the streamerContext pot) — there is no
  pot-less SABR client the way DIRECT has ANDROID_VR.
- **On-device soak** (more clients/content, long tracks, seeks, network transitions) is the remaining
  gate before SABR is promoted from experimental. It is fully isolated and cannot affect the DIRECT
  path while off.
