# Zemer Stations (`playback/queues/StationQueue`, the Home "Radio" tab)

**Synchronized broadcast radio**: one shared, server-programmed wall-clock schedule per station -
every listener hears the SAME track at the SAME moment, joining mid-song like FM. The server owns
the *program*; audio still streams from YouTube + the cipher. Contract:
`handoff-docs/zemer-app-stations.md`; the non-negotiable invariants are in `AGENTS.md` §Zemer Stations.

## The two radio products

| | `/radio` (`ZemerRadioQueue`) | Stations (`StationQueue`) |
| --- | --- | --- |
| Queue | personal, seeded, endless | one shared broadcast per station |
| Position | starts at track 0 (or the seed) | joins at the live wall-clock offset |
| Controls | full transport | play/stop ONLY |
| Persistence | persisted like any queue | NEVER persisted (`saveQueueToDisk` guard) |
| Tracking | seed = context, fill = `radio` | whole listen = `station:<id>` |

## The sync design (one primitive)

- **Wire + clock math** (`search/ZemerStationsModels.kt`, pure + unit-tested): skew against
  `serverTimeMs`, the live join position, start-at-0 for a negative `offsetMs`, the dying-track join
  rule (`STATION_DYING_TRACK_MS` = 5 s), and `stationOnAirOffsetMs` - the on-air window test
  everything else is built on.
- **`MusicService.resyncStationPlayback`** owns EVERY drift path - track boundaries, pause-resume,
  error skips, `STATE_ENDED`: seeks forward when behind, WAITS (pause until `startMs`) when ahead,
  and re-tunes from scratch when nothing queued is on-air. Never a mid-track jump; never a backward
  seek into a played or unplayable slot (a failed stream is `markUnplayable`d and produces no play
  event - the zero-play-time guard).
- **Runway**: `next` entries queue ahead; the top-up ignores the Auto-load-more preference and
  repeat is forced OFF at station start. Blocked ids are dropped client-side (`BlockedIdsCache`).

## Transport lockdown (play/stop only)

The session player (`CastAwarePlayer.maskTransportForStation`) strips seek/skip/repeat/shuffle
commands, NO-OPs them against stale controllers, and notifies command changes on every flip
(`notifyStationMaskChanged`) - so the notification, Android Auto and Bluetooth all comply. In-app,
every raw-player surface is gated on `PlayerConnection.isStationBroadcast` (and
`PlayerConnection.seekTo/seekToNext/seekToPrevious` early-return): mini-player swipes, the full
player's thumbnail swipe, queue-sheet taps, lyrics buttons and line-tap seeks, the widget's skip
actions, repeat/shuffle toggles, and the Start-radio affordances (menu row hidden, notification button
disabled, `MusicService.startRadioSeamlessly` chokepoint guard). Queue MUTATIONS (Play next / Add to queue) deliberately EXIT broadcast mode
(`exitStationOnQueueMutation`). The full player swaps the seek slider for the read-only
`StationLiveBar`; the mini player shows the shared `StationLiveBadge`.

## The Radio tab

The stations are the Home **Radio** tab: a titled ("Zemer Radio") 3-column station grid, no See-all
arrow (the tab is the whole list). `ZemerStationsViewModel` (isolated - a stations failure can never
break Home) feeds `GridItem`-based `ZemerStationCard`s: the branded SVG cover carries the station
name, so the text under it is the live now-playing song over its artist. The now-playing line
refreshes every `STATION_ROW_REFRESH_MS` (60 s) under `repeatOnLifecycle(RESUMED)` - nothing polls
while backgrounded. Empty/unreachable hides the grid. Stations are LIVE-ONLY (no offline snapshot
fallback) and send no content flags (pools are pre-filtered server-side).

## Telemetry

Every station play tags `PlaySource.station(id)` (`station:<id>`, see `docs/tracking/README.md`);
both context flags are true, so the whole broadcast reports under that source. A failed slot sends NO
play event, so the server's skip-docking can never penalize a track that never played.

## Testing

Pure contracts are JVM-tested in `ZemerStationsTest` (wire decode, card shaping, skew/join/dying-track/
on-air math). The `StationQueue`/`MusicService`/`CastAwarePlayer` wiring has no JVM seam (Hilt
EntryPoint + Media3) - verify on device: mid-song tune-in, two devices in sync, dead transport
everywhere, pause-then-resume rejoins live, long-pause re-tunes, queue mutation exits broadcast mode.
