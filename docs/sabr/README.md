# SABR playback (`playback/sabr/`)

SABR (**S**erver-side **A**daptive **B**it**R**ate, over the **UMP** framing) is an alternative media
transport to progressive stream URLs. It is **experimental and OFF by default**, fully isolated behind a
Stream Sources toggle; with it off the DIRECT playback path is byte-for-byte unchanged.

This is the deep guide. The one-paragraph version lives in `AGENTS.md` (§SABR playback); the field numbers
and the ground-truth behavior live in the harness (`tests/sabr-stream.mjs`, `tests/sabr-clients.mjs`),
which is where any change must be proven against the live CDN first.

---

## 1. Why SABR exists

YouTube is migrating clients **off progressive delivery**. For a migrated client the `/player`
response's `adaptiveFormats[].url` is now only a **~1-MiB preview stub**: it serves the first ~1 MiB and
then `403`s every further range (verified against the live CDN — see the streaming investigation). The
real, whole media lives behind a single field, `streamingData.serverAbrStreamingUrl`, which only speaks
SABR:

> POST a binary `VideoPlaybackAbrRequest` → receive a **UMP**-framed body → decode it → advance the
> player time + buffered ranges → POST again → repeat until every segment has arrived.

Today the app streams its clients progressively and that still works. SABR is the **fallback for when
progressive gets walled for those clients too** — the industry trend (see yt-dlp #12482). It is landed
now, isolated and off, so the transport is ready and validated before it is ever needed.

---

## 2. What actually works (validated, live)

Validated whole-song over SABR with **the pot the app can mint** (the WebView BotGuard pot), across
multiple videos, by `tests/sabr-clients.mjs`:

| Client | SABR result | In the app roster? |
|---|---|---|
| **WEB_REMIX** (the app's main client) | ✅ whole song, every video | yes (tried first) |
| **VISIONOS** / VISIONOS_0_1 | ✅ whole song, pot-less direct client | yes |
| **TVHTML5_SIMPLY** | ✅ whole song, no sign-in | yes |
| **MWEB** | ▲ whole song on most content, context-challenge stall on some | yes (last) |
| IOS / IPADOS / WEB_CREATOR / ANDROID_VR | ✖ throttled to ~60s on most content (whole only on rare unrestricted videos) | **no** |
| WEB (desktop) | ✖ needs browser-grade attestation | no |

Two hard-won facts behind that table:

- **The web family's `serverAbrStreamingUrl` is CIPHERED** — it carries `n`/`sig` params, unlike the
  direct clients' URLs. It must be **n-transformed** (the same cipher the app runs for web progressive
  URLs) before POSTing, and the **videoId-bound pot appended** as `&pot=`. This n-transform was the single
  missing piece that unlocked WEB_REMIX/TVHTML5_SIMPLY/MWEB; without it they `403`.
- **The ~60s cap on IOS/IPADOS/WEB_CREATOR/ANDROID_VR is server-side, keyed on client identity, and NOT a
  bug in this code** — the *identical* loop and pot drain VISIONOS/WEB_REMIX whole. YouTube throttles the
  sensitive clients to ~60s on most (esp. premium/label) content; those clients require their native
  attestation (DroidGuard / iOS BotGuard) that an Android WebView cannot produce. They are excluded from
  the roster.

---

## 3. The protocol

### 3.1 The request — `VideoPlaybackAbrRequest`

Built by `SabrMessages.abrRequest`. Top-level fields (transcribed from the reverse-engineered protos,
pinned to the Node reference):

| # | field | notes |
|---|---|---|
| 1 | `clientAbrState` | `{ playerTimeMs=28, enabledTrackTypesBitfield=40 }` — 40=1 selects audio only |
| 2 | `selectedFormatId` | follow-up requests only (once a format is locked) |
| 3 | `bufferedRange` (repeated) | what we already have: `{ formatId, startTimeMs, durationMs, startSegmentIndex, endSegmentIndex }` |
| 4 | `playerTimeMs` | follow-up only |
| 5 | `videoPlaybackUstreamerConfig` | the base64 blob from `playerConfig.mediaCommonConfig.mediaUstreamerRequestConfig` |
| 16 | `preferredAudioFormatId` | `{ itag, lastModified }` |
| 19 | `streamerContext` | `{ clientInfo=1, poToken=2, playbackCookie=3, sabrContexts=5 }` |

`clientInfo` (inside `streamerContext`) carries `{ deviceMake=12, deviceModel=13, clientName=16 (the
InnerTube client id), clientVersion=17, osName=18, osVersion=19, androidSdkVersion=64 }`.

### 3.2 The response — UMP frames

A UMP body is a sequence of parts, each = `umpVarint(partType) + umpVarint(partSize) + payload`. **The UMP
varint is NOT the protobuf varint** — the leading byte's high bits encode the total width (like UTF-8);
`SabrUmp.readVarint` implements it. Parts we act on:

| type | name | what we do |
|---|---|---|
| 20 | `MEDIA_HEADER` | `{ header_id=1, itag=3, start_range=6, is_init_seg=8, sequence_number=9, content_length=14, time_range=15 }` — maps a `header_id` to a segment's byte offset + time span |
| 21 | `MEDIA` | `varint(header_id) + media bytes` — the actual audio, written at the header's offset |
| 42 | `FORMAT_INITIALIZATION_METADATA` | `end_segment_number=4` — the total segment count (completion test) |
| 35 | `NEXT_REQUEST_POLICY` | `playback_cookie=7` — echoed back in the next request |
| 43 | `SABR_REDIRECT` | a new url (field 1) to continue against |
| 57 | `SABR_CONTEXT_UPDATE` | `{ type=1, value=3 }` — echoed back as a `streamerContext.sabrContext { type, value }` |
| 58 | `STREAM_PROTECTION_STATUS` | `status` (1=OK, 2=pending, 3=attestation-required) — the attestation signal, logged |
| 44 | `SABR_ERROR` | the server rejected the request |

### 3.3 The continuation loop

`SabrSession.loop()` — a faithful port of `tests/sabr-stream.mjs`:

1. POST the request (cold start: no `bufferedRange`, no `selectedFormatId`).
2. Parse the UMP response; collect `MEDIA_HEADER`s, `end_segment_number`, the playback cookie, any
   `SABR_CONTEXT_UPDATE`s, a redirect, `SABR_ERROR`, `STREAM_PROTECTION_STATUS`.
3. For each **new** segment (init once, each `sequence_number` once — resends skipped), write its `MEDIA`
   bytes at its **absolute `start_range`** (see §4).
4. Advance `playerTimeMs` = the buffered end time; set `bufferedRange` = `[0, bufEnd]`, `endSegmentIndex` =
   the last sequence. Echo the cookie + context updates. POST again.
5. Stop when `lastSeq >= end_segment_number`.

---

## 4. Reassembly correctness (the crash that was)

**Segments are written at their absolute byte offset, never sequentially appended.** `SabrSession` tracks a
per-`header_id` write cursor starting at `start_range`; `SabrBuffer.writeAt(offset, …)` copies into a
fixed-size buffer and maintains a **contiguous-from-0 watermark** that `read()` blocks on. `available()` is
that watermark; `SabrDataSource.read()` serves bytes below it.

Why it matters: the first implementation appended segment bytes in arrival order, assuming perfect
ordering. That corrupted the webm/opus container, so ExoPlayer's extractor reported a **garbage
duration/position**, which overflowed media3's `Util.percentInt` inside `getBufferedPercentage` and
**crashed the app on every launch** (the media session polls it on restore — a poisoned persisted track
crash-looped with no recovery short of clearing data). The positional write makes reassembly **byte-exact
regardless of arrival order** — `SabrBufferTest` pins out-of-order writes (segment 3 before 2 before init)
reassembling to the exact bytes, gaps holding the watermark back, and over-length writes ignored.

### 4.1 The companion hardening — `CastAwarePlayer.getBufferedPercentage`

Overridden to compute the percentage in **double math, clamped 0..100**, guarding `TIME_UNSET`/zero/NaN.
media3's default throws `IllegalArgumentException: Out of range` on a pathological duration/position, and
the session polls it on every player-info change **including restore**. This makes it *structurally
impossible* for a buffered-percentage read to crash the media session — for SABR **or any current/future
source**. It is a general robustness fix, not SABR-specific, and must not regress.

---

## 5. The engine (`playback/sabr/`)

Pure, isolated, JVM-tested where possible:

| File | Responsibility | Test |
|---|---|---|
| `SabrProto.kt` | protobuf wire writer/reader (varint, length-delimited, fixed32) | `SabrProtoTest` |
| `SabrUmp.kt` | UMP frame parser (the custom leading-bits varint + part types) | `SabrUmpTest` |
| `SabrMessages.kt` | request builder + response part parsers; field numbers | `SabrMessagesTest` |
| `SabrBuffer.kt` | thread-safe positional reassembly + contiguous watermark | `SabrBufferTest` |
| `SabrSession.kt` | the continuation state machine → fills the buffer | (network; proven by the harness) |
| `SabrDataSource.kt` | the ExoPlayer `DataSource` + the `sabr://<mediaId>` registry | — |
| `SabrStreamResolver.kt` | builds/registers a `SabrConfig`; the SABR OkHttp client | — |
| `SabrPlayerResolver.kt` | resolves over the client roster (`/player` + pot + cipher) | (network) |

`SabrConfig` is everything one session needs; `SabrStreamRegistry` passes it from the resolver to the
DataSource keyed by media id (the DataSpec carries only a `sabr://<id>` uri).

---

## 6. The resolver + client roster

`SabrPlayerResolver.resolve(videoId, enabled)` tries the **enabled** clients in priority order and the
first that exposes SABR inputs wins:

```
WEB_REMIX (web)  →  VISIONOS (direct)  →  TVHTML5_SIMPLY (web)  →  MWEB (web)
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
strict `URL_SAFE` decode threw `bad base-64` on the app's tokens — the first on-device failure.

Each client is individually toggleable (`StreamSabr{WebRemix,VisionOS,TVHTML5,MWEB}Key`, all default on),
exposed as the **"SABR clients"** sub-list in Stream Sources. Only clients validated to deliver a whole
song are in the roster — the ~60s-capped ones are deliberately absent.

---

## 7. Integration (`MusicService`, the RELAY pattern)

- A per-open `sabrDataSourceFactory` — a `ResolvingDataSource` whose callback `runBlocking`s
  `SabrPlayerResolver.resolve` and returns a `sabr://<id>` uri — is selected in the dispatcher **only when
  `StreamSabrKey` is on**. Otherwise the DIRECT factory is used verbatim. A downloaded file still plays
  from disk (same as DIRECT/RELAY).
- On a successful resolve it **persists a `FormatEntity`** (overwriting any stale DIRECT one) with
  `streamClient = "WEB_REMIX (SABR)"` (etc.) + the format's itag/mime/codecs/bitrate/contentLength, so the
  **song-details sheet** (`ShowMediaInfo`) shows the SABR client + format exactly like a normal play.
  `ShowMediaInfo` strips the ` (SABR)` suffix before the web-client check, so a web SABR client still
  resolves its player hash + cipher date (the cipher n-transform ran); **VISIONOS (SABR)** stays N/A —
  correct, it runs no cipher.
- innertube exposes the inputs **additively**: `StreamingData.serverAbrStreamingUrl` +
  `PlayerConfig.mediaCommonConfig.mediaUstreamerRequestConfig.videoPlaybackUstreamerConfig` (defaulted
  null, ignored by clients that omit them).

---

## 8. The harness — the proof + validator

Both live in `tests/` (Node ≥20, deps vendored; needs `innertube_cookie.txt` at the repo root):

- **`node tests/sabr-stream.mjs [videoId] [VISIONOS|ANDROID_VR|IOS|IPADOS]`** — streams a whole song over
  SABR and proves byte-exact reassembly by **full distinct-segment coverage** (init + every segment
  1..N summing exactly to `contentLength` — a resend can't inflate a distinct-segment set). This is the
  reference the Kotlin engine is a port of.
- **`node tests/sabr-clients.mjs [videoId]`** — runs the whole client roster and reports, per client,
  whether it delivers a whole song over SABR with the app's pot. This produced the §2 table and the
  n-transform / context-update discoveries.

**Streaming is the danger zone.** Any change to the SABR path is proven against the live CDN in the
harness first, then on-device. When the app's client constants / pot / cipher change, keep the harness
mirrors (`tests/clients.mjs`, etc.) in step, exactly as for the DIRECT harness.

---

## 9. Known limitations / future work

- **Seeking** is served from the in-memory buffer (backward seeks are free; a forward seek blocks until the
  sequential drain reaches that offset). A true SABR seek (jump `playerTimeMs`) is not implemented.
- **Audio only.** The roster + request select the best audio format; video-over-SABR is out of scope.
- **MWEB** is inconsistent (context-challenge stall on some content) — it sits last so it's only reached
  when the reliable clients are disabled.
- **On-device soak** (more clients/content, long tracks, network transitions) is the remaining gate before
  SABR is promoted from experimental. It is fully isolated and cannot affect the DIRECT path while off.
