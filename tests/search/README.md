# tests/search — search-path harness

Exercises the app's **one remaining** YouTube Music search function, **exactly as the app does**,
against the live API, and reports any error: a strict-deserialization break, a parser drop, or an
unexpectedly empty result.

Zemer is the app's only search *engine*; the one InnerTube search left is `YouTube.search(query,
filter)`, called by `RecognitionResolver`, the Android Auto voice search (`MediaLibrarySessionCallback`)
and the add-to-playlist online search dialog (`AddToPlaylistDialogOnline`).

## What it reproduces

The app's search entry point, on the `WEB_REMIX` client:

| App function (`YouTube.kt`) | Request | Parser |
| --- | --- | --- |
| `search(query, filter)` x6 filters | POST `search` (params) | `SearchPage.toYTItem` |

**Faithfulness facts** (verified against `InnerTube.kt`):
- Search runs with `setLogin = false, sendVisitorData = false` — the app sends **no visitorData, no
  cookie and no Authorization** (`InnerTube.kt` `search()`; a shared/stale visitorData can make search
  silently return empty). **Known drift:** the harness (`lib.mjs`) still sends `visitorData` (it reads
  `innertube_cookie.txt` only to reuse it, as `X-Goog-Visitor-Id` + `context.client.visitorData`), so
  it is not app-exact on that one point.
- The 6 `SearchFilter` param strings, the request body shape, and the section-walking logic
  (`musicShelfRenderer` + `itemSectionRenderer`, `distinctBy id`) are copied verbatim.
- `lib.mjs` / `parsers.mjs` are line-for-line ports of the InnerTube helpers, the
  `MusicResponsiveListItemRenderer` accessors, and the `toYTItem` parser. A drop here = a drop in the app.

## The strict-deserialization check (the big one)

The app decodes with kotlinx `ignoreUnknownKeys=true`, `explicitNulls=false`, **no
`coerceInputValues`**. So a Kotlin property that is **non-null and has no default** is REQUIRED: if
YouTube stops sending it, `body<SearchResponse>()` throws `MissingFieldException` and the **entire**
response fails — `YouTube.search()` returns a failure, which its callers turn into no results (or an error).
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
node tests/search/pill-survival.mjs             # per filter, how many results survive the whitelist id match
node tests/search/album-facet-probe.mjs         # which artists get an "Albums" search chip (see below)
node tests/search/verify-album-fix.mjs          # albums on the artist /browse page vs album search
```

`diag-auth.mjs` holds authenticated `search`/`browse` helpers for these probes — **diagnostic only**,
not a model of the app's real (unauthenticated) search path.

### Lyrics-source research probes (one-off, not CI)

The `lyrics-*.mjs`, `jyrics-*.mjs`, `jkaraoke-resolve.mjs`, `corpus-*.mjs`, `drive-*-resolve*.mjs` and
`names-resolve-yt.mjs` scripts are the coverage/accuracy probes that chose the app's lyrics sources (Zemer resolver
sources, SimpMusic, LrcLib, YouTube). They write under the gitignored `tests/search/.cache/`; `corpus-*.mjs`,
`jkaraoke-resolve.mjs` and `drive-folder-resolve2.mjs` also read the sibling `zemer-search` repo's `data/corpus.db`
(+ `corpus/lyrics.mjs`) through `jyrics-common.mjs` (`ZEMER_SEARCH=/path/to/zemer-search` overrides the default
workspace sibling). Diagnostic only, never wired into CI.

## Out of scope (by design)

Zemer's **artist-whitelist filter** (`app/.../utils/WhitelistFilter.kt`) runs *after* `search()` at
its call sites and drops every item whose artist isn't whitelisted. It needs the app's Room DB, so it
can't run here — but it is the **next** suspect when the function is healthy and a caller still
comes up empty (an empty/un-synced whitelist drops everything).

## Findings

- A 300-artist whitelist sweep found no strict breaks and full parser extraction. Latent issue still in
  `toYTItem`: an artist row with no shuffle/radio endpoint is dropped (~1 in 6 artist searches).
- **Album search is empty for independent artists, and no request change fixes it.** YouTube offers no
  "Albums" facet in search for non-Official-Artist-Channel artists (no chip; `filter=albums` returns a
  "No results" `messageRenderer` regardless of params or auth), though their `MPREb_…` albums still
  exist on the artist's `/browse` page. Never source an artist's albums from YouTube album search
  (reproduce: `album-facet-probe.mjs`, `verify-album-fix.mjs`); the artist page is served by the
  Zemer server (`/artist`).
