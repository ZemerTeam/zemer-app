# JewishStatus API

The public Supabase (PostgREST) API behind jewishstatus.com, as called by
`statuses/StatusesApi.kt` (a `supabase-category` provider). The REST base, key and category UUIDs come
from the status-sources config (see [README](README.md)); only the Cloudflare R2 media CDN host is baked
in (the `CDN` constant). No `Origin`/`Referer` is required.

Every request sends:

```
apikey: <key>
Authorization: Bearer <key>
Content-Type: application/json     # POST/RPC only
```

## Endpoints the app calls

### Creators - `POST <base>/rpc/browse_creators_sorted`

One call series per configured category, fetched concurrently (`fetchStatusCreators`), de-duplicated by
`id`; creators with an empty `recent_post_ids` are dropped (they would render an empty ring).

```json
{ "p_section": "all", "p_search": null, "p_limit": 100, "p_offset": 0,
  "p_category": "<category-uuid>", "p_location": null, "p_sort": "recent" }
```

Paginated 100/page until a short page.

### Statuses - `GET <base>/public_posts`

All posts for one creator, oldest first, paginated 100/page (`fetchStatusPosts`):

```
GET /public_posts
    ?creator_id=eq.<creator-uuid>
    &select=id,kind,media_path,thumb_path,caption,text_body,text_bg_color,link_url,duration_seconds,posted_at,view_count,download_count
    &order=posted_at.asc
    &limit=100&offset=<n>
```

No `is_featured` prioritization - pinning featured posts first would scramble the timeline.

### Post kinds - `GET <base>/public_posts?id=in.(…)&select=id,kind`

Resolves the kind of each creator's `recent_post_ids` (the browse RPC carries ids only) so the ring can
drop kinds the content filter hides (`fetchJewishPostKinds`, 100 ids per call). The repository runs it
AFTER publishing the creators, off the critical path; an unresolved id stays unknown and is shown.

## Fields the app reads

**Creator** (`parseCreators`): `id`, `slug`, `display_name`, `avatar_path` (relative, `<CDN>/avatars/…`),
`recent_post_ids` - **oldest-first**, so the newest status is the LAST id (drives the ring segments and
the caught-up state).

**Post** (`parsePosts`): `id`, `kind` (`video`/`image`/`text`), `media_path` / `thumb_path` (relative,
`<CDN>/status-media/…`), `caption`, `text_body`, `text_bg_color` (`#RRGGBB`), `link_url`,
`duration_seconds` (images/text default to 7 s in the viewer), `posted_at` (ISO-8601 UTC - display in the
device zone), `view_count`, `download_count`.

## Gotchas

- **`org.json` `optString` returns the literal `"null"`** for a JSON `null` on Android. Read nullable
  fields through `optStringOrNull` (checks `isNull` first), or a text status renders the word "null".
- **A text status's body is in `text_body`, not `caption`** - a query that omits `text_body` shows an
  empty status.
- **Videos never show `thumb_path` as a poster** (low-res, reads as a blurry flash): the viewer holds a
  black cover until the first frame, adding `StatusLoadingIndicator` only if still not ready after
  0.75 s. `thumb_path` is used only by `prefetchStatusImage` to warm a neighbor creator's cube face.

Parsing is unit-tested in `StatusesApiTest`.
