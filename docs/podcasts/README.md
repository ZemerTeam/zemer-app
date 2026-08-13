# Podcasts

A "Kosher" podcast client layered on the same architecture as the music side: **discovery is the
Zemer server** (`search.zemer.io`), the **allow-set is the content mirror** (`content.zemer.io`),
and **playback stays InnerTube** (an episode is a YouTube video with a real `videoId`, played
through the exact same pipeline as a song). This doc is the app-side map; the app<->server threads
live in `handoff-docs/zemer-app-podcasts-request.md` and its follow-ups.

## The data model (the #1 gotcha first)

- A **show** is `MPSP...` - a series of episodes. Opened by `OnlinePodcastScreen`
  (`online_podcast/{id}`), saved to the library as a `PodcastEntity` bookmark.
- A **host channel** is `UC...` - a publisher of one or more shows. Opened by the podcast variant
  of the artist page (`artist/{id}?isPodcastChannel=true`, loaded from `/podcast-channel`, never
  InnerTube). Subscribing writes a bookmarked `ArtistEntity` with `isPodcastChannel = 1`.
- An **episode** IS a `SongEntity` with `isEpisode = 1`. "Saved for later" is `inLibrary != null`
  (not `liked`); it plays by `videoId`; `EpisodeItem.asSongItem()`/`toMediaMetadata()` carry
  `isEpisode = true` end to end. Regular downloaded-songs queries EXCLUDE episodes; episodes never
  enter the music discovery rows (Keep Listening / Quick Picks / Forgotten Favorites all filter
  `!isEpisode`).
- `whitelistedPodcastRoute(podcastId, channelId)` (unit-tested) routes a browsed show
  CHANNEL-first when the host is known, else to the show.

## Whitelist (channel-level, mirror-first)

Podcasts have their OWN whitelist, separate from the artist one: approve a publisher `UC...` and
its whole catalog is kosher. `SyncUtils.syncPodcastWhitelist` reads
`content.zemer.io/podcastChannelsWhitelist` (+ `/version` gate) with a Firestore
`podcastChannelsWhitelist` fallback - exactly the artist-whitelist pattern. Each mirror doc
carries `thumbnailUrl` + `channelId`, so the browse grid renders straight from the allow-set with
no per-row fetch. A failed/empty fetch preserves the last-good table (never unblocks).
`filterWhitelisted` gates `PodcastItem`/`EpisodeItem` against this table (respecting filters-off)
as defense-in-depth; an episode `SongItem` is gated on the podcast whitelist, never the artist one.

## Surfaces

- **Browse** (`WhitelistedPodcastsScreen`): the channel grid from the allow-set, with an instant
  local title filter. A typed query also offers a "Search episodes for X" hand-off pill
  (`SearchHandoffPill`) into the global search screen, prefilled with the Episodes chip selected
  (`zemerSearchRoute(query, SEARCH_FILTER_EPISODES)`) - this screen deliberately stays a local
  filter, never a networked results page. The top bar carries the whitelist sync button (parity
  with Artists/KidZone).
- **Show screen**: `/podcast?id=MPSP...&offset=` pages the episode list newest-first.
- **Channel page**: `ArtistScreen` with a Podcasts shelf + latest episodes; its Episodes see-all
  (`ArtistSectionScreen`) pages the CHANNEL-WIDE history (`/podcast-channel?offset=`, near-edge
  prefetch) and has its own episode search: a client-side title filter plus a bounded,
  query-driven history drain (`ArtistViewModel.drainEpisodeHistoryForSearch`) that always
  terminates - results, an honest "No results found", or an incomplete-search notice with Retry.
  While a query is active the drain owns paging (the near-edge trigger is gated off).
- **Home Podcasts tab**: genre strip (server-owned `kind` sections via `podcastGenreSections`,
  rendered through the shared `GenreCardGrid`; hidden with the same "Show genres on home"
  preference as the music strip), ranked rows from `/podcast-home-rows` (fail-soft, live-only),
  Continue Listening (in-progress episodes via the `event` table - no new column), and New
  Episodes (`/podcasts/new-episodes`, scoped CLIENT-side to locally-subscribed shows so it works
  for anonymous sessions).
- **Library -> Podcasts**: EPISODES / CHANNELS / DOWNLOADED sub-tabs with their own sort keys
  (never the Songs keys). Shared data sources live in `utils/PodcastLibrarySources` so the two
  podcast VMs cannot drift.
- **Search**: podcast/episode groups fold into `/search`; a show row routes via
  `whitelistedPodcastRoute`, an episode row plays by videoId.

## Playback and account rules

- **Playback never moves off InnerTube** - the irreducible core, same as music.
- A tapped episode plays via `ListQueue.episode(item, playSource)` - never song radio around its
  videoId. Plays tag `PlaySource.podcast(id)` / the `podcast|channel` tracking surfaces.
- **Episode resume**: `MusicService` persists `lastPositionMs` on every exit path and seeks on
  load; the pure `EpisodeResume` decides edges (no resume near the start, restart when finished).
  Never read `player.*` inside `database.query {}`. Rows show an "N left" hint; the menu has local
  mark-played/unplayed.
- **Episode-only player controls**: speed pill + +/-30s skips, shown only for `isEpisode`; the
  service resets speed to 1x on any non-episode so podcast speed never leaks into music.
- **Account sync** (subscriptions, episodes-for-later) gates on `isPersonalAccountSignedIn` -
  never SAPISID (the pooled-account leak rule). Anonymous sessions are local-only; save/subscribe
  toggles are optimistic. Channel subscribe must send `params="EgIIAhgA"` or the server no-ops.

## Server endpoints consumed

`/podcast-channels`, `/podcast-genres` (+`kinds` catalog), `/podcast-home-rows` (live-only),
`/podcast`, `/podcast-channel` (+ `offset` paging), `/podcasts/new-episodes`, `/podcasts/version`,
and podcast groups in `/search`. All are server-first with the offline-snapshot fallback except
the live-only home rows. Contracts: the `zemer-app-podcast*` handoff docs.
