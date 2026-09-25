# 2 · The runtime store: `PlayerConfigStore`

`cipher/library/src/main/kotlin/com/zemer/cipher/PlayerConfigStore.kt` - a process-wide `object`
owning the config table on a device. Parsing is delegated to `PlayerConfigParser`; the store handles
assets, disk cache, HTTP (the shared `ZemerCipher.httpClient`) and concurrency. Log tag
`Zemer_CipherConfig`.

## Constants

| Constant | Value | Why |
|---|---|---|
| `REMOTE_URL` | raw zemer-cipher `master` `player_configs.json` | pushing to `master` is the deploy |
| `REFRESH_TTL_MS` | 6 h | mirrors `PlayerJsFetcher.CACHE_TTL_MS` - change them together |
| `FORCE_REFRESH_COOLDOWN_MS` | 5 min | an unknown-everywhere player must not turn every song into a GitHub hit |
| `CACHE_FILE` / `META_FILE` | `configs_remote.json` / `configs_remote.meta` | in `filesDir/cipher_cache/`; meta = ETag line + `lastFetchMs` line |
| `ASSET_NAME` | `player_configs.json` | the bundled default |

**Naming rule:** `cipher_cache/` is shared with `PlayerJsFetcher`, whose `writeToCache` and
`invalidateCache` delete `player_*` files (and `current_hash.txt`). The config files must **never**
start with `player_`, so the config cache and its ETag survive every decipher retry.

**Clock rule:** every wall-clock window check (cooldowns, the TTL, `PlayerJsFetcher`'s cache age) goes
through `withinWindow(now, stamp, window)` = `(now - stamp) in 0 until window`, so a backward clock
step counts as expired instead of wedging a cooldown while playback is broken.

## State

- `bundledConfigs`, `mergedConfigs` - `@Volatile` immutable maps. `get(hash)` reads `mergedConfigs`
  lock-free on the hot path; `mergedConfigs` is always `PlayerConfigParser.merge(bundled, remote)`
  (remote wins per key, bundled-only keys survive), swapped wholesale.
- `configEpoch` - incremented whenever a refresh actually changes the table; the cipher rebuilds its
  WebView when it advances ([03](03-extraction-and-self-heal.md)).
- `lastForcedAttemptMs` / `lastRejectionAttemptMs` - two **independent** cooldown stamps;
  `lastAttemptReachedServer` - whether the last fetch got any HTTP response.
- `refreshMutex` serializes every refresh (startup, forced, stream-rejection).

`knownHashes()` feeds the `Known hashes: …` diagnostic in `FunctionNameExtractor.getHardcodedConfig`.

## Lifecycle

1. **`initialize(context)`** - synchronous, from `ZemerCipher.initialize()` (called by the app's
   `App.kt`). Parses the bundled asset (missing/invalid → error log, empty table, no crash), then
   `applyCachedOverlay()`: a valid `configs_remote.json` is merged over it, so a device that
   self-healed earlier is healed before any network, offline included. On **any** failure to load the
   cache, the body **and** the meta are deleted together - an ETag surviving a corrupt body would make
   every conditional fetch 304 forever (the **304-lock**).
2. **`scheduleStartupRefresh()`** - fire-and-forget `refreshIfStale()`: no-op while the meta's
   `lastFetchMs` is within the TTL, else `fetchAndApply()` under the mutex.
3. **`fetchAndApply()`** - one GET of `REMOTE_URL` (`User-Agent: Mozilla/5.0`, `If-None-Match` when an
   ETag is stored):
   - 304 → re-stamp `lastFetchMs` only;
   - non-2xx (incl. a 404) or empty body → keep everything; `lastFetchMs` is **not** advanced, so the
     next trigger retries;
   - 2xx → `PlayerConfigParser.parse`; `Failure` keeps the previous map **and** cache (a bad push can't
     evict a good cache); `Success` → `applyRemote`;
   - any exception → keep everything.
4. **`applyRemote(remote, body, etag)`** - swaps the merged map into memory (bumping `configEpoch` on a
   change) **before** any disk IO, then persists body + meta in a try/catch: a full disk must never
   discard an in-hand validated fix. Writes go through `writeAtomic` (temp + rename, direct-write
   fallback) so a crash can't leave a torn body beside a valid ETag - the pair with the
   `applyCachedOverlay` purge that makes a 304-lock impossible.

## Failure-triggered refreshes

Both run under `refreshMutex`, and both reset their own cooldown stamp when the fetch never reached
the server (`fetchAndApplyResetting`): the cooldown protects the host from repeat hits, not delays
recovery after an offline moment.

**`forceRefresh(missingHash)`** - called by `CipherDeobfuscator` on incomplete extraction:

1. If `missingHash` is already in the table (a concurrent refresh landed it while we waited) → `true`,
   no fetch, no cooldown.
2. If the forced cooldown is active → `false`.
3. Otherwise stamp the cooldown and fetch; return **whether the hash is now available** (not whether
   this call changed anything), so the caller re-extracts exactly when it can succeed.

The cooldown decision is made under the lock; a check-then-set outside it would let concurrent misses
race.

**`refreshAfterStreamRejection()`** - called via `CipherDeobfuscator.onStreamRejected()` when the CDN
rejects a deciphered URL. A wrong-but-non-throwing signature (stale entry, legacy false positive) is
invisible to `forceRefresh` (extraction looked complete) and to the exception retry (nothing threw).
Unlike `forceRefresh` it **always re-fetches** (the entry may be present but wrong), uses its **own**
cooldown stamp (a 403 on any client must never starve the unknown-hash self-heal, or vice versa), and
returns whether the table **changed**.

## Tests (`cipher/library/src/test/kotlin/com/zemer/cipher/`, `./gradlew :library:testDebugUnitTest` in `cipher/`)

`PlayerConfigStoreCacheTest` (304-lock purge, atomic writes), `PlayerConfigStoreApplyRemoteTest`
(memory before disk, disk-failure survival), `PlayerConfigStoreForceRefreshTest` (cooldown under
lock, hash-presence return, offline reset), `PlayerConfigStoreEpochTest` (epoch advances only on a
real change), `PlayerConfigStoreCooldownTest` (the two cooldowns are independent),
`PlayerConfigMergeTest`, `BundledAssetTest`. Seams: `cacheDirForTest`, `setTableForTest`,
`arm*CooldownForTest`.
