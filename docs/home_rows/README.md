# Home rows — telemetry-ranked, zero-InnerTube home tab

The **home tab**: how each row is sourced, why the tab makes no InnerTube call for content, and the
rules that must not regress. The app↔server design lives in `handoff-docs/zemer-app-home-rows-request.md`
+ `handoff-docs/home-rows-plan.md`.

## TL;DR

The home tab serves **what the Zemer audience actually plays** and touches InnerTube for **zero**
content (YouTube's global feeds carry almost no whitelisted artists, so scraping them rendered
nothing):

1. **Featured Albums / Videos / Artists / Playlists** come from the Zemer `GET /home-rows` endpoint —
   ranked by real distinct-device listening (albums/videos/artists) and YouTube view count (community
   playlists), 30-day window, whitelist-pure + content-filtered server-side.
2. **Quick Picks / Keep Listening / Forgotten Favorites** come from local Room. A brand-new user's empty
   Quick Picks seeds from the Zemer `auto-top-50` curated playlist, not YouTube. Presentation across
   loads is the pure `QuickPicksPresentation`: a pull-to-refresh keeps the displayed rows until the final
   list lands (no intermediate reshuffle), surviving items keep their position with newcomers appended,
   and a pool under `MIN_POOL_FOR_ROTATION` (8) allowed songs is shown whole (rotation would flip a tiny
   library's row between two subsets on every pull). Quick Picks and Forgotten Favorites (and their
   See-all) read the live Room row via `rememberLiveSong`, which falls back to the snapshot when the
   whitelist sync deletes the row mid-display.
3. **Latest Releases** comes from the flipphoneguy feed; **Zemer Playlists** from `/zemer-playlists`
   (`docs/zemer_playlists/README.md`).
4. **Zemer Radio** is the Home **Radio** tab, from `GET /stations` (`docs/stations/README.md`).

The **only** `YouTube.*` content call in `HomeViewModel` is `accountInfo()` — a signed-in user's own
name/avatar for the account card. There is **no InnerTube scrape fallback**: if `/home-rows` is
unreachable (and no offline snapshot serves it), the featured rows just hide (local rows + Latest
Releases still populate).

The home tab was the first surface of the ongoing InnerTube → Zemer migration (see `AGENTS.md` §The
home tab for the direction and the remaining punch list); streaming/playback stays on InnerTube + the
cipher.

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
GET https://search.zemer.io/home-rows?allowFemale=0&blockVideos=0&kidZone=0
→ { topAlbums:[ZemerAlbum+artistId], topVideos:[ZemerTrack+artistId+realVideo],
    topArtists:[ZemerArtist], topCommunity:[ZemerPlaylist] }
```

- All three content flags sent explicitly every request (server is default-OPEN; `blockVideos` is
  pinned `0` by `zemerSearchOptions` — blocked videos render as audio; `kidZone` always `0` — home is
  never reachable from the KidZone tab).
- Cards carry the **artist channel id** (`artistId` / `ZemerArtist.id`). Load-bearing: the
  one-per-artist `rotateByArtist` dedup and the ranked-gate defence-in-depth both key on it and
  **no-op when it is null**.
- `topVideos[].realVideo` flags a real filmed video. It filters only the Featured Videos **hero**
  (`HomeRows.realVideoIds`; the whole pool when none are flagged, so the shelf never vanishes); the
  See-all keeps the full video pool.
- `topCommunity` = community playlists ranked by their own YouTube view count. Backs the **Featured
  Playlists** row.

## Rules that must not regress

- **No InnerTube for content.** The featured rows have no scrape fallback; `loadHomeRows()` null → rows
  hide. `seedQuickPicksFromZemer` is the cold-start seed. Never reintroduce `YouTube.home()` /
  `explore()` / `getChartsPage()` or an InnerTube featured scrape on this tab, nor the mainstream
  Trending row (charts carry ~no whitelisted artists, so it only ever filtered to empty).
- **Ranked content gate ≠ home gate.** `isAllowedRanked` applies the shared, JVM-tested
  `utils/RankedContentGate` (female when blocked + Israeli + kids-only; blocked ids are already dropped
  in the mapper) — NOT the famous/american quality proxy in `isBlockedArtist`: real listening reach
  supersedes the proxy, and applying it cut the rows to near-empty. `VideoHomeRowsViewModel` applies
  the same gate to the Videos tab's `/video-home-rows`.
- **Server routing on tap.** Zemer-sourced albums/playlists open via `zemerAlbumRoute` /
  `zemerPlaylistRoute` (`search/ZemerRoutes.kt`, `?zemer=true`), gated on `featuredAlbumsAreZemer` /
  `featuredPlaylistsAreZemer`, so the opened screen is whitelist-scoped and immune to InnerTube bot-gating.
- **The shuffle button is "Radio mode"** — `HomeViewModel.shuffleRadioQueue()` →
  `ZemerRadioQueue(kind = "shuffle", seed = null)`, a whole-catalog, whitelist-pure `/radio` station.
  Don't reintroduce the lucky-item InnerTube radio or a per-item `radioEndpoint != null` filter.
- **"See all" reads the published snapshot.** `HomeSeeAllStore` holds the FULL filtered pool (led by the
  row's displayed items, in row order) that `HomeViewModel` publishes each load; the see-all pages render straight from it (no re-fetch, no
  re-filter), so they can never disagree with the row. Featured grids are 2-column.
- **Row sizing.** Featured rows use `rotateByArtist(maxPerArtist = 1, target = 20)`. Featured Playlists
  have no curator id, so no `rotateByArtist`: the pool is shuffled, the ids shown on the previous load
  (`recentCommunityIds`) sort last, then `take(8)` — a pull-to-refresh turns the row over.
