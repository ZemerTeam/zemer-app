# 01 - Architecture, data flow & filtering

## Modules (`app/src/main/kotlin/com/jtech/zemer/`)

| File | Responsibility |
|---|---|
| `latestreleases/LatestReleasesStore.kt` | `LatestReleasesFeed` / `LatestRelease` models; network + disk cache ([03](03-runtime-store.md)). |
| `latestreleases/LatestReleaseMapping.kt` | `LatestRelease.toAlbumItem()`. |
| `latestreleases/LatestReleaseDate.kt` | `LatestRelease.relativeDateLabel()`. |
| `latestreleases/LatestReleasesVisibility.kt` | `visibleLatestReleases` - the reactive re-filter. |
| `latestreleases/LatestReleasePlayback.kt` | `isPlayableSingle` / `playableSingle` / `openOrPlay` (tap), `isNowPlaying`, `playAlbum`, `sampleTracks` / `shufflePlay` ([04](04-ui.md)). |
| `latestreleases/LatestReleaseFilter.kt` | See-all All / Albums / Songs filter (`applyFilter`). |
| `latestreleases/LatestReleaseCarouselItem.kt` | The Home carousel hero. |
| `latestreleases/LatestReleaseCard.kt` | The See-all list row + the shared `ReleaseBadges`. |
| `viewmodels/LatestReleasesViewModel.kt` | Orchestration + whitelist re-filter; exposes `releases: StateFlow<List<LatestRelease>>`. |
| `ui/screens/HomeScreen.kt`, `ui/screens/LatestReleasesScreen.kt` | The shelf; the `latest_releases` See-all route. |

## Flow

```
server job --writes--> recent-releases.json --(ETag)--> FEED_URL
                                                           |
LatestReleasesViewModel.init                     LatestReleasesStore
  cachedReleases()  ─── disk cache, no network ──────────┤
  refresh()         ─── conditional GET, ≤3 attempts ────┘
        │ sets the unfiltered `feed`
        ▼
  visibleLatestReleases(feed, WhitelistCache.entries, ContentFilterState.state, ::filterReleases)
        ▼
  releases ──▶ HomeScreen (take(12), carousel)
           └─▶ LatestReleasesScreen (full list)
```

Each surface gets its own `hiltViewModel()` instance, but the store is a process-wide `object`, so
the cache and the give-up state are shared. Each instance calls `refresh()`; the store's mutex
serializes the calls but does not dedupe them, so a later instance re-sends the conditional GET.

## The ViewModel

```kotlin
init {
    LatestReleasesStore.initialize(context)
    viewModelScope.launch(Dispatchers.IO) {
        visibleLatestReleases(feed, WhitelistCache.entries, ContentFilterState.state, ::filterReleases)
            .collect { _releases.value = it }
    }
    viewModelScope.launch(Dispatchers.IO) {
        val cached = LatestReleasesStore.cachedReleases()   // instant, no network
        if (cached.isNotEmpty()) feed.value = cached
        feed.value = LatestReleasesStore.refresh()          // fresh on 200, last-good otherwise
    }
}
```

The loader sets only the **unfiltered** `feed` (null until the cache or refresh answers).
`visibleLatestReleases` is `combine(feed.filterNotNull(), whitelist, filters)` + `mapLatest`: it
**re-filters whenever the feed, the artist whitelist or the content filters change** (a newer input
cancels an in-flight pass) and emits nothing until the feed has loaded. Don't go back to filtering
once at load: a feed that lands before the whitelist would stay empty all session (every artist still
unverified). Toggling a content preference re-filters the shelf live, cached feed included.

### `filterReleases`

1. `distinctBy { it.browseId }` - the external feed may list one album under several artists, and
   `browseId` is the list key on both surfaces (a duplicate crashes the lazy list).
2. Map to `AlbumItem` (`toAlbumItem`) and run `filterWhitelisted(database, config)` - the **same**
   app-wide filter, so female / KidZone / Israeli preferences apply by artist id exactly as elsewhere.
3. Keep the de-duplicated releases whose `browseId` survived, **preserving feed (newest-first) order**.
   Logs `Showing X releases (of Y before whitelist filter)`.

## Adapters

- **`toAlbumItem()`** builds an `AlbumItem(browseId, playlistId, title, artists = [Artist(artistName,
  artistId)], year, thumbnail)`. The feed carries one artist per release. Mapping to `AlbumItem` is
  what reuses the album row, `YouTubeAlbumMenu` / `ytItemMenu`, `album/<id>` navigation and
  `filterWhitelisted`.
- **`relativeDateLabel(now)`** parses `uploadDate` with `OffsetDateTime` and formats it with
  `DateUtils.getRelativeTimeSpanString(…, DAY_IN_MILLIS)` - localized, no string resources. Returns
  null on an unparseable date; the subtitle `joinByBullet(artistName, label)` then shows just the
  artist.
