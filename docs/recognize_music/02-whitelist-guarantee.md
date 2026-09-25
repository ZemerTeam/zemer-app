# 2 · The whitelist guarantee

**Recognition must never show or play a song whose artist is not on the artist whitelist** - even if
Shazam recognizes a non-kosher song, even with content filtering disabled app-wide, even before the
whitelist has synced. Three independent properties make that true. If you touch
`RecognitionResolver`, keep all three.

## A - Shazam's metadata is never displayed

The whitelist (`artist_whitelist`) is keyed on the YouTube artist **channel id**, not a name, so
matching Shazam's artist string would be fuzzy and leaky. The recognized `(title, artist)` is used
**only as a search query**; `RecognizeUiState.Result` holds a `SongItem` from YouTube-Music search,
never `RecognitionResult` fields. `RecognitionMatchSelector.select(...)` returns an element of the
candidate list it was given or `null` - it cannot fabricate a result
(`RecognitionMatchSelectorTest`: "any returned result is always a member of the input list").

## B - Gate 1: the candidate filter is forced on

`filterWhitelisted` (`utils/WhitelistFilter.kt`) passes everything through when
`config.filtersEnabled` is false (the user-settable `EnableContentFiltersKey`). So the resolver forces
it on regardless of the global toggle:

```kotlin
val forcedConfig = ContentFilterState.current.copy(filtersEnabled = true)
val candidates = searchResult.items
    .filterWhitelisted(database, forcedConfig)
    .filterIsInstance<SongItem>()
```

## C - Gate 2: a hard, config-independent re-check (fail-closed)

The chosen song is re-verified directly against the table - no config flags, no `WhitelistCache`, no
`filterWhitelisted`:

```kotlin
val confirmed = RecognitionMatchSelector.isWhitelistedResult(match) { artistId ->
    database.isArtistWhitelisted(artistId)   // SELECT EXISTS(... FROM artist_whitelist ...)
}
if (!confirmed) return@withContext Outcome.NoMatch
```

`isWhitelistedResult` returns true only when **≥1 artist id** is whitelisted; no artists or all-null
ids → false. So an empty/unsynced table yields "No match", never a leak.

## The matcher (accuracy, not safety)

`RecognitionMatcher.bestMatchIndex` picks *which* (already-whitelisted) candidate is the recognized
track, so an unrelated song that merely shares a word isn't surfaced:

- normalizes (strip bracketed segments + diacritics, lowercase, drop punctuation and
  `feat`/`ft`/`featuring`);
- **gate**: token **recall** ≥ 0.5 on the title *and* on some candidate artist;
- **rank**: highest Jaccard similarity (title 0.6 + artist 0.4); ties → earliest (search order).

Nothing clears the gate → `NoMatch`.

## History is re-checked against the *current* whitelist

After both gates pass, `recordHistory` stores the song plus its artist ids
(`RecognitionHistoryFilter.joinIds`). The whitelist is mutable (sync can remove an artist later), so
"whitelisted at insert time" is not enough: `RecognitionHistoryViewModel.history` combines the history
flow with `getAllWhitelistedArtistIds()` and keeps only entries passing
`RecognitionHistoryFilter.isAllowed`, which fails closed like Gate 2. That flow is the only place
history is exposed, so a de-whitelisted entry disappears and can't be replayed.

## Known boundary

The *result* is always whitelisted. After **Play**, the queue that follows uses the app's normal
playback filtering (which respects the global toggle), like any other play in the app.
