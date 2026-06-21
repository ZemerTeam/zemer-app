# Remote cipher config — the remote-updatable player-config system

Hand-authored docset for the system that lets a **pushed JSON file fix deciphering on every
deployed Zemer app within minutes, with no APK release**. Everything in these pages is
derived from the code as of zemer-app `be28bf2` / zemer-cipher `81c7ed8` (the June 2026
implementation); every claim cites the file and symbol that proves it.

## TL;DR

YouTube rotates its `player_ias` JavaScript frequently. Each rotation changes the two
obfuscated transforms (signature decipher + n-transform) that the app must run to get a
playable stream URL. Before this system, every rotation required editing Kotlin
(`FunctionNameExtractor.kt` hardcoded configs), building an APK, and shipping it — days of
broken playback for users.

Now there is **one JSON file**:

```
cipher/library/src/main/assets/player_configs.json     (repo: ZemerTeam/zemer-cipher)
```

and it is consumed in three places that can never drift apart, because they all read the
same bytes:

| Consumer | How | Code |
|---|---|---|
| APK (offline default) | bundled as an Android asset | `PlayerConfigStore.initialize()` → `context.assets.open("player_configs.json")` |
| Deployed devices (the point) | fetched from raw GitHub `master`, 6 h TTL + ETag, plus a failure-triggered forced refresh | `PlayerConfigStore` (`REMOTE_URL`, `refreshIfStale`, `forceRefresh`) |
| Tests / CI monitor | read from the submodule checkout / live URL with the same validation rules | `tests/player-configs.mjs`, `tests/config-covers.mjs` |

**Pushing a new entry to zemer-cipher `master` is the deploy.** Devices pick it up at the
next 6-hour startup refresh — or *immediately mid-session* when an unknown player breaks
extraction (the self-heal path, doc 05).

## The pages

1. **[01-why-it-exists.md](01-why-it-exists.md)** — the problem: player rotation, what a
   "config" actually is (sig call + n URL-class + STS), and why empirical CDN validation
   (HTTP 206) is the only ground truth.
2. **[02-file-format.md](02-file-format.md)** — the JSON schema, byte-exact validation
   regexes, alias semantics, `schemaVersion` rules, and the current live table.
3. **[03-runtime-store.md](03-runtime-store.md)** — `PlayerConfigStore`: initialization,
   the merged in-memory table, TTL refresh, the forced-refresh path, the disk cache, and
   every failure mode it defends against (304-lock, torn writes, disk-full, offline).
4. **[04-validation-and-security.md](04-validation-and-security.md)** — `PlayerConfigParser`:
   why remote data can never inject JS into the WebView, entry-skip vs file-reject
   semantics, and the cross-language parity fixtures that pin both readers together.
5. **[05-extraction-and-self-heal.md](05-extraction-and-self-heal.md)** — how a config
   becomes executable JS in `CipherWebView`, config-over-heuristic precedence, and the
   mid-session self-heal that fixes playback at the exact moment it would break.
6. **[06-harness-and-monitor.md](06-harness-and-monitor.md)** — the `tests/` loader, the
   `config-covers.mjs` verdict CLI, and the hourly `player-monitor.yml` workflow.
7. **[07-runbook.md](07-runbook.md)** — operations: adding a config for a new player,
   deploy order, schema bumps, and what to check when something is off.

## One-paragraph mental model

A config entry is *data describing two function calls*, not code. The parser
(`PlayerConfigParser`) regex-locks every field so the file cannot carry JavaScript; the
executable n-transform is built device-side from a pinned template. The store
(`PlayerConfigStore`) holds an immutable merged map (bundled ⊕ remote, remote wins) behind
a `@Volatile` reference — reads are lock-free, refreshes swap the whole map. Any invalid
remote file is rejected wholesale and the device keeps its last-good table, so the worst a
bad push can do is *nothing*. CI watches YouTube hourly and opens an issue + email when an
unknown player appears; a human derives the new entry, validates it against the live CDN
(`node tests/validate-player-config.mjs <hash>` — HTTP 206 is the proof), and pushes it.

## A cosmetic sibling: `player_dates.json`

Separate from everything above, and deliberately so: a **purely cosmetic** map of player hash ->
the date we added cipher support for it (`{ "959dabb2": "2026-06-12", ... }`), shown in the
song-details sheet next to the player hash (`CipherDeobfuscator.lastUsedPlayerHash`). It lives at
the **root of the zemer-cipher repo** (`player_dates.json`, *not* under `assets/`), is **not
bundled** in the APK, and is fetched purely from
`raw.githubusercontent.com/.../master/player_dates.json` by `PlayerDatesStore` (disk-cached for
instant/offline) — so adding a date is a push, no APK update. It is decoupled from the critical
path on purpose: a separate file old apps never fetch (so it can't affect them), parsed
tolerantly, and any failure only blanks a UI label — deciphering is never touched. Keep it
git-accurate with `node tests/gen-player-dates.mjs` (derives each date from the config's commit
history). This is the *only* part of this system that is cosmetic; everything else is load-bearing.

## Implementation history (the actual commits)

zemer-cipher (`ZemerTeam/zemer-cipher`, all on `master`):

| Commit | What |
|---|---|
| `fdb1219` | the feature: `player_configs.json` asset, `PlayerConfigParser`, `PlayerConfigStore`, remote fetch + self-heal; deleted 227 lines of hardcoded Kotlin configs from `FunctionNameExtractor` |
| `4a79a96` | config precedence over legacy regex patterns; self-heal on *partial* extraction |
| `023a204` | `forceRefresh` decides cooldown under the lock; reports hash presence |
| `42c46a7` | purge meta together with a rejected cache body; atomic cache writes |
| `0ede443` | apply a validated remote table to memory before persisting it |
| `5b7ef67` | hash/alias collisions reject the whole file |
| `ee29c60` | reject string-typed `schemaVersion`; shared parity fixtures |
| `d78a8b3` | n-IIFE template pinned to a cross-language golden file |
| `81c7ed8` | README documentation |
| `2826208` | rebuild the cached WebView on a config change (`configEpoch`, captured pre-build) + a stream-rejection refresh (`refreshAfterStreamRejection`, own cooldown) so a wrong-but-non-throwing cipher recovers without an app restart, for every cipher client (doc 05) |

zemer-app (`main`):

| Commit | What |
|---|---|
| `1b05655` | harness reads player configs from the single-source JSON (deleted the mirrored tables) |
| `09a1913` | harness loader rejects hash/alias collisions |
| `8bdb956` | monitor fetches the config file once, explicit fallback |
| `4b47b6f` | monitor validates configs like a device (`config-covers.mjs`) instead of grepping |
| `ac1965a` | actionable submodule error, lazy config load, resilient validate tool |
| `8c18396` | harness schemaVersion gate aligned with the app parser |
| `53d2aa0` | `validate-player-config.mjs` uses the shared n-IIFE template |
| `1ada9a3`, `15e795d` | submodule pointer bumps to the deploy |
| `be28bf2` | AGENTS/docs sync |
| `8e311da` | on a stream 403 ask the cipher to refresh its config + clear the WEB_REMIX failure set (restore WEB_REMIX without restart); submodule bump to `2826208` |
