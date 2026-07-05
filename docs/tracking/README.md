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
  corrupt-line tolerant; appends are O(1) file appends (only evictions/removals rewrite). After an
  upload, **`removeBatch` aligns the uploaded lines against the head** instead of removing the
  first N — cap-eviction during an in-flight upload must never delete never-uploaded events
  (review-confirmed data-loss race, regression-tested).
- `TrackingUploader.kt` — one POST per batch; 400 → drop batch, 429 → wait ≥2 min, else backoff
  30 s → 2 min → 10 min (`trackingRetryDelayMs`, tested). `expectSuccess = false` — non-2xx is a
  mapped outcome, never an exception.
- `Tracker.kt` + `FlushSchedule.kt` — the façade + flush loop. Triggers: queue ≥ 20, 60 s with a
  non-empty queue, app backgrounded. ONE in-flight upload, and **every trigger honors the failure
  backoff** (`FlushSchedule`, tested): a ≥20-event queue during a server outage must NOT fire a
  POST per newly enqueued event. Device id: `UUID.randomUUID()` only — **the server 400s any
  non-canonical UUID** (verified live), guarded by `isCanonicalUuid`.
- **Debug builds are server-exempt**: the envelope carries `debug: BuildConfig.DEBUG`; the server
  ACKs a debug batch exactly like production (responding `debug:true`) but stores nothing, so test
  devices never pollute the stats. Debug and release run the IDENTICAL client code path — never
  gate the tracker on `BuildConfig.DEBUG` in the app.
- `TrackingLifecycle.kt` — `open` session semantics via ActivityLifecycleCallbacks (cold start +
  return-to-foreground after >30 min; service-only process starts fire nothing) and the
  flush-on-background trigger. Configuration changes (rotation, theme) transit the started-count
  through 0 without leaving the foreground — `isChangingConfigurations` gates them out of both the
  flush and the session arithmetic — and the gap is measured on monotonic
  `SystemClock.elapsedRealtime`, never wall clock (an NTP step must not fabricate/suppress opens).
  Registered with `Tracker.initialize` in `App.onCreate`.

## Where each event fires (the wiring)

- **`open`** — `TrackingLifecycle` only.
- **`search`** — `OnlineSearchViewModel`: ONE event per executed query (the VM is per submitted
  query; `searchTracked` guard, persisted in the SavedStateHandle so a back-stack entry restored
  after process death never re-fires), on the first successful load, both engines; `results` =
  items shown; zero results sent faithfully; chip switches and engine toggles never re-fire.
- **`click`** — `OnlineSearchResult`'s single `activate` path (tap AND D-pad select — KeyDown
  only, auto-repeats ignored, so a held Enter is ONE click): the query, tapped id, `kind`
  (`clickKind()` — Videos chip → `video`, Community chip → `community`), and 0-based rank within
  the displayed category.
- **`play`** — `MusicService.onPlaybackStatsReady`: one event per listen when it ENDS, however
  short (Media3's `PlaybackStats.totalPlayTimeMs` = accumulated real play time; pauses excluded,
  seek-backs not double-counted; fires on skip/complete/queue-advance and on player release =
  app killed). Zero-play-time sessions are skipped — a restored persisted queue opens a stats
  session without the user pressing play, and those phantoms must not count as listens.
  Downloaded/offline playback is tracked identically. NOT yet covered: the separate video-player
  screen's own player (`VideoPlayerScreen`) — known follow-up.
- **`action`** — central chokepoints: the four entity `toggleLike()`s (`favorite`/`unfavorite` —
  every UI path converges there); `MediaStoreDownloadManager.downloadSong/downloadVideo`
  (`download`, fired AFTER the already-downloading/completed no-op check so a re-tap that enqueues
  nothing reports nothing, and only with `fromUser = true` — retries, self-repair and
  auto-download-on-like never report); `DatabaseDao.addSongToPlaylist` (`add_playlist`: a single
  add reports the videoId, a bulk add (playlist import) reports ONE collection-level event with
  the playlist id per the spec's id rule — a 500-song import must not flood the 500-cap queue;
  playlist SYNC writes maps directly and correctly bypasses it); and the ten share buttons
  (`share`).

## `play.source` — where a listen started

Set when a queue is built, never per-surface guesswork:

- `Queue.playSource` (default `"other"`) is passed at construction by the surfaces with a spec
  taxonomy value — all wired: search taps (`OnlineSearchResult` → `search`), Latest Releases
  (`LatestReleasePlayback` → `new`), artist pages (`ArtistScreen`/`ArtistSongsScreen`/
  `ArtistItemsScreen` → `artist:UC…`), albums (`album:…` — intrinsic to
  `LocalAlbumRadio`/`YouTubeAlbumRadio`, covers `AlbumScreen`), online playlists
  (`OnlinePlaylistScreen` + `YouTubePlaylistMenu` → `playlist:PL…`), curated playlists
  (`ZemerCuratedPlaylistScreen` → `zemer:<slug>`).
- `MusicService.playQueue` registers the chosen items in `Tracker.playSources`
  (`PlaySourceResolver`, tested); `Queue.initialItemsAreContext` distinguishes chosen tracks from
  a radio queue's autoplay fill. Only the `RDAMVM` song-radio watch-playlist prefix (or a bare
  videoId) is fill: other RD ids — YT Music editorial playlists (`RDCLAK5uy_…`), artist shuffle
  (`RDAO…`) — are user-CHOSEN contexts. The async registration is guarded against a slow-loading
  queue the user already replaced.
- `Queue.continuationIsContext`: page 2+ of a CHOSEN playlist keeps the context source (spec:
  tracks continuing from an originally-chosen context keep it); only a radio queue's pages and the
  album radios' beyond-the-album continuation register `radio`. Seamless-radio registers only the
  ADDED items — the current song keeps its source.
- The resolver keeps TWO generations: starting a new queue demotes (not wipes) the old registry,
  because the interrupted listen resolves its source after the new queue registered — otherwise
  every tap-A-then-tap-B listen would misreport `other`. Anything unregistered (manual queue adds,
  a restored persisted queue) resolves `other`.
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
