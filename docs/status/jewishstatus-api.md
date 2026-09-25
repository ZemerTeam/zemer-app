# JewishStatus API (jewishstatus.com)

Public API behind **jewishstatus.com**, a WhatsApp/Stories-style platform where Jewish/kosher creators
post short video / image / text "statuses". This is the source the app's **Music Status** feature uses
today (`com.jtech.zemer.statuses`). Verified against the live endpoints **2026-08-02**.

## Hosts & auth

| | Value |
|---|---|
| REST base | `https://raiodurvjneoehnphkrs.supabase.co/rest/v1` |
| Media CDN | `https://pub-0dd407ad34e240909673d1619658d5c2.r2.dev` (Cloudflare R2) |
| Auth | Supabase **publishable** anon key, sent as `apikey` **and** `Authorization: Bearer <key>` |
| Key | the platform's publishable anon key (delivered by the status-sources config; not recorded here) |

The key is **client-safe** (RLS-scoped, read-only) - it is the same key the JewishStatus web app ships
publicly. The app does NOT bake the REST base, key or category UUIDs in: they arrive from the server-driven
status-sources config (`content.zemer.io/status-sources`, `statuses/StatusSourcesConfig.kt`, a
`supabase-category` provider). Only the R2 media CDN constant is baked into `statuses/StatusesApi.kt`. The
values above and below are the platform's, as observed on the verification date.
No `Origin`/`Referer` header is required; these are plain public PostgREST calls.

Standard headers for every request:

```
apikey: <key>
Authorization: Bearer <key>
Content-Type: application/json     # POST/RPC only
```

## Endpoints

### 1. Creators - `POST /rpc/browse_creators_sorted`

Returns the creator list for one **category**, most-recent first. The app fetches every configured music
category concurrently and de-duplicates by `id`.

Request body:

```json
{
  "p_section":  "all",
  "p_search":   null,
  "p_limit":    100,
  "p_offset":   0,
  "p_category": "<category-uuid>",
  "p_location": null,
  "p_sort":     "recent"
}
```

Paginate with `p_limit`/`p_offset` (stop when a page returns `< p_limit`).

Music categories observed on the platform (the app's set comes from the status-sources config):

| Category | UUID |
|----------|------|
| Jewish Music | `dc207cab-3514-4ae8-a5c1-8a69fb27ced3` |
| Music industry | `02ed4e29-d461-43f4-9aab-e16d05d3f795` |
| Concerts | `5a08c0ba-400a-4576-aa33-97fa9ec38d0e` |

Response: array of creator objects (see [Creator](#creator)). Note the browse RPC **omits**
`is_verified`/`downloads_enabled` - fetch those separately if needed (endpoint 2).

### 2. Creator flags - `GET /public_creators`

Batch-fetch `is_verified` / `downloads_enabled` for creators the browse RPC didn't include them on.
Chunk the id list so the URL stays under proxy length limits (~100 ids per call).

```
GET /public_creators?select=id,is_verified,downloads_enabled&id=in.(<id1>,<id2>,...)
```

> The app previously used this to render a verified badge; that badge was removed, so the app no longer
> calls this endpoint. Documented for completeness.

### 3. Statuses (posts) - `GET /public_posts`

All visible posts for one creator, **oldest first**, paginated 100/page:

```
GET /public_posts
    ?creator_id=eq.<creator-uuid>
    &select=id,kind,media_path,thumb_path,caption,text_body,text_bg_color,link_url,duration_seconds,posted_at,view_count,download_count
    &order=posted_at.asc
    &limit=100&offset=<n>
```

Response: array of post objects (see [Status / post](#status--post)).

### 4. Post kinds - `GET /public_posts?id=in.(...)`

Batch-resolve the `kind` of each creator's `recent_post_ids`, so the story ring can drop statuses the
content filter hides (the browse RPC carries ids only). Chunked to 100 ids per call:

```
GET /public_posts?id=in.(<id1>,<id2>,...)&select=id,kind
```

Used by `fetchJewishPostKinds` (`StatusesApi.kt`); a failed/partial lookup leaves kinds unknown, which
shows all statuses.

## Data models

### Creator

From `browse_creators_sorted`. Full key set seen on a row:
`address, avatar, avatar_path, bio, category, categories, color, id, live_now, locations, name,
recent_post_ids, slug, sub_locations, updates_count, verified, ...`

Fields the app maps:

| Field | Type | Notes |
|-------|------|-------|
| `id` | uuid | Creator id (stable). |
| `slug` | string | URL slug. |
| `display_name` | string | Display name. |
| `avatar_path` | string? | Relative -> `<CDN>/avatars/<avatar_path>`. Empty string => treat as null. |
| `recent_post_ids` | string[] | Recent status ids, **oldest-first** (verified against `posted_at`). Drives the story ring (one segment each) and the "caught up" state. |
| `live_now` | bool | True when the creator posted within ~24 h. **Not** a live broadcast - low signal (true for nearly every recency-sorted creator). The app does not use it. |

Gotchas:

- **`recent_post_ids` is oldest-first**, so the newest status is the **last** id. (Confirmed by joining
  the ids against `public_posts.posted_at`.)
- **`updates_count` is `0`** on the browse RPC - use `recent_post_ids.length` for the count.

### Status / post

From `public_posts`.

| Field | Type | Notes |
|-------|------|-------|
| `id` | uuid | Status id. |
| `kind` | string | `video` \| `image` \| `text`. |
| `media_path` | string? | Relative -> `<CDN>/status-media/<media_path>`. |
| `thumb_path` | string? | Relative -> `<CDN>/status-media/<thumb_path>`. Videos carry a `.jpg` thumbnail. |
| `caption` | string? | Caption for media statuses. |
| `text_body` | string? | Body of a **text** status (text posts carry their text here, **not** in `caption`), on `text_bg_color`. |
| `text_bg_color` | string? | `#RRGGBB` background for a text status. |
| `link_url` | string? | Optional link. |
| `duration_seconds` | int? | Video length; images/text default to 7 s client-side. |
| `posted_at` | string | ISO-8601 **UTC/offset**, e.g. `2026-08-01T19:32:52+00:00`. Convert to device-local before display. |
| `view_count` / `download_count` | int | Counters. |

## Media URLs

```
avatar   : <CDN>/avatars/<avatar_path>
media    : <CDN>/status-media/<media_path>
thumbnail: <CDN>/status-media/<thumb_path>
```

`<CDN> = https://pub-0dd407ad34e240909673d1619658d5c2.r2.dev`.

## Client gotchas (learned the hard way)

- **`org.json.optString` returns the literal string `"null"`** for a JSON `null` on Android's runtime
  (the reference impl returns `""`). Guard with `isNull(key)` before reading - otherwise a text status
  body rendered as the word "null". (`StatusesApi.optStringOrNull`.)
- **Text body vs caption**: a `text` status stores its body in `text_body`, not `caption` - a query that
  forgets to `select` `text_body` shows an empty/`"null"` status.
- **Timestamps are UTC** - display in the device zone or times read as "wrong" by the user's offset.
- **Videos never show the `thumb_path` poster** (it is low-res and reads as a blurry flash): the story
  viewer holds a black cover until the first frame draws, adding the shared `StatusLoadingIndicator`
  (avatar + ring) only if the video is still not ready after 0.75 s. `thumb_path` is used only to
  prefetch a neighbor creator's image for the cube swipe (`prefetchStatusImage`); the saved-status grid
  decodes its own poster frame from the downloaded file.

## How the app uses it

`com.jtech.zemer.statuses`:

- `StatusesApi.kt` - `fetchStatusCreators(base, key, categoryIds)` (configured categories, concurrent,
  de-duped), `fetchStatusPosts(base, key, creatorId)` (paginated, `order=posted_at.asc`) and
  `fetchJewishPostKinds(base, key, ids)`. Only the R2 CDN constant lives here; base/key/categories come
  from the status-sources config.
- `StatusesRepository.kt` - session cache (`@Singleton`), shared creators `StateFlow`, per-creator posts
  cache; fail-soft.
- `StoryViewModel` / `ZemerStatusesViewModel` - the viewer + Home-row VMs.

App parsing is unit-tested in `app/src/test/.../statuses/StatusesApiTest.kt`.
