# Home rows — telemetry-ranked, zero-InnerTube home tab

Hand-authored docset for the **home tab**: how each row is sourced, why the tab makes no InnerTube
call for content, and the rules that must not regress. Full app↔server design lives in the handoff
thread `~/zemer-fix/handoff-docs/zemer-app-home-rows-request.md` + `home-rows-plan.md`.

## TL;DR

The old home tab scraped InnerTube on every load (`YouTube.artist().sections`, `getChartsPage()`,
`YouTube.home()`/`explore()`), shuffled, and showed random YouTube-shaped content — most of which
either rendered nothing (charts/home/explore carry ~no whitelisted artists) or was arbitrary. It now
serves **what the Zemer audience actually plays**, and touches InnerTube for **zero** content:

1. **Featured Albums / Videos / Artists / Playlists** come from the Zemer `GET /home-rows` endpoint —
   ranked by real distinct-device listening (albums/videos/artists) and YouTube view count (community
   playlists), 30-day live window, whitelist-pure + content-filtered server-side.
2. **Quick Picks / Keep Listening / Forgotten Favorites** come from local Room. A brand-new user's empty
   Quick Picks seeds from the Zemer `auto-top-50` curated playlist, not YouTube.
3. **Latest Releases** comes from the flipphoneguy feed; **Zemer Playlists** from `/zemer-playlists`.
4. **Zemer Radio** (under Zemer Playlists) comes from `GET /stations` — the synchronized broadcast
   stations, with a live now-playing line per card (lifecycle-scoped 60s on-screen ticker) and a
   See-all grid (`zemer_stations`). Fail-soft like every Zemer row; full detail in
   `docs/stations/README.md`.
5. The **mainstream Trending row is removed** (it filtered to empty and never displayed).

The **only** remaining `YouTube.*` call in `HomeViewModel` is `accountInfo()` — a signed-in user's own
name/avatar for the account card. There is **no InnerTube scrape fallback**: if `/home-rows` is
unreachable, the featured rows just hide (local rows + Latest Releases still populate).

## Direction — this is a template, not a one-off

Progressively **replacing as much InnerTube as we can across the app** with Zemer-served, whitelist-pure
data is a real, ongoing project goal. The home tab migrated first; artist opens (`/artist`), album
opens (`/album`), all radio (`/radio` — including this tab's Radio mode), and every single-song tap
(seed-first song radio) have since followed, each deleting its InnerTube path. Remaining discovery
surfaces (browse shelves, mood/genre, charts, related/recommendations) are candidates to move
the same way — a Zemer endpoint (or a handoff request for one) rather than a YouTube feed. Streaming and
playback are the exception: they need InnerTube + the cipher and stay (see `AGENTS.md` §The streaming
pipeline). This doc is about content *discovery*, where YouTube's global feeds carry almost no kosher
content to begin with.

| Where | File |
|---|---|
| Home orchestration | `app/.../viewmodels/HomeViewModel.kt` (`load()`, `loadHomeRows()`, `seedQuickPicksFromZemer()`) |
| Endpoint client | `app/.../search/ZemerSearchClient.kt` (`homeRows()`) → `GET https://search.zemer.io/home-rows` |
| Repository | `app/.../search/ZemerSearchRepository.kt` (`homeRows()`) |
| Wire → native items | `app/.../search/ZemerResultMapper.kt` (`homeRows()` → `HomeRows`) |
| Wire models | `app/.../search/ZemerSearchModels.kt` (`ZemerHomeRowsResponse`, `ZemerAlbum/Track.artistId`) |
| Render | `app/.../ui/screens/HomeScreen.kt` |
| "See all" | `app/.../ui/screens/HomeSeeAllScreen.kt`, `app/.../viewmodels/HomeSeeAllStore.kt` |

## The `/home-rows` contract

```
GET https://search.zemer.io/home-rows?allowFemale=0&blockVideos=1&kidZone=0
→ { topAlbums:[ZemerAlbum+artistId+explicit], topVideos:[ZemerTrack+artistId],
    topArtists:[ZemerArtist], topCommunity:[ZemerPlaylist] }
```

- All three content flags sent explicitly every request (server is default-OPEN; `kidZone` always `0`
  from home — home is never reachable from the KidZone tab).
- Cards carry the **artist channel id** (`artistId` / `ZemerArtist.id`). This is load-bearing: the home
  one-per-artist `rotateByArtist` dedup and the female/israeli defence-in-depth both key on it and **no-op
  when it is null**.
- `topCommunity` = discovery-sourced community playlists, ranked by each playlist's own YouTube view
  count (no telemetry tagging, no cold-start). Backs the **Featured Playlists** row.

## Rules that must not regress

- **No InnerTube for content.** The featured rows have no scrape fallback; `loadHomeRows()` null → rows
  hide. `seedQuickPicksFromZemer` is the cold-start seed. The only `YouTube.*` in `HomeViewModel` is
  `accountInfo()`. Never reintroduce `loadFeaturedContent` / `loadFeaturedPlaylists` / `YouTube.home()` /
  `getChartsPage()` on this tab.
- **Ranked content gate ≠ home gate.** `isAllowedRanked` applies female/israeli/blocked-ids ONLY, NOT the
  famous/american quality proxy in `isBlockedArtist`. Real listening reach supersedes the proxy; applying
  it cut the rows to near-empty (measured: albums 40→7, videos 19→2). See handoff REPLY 3.
- **Server routing on tap.** Zemer-sourced albums/playlists open via `onlineAlbumRoute` /
  `onlinePlaylistRoute` (`?zemer=true`), gated on `featuredAlbumsAreZemer` / `featuredPlaylistsAreZemer`,
  so the opened screen is whitelist-scoped and bot-gate-proof.
- **The shuffle button is "Radio mode"** — `HomeViewModel.shuffleRadioQueue()` →
  `ZemerRadioQueue(kind = "shuffle", seed = null)`, a whole-catalog, whitelist-pure Zemer `/radio`
  station. The old lucky-item InnerTube radio and its `radioEndpoint != null` pool are GONE — the tab's
  last InnerTube content path went with them; don't reintroduce a per-item radio-endpoint filter.
- **"See all" reads the published snapshot.** `HomeSeeAllStore` holds the FULL (un-rotated) filtered pool
  `HomeViewModel` publishes each load; the see-all pages render straight from it (no re-fetch, no
  re-filter), so they can never disagree with the row. Featured grids are 2-column.
- **Row sizing.** Featured rows `rotateByArtist(maxPerArtist=1, target=20)`; Featured Playlists `target=8`.
  Community playlists have no curator id, so the dedup is a pass-through (server already applies the
  female-owner hide + member survival + blocked-ids).

## Why the removed subsystems produced nothing

- `YouTube.getChartsPage()` browses `FEmusic_charts` (mainstream YT Music) — ~no whitelisted artists, so
  the Trending row filtered to empty and never rendered.
- `YouTube.home()`/`explore()` results (`homePage`/`explorePage`) were stored in UI state but **never
  drawn**; `home()` only seeded cold-start Quick Picks (now Zemer), `explore()` only fed a dedup set.
- The InnerTube featured scrape (`loadFeaturedContent`, `YouTube.artist()`) only ever ran as a thin-row /
  outage fallback; live data showed a healthy server never returns a row under the old `MIN_ROW=4`
  (albums 20 / videos 14 / artists 20 / community 8), so it only covered the outage case — dropped in
  favour of hiding the row (a Kosher home shows no YouTube-shaped content even during a Zemer outage).
