# Remote cipher config - the remote-updatable player-config system

Hand-authored docset for the system that lets a **pushed JSON file fix deciphering on every deployed
Zemer app within minutes, with no APK release**. The code lives in the `cipher/` submodule
(`ZemerTeam/zemer-cipher`, package `com.zemer.cipher`) plus the `tests/` harness and the
`player-monitor.yml` workflow in this repo.

YouTube rotates its `player_ias` JavaScript frequently, and each rotation changes the two obfuscated
transforms (signature decipher + n-transform) the app must run to get a playable stream URL. All
per-player knowledge lives in **one JSON file**:

```
cipher/library/src/main/assets/player_configs.json
```

consumed in three places that read the same bytes, so they cannot drift:

| Consumer | How | Code |
|---|---|---|
| APK (offline default) | bundled asset | `PlayerConfigStore.initialize()` |
| Deployed devices | raw GitHub `master`, 6 h TTL + ETag, plus failure-triggered refreshes | `PlayerConfigStore` (`REMOTE_URL`, `refreshIfStale`, `forceRefresh`, `refreshAfterStreamRejection`) |
| Tests / CI monitor | the submodule copy or the live URL, same validation rules | `tests/player-configs.mjs`, `tests/config-covers.mjs`, `tests/scan-live-players.mjs` |

**Pushing an entry to zemer-cipher `master` is the deploy.** Devices pick it up at the next stale
startup refresh, or immediately when an unknown player breaks extraction mid-session.

## Mental model

An entry is *data describing two function calls*, not code. `PlayerConfigParser` regex-locks every
field so the file cannot carry JavaScript; the executable n-transform is built device-side from a
pinned template. `PlayerConfigStore` holds an immutable merged map (bundled ⊕ remote, remote wins)
behind a `@Volatile` reference - lock-free reads, whole-map swaps. An invalid remote file is rejected
wholesale and the device keeps its last-good table, so the worst a bad push can do is *nothing*. CI
scans YouTube every 30 minutes and alerts on an unknown player; a human derives the entry, proves it
against the live CDN (`node tests/validate-player-config.mjs <hash>` → HTTP 206), and pushes it.

## Pages

1. [Concepts & file format](01-concepts-and-format.md) - what a config is, why only a CDN 206
   proves it, the schema, validation rules, the security boundary, parity fixtures.
2. [The runtime store](02-runtime-store.md) - `PlayerConfigStore`: init, refresh paths, cooldowns,
   the disk cache and the failure modes it defends against.
3. [Extraction & self-heal](03-extraction-and-self-heal.md) - config-over-heuristic precedence, the
   mid-session self-heal, the WebView rebuild, the app call sites.
4. [Harness, monitor & runbook](04-operations.md) - `tests/` tooling, `player-monitor.yml`, adding
   a config, scheme changes, diagnosing a device, the invariant → test table.

## A cosmetic sibling: `player_dates.json`

A purely cosmetic map of player hash → the date cipher support was added, shown in the song-details
sheet next to `CipherDeobfuscator.lastUsedPlayerHash` (`ui/utils/ShowMediaInfo.kt`). It lives at the
**root** of the zemer-cipher repo (not under `assets/`), is **not bundled**, and is fetched from raw
`master` by `PlayerDatesStore` (disk-cached). It is deliberately a separate file: old apps never
fetch it, it is parsed tolerantly, and any failure only blanks a UI label - deciphering is never
touched. Regenerate it with `node tests/gen-player-dates.mjs` (dates from the config file's git
history) whenever a player is added.
