# Watch-time reporting - emulating a genuine YouTube Music playback-stats session

Every DIRECT Zemer play (music, video-songs, podcast episodes; SABR transport included) sends the same
view + watch-time signals a real YouTube Music (WEB_REMIX) web session sends, so real plays give the
artist their *legitimate* credit: **one `cpn` per listen**, a **playback ping at play START**,
**watchtime pings** at the server's scheduled cadence plus on pause/seek carrying the **really-watched**
segments, and a **`final=1`** ping at the end.

## The rules that rule everything

1. **Watch time reported MUST equal what the user actually played.** Fabricated watch time is invalid
   traffic by YouTube's own definition and can flag the channel. Every reported range comes from real
   player positions; a paused player accrues nothing; a seek is never watched time.
2. **Telemetry must never break playback.** Beacons are fire-and-forget on the service scope; a network
   failure logs at most a `Timber.d` line.
3. **The view is minted by the playback ping** (`videostatsPlaybackUrl`, `cmt=<start>`, `final=0`), fired
   at play START with no duration gate - YouTube counts a view from the first frame. Watch time does not
   gate the view but feeds Engaged views and YPP watch hours, so honesty stays the hard rule.
4. **Never reintroduce an end-of-listen `registerPlayback` call** - it would double-report the session.

## The model (the official WEB_REMIX client)

The `/player` response's `playbackTracking` (`PlayerResponse.PlaybackTracking`) drives it:

| key | what it is | sent |
|-----|-----------|------|
| `videostatsPlaybackUrl` | **playback** ping - opens the session at START | **yes** |
| `videostatsWatchtimeUrl` | **watchtime** ping - periodic + final, carries watched segments | **yes** |
| `videostatsScheduledFlushWalltimeSeconds` / `videostatsDefaultFlushIntervalSeconds` | the flush cadence | **yes** - drives our ticker |
| `atrUrl` | ad telemetry | no (no ads) |
| `ptrackingUrl` | one-shot playback tracking | no (no benefit) |
| `qoeUrl` | plain timestamped player telemetry | no (reproducible, but a controlled A/B showed zero watch-time-survival benefit) |
| `videostatsDelayplayUrl` | fired only when playback was delayed at start | no |

Beacons ride `ver=2&c=WEB_REMIX&cpn=…` with the `s.youtube.com`→`music.youtube.com` host swap
(`YouTube.statsUrl`) and the WEB_REMIX headers + SAPISIDHASH via the shared `ytClient`. Base URLs
(already carrying `docid`, `ei`, `len`, `plid`, `vm`, …) come straight from the `/player` response, so
those params need no synthesis.

## The pieces

- **`playback/WatchTimeSegments.kt`** (pure, `WatchTimeSegmentsTest`) - accumulates the media-time
  ranges actually played and drains them as a ping's `st`/`et` lists. Drains are **deltas** (each ping
  carries only newly watched ranges), a seek closes at the departed position and reopens at the target,
  a backwards jump without a seek closes rather than fabricates, sub-`MIN_SEGMENT_MS` (500 ms) jitter is
  dropped. `formatSeconds` = `%.1f`, `Locale.US`.
- **`playback/WatchTimeSchedule.kt`** (pure, `WatchTimeScheduleTest`) - `flushOffsetMs(index)`: the
  server's scheduled seconds first, then the last scheduled offset plus multiples of the default
  interval; falls back to the base.js `klA` default `[10,20,30]`/`40` when the response omits them. A
  fixed interval would be a timing fingerprint.
- **`playback/WatchTimeReporter.kt`** - the session owner. State is confined to the service main scope;
  one **ordered ping channel** per session so the playback ping always precedes its watchtime pings;
  the tracking-URL cache (`MAX_CACHED_TRACKING`, preserves the live listen's entry) is the one
  concurrent piece (seeded from the resolver thread). `scheduledFlushCount` advances the wall-clock
  ticker; pause/seek pings are extra and never touch it, and overdue offsets after a long pause are
  skipped, not burst. A tracking resolution older than `TRACKING_MAX_AGE_MS` (1 h) is re-fetched.
  Beacons go out via `YouTube.registerPlayback` / `registerWatchtime`.
- **`playback/PlaybackProbe.kt`** - the read-only `Player` slice the reporter needs (position,
  isPlaying, playbackState, playWhenReady, currentMediaId, hasCurrentMetadata, volume), so the whole
  state machine is JVM-tested with a pure fake (`WatchTimeReporterTest`). `MusicService` adapts the
  real `Player` - keep the probe returning exactly the `Player` values.
- **`innertube` `YouTube`/`InnerTube`** - `generateCpn()` (16 chars), `registerWatchtime(...)`, and
  optional `cmt`/`final`/`fmt`/`muted` on `registerPlayback`.

## MusicService wiring (event forwarding only)

- `onIsPlayingChanged` → open/continue the session, start/stop the ticker.
- `onPositionDiscontinuity` (primitive params) → a same-item seek closes+reopens the segment and fires a
  state-change ping, except a rendition swap's position-continuous seek (delta < `REAL_SEEK_MIN_MS`,
  1 s). Any `AUTO_TRANSITION` - a track boundary OR a **repeat-one loop** back to the same item - captures
  the departed item's REAL end position for the final ping.
- `onMediaItemTransition` → a REAL track change ends the departed session (`final=1`) and arms the next.
  Placed **after** the video-mode own-swap early-return, so an audio↔video swap keeps its session.
- `STATE_ENDED` → final ping for the last queue item. `onDestroy` → best-effort final ping.
- The stream resolver calls `onTrackingResolved(...)` so the session opens with **no extra `/player`**
  and `fmt` carries the real streamed itag; cached/local plays fall back to one metadata fetch.

End positions: on a track/queue CHANGE the departed item's own last-known position
(`WatchTimeSegments.lastKnownPositionMs`) is used, never `player.currentPosition` (it belongs to the new
item); `onPlaybackEnded` and `onDestroy` pass `player.currentPosition` (same item). The video own-swap
path calls `onOwnSwapTransition()`, which neutralises a captured end position and nulls `fmt`.

## Coverage

The app has three ExoPlayer instances: `MusicService`'s (covered by the reporter) and the two
status-viewer players (`StoryScreen`, `SavedStatusScreen`), which only play third-party status media and
never a YouTube videoId. Every queue type (`ListQueue`, `YouTubeQueue`, `ZemerRadioQueue`,
`StationQueue`, `LocalAlbumRadio`), Android Auto, the widget, downloads, podcasts and video mode share
the one service player. Zemer radio/stations are Zemer-*selected* but stream DIRECT, so they beacon.

## Hard exclusions (strict gates; nothing fabricated for an excluded play)

- **RELAY mode** - beacons must never ride the relay egress. Gated at session creation via
  `isRelay = { relayModeNow != false }`, so the unresolved cold-start window never beacons.
- **Cast** - the receiver plays (`isCasting`).
- **Offline plays** - no tracking URL; the live session reports nothing, the listen is deferred (below).
- **`PauseListenHistoryKey`** - re-checked PER PING, so enabling it mid-listen silences the rest of the
  session. A watchtime/final ping is never sent for a session whose playback ping was suppressed (the
  `opened` flag is set inside the playback-ping send).

## Fidelity: verified from base.js, never guessed

Only params whose KEY **and** truthful VALUE are both derivable from the live deployed `base.js` are
sent:

- **`fmt=<itag>`** (`n.fmt=y.D.itag`) - the real resolved itag; omitted where unknown.
- **`muted`/`mos`** (`isMuted()?1:0`, `mos == muted`) - our player has no mute separate from volume, so
  `volume <= 0` is the truthful read; captured on the main thread at enqueue time.

**Deliberately NOT sent:** `volume`, `state`, `fs`/`playerheight`/`playerwidth`/`clipid` (no value we
could derive rather than recall), and the traffic-source params `referrer`/`sdetail`/`sourceid` (a
third-party app has no genuine referrer - synthesizing one is fabrication). Adding any param later
requires re-reading base.js for its exact value semantics.

## CDN `cpn` correlation (media request + beacons share one cpn)

The official client stamps its beacon cpn on the googlevideo **media** request too. We do the same:

- **`playback/PlaybackNonceRegistry.kt`** (thread-safe, `PlaybackNonceRegistryTest`) mints ONE cpn per
  listen, keyed by `VideoRendition.baseVideoId` (so audio / `video:` / `videoaudio:` renditions share
  it). Both the stream resolver (`WatchTimeReporter.mediaCpnFor`) and the beacon session
  (`ensureSession`) read it; `finishSession` releases it (fresh cpn per play). It is an access-ordered
  LRU (`MAX_ENTRIES`) whose eviction skips `pin`ned ids, so the live listen's cpn is never evicted.
- **`MusicService.stampCpn`** appends `&cpn=` (`PlaybackNonceRegistry.appendCpn`) at every DIRECT
  googlevideo `withUri` site (audio, video-mode, merge-audio; fresh and cached-URL) - never a local-file
  uri, a pure cache hit, or the RELAY factory. It is appended per fetch, so `songUrlCache` holds the
  unstamped URL. SABR media POSTs carry the same cpn via `MusicService.sabrCpnFor`.
- **Regression gate:** `tests/watchtime-cpn-stream.mjs` drains a real stream with and without `&cpn` and
  must see 206 throughout. Run it whenever this path changes.

Correlation is best-effort: a replay served from the persistent cache fetches nothing from the CDN, and
watch time still credits. Never disable the cache to force per-play delivery.

## What to expect (so the strip isn't mistaken for a bug)

Views are durable. Watch time from **concentrated single-account / one-IP testing is retroactively
stripped** by YouTube's invalid-traffic sweep acting on the traffic *pattern* - a complete official
browser session was stripped identically, so **do not chase it by adding beacons** (qoe / source / atr /
ptracking). The payoff is real distributed users. **Never** route beacons through the relay /
free-proxy egress.

## Deferred offline recovery (`DeferredStatsQueue` - additive, live path untouched)

A downloaded song played with **no network** has no tracking URLs. The listen is captured and re-pushed
on reconnect as a **deferred** session from a fresh `/player`. The rules:

- **Capture happens ONLY in the reporter's offline branch** (`onOfflineListen`), so relay/cast (never a
  session) and online plays never reach it; an online *cached* play resolves fresh URLs and reports live.
- **Same honesty rule.** The queued `st`/`et` are the real ranges `WatchTimeSegments` produced; `cmt` is
  the final position; `rt` is the real watched seconds; the open ping's `cmt` is
  `DeferredStatsRecord.openCmt()` (the first watched range's start, never 0). The only gate is the
  ≥500 ms segment floor - **no minimum-duration gate**, so any genuinely watched offline play mints a
  view on reconnect, like the live path. `PauseListenHistoryKey` suppresses capture with the live path's
  per-ping semantics (paused at start captures nothing; accumulation stops at the first paused ping).
- **No Room, no migration.** `tracking/TrackingQueue` over `filesDir/deferred-stats.jsonl` (cap
  `MAX_SIZE` 500, drop-oldest, atomic rewrite) + `tracking/FlushSchedule` backoff. A corrupt line
  decodes to null and never crashes the flush.
- **Flush:** single-flight, triggered on the connectivity false→true edge, and self-rescheduling
  whenever work remains - after a RETRY it waits out the backoff; after a full batch (`BATCH_SIZE` 20)
  with records still queued it waits `PACE_MS`. So a backlog drains fully AND trickles out instead of
  bursting. Records older than `MAX_AGE_MS` (7 days) are dropped.
- **`pushDeferredStats`** (`DeferredStatsPush.kt`): fresh `/player` + fresh `generateCpn()`, then **the
  watchtime ping fires ONLY after the playback ping is accepted** (a partial failure re-pushes the whole
  record under a new cpn, so an early watchtime would double-count). Classification: playback 400 →
  drop, playback non-2xx → retry; then watchtime 2xx → remove, 400 → drop, else → retry. The 400 is read
  from the thrown `ResponseException` (the client is `expectSuccess`), so DROP is reachable. Never via
  the relay egress.
- A deferred play registers at push time, not original play time (`rt` is relative) - an honest, minor
  analytics skew.

## Regression gate

`WatchTimeSegmentsTest` (deltas, seeks, jitter, pause, backwards correction, format),
`WatchTimeScheduleTest` (offset math), `WatchTimeReporterTest` (the full state machine via the
`PlaybackProbe` fake), `PlaybackNonceRegistryTest` (shared/rotate/append, pinned-eldest LRU),
`DeferredStatsRecordTest` (round-trip / staleness / corrupt line), `DeferredStatsPushTest` (the
classification + watchtime-not-sent-when-open-fails), `DeferredStatsQueueTest` (capture, reconnect
flush, keep-on-retry, drop-when-stale, quiet while offline), plus `tests/watchtime-cpn-stream.mjs`.
