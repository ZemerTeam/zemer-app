# 04 — ViewModel, whitelist filtering, and the adapters

## `LatestReleasesViewModel` (`app/.../viewmodels/LatestReleasesViewModel.kt`)

A `@HiltViewModel` (`:31`) injected with the application `Context` and the `MusicDatabase`
(`:32-35`). It exposes one observable:

```kotlin
private val _releases = MutableStateFlow<List<LatestRelease>>(emptyList())
val releases: StateFlow<List<LatestRelease>> = _releases.asStateFlow()   // :36-37
```

It is **deliberately separate from `HomeViewModel`** "so a failure fetching the external feed can
never affect the rest of Home" (`:22-30`). This is the structural half of the "never break the
UI" contract (doc 01).

### The init sequence

```kotlin
private val feed = MutableStateFlow<List<LatestRelease>?>(null)   // null until cache/refresh answers

init {
    LatestReleasesStore.initialize(context)
    viewModelScope.launch(Dispatchers.IO) {
        visibleLatestReleases(feed, WhitelistCache.entries, ContentFilterState.state, ::filterReleases)
            .collect { _releases.value = it }
    }
    viewModelScope.launch(Dispatchers.IO) {
        val cached = LatestReleasesStore.cachedReleases()      // instant, no network
        if (cached.isNotEmpty()) feed.value = cached           // show cached immediately
        feed.value = LatestReleasesStore.refresh()             // one network pass, replaces it
    }
}
```

The loader only sets the **unfiltered** `feed`; filtering is reactive.
`visibleLatestReleases` (`latestreleases/LatestReleasesVisibility.kt`, JVM-tested) is a
`combine(feed.filterNotNull(), whitelist, filters)` + `mapLatest { filter(...) }`: it
**re-filters whenever the feed, the artist whitelist or the content filters change** (a newer
input cancels an in-flight pass), and emits **nothing until the feed has loaded**. Filtering once
at load used to leave the row empty for the whole session when the feed landed before the
whitelist (every artist still unverified and rejected).

Two feed updates in the common case:

1. **Cached-first:** if a fresh-enough disk cache exists, it is published right away — the shelf
   shows without waiting for the network.
2. **Refresh:** the once-per-launch `refresh()` runs; its result (fresh on 200, last-good on
   304/failure) replaces the first.

All on `Dispatchers.IO` (network + DB-backed filtering). If the cache is empty, only the second
update happens, and it may be empty (server unreachable + no cache) — a valid state.

### `filterReleases` — the whitelist re-filter

```kotlin
private suspend fun filterReleases(
    releases: List<LatestRelease>,
    config: ContentFilterConfig,
): List<LatestRelease> {
    if (releases.isEmpty()) return emptyList()
    val unique = releases.distinctBy { it.browseId }
    val allowedBrowseIds = unique.map { it.toAlbumItem() }
        .filterWhitelisted(database, config)
        .mapNotNull { (it as? AlbumItem)?.browseId }
        .toSet()
    return unique.filter { it.browseId in allowedBrowseIds }
}
```

Four steps:

1. De-duplicate by `browseId` (`distinctBy`, keeping the first/newest occurrence). The feed is
   external and may list one album under more than one whitelisted artist, and `browseId` is the
   Compose list key on both surfaces — a duplicate would otherwise crash the list with a
   "key already used" error.
2. Map each surviving `LatestRelease` to an `AlbumItem` (`toAlbumItem`, below) and run the list
   through `filterWhitelisted(database, config)` — the **same** filter every other surface uses
   (`utils/WhitelistFilter.kt:149`).
3. Collect the surviving `AlbumItem`s' `browseId`s into a set.
4. Return the de-duplicated releases whose `browseId` is in that set, **preserving the feed's
   newest-first order** (a plain `filter` over the original order — no fragile map-back).

This is why per-user content preferences apply identically here: there is no bespoke filtering;
the feature borrows the app-wide filter. `filterWhitelisted` is handed the active
`ContentFilterConfig` (the `ContentFilterState.state` value the combine delivered) and reads the
cached whitelist, deciding each `AlbumItem` by its artist id — exactly the female / KidZone / Israeli
gating used elsewhere.

> **Filtering runs on every input change**, including the cached feed. So a release that the
> server included but the *user's* current preferences exclude never reaches the UI, even from
> cache — and toggling a preference re-filters the shelf live.

## `toAlbumItem` — the feed -> InnerTube adapter (`latestreleases/LatestReleaseMapping.kt`)

```kotlin
fun LatestRelease.toAlbumItem(): AlbumItem = AlbumItem(
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    artists = listOf(Artist(name = artistName, id = artistId)),
    year = year,
    thumbnail = thumbnail,
)
```

The feed carries a **single** artist per release, surfaced as the album's only artist
(KDoc). `AlbumItem` has no explicit field and the feed carries no explicit flag — the whitelist
is the content gate. Mapping to `AlbumItem` is what unlocks reuse of the album card,
the album menu (`YouTubeAlbumMenu`), navigation (`album/<id>`), and `filterWhitelisted`.

## `relativeDateLabel` — the date line (`latestreleases/LatestReleaseDate.kt`)

```kotlin
fun LatestRelease.relativeDateLabel(now: Long = System.currentTimeMillis()): String? = try {
    val millis = OffsetDateTime.parse(uploadDate).toInstant().toEpochMilli()
    DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.DAY_IN_MILLIS).toString()
} catch (e: Exception) {
    null
}
```

- Parses the ISO-8601 `uploadDate` and formats it via Android's `DateUtils` as a **localized**
  relative span ("2 days ago", "Yesterday", "Today"). Using `DateUtils` means **no new string
  resources** are needed (`:6-9` KDoc).
- On an unparseable date it returns `null`. The card subtitle is
  `joinByBullet(artistName, relativeDateLabel)`, and `joinByBullet` drops null/empty parts
  (`utils/StringUtils.kt:27`), so a null date degrades the line to **just the artist name**
  (it does not fall back to the default `AlbumItem` subtitle; see doc 05).
- `now` is a parameter (defaulting to the system clock) so the formatting is deterministic in a
  test if needed.

The See-all row memoizes this per `browseId` (`LatestReleaseCard.kt`:
`remember(release.browseId) { release.relativeDateLabel() }`); the Home carousel hero computes it
inline in its `HeroTitleOverlay` subtitle.
