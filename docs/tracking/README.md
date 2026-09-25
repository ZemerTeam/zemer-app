# Tracking — anonymous usage telemetry

Anonymous usage events posted to `https://tracking.zemer.io/v1/events`, the data layer for Zemer's
recommendations. The authoritative wire spec is the handoff doc
`handoff-docs/zemer-tracking-app-integration.md`.

## TL;DR

Six events — `open`, `search`, `play`, `click`, `action`, `impression` — batched into a durable on-disk
queue and POSTed fire-and-forget. Identity is ONE random UUID (`TrackingDeviceIdKey` in DataStore),
nothing else: no account data, no device identifiers, no location. Product decisions: track everything
including KidZone, no opt-out, one `search` event per executed query, offline plays queue and upload late.

## The invariant that rules everything

**Telemetry must never break the app.** Every entry point on `tracking/Tracker.kt` is a cheap
`scope.launch` onto a single-threaded dispatcher; every failure is silent (a `Timber` line at most);
the queue caps at 500 events dropping OLDEST (`TrackingQueue.MAX_SIZE`); a server 400 drops the batch
rather than poison-pilling the queue. Losing events is fine. Breaking playback is not.

## The pieces (all in `com.jtech.zemer.tracking`, pure parts JVM-tested)

- `TrackingEvents.kt` — the wire-event builders + batch body, pinned byte-for-byte by
  `TrackingEventsTest`. `t` = epoch millis at event time.
- `TrackingQueue.kt` — JSONL file queue at `filesDir/tracking/events.jsonl` (deliberately NOT a Room
  table: no schema risk for droppable telemetry). 500-cap drop-oldest, ≤100-event batches
  (`BATCH_SIZE`), corrupt-line tolerant, O(1) appends. After an upload, **`removeBatch` aligns the
  uploaded lines against the head** instead of removing the first N — cap-eviction during an in-flight
  upload must never delete never-uploaded events (regression-tested).
- `TrackingUploader.kt` — one POST per batch; 400 → drop batch, 429 → wait ≥2 min, else backoff
  30 s → 2 min → 10 min (`trackingRetryDelayMs`, tested). `expectSuccess = false` — non-2xx is a mapped
  outcome, never an exception.
- `Tracker.kt` + `FlushSchedule.kt` — the façade + flush loop. Triggers: queue ≥ 20
  (`FLUSH_THRESHOLD`), 60 s with a non-empty queue, app backgrounded. ONE in-flight upload, and
  **every trigger honors the failure backoff** (`FlushSchedule`, tested): a ≥20-event queue during a
  server outage must NOT fire a POST per newly enqueued event. Device id: `UUID.randomUUID()` only —
  **the server 400s any non-canonical UUID**, guarded by `isCanonicalUuid`.
- **Debug builds are server-exempt**: the envelope carries `debug: BuildConfig.DEBUG`; the server ACKs
  a debug batch like production but stores nothing. Debug and release run the IDENTICAL client code
  path — never gate the tracker on `BuildConfig.DEBUG` in the app.
- `TrackingLifecycle.kt` — `open` session semantics via ActivityLifecycleCallbacks (cold start +
  return-to-foreground after >30 min, `SESSION_GAP_MS`; service-only process starts fire nothing) and
  the flush-on-background trigger. Configuration changes transit the started-count through 0 without
  leaving the foreground — `isChangingConfigurations` gates them out of both the flush and the session
  arithmetic — and the gap is measured on monotonic `SystemClock.elapsedRealtime`, never wall clock.
  Registered with `Tracker.initialize` in `App.onCreate`.

## Where each event fires (the wiring)

- **`open`** — `TrackingLifecycle` only.
- **`search`** — `OnlineSearchViewModel`: ONE event per executed query (`searchTracked` guard,
  persisted in the SavedStateHandle so a back-stack entry restored after process death never re-fires),
  on the first successful load; `results` = items shown; zero results sent faithfully; chip switches
  never re-fire. Carries `provider` (Zemer extension,
  `handoff-docs/zemer-tracking-search-provider-request.md`): the pinned constant
  `SEARCH_TRACKED_PROVIDER = "zemer"` (Zemer is the only engine; keep sending the field, the dashboard
  splits on it).
- **`click`** — `OnlineSearchResult`'s single `activate` path (tap AND D-pad select — KeyDown only,
  auto-repeats ignored, so a held Enter is ONE click): the query, tapped id, `kind` (`clickKind()` — a
  `SongItem` reports `video`/`song` from its own `isVideo` flag, not the chip; then `album`, `artist`,
  `podcast`, `episode`, and `community` vs `playlist`), and 0-based rank within the displayed category.
- **`play`** — `MusicService.onPlaybackStatsReady`: one event per listen when it ENDS, however short
  (Media3's `PlaybackStats.totalPlayTimeMs` = accumulated real play time; pauses excluded, seek-backs
  not double-counted; fires on skip/complete/queue-advance and on player release). Zero-play-time
  sessions are skipped — a restored persisted queue opens a stats session without the user pressing
  play, and those phantoms must not count. Downloaded/offline playback and video-mode renditions (an
  in-player swap on the same session) are tracked identically.
- **`impression`** — `TrackImpressionsByKey` (`ImpressionReporter.kt`) on the instrumented rows; see
  below for the definition and surface list (both are contracts).
- **`action`** — central chokepoints only: the four entity `toggleLike()`s (`favorite`/`unfavorite`);
  `MediaStoreDownloadManager` (`download`, fired AFTER the already-downloading/completed no-op check so
  a re-tap that enqueues nothing reports nothing, and only with `fromUser = true` — retries, self-repair
  and auto-download-on-like never report); `DatabaseDao.addSongToPlaylist` (`add_playlist`: a single add
  reports the videoId, a bulk add reports ONE collection-level event with the playlist id — an import
  must not flood the 500-cap queue; playlist SYNC writes maps directly and bypasses it); and the share
  buttons (`share`). Never add per-surface duplicates.

## `play.source` — where a listen started

Set when a queue is built, never per-surface guesswork:

- `Queue.playSource` (default `"other"`) is passed at construction with a `PlaySource` value: search
  taps (`OnlineSearchResult` → `search`), Latest Releases (`LatestReleasePlayback` → `new`), artist
  pages (`ArtistScreen`/`ArtistSectionScreen` → `artist:UC…`), albums (`album:…`, intrinsic to
  `LocalAlbumRadio`), online playlists (`OnlinePlaylistViewModel` + `YouTubePlaylistMenu` →
  `playlist:PL…`, or `community:PL…` for community playlists), curated playlists
  (`ZemerCuratedPlaylistScreen` → `zemer:<slug>`), Zemer Stations (`StationQueue` → `station:<id>`),
  genre tracklist taps (`GenreScreen` → `genre:<slug>`), podcast surfaces (`podcast:<show-id>`, bare
  `podcast` when unknown), and the Home Videos-tab ranked rows (`home:video-trending` / `home:video-new`).
  New queue types/surfaces must declare their source.
- `MusicService.playQueue` registers the chosen items in `Tracker.playSources` (`PlaySourceResolver`,
  tested); `Queue.initialItemsAreContext` distinguishes chosen tracks from a radio queue's autoplay
  fill. **`ZemerRadioQueue` hardcodes the answer** (every single-song tap is a seed-first
  `/radio?kind=song` queue): `initialItemsAreContext = false`, so the FILL is `radio` while the
  preloaded seed (registered from `queue.preloadItem`) keeps the queue's declared source. The menus'
  Start radio rows declare `PlaySource.RADIO`; Home Radio mode (`kind=shuffle`) has no seed, so every
  item is `radio`. Where `YouTubeQueue` survives, only the `RDAMVM` song-radio prefix (or a bare
  videoId) is fill; other RD ids (`RDCLAK5uy_…`, `RDAO…`) are user-chosen contexts. The async
  registration is guarded against a slow-loading queue the user already replaced. **Dashboard note:**
  the seed-first radio taps raise the `radio` share of `play` events by design.
- `Queue.continuationIsContext`: page 2+ of a CHOSEN playlist keeps the context source; only a radio
  queue's pages and `LocalAlbumRadio`'s beyond-the-album `/radio?kind=album` continuation register
  `radio`. Seamless-radio registers only the ADDED items — the current song keeps its source.
- The resolver keeps TWO generations: starting a new queue demotes (not wipes) the old registry,
  because the interrupted listen resolves its source after the new queue registered — otherwise every
  tap-A-then-tap-B listen would misreport `other`. Anything unregistered (manual queue adds, a restored
  persisted queue) resolves `other`.
- Known imprecision: online playlists report `playlist:<id>` unless the surface knows it is community.

## `play.client` / `play.player` — Zemer extensions

(`handoff-docs/zemer-tracking-play-client-fields-request.md`.) `MusicService` records
`PlaybackData.streamClient` (and, for the deciphered web clients in `WEB_STREAM_CLIENTS`,
`CipherDeobfuscator.lastUsedPlayerHash`) at stream-resolution time via `Tracker.onStreamResolved`; the
play event attaches them. Absent for downloaded/local playback. The last resolution per videoId wins.

## `impression` — what the app SHOWED

Plays alone can't tell "everyone chose this" from "everyone was handed this"; impressions are the
denominator the server's exposure dampener divides by.

Wire: `{"type":"impression","t":…,"ids":[…11-char videoIds…],"surface":"home:quick-picks"}`.

**The definition — normative.** An item counts once it is inside the viewport AND has stayed there
~300 ms (`DWELL_MS`):

- **Viewport, not composition.** Compose composes ahead of the visible area; `TrackImpressionsByKey`
  reads `layoutInfo.visibleItemsInfo`.
- **A nested row must also be visible ITSELF.** A `LazyRow` inside a `LazyColumn` item reports its own
  viewport, which says nothing about whether the row is on screen. Callers inside a lazy parent pass
  `parent` + `parentKey` so both viewports are ANDed.
- **No flung-past rows.** The dwell (`collectLatest` + `delay`, restarted by every scroll frame)
  reports only what the user settled on.
- **Matched by list KEY, never by visible index.** Headers, chips and section titles share the index
  space with results; an index-based reporter could report the WRONG videoId under the right surface,
  while a key mismatch can only under-report. Don't add one.
- **Deduped per `(surface, videoId)`** (`Tracker.seenImpressions`, bounded by `SEEN_IMPRESSIONS_MAX` and
  evicted BEFORE the add pass so a call never re-reports its own ids).

Under-counting is the safe direction and over-counting is not: the dampener DOCKS a song for being
widely shown, so a phantom impression silently penalises it. **When in doubt, do not report.**

Client rules that must not regress:

- **Impressions never evict plays.** They are the ONLY event type that may be dropped rather than
  queued: `Tracker.impression` drops them while the backoff window is open and once the queue passes
  half its cap (`IMPRESSION_QUEUE_CEILING`). Both drops are song-independent, so they shrink exposure
  counts without skewing the exposed share.
- **We cap impression ROWS per POST** (`capImpressionRows`, `MAX_IMPRESSION_ROWS_PER_POST` = 500 — the
  server's per-batch limit). The drain is event-counted while the server's limit is row-counted, and a
  server-side truncation would always land on what was queued last — biasing exposure, not thinning it.
- **We chunk at 50 ids ourselves** (`impressionChunks`, `MAX_IMPRESSION_IDS`). The server truncates an
  over-long event by keeping its HEAD, which would over-count the start of every long row.
- **`impressionsDropped` in the 200 body** means a POST exceeded the server's per-batch row limit.
  `TrackingUploader` logs it and nothing retries. **Seeing it from a released build means the row cap
  failed — report it to the tracking maintainer.** Debug batches report truthful counters too, so
  impression batching can be validated without a release build.

**Surfaces instrumented (`TrackingSurface`) — this list is a contract.** The server's declared-surface
gate (`EXPOSURE_REQUIRED_SURFACES`) will not enable the dampener until every declared surface is
reporting, so **renaming a slug reads as a surface disappearing** and re-closes the gate. Treat them as
append-only, and send the tracking maintainer an updated list whenever a release adds one.

| Surface | Where |
|---|---|
| `home:quick-picks` · `home:forgotten-favorites` · `home:keep-listening` · `home:featured-videos` · `home:video-trending` · `home:video-new` | `HomeScreen` — one slug per row, not a flat `home:top` (the Featured Videos hero reports per settled carousel item, `VideoHeroCarousel`) |
| `search` | `OnlineSearchResult` — keyed, not indexed |
| `zemer:<playlist-id>` | `ZemerCuratedPlaylistScreen` — the `auto-*` charts and the curated playlists |
| `genre:<slug>` | `GenreScreen` — the genre detail tracklist |

The `zemer:` surfaces matter most: the dampener corrects for a song being played *because* it sits at
the top of a chart, so leaving that screen uninstrumented would run the exposure-bias loop backwards.

Not yet instrumented (the dampener stays off): artist pages and podcast surfaces
(`TrackingSurface.podcast`/`channel` exist but no screen reports them). Partial coverage is *worse*
than none — it docks the instrumented discovery paths and leaves the rest untouched.

**Declared to the server** (the value of `EXPOSURE_REQUIRED_SURFACES`; a trailing `:` is a prefix match):

```
home:,search,zemer:
```

Declare `zemer:` as a **prefix**, never individual chart slugs — seasonal/yearly charts
(`zemer:auto-year-<YYYY>`) come and go, and a hardcoded slug would read as a surface that stopped
reporting and close the gate. **Pending:** add `genre:` (a prefix) when the tracking maintainer updates
the list — an *undeclared* reporting surface reopens the partial-coverage hole the gate exists to close.

Two things `impression` is NOT:

- **Not a CTR denominator.** `surface` shares an alphabet with `play.source` but not its meaning
  (queue context vs the row looked at); surface-level CTR would need a separate `play.surface` field.
- **Not engagement.** The server never counts impressions toward active users or retention; only its
  distinct-`devices` count is meaningful.

The exposure dampener is currently **off** server-side (`EXPOSURE_DAMPENER` kill switch) and
additionally gated on device coverage plus the declared-surface list. Any other client that ships
impressions must count identically, or the distinct-device denominators diverge.

## Chart movement (`auto-*` playlists)

Not telemetry, but it shares the same "absent means absent" discipline. `/zemer-playlists?id=…` sends
`rank`, `prevRank`, `delta` (positive = climbed), `new`, `reentry` per track and `anchorDate` on the
header; `chartMovementOf` (`search/ZemerSearchModels.kt`; `new`/`reentry` checked before `delta`) turns
them into a `ChartMovement`, and `ChartRankCell` renders the position in its own left column with the
movement beside it: ▲/▼ and how far, `NEW`/`RE` for a debut or return, and nothing when the song held
its place. Climb/fall colours are explicit values (`ui/theme/ChartColors.kt`), not M3 roles — under
dynamic colour `tertiary` can land on red. The triangle's ORIENTATION is the only non-colour cue for a
red/green colour-blind reader, which is why it is sized at `labelMedium`.

- **Absent fields must render NO badge** — not a dash, not a zero, and never a fallback to a
  device-local diff of a previous fetch: movement is a property of the CHART, identical for every user.
  Absence is normal (curated non-chart playlists, `auto-year-<YYYY>`, a too-young rank history, a
  post-formula-change blackout).
- **The position is the server's `rank`** — never the row index, never `prevRank`. The list is filtered
  server- and client-side, so a filtered list shows GAPS (…31, 32, 34…) and **row count does not equal
  the last position**; never derive one from the other.
- **`rank` present is the test for "ranked chart"**, NOT `anchorDate` (a blackout still sends `rank`
  with no badges). No `rank` → no column and no reserved space.
- `anchorDate` labels what the movement is measured against. Deltas are computed pre-filter, so a
  filtered user sees the same `▲3` as everyone else — filtered-out rows must not create fake movement.

## One-time history backfill (`play_backfill`)

Contract: `handoff-docs/zemer-tracking-history-backfill-request.md`. `PlayHistoryBackfill` uploads the
device's LOCAL listen history (the Room `event` table; a cleared history sends nothing) once, as
`play_backfill` events carrying the ORIGINAL listen time (server window now−3y..now+5min). Rules that
must not regress:

- **Bypasses the live queue** (`Tracker.uploadBackfill`) — thousands of rows must never flood the
  500-cap live queue — but SHARES the single-in-flight discipline and the failure backoff with the live
  path.
- **Zone-correct timestamps**: the local history stores wall-clock `LocalDateTime`s, converted with the
  DEVICE ZONE (`historyEventEpochMillis`, tested) — a naive UTC reading drops the freshest hours for
  east-of-UTC users.
- **Bounded against double-counting**: the max event-row id is captured and persisted on the first run;
  rows above it were already reported live and never upload as backfill.
- **Resumable + loss-free + one-shot**: the cursor is the last acked row's autoincrement ID (a timestamp
  cursor skips equal-timestamp rows at batch boundaries), advanced per acked batch; the done-flag ends it
  forever and is checked before the DB is opened. A server-rejected (400) batch advances but is LOGGED
  and counted separately — never silently folded into success.
- **Paced**: `BACKFILL_BATCH_ROWS` (100) per batch, one per `BACKFILL_BATCH_PACE_MS` (3 s) after REAL
  uploads only, started 45 s after launch. The per-batch policy (`planBackfillBatch` + `backfillLine`)
  is pure and tested.

## One-time library-action backfill (`action_backfill`)

Contract: `handoff-docs/zemer-tracking-action-backfill-request.md` (SETTLED). `LibraryActionBackfill`
uploads the currently-liked and currently-downloaded song snapshot once (`SongEntity.liked+likedDate` /
`isDownloaded+dateDownload`) as `action_backfill` events (`kind` = `favorite`|`download` only), reusing
the play backfill's queue bypass, pacing and `historyEventEpochMillis`, with these differences:

- **Resume by acked-line COUNT, not a row cursor.** Snapshot timestamps are NOT stable across attempts
  (a zone change shifts every `t`; `SyncUtils.syncLikedSongs` rewrites `likedDate` to sync time), so server
  dedup cannot absorb a full replay. A persisted acked-line count skips the acked prefix on restart; the
  line order must stay stable (favorites before downloads, both `ORDER BY id`).
- **10-year window, not plays' 3** (`MAX_ACTION_BACKFILL_AGE_MS`): an old `likedDate` on a still-liked
  song is a long-standing favorite, not stale data. Don't "fix" it back.
- **Downloads are a weak signal by contract**: the snapshot can't reconstruct `fromUser`, so machine
  downloads are included and the server weights backfilled `download` as corroboration only.
- 90 s start delay (load spreading, NOT an ordering guarantee — serialization comes from
  `Tracker.uploadBackfill`); done-flag checked before any DB work and written immediately after the
  last ack (pacing sleeps only BETWEEN batches, so a process kill can't discard a completed drain).
  `actionBackfillLine` + `actionBackfillLines` are pure and tested.

## Verifying a build

`https://tracking.zemer.io/stats?key=<KEY>&days=1` or the dashboard at `https://tracking.zemer.io` (ask
for the stats key): a 5 s skip bumps `plays` but not `qualifiedPlays`; a gibberish search shows under
zero-result searches within ~a minute of a flush. The `PlaybackStatsListener`/lifecycle layers need a
device (no Robolectric); everything else is covered by `app/src/test/.../tracking/`.
