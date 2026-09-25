# SABR playback (`playback/sabr/`)

SABR (**S**erver-side **A**daptive **B**it**R**ate, over **UMP** framing) is an alternative media
transport to progressive stream URLs. It is **experimental and OFF by default** (`StreamSabrKey`, Stream
Sources → Experimental); with it off the DIRECT playback path is byte-for-byte unchanged.

The agent rules are summarised in `AGENTS.md` §SABR playback. The field numbers and ground-truth
behavior live in the harness (`tests/sabr-*.mjs`), where any change must be proven against the live CDN
first, then on-device.

---

## 1. Why SABR exists

For a client YouTube has migrated off progressive delivery, the `/player` response's
`adaptiveFormats[].url` is only a ~1-MiB preview stub (it 403s past the first ~1 MiB); the whole media
lives behind `streamingData.serverAbrStreamingUrl`, which only speaks SABR:

> POST a binary `VideoPlaybackAbrRequest` -> receive a **UMP**-framed body -> decode it -> advance the
> player time + buffered ranges -> POST again -> repeat until every segment has arrived.

The app's clients still stream progressively; SABR is the ready, validated fallback for when progressive
is walled for them too.

---

## 2. Which clients work

Only clients `tests/sabr-clients.mjs` validates to deliver a **whole song over SABR with the pot the app
can mint** (the WebView BotGuard pot) are in the roster:

| Client | Whole song over SABR | In the app roster? |
|---|---|---|
| **WEB_REMIX** (main client) | yes | yes (tried first) |
| **VISIONOS** | yes (direct client, no url-pot) | yes |
| VISIONOS_0_1 | yes (harness) | no (DIRECT fallback only) |
| **TVHTML5_SIMPLY** | yes | yes |
| IOS / IPADOS / WEB_CREATOR / ANDROID_VR | no - server-side throttle to ~60s on most content (needs native attestation a WebView can't produce) | **no** |
| WEB (desktop) | no - needs browser-grade attestation | no |

The ~60s cap is keyed on client identity, not a bug in this code (the identical loop drains the roster
clients whole). **The web family's `serverAbrStreamingUrl` is CIPHERED**: it must be **n-transformed**
and have the **videoId-bound pot appended** (`&pot=`) before POSTing, or it 403s. Video adds no new
usable client (sec 9.3). MWEB was removed from the roster (and the DIRECT chain) — attestation-walled on
gated content on both transports; see `tests/MWEB-INVESTIGATION.md`.

---

## 3. The protocol

### 3.1 The request - `VideoPlaybackAbrRequest` (`SabrMessages.abrRequest`)

| # | field | notes |
|---|---|---|
| 1 | `clientAbrState` | `{ playerTimeMs=28, enabledTrackTypesBitfield=40 }` - bitfield value 1 = audio only |
| 2 | `selectedFormatId` | follow-up requests only (once a format is locked) |
| 3 | `bufferedRange` | `{ formatId=1, startTimeMs=2, durationMs=3, startSegmentIndex=4, endSegmentIndex=5 }`; omitted until a segment arrived |
| 4 | `playerTimeMs` | sent when > 0 |
| 5 | `videoPlaybackUstreamerConfig` | the base64 blob from `playerConfig.mediaCommonConfig.mediaUstreamerRequestConfig` |
| 16 | `preferredAudioFormatId` | `FormatId { itag=1, lastModified=2 }` |
| 19 | `streamerContext` | `{ clientInfo=1, poToken=2, playbackCookie=3, sabrContexts=5 (repeated) }` |

`clientInfo` = `{ deviceMake=12, deviceModel=13, clientName=16 (InnerTube client id), clientVersion=17,
osName=18, osVersion=19, androidSdkVersion=64 }`.

### 3.2 The response - UMP frames

A UMP body is a sequence of parts, each `umpVarint(partType) + umpVarint(partSize) + payload`. **The UMP
varint is NOT the protobuf varint** - the leading byte's high bits encode the total width (like UTF-8);
`SabrUmp.readVarint` implements it. The `MEDIA` part's header-id prefix is a UMP varint too
(`SabrMessages.mediaHeaderId`) — never read it as protobuf LEB128; the two agree only below 128.

| type | name | what we do |
|---|---|---|
| 20 | `MEDIA_HEADER` | `{ header_id=1, itag=3, start_range=6, is_init_seg=8, sequence_number=9, content_length=14, time_range=15 }` - maps a `header_id` to a segment's byte offset + time span |
| 21 | `MEDIA` | `umpVarint(header_id) + bytes` - written at the header's offset |
| 42 | `FORMAT_INITIALIZATION_METADATA` | `end_segment_number=4` - total segment count |
| 35 | `NEXT_REQUEST_POLICY` | `playback_cookie=7` - echoed back in the next request |
| 43 | `SABR_REDIRECT` | a new url (field 1) to continue against |
| 57 | `SABR_CONTEXT_UPDATE` | `{ type=1, value=3 }` - echoed back as `streamerContext.sabrContext { type=1, value=2 }` |
| 58 | `STREAM_PROTECTION_STATUS` | 1=OK, 2=pending, 3=attestation-required; `SabrProtection.STALL_LIMIT` (3) consecutive no-media responses at >= 2 bail the session as `attestation-capped`, so the fallback moves to the next client |
| 44 | `SABR_ERROR` | the server rejected the request - fail the session |

### 3.3 The continuation loop (`SabrSession.loop`, a port of `tests/sabr-stream.mjs`)

1. POST the request (cold start: no `bufferedRange`, no `selectedFormatId`).
2. Parse the UMP response: `MEDIA_HEADER`s, `end_segment_number`, cookie, context updates, redirect,
   `SABR_ERROR`, protection status.
3. Write each **new** segment (init once, each `sequence_number` once - resends skipped) at its
   **absolute `start_range`** (sec 4).
4. Advance `playerTimeMs` to the buffered end; `bufferedRange` spans OUR first segment to the buffered
   end (a seek-restarted session anchors at its own first segment, never (0, 1)). Echo cookie + contexts.
5. Stop when `lastSeq >= end_segment_number`, or when byte coverage from the first segment reaches
   contentLength (a seeked session gets no `end_segment_number`); fail on 6 dry responses, the
   duration-derived iteration cap, or the attestation cap.

---

## 4. Reassembly correctness

**Segments are written at their absolute byte offset, never sequentially appended.** `SabrBuffer.writeAt`
writes into a spool file preallocated to contentLength and tracks filled intervals, exposing a
**contiguous-from-0 watermark** and per-region coverage (`readCovered`). Sequential append corrupted the
container, so the extractor reported a garbage duration that overflowed media3's `getBufferedPercentage`
and crash-looped the app on restore. `SabrBufferTest` pins out-of-order writes reassembling byte-exact,
gaps holding the watermark back, covered mid-stream regions, and a final segment that overshoots
contentLength being CLAMPED to the declared length (the in-range prefix is written, never dropped whole).

### 4.1 `CastAwarePlayer.getBufferedPercentage` (general hardening)

Overridden with **double math, clamped 0..100**, guarding `TIME_UNSET`/zero/NaN. media3's default throws
`IllegalArgumentException: Out of range` on a pathological duration/position, and the session polls it
on every player-info change **including restore**. Never let a buffered-percentage read crash the session,
for any source.

---

## 5. The engine (`playback/sabr/`)

| File | Responsibility | Test |
|---|---|---|
| `SabrProto.kt` | protobuf wire writer/reader; an overflowing length varint is truncation, not a throw | `SabrProtoTest` |
| `SabrUmp.kt` | UMP frame parser (leading-bits varint + part types) | `SabrUmpTest` |
| `SabrMessages.kt` | request builders + response part parsers; field numbers | `SabrMessagesTest` |
| `SabrBuffer.kt` | DISK-backed positional reassembly, region coverage, demand pacing | `SabrBufferTest` |
| `SabrSpool.kt` | the spool dir (`cacheDir/sabr-spool/`) + the persistent replay cache | `SabrSpoolTest`, `SabrStreamLifecycleTest` |
| `SabrSeekLogic.kt` | PURE seek-restart decision shared by audio + video | `SabrSeekLogicTest` |
| `SabrProtection.kt` | PURE attestation-cap detector | `SabrProtectionTest` |
| `SabrSession.kt` | `SabrConfig` + the continuation state machine (seek start, pacing) | `SabrStreamLifecycleTest` |
| `SabrDataSource.kt` | `SabrAudioStream`, `SabrStreamRegistry` (`sabr://<id>`), the ExoPlayer `SabrDataSource` | `SabrStreamLifecycleTest` |
| `SabrStreamResolver.kt` | `buildConfig`, the SABR OkHttp client, tolerant pot decode, the audio download | - |
| `SabrPlayerResolver.kt` | roster resolve (`/player` + pot + cipher), `pickAudio`, resolve cache, stall fallback | `SabrAudioPickTest` |
| `SabrVideoSession.kt` / `SabrVideoStream.kt` / `SabrVideoResolver.kt` | video over SABR (sec 9) | `SabrVideoRungPickTest` |

**`SabrBuffer` is disk-backed, never a heap array**, so a multi-hour episode or a 2160p track can't OOM,
and reads serve any COVERED region, not just a prefix. It refuses an out-of-range contentLength at
construction (`lengthValid`, 1..`MAX_BUFFER_BYTES`); resolvers skip such a format instead.

**Audio stream lifetime is registry-owned, never per DataSource open.** `SabrStreamRegistry` holds the
id's `SabrConfig` and live `SabrAudioStream`; media3 closes/reopens the DataSource on every seek outside
its sample buffer, so `SabrDataSource.close()` drops only its own refs. A stream ends only on registry
replace, eviction (`MAX_STREAMS` = 3: current + gapless-next), `remove`, or `clear` (`MusicService.onDestroy`,
both registries). `destroy()` marks an incomplete buffer errored so a parked reader is always woken.

**Streams orchestrate sessions around reader demand** (`SabrAudioStream` / `SabrVideoStream`): a covered
read serves from the spool; a read ahead of a live session's landing point waits for it to drain forward;
a read behind the session's first segment (or with no live session) **seek-restarts** a session at the
estimated `playerTimeMs` (`SabrSeekLogic.estimateStartMs`, linear over `approxDurationMs`). The pure
`SabrSeekLogic.decide` rules:
- a session that landed AT OR BEFORE the target is left to DRAIN FORWARD, never restarted (re-issuing the
  same estimate only cancels the one session making progress);
- only a landing PAST the target widens the back-margin and re-aims;
- an unknown duration estimates 0 (a from-0 drain), never an endless restart-at-0;
- `MAX_SEEK_RESTARTS` bounds it, then the stream errors.

Each restart re-anchors the demand gate to the seek target (`SabrBuffer.resetDemandFrom`); pacing against
the stale pre-seek watermark blocked the fresh session before its first POST on any track larger than the
ahead-window. **Playback sessions are demand-paced** (`SabrBuffer.awaitDemand`, `AHEAD_AUDIO_BYTES` 8 MiB;
video `AHEAD_VIDEO_BYTES` 32 MiB): a skipped track stops the spend, and the server session survives the
idle gaps. Downloads drain at full speed.

**Honesty rules:**
- An **incomplete drain is an error, never complete**: the reader still serves every reassembled byte,
  then surfaces a real player error at the gap (`markComplete` would silently truncate the track).
- A playback session is **`restartable`**: it never marks the shared buffer on its own failure (it only
  wakes the reader); the stream is the sole terminal-error authority, so a dying session can't poison the
  buffer the next seek-restart reuses. A standalone (download) session marks the buffer.
- `SabrSpool` promotes a complete drain on stream destroy (`<id>.<itag>.done` + `<id>.meta`,
  `MAX_CACHE_BYTES` 512 MiB LRU). Promotion deletes any stale-itag `.done` sibling, and prune only deletes
  a `.meta` that still points at the file it evicts, so a live replay is never stranded.

---

## 6. The resolver + client roster (`SabrPlayerResolver`)

`resolve(videoId, enabled, …)` tries the **enabled** clients in order `WEB_REMIX -> VISIONOS ->
TVHTML5_SIMPLY`; the first that returns SABR inputs wins. Each is toggleable
(`StreamSabr{WebRemix,VisionOS,TVHTML5}Key`, default on; the "SABR clients" sub-list in Stream Sources).

- **Web** (WEB_REMIX / TVHTML5_SIMPLY): `/player` with the cipher STS + the web player pot; the SABR url
  is **n-transformed** (`CipherDeobfuscator.transformNParamInUrl`) and the videoId-bound pot
  (`streamingDataPoToken`) appended as `&pot=`.
- **Direct** (VISIONOS): no STS/player pot; the url is used as-is (no n-transform, no url-pot).
- The `streamerContext.poToken` is the **session (visitorData-bound)** token for all of them
  (`playerRequestPoToken`), so a fresh resolve always needs the WebView pot.
- **Pot decoding is tolerant** (`SabrStreamResolver.decodeBase64`): `PoTokenGenerator` emits standard
  base64 (`+/`), bgutils url-safe (`-_`); normalize, pad, decode. A strict URL_SAFE decode throws on the
  app's tokens.
- **Audio pick** (`pickAudio`, `SabrAudioPickTest`) mirrors `YTPlayerUtils`: bitrate weighted by
  `AudioQuality` (AUTO follows the metered state) plus the opus/webm bonus. `opusAllowed = false` (a
  COMPATIBLE / pre-API-29 download) restricts to AAC (`audio/mp4`) and drops the bonus - DIRECT's
  `downloadOpusOk` parity. Downloads resolve at HIGH.
- **Failure classification:** with `classifyErrors = true` (playback), if every client failed and a
  failure was network-class (unknown host / connect / timeout / SSL) it is rethrown, so `MusicService`
  raises `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` and `waitOnNetworkError` fires instead of skipping the
  queue. Downloads keep plain null-on-failure.
- **Resolve cache** (`CACHE_TTL_MS` 45 min, `CACHE_MAX` 8; songUrlCache parity): playback-only (`register
  = true`); a failed stream invalidates it.
- **Stall fallback:** a client whose session drained INCOMPLETE for an id is recorded (`recordStall`, fed
  by the sessions' `onIncomplete`) and tried last on that id's next resolve — a `/player` that succeeds
  gives the roster loop no other failure signal. Shared with the video resolver.

innertube exposes the inputs additively: `StreamingData.serverAbrStreamingUrl` +
`PlayerConfig.mediaCommonConfig.mediaUstreamerRequestConfig.videoPlaybackUstreamerConfig` (defaulted null).

---

## 7. Integration (`MusicService`, the RELAY pattern)

- **Routing:** `RoutingDataSource` picks the factory at `open()`, where the dataSpec key is known: RELAY
  -> the relay factory; a DIRECT `video:`/`videoaudio:` key -> the DIRECT factory even under SABR (SABR
  video uses `sabrvideo://`/`sabraudio://` URIs, so the audio resolver must never receive one); SABR on
  (the `sabrModeNow` mirror, not a per-open blocking read) -> `sabrDataSourceFactory`; else DIRECT.
  `isSabrPlaybackMode()` reads the same mirror for the main thread.
- **`sabrDataSourceFactory`** is a `ResolvingDataSource` over a `DefaultDataSource.Factory` wrapping the
  SABR source, so a downloaded file's `content://`/`file://` uri (`resolveDownloadedFileUri`) routes to
  the platform sources (`SabrDataSource` accepts only `sabr://`). The callback, in order:
  1. a downloaded file plays from disk;
  2. a **usable live registry stream** is reused as-is (a seek's close→reopen, a repeat-one replay) — no
     second `/player` + pot, no telemetry re-seed, no duplicate `FormatEntity`; a failed one is torn down
     (registry + resolve cache, and a failed spool replay evicts its cache entry);
  3. a **spool replay** (`SabrSpool.lookup`) serves the whole play from disk, zero network;
  4. otherwise `runBlocking` a fresh `SabrPlayerResolver.resolve`.
- A fresh resolve seeds `watchTimeReporter.onTrackingResolved` + `Tracker.onStreamResolved` (the player
  hash only for web clients), launches **`recoverSong`** (else the listen-end `Event` insert fails its
  foreign key for a song not yet in the DB), and upserts a **`FormatEntity`** with `streamClient =
  "WEB_REMIX (SABR)"` etc. and the response's `loudnessDb` (audio normalization works; a SABR play never
  nulls a stored loudness). `ShowMediaInfo` strips the ` (SABR)` suffix so a web SABR client still
  resolves its player hash; VISIONOS (SABR) stays N/A (no cipher).

### 7.1 Full DIRECT parity

SABR hits googlevideo like DIRECT, so every DIRECT feature is wired — never describe SABR as a reduced mode:
- **Stats, views, watch time:** the resolve seeds the reporter from THIS `/player` (no second round-trip,
  truthful `fmt`), and every media POST carries the listen's cpn (`MusicService.sabrCpnFor` ->
  `SabrConfig.cpn` / `SabrVideoConfig.cpn`, appended in `prepared()`) - DIRECT's `stampCpn` correlation.
  A spool replay rides the reporter's metadata-fetch fallback; an offline replay is captured by its
  offline branch and pushed by the deferred stats queue. The reporter gates only RELAY and cast.
  Proof: `tests/sabr-watchtime.mjs`; CDN safety of the stamp: the harness `CPN=` knob.
- **Audio quality:** `pickAudio` (sec 6).
- **Instant switching + prefetch:** one `/player` serves every video rung (the itag is pinned per
  request); `SabrVideoResolver` caches it, and `MusicService.prefetchVideoRendition` warms that cache
  (`SabrVideoResolver.prefetch`, never the DIRECT `/player`) while the Song/Video pill shows.
- **Metered AUTO cap:** AUTO video is capped at 720p AND `VideoRendition.defaultMaxBitrateKbps`; an
  explicit label is never capped.
- **Errors:** a video-mode error calls `SabrVideoResolver.invalidate` alongside the DIRECT caches.
- **Replays:** the spool replay cache (playerCache parity) + the 45 min resolve cache (songUrlCache parity).
- **Data usage / seeking:** demand pacing and seek-restart (sec 5) — a resumed long episode starts near
  its resume point without draining the head.

### 7.2 Downloads

When SABR mode is on, downloads run over SABR too (a walled client's progressive download URL is walled
the same way). `MediaStoreDownloadManager` mirrors the RELAY branch:
- The enabled SABR clients are read from the same prefs as playback (`StreamSabrKey` + the three client
  keys; empty under RELAY) and split into `sabrAudioMode` and `sabrVideoMode` (sec 9.4).
- `playbackData` is null; audio runs `SabrStreamResolver.download(videoId, enabled, file, onProgress,
  audioQuality, opusAllowed)` (HIGH; `opusAllowed = AudioRemux.oggMuxSupported && DownloadAudioFormat.BEST`),
  which resolves with **`register = false`** (a download must never touch the playback registry) and
  runs a session to completion. An **incomplete** drain returns null → the attempt retries; a truncated
  stream is never saved.
- Both SABR drains run under `runInterruptible` (cancelling the download Job interrupts the in-flight
  OkHttp call) and report progress through the throttled `sabrProgressReporter`.
- The null-`playbackData` tail is shared with relay: the container is sniffed (`sniffAudioExtension`:
  WebM/Ogg → `.opus`, MP4 → `.m4a`), duration comes from the file (`durationSecFromFile`), and `isVideo`
  is false for an audio download (true for a `sabrVideoMode` mux).
- DIRECT and RELAY download paths are untouched; every SABR branch is a no-op while SABR is off.

---

## 8. The harness - proof + validator

`tests/` (Node >= 20; `npm ci --prefix tests` once; needs `innertube_cookie.txt` at the repo root):

- **`node tests/sabr-stream.mjs [videoId] [client]`** - whole-song drain proving byte-exact reassembly by
  full distinct-segment coverage summing to `contentLength`. The reference the Kotlin engine ports.
- **`node tests/sabr-seek.mjs [videoId] [seekSeconds] [client]`** - a session cold-started at
  `playerTimeMs = T` serves the segment containing T and the tail drains whole. `PACE_PAUSE_S` /
  `PACE_EVERY` insert idle gaps (the demand-pacing proof). Dual-track: `START_S=<s> node tests/sabr-video.mjs`.
- **`node tests/sabr-watchtime.mjs [videoId]`** - a whole cpn-stamped WEB_REMIX drain, then the same
  cpn's playback / watchtime / final beacons; every ping must 204.
- **`node tests/sabr-clients.mjs [videoId]`** - the roster verdict (sec 2).
- **`node tests/sabr-video.mjs [videoId] [client] [maxHeight]`** / **`node tests/sabr-video-clients.mjs
  [videoId] [maxHeightPx]`** - video (sec 9).

When the app's client constants / pot / cipher change, keep the harness mirrors (`tests/clients.mjs`, …)
in step.

---

## 9. Video over SABR (dual-track, quality-pinnable)

One SABR session carries **video + audio interleaved**, and the exact video itag is **pinnable**, so the
DIRECT quality ladder carries over.

### 9.1 The dual-track request (`SabrMessages.abrRequestVideo`)

Differences from sec 3.1: `enabledTrackTypesBitfield` value **0** (video + audio);
`preferredAudioFormatId` (16) AND **`preferredVideoFormatId` (17)**; `selectedFormatId` (2) and
`bufferedRange` (3) are sent **per track**; `playerTimeMs` advances to the **minimum** buffered end of
the two tracks. Each `MEDIA` part is routed to its track by its header's `MediaHeader.itag`, and each
track reassembles positionally into its own `SabrBuffer`.

### 9.2 Field 17 is the lever

Only top-level field **17** makes the server serve the requested video itag (e.g. 133/134/135/136/137 ->
exactly that rung, whole and byte-exact); without it the server picks its own (av01) format. This lets
the app pin broadly decodable avc1. The lesson: never reason from convention against this CDN - prove a
field against live bytes (`tests/sabr-video.mjs`'s `VFMT` knob; the sweep is recorded in its header).

### 9.3 Roster

`tests/sabr-video-clients.mjs` shows the reliable video+audio set is **identical to the audio roster**
(WEB_REMIX, TVHTML5_SIMPLY, VISIONOS); the sensitive clients hit the same ~60s identity cap. The app's
SABR roster covers video unchanged.

### 9.4 App integration (RELAY/SABR isolation pattern)

- **`SabrVideoSession`** - one loop draining both tracks into two `SabrBuffer`s.
- **`SabrVideoStream` / `SabrVideoRegistry` / `SabrVideoDataSource`** - ONE shared session feeding two
  DataSources (`sabrvideo://<id>` + `sabraudio://<id>`) merged by a `MergingMediaSource`. **Stream
  lifetime is explicit, never tied to DataSource open/close**: entering video mode seeks mid-track and
  media3 re-opens both children, leaving a close→reopen gap with zero DataSources open; a ref-counted
  lifetime killed the session inside that gap. The stream ends only on registry `remove`
  (`VideoModeController.clearState`, the chokepoint every video-mode exit funnels through) or `put`
  replacing it. `destroy()` **marks both buffers errored** so parked readers are always woken.
- **`SabrVideoResolver`** - dual-format resolve over the same roster (cipher n-transform for web clients),
  pinning the video itag via field 17 for the quality target. Its ladder is `VideoQualityLogic.rungs`
  minus progressive (SABR video is dual-track) and minus rungs `VideoDecoderCaps` rejects; rungs whose
  contentLength fails `SabrBuffer.lengthValid` are excluded from the pick, not the published ladder.
  **The resolve returns a READY, UNREGISTERED stream**: `VideoModeController` installs it
  (`SabrVideoRegistry.put`) only at the swap COMMIT on the main thread, after the `stillOurs` guard (an
  IO-thread put destroyed the currently-playing stream before the guard could veto). Position +
  `playWhenReady` are captured AT COMMIT, never before the seconds-long resolve.
- **Wiring** (all `StreamSabrKey`-gated; RELAY takes priority; DIRECT unchanged):
  `createMediaSourceFactory` builds the merge from two `SabrVideoDataSourceFactory` children for a
  `sabrvideo://` URI. `VideoModeController.enterVideoModeSabr` resolves asynchronously, then swaps to an
  item whose **cache key is `video:<id>:q<itag>`** (so the exit / own-swap / listen machinery recognises
  it, and each rung is a distinct item) but whose **URI is `sabrvideo://<id>`**.
- **Live quality switcher:** unlike RELAY, SABR pins an exact itag, so the picker is offered; the
  controller publishes the resolver's ladder, and `setVideoQuality` / `downgradeForStall` re-resolve the
  session at the new target through the shared `resolveAndSwapSabr` (each rung is a different
  server-pinned stream, so there is no cache re-key). AUTO caps at 720p.
- **Downloads** (`sabrVideoMode`): `SabrVideoResolver.download` drains both tracks to two temp files and
  remuxes on-device (`VideoMuxer.mux`), like a DIRECT adaptive download, with DIRECT's gates
  (`pickRung(downloadable = true)`, `SabrVideoRungPickTest`): only remux-capable rungs
  (`VideoQualityLogic.isDownloadableRung` - no av01; webm/vp9 only on API 29+) and a CONTAINER-MATCHED
  audio partner (mp4/avc → AAC, webm/vp9 → Opus), so an explicit pick can never drain hundreds of MB into
  a deterministic INCOMPATIBLE mux. An incomplete drain throws (retryable); `INCOMPATIBLE` clears the
  requested quality, `TRANSIENT` preserves it. Cancel + progress ride the sec 7.2 wiring.
- **DataSource lifecycle:** every SABR `DataSource` fires `transferEnded()` only after `transferStarted()`
  ran — media3's `DefaultBandwidthMeter` NPEs otherwise, and a `MergingMediaSource` tearing down a
  sibling mid-open surfaced that as "Source error".

---

## 10. Known limitations

- The byte→time seek estimate is linear over `approxDurationMs`, so a highly VBR track may need a
  convergence restart or two (bounded by `MAX_SEEK_RESTARTS`, then errors loudly).
- **Casting** cannot ride SABR: the receiver fetches its own URL and cannot speak UMP.
- **A WebView poToken is required** for every fresh resolve (the streamerContext pot), unlike DIRECT's
  pot-less VISIONOS fallback.
- **On-device soak** (more content, long tracks, seeks, network transitions) is the remaining gate before
  SABR is promoted from experimental.
