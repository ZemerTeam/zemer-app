# Watch-time reporting — emulating a genuine YouTube Music playback-stats session

Hand-authored docset for the playback-stats emulation: every DIRECT Zemer play sends the same
view + watch-time signals a real YouTube Music (WEB_REMIX) web session sends, so real user plays
give the artist maximal *legitimate* credit (Studio watch time, retention, YPP qualified watch
hours). The authoritative wire spec is the handoff doc
(`~/zemer-fix/handoff-docs/zemer-app-emulate-youtube-music-stream-request.md`); the reverse-engineering
evidence lives in `~/zemer-fix/ytmonetization/`. Every claim below cites the file or live capture
that proves it.

## TL;DR

Before this, the app fired **exactly one** view beacon per listen (`videostatsPlaybackUrl`) at
listen END, with a fresh random `cpn`, and never sent watch time. Now every direct play runs a full
stats session: **one `cpn` per listen**, a **playback ping at play START**, **watchtime pings** at
the server's scheduled cadence plus on pause/seek carrying the **really-watched** segments, and a
**`final=1`** ping at end. Covers music, video-songs and podcast episodes. **Confirmed on a live
channel 2026-08-14**: a direct play credits both a real view AND real watch time in Studio (14
views / 0.2 h on a test video). Crediting does **not** require CDN-cpn correlation.

## The invariant that rules everything

**Watch time reported MUST equal what the user actually played.** Fabricated watch time is invalid
traffic by YouTube's own definition — it gets stripped and can flag the channel. This system
*completes* the signals a real play already generates; it manufactures nothing. Every reported range
comes from real player positions; a paused player accrues nothing; a seek is never watched time.

Second invariant, inherited from tracking: **telemetry must never break playback.** Beacons are
fire-and-forget on the service scope; a network failure logs at most a `Timber.d` line and moves on.

## The model (what the official WEB_REMIX client does, captured live)

The `/player` response's `playbackTracking` (parsed in `PlayerResponse.PlaybackTracking`) drives it.
A live WEB_REMIX capture (`~/zemer-fix/ytmonetization/tests/`) shows these keys:

| key | what it is | do we send it |
|-----|-----------|---------------|
| `videostatsPlaybackUrl` | **playback** ping — opens the stats session at START | **yes** |
| `videostatsWatchtimeUrl` | **watchtime** ping — periodic + final, carries watched segments | **yes** |
| `videostatsScheduledFlushWalltimeSeconds` / `videostatsDefaultFlushIntervalSeconds` | the flush cadence (live: `[10,20,30]` then `40`) | **yes** — drives our ticker |
| `atrUrl` | ad telemetry | no (no ads — spec says skip) |
| `ptrackingUrl` | one-shot playback tracking | no (param set not verifiable enough to send truthfully) |
| `qoeUrl` | encoded buffer/quality telemetry | **no — cannot produce truthfully** (see the ceiling) |
| `videostatsDelayplayUrl` | fired only when playback was delayed at start | no (not a normal-path beacon) |

All beacon params ride `ver=2&c=WEB_REMIX&cpn=…`, `s.youtube.com`→`music.youtube.com` host swap, with
the WEB_REMIX headers + SAPISIDHASH via the shared `InnerTube.ytClient`. Base URLs (already carrying
`docid`, `ei`, `len`, `plid`, `of`, `ns`, `el`, `cl`, …) come straight from the `/player` response.

## The pieces

- **`playback/WatchTimeSegments.kt`** (pure, JVM-tested `WatchTimeSegmentsTest`) — accumulates the
  media-time ranges actually played and drains them as the `st`/`et` lists of a ping. Drains are
  **deltas** (each ping carries only newly watched ranges, like the official client), seeks close at
  the departed position and reopen at the target, a backwards jump without a seek closes rather than
  fabricates, sub-`MIN_SEGMENT_MS` (500ms) jitter is dropped. Seconds format `%.1f` (Locale.US).
- **`playback/WatchTimeSchedule.kt`** (pure, JVM-tested `WatchTimeScheduleTest`) — the flush cadence.
  `flushOffsetMs(index)` returns the wall-clock offset of the index-th flush: the server's scheduled
  seconds first, then the last scheduled offset plus multiples of the default interval. Falls back to
  the base.js `klA` default `[10,20,30]`/`40` when the response omits the fields.
- **`playback/WatchTimeReporter.kt`** — the session owner (the `EpisodePositionTracker` extraction
  pattern). Session state is confined to the service main scope; one **ordered ping channel** per
  session so the playback ping always precedes its watchtime pings; the tracking-URL cache is the one
  concurrent piece (seeded from the data-source resolver thread). One `WatchTimeSchedule` per session;
  a `scheduledFlushCount` advances the wall-clock ticker (pause/seek pings are extra and never touch
  it). Beacons are fire-and-forget via `YouTube.registerPlayback` / `registerWatchtime`.
- **`innertube` `YouTube`/`InnerTube`** — `generateCpn()` (16 chars), `registerWatchtime(...)`, and
  optional `cmt`/`final`/`fmt`/`muted` on `registerPlayback` (all additive; legacy callers unchanged).

## MusicService wiring (event forwarding only)

`MusicService` forwards player events; the reporter holds all the logic:

- `onIsPlayingChanged` → open/continue the session, start/stop the scheduled ticker.
- `onPositionDiscontinuity` → a same-item seek closes+reopens the segment and fires a state-change
  ping; an item change captures the departed item's REAL end position for the final ping.
- `onMediaItemTransition` → a REAL track change ends the departed session (`final=1`) and arms the
  next. Placed **after** the video-mode own-swap early-return, so an audio↔video swap keeps its
  session (same listen, one cpn).
- `STATE_ENDED` → the last queue item ran out (no transition fires): send the final ping.
- `onDestroy` → best-effort final ping.
- The stream resolver hands `onTrackingResolved(mediaId, playbackTracking, itag)` so the session opens
  with **no extra `/player` round-trip** and `fmt` carries the real streamed itag; cached/local plays
  fall back to one light metadata fetch (the legacy ping's own behavior).

The legacy end-of-listen `registerPlayback` call was **removed** — keeping it would double-report the
session. Do not reintroduce it.

## Coverage (independently audited — zero gaps)

There are exactly **three** ExoPlayer instances in the app: the one in `MusicService` (fully covered
by the reporter) and two **status-viewer** players (`StoryScreen`, `SavedStatusScreen`) that carry
only third-party JewishStatus/YidStatus content and **never** touch a YouTube videoId — they can
never beacon. Every queue type (`ListQueue`, `YouTubeQueue`, `ZemerRadioQueue`, `StationQueue`,
`LocalAlbumRadio`), Android Auto, the home widget, cast, downloads, podcasts and video-mode share the
one MusicService player. Zemer stations/radio are DIRECT (the *selection* is Zemer-served; the audio
stream is still InnerTube + cipher), so they beacon.

## Hard exclusions

- **RELAY mode** (`stream.zemer.io`) — beacons must never ride the relay egress (spec rule + our test
  showed relay beacons don't even register). Gated at session creation (`isRelay`).
- **Cast** — the receiver plays, not this device. Gated (`isCasting`).
- **Offline local plays** — no tracking URL resolvable; the session reports nothing.
- **Listen-history paused** (`PauseListenHistoryKey`) — silences beacons (parity with the legacy
  ping, which sat inside that gate).

Every exclusion is a strict gate; nothing is fabricated for an excluded play.

## Fidelity: verified-from-data only, never guessed

The extra params the official client carries were sourced by reading the **live deployed** player
`base.js` `Y2`/`qT` builders (fetched from `music.youtube.com`), not from memory. Only params whose
KEY **and** truthful VALUE are both derivable are sent:

- **`fmt=<itag>`** — base.js `n.fmt=y.D.itag`; the real resolved itag. Omitted on cached/local plays
  where it is unknown, never fabricated.
- **`muted`/`mos`** — base.js `isMuted()?1:0`, `mos == muted`. Our player has no mute separate from
  volume, so `player.volume <= 0` is the truthful read; captured on the main thread at enqueue time.
- **Flush cadence** — server-provided (see `WatchTimeSchedule`).

**Deliberately NOT sent** (value only obtainable from memory, which would be guessing): `volume`
(scale is a mangled method in base.js), `state` (the `state=` value strings could not be pinned to
literals in the minified source), `fs`/`playerheight`/`playerwidth`/`clipid` (no truthful value for an
audio service). Adding any of these later requires re-reading base.js for its exact value semantics.

## The ceiling on "indistinguishable"

Two things a real browser sends that we do not, and why:

- **CDN `cpn` correlation** — the official client stamps `cpn=${clientPlaybackNonce}` on the
  googlevideo media request (confirmed in base.js), and appending it to a real deciphered stream URL
  keeps HTTP 206 (proven live, `~/zemer-fix/ytmonetization/` / the cpn-safety probe). **But watch
  time credits fully WITHOUT it** (proven on the live channel), so this is now purely fingerprint
  matching, and it touches the streaming danger zone. Not implemented — it is unnecessary for the
  goal and belongs in its own `tests/`-validated change if ever wanted. If added, the cpn must be
  minted once per play and shared between the media request and the beacon session.
- **`qoe` beacons** — a real client always sends encoded buffer/quality telemetry. We **cannot**
  produce that truthfully, and faking it would be both dishonest and risky. This is the hard floor:
  true byte-level indistinguishability is not reachable without fabrication, which the honesty
  invariant forbids.

## Regression gate

`WatchTimeSegmentsTest` (honesty rules: deltas, seeks-not-counted, jitter drop, pause, backwards
correction, format) + `WatchTimeScheduleTest` (the flush offset math). Beacon request shapes are
replica-verified against live YouTube (every beacon HTTP 204/204); the added params + the flush
schedule are verified against the live deployed `base.js` and a live `/player` capture. When touching
this system, keep every reported value tied to a real player observation, and keep the coverage/
exclusion gates intact.
