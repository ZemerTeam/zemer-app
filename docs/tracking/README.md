# Tracking — anonymous usage telemetry

Hand-authored docset for the tracking integration: anonymous usage events posted to
`https://tracking.zemer.io/v1/events`, the data layer for Zemer's future recommendation algorithm.
The authoritative wire spec is the handoff doc (`~/zemer-fix/handoff-docs/
zemer-tracking-app-integration.md`, mirrored in summary here); every claim below cites the file
that proves it.

## TL;DR

Five events — `open`, `search`, `play`, `click`, `action` — batched into a durable on-disk queue
and POSTed fire-and-forget. Identity is ONE random UUID (`TrackingDeviceIdKey` in DataStore),
nothing else: no account data, no device identifiers, no location; the server stores no IPs.
Decisions made 2026-07-05: track everything including KidZone and the YouTube search engine, no
opt-out, one `search` event per executed query, offline plays queue and upload late.

## The invariant that rules everything

**Telemetry must never break the app.** Every entry point on `tracking/Tracker.kt` is a cheap
`scope.launch` onto a single-threaded dispatcher; every failure is silent (a `Timber` line at
most); the queue caps at 500 events dropping OLDEST (`TrackingQueue`); a server 400 drops the
batch rather than poison-pilling the queue. Losing events is fine. Breaking playback is not.

## The pieces (all in `com.jtech.zemer.tracking`, pure parts JVM-tested)

- `TrackingEvents.kt` — the five wire-event builders + batch body, pinned byte-for-byte by
  `TrackingEventsTest`. `t` = epoch millis at event time. The `play` event carries two Zemer
  extension fields, `client` + `player` (see below).
- `TrackingQueue.kt` — JSONL file queue under `filesDir/tracking/` (deliberately NOT a Room
  table: no schema risk for droppable telemetry). 500-cap drop-oldest, ≤100-event batches,
  corrupt-line tolerant, atomic writes.
- `TrackingUploader.kt` — one POST per batch; 400 → drop batch, 429 → wait ≥2 min, else backoff
  30 s → 2 min → 10 min (`trackingRetryDelayMs`, tested). `expectSuccess = false` — non-2xx is a
  mapped outcome, never an exception.
- `Tracker.kt` — the façade + flush loop. Triggers: queue ≥ 20, 60 s with a non-empty queue, app
  backgrounded. ONE in-flight upload. Device id: `UUID.randomUUID()` only — **the server 400s any
  non-canonical UUID** (verified live), guarded by `isCanonicalUuid`.
- **Debug builds are server-exempt**: the envelope carries `debug: BuildConfig.DEBUG`; the server
  ACKs a debug batch exactly like production (responding `debug:true`) but stores nothing, so test
  devices never pollute the stats. Debug and release run the IDENTICAL client code path — never
  gate the tracker on `BuildConfig.DEBUG` in the app.
- `TrackingLifecycle.kt` — `open` session semantics via ActivityLifecycleCallbacks (cold start +
  return-to-foreground after >30 min; service-only process starts fire nothing) and the
  flush-on-background trigger. Registered with `Tracker.initialize` in `App.onCreate`.

## Where each event fires (the wiring)

- **`open`** — `TrackingLifecycle` only.
- **`search`** — `OnlineSearchViewModel`: ONE event per executed query (the VM is per submitted
  query; `searchTracked` guard), on the first successful load, both engines; `results` = items
  shown; zero results sent faithfully; chip switches and engine toggles never re-fire.
- **`click`** — `OnlineSearchResult`'s single `activate` path (tap AND D-pad select): the query,
  tapped id, `kind` (`clickKind()` — Videos chip → `video`, Community chip → `community`), and
  0-based rank within the displayed category.
- **`play`** — `MusicService.onPlaybackStatsReady`: one event per listen when it ENDS, however
  short (Media3's `PlaybackStats.totalPlayTimeMs` = accumulated real play time; pauses excluded,
  seek-backs not double-counted; fires on skip/complete/queue-advance and on player release =
  app killed). Downloaded/offline playback is tracked identically. NOT yet covered: the separate
  video-player screen's own player (`VideoPlayerScreen`) — known follow-up.
- **`action`** — central chokepoints: the four entity `toggleLike()`s (`favorite`/`unfavorite` —
  every UI path converges there), `DownloadUtil.downloadToMediaStore`/`downloadVideoToMediaStore`
  (`download`, with `fromUser = false` for auto-download-on-like and the missing-file self-repair
  so the signal stays user-intent), `DatabaseDao.addSongToPlaylist` (`add_playlist`, one per song;
  playlist SYNC writes maps directly and correctly bypasses it), and the ten share buttons
  (`share`).

## `play.source` — where a listen started

Set when a queue is built, never per-surface guesswork:

- `Queue.playSource` (default `"other"`) is passed at construction by the surfaces with a spec
  taxonomy value: search taps (`search`), Latest Releases (`new`), artist pages (`artist:UC…`),
  albums (`album:…` — intrinsic to `LocalAlbumRadio`/`YouTubeAlbumRadio`), online playlists
  (`playlist:PL…`), curated playlists (`zemer:<slug>`).
- `MusicService.playQueue` registers the chosen items in `Tracker.playSources`
  (`PlaySourceResolver`, tested); `Queue.initialItemsAreContext` distinguishes an album/playlist's
  tracks (context) from a radio queue's autoplay fill (`YouTubeQueue`: real playlist id = context,
  bare videoId or `RD…` watch playlist = fill → `radio`).
- Continuation pages and seamless-radio adds register as `radio`; anything unregistered (manual
  queue adds, a restored persisted queue) resolves `other`.
- Known imprecision: community playlists can't be distinguished from artist-owned on every path,
  so online playlists report `playlist:<id>` unless the surface knows better.

## `play.client` / `play.player` — Zemer extensions

Requested in `handoff-docs/zemer-tracking-play-client-fields-request.md` (the live ingest already
accepts the fields — verified): `MusicService` records `PlaybackData.streamClient` (and, for the
deciphered web clients in `WEB_STREAM_CLIENTS`, `CipherDeobfuscator.lastUsedPlayerHash`) at
stream-resolution time via `Tracker.onStreamResolved`; the play event attaches them. Absent for
downloaded/local playback. Session-level caveat: the last resolution per videoId wins.

## Verifying a build

`curl 'https://tracking.zemer.io/stats?key=<KEY>&days=1'` or the dashboard at
`https://tracking.zemer.io` (ask for the stats key). Sanity: a 5 s skip bumps `plays` but not
`qualifiedPlays`; a gibberish search shows under zero-result searches within ~a minute of a flush.
The `PlaybackStatsListener`/lifecycle layers need a device — the project has no Robolectric — so
they are verified there; everything else is covered by `app/src/test/.../tracking/`.
