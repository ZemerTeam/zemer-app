# 4 · Harness, monitor & runbook

## `tests/` tooling

- **`tests/player-configs.mjs`** - the harness's only source of configs, reading the submodule's
  `cipher/library/src/main/assets/player_configs.json` (no mirrored tables). Exports
  `parsePlayerConfigs(jsonText, label)` (the device rules; throws on a bad entry - use it to validate
  *any* copy, e.g. the live file), `loadRawPlayerConfigs(path?)` (lazy; a missing submodule gives the
  actionable `git submodule update --init` error), `loadKnownPlayerConfigs()` (alias-expanded
  `{ sigExpr, nExpr, sts }` map) and `nTrick(urlClass)` (the JS n-IIFE, golden-pinned).
  `node --test tests/player-configs.test.mjs` - no cookie or network: validation rules, collisions,
  the `config-covers.mjs` CLI, the schemaVersion gate, the template golden, the parity fixtures.
- **`tests/validate-player-config.mjs <hash> ["<sig>" <nClass>]`** - downloads the player, enumerates
  candidate `(sig, nClass)` pairs (or tests the given one), rebuilds the cipher like a device (jsdom,
  the same shims, `nTrick`), resolves a real WEB_REMIX `/player` response pinned to that player's STS
  with the logged-in `innertube_cookie.txt`, deciphers a real stream and **GETs the CDN: 206 = correct,
  403 = wrong**. Prints a paste-ready entry with the md5 alias; re-validates an already-committed entry
  first.
- **`tests/config-covers.mjs <hash> <file>`** - prints `covered` / `uncovered`; exits 1 with the
  validation error when a device would reject the whole file.
- **`tests/scan-live-players.mjs <configs> <n>`** - the monitor's scanner (pure core `aggregate` /
  `coveredKeys`, tested in `tests/scan-live-players.test.mjs`).

## `.github/workflows/player-monitor.yml`

Every 30 minutes (`*/30 * * * *`) + manual dispatch; serialized by the `youtube-player-monitor`
concurrency group (`cancel-in-progress: false`) so overlapping runs can't open duplicate issues.

1. **Fetch the config once from the live raw `master` URL** (`curl -fsS --retry 3`) - the verdict is
   about what deployed apps actually self-heal from, and never false-alarms when cipher `master` is
   ahead of the submodule pointer. Falls back to the submodule copy with a `::warning::`; if both
   fail, the run is red.
2. **Multi-sample the live players**: `node tests/scan-live-players.mjs /tmp/player_configs.json 30`
   samples `iframe_api` (what `PlayerJsFetcher` uses) 30× plus `music.youtube.com` a few times, so a
   low-rate A/B **canary** is caught before it rotates in (a single sample would usually miss it). Unknown hashes are re-checked by md5 alias, and "known" is decided by the harness
   loader - a pushed-but-invalid entry still counts as unknown. Exits non-zero only when the live
   config itself is invalid.
3. **Alert per unknown hash**: a Telegram message (skipped without `TELEGRAM_BOT_TOKEN`), one GitHub
   issue per hash titled `New YouTube player detected: <hash>` (labels `player-update`, `cipher`;
   deduped against open issues by that title; opened as the Zemer-Dude GitHub App when
   `vars.ZEMER_APP_ID` is set, else `GITHUB_TOKEN`), and one summary email via Gmail SMTP (sent even
   if issue creation fails), all carrying the runbook commands.

It **never auto-commits**: validation needs a logged-in cookie and live-CDN judgment CI shouldn't hold.

Still mirrored by hand (not configs): `tests/clients.mjs` / `tests/potoken.mjs` must follow
`YouTubeClient.kt` / `PoTokenGenerator.kt`.

## Runbook

### A. New player (routine)

Trigger: a monitor issue/email, or `Zemer_CipherFnExtract: No hardcoded config for hash …` in the
field.

1. `node tests/validate-player-config.mjs <hash>` (needs the gitignored `innertube_cookie.txt`).
   Accept nothing less than **HTTP 206**.
2. Add the printed entry to `library/src/main/assets/player_configs.json` in **zemer-cipher** (the
   submodule checkout is fine) - there are no other copies to sync. Then `node tests/gen-player-dates.mjs`
   to rewrite the cosmetic `player_dates.json`.
3. Check:
   ```
   cd cipher && ./gradlew :library:testDebugUnitTest
   cd .. && node --test tests/player-configs.test.mjs
   node tests/config-covers.mjs <hash> cipher/library/src/main/assets/player_configs.json
   ```
   A duplicate hash/alias anywhere rejects the **whole** file on every device - run the tests.
4. **Push zemer-cipher `master` - this is the deploy** (immediate for a device whose song fails, ≤ 6 h
   via the startup refresh). No APK.
5. Bump the submodule pointer in zemer-app afterwards. **Push order: zemer-cipher first, then the
   pointer** - the reverse leaves fresh clones / CI pointing at a commit not on the remote. (Push only
   when explicitly authorized.)

### B. Scheme change (new config shape)

Needs code + an APK. Change `PlayerConfigParser.kt`, `tests/player-configs.mjs` **and** the
`config-parity/` fixtures together. Bump `SUPPORTED_SCHEMA_VERSION` (Kotlin and JS) and the file's
`schemaVersion` **only if the change is breaking** - every deployed app then freezes on its last-good
table until updated, so prefer backward-compatible optional fields. Build debug and release.

### C. A device isn't healing

| Tag | Look for |
|---|---|
| `Zemer_CipherConfig` | `Loaded bundled configs (N hashes)`, `Overlaying cached remote configs`, `Remote configs applied (… changed=…)`, `Remote configs rejected: <reason>`, `forceRefresh skipped (cooldown)`, `Remote config fetch HTTP <code>` |
| `Zemer_CipherFnExtract` | `No hardcoded config for hash: <h>` + `Known hashes: …`, `USING EXPRESSION-BASED SIG` |
| `Zemer_CipherDeobfusc` | `Incomplete extraction for player <h> … forcing remote config refresh` |
| `YTPlayerUtils` | `Playback: client=…, itag=…` |

1. Is the entry on the **live URL** (devices fetch raw `master`, not your branch)?
2. Would a device accept the **whole** file? `node tests/config-covers.mjs <hash> <(curl -s <REMOTE_URL>)`
   - textual presence of the hash means nothing if the file is invalid.
3. Cooldown: a failure-triggered refresh runs at most once per 5 min (only when GitHub was reached);
   the startup refresh runs on any launch where the table is > 6 h old.
4. Cache: `filesDir/cipher_cache/configs_remote.json` + `.meta`. A stale 304 loop is prevented by
   construction (meta purged with a bad body; atomic writes) - re-check `PlayerConfigStoreCacheTest`
   before theorizing otherwise.
5. Deciphers but dies around 1 MiB → a **pot binding** problem, not a config problem
   (`tests/INVESTIGATION.md`).

## Invariants → the test that enforces them

| Invariant | Enforced by |
|---|---|
| Bundled asset always valid | `BundledAssetTest` |
| Kotlin parser ⇔ JS loader file-level parity | `ConfigParityFixturesTest` + `player-configs.test.mjs` over `config-parity/` |
| n-IIFE byte-identical in Kotlin / JS / validator | `NJsExpressionTemplateTest` + the golden test in `player-configs.test.mjs` |
| Duplicate keys reject the whole file | `PlayerConfigParserTest` + `player-configs.test.mjs` |
| Config beats heuristic; heuristic never blocks self-heal | `FunctionNameExtractorPrecedenceTest` |
| Validated remote table reaches memory even if disk fails | `PlayerConfigStoreApplyRemoteTest` |
| No 304-lock (ETag without body / torn writes) | `PlayerConfigStoreCacheTest` |
| forceRefresh single-flight, cooldown under lock, offline doesn't arm it | `PlayerConfigStoreForceRefreshTest` |
| `configEpoch` advances only on a real change | `PlayerConfigStoreEpochTest` |
| Forced and stream-rejection cooldowns independent | `PlayerConfigStoreCooldownTest` |

Not test-enforced, from code comments: config cache filenames never start with `player_`, and
`REFRESH_TTL_MS` mirrors `PlayerJsFetcher.CACHE_TTL_MS`.
