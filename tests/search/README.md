# tests/search — search-path harness

Exercises **every** YouTube Music search function the app calls, **exactly as the app does**, against
the live API, and reports any error: a strict-deserialization break, a parser drop, the
`searchContinuation` `!!` NPE, or an unexpectedly empty result. Built to answer "why is search not
working?" with measured data instead of guesses (same philosophy as the parent `tests/` harness).

## What it reproduces

The app's search entry points, all on the `WEB_REMIX` client:

| App function (`YouTube.kt`) | Request | Parser |
| --- | --- | --- |
| `searchSuggestions(query)` | POST `music/get_search_suggestions` | `SearchSuggestionPage` |
| `searchSummary(query)` | POST `search` (no params) | `SearchSummaryPage` (card + sections) |
| `search(query, filter)` x6 filters | POST `search` (params) | `SearchPage.toYTItem` |
| `searchContinuation(token)` | POST `search?continuation=&ctoken=` | `SearchPage.toYTItem` |

**Faithfulness facts** (verified against `InnerTube.kt`):
- Search runs with `setLogin = false` — the app sends **visitorData only, no cookie/Authorization**.
  The harness matches this (it does read `innertube_cookie.txt` only to reuse its `visitorData`).
- The 6 `SearchFilter` param strings, the request body shape, and the section-walking logic
  (`musicShelfRenderer` + `itemSectionRenderer`, `distinctBy id`) are copied verbatim.
- `lib.mjs` / `parsers.mjs` are line-for-line ports of the InnerTube helpers, the
  `MusicResponsiveListItemRenderer` accessors, and the four parsers. A drop here = a drop in the app.

## The strict-deserialization check (the big one)

The app decodes with kotlinx `ignoreUnknownKeys=true`, `explicitNulls=false`, **no
`coerceInputValues`**. So a Kotlin property that is **non-null and has no default** is REQUIRED: if
YouTube stops sending it, `body<SearchResponse>()` throws `MissingFieldException` and the **entire**
response fails — `YouTube.search*().getOrNull()` swallows it to `null` and the UI shows "No results".
One missing field kills every result.

`schema.mjs` encodes, per renderer reachable from a search response, which fields are required vs
optional (transcribed from the Kotlin models) and `validate()` walks the live JSON the same way
kotlinx would, flagging every non-null field the server omitted. **When a model's nullability
changes, update the matching entry here.** Subtrees that never appear in a search response
(`gridRenderer`, `musicQueueRenderer`, …) are intentionally unencoded and reported in `unencoded` if
ever met, so the sweep never silently skips something.

## Run

```bash
node tests/search/run.mjs                       # default query set, every function
node tests/search/run.mjs "mordechai shapiro"   # one query
node tests/search/run.mjs q1 q2 ...             # several queries
SAVE=1 node tests/search/run.mjs                # also dump raw JSON to tests/search/out/
node --test tests/search/self-test.mjs          # prove the checker catches breaks (no network)
```

Exit code: `0` = no whole-response killers; `1` = a strict break or continuation NPE was found.

## Out of scope (by design)

Zemer's **artist-whitelist filter** (`app/.../utils/WhitelistFilter.kt`) runs *after* these functions
in `OnlineSearchViewModel` and drops every item whose artist isn't whitelisted. It needs the app's
Room DB, so it can't run here — but it is the **next** suspect when these functions are healthy and
search still looks empty (an empty/un-synced whitelist drops everything).

## Findings (2026-06-12, current `main`)

Across `searchSuggestions`, `searchSummary`, all 6 filters, and `searchContinuation` over multiple
queries: **no strict breaks, no continuation NPE, parsers extract 100% of music items.** The InnerTube
search layer is healthy. Observed non-issues:
- `FILTER_ALBUM` / `FILTER_FEATURED_PLAYLIST` come back empty for niche artists — YouTube itself
  returns a "No results" `messageRenderer` (mainstream artists like "taylor swift" return 20 albums).
  The params are identical to upstream Metrolist; not an app bug.
- `searchSummary` drops a few items per query — all `MUSIC_PAGE_TYPE_USER_CHANNEL` profiles and
  podcasts, which the app doesn't classify as music (and the whitelist would drop anyway).
