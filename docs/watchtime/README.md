# Watch-time reporting - emulating a genuine YouTube Music playback-stats session

Hand-authored docset for the playback-stats emulation: every DIRECT Zemer play sends the same
view + watch-time signals a real YouTube Music (WEB_REMIX) web session sends, so real user plays
give the artist maximal *legitimate* credit (Studio watch time, retention, YPP qualified watch
hours). Every claim below cites the source in this repo or the live capture that proves it.

## TL;DR

Before this, the app fired **exactly one** view beacon per listen (`videostatsPlaybackUrl`) at
listen END, with a fresh random `cpn`, and never sent watch time. Now every direct play runs a full
stats session: **one `cpn` per listen**, a **playback ping at play START**, **watchtime pings** at
the server's scheduled cadence plus on pause/seek carrying the **really-watched** segments, and a
**`final=1`** ping at end. Covers music, video-songs and podcast episodes. **Confirmed on a live
channel**: a direct play credits both a real view AND real watch time in Studio (14 views / 0.2 h on
a test video). Crediting does **not** require CDN-cpn correlation. Views are
durable; watch time from concentrated single-account testing is **retroactively stripped** as
invalid traffic - expected, not a bug (see "what to expect" for why, and why real distributed
users are the payoff).

## The invariant that rules everything

**Watch time reported MUST equal what the user actually played.** Fabricated watch time is invalid
traffic by YouTube's own definition - it gets stripped and can flag the channel. This system
*completes* the signals a real play already generates; it manufactures nothing. Every reported range
comes from real player positions; a paused player accrues nothing; a seek is never watched time.

Second invariant, inherited from tracking: **telemetry must never break playback.** Beacons are
fire-and-forget on the service scope; a network failure logs at most a `Timber.d` line and moves on.

## The model (what the official WEB_REMIX client does, captured live)

The `/player` response's `playbackTracking` (parsed in `PlayerResponse.PlaybackTracking`) drives it.
A live WEB_REMIX capture shows these keys:

| key | what it is | do we send it |
|-----|-----------|---------------|
| `videostatsPlaybackUrl` | **playback** ping - opens the stats session at START | **yes** |
| `videostatsWatchtimeUrl` | **watchtime** ping - periodic + final, carries watched segments | **yes** |
| `videostatsScheduledFlushWalltimeSeconds` / `videostatsDefaultFlushIntervalSeconds` | the flush cadence (live: `[10,20,30]` then `40`) | **yes** - drives our ticker |
| `atrUrl` | ad telemetry | no (no ads served) |
| `ptrackingUrl` | one-shot playback tracking | no (reproducible; zero survival benefit) |
| `qoeUrl` | plain timestamped ExoPlayer telemetry (bandwidth, buffer, media-time, state, battery) | **no - reproducible but zero survival benefit** (see "what to expect") |
| `videostatsDelayplayUrl` | fired only when playback was delayed at start | no (not a normal-path beacon) |

All beacon params ride `ver=2&c=WEB_REMIX&cpn=…`, `s.youtube.com`→`music.youtube.com` host swap, with
the WEB_REMIX headers + SAPISIDHASH via the shared `InnerTube.ytClient`. Base URLs (already carrying
`docid`, `ei`, `len`, `plid`, `of`, `ns`, `el`, `cl`, …) come straight from the `/player` response.

## The pieces

- **`playback/WatchTimeSegments.kt`** (pure, JVM-tested `WatchTimeSegmentsTest`) - accumulates the
  media-time ranges actually played and drains them as the `st`/`et` lists of a ping. Drains are
  **deltas** (each ping carries only newly watched ranges, like the official client), seeks close at
  the departed position and reopen at the target, a backwards jump without a seek closes rather than
  fabricates, sub-`MIN_SEGMENT_MS` (500ms) jitter is dropped. Seconds format `%.1f` (Locale.US).
- **`playback/WatchTimeSchedule.kt`** (pure, JVM-tested `WatchTimeScheduleTest`) - the flush cadence.
  `flushOffsetMs(index)` returns the wall-clock offset of the index-th flush: the server's scheduled
  seconds first, then the last scheduled offset plus multiples of the default interval. Falls back to
  the base.js `klA` default `[10,20,30]`/`40` when the response omits the fields.
- **`playback/WatchTimeReporter.kt`** - the session owner (the `EpisodePositionTracker` extraction
  pattern). Session state is confined to the service main scope; one **ordered ping channel** per
  session so the playback ping always precedes its watchtime pings; the tracking-URL cache is the one
  concurrent piece (seeded from the data-source resolver thread). One `WatchTimeSchedule` per session;
  a `scheduledFlushCount` advances the wall-clock ticker (pause/seek pings are extra and never touch
  it). Beacons are fire-and-forget via `YouTube.registerPlayback` / `registerWatchtime`.
- **`playback/PlaybackProbe.kt`** - the read-only `Player` slice the reporter needs (position,
  isPlaying, playbackState, playWhenReady, currentMediaId, hasCurrentMetadata, volume). Decouples the
  reporter from `androidx.media3` so its whole state machine is JVM-tested with a pure fake;
  `MusicService` adapts the real `Player`, and `onPositionDiscontinuity` takes primitive params.
- **`innertube` `YouTube`/`InnerTube`** - `generateCpn()` (16 chars), `registerWatchtime(...)`, and
  optional `cmt`/`final`/`fmt`/`muted` on `registerPlayback` (all additive; legacy callers unchanged).

## MusicService wiring (event forwarding only)

`MusicService` forwards player events; the reporter holds all the logic:

- `onIsPlayingChanged` → open/continue the session, start/stop the scheduled ticker.
- `onPositionDiscontinuity` → a same-item seek closes+reopens the segment and fires a state-change
  ping (but a rendition-swap's position-continuous seek, delta < 1s, fires no spurious ping - the
  swap stays transparent). Any AUTO_TRANSITION - a track boundary OR a **repeat-one loop** back to the
  same item, which wraps position to ~0 - captures the departed item's REAL end position for the final
  ping, so a looped song's tail is not under-reported.
- `onMediaItemTransition` → a REAL track change ends the departed session (`final=1`) and arms the
  next. Placed **after** the video-mode own-swap early-return, so an audio↔video swap keeps its
  session (same listen, one cpn).
- `STATE_ENDED` → the last queue item ran out (no transition fires): send the final ping.
- `onDestroy` → best-effort final ping.
- The stream resolver hands `onTrackingResolved(mediaId, playbackTracking, itag)` so the session opens
  with **no extra `/player` round-trip** and `fmt` carries the real streamed itag; cached/local plays
  fall back to one light metadata fetch (the legacy ping's own behavior).

The legacy end-of-listen `registerPlayback` call was **removed** - keeping it would double-report the
session. Do not reintroduce it.

## Coverage (independently audited - zero gaps)

There are exactly **three** ExoPlayer instances in the app: the one in `MusicService` (fully covered
by the reporter) and two **status-viewer** players (`StoryScreen`, `SavedStatusScreen`) that carry
only third-party JewishStatus/YidStatus content and **never** touch a YouTube videoId - they can
never beacon. Every queue type (`ListQueue`, `YouTubeQueue`, `ZemerRadioQueue`, `StationQueue`,
`LocalAlbumRadio`), Android Auto, the home widget, cast, downloads, podcasts and video-mode share the
one MusicService player. Zemer stations/radio are DIRECT (the *selection* is Zemer-served; the audio
stream is still InnerTube + cipher), so they beacon.

## Hard exclusions

- **RELAY mode** (`stream.zemer.io`) - beacons must never ride the relay egress (spec rule + our test
  showed relay beacons don't even register). Gated at session creation (`isRelay`), fail-safe on the
  unresolved cold-start window (`relayModeNow != false`, so an unknown relay state never beacons).
- **Cast** - the receiver plays, not this device. Gated (`isCasting`).
- **Offline local plays** - no tracking URL resolvable; the LIVE session reports nothing, but the
  listen is not lost: it is queued and re-pushed on reconnect (see §Deferred offline recovery).
- **Listen-history paused** (`PauseListenHistoryKey`) - silences beacons, re-checked PER PING so
  enabling the switch mid-listen silences the rest of the in-flight session too.

Every exclusion is a strict gate; nothing is fabricated for an excluded play.

## Fidelity: verified-from-data only, never guessed

The extra params the official client carries were sourced by reading the **live deployed** player
`base.js` `Y2`/`qT` builders (fetched from `music.youtube.com`), not from memory. Only params whose
KEY **and** truthful VALUE are both derivable are sent:

- **`fmt=<itag>`** - base.js `n.fmt=y.D.itag`; the real resolved itag. Omitted on cached/local plays
  where it is unknown, never fabricated.
- **`muted`/`mos`** - base.js `isMuted()?1:0`, `mos == muted`. Our player has no mute separate from
  volume, so `player.volume <= 0` is the truthful read; captured on the main thread at enqueue time.
- **Flush cadence** - server-provided (see `WatchTimeSchedule`).

**Deliberately NOT sent** (value only obtainable from memory, which would be guessing): `volume`
(scale is a mangled method in base.js), `state` (the `state=` value strings could not be pinned to
literals in the minified source), `fs`/`playerheight`/`playerwidth`/`clipid` (no truthful value for an
audio service). Adding any of these later requires re-reading base.js for its exact value semantics.

## CDN `cpn` correlation (media request + beacons share one cpn)

The official client stamps `cpn=${videoData.clientPlaybackNonce}` on the googlevideo **media**
request (confirmed in base.js), using the SAME cpn as its beacons - so YouTube can tie the reported
watch time to real byte delivery. We do the same:

- **`playback/PlaybackNonceRegistry.kt`** (thread-safe, JVM-tested) mints ONE cpn per in-flight
  listen, keyed by **base videoId** (`VideoRendition.baseVideoId` collapses audio / `video:` /
  `videoaudio:` rendition keys to one id, so a video-mode swap shares the listen's cpn). Called from
  BOTH the stream resolver (a background thread - via `WatchTimeReporter.mediaCpnFor`) and the beacon
  session (`ensureSession`), so both use the same value. `finishSession` **releases** the id, so the
  next play mints a fresh cpn - the client's fresh-cpn-per-playback model, which keeps view counts
  incrementing on repeat.
- **`MusicService.stampCpn`** appends `&cpn=<cpn>` (`PlaybackNonceRegistry.appendCpn`) to the
  googlevideo URL at every DIRECT stream `withUri` site - the audio, video-mode, and merge-audio
  branches, both fresh-resolution and cached-URL. It is applied ONLY to googlevideo URLs: never a
  downloaded local-file uri, never a pure cache hit (no fetch), never the RELAY factory (its own
  resolver never calls `mediaCpnFor`). The cpn is appended per-fetch (the `songUrlCache` stores the
  base URL), so it never pollutes the cached value.
- **Regression gate:** `tests/watchtime-cpn-stream.mjs` resolves a real stream URL the app's exact way
  (cipher + poToken, WEB_REMIX) and drains the whole file in sequential ranges, once without and once
  WITH `&cpn` - proving the cpn-stamped URL streams with 206s throughout (no HTTP rejection),
  identical to the control. Run it whenever this path changes.

Correlation is best-effort by design: on a FRESH play the media is fetched under the listen's cpn, so
the beacons correlate; on a REPEAT from the persistent cache no CDN fetch happens, so the fresh
session's beacons carry a cpn with no new delivery - the same as before, and watch time still credits
(proven on the live channel). Disabling the cache to force per-play delivery would regress playback
and is deliberately not done.

## Reproduced, evaluated, and deliberately rejected

Every remaining official-session signal was captured live and confirmed **fully reproducible** - there is no cryptographic wall anywhere in the stats protocol. They are omitted on purpose, each
for a stated reason, not because they can't be produced:

- **`qoe`** (`/api/stats/qoe`) - **not** an opaque/signed blob (the earlier "cannot produce
  truthfully" reading was wrong). It is plain timestamped ExoPlayer telemetry - bandwidth
  estimate/measured, buffer health, media-time samples, player state, battery - all honestly
  derivable. Added in a controlled A/B and gave **zero** watch-time-survival benefit. Skip.
- **Traffic-source params** (`referrer`, `sdetail`, `sourceid`) - populate Studio "Top sources", but
  a third-party app has no genuine web referrer; synthesizing them is fabrication and also gave no
  survival benefit. Skip.
- **`atr`** (ad telemetry) - no ads served. Skip.
- **`ptracking`** - reproducible, no benefit. Skip.
- The `vm` token and every other `/player`-baseUrl param are already carried for free by firing from
  the baseUrl - no synthesis needed.
- **Never** route any beacon through the relay / free-proxy egress: login-less free-proxy beacons
  don't even register, and concentrating them would burn the pool.

So the honesty invariant (§"The invariant that rules everything") is the floor, and it costs nothing
here: the rejected signals are honest AND reproducible, they just don't move the outcome. The real
ceiling is not producibility - it is YouTube's invalid-traffic sweep on the traffic *pattern*, below.

## What to expect (so the strip isn't mistaken for a bug)

From a controlled A/B on a test channel. Read this before concluding the system is broken:

- **Views count and stick.** Direct plays register real, durable views (measured net **+708** across
  a 92-vs-92 A/B; held, not stripped).
- **Watch time from concentrated single-account / one-IP testing gets RETROACTIVELY STRIPPED to
  0.0 h.** This is the invalid-traffic sweep acting on the *pattern* - one viewer replaying obscure
  videos on a history-less channel - **not** a defect in this system. Proof: a **complete** official
  session (these beacons *plus* qoe *plus* source params, from a real browser on a residential IP)
  was stripped **identically** to the reduced set. More beacons do not change the outcome. ⇒ Testing
  this from a single account, you will see watch time appear in Studio then vanish on the sweep
  (public view counts may hold while Studio Analytics validates them away). **That is expected - do
  not chase it by adding beacons.**
- **The payoff is real distributed users.** Real Zemer traffic is thousands of distinct devices/IPs
  each playing once - the opposite of the swept pattern - where the honest watch-time signal is
  expected to survive. This can't be proven externally (manufacturing distinct real viewers is the
  fraud we refuse); it is the design intent and the reason to ship.
- **The final monetization binary** - whether surviving real-user watch time actually earns a
  pro-rata music-pool share - is the one thing YouTube doesn't publish; only a rights-holder royalty
  report settles it.

Bottom line: ship the direct path as-is. No further beacons (qoe / source / atr / ptracking) are
warranted, and the retroactive strip under single-account testing is expected behavior, not a bug.

## Deferred offline recovery (`DeferredStatsQueue` - additive, live path untouched)

The live session above needs `/player` tracking URLs, so a downloaded song played with **no network**
fires nothing - that listen would contribute 0 views and 0 watch time. This path recovers it by pushing
a **deferred** stats session from a fresh `/player` once the device reconnects. A deferred session is
accepted at ingestion (proven: fresh WEB_REMIX `/player` → new cpn → playback + `final=1` watchtime pings,
both `204`), because the fresh response's `ei`/`plid`/`vm` tokens have no expiry problem.

The rules that must not regress:

- **The live DIRECT path is untouched.** Capture happens ONLY in the reporter's existing offline branch
  (the "no tracking URLs" case that used to do nothing) - so relay/cast (never a session) and online plays
  (report live) never reach it. An online *cached* play resolves fresh URLs via `fetchTracking` and reports
  live; only a genuinely unreachable play is deferred.
- **Honesty is the same hard rule.** The queued `st`/`et` are the SAME real ranges `WatchTimeSegments`
  computed for the listen (accumulated from the pings, `WatchTimeSegments.Drained.watchedMs` summed); `cmt`
  is the final position, `rt` the total real watched seconds (≤ played time ≤ duration). Nothing is
  re-derived or fabricated. Gated at the ≥10s genuine-play threshold (`MIN_DEFERRED_MS`), and **the
  privacy switch (`PauseListenHistoryKey`) suppresses capture with the SAME per-ping semantics as the
  live path** - nothing is captured if history was paused at the Start ping, and accumulation stops at
  the first paused ping, so a mostly-private listen is never queued even if unpaused before it ends (a
  deferred beacon is still a beacon).
- **No Room, no migration.** Durability reuses `tracking/TrackingQueue` (JSONL under `filesDir`,
  `deferred-stats.jsonl`, cap 500, drop-oldest, atomic rewrite); backoff reuses `tracking/FlushSchedule`.
  `DeferredStatsRecord` is the serialized row; a corrupt line decodes to null (never crashes the flush).
- **Flush is single-flight, connectivity-gated, staleness-capped, self-rescheduling.** Triggered on
  reconnect (the `connectivityObserver` false→true edge); the reconnect edge is the ONLY external
  trigger, so the flush self-reschedules whenever work remains: after a RETRY it waits out the backoff,
  and after a full batch (`BATCH_SIZE` = 20) drains with records still queued it waits a short **pace**
  (`PACE_MS`) before the next batch. That both **fully drains a backlog larger than one batch on a
  stable connection** (otherwise it would stall past 20 until the next reconnect) AND **trickles the
  beacons out instead of firing the whole backlog as one burst** (the right mitigation for a
  long-offline reconnect - pace it, don't drop it; the 7-day cap already bounds how big the backlog
  gets). Per record, past a **7-day** staleness drop, `pushDeferredStats` does a fresh `/player`
  (reusing `fetchTracking`) + a fresh `generateCpn()` (no media to correlate), then **the watchtime ping
  fires ONLY after the playback (session-open) ping is accepted** - a watchtime with no preceding
  playback ping is the orphan shape, and on a partial failure the whole record is re-pushed under a
  fresh cpn, so beaconing watch time before the open succeeds would double-count. Classification:
  playback 400 → drop, playback not-2xx → retry (watchtime NOT sent); after playback 2xx, watchtime
  2xx → remove, 400 → drop, else → retry with backoff. A malformed 400 is read from the thrown
  `ResponseException` (the InnerTube client is `expectSuccess`), so DROP is reachable in production, not
  just in tests. **Never via the relay egress** (fired on the device's own direct connection).
- **What to expect** (same as the live path, `docs/watchtime/README.md` "what to expect"): the **view** is
  the realistic win; single-account watch time may still be swept as invalid traffic - the payoff is real
  distributed users each pushing their own offline listen on reconnect (the good pattern). A deferred play
  registers at push time, not original play time (`rt` is relative, no app-set absolute time) - a minor,
  honest analytics skew.
- **Isolation-tested:** `DeferredStatsRecordTest` (round-trip / staleness / corrupt-line), `DeferredStatsPushTest`
  (both-2xx→SUCCESS / playback-400→DROP / else→RETRY / no-tracking→RETRY / watchtime-not-sent-when-open-fails),
  `DeferredStatsQueueTest` (capture, reconnect flush, keep-on-retry, drop-when-stale, quiet while offline),
  and `PlaybackNonceRegistryTest` (LRU eviction skips past a pinned eldest, so the live cpn survives and the
  cap holds).

## Regression gate

`WatchTimeSegmentsTest` (honesty rules: deltas, seeks-not-counted, jitter drop, pause, backwards
correction, format) + `WatchTimeScheduleTest` (the flush offset math) + `PlaybackNonceRegistryTest`
(shared/rotate/append, LRU that survives a pinned eldest) + the `DeferredStats*` tests + **`WatchTimeReporterTest`
(the full state machine, driven through the `PlaybackProbe` fake)**. The reporter reads the player only
through `PlaybackProbe`, so its event/state machine is JVM-testable without Robolectric - the extraction
is behavior-preserving (`MusicService` adapts the real `Player`). Beacon request shapes are replica-verified
against live YouTube (every beacon HTTP 204/204); the added params + the flush schedule are verified against
the live deployed `base.js` and a live `/player` capture. When touching this system, keep every reported
value tied to a real player observation, keep the probe returning exactly the `Player` values, and keep the
coverage/exclusion gates intact.
