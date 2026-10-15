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
- **Pinned providers** (additive `pinned`, default false, plus the display `name`): a pinned provider's
  creators carry `StatusCreator.pinnedSection` (= the provider name) and ALWAYS lead the Home row and the
  See-all (`sortedByUnseenFirst`: pinned in provider order, then unseen-first); their ring keeps its
  seen/unseen look, only the position is fixed. The See-all gives each pinned provider its own top
  section titled with its name, and `mergeStatusCreators` puts pinned creators first and lets them claim
  their names, so a same-named creator on any platform is dropped (never shown beside or instead of them). Today this is Zemer's own provider (`zemer`, served by the
  content mirror in the JewishStatus shape).
- **Persistence.** `StatusSourcesConfigKey` (raw JSON) + `StatusSourcesVersionKey` (the effective
  version), reloaded in `App.kt`; `StatusSourcesCache.update()` never installs an older version, so the
  startup restore cannot roll back a concurrent sync.

## Sponsored posts + exact views (Zemer's own provider)

Contract: `handoff-docs/zemer-app-sponsored-status-request.md`. Additive `public_posts` fields, present
only on ads: `sponsored`, `advertiser`, `link` (parsed by `parsePosts`; `ends_at` is enforced
server-side, which only serves an ad inside its window). Every ad is already labeled server-side
("Sponsored · <advertiser>" leading the caption / ending the text body), so older builds stay honest.

- **Viewer:** a "Sponsored" badge beside the creator name (`StatusStoryTopOverlay.badge`); the label line
  is dropped from the shown caption/body (`withoutSponsoredLabel`; copy + save keep the full text); a
  `link` shows an "Open link" pill (`Context.openStatusLink`, fail-soft when a filter blocks browsers).
- **Rotation:** `rotateSponsored(seed = telemetry device id + UTC date)` shuffles ads among their own
  slots within a posting date; normal posts keep their chronological slots. Applied in `StoryViewModel`
  with the content filter so every viewer path sees one order.
- **Exact views:** for PINNED creators only, a post with >= 1 s of VISIBLE time (frame clock, so
  backgrounded time never counts; a video only after its first frame, so a video that never rendered is
  never a view) reports
  `{"type":"status","kind":"view","id","secs"}` (secs capped at its duration, else the player's), once per post per device
  per UTC day (persisted `status_views_sent`, `recordDailyStatusView`); the Open-link pill reports
  `kind":"tap"`. See `docs/tracking/README.md`.
- **Landscape media:** `StatusVideoSurface` switches ZOOM -> FIT for a square/landscape video wider than the surface
  (`statusVideoResizeMode`), so landscape posts from any platform are letterboxed, not cropped.

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
