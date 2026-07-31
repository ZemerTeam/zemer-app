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
Decisions made 2026-07-05: track everything including KidZone, no
opt-out, one `search` event per executed query, offline plays queue and upload late.

## The invariant that rules everything

**Telemetry must never break the app.** Every entry point on `tracking/Tracker.kt` is a cheap
`scope.launch` onto a single-threaded dispatcher; every failure is silent (a `Timber` line at
most); the queue caps at 500 events dropping OLDEST (`TrackingQueue`); a server 400 drops the
batch rather than poison-pilling the queue. Losing events is fine. Breaking playback is not.

## The pieces (all in `com.jtech.zemer.tracking`, pure parts JVM-tested)

- `TrackingEvents.kt` — the wire-event builders + batch body, pinned byte-for-byte by
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
  items shown; zero results sent faithfully; chip switches never re-fire. `provider` is the pinned
  constant `"zemer"` since the YouTube engine's removal (the wire contract still accepts both values).
  Carries `provider` (Zemer extension, `handoff-docs/zemer-tracking-search-provider-request.md`):
  `"zemer"` or `"youtube"` = the engine that served the query (`SearchProvider.name.lowercase()`),
  so the dashboard separates a real whitelist-expansion demand gap from a legacy YouTube-path zero.
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
- **`impression`** — `TrackImpressionsByKey` on the instrumented rows; see the `impression` section
  below for the definition and the surface list (both are contracts).
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
  `LocalAlbumRadio`, covers `AlbumScreen` and the album long-press menu), online playlists
  (`OnlinePlaylistScreen` + `YouTubePlaylistMenu` → `playlist:PL…`), curated playlists
  (`ZemerCuratedPlaylistScreen` → `zemer:<slug>`). The album radios now continue beyond the album on
  Zemer `/radio?kind=album` (`LocalAlbumRadio`), not `YouTube.next()` — the source semantics are
  unchanged (album tracks = context, continuation = `radio`).
- `MusicService.playQueue` registers the chosen items in `Tracker.playSources`
  (`PlaySourceResolver`, tested); `Queue.initialItemsAreContext` distinguishes chosen tracks from
  a radio queue's autoplay fill. **`ZemerRadioQueue` hardcodes the answer** (the majority path now —
  every single-song tap is a seed-first `/radio?kind=song` queue): `initialItemsAreContext = false`,
  so the FILL is `radio` while the preloaded seed (registered from `queue.preloadItem`) keeps the
  queue's declared surface source. The menus' Start radio rows declare `PlaySource.RADIO` outright;
  Home Radio mode (`kind=shuffle`) has no seed, so every item is fill → `radio`. The legacy
  `YouTubeQueue` heuristic still applies where that queue survives: only the `RDAMVM` song-radio
  watch-playlist prefix (or a bare videoId) is fill; other RD ids — YT Music editorial playlists
  (`RDCLAK5uy_…`), artist shuffle (`RDAO…`) — are user-CHOSEN contexts. The async registration is
  guarded against a slow-loading queue the user already replaced. **Dashboard note:** converting the
  single-song taps to radio queues shifted the `radio` share of `play` events UP by design.
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

## `impression` — what the app SHOWED

Plays alone can't tell "everyone chose this" from "everyone was handed this": a song on the home
screen's top row is played by more devices *because it was shown to every device*. Impressions are
the denominator that lets the ranking side divide the two (contract settled 2026-07-24 with the
tracking maintainer; server shipped).

Wire: `{"type":"impression","t":…,"ids":[…11-char videoIds…],"surface":"home:quick-picks"}`.

**The definition of an impression — normative, and the reason the numbers mean anything.** An item
counts once it is inside the viewport AND has stayed there ~300 ms:

- **Viewport, not composition.** Compose composes ahead of the visible area, so "composed" is not
  "seen". `TrackImpressionsByKey` reads `layoutInfo.visibleItemsInfo`.
- **A nested row must also be visible ITSELF.** A `LazyRow` inside a `LazyColumn` item reports its
  own viewport, which says nothing about whether the row is on screen — and the parent composes an
  item or so past its own viewport. Callers inside a lazy parent pass `parent` + `parentKey` so both
  viewports are ANDed. Forgetting this reports a screenful of songs the user never reached.
- **No flung-past rows.** The dwell (`collectLatest` + `delay`, restarted by every scroll frame)
  reports only what the user settled on.
- **Matched by list KEY, never by visible index.** There is deliberately no index-based reporter:
  headers, chips and section titles share the index space with results, and an index-based variant
  would additionally require the caller to pass a list identical to the one it renders — an
  invariant nothing can enforce, whose violation reports the WRONG videoId under the right surface.
  A key mismatch can only under-report, which the dampener treats as conservative.
- **Deduped per `(surface, videoId)` for the process lifetime** (`Tracker.seenImpressions`). The
  server tolerates repeats — it aggregates distinct devices — but scroll jitter, recomposition and
  back-navigation would otherwise re-report the same row forever, which is what actually consumes
  the per-POST cap and the queue.

Under-counting is the safe direction and over-counting is not: the dampener DOCKS a song for being
widely shown, so a phantom impression silently penalises it. **When in doubt, do not report.**

Client rules that must not regress:

- **Impressions never evict plays.** They are the ONLY event type that may be thrown away rather
  than queued: they share the one 500-event drop-oldest queue with plays and arrive an order of
  magnitude more often. `Tracker.impression` drops them outright while the backoff window is open
  and once the queue passes half its cap. The loss is song-independent (it tracks server health, not
  what was on screen), so it shrinks exposure counts without skewing the exposed/instrumented share
  the dampener divides by.
- **We cap impression ROWS per POST** (`capImpressionRows`). The drain is event-counted (up to 100
  events) while the server's limit is row-counted, so an uncapped scroll-heavy queue could POST
  thousands of rows and have the tail dropped. That loss would NOT be song-independent — it always
  lands on whatever was queued last, i.e. whatever the user scrolled to most recently — so it would
  bias exposure rather than thin it, contradicting the argument the drop policy rests on. A non-zero
  `impressionsDropped` from a released build therefore means THIS cap failed, not merely that a big
  batch was sent.
- **We chunk at 50 ids ourselves** (`impressionChunks`). The server truncates an over-long event by
  keeping its HEAD, which would over-count the start of every long row and under-count its tail.
- **`impressionsDropped` in the 200 body** means a POST carried more impression rows than the server
  stores per batch (500, event-aligned — an event that doesn't fit is dropped whole).
  `TrackingUploader` logs it and nothing retries: the fix is smaller batches or a raised ceiling,
  and neither belongs in a fire-and-forget path. **Seeing it in normal use is a bug to report to the
  tracking maintainer.** Debug builds run the real ingest in a rolled-back transaction, so both
  counters are truthful there — impression batching can be validated without a release build.

**Surfaces instrumented (`TrackingSurface`) — this list is a contract.** The server's declared-
surface gate (`EXPOSURE_REQUIRED_SURFACES`) will not enable the dampener until every declared
surface is reporting, so **renaming a slug reads as a surface disappearing** and re-closes the gate.
Treat them as append-only, and send the tracking maintainer an updated list whenever a release adds
one.

| Surface | Where |
|---|---|
| `home:quick-picks` · `home:forgotten-favorites` · `home:keep-listening` · `home:trending` · `home:featured-videos` | `HomeScreen` — one slug per row, not a flat `home:top` |
| `search` | `OnlineSearchResult` — keyed, not indexed: chips and section titles share the index space with results |
| `zemer:<playlist-id>` | `ZemerCuratedPlaylistScreen` — the `auto-*` charts and the curated playlists |

The `zemer:` surfaces matter most of all: the dampener exists to correct for a song being played
*because* we put it at the top of Trending, so leaving that screen uninstrumented would dock
home- and search-surfaced songs while the chart's own picks accrued no exposure at all — the
exposure-bias loop running backwards.

Not yet instrumented, and the reason the dampener stays off: artist pages, mood/genre, charts.
Partial surface coverage is *worse* than none — it docks the instrumented discovery paths and
leaves the rest untouched.

**Declared to the server for this release** (the value of `EXPOSURE_REQUIRED_SURFACES`; a trailing
`:` is a prefix match on their side):

```
home:,search,zemer:
```

Declare `zemer:` as a **prefix**, never the individual chart slugs — `zemer:auto-year-<YYYY>` rolls
over every January, and `zemer:auto-acapella-top-50` exists only during the Three Weeks. A hardcoded
slug for either would read as a surface that stopped reporting and close the gate for the rest of
the year.

Whenever a release instruments a new surface, send the tracking maintainer the updated list — until
every declared surface reports ≥10 devices the dampener stays gated, and an *undeclared* surface is
worse: it silently reopens the partial-coverage hole the gate exists to close.

## Chart movement (`auto-*` playlists)

Not telemetry — the other direction — but it shares the same "absent means absent" discipline, so
it lives next to it. `/zemer-playlists?id=…` sends `prevRank`, `delta` (positive = climbed), `new`,
`reentry` per track and `anchorDate` on the header; `chartMovementOf` turns them into a
`ChartMovement`, and `ChartRankCell` renders the position in its own left column with the movement
beside it: a triangle and how far it moved, `NEW`/`RE` for a debut or return, and nothing at all
when the song held its place.

The marker is drawn only on rows that actually moved — an unchanged row stays empty, so a 50-row
chart shows markers only where something happened. Climb/fall colours are explicit values rather
than M3 roles (`ui/theme/ChartColors.kt`): under dynamic colour `tertiary` follows the wallpaper and
can land on red, which would be actively wrong for a climb. Since both markers share one slot, the
triangle's ORIENTATION is the only non-colour cue a red/green colour-blind reader has — which is why
it is sized at `labelMedium` rather than the smallest role available.

- **Absent fields must render NO badge** — not a dash, not a zero, and never a fallback to a
  device-local diff of a previous fetch. Movement is a property of the CHART: two users opening the
  same chart on the same day must see the same arrows, which a local snapshot cannot guarantee (a
  week-dormant device would diff against week-old data, a fresh install against nothing).
- Absent is normal in four cases: curated non-chart playlists; `auto-year-<YYYY>` (a dynamic rule
  computed at read time, never a ranked chart, so it has no previous ordering); a rank history too
  young; and the window after a ranking-formula change, where the server drops the baseline on
  purpose so a reshuffle isn't rendered as a real surge.
- **The position is the server's `rank`** — never the row index, and never `prevRank` (which is
  where the song *was*). Our list is filtered both server-side and client-side, so a row index would
  disagree with the `delta` beside it, which is measured against the unfiltered chart.
- **A filtered list therefore shows GAPS** — …31, 32, 34… — because a filtered-out row's position is
  left empty rather than absorbed. That is correct: it is a chart position, not a line number.
  Consequently **row count does not equal the last position**; never derive one from the other.
- **`rank` present is the test for "ranked chart"**, NOT `anchorDate`. The server sends a position
  whenever a stored ordering exists, including during a post-formula-change blackout when there are
  no badges at all — the chart is still a chart. Curated playlists and `auto-year-<YYYY>` have no
  stored ordering, so no `rank`, so no column and no reserved space.
- Arrows change **weekly** (Sunday), though chart data refreshes twice daily. `anchorDate` labels
  the comparison so the header can say what the movement is measured against.
- Deltas are computed pre-filter, so a user with content filters on sees the same `▲3` as everyone
  else even though rows are missing from their list. Deliberate: filtered-out rows must not shift
  everyone below them into fake movement.

Two things this is NOT:

- **Not a CTR denominator.** `surface` shares an alphabet with `play.source` but not its meaning —
  `play.source` is the queue context that got played, this is the row the user looked at. Home taps
  can never report a `home:*` source and radio plays have no impression at all, so surface-level CTR
  was dropped from scope; it would need a separate `play.surface` field.
- **Not engagement.** The server never counts impressions toward active users, new devices or
  retention. Server-side `n` is "distinct rows seen per session", NOT a render count — only
  `devices` is meaningful.

The exposure dampener is currently **off** server-side (`EXPOSURE_DAMPENER`, a permanent kill
switch) and additionally gated on device coverage ≥60% plus the declared-surface list above. If the
diverged Metrolist-fix sibling ever ships impressions it must count identically, or the
distinct-device denominators silently diverge.

## One-time history backfill (`play_backfill`)

The recommender's warm start (contract: `handoff-docs/zemer-tracking-history-backfill-request.md`,
shipped server-side): `PlayHistoryBackfill` uploads the device's LOCAL listen history (the Room
`event` table — listens over the user's history threshold; a cleared history sends nothing) once,
as `play_backfill` events carrying the ORIGINAL listen time. The server stores them unclamped
(now−3y..now+5min), segregated from live plays and dashboards, deduped on (device, videoId, t).
Client rules that must not regress:

- **Bypasses the live queue** (`Tracker.uploadBackfill`) — thousands of rows must never flood the
  500-cap live event queue — but SHARES the single-in-flight discipline and the failure backoff
  with the live path: a rate-limited server is never poked by backfill mid-ladder.
- **Zone-correct timestamps**: the local history stores wall-clock `LocalDateTime`s, converted
  with the DEVICE ZONE (`historyEventEpochMillis`, tested) — the naive UTC reading shifted every
  `t` by the zone offset and silently dropped the freshest hours for east-of-UTC users.
- **Bounded against double-counting**: the max event-row id is captured and persisted on the first
  run; rows above it were already reported live and never upload as backfill.
- **Resumable + loss-free + one-shot**: the cursor is the last acked row's autoincrement ID (a
  timestamp cursor skips equal-timestamp rows at batch boundaries), advanced per acked batch; the
  done-flag ends it forever and is checked before the DB is ever opened. Server dedup makes a
  replayed boundary batch harmless. A server-rejected (400) batch advances but is LOGGED and
  counted separately — never silently folded into success.
- **Paced**: ≤100-row batches, one per 3 s after REAL uploads only (all-filtered pages advance
  without sleeping), started 45 s after launch. The per-batch policy (`planBackfillBatch` +
  `backfillLine`) is pure and tested; the remaining shell is a thin DataStore/loop wrapper,
  verified on-device.

## One-time library-action backfill (`action_backfill`)

The favorites/downloads companion (contract: `handoff-docs/zemer-tracking-action-backfill-request.md`,
SETTLED and built server-side): `LibraryActionBackfill` uploads the currently-liked and
currently-downloaded song snapshot once — `SongEntity.liked+likedDate` / `isDownloaded+dateDownload`
— as `action_backfill` events (`kind` = `favorite`|`download` only; the other action kinds have no
durable timestamp). It reuses the play backfill's queue-bypass, pacing (100-row batches / 3 s), and
zone-correct conversion (`historyEventEpochMillis` — same regression class), with the differences
the contract settled:

- **Snapshot, not a log; resume by acked-line COUNT, not a row cursor.** The snapshot's timestamps
  are NOT stable across attempts — a device-zone change shifts every converted `t`, and
  `SyncUtils.likedSongs` rewrites every synced song's `likedDate` to sync time — so server dedup on
  (device, kind, id, t) canNOT be relied on to absorb a full-snapshot replay. A persisted acked-line
  count skips the acked prefix on restart (the line order is stable: favorites before downloads —
  the primary signal lands first — both `ORDER BY id`), bounding any replay to the unacked tail.
  Rows acted on after live tracking shipped are also live-tracked — a total, permanent overlap the
  server keeps segregated. Note the sync rewrite also means a personal-account user's favorite `t`s
  are really "last sync time", not original like time.
- **10-year window, not plays' 3** (`now−10y..now+5min`, separate constant): an old `likedDate` on
  a still-liked song is a long-standing favorite, not stale data. The server skips out-of-window
  rows PER-ROW (never a batch-level 400), so the client-side window filter is bandwidth hygiene,
  not a safety requirement.
- **Downloads are a weak signal by contract**: the snapshot can't reconstruct `fromUser`, so
  machine-initiated downloads (auto-download-on-like, self-repair) are included and the server
  weights backfilled `download` as corroboration of `favorite`, never equal-weight.
- Own start delay (90 s — spreads first-launch load; wire serialization comes from
  `Tracker.uploadBackfill`'s single-in-flight discipline, NOT from the delay); done-flag checked
  before any DB work and written immediately after the last ack (pacing sleeps only BETWEEN
  batches — a trailing sleep was a window for a process kill to discard a completed drain). The
  conversion policy (`actionBackfillLine` + `actionBackfillLines`) is pure and tested.

## Verifying a build

`curl 'https://tracking.zemer.io/stats?key=<KEY>&days=1'` or the dashboard at
`https://tracking.zemer.io` (ask for the stats key). Sanity: a 5 s skip bumps `plays` but not
`qualifiedPlays`; a gibberish search shows under zero-result searches within ~a minute of a flush.
The `PlaybackStatsListener`/lifecycle layers need a device — the project has no Robolectric — so
they are verified there; everything else is covered by `app/src/test/.../tracking/`.
