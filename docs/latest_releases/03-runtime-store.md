# 03 — The runtime store (`LatestReleasesStore`)

`app/.../latestreleases/LatestReleasesStore.kt`. A process-wide `object` (`:68`) that owns the
feed at runtime: an in-memory copy, a disk cache for instant display, and a conditional network
refresh. Modelled on the cipher `PlayerConfigStore` (its KDoc says so, `:50-53`).

## Constants (`:69-78`)

| Constant | Value | Meaning |
|---|---|---|
| `TAG` | `"Zemer_LatestReleases"` | Timber tag (shared with the ViewModel). |
| `FEED_URL` | `https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json` | The feed. |
| `MAX_ATTEMPTS` | `3` | Network tries per launch before giving up. |
| `MAX_STALE_MS` | `3 * 24 * 60 * 60 * 1000L` (3 days) | A disk cache older than this is treated as gone. |
| `CACHE_FILE` | `"latest_releases.json"` | The cached feed body. |
| `META_FILE` | `"latest_releases.meta"` | ETag + last-fetch timestamp. |

Cache directory: `File(context.filesDir, "latest_releases")`, created on demand
(`cacheDir()`, `:213-217`).

## In-memory state (`:82-93`)

- `@Volatile cached: List<LatestRelease>?` — last-good releases.
- `@Volatile gaveUp: Boolean` — set true after `MAX_ATTEMPTS` failures, suppresses further
  network this launch.
- `mutex: Mutex` — serializes `refresh()` (only one network pass at a time).
- `httpClient: HttpClient by lazy { HttpClient(CIO) }` — Ktor CIO engine, created on first use.

## Public API

### `initialize(context)` (`:108-110`)

Stores `context.applicationContext`. Called by the ViewModel before any cache access
(`LatestReleasesViewModel.kt:40`).

### `cachedReleases(): List<LatestRelease>` (`:117-122`) — the instant path

1. If the in-memory `cached` is set, return it.
2. Otherwise read the disk cache via `readValidDiskCache()`; if valid, memoize into `cached` and
   return it.
3. Otherwise return `emptyList()`.

Never touches the network. This is what lets the Home shelf appear without a blank gap, like the
DB-backed sections (`:55-58`).

### `refresh(): List<LatestRelease>` (`:129-157`) — the network path

Runs under `mutex.withLock`. Logic:

1. If `gaveUp`, return `cached ?: cachedReleases()` immediately — no network.
2. Read the stored ETag (`readMeta()?.first`).
3. `repeat(MAX_ATTEMPTS)`:
   - Call `fetcher(etag)` inside a `try/catch`; any thrown exception becomes `FetchOutcome.Failure`
     (logged, `:136-139`).
   - **`Success(body, etag)`** -> `applyFetched(body, etag)` and return.
   - **`NotModified`** (304) -> re-stamp the meta's last-fetch time to now (`writeMeta`), keep the
     cache, return `cachedReleases()` (`:142-146`).
   - **`Failure`** -> if not the last attempt, `delay(retryDelayMs)` then loop (`:147-150`).
4. After all attempts fail: set `gaveUp = true`, log, return `cached ?: cachedReleases()`
   (`:154-156`).

So at most **3 attempts, with a delay between them, once per launch.** There is no background
retry loop — once it gives up, it stays given-up until the process restarts (proven by the JVM
test `repeated failure gives up after MAX_ATTEMPTS and stops refetching`,
`LatestReleasesStoreTest.kt:62-73`).

### `applyFetched(body, etag)` (`:159-175`)

1. Parse `body` into `LatestReleasesFeed`. **On a parse exception, log and return the previous
   releases** (`cached ?: cachedReleases()`) — a bad body never clears a good cache
   (`:160-165`; test `an unparseable body keeps the previous releases`, `:106-115`).
2. Set `cached = feed.releases`, log the count/window/version.
3. Persist: write the body to the cache file atomically and stamp the meta. **Persist failures
   are caught** — the feed stays in memory even if disk write fails (`:168-173`).
4. Return `feed.releases`.

### `httpFetch(etag)` (`:177-187`) — the default `fetcher`

A Ktor `get(FEED_URL)`:
- Sends `If-None-Match: <etag>` when a non-empty ETag is stored (`:179`).
- `304 Not Modified` -> `FetchOutcome.NotModified`.
- Non-2xx -> log the status, `FetchOutcome.Failure`.
- `2xx` -> `FetchOutcome.Success(body, response ETag header)`.

## Disk cache

### `readValidDiskCache()` (`:190-206`) — the staleness gate

1. Read the meta's last-fetch timestamp.
2. **If the meta is missing, or `now - lastFetch > MAX_STALE_MS` (3 days), drop the cache**
   (`clearDiskCache()`) and return null (`:192-197`). This is why a server that has been down
   for days lets old "latest" releases disappear rather than lingering — proven by the test
   `cache older than 3 days is dropped` (`LatestReleasesStoreTest.kt:86-95`).
3. Read and parse the cache file; on any read/parse error, drop the cache and return null
   (`:198-205`).

### Meta format (`:223-232`)

A two-line text file: line 1 = ETag (may be empty), line 2 = `lastFetchMs`. Parsed defensively —
fewer than two lines, or a non-numeric timestamp, yields null.

### Atomic writes (`writeAtomic`, `:242-249`)

Write to `<file>.tmp`, then `renameTo(file)`. If the rename fails, fall back to a direct write
and delete the temp. Prevents a torn cache file from a mid-write crash.

## Failure-mode summary (all proven by `LatestReleasesStoreTest.kt`)

| Situation | Behaviour | Test |
|---|---|---|
| Fresh 200 | Parse, cache to disk, return newest-first | `refresh parses, returns newest-first, and caches to disk` |
| 3x network failure | Returns empty, then no further network this launch | `repeated failure gives up after MAX_ATTEMPTS…` |
| Failure after a prior good fetch | Keeps the last-good cache | `a failed refresh keeps the last-good cache…` |
| Cache > 3 days old | Dropped, treated as empty | `cache older than 3 days is dropped` |
| 304 Not Modified | Keeps cached releases, re-stamps freshness | `not-modified keeps the cached releases` |
| 200 with garbage body | Keeps the previous releases | `an unparseable body keeps the previous releases` |

## Test seams (`:103-106`, `:252-255`)

The `object` exposes `internal var` seams so the resilience logic is testable with no server and
no Android runtime:

- `cacheDirForTest: File?` — overrides the cache directory.
- `nowProvider: () -> Long` — overrides the clock (drives the staleness test).
- `fetcher: suspend (etag) -> FetchOutcome` — overrides the network.
- `retryDelayMs` — set to `0L` in tests so retries don't sleep.
- `resetForTest()` — clears in-memory `cached`/`gaveUp` between cases (and to simulate a fresh
  launch).
