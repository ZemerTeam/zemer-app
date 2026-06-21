# 03 — The runtime store: `PlayerConfigStore`

File: `cipher/library/src/main/kotlin/com/zemer/cipher/PlayerConfigStore.kt` (a Kotlin
`object`, i.e. process-wide singleton). It owns the config table on a device. Parsing is
delegated entirely to `PlayerConfigParser` (doc 04); this object handles Android concerns:
assets, disk cache, HTTP, concurrency.

## The constants (hard numbers)

| Constant | Value | Why |
|---|---|---|
| `REMOTE_URL` | `https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json` | pushing to cipher `master` IS the deploy |
| `REFRESH_TTL_MS` | 6 h (`6*60*60*1000`) | mirrors `PlayerJsFetcher.CACHE_TTL_MS` — config staleness tracks player-JS staleness |
| `FORCE_REFRESH_COOLDOWN_MS` | 5 min (`5*60*1000`) | a player unknown both locally and remotely must not turn every song into a GitHub hit |
| `CACHE_FILE` | `configs_remote.json` | in `filesDir/cipher_cache/` |
| `META_FILE` | `configs_remote.meta` | line 1 = ETag (may be empty), line 2 = lastFetchMs (`readMeta`/`writeMeta`) |
| `ASSET_NAME` | `player_configs.json` | the bundled offline default (~1.5 KB) |

**Naming trap, documented in the code:** the cache dir is *shared* with `PlayerJsFetcher`,
which (a) purges every `player_*` file on each player-JS refresh
(`PlayerJsFetcher.writeToCache`: `listFiles()?.filter { it.name.startsWith("player_") }?.forEach { it.delete() }`)
and (b) wipes the entire dir in `invalidateCache()`. Hence the config files must NOT start
with `player_`. A full wipe is benign by design: the in-memory map survives, and the next
refresh refetches without an ETag.

## State

Three `@Volatile` immutable maps/fields; **reads are lock-free**:

- `bundledConfigs` — parsed once from the asset at `initialize()`.
- `mergedConfigs` — what `get(hash)` reads. Always `bundled + remote` (remote wins per
  key, bundled-only keys survive — `PlayerConfigParser.merge` is literally `bundled + remote`).
  Refreshes build a new map and swap the reference wholesale; no partial states are ever
  visible.
- `lastForcedAttemptMs` / `lastRejectionAttemptMs` / `lastAttemptReachedServer` — the two
  **independent** refresh cooldowns (forced vs. stream-rejection) + the reached-server flag (below).
- `configEpoch` — a counter bumped whenever a refresh actually changes the table. The cipher
  records the epoch its cached WebView was built under and rebuilds when it advances, so a
  corrected config takes effect on the next decipher without an app restart — for every cipher
  (web) client sharing that WebView (doc 05).

A single `refreshMutex` serializes all *writes* (startup refresh + forced + stream-rejection
refreshes).

## Lifecycle

### 1. `initialize(context)` — synchronous, called from `ZemerCipher.initialize()`

(`ZemerCipher.initialize()` is the library entry point the app calls from its
`Application` class; it wires `CipherDeobfuscator`, proxy, then
`PlayerConfigStore.initialize(context)` + `scheduleStartupRefresh()`.)

1. Parse the bundled asset → `bundledConfigs`. Missing/invalid asset logs an error
   (`Zemer_CipherConfig`) and starts the table empty — it does not crash.
2. `applyCachedOverlay()`: if `configs_remote.json` exists and parses **valid**, overlay it
   (`merge`) — so a device that self-healed yesterday is healed *before any network*,
   including fully offline starts. On **any** failure to load it (missing, corrupt,
   rejected), the cache body **and the meta file are deleted together**. Rationale in the
   KDoc: an ETag surviving a corrupt body would make every future conditional fetch return
   304 with no re-download, locking the device on bundled-only configs until the remote
   file happens to change — the "**304-lock**" failure mode (cipher commit `42c46a7`).

This is deliberately cheap (one small asset + at most one small file) and guarantees the
table exists before any lookup.

### 2. `scheduleStartupRefresh()` — async, fire-and-forget

Launches `refreshIfStale()` on `Dispatchers.IO`. If `lastFetchMs` from the meta file is
younger than 6 h → no-op. Otherwise take the mutex and `fetchAndApply()`.

### 3. `fetchAndApply()` — one conditional GET

- Request: `REMOTE_URL`, `User-Agent: Mozilla/5.0`, plus `If-None-Match: <etag>` when a
  stored ETag exists.
- **304** → just bump `lastFetchMs` (write meta). No body transferred — at steady state a
  device costs GitHub one 304 per 6 h.
- **Non-2xx** (incl. the 404 served before the file first lands on the repo's default
  branch) → log, keep everything. `lastFetchMs` is NOT advanced, so the next trigger
  retries.
- **2xx** → parse with `PlayerConfigParser.parse()`. `Failure` → log reason, keep previous
  map AND previous cache (a bad push can't evict a good cache). `Success` → `applyRemote()`.
- Any exception (network down, TLS, …) → log, keep everything.
- `lastAttemptReachedServer` is set true the moment *any* HTTP response arrives — used by
  the cooldown logic below.

### 4. `applyRemote(remote, body, etag)` — memory first, disk best-effort

Ordering is load-bearing (cipher commit `0ede443`): the merged map is swapped into
`mergedConfigs` **before** any disk IO. Then the raw body and meta are persisted inside a
try/catch. KDoc rationale: a disk failure (full disk, IO error) must never discard an
in-hand validated fix — losing the cache only costs a refetch on the next start; losing
the memory update costs working playback *now*.

Persistence uses `writeAtomic()`: write `<name>.tmp`, `renameTo` the final name, with a
direct-write fallback for filesystems where rename fails. A process death mid-write
therefore can't leave a truncated `configs_remote.json` beside a valid ETag — which is
exactly the 304-lock state `applyCachedOverlay()` defends against (the two defenses are a
matched pair).

### 5. `forceRefresh(missingHash)` — the self-heal fetch

Called by `CipherDeobfuscator` when extraction for the current player is incomplete
(doc 05). Semantics, all decided **under the mutex** (cipher commit `023a204` — a
check-then-set outside the lock would let concurrent misses race):

1. If `missingHash` is *already* in `mergedConfigs` — a concurrent/just-finished refresh
   landed it while we waited on the lock — return `true` without fetching or arming the
   cooldown.
2. If the last forced attempt was < 5 min ago → return `false` (cooldown).
3. Otherwise stamp the cooldown, `fetchAndApply()`, and — key subtlety — if the attempt
   never reached the server (`lastAttemptReachedServer == false`, e.g. rotation hit while
   offline), **reset the cooldown to 0**. The cooldown exists to protect the config host
   from repeat hits, not to delay recovery after a pure network failure: the moment
   connectivity returns, the next miss may retry immediately.
4. Return `mergedConfigs.containsKey(missingHash)` — *"is the hash now available"*, not
   *"did my fetch change anything"* — so the caller retries extraction exactly when it can
   succeed.

### 6. `refreshAfterStreamRejection()` — the stream-rejection refresh (cipher commit `2826208`)

Called by `CipherDeobfuscator.onStreamRejected()` when a deciphered URL is rejected by the
CDN (a 403/410, surfaced from `MusicService.handleExpiredUrlError`). It exists because a
wrong-but-*non-throwing* signature — a stale/wrong config, or a legacy false positive on a
real function — is invisible to both the extraction-incomplete `forceRefresh` (extraction
looked complete) and the exception-retry (nothing threw). Differences from `forceRefresh`:

1. It does **not** short-circuit when the current hash is already present — the entry may be
   present but WRONG, so it always re-fetches.
2. It uses its **own** cooldown stamp (`lastRejectionAttemptMs`), so a 403 — which can fire on
   any client, including unrelated/expired-URL ones — can never arm a cooldown that starves
   the unknown-hash `forceRefresh` self-heal, or vice versa. Both still serialize on
   `refreshMutex`, and both reset their stamp on a pure network failure (shared
   `fetchAndApplyResetting` helper).
3. It returns whether the table **changed**; on a change `configEpoch` advances → the cipher
   rebuilds (doc 05), and `MusicService` clears the WEB_REMIX failure set so playback returns
   to WEB_REMIX.

## Read path

`get(hash)` reads the `@Volatile` map — no locks, no IO, called on the hot path of every
sig/n extraction. An empty table (initialize never called / broken asset) logs a warning.
`knownHashes()` backs the diagnostic log in `FunctionNameExtractor.getHardcodedConfig()`
("Known hashes: …" — what you see in logcat when a player is unknown).

## Test seams + coverage

- `cacheDirForTest` (point disk IO at a temp dir) and `setTableForTest` (swap the map
  without disk/network) are `internal`.
- JVM unit tests in `cipher/library/src/test/`:
  `PlayerConfigStoreCacheTest` (304-lock purge, atomic writes),
  `PlayerConfigStoreApplyRemoteTest` (memory-before-disk, disk-failure survival),
  `PlayerConfigStoreForceRefreshTest` (cooldown under lock, hash-presence return,
  offline cooldown reset), `PlayerConfigStoreEpochTest` (epoch advances only when the table
  actually changes), `PlayerConfigStoreCooldownTest` (the forced and rejection cooldowns are
  independent — neither gates the other), plus `PlayerConfigMergeTest` and `BundledAssetTest`.
  Run: `./gradlew :library:testDebugUnitTest` in `cipher/`.
