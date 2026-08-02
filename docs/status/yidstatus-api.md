# YidStatus API (yidstatus.com)

Public API behind **yidstatus.com**, a second WhatsApp/Stories-style platform for Jewish/kosher creators
(video / image / text / audio "statuses"). Much larger than JewishStatus (hundreds of creators across many
categories). Reverse-engineered from the site's web bundle and verified against the live endpoints
**2026-08-02**.

## Hosts & auth

| | Value |
|---|---|
| API base | `https://api.yidstatus.com` (custom domain fronting Supabase project `fsinwalqhgwapevwibmd`) |
| REST base | `https://api.yidstatus.com/rest/v1` |
| Edge functions | `https://api.yidstatus.com/functions/v1/<name>` |
| Media / storage | `https://fsinwalqhgwapevwibmd.supabase.co/storage/v1/object/public/status-media/...` (full URLs are returned in responses) |
| Auth | Supabase **anon** JWT, sent as the `apikey` header |
| Key | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZzaW53YWxxaGd3YXBldndpYm1kIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI2ODEyODUsImV4cCI6MjA5ODI1NzI4NX0.ZwrXgeUknPSDAWsOzdI8jdj7wCO9xOe7glLSj3OB_vA` |

The key is **client-safe** (RLS-scoped anon role, read-only) and is shipped in the yidstatus public web
bundle. It is **not** a secret. (The site also loads a *second* Supabase project,
`hioieoplqqefurwznrol`, for its own analytics/profiles/favorites; the content lives on
`api.yidstatus.com` only.)

### CRITICAL: the feed requires an Origin header

`POST /functions/v1/feed` returns `403 {"error":"Forbidden"}` unless the request carries:

```
Origin: https://yidstatus.com
```

This is a server-side check inside the edge function (confirmed: identical request with the header
returns `200`; without it, `403`). The CORS preflight advertises
`access-control-allow-origin: https://yidstatus.com`. A **native client** (OkHttp / `HttpURLConnection`)
can set `Origin` freely (there is no browser CORS enforcement off-browser), so the app can read the feed
by sending that header. It is trivially spoofable, so it works today, but treat it as fragile: the
platform can change the allowlist, add Turnstile, or rate-limit by IP at any time. Fail soft.

The plain PostgREST RPCs (below) are **not** Origin-gated; they work with just the `apikey` header.

## Endpoints

### 1. Creator strip - `POST /rpc/avatar_strip`

A small, weighted, shuffled subset of creators for the story strip. Works with just `apikey`.

```
POST /rest/v1/avatar_strip
apikey: <key>
Content-Type: application/json

{ "p_limit": 60 }
```

Returns an array of `{ id, name, avatar, avatar_url, color, verified }`. Useful for a lightweight creator
list, but it does **not** include statuses and is a curated subset, so for full data prefer the feed.

### 2. The feed - `POST /functions/v1/feed`  (primary)

Returns EVERYTHING for a rolling window in one call: every eligible influencer plus every status in the
last `days` days. This is the main read path.

```
POST /functions/v1/feed
apikey: <key>
Content-Type: application/json
Origin: https://yidstatus.com

{ "days": 1, "since": null }
```

- `days` (int): rolling window size. `days:7` returned ~19 MB (313 influencers, 13,380 statuses), so use a
  **small window** (1-2) on the hot path.
- `since` (string|null): ISO timestamp cursor for **incremental** fetches (statuses newer than `since`).
- The site retries up to 3 times with backoff; a `4xx` is terminal.

Response envelope:

| Key | Type | Notes |
|-----|------|-------|
| `influencers` | array | All creators (see [Influencer](#influencer)). |
| `statuses` | array | All statuses in the window; each carries `influencer_id` (see [Status](#status)). |
| `flags` | array | `{key,value}` feature flags. |
| `placements` | array | Ad placements. Not content. |
| `storyAds` | array | Story ads. Not content - filter out. |
| `highlights` / `highlightStatuses` | array | Curated highlight reels + their statuses. |
| `channelMessages` | array | Channel messages. |

Group `statuses` by `influencer_id` to reconstruct per-creator stories; sort each group by `timestamp`.

### 3. Reactions / telemetry (write, optional)

- `POST /rpc/react_to_status` body `{sid, vis, emoji}` - react to a status.
- `POST /rpc/record_site_event` body `{ev, sid, iid, vis, extra}` - the site's own analytics. Do not call.
- `POST /rpc/popular_search_terms` body `{p_days, p_limit}` - search suggestions.

### 4. Admin (do not use)

`POST /functions/v1/admin` dispatches admin actions (`login`, `backfill`, `update`, `delete_status`,
`group_feed`, ...) and requires an authenticated admin session. Listed only so it is not mistaken for a
public read path. Likewise `admin-analytics`, `admin-businesses`, `admin-files`, `admin-lite`,
`admin-settings`, `account-merge`, `signup`, `member`, `report`, `subscribe`, `reset-code`, `business`.

## Data models

### Influencer

Full key set: `address, avatar, avatar_url, bio, categories, category, color, contact_email,
contact_prefs, created_at, custom_links, id, kind, locations, messaging_disabled, name, paused,
public_wa, review_hidden, search_keywords, slug, sub_locations, unlisted, verified, weight`.

Fields that matter for a client:

| Field | Type | Notes |
|-------|------|-------|
| `id` | uuid | Creator id (== `status.influencer_id`). |
| `name` | string | Display name. |
| `slug` | string | URL slug. |
| `avatar_url` | string | **Full** avatar URL (Supabase storage). May be null; `avatar` holds initials as a fallback. |
| `color` | string | Hex accent color. |
| `verified` | bool | Verified badge. |
| `category` / `categories` | string / string[] | Category taxonomy (see [music filter](#music-category-filter)). |
| `kind` | string | `influencer` (201) or `business` (112). |
| `paused` / `unlisted` / `review_hidden` | bool | **Exclude** any that are true. |
| `weight` | number | Ranking weight. |

### Status

Full key set: `background_color, caption, channel_at, creator_keywords, duration_seconds, featured_at, id,
influencer_id, is_ad, link_description, link_image_checked_at, link_image_url, link_preview, link_title,
media_url, ocr_text, poster_url, promoted_*, reactions, seed_reactions, site_*, sponsor, summary, tags,
text_color, text_font, timestamp, topics, type, views`.

Fields that matter:

| Field | Type | Notes |
|-------|------|-------|
| `id` | uuid | Status id. |
| `influencer_id` | uuid | Owning creator (the linkage - the feed is global). |
| `type` | string | `video` \| `image` \| `text` \| **`audio`**. Counts in one 7-day pull: video 5678, image 5324, text 2358, audio 20. |
| `media_url` | string? | **Full** media URL. Null for `text`. |
| `poster_url` | string? | **Full** thumbnail/poster URL. Present for `video`; **null for `image`** (use `media_url` as the frame). |
| `caption` | string? | Caption; for a `text` status this holds the body text. |
| `summary` | string? | Short summary (sometimes present alongside caption). |
| `background_color` | string? | `#RRGGBB` for a `text` status. |
| `text_color` / `text_font` | string? | Text-status styling. |
| `duration_seconds` | int? | For `audio`/`video`; may be null. |
| `timestamp` | string | ISO-8601 UTC, e.g. `2026-08-02T16:04:06+00:00`. Convert to device-local for display. |
| `is_ad` | bool | **Exclude** when true (ads). |
| `sponsor` | object? | Sponsor info when sponsored. |
| `link_title` / `link_description` / `link_image_url` / `link_preview` | - | Rich link-preview data. |

## Media URLs

Unlike JewishStatus, media URLs come back **fully qualified** in the response (no prefixing needed), e.g.

```
avatar : https://fsinwalqhgwapevwibmd.supabase.co/storage/v1/object/public/status-media/avatar-<id>.jpg
media  : .../status-media/status/<HASH>.mp4   (or .jpg / .ogg)
poster : .../status-media/posters/<HASH>.jpg
```

## Music-category filter

The feed is all-categories (News, Entertainment, Services, Real Estate, etc.). To keep the app kosher and
on-topic, filter influencers to the **music** categories. Category counts in the sampled feed:

| Music-relevant category | Influencers |
|-------------------------|-------------|
| `Music` | 15 |
| `Singer` | 6 |
| `Kumzits` | 2 |
| (adjacent, decide per product) `Simcha / Events` | 5 |
| (adjacent) `Entertainment` | 18 |
| (adjacent) `Comedy` | 4 |

Match case-insensitively against BOTH `category` and every entry of `categories` (a creator can carry
several). Everything else (News, Services, Real Estate, Kosher Food, Business, ...) is excluded.

## Differences from JewishStatus (for a shared client)

- **One global feed** vs per-creator pagination. Fetch once, group by `influencer_id`, sort by
  `timestamp`. There is no `recent_post_ids` - derive the ring/segment count from the grouped statuses.
- **Full media URLs** vs relative paths.
- **`audio` status type** exists (no JewishStatus analog) - handle or skip.
- **`is_ad` / `storyAds` / `placements`** must be filtered for kosher content.
- **Origin header** is mandatory for the feed (see above).
- **Timestamps** field is `timestamp` (not `posted_at`), still UTC.
- **Category filter** is required (the platform is not music-only).

## Verification snippet

Confirmed working (2026-08-02):

```
curl -s -X POST "https://api.yidstatus.com/functions/v1/feed" \
  -H "content-type: application/json" \
  -H "apikey: <anon-key>" \
  -H "Origin: https://yidstatus.com" \
  -d '{"days":1,"since":null}'
```

Returns `{ influencers:[...], statuses:[...], flags, placements, storyAds, highlights, highlightStatuses,
channelMessages }`. Omitting the `Origin` header returns `403 {"error":"Forbidden"}`.
