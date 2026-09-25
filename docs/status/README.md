# Music Status - third-party status platforms

The **"Music Status"** Home row and story viewer draw WhatsApp/Stories-style "status" content from two
**third-party platforms we do not control**. This folder is the platform-API reference (hand-authored,
not generated) for what the app calls; the app-side architecture (fail-soft isolation, the shared
`StatusesRepository`, the viewer's no-flash invariants, the content filter, live refresh) is in the
`AGENTS.md` "Music Status" section. The API shapes were reverse-engineered from the platforms' public
web apps and can change without notice - every call is fail-soft, and a dead source just hides its
creators.

| Platform | Doc | Backend | Client |
|----------|-----|---------|--------|
| **JewishStatus** | [jewishstatus-api.md](jewishstatus-api.md) | Supabase PostgREST + Cloudflare R2 | `statuses/StatusesApi.kt` (`HttpURLConnection`) |
| **YidStatus** | [yidstatus-api.md](yidstatus-api.md) | Supabase (custom domain) + Supabase Storage | `statuses/YidStatusApi.kt` (**OkHttp**) |

Creators from both are merged into one row, de-duplicated by normalized name (`mergeStatusCreators`;
JewishStatus wins a tie); the See-all screen groups them back by `StatusCreator.source`.

## At a glance

| | JewishStatus | YidStatus |
|---|---|---|
| Creator list | `POST /rpc/browse_creators_sorted` per configured category | the feed's `influencers` array |
| Status read | `GET /public_posts?creator_id=eq.…` (per creator, paginated) | the same `POST /functions/v1/feed` call (one global response) |
| Auth | publishable anon key as `apikey` + `Authorization: Bearer` | anon JWT as `apikey` |
| Access gate | none | **the feed requires `Origin: https://yidstatus.com`** |
| Media | relative paths, prefixed with the R2 CDN client-side | full URLs in the response |

## Field mapping (both -> `StatusCreator` / `StatusPost`)

| App field | JewishStatus | YidStatus |
|-----------|--------------|-----------|
| creator `id` / `slug` | `id` / `slug` | `id` (= status `influencer_id`) / `slug` (falls back to `id`) |
| `displayName` | `display_name` | `name` |
| `avatarPath` | `avatar_path` (relative) | `avatar_url` (full) |
| `recentPostIds` | `recent_post_ids` (oldest-first) | derived from the grouped statuses |
| `recentPostKinds` | resolved separately (`fetchJewishPostKinds`) | the grouped statuses' `type` |
| status `kind` | `kind` | `type` (`audio` dropped) |
| `mediaPath` / `thumbPath` | `media_path` / `thumb_path` (relative) | `media_url` / `poster_url` (full) |
| `caption` / `textBody` | `caption` / `text_body` | `caption` -> `textBody` for a `text` status, else `caption` |
| `textBgColor` | `text_bg_color` | `background_color` |
| `linkUrl` | `link_url` | `link_title` |
| `durationSeconds` | `duration_seconds` | `duration_seconds` |
| `postedAt` (ISO-8601 UTC) | `posted_at` | `timestamp` |

`statusMediaUrl` / `statusAvatarUrl` pass an `http…` path through unchanged and prefix anything else
with the R2 CDN, so the viewer is source-agnostic.

## Server-driven source config (which categories / keywords count as "music")

The platform base URLs, keys and "music" filters are **not in the APK**: they sync (version-gated) from
the content mirror, `GET content.zemer.io/status-sources` (+ `/status-sources/version`), so the owner can
retune, dark or add a source without a release. Only the filter *config* is centralized; status content
still comes straight from the third parties. Contract:
`handoff-docs/zemer-status-sources-config-request.md`.

- **Typed descriptors.** `{ version, providers: [ { id, type, baseUrl, apiKey, categoryIds | musicKeywords,
  enabled } ] }`, one handler per `type` (`StatusProviderType`): `supabase-category` (JewishStatus
  shape) and `keyword-feed` (YidStatus shape). A new provider of an existing type is config-only; a new
  `type` needs an app change. `baseUrl` is trailing-slash-trimmed at parse time.
- **Protocol details stay in the handler.** The R2 CDN host (`StatusesApi.kt`) and the YidStatus
  `/functions/v1/feed` path + `Origin` header (`YidStatusApi.kt`) are baked into their type's handler,
  never carried by a descriptor.
- **No baked-in fallback.** Until a device's first successful sync it has no config and the row is
  hidden (the worst case is an absent row, never wrong content).
- **Fail-soft parse** (`parseStatusSourcesConfig`): null ONLY when a valid config cannot be obtained
  (blank, non-JSON, no `providers` array; the mirror's `503` never parses) -> the caller keeps its
  last-good config. A valid config is honored as-is even when nothing in it is usable (an intentional
  dark). A disabled provider, an unknown `type`, a missing `id`/`baseUrl`/`apiKey`, or an empty filter
  list is skipped non-fatally.
- **Sync** (`StatusesRepository.syncStatusSources`, via `ZemerContentClient.statusSourcesVersion` /
  `statusSourcesRaw`): runs on the `refreshCreators` path - AWAITED only while nothing has synced yet,
  non-blocking after; one attempt per `STALE_MS` window (pull-to-refresh bypasses it); quiet on failure.
  The installed version is `minOf(body, endpoint)` so a stale CDN body can never suppress future syncs.
  Family caches are stamped with the config version they loaded under, so a config change makes them
  stale immediately; a failed provider keeps its previous creators while its siblings refresh.
- **Persistence.** `StatusSourcesConfigKey` (raw JSON) + `StatusSourcesVersionKey` (the effective
  version), reloaded in `App.kt`; `StatusSourcesCache.update()` never installs an older version, so the
  startup restore cannot roll back a concurrent sync.

## Integration rules

- **YidStatus must use OkHttp.** The feed 403s without `Origin: https://yidstatus.com`, and
  `HttpURLConnection` silently drops `Origin` (a restricted header).
- **Keep the YidStatus window small.** The feed is global (all categories) and returns everything for
  the window in one response; the app fetches `days = 1` (`YID_FEED_DAYS`). Never widen it on the hot path.
- **Filter non-content.** YidStatus ads (`is_ad`), `paused` / `unlisted` / `review_hidden` influencers,
  and `audio` statuses are dropped (the viewer renders only video/image/text); the envelope's
  `storyAds` / `placements` are never read.
- **Music-only.** YidStatus creators are kept only when a `category`/`categories` entry contains a
  configured `musicKeywords` entry (substring, case-insensitive); Comedy and general Entertainment are
  deliberately excluded (owner decision). JewishStatus is scoped by its configured category UUIDs.
- **Keys are client-safe.** The anon/publishable keys are the ones each platform ships in its public web
  bundle (RLS-scoped, read-only); they arrive via the config and are not recorded in these docs.
