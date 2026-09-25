# 03 - The runtime store (`LatestReleasesStore`)

`latestreleases/LatestReleasesStore.kt`: a process-wide `object` holding the feed in memory and on
disk, refreshed with a conditional GET. Modelled on the cipher `PlayerConfigStore`.

## Constants

| Constant | Value | Meaning |
|---|---|---|
| `TAG` | `Zemer_LatestReleases` | Timber tag (shared with the ViewModel). |
| `FEED_URL` | `https://flipphoneguy.duckdns.org/?page=zemer_releases` | The feed. |
| `MAX_ATTEMPTS` | `3` | Network tries per launch before giving up. |
| `MAX_STALE_MS` | 3 days | A disk cache older than this (since its last successful fetch) is treated as gone. |
| `CACHE_FILE` / `META_FILE` | `latest_releases.json` / `latest_releases.meta` | Under `filesDir/latest_releases/`. |

In memory: `@Volatile cached` (last-good releases), `@Volatile gaveUp`, a `Mutex` serializing
`refresh()`, and a lazy Ktor CIO `HttpClient`.

## API

- **`initialize(context)`** - stores the application context; the ViewModel calls it first.
- **`cachedReleases()`** - the instant path, never networked: in-memory copy, else a valid disk cache
  (memoized), else empty.
- **`refresh()`** - under the mutex:
  1. If `gaveUp`, return the cache - no network.
  2. Up to `MAX_ATTEMPTS` × `fetcher(etag)` (a thrown exception counts as `Failure`):
     `Success` → `applyFetched`; `NotModified` (304) → re-stamp the meta's fetch time and return the
     cache; `Failure` → `delay(retryDelayMs)` (1.5 s) before the next attempt.
  3. All failed → `gaveUp = true` **until the process restarts** (no background retry loop); return the
     cache.
- **`applyFetched(body, etag)`** - a parse failure returns the previous releases (**a bad body never
  clears a good cache**). On success: set `cached`, then persist body + meta; a persist failure is
  caught and the feed stays in memory.
- **`httpFetch(etag)`** (default `fetcher`) - `GET FEED_URL` with `If-None-Match` when an ETag is
  stored; 304 → `NotModified`, non-2xx → `Failure`, 2xx → `Success(body, ETag header)`.

## Disk cache

- **`readValidDiskCache()`**: a missing meta, or `now - lastFetchMs > MAX_STALE_MS`, **deletes** the
  cache and returns null; so does a read/parse error.
- **Meta**: two lines - ETag (may be empty), `lastFetchMs`; anything malformed reads as null.
- **`writeAtomic`**: write `<file>.tmp` then rename, so a crash can't leave a torn file - unless the
  rename fails, when it falls back to a direct write of `<file>`, which can.

## Failure modes (`LatestReleasesStoreTest`)

| Situation | Behaviour | Test |
|---|---|---|
| Fresh 200 | Parse, cache to disk, return in feed order | `refresh parses, returns newest-first, and caches to disk` |
| 3 × failure | Empty, then no more network this launch | `repeated failure gives up after MAX_ATTEMPTS and stops refetching until next launch` |
| Failure after a good fetch | Last-good cache kept | `a failed refresh keeps the last-good cache instead of clearing it` |
| Cache > 3 days old | Dropped | `cache older than 3 days is dropped` |
| 304 | Cache kept, freshness re-stamped | `not-modified keeps the cached releases` |
| 200 with garbage | Previous releases kept | `an unparseable body keeps the previous releases` |

Test seams (`internal var`): `cacheDirForTest`, `nowProvider`, `fetcher`, `retryDelayMs` (tests use
`0L`), plus `resetForTest()` to clear `cached` / `gaveUp` (simulates a fresh launch).
