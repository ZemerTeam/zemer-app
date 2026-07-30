# Zemer Stations (`playback/queues/StationQueue`, the "Zemer Radio" home row)

**Synchronized broadcast radio**: one shared, server-programmed wall-clock schedule per station -
every listener hears the SAME track at the SAME moment, joining mid-song like FM. The server owns
the *program*; audio still streams from YouTube + the cipher like everywhere else. The app-side
contract (and its settled Q&A + addendum) is `~/zemer-fix/handoff-docs/zemer-app-stations.md`; the
non-negotiable invariants live in `AGENTS.md` §Zemer Stations. This doc is the map.

## The two radio products

| | `/radio` (`ZemerRadioQueue`) | Stations (`StationQueue`) |
| --- | --- | --- |
| Queue | personal, seeded, endless | one shared broadcast per station |
| Position | starts at track 0 (or the seed) | joins at the live wall-clock offset |
| Controls | full transport | play/stop ONLY |
| Persistence | persisted like any queue | NEVER persisted |
| Tracking | seed = context, fill = `radio` | whole listen = `station:<id>` |

## The sync design (one primitive)

- **Wire + clock math** (`search/ZemerStationsModels.kt`, pure + unit-tested): skew measurement
  against `serverTimeMs`, the live join position, the start-at-0 rule for the addendum's negative
  `offsetMs`, the 5s dying-track join rule, and `stationOnAirOffsetMs` - the on-air window test
  everything else is built on.
- **`resyncStationPlayback` (MusicService)** owns EVERY drift path - track boundaries,
  pause-resume, error skips, `STATE_ENDED`: seeks forward when behind, WAITS (pause until
  `startMs`) when ahead, and re-tunes from scratch when nothing queued is on-air. Never a mid-track
  jump; never a backward seek into a played or unplayable slot (a failed stream is
  `markUnplayable`d and produces no play event - the zero-play-time guard).
- **Runway**: `next` entries queue ahead; the top-up ignores the Auto-load-more preference and
  repeat is forced OFF at station start.

## Transport lockdown (play/stop only)

The session player (`CastAwarePlayer.maskTransportForStation`) strips seek/skip/repeat/shuffle
commands, NO-OPs them against stale controllers, and notifies command changes on every flip - so
the notification, Android Auto and Bluetooth all comply. In-app, every raw-player surface is gated
on `PlayerConnection.isStationBroadcast`: mini-player swipes, the full player's thumbnail swipe,
queue-sheet taps, lyrics buttons and scrubs, the widget's skip actions, repeat/shuffle toggles, and
the Start-radio affordances (menu row hidden, notification button disabled, chokepoint guard).
Queue MUTATIONS (Play next / Add to queue) deliberately EXIT broadcast mode
(`exitStationOnQueueMutation`). The full player swaps the seek slider for the read-only
`StationLiveBar`; both mini players show the shared `StationLiveBadge`.

## The home row + See-all

`ZemerStationsViewModel` (isolated - a stations failure can never break Home) feeds `GridItem`-based
`ZemerStationCard`s: the branded SVG cover carries the station name, so the text under it is the
live now-playing SONG (bold, marquee) over its artist. The now-playing line refreshes on a 60s
ticker scoped with `repeatOnLifecycle(RESUMED)` - nothing polls while backgrounded. The title arrow
opens the `zemer_stations` grid (`ZemerStationsScreen`). Live cards only; empty/unreachable hides
the row (the `/home-rows` fail-soft convention). Stations are LIVE-ONLY: excluded from the offline
snapshot fallback like `/playlist` and `/radio`, and no content flags are sent (pools are
pre-filtered server-side to the strictest common denominator; blocked-ids still run client-side).

## Telemetry

Every station play tags `PlaySource.station(id)` (`station:<id>`, declared in
`docs/tracking/README.md`); both context flags are true, so the whole broadcast reports under that
source. The server excludes station plays from co-occurrence training and docks heavily-skipped
tracks in the next schedule - a failed slot sends NO play event, so it can never be mis-docked.

## Testing

Pure contracts under JVM tests (`ZemerStationsTest`): wire decode of the contract payloads, card
shaping, skew/join/dying-track/on-air math. The `StationQueue`/MusicService/CastAwarePlayer wiring
has no JVM seam (Hilt EntryPoint + Media3) - verified on device: mid-song tune-in, two devices in
sync, dead transport everywhere, pause-then-resume rejoins live, long-pause re-tunes, queue
mutation exits broadcast mode.
