# 2 · The whitelist guarantee — why non-kosher results are impossible

This is the reason the feature exists in Zemer and not just upstream. **It must be impossible for
recognition to show or play a song whose artist is not on the Firebase artist whitelist** — even if
Shazam recognizes a non-kosher song, even if the user has globally disabled content filtering, even
if the whitelist hasn't synced.

Three independent properties combine to make that true.

## Property A — Shazam's metadata is never displayed

Shazam returns an artist *name* string. Zemer's whitelist (`artist_whitelist` table) is keyed on the
YouTube **artist channel id**, not a name, so matching the name would be fuzzy and leaky. So the
recognized `(title, artist)` is used **only as a search query**; the thing shown/played is a
`SongItem` that came out of YouTube-Music search and survived the whitelist filter. The
`RecognitionResult` (cover art, artist string, ISRC, links) is **never** placed into UI state —
`RecognizeUiState.Result` holds a `SongItem`, full stop (`RecognizeMusicViewModel.kt`).

This is enforced structurally by `RecognitionMatchSelector.select(...)`
(`RecognitionMatchSelector.kt`): it returns an element of the candidate list it was given, or `null`
— it can never fabricate a result from Shazam data. Unit-tested in `RecognitionMatchSelectorTest`
("any returned result is always a member of the input list").

## Property B — the candidate filter is forced on (Gate 1)

`filterWhitelisted` (`utils/WhitelistFilter.kt`) is a **pass-through when content filtering is
globally disabled** — `config.filtersEnabled` comes from the user-settable `EnableContentFiltersKey`
(`App.kt`). If recognition simply called `filterWhitelisted(database)` and the user had turned filters
off, it would return everything.

So `RecognitionResolver.resolveWhitelisted` **forces filtering on** for the recognition search,
regardless of the global toggle:

```kotlin
val forcedConfig = ContentFilterState.current.copy(filtersEnabled = true)
val candidates = searchResult.items
    .filterWhitelisted(database, forcedConfig)
    .filterIsInstance<SongItem>()
```

## Property C — a hard, config-independent re-check (Gate 2, fail-closed)

Even if Gate 1 were bypassed or buggy, the chosen song is re-verified directly against the
`artist_whitelist` table — no dependence on config flags, the in-memory `WhitelistCache`, or
`filterWhitelisted`:

```kotlin
val confirmed = RecognitionMatchSelector.isWhitelistedResult(match) { artistId ->
    database.isArtistWhitelisted(artistId)        // SELECT EXISTS(... FROM artist_whitelist ...)
}
if (!confirmed) return Outcome.NoMatch
```

`isWhitelistedResult` (`RecognitionMatchSelector.kt`) **fails closed**: a song with no artists, or
whose artists all have null ids, returns `false`. So:

- whitelist table empty / not synced → every result is discarded → "No match" (never a leak);
- artist not in the table → discarded;
- only a song with **≥1 artist id present in `artist_whitelist`** can be returned.

Tested in `RecognitionMatchSelectorTest` ("hard gate …"): passes when an artist is whitelisted;
rejects when none are; fails closed on empty-artists and null-ids.

## The matcher (accuracy, not safety)

`RecognitionMatcher.bestMatchIndex` (`RecognitionMatcher.kt`) decides *which* whitelisted candidate
corresponds to the recognized track. Safety is already guaranteed (the candidate list is
post-filter), so the matcher's only job is to avoid surfacing an unrelated whitelisted song that
merely shares a word:

- normalizes title/artist (lowercase, strip diacritics + bracketed segments, drop `feat`/`ft`);
- **gate**: a candidate must clear a token-**recall** threshold on *both* title and artist;
- **rank**: among those that pass, the highest **Jaccard** similarity wins (so an exact title beats
  a longer superset), ties → earliest (search-relevance order).

If nothing clears the gates → `NoMatch`. Covered by `RecognitionMatcherTest` (exact match,
different-artist rejection, shared-title-word rejection, diacritics/feat/brackets, tightest-wins).

## What this means for maintainers

If you touch `RecognitionResolver`, keep **all three** properties:

1. Never put `RecognitionResult` fields into UI state — only the resolved `SongItem`.
2. Keep `filtersEnabled = true` forced for the recognition search.
3. Keep the `isArtistWhitelisted` hard gate, and keep it fail-closed.

The history table also only ever stores the resolved (whitelisted) `SongItem`
(see [04](04-recognition-history.md)). But the whitelist is **mutable** — Firebase sync can remove an
artist after a song was recognized — so "whitelisted at insert time" is not enough. History therefore
stores each entry's **artist ids** and is **re-checked against the *current* whitelist every time the
list is built** (`RecognitionHistoryViewModel` combines the history flow with the live whitelist flow
and drops any entry no clearer of `RecognitionHistoryFilter.isAllowed`, which fails closed exactly
like Gate 2). A de-whitelisted entry simply disappears from history and can't be replayed.

## Known boundary

The *result* is impossible-to-be-non-whitelisted. Once the user taps **Play**, the autoplay/radio
queue that follows uses the app's normal queue filtering (which respects the global toggle), exactly
like playing any song elsewhere in Zemer — that's app-wide playback behavior, not the recognition
result.
