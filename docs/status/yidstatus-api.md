# YidStatus API

The public Supabase API behind yidstatus.com (custom API domain fronting a Supabase project), as called
by `statuses/YidStatusApi.kt` (a `keyword-feed` provider). The base URL, anon JWT and music keywords come
from the status-sources config (see [README](README.md)); the feed path and `Origin` header are baked in.

## The one call: `POST <base>/functions/v1/feed`

```
POST <base>/functions/v1/feed
apikey: <key>
Content-Type: application/json
Origin: https://yidstatus.com

{ "days": 1, "since": null }
```

- **`Origin` is mandatory.** The edge function returns `403 {"error":"Forbidden"}` without it, and
  `HttpURLConnection` silently drops `Origin` (a JDK/Android restricted header) - hence the OkHttp
  client (`yidHttpClient`). A non-2xx throws; the repository treats it as fail-soft.
- **`days` stays at `YID_FEED_DAYS` = 1.** The feed is GLOBAL (all categories, no server-side category
  filter), returns every status in the window in one response, and the edge function hard-errors past
  ~15 days. `since` is always null.
- **No per-creator endpoint.** The statuses table is not publicly readable, so a creator's posts are only
  what the feed carried: the repository primes its posts cache from the feed and never calls a
  per-creator fetch for a feed creator, and there is no deep "jump to date" history for YidStatus.

The app reads only the envelope's `influencers` and `statuses` arrays (`storyAds`, `placements`,
highlights etc. are ignored).

## Fields the app reads

**Influencer** (`parseYidCreators`): `id`, `slug`, `name`, `avatar_url` (full URL), `category` +
`categories` (matched against the keywords), and `paused` / `unlisted` / `review_hidden` (any true ->
excluded).

**Status** (`parseYidStatuses`): `id`, `influencer_id` (the creator linkage), `type`
(`video`/`image`/`text` kept; `audio` and unknown dropped), `is_ad` (true -> dropped), `media_url` and
`poster_url` (full URLs; `poster_url` is null for images), `caption` (a `text` status's body),
`background_color`, `link_title`, `duration_seconds`, `timestamp` (ISO-8601 UTC).

## Music filter and grouping

A creator is kept when any `category`/`categories` entry contains any configured keyword (substring,
case-insensitive; `YidStatusApiTest` pins the matching with an example set). Statuses are grouped by
`influencer_id` and sorted oldest-first per creator (the ring and resume logic assume ascending time);
creators with no status in the window are dropped, and each kept creator's `recentPostIds` /
`recentPostKinds` come from its grouped statuses.

Tests: `YidStatusApiTest`.
