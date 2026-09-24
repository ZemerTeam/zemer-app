# tests/search — search-path harness

Exercises the app's **one remaining** YouTube Music search function, **exactly as the app does**,
against the live API, and reports any error: a strict-deserialization break, a parser drop, or an
unexpectedly empty result. Built to answer "why is search not working?" with measured data instead
of guesses (same philosophy as the parent `tests/` harness).

Zemer is the app's only search *engine*: `searchSuggestions`/`searchSummary`/`searchContinuation`
were deleted from `YouTube.kt` (dead since the engine removal), so their probes are gone from this
harness too. What survives is `YouTube.search(query, filter)`, still called by `RecognitionResolver`,
the Android Auto voice search, and the add-to-playlist online search dialog.

## What it reproduces

The app's search entry point, on the `WEB_REMIX` client:

| App function (`YouTube.kt`) | Request | Parser |
| --- | --- | --- |
| `search(query, filter)` x6 filters | POST `search` (params) | `SearchPage.toYTItem` |

**Faithfulness facts** (verified against `InnerTube.kt`):
- Search runs with `setLogin = false` — the app sends **visitorData only, no cookie/Authorization**.
  The harness matches this (it does read `innertube_cookie.txt` only to reuse its `visitorData`).
- The 6 `SearchFilter` param strings, the request body shape, and the section-walking logic
  (`musicShelfRenderer` + `itemSectionRenderer`, `distinctBy id`) are copied verbatim.
- `lib.mjs` / `parsers.mjs` are line-for-line ports of the InnerTube helpers, the
  `MusicResponsiveListItemRenderer` accessors, and the `toYTItem` parser. A drop here = a drop in the app.

## The strict-deserialization check (the big one)

The app decodes with kotlinx `ignoreUnknownKeys=true`, `explicitNulls=false`, **no
`coerceInputValues`**. So a Kotlin property that is **non-null and has no default** is REQUIRED: if
YouTube stops sending it, `body<SearchResponse>()` throws `MissingFieldException` and the **entire**
response fails — `YouTube.search().getOrNull()` swallows it to `null` and the caller sees no results.
One missing field kills every result.

`schema.mjs` encodes, per renderer reachable from a search response, which fields are required vs
optional (transcribed from the Kotlin models) and `validate()` walks the live JSON the same way
kotlinx would, flagging every non-null field the server omitted. **When a model's nullability
changes, update the matching entry here.** Subtrees that never appear in a search response
(`gridRenderer`, `musicQueueRenderer`, …) are intentionally unencoded and reported in `unencoded` if
ever met, so the sweep never silently skips something.

## Run

```bash
node tests/search/run.mjs                       # default query set, every filter
node tests/search/run.mjs "mordechai shapiro"   # one query
node tests/search/run.mjs q1 q2 ...             # several queries
SAVE=1 node tests/search/run.mjs                # also dump raw JSON to tests/search/out/
node --test tests/search/self-test.mjs          # prove the checker catches breaks (no network)
```

Exit code: `0` = no whole-response killers; `1` = a strict break was found.

### Whitelist-driven probes (need a names file at `tests/search/.cache/whitelist.json`, gitignored)

```bash
node tests/search/fetch-whitelist.mjs           # pull the whitelist (reads gitignored google-services.json)
N=300 node tests/search/coverage.mjs            # every filter over N real whitelisted artists; aggregates errors
node tests/search/whitelist-findable.mjs        # are whitelisted artists findable in artist search? (drop reasons)
node tests/search/album-facet-probe.mjs         # ROOT CAUSE: which artists get an "Albums" search chip
node tests/search/verify-album-fix.mjs          # proves the artist-page album grid (the fix's data source)
```

`diag-auth.mjs` holds authenticated `search`/`browse` helpers for these probes — **diagnostic only**,
not a model of the app's real (unauthenticated) search path.

### Lyrics-source research probes (one-off, not CI; need the sibling `zemer-search` checkout)

The `lyrics-*.mjs`, `jyrics-*.mjs`, `jkaraoke-resolve.mjs`, `corpus-*.mjs`, `drive-*-resolve*.mjs` and
`names-resolve-yt.mjs` scripts are the coverage/accuracy probes that chose the app's lyrics sources (Zemer resolver
sources, SimpMusic, LrcLib, Musixmatch, YouTube). They read the sibling `zemer-search` repo's `data/corpus.db` +
`corpus/lyrics.mjs` through `jyrics-common.mjs` (`ZEMER_SEARCH=/path/to/zemer-search` overrides the default
workspace sibling) and write under the gitignored `tests/search/.cache/`. Diagnostic only, never wired into CI.

## Out of scope (by design)

Zemer's **artist-whitelist filter** (`app/.../utils/WhitelistFilter.kt`) runs *after* `search()` at
its call sites and drops every item whose artist isn't whitelisted. It needs the app's Room DB, so it
can't run here — but it is the **next** suspect when the function is healthy and a caller still
comes up empty (an empty/un-synced whitelist drops everything).

## Findings (2026-06-12 sweep; harness trimmed 2026-08-16)

The 2026-06-12 sweep ran every then-existing search function over 300 real whitelisted-artist
queries: **no strict breaks, parsers extracted 100% of music items** — the InnerTube search layer was
structurally healthy. Notable then: ~48/300 artist searches dropped the artist row for a missing
shuffle/radio endpoint (latent robustness issue in `toYTItem`, still present). The
suggestions/summary/continuation probes (and their findings) were retired on 2026-08-16 when the app
functions they mirrored were deleted.

## Why album search is empty for whitelisted artists (root cause)

Symptom: the "Albums" search tab / an artist's "Albums" come back empty for whitelisted (independent,
mostly Jewish) artists — **even though their channel page lists every album**. This used to work.

It is **not** the app's params, auth, or code, and **not** fixable in the search request:

| | "Albums" chip offered in search? | album search result | albums on `/browse` page |
| --- | --- | --- | --- |
| Taylor Swift (Official Artist Channel) | yes | 20 | yes |
| Mordechai Shapiro (independent) | **no** | **0** | **22** |
| Yaakov Shwekey / Baruch Levine / Avraham Fried | no | 0 | 24 / 23 / 24 |

The chip cloud is YouTube telling you which result categories exist for a query. **YouTube stopped
exposing an album facet in search for non-Official-Artist-Channel artists** — no "Albums" chip, and
`search?filter=albums` returns a "No results" `messageRenderer` regardless of the params (app's hard-
coded vs YouTube's current context segment) or authentication. Mainstream/OAC artists still get the
facet. The albums still exist as real `MPREb_…` entities — **only on the artist's `/browse` page now.**
(Reproduce: `album-facet-probe.mjs`, `verify-album-fix.mjs`.)

### The app-side bug + fix

`ArtistScreen` special-cased the "Albums" section's "see all" to navigate to `search?filter=albums`
(the now-dead facet), while **every other section** navigated via its own `section.moreEndpoint` — the
artist's item grid (then loaded by `YouTube.artistItems()`), which sourced straight from the `/browse`
page where the albums still live. The fix removed that special-case so "Albums" used `moreEndpoint`
like the rest. Verified: the album grid returned 34 / 19 albums for Shwekey / Levine. (Historical:
the artist page has since moved to the Zemer server entirely and `artistItems()` is deleted — this
section is kept as the record of why YouTube album search is empty for independent artists.)
