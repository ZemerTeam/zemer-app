# Working with Zemer as an AI agent

Zemer is a "Kosher" YouTube Music client for Android (Kotlin, Jetpack Compose, Material 3), forked from [Metrolist](https://github.com/MetrolistGroup/Metrolist) with content-filtering layered on top (artist whitelist, KidZone, per-artist flags like `isFemale`/`isChasid`). The shared library modules keep the **`com.metrolist.*`** package namespace while the app is **`com.jtech.zemer`** — that split is intentional, don't "fix" it.

## Project rules

1. Pull the latest `main` before starting, to minimize merge conflicts.
2. Commit messages follow `type(scope): short description` (e.g. `fix(player): skip HEAD validation for WEB_REMIX`, `feat(ui): add history button`); the scope is optional.
3. User-facing strings: add/edit **only** the default English `app/src/main/res/values/metrolist_strings.xml`. Do **not** edit `strings.xml` or any translated `metrolist_strings.xml` — other locales are managed separately.
4. Database schema changes (`app/.../db/MusicDatabase.kt` + entities) require a versioned Room migration and are high-risk — confirm with a human before changing the schema.
5. Don't rename the `com.metrolist.*` library namespace, and don't bump the app version — version bumps are a release-team decision.
6. Follow Kotlin/Android best practices; prioritize performance, battery, and maintainability.

## Working agreement

- **Do not commit, push, or merge unless explicitly asked in the current request.** When you are authorized, doing so is fine and the responsibility lies with the requester. Never rewrite git history, force-push (except rebasing your own branch), or delete branches without explicit instruction.
- **Never commit secrets** — `innertube_cookie.txt`, cookies / poTokens, `release.keystore`, `google-services.json` are gitignored; keep them that way.
- Edit README / docs only when that is the task, not as a side effect.
- Ask a human when requirements are unclear; don't assume. Add comments only for complex or non-obvious logic.

## Engineering rules (non-negotiable)

- **Regression tests are required** for every behavioral change or bug fix wherever a test does not demand heavy new infrastructure (plain JVM/unit tests, Robolectric, or the `tests/` streaming harness for stream/cipher/poToken work). "It builds" and "I watched it work once" are not regression protection. If a fix genuinely cannot be tested without heavy new infrastructure, say so explicitly in the change description instead of skipping silently.
- **Keep code modular.** No new god files: split by responsibility (screen scaffolding vs. business logic vs. data access). New logic goes behind small, single-purpose functions/classes — not appended to `MainActivity.kt`, `OnboardingScreen.kt`, `MusicService.kt`, or other existing giants; shrink them when touching them.
- **Keep it professional.** Code must pass the bar of an external staff-engineer review: layering respected (UI does not run database/network calls inline), errors handled rather than swallowed, user-facing strings localized, no copy-pasted near-duplicates, no dead code left behind.

## Build & run

- **JDK 21**, `compileSdk`/`targetSdk` 36, `minSdk` 26. Native code targets `arm64-v8a` + `armeabi-v7a` only (NDK 27). There are no product flavors.
- `./gradlew :app:assembleDebug` — debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:assembleRelease` — release APK. **Build BOTH after any change**: release runs R8 (`isMinifyEnabled = true`) and catches shrink/keep-rule breakage that debug never will.
- Submodules are required: `git submodule update --init --recursive` (`cipher/` and the native `app/src/main/cpp/bento4`). CI pulls a prebuilt bento4 from `ZemerTeam/zemer-bento4`.
- Install to a connected device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Stream resolution logs under logcat tag `YTPlayerUtils` (also `PoTokenWebView`, `Zemer_CipherFnExtract`).
- CI: `.github/workflows/release-build.yml` builds a signed release on push to `main` / PRs (skips `docs/**`, `tests/**`, `**.md`); keystore + `google-services.json` come from base64 secrets.

## Architecture & the danger zones

### The streaming pipeline (the core; where things break)

`app/.../utils/YTPlayerUtils.kt` `playerResponseForPlayback()` is the heart of the app. It:
1. Tries `WEB_REMIX` (main client), then a user-configurable `STREAM_FALLBACK_CLIENTS` list (VISIONOS, WEB_CREATOR, ANDROID_VR, TVHTML5, IOS/IPADOS, …) — order/enable-state are settable in the Stream Sources setting.
2. For web clients, deciphers the `signatureCipher` (sig + n-transform) via the **`cipher` submodule**, then appends a BotGuard `pot=` token.
3. Validates, then hands the URL to ExoPlayer in `MusicService`.

Two hard-won facts that govern this area — always verify against the live CDN via `tests/`, never reason from convention (the convention was wrong here):
- **googlevideo serves the first 1 MiB of a stream free, then 403s every new connection** unless the URL's `&pot=` is bound to the **videoId** (not visitorData). Clients whose attestation the web poToken can't satisfy (IOS/IPADOS — and MWEB, which was removed for this reason) 403 past the wall under every binding.
- **`validateStatus` does a HEAD that false-negatives** (403 on URLs that GET fine), so WEB_REMIX intentionally skips it.

### Cipher / player rotation (the most common future break)

The `cipher` submodule (package `com.zemer.cipher`, repo `ZemerTeam/zemer-cipher`) deciphers YouTube's `player_ias` signatures in an Android WebView and mints poTokens. It's wired **two ways**: a git submodule *and* a Gradle composite build — `includeBuild("cipher")` in `settings.gradle.kts` substitutes `com.zemer:cipher` → the local `:library`, so the app always builds the working tree.

YouTube rotates `player_ias` frequently. Player configs live in **one JSON file**: `cipher/library/src/main/assets/player_configs.json` — per player the sig call expression (e.g. `mP(4,155,INPUT)`), the n-transform URL class (e.g. `Yx`), the STS, and the md5-of-first-10000-bytes alias. That single file is (1) bundled in the APK as the offline default, (2) **fetched at runtime from raw zemer-cipher `master`** by `PlayerConfigStore` (6 h TTL + ETag, plus a forced refresh + one retry the moment an unknown hash breaks deciphering), and (3) read by the `tests/` harness — so **a config pushed to cipher `master` fixes deployed apps within minutes, no APK release**. Parsing/validation is `PlayerConfigParser` (strict regexes; the n-IIFE is built from a local template — remote data can never inject free-form JS into the WebView; invalid entries are skipped, invalid files — including any duplicate hash/alias key — are rejected wholesale and the previous table kept). The validation rules exist in TWO readers (the Kotlin parser and `tests/player-configs.mjs`); file-level accept/reject verdicts and the n-IIFE template are pinned byte-for-byte by shared fixtures in `cipher/library/src/test/resources/config-parity/` — a rule change must update both readers AND the fixtures, or one of the two test suites goes red. When adding a config:
- **Validate empirically**: `node tests/validate-player-config.mjs <hash>` deciphers a real stream and checks the CDN returns **HTTP 206**. That 206 is ground truth, not regex extraction — multiple constant pairs can decipher correctly, only the live response confirms which the server accepts. It prints a paste-ready JSON entry (and re-validates the committed entry first if one exists).
- Add the entry (with its MD5 alias) to `player_configs.json` only — there are no Kotlin/harness mirrors to sync anymore; unit tests in `cipher/library/src/test/` guard the file's shape. Then run `node tests/gen-player-dates.mjs` to refresh `player_dates.json` (a **separate, cosmetic** file mapping each hash to the commit date support was added, shown in the song-details sheet via `PlayerDatesStore`). It is deliberately decoupled: old apps never fetch it, and a malformed/missing dates file only blanks a UI label — deciphering is never affected.
- **Push to cipher `master` is the deploy**: that is the URL devices fetch. Bump the submodule pointer in `zemer-app` afterwards so bundled defaults stay fresh (push order: `zemer-cipher` first, then the pointer — reverse breaks fresh clones / CI).
- A cipher *scheme* change (new config shape, not just a new hash) still needs code + an APK; bump `schemaVersion` only on breaking shape changes — old apps reject newer schema files and keep their last-good table.
- `.github/workflows/player-monitor.yml` checks hourly: it fetches the live raw `master` file once (the submodule copy is only a warned-about fallback) and **multi-samples** the live player surfaces via `tests/scan-live-players.mjs` (30× `iframe_api` + `music.youtube.com`) so a low-rate A/B **canary** — served ~1/6 of the time, which a single sample misses ~83% of the time — is caught the first hour it appears, not once it has already rotated in. "Known" is still decided by the harness loader (`parsePlayerConfigs`, the app's validation rules) against real keys + md5 aliases, so a pushed-but-invalid entry counts as UNKNOWN and still alerts. Opens one issue per unknown hash + a summary email, but does **not** auto-commit — the config is added by hand.

### Accounts: personal vs anonymous (pooled) — `SAPISID` ≠ logged in

There are two signed-in states and telling them apart is non-obvious. A **personal** Google login sets a **`dataSyncId`**. The **"anonymous"** login signs into a **shared, pooled** account: its cookie **does** carry `SAPISID`, but the flow deliberately clears `dataSyncId` (`App.kt` / `LoginGateScreen` — `onBehalfOfUser`/dataSyncId breaks the pooled player request). So `parseCookieString(cookie).containsKey("SAPISID")` / the cookie-based `Context.isUserLoggedInFlow()` are **true for anonymous** and must **never** gate remote *account* reads or writes — doing so leaks the pooled account's library/likes/subscriptions across every anonymous user. (The old blocking `Context.isUserLoggedIn()`/`isSyncEnabled()` helpers — `runBlocking` around a DataStore read plus, in the login case, a blocking DNS socket — were dead and were deleted; use the reactive `*Flow` variants.)

- The correct discriminator is **`com.jtech.zemer.extensions.AccountState`**: `isPersonalAccountSignedIn` (= non-empty `YouTube.dataSyncId`, usable from context-free entity code) and the reactive `Context.isPersonalAccountFlow()`. Gate remote account sync/writes on these — never on `SAPISID`.
- Already gated: `SyncUtils` account syncs + `likeSong`, the entity `toggleLike` remote side-effects (`Song/Artist/Album/PlaylistEntity`), and the add/remove/create/rename/delete-playlist + library/history-feedback writes in the menus. **Local DB writes always run**, so anonymous keeps likes/subscribes/playlists locally; personal logins are unaffected (each gate is a no-op when the predicate is true). The **Firebase artist-whitelist sync (`syncArtistWhitelist`) is account-independent and stays on for anon** — it powers content filtering.
- **UI account display is gated too** (#137): the Settings → Account "Signed in as" card (name/email/handle/avatar) and the **"More content"** + **"Auto sync with account"** switches render only when `isPersonalAccountSignedIn` (`AccountSettings.kt`) — never the SAPISID-based `isLoggedIn`, which would show the *pooled* account's identity and account-personalization controls to every anonymous user. The Anonymous-login button is hidden once signed in, so there is a single Logout control, not a duplicate.
- **Synced playlists reconcile non-destructively and stay 100% whitelisted** (`SyncUtils.syncSavedPlaylists`/`syncPlaylist`, #130): keep a song only if a whitelisted artist is resolvable from the playlist renderer **or the local DB row** (`filterWhitelistedWithLocalArtists`). Do **not** restore the old `clearPlaylist()` + strict `filterWhitelisted` rebuild — it wiped user-added songs whose YTM renderer carried sparse/topic-channel artist ids while they stayed in YouTube Music. A failed/empty/partial remote read must never delete a playlist or its songs.
- Still personalized-to-the-pool for anon (NOT yet gated): Android-Auto browse still reads pooled-cookie InnerTube surfaces. (`YouTube.home()` no longer feeds the app's Home tab — the home tab is InnerTube-free for content, see §The home tab — so the old "anon Home shows the pooled account's mixes" leak is gone with it.) Note the *Library* "My top 50" is a **local** most-played auto-playlist (`mostPlayedSongs`), not a leak.

### Content filtering (whitelist, conditional id overrides, filtered covers)

The "Kosher" guarantee runs through one chokepoint — `utils/WhitelistFilter.kt` `filterWhitelisted`
(applied on every YouTube/browse/playback surface) over the artist whitelist it reads. Two layers on top
are non-obvious and regression-prone; full detail in `docs/whitelist/README.md`:

- **Conditional id overrides** (`blockedContentIds` Firestore collection → `utils/BlockedIdsCache.kt`,
  #161): a server-listed, read-only table of specific ids hidden *conditionally* by a **reason** —
  `female` hides only when `!allowFemaleSingers`, `global` (and any unknown reason) hides for everyone,
  all inert when filtering is off. The surgical complement to the artist whitelist: a *mixed* channel
  stays whitelisted while specific items from it are dropped by id. Applied centrally in
  `filterWhitelisted`, in `search/ZemerResultMapper.dropBlocked()`, **and** in the offline read layer
  (`offline/SubsetReadLayer.idDropped` + the shared `contentGatePasses` gate — see §Offline search
  backup): a change to the filtering contract must now land in all THREE enforcement sites. The
  artist-membership whitelist is deliberately never run over raw Zemer search results (it would clip
  legitimate Hebrew/community hits), but a specific-id drop is safe there. Synced inside `syncArtistWhitelist` (no
  user interaction), persisted to DataStore, loaded at startup; a failed sync keeps the previous table
  (never unblocks). The `blockedContentIds` collection is managed by the separate **zemer-admin** app.
- **Playlist covers come from the filtered tracks, never the raw curator image.** A community/online
  playlist's `playlist.thumbnail` is YouTube's curator art and bypasses the filter, so a mostly-female
  playlist would otherwise show a female cover even when female is blocked.
  `ui/screens/playlist/filteredPlaylistCover(songs)` derives both the opened-playlist header cover and
  the saved-to-Library cover from the first *content-filtered* track (`songs` is already
  `filterWhitelisted`-filtered), falling back to the neutral `queue_music` placeholder / null
  `thumbnailUrl` — **never** `playlist.thumbnail`. Mirrors the local-playlist screens; don't revert
  either site.

### Zemer curated playlists (the Home "Zemer Playlists" shelf)

Hand-curated playlists served ready-to-render by the search server's `/zemer-playlists` endpoint;
full detail in `docs/zemer_playlists/README.md`. The rules that must not regress:

- **Ids are server slugs (`"acapella"`), never YouTube playlist ids** — they get their own screens
  (`zemer_playlist/{id}` detail, `zemer_playlists` see-all) and must never enter a YouTube-playlist
  code path (`online_playlist/…`, save-to-library, playlist menus).
- **All three content flags are sent explicitly on every request** (the server is default-OPEN;
  `zemerCuratedPlaylistsParameters()` is the unit-tested contract), and the repository deliberately
  does **not** cache — a plain re-fetch per screen-open is the endpoint's freshness contract and
  guarantees a response fetched under one flag set is never shown under another. No client
  re-filtering beyond the usual `dropBlocked` + `hideExplicit`.
- **Covers are server-generated SVGs at relative URLs** — resolved by `resolveZemerUrl()` and
  decoded by the `SvgDecoder` registered in `App.newImageLoader` (that's why `coil-svg` exists).
- Empty list = hidden section (normal state); detail 404 = back out + Home re-fetch. The Home shelf
  is backed by its own `ZemerCuratedPlaylistsViewModel` (LatestReleases isolation pattern) so a feed
  failure can never affect the rest of Home.
- The detail screen's **All/Albums/Songs chips reuse `LatestReleaseFilter`** and split on the
  server's `fromAlbum`/`albums` fields. The **chip row shows ONLY when the playlist has albums**
  (`curatedChipsVisible(albumCount)`, unit-tested): a direct-picks playlist has no albums, so All ==
  Songs and Albums is an empty dead end — those render the plain track list instead. `effectiveFilter`
  pins the filter to ALL whenever the chips are hidden. Rows, Play and Shuffle all read the same
  filtered list; rows never pass `albumIndex` (the shared row renders a number *instead of* artwork).
- App↔server field changes travel as request docs in `~/zemer-fix/handoff-docs/`, never as direct
  edits to the zemer-search repo.

### Genres (the song-level genre layer: Home chips, catalog, detail, radio)

Song-level genre browsing served by the search server's `/genres` family; full detail in
`docs/genres/README.md`, server contract in `~/zemer-fix/handoff-docs/zemer-app-genres.md`. Genre is
a property of the SONG (via its release), independent of the artist flags — never conflate the two.
The rules that must not regress:

- **Key off the SLUG (`"nigunim"`), render the `title`** — `id` is the stable contract, `title` is a
  display string the server changes freely. Routes carry the raw slug (`[\w-]`, URL-safe, no
  encoding; `search/ZemerRoutes.kt` — `zemerGenresRoute`/`zemerGenreRoute`/`zemerGenreSectionRoute`,
  unit-tested).
- **`kind` grouping is fail-closed.** `musicGenres()` drops `non-music` AND any unknown/new kind
  (`GenreKind.fromSlug` returns null → dropped), so spoken-word never renders beside songs.
  `HIDDEN_GENRE_SLUGS` (lullaby/carlebach/workout/kids) are hidden from browse app-side (owner
  decision, songs still reachable elsewhere); `acapella` is pinned LAST (`pinLast()`). All in
  `search/ZemerGenresModels.kt`, JVM-tested in `ZemerGenresTest`.
- **All three content flags on every call** (default-OPEN server; `zemerGenresParameters` /
  `zemerGenreFacetParameters`, unit-tested). All genre endpoints are **live-only** (no offline
  snapshot, like `/playlist`/`/radio`/`/stations`). The catalog has a 60 s flag-keyed TTL memo in
  `ZemerSearchRepository.genres()` (collapses the Home→see-all→back nav burst); detail/facet are
  uncached.
- **The detail Play button is genre RADIO** (`ZemerRadioQueue.genre(slug)` → `/radio?kind=genre`),
  NEVER the browse tracklist. It seeds no song, so its plays report `radio`; per-genre
  `PlaySource.genre`/`TrackingSurface.genre` rides only the tracklist row taps (seed-first song
  radio). **No Artists shelf** on a genre page — an artist card opens a full, mostly-unrelated
  catalog (deliberately omitted).
- **Tracklist paging + cross-list dedup** (`viewmodels/ZemerGenreViewModel`): near-edge prefetch
  (`shouldPrefetchNearEnd`, off-composition `snapshotFlow`), and a track the corpus returns in BOTH
  the song and video arrays is de-duped across the two lists (page 0 AND `loadMore`) with disjoint
  `song_`/`video_` `LazyColumn` keys.
- **See-all uses the facet endpoint, not a `k` cap** (`viewmodels/ZemerGenreSectionViewModel`,
  `GenreSectionScreen.kt`, route `genre_section/{genreId}?section=`): pages
  `/genres?id=&facet=albums|singles` (limit 200) until `nextOffset` is null, so the FULL list is
  browsable. Reuses the shared `YtItemGrid` + `BackTopAppBar`; the see-all arrow shows on any
  non-empty shelf (always, like the artist page).
- **Visuals are monochrome + one gold accent** (`docs/genres/README.md` §visual): per-genre motif
  drawables (`ui/component/GenreIcons.kt` → `res/drawable/genre_*.xml`, incl. hand-drawn
  menorah/alef/sukkah; NOT `material-icons-extended`); the drifting weave (`GenreWeaveLayer`) is a
  GPU-composited `graphicsLayer` translation of a once-drawn tile (NOT a per-frame redraw — that
  caused catalog jank; the fix is load-bearing); the detail header album-art mosaic
  (`ZemerResultMapper.headerCovers`) is the ONE color source, de-duped, min 3 unique covers
  (songs reuse album art), neutral `ColorPainter` fallback (never a transparent gap), sized by the
  mosaic-only `mosaicVariant` (isolated from the shared `thumbnailFor`). `HeaderFontFamily` (Heebo)
  is used ONLY on genre titles/Play/card titles, never app-wide.
- Home strip (`ui/screens/HomeGenresRow.kt`, own fail-soft `ZemerGenresViewModel`, isolated like
  Stations) sits under Quick Picks, hidden/restored via a Settings → Appearance switch
  (`ShowHomeGenresKey`). App↔server field changes travel as handoff docs, never as zemer-search edits.

### Shared UI components (componentized — import, don't re-roll)

A componentization pass extracted the app's repeated composables into `ui/component/`; reuse them
instead of hand-rolling: `BackNavigationIcon` / `BackTopAppBar` (top-bar back button), `MoreVertMenuButton`
(row 3-dot menu), `TopAppBarActionButton` (plain `TopAppBar` action icon — history/search/now-playing/
refresh), `AppBarTitle` (the shared bold screen title — `titleLarge`+Bold, single-line+ellipsis; put
EVERY screen-level `TopAppBar`/`BackTopAppBar` title through it so weights don't drift), `zemerTopAppBarColors()`
(the one top-bar container color — pure black under AMOLED / `surfaceContainer` otherwise, container ==
scrolled so bars never grey-out on scroll; baked into `BackTopAppBar`, and every hand-rolled screen
`TopAppBar` passes `colors = zemerTopAppBarColors()` — except the full-bleed login/onboarding bars, the
video player's fixed-black bar, and ArtistScreen's over-header transparent state),
`PlaylistPlayShuffleButtons` + `PlaylistHeaderShimmer` (playlist headers/skeletons),
`shimmer/BoxPlaceholder` (the base shimmer slab under `ButtonPlaceholder`/`GridItemPlaceholder`),
`ArtistBrowseComponents` (KidZone/whitelist browse header), `IconCategoryCard` (the square category
tile — centered gold icon + bold title + count subtitle on one neutral `surfaceContainerHigh` box, with
the D-pad focus treatment; the Downloaded library's Music/Videos/Status tiles all render through it). The
**status viewers** share a family so the live (`StoryScreen`) and saved (`SavedStatusScreen`) viewers
can't drift: `StatusStoryTopOverlay` (segment bars + avatar/name/date), `ExpandableStatusCaption` (the
WhatsApp Read-more caption with clickable links + inline copy), `StatusCopyButton` (icon-only themed copy
circle), `StatusVideoSurface` (the full-bleed ZOOM `PlayerView`, controls/buffering disabled),
`StatusLoadingIndicator` (avatar + M3 progress ring loading state, spinner fallback), plus the
`ui/utils/cubeFace` modifier (the cube swipe transform). New screens use these; a hand-rolled duplicate is
a review miss.

**Componentize on every touch (non-negotiable).** Whenever you touch anything in the app, first check
whether a shared component already covers it — if one exists, use it. If you find yourself writing (or
editing) a second near-copy of a widget that already appears elsewhere, STOP and extract it into
`ui/component/` (or reuse the existing one), then point every site at it in the same pass — never leave
two hand-rolled copies to drift. Extracting the shared piece is part of the change, not a follow-up: the
staff-engineer review bar rejects copy-pasted near-duplicates. When you add a new shared component, list
it in the paragraph above so the next contributor finds it.

**Shared non-visual helpers (de-dup logic too, not just composables).** The same "reuse, don't re-roll"
rule covers repeated *logic*. The current shared helpers — reach for these before hand-writing the pattern:
- **Id-bearing navigation:** `navigateToArtist(id)` / `navigateToAlbum(id)` (`ui/utils/AppNavigation.kt`)
  over `navController.navigate("artist/$id")`. A blank id builds `"artist/"`, matches no destination and
  **crashes** — the helper makes a blank id a no-op; the pure `artistRoute`/`albumRoute` builders are
  unit-tested (`AppNavigationTest`). Query-param routes keep their own builders (`ZemerRoutes.kt`).
  Ratcheted by `R16-navroute` (baseline 0).
- **The row 3-dot menu body:** `ytItemMenu(item, navController, coroutineScope, onDismiss, isVideo)`
  (`ui/menu/YouTubeItemMenu.kt`) returns the `@Composable ColumnScope.() -> Unit` for `menuState.show`,
  dispatching `SongItem`/`AlbumItem`/`ArtistItem`/`PlaylistItem` to the right `YouTube*Menu` — never
  re-write that `when` per screen.
- **The Zemer repository from a leaf composable/queue:** `context.zemerSearchRepository()`
  (`di/ZemerSearchRepositoryEntryPoint.kt`) over a hand-written `EntryPointAccessors.fromApplication(...)`.
  Ratcheted by `R17-entrypoint` (UI-scoped, baseline 0).
- **Sharing a URL/deep link:** `context.shareText(url)` (`extensions/ContextExt.kt`) over a hand-rolled
  `Intent(ACTION_SEND)` + `createChooser`. `Tracker.action(SHARE, …)` and `onDismiss()` stay at the call
  site. File/stream shares (log export, lyric image) keep their own builder. Ratcheted by `R19-share`
  (baseline 0; `component/Lyrics.kt`'s lyric-image `EXTRA_STREAM` share is excluded, not a text share).
- **Copying to the clipboard:** `context.copyToClipboard(label, text, confirmationRes = R.string.copied)`
  (`extensions/ContextExt.kt`) over a hand-rolled `ClipboardManager.setPrimaryClip(...)` — it also shows
  the confirmation toast (`link_copied` for link copies). `text` is a `CharSequence` so an
  `AnnotatedString` copies verbatim. Ratcheted by `R20-clipboard` (baseline 0).
- **Showing a toast:** `context.toast(resId | text, long = false)` (`extensions/ContextExt.kt`) over a
  hand-rolled `Toast.makeText(...).show()` — two overloads mirror the framework (string-resource id /
  `CharSequence`); `long = true` is `LENGTH_LONG`. Ratcheted by `R21-toast` (UI-scoped, baseline 0). Works
  from any `Context` (Activity, Service, Application, `this@MusicService`).

**Never `runBlocking` on a UI path.** A composable/UI file that blocks the main thread ANRs. Collect the
value with a suspend function + `LaunchedEffect`/`rememberCoroutineScope`, or a `Flow` (`collectAsState`);
the DataStore sync accessors (`dataStore[Key]`, `dataStore.get(Key, default)`) are the documented
exception and must run OFF the main thread. Ratcheted by `R18-runblocking` (UI-scoped, baseline 0). The
legitimate blocking sites live outside `ui/` and are deliberate — ExoPlayer's `createDataSourceFactory`
(a Media3 contract that must return synchronously), the download thread, the DataStore primitives.

Enforcement lives in `scripts/ui-audit.sh` (see the rule list at the top of that file) + `docs/ui/standards.md`;
when you add a new shared helper with a greppable anti-pattern, add a ratchet rule there in the same pass.

### The home tab (telemetry-ranked rows; zero-InnerTube for content)

`HomeViewModel` + `HomeScreen`. The home tab is **InnerTube-free for content** — every row is served
from the Zemer `/home-rows` endpoint, local Room, or the flipphoneguy Latest-Releases feed. The **only**
`YouTube.*` call left in `HomeViewModel` is the account-card identity lookup (`accountInfo()` for a
signed-in user's name/avatar); do not add others. Full detail in `docs/home_rows/README.md`.

**Project direction (a real, ongoing goal):** progressively **replace as much InnerTube as we can across
the app** with Zemer-served, whitelist-pure data. The home tab migrated first; since then **artist opens
(`/artist`), album opens (`/album`), ALL radio (`/radio` — see §Zemer Radio), every single-song tap
(seed-first song radio), and search-with-offline-fallback** have followed — each with its InnerTube path
deleted, not kept as fallback. When you touch any surface that reaches YouTube for *discovery* content
(explore feeds, charts, recommendations, related, browse shelves, search-adjacent rows), prefer a Zemer
endpoint — or a handoff request for one (`~/zemer-fix/handoff-docs/`) — over deepening the InnerTube
dependency, and delete the InnerTube path once the Zemer source lands. **Streaming/playback itself still
needs InnerTube + the cipher** (see §The streaming pipeline — that's the irreducible core) and is out of
scope; this goal is about *content discovery*, where YouTube's global feeds carry almost no kosher
content anyway. (The YouTube search *engine* is REMOVED — Zemer is the app's only search engine; the
greenlight and evidence live in `~/zemer-fix/handoff-docs/zemer-app-artist-album-innertube-swap.md`.)

**Remaining InnerTube candidates (the punch list to complete the migration)** — everything still
reaching YouTube for content, in rough priority order. Pick from here before inventing new scope:

- **Whole-screen discovery surfaces:** `ChartsScreen` and `NewReleaseScreen`
  (`FEmusic_new_releases`) remain; each wants a Zemer endpoint (or a handoff request) the way
  home-rows got one. (`MoodAndGenresScreen`, `YouTubeBrowseScreen`, `BrowseScreen` and their
  `YouTube.moodAndGenres`/`explore`/`ExplorePage` InnerTube paths were DELETED with the Genres
  feature — the Zemer catalog is the replacement moods/genres surface. The legacy `ArtistItemsScreen`
  is superseded by the Zemer per-section see-all — delete, don't migrate.)
- **Non-engine InnerTube *search* users** (survived the engine removal deliberately — each needs its
  own design, not a blind swap): `RecognitionResolver` (fingerprint match → `YouTube.search` →
  whitelist check; a corpus-side match would need server support), the Android Auto **voice search**
  (`MediaLibrarySessionCallback`), and the **add-to-playlist online search** dialog
  (`AddToPlaylistDialogOnline`).
- **Android Auto browse** still reads pooled-cookie InnerTube surfaces (see §Accounts) — the last
  place anon can meet pooled-account personalization.
- **Account-tied InnerTube** (`SyncUtils` library/likes sync, `accountInfo()` for the account card)
  is inherent to the personal-login feature, not discovery — out of this punch list unless the
  feature itself changes.

Rules that must not regress:

- **Featured Albums / Videos / Artists / Playlists come solely from `ZemerSearchRepository.homeRows()`**
  (`GET /home-rows`, mapped by `ZemerResultMapper.homeRows()` → `HomeRows`). Ranked by real
  distinct-device listening (albums/videos/artists) and YouTube view count (community playlists =
  Featured Playlists row), 30-day live window, whitelist-pure + content-filtered server-side. There is
  **no InnerTube scrape fallback** — an empty pool (only if search.zemer.io is unreachable) just hides
  the row. `loadHomeRows()` returning null hides all four featured rows; it never breaks Home.
- **The ranked content gate is female/israeli/blocked-ids ONLY — NOT the famous/american quality
  proxy** (`isAllowedRanked`, distinct from `isBlockedArtist`). Real listening reach supersedes the
  proxy that gates the (now-removed) scrape; applying famous/american here cut the rows to near-empty.
  Cards carry the artist channel id (`ZemerAlbum/Track.artistId`, `ZemerArtist.id`) so the one-per-artist
  `rotateByArtist` dedup and the female/israeli check work; without it both no-op.
- **Zemer-sourced albums/playlists open via the server route** (`onlineAlbumRoute` / `onlinePlaylistRoute`,
  `?zemer=true`), gated on `featuredAlbumsAreZemer` / `featuredPlaylistsAreZemer`, so the opened screen
  is whitelist-scoped and immune to on-device InnerTube bot-gating. The Home shuffle button is **"Radio
  mode"**: `HomeViewModel.shuffleRadioQueue()` → `ZemerRadioQueue(kind = "shuffle", seed = null)`, a
  whole-catalog, whitelist-pure Zemer station (the old lucky-item InnerTube radio and its
  `radioEndpoint != null` pool are GONE — don't reintroduce a per-item radio-endpoint filter here).
- **A brand-new user's empty Quick Picks seeds from Zemer**, not YouTube: `seedQuickPicksFromZemer`
  pulls the `auto-top-50` curated playlist. Returning users seed from local history; the seed is a no-op
  when Quick Picks is non-empty and never breaks Home on failure.
- The **"Zemer Radio" row** (under Zemer Playlists) is the synchronized-broadcast stations shelf —
  see §Zemer Stations below and `docs/stations/README.md`; its now-playing cards tick every 60s
  while ON SCREEN only (`repeatOnLifecycle(RESUMED)`).
- **Easter egg:** five quick taps on the Home top-bar title (1.5s idle resets) play a fixed song via
  the deep-link path, whitelist-filtered (`ui/utils/HomeTitleEasterEgg.kt`, tap rule unit-tested).
  Deliberate and owner-requested — do not "clean it up".
- **Every content row has a "See all" arrow** → `home_see_all/{row}` (`HomeSeeAllRow`). The pages read a
  process-wide `HomeSeeAllStore` snapshot that `HomeViewModel` publishes each load (the FULL, un-rotated
  filtered pool), so See-all can never disagree with the row it opened from — no re-fetch, no re-filter.
  Featured grids are 2-column (long Hebrew+English titles truncate at 3). Latest Releases / Zemer
  Playlists keep their own see-all screens + ViewModels.
- **The mainstream Trending row is gone** — `YouTube.getChartsPage()` charts carry ~no whitelisted
  artists (it filtered to empty and never displayed); the `auto-trending` / `auto-top-50` Zemer playlists
  in the Zemer-Playlists shelf are the real trending/top surface. Don't reintroduce a charts-scraped row.
- The `/home-rows` contract and every design decision are recorded in
  `~/zemer-fix/handoff-docs/zemer-app-home-rows-request.md` (the app↔server thread) and
  `home-rows-plan.md`. App↔server field changes travel there, never as edits to the zemer-search repo.

### Zemer Radio (`/radio` — every radio surface; SELECTION only)

All radio runs on the Zemer server's `/radio` endpoint (whitelist-pure, blocked-ids filtered
server-side + the client `dropBlocked` pass) via **`playback/queues/ZemerRadioQueue`** — artist /
album / song / playlist seeds and `kind=shuffle` (Home "Radio mode"). `YouTube.next()` is gone from
every radio path. **The audio stream is still InnerTube + the cipher** — this replaced selection
only. Rules that must not regress:

- **The continuation token is opaque** (it encodes seed + flags + position): the queue keeps no
  cursor state; `nextPage()` just echoes the last token back. Continuation pages are PURE fresh
  tracks — never re-apply the old YouTube-style `drop(1)`; `MusicService`'s auto-load dedupes
  against the ids already in the player (`continuationItemsToAppend`) instead.
- **Single-song taps are seed-first** (`ZemerRadioQueue.song()`): the tapped song is the
  `preloadItem` (plays instantly) AND heads the queue at index 0, with the `/radio?kind=song` fill
  deduped around it. Every converted tap site (Home, History, Charts, Stats, artist page, search,
  menus' Start radio, recognition history, latest releases) uses this factory — a bare
  `ZemerRadioQueue("song", …)` without the seed is a station, not a tap.
- **A failed fetch is never silent**: `playQueue()` surfaces it (toast + session error). With no
  preload it also restores the previous queue (only the pointer was swapped); with a preload the
  song keeps playing and the queue's `initialFailed` flag lets `nextPage()` retry the seed page on a
  later transition — the flag is set only AFTER the initial fetch completes, so a retry can never
  run concurrently with it and double-append the fill.
- `LocalAlbumRadio` plays the local album then continues on `/radio?kind=album`; its
  `firstTimeLoaded` flips only after a successful fetch (a transient failure must stay retryable).
- Both queues hold only the **application context** — `MusicService.currentQueue` retains the queue
  for the whole session, so a captured Activity is a leak.
- Tracking: radio fill reports as `radio` (`initialItemsAreContext = false`,
  `continuationIsContext = false`); the preloaded seed is the one user-chosen context item.

**Zemer Stations** (`playback/queues/StationQueue`, the "Zemer Radio" home row) are the OTHER radio
product: one shared, server-programmed wall-clock schedule per station — every listener hears the
same track at the same moment (contract: `~/zemer-fix/handoff-docs/zemer-app-stations.md`). Rules
that must not regress: ALL drift funnels through the BIDIRECTIONAL `resyncStationPlayback` (seek
forward when behind, WAIT — pause until startMs — when ahead, full re-tune when nothing queued is
on-air; never a mid-track jump, never a backward seek into a played/unplayable slot), invoked from
boundaries, pause-resume, error skips and STATE_ENDED; **pause = stop, resume = rejoin live**; a
broadcast is NEVER persisted (`saveQueueToDisk` guard) and a queue MUTATION (Play next / Add to
queue) EXITS broadcast mode (`exitStationOnQueueMutation` — without it station state latches and
queue persistence dies for the process); the session player masks all skip/seek/repeat/shuffle
commands AND no-ops them against stale controllers, notifying command changes on every mask flip
(`CastAwarePlayer.maskTransportForStation`/`notifyStationMaskChanged`); every raw-player transport
surface (mini-player swipes, the full player's thumbnail swipe, queue-sheet taps, lyrics buttons,
the widget's onStartCommand skips, repeat/shuffle toggles, and the Start-radio affordances — player
menu row hidden, notification button disabled, `startRadioSeamlessly` chokepoint guard) is gated on
`isStationBroadcast`, and `PlayerConnection.seekTo/Next/
Previous` early-return during a broadcast; the station runway top-up ignores the Auto-load-more
preference and repeat is forced OFF at station start; station items map through
`ZemerResultMapper.toSongItem` (coverless slots get the derived artwork fallback); the row's
now-playing ticker is LIFECYCLE-scoped (`repeatOnLifecycle(RESUMED)` — nothing polls while
backgrounded); no content flags are sent (pools pre-filtered server-side; blocked-ids still run
client-side as the third layer); a failed slot is marked unplayable and produces no play event
(zero-play-time guard); every station play tags `PlaySource.station(id)`.

### Corpus-native artist/album opens (no InnerTube fallback)

Artist (`/artist`) and album (`/album`) screens load purely from the Zemer server
(`ZemerSearchRepository.artist/album` → `ZemerResultMapper.toArtistPage/toAlbumPage`), with the
offline snapshot as outage fallback — there is deliberately **no InnerTube fallback** (a non-corpus
item is non-whitelisted and shouldn't open). A 404 renders the neutral "not available" state. Two
non-obvious rules:

- **The stale-row delete is flag-aware** (`AlbumViewModel`, `staleAlbumGoneForEveryone`): the server
  404s an album that is merely FULLY BLOCKED under the user's content flags, so a 404 under
  restrictive flags is re-probed with open flags before the local `AlbumEntity` row is deleted — a
  flag-hidden album must never be destroyed by its own filter, and a failed probe keeps the row.
- **An opener-threaded playlistId equal to the browseId never wins** (`toAlbumPage`): cards fall
  their playlistId back to the browseId, and persisting that MPRE as `AlbumEntity.playlistId`
  dead-presses album radio and mis-ids share links; the server's real OLAK id (or the browseId
  fallback, whose only consumer is the disabled automix) is used instead.

### Music Status (the Home "Music Status" row + story viewer; third-party sourced)

A WhatsApp/Stories-style feature: a Home row of creator "status" circles under Quick Picks, a
full-screen story viewer, and a See-all grid. Content comes from TWO third-party services the app
can't guarantee are up, so the whole feature is **fail-soft and isolated** the way Stations/Latest
Releases are. Feature package `statuses/`; UI under `ui/screens/statuses/`; full detail in
`docs/status/` (README + one API reference per platform). The rules that must not regress:

- **Two sources, merged + deduped, music-only.** JewishStatus (`StatusesApi.kt`, Supabase PostgREST
  + R2 CDN) and YidStatus (`YidStatusApi.kt`). **YidStatus MUST go through OkHttp**: its edge function
  requires an `Origin: https://yidstatus.com` header that `HttpURLConnection` silently drops (restricted
  header) -> 403. YidStatus is filtered to music categories (no comedy). `mergeStatusCreators` drops
  cross-platform duplicates by a normalized name (`statusNameKey`); the Home row is uniform over the
  merge, the See-all groups by `source`. Creators with empty `recentPostIds` are dropped (no ring).
- **Fail-soft + isolated.** `ZemerStatusesViewModel` (Stations pattern): a fetch failure keeps the row
  empty and `HomeScreen` hides it; nothing about Home depends on it. Gated by `ShowHomeStatusesKey`.
  If only one source fails the other still populates the row (progressive `republish`).
- **Video-first, so hidden when videos are blocked.** Statuses are predominantly video, so both the Home
  row AND the whole Music Status section in Appearance settings are hidden when `BlockVideosKey` is on
  (gate both sites together - hiding only one would leave a row the user can't turn off, or an orphan
  settings group).
- **One source of truth.** `StatusesRepository` (per-source caches + mutexes, the merged/deduped
  publish, the persisted seen set in `StatusSeenStore`). Live-refresh is three-layer: a staleness
  window (`STALE_MS`), pull-to-refresh (`refresh(force = true)`), and a per-creator re-fetch the moment
  a creator is opened (`refreshPosts`, appends newer statuses in place without disturbing playback).
- **Pure timeline math is extracted and JVM-tested — keep it that way.** `StatusTimeline.kt`
  (`resumePos`, `statusDateGroups`, `formatPostedAt`, `statusLocalDate`; zone-injectable so the tests
  are deterministic) holds the non-obvious WhatsApp logic. Do NOT inline it back into `StoryScreen`
  (that is exactly the untestable-logic-in-UI split the extraction fixed). `StatusTimelineTest` +
  `StatusesApiTest` (parse/merge/`caughtUpOnLatest`/`applyStatusFilter`) + `StatusNavigationTest` are
  the regression gate.
- **WhatsApp read/resume semantics.** Open on TODAY's date window (or the newest date if none today)
  at the first UNSEEN status; caught-up (NEWEST status seen) sinks the creator to the end
  (`sortedByUnseenFirst` / `caughtUpOnLatest`). Finishing a date rolls FORWARD into the same creator's
  next date; only the creator's newest status advances to the next creator; back is floored at the
  entry date (jump-to-date sheet goes earlier). The per-segment ring in `StatusCreatorCircle` colors
  seen vs unseen (accent unseen / `outlineVariant` seen).
- **The ring respects the content filter.** `StatusCreator.recentPostKinds` carries the kind of each
  recent status (YidStatus from the feed; JewishStatus via a batched `public_posts?id=in.(...)&select=
  id,kind` fetch in `fetchStatusCreators`), so `visibleRecentIds(filter)` drops hidden-kind statuses.
  The ring, `caughtUpOnLatest` and `sortedByUnseenFirst` all key off the VISIBLE ids, and a creator with
  nothing viewable drops from the row/see-all. Unknown kinds (fetch failed / size mismatch) show all -
  never hide more than we can prove.
- **The viewer's no-flash invariants** (`StoryScreen`, all hard-won): creators live in a cube
  `HorizontalPager`; the active face renders only when `postsCreatorIdx == creatorIdx` (else the stale
  previous-creator content flashes on settle); both neighbors are prefetched (posts AND the thumbnail
  bytes); the resume position is resolved EXACTLY ONCE against the AWAITED `seenSnapshot()` (never the
  `seenPostIds` StateFlow, which is `emptySet` for the first frames of a fresh viewer -> "always starts
  at the first status"); the play effect keys on the CURRENT status id (not the whole list) so a
  background refresh that appends statuses doesn't restart the video; progress is driven on the display
  frame clock (`withFrameNanos`, dt-capped). A video FILLS the screen (`RESIZE_MODE_ZOOM` in the shared
  `StatusVideoSurface`, whose PlayerView controller auto-show + buffering spinner are disabled BEFORE the
  player binds, so no transport controls flash on prepare); until its first frame draws the shared
  `StatusLoadingIndicator` (the creator's avatar + an M3 progress ring) covers the surface - videos never
  show a low-res/blurry poster. Its own short-lived ExoPlayer; the music player pauses on open, resumes on
  close, and the video pauses when the app is backgrounded (`ON_STOP`).
- **The caption + text are interactive** (shared with the saved viewer). The bottom caption is
  `ExpandableStatusCaption`: it collapses to 3 lines with a Read more/less toggle (expanding freezes
  auto-advance and darkens the panel), links are clickable via `linkifyStatusText`, and an inline
  `StatusCopyButton` copies it; a text status gets its own copy pill (its body has no caption band). Links
  open in the EXTERNAL browser via `Context.openStatusLink` UNLESS the URL matches one of the app's OWN
  registered deep links (YouTube / music·video.zemer.io) - a status link must never open in an in-app
  webview (the browser intent is pinned to the default browser). The save FAB shows a DETERMINATE download
  progress ring (real byte progress from `StatusDownloadManager`, streamed) tracing the FAB's own
  rounded-square outline while saving.
- **Content filter (`HideTextStatusKey` ON by default / `HideImageStatusKey` off, Appearance).**
  Applied at the `StoryViewModel` chokepoints (`applyStatusFilter` on `loadPosts`/`refreshPosts`/
  `cachedPosts`) so the driver, cube preview and resume math all see the SAME visible list; a
  fully-filtered creator ends up with no posts and is auto-skipped. `StoryViewModel.contentFilter` starts
  **null** ("not read yet"), NOT a provisional default, and the driver waits for the first real DataStore
  value before loading - seeding a guessed default let DataStore flip it a few ms after open and re-run
  the driver, restarting playback (visible only when the real setting differed from the guess, i.e.
  hide-image ON). The See-all screen's gear opens
  Appearance scrolled to the Music Status group (`settings/appearance?scrollTo=status` +
  `BringIntoViewRequester`), not the top.
- **Media URLs are source-agnostic** (`statusMediaUrl`/`statusAvatarUrl`): a full `https` path passes
  through unchanged (YidStatus), a relative path gets the R2 prefix (JewishStatus).
- **Third-party, so NO handoff doc** (these are not Zemer services). API shape, the OkHttp/Origin
  gotcha, and the feed cost caps are recorded in `docs/status/` instead.

**Status downloads (save a status to the device gallery).** A save FAB in the story viewer writes the
current status to the gallery, and a "Status" card in Library -> Downloaded opens a browser of saved
statuses. Rules that must not regress:

- **A gallery-media concern, SEPARATE from the song download system.** Saves go through `StatusGallery`
  (MediaStore `Images`/`Video` insert under `Pictures|Movies / Zemer / Status / <creator>`; delete reuses
  `MediaStoreHelper.deleteFromMediaStore`), NOT `MediaStoreDownloadManager`. The FAB uses a
  drawable-painter icon, never the Material download icon, so the download-unification ratchets stay green
  (that system is song-only). `StatusDownloadManager` orchestrates fetch -> save -> index off the main
  thread, fail-soft.
- **No Room migration.** The saved-status INDEX is `StatusDownloadsStore` - a JSON array in DataStore
  (the `StatusSeenStore` pattern), each record `{id, kind, creatorId, creatorName, creatorAvatar,
  postedAt, caption, textBody, mediaUri, savedAt}`. The gallery holds the files; this is just the index
  that lets the library list/group/filter/re-open them offline. Pure filename/index/view logic
  (`StatusDownloadNaming`, `StatusDownload` JSON, `StatusDownloadsView`) is JVM-tested.
- **Filename = posted time, creator = folder.** `Zemer/Status/<creator>/<yyyy-MM-dd HH-mm-ss>.<ext>`
  (posted time, device zone; colons are illegal so hyphens). Text statuses render to a PNG
  (`StatusTextImage`, color-agnostic - the composable passes theme colors in). Kept as `kind == "text"`
  so the chip filter still classifies it.
- **Gated on `BlockVideosKey`** everywhere (the FAB, the Downloaded card, the library screen) - statuses
  are video-first, same gate as the row/preferences.
- **The saved viewer is at FULL PARITY with the live one** (`SavedStatusScreen`): creators are
  cube-pager pages you swipe between (`SavedStatusViewModel` groups all saved statuses by creator), with
  the same segment bars/header (`StatusStoryTopOverlay`), auto-advance, tap-left/right, press-hold pause,
  background-pause, the `ExpandableStatusCaption` (Read more / clickable links / inline copy) for a
  captioned image/video and a copy pill for a text status, and the shared `StatusLoadingIndicator`
  (avatar + ring) while a video prepares - only the media comes from the DOWNLOADED files. It reuses the
  same shared components + the `cubeFace` transform as `StoryScreen`; the `faceCreator` gate + poster
  cache (`rememberVideoThumbnail`, byte-bounded LruCache) keep the swipe flash-free.
- **The library is a flat grid with filters + multi-select** (no grouped/shelf view - that was removed):
  kind chips (All/Video/Image/Text), a Recently-saved/Recently-posted sort, and - when more than one
  creator is saved - a creator-avatar filter row (tap to filter to one creator, tap again to clear). A
  long-press opens the standard bottom-sheet menu (`SavedStatusMenu`: avatar/name/date header +
  `Material3MenuGroup` Select / Remove). **Select** enters multi-select (the shared `SelectionTopActions`:
  count / select-all / a bulk-remove menu via `ItemWrapper` + `removeAll`); the tile shows an accent
  border + check badge when selected. Grid tiles decode a video poster frame via the cached
  `rememberVideoThumbnail`; text tiles render natively (never cropped).

### Offline search backup (`offline/` — the outage fallback)

A downloaded, incrementally-synced snapshot of the corpus serves `/search`, `/artist`, `/album`,
`/home-rows` and `/zemer-playlists` when `search.zemer.io` is unreachable — a faithful Kotlin port of
the zemer-search read layer returning the SAME wire models, so a fallback response is consumed
identically to a live one. Full detail in `docs/offline/README.md`. The invariants:

- **Server-first, always.** `serverOrOffline` falls back only on `isZemerServerUnreachable()` —
  `IOException` **or** `UnresolvedAddressException` (Ktor CIO signals no-network/DNS that way; it is
  NOT an IOException). A 404-null is returned as-is and never triggers the fallback; a non-network
  exception is never masked. Only SERVER responses enter the search LRU (a cached offline result
  would outlive the outage). `/playlist` and `/radio` are live-only (not in the snapshot).
- **Kosher defenses:** a 14-day staleness cap (`subsetSnapshotIsFresh`), the live Firestore-synced
  whitelist overlaid at corpus load (`SubsetCorpus.withLiveWhitelist` — de-whitelisted artists drop
  the moment the app's whitelist sync lands, `isFemale` comes from the live flag), and ONE shared
  content gate (`contentGatePasses`) + `idDropped` across every offline surface — never hand-inline
  the female/KidZone/video predicate per site.
- **Sync is staged and crash-safe:** shards are content-hash diffed, downloaded to `.staged` files,
  verified, promoted only when ALL verified, and the manifest commits last; `loadCorpus` re-verifies
  shard hashes at read time, and an unknown manifest `schema` generation is rejected wholesale
  (cipher precedent). Enabled = **daily auto-update on ANY connection** (no metered gate — a product
  decision), running on the syncer's OWN scope so leaving a screen never cancels a download.
- **Parity is the correctness bar:** the port is verified against captured live responses (id-set +
  order) and thumbnails deliberately match the server's `mqdefault` variant — do not "fix" them to
  `thumbnailFor`'s `hqdefault`, that breaks the parity diff. See the offline unit tests + the
  handoff doc `zemer-app-ondevice-fallback-subset.md`.
- **Discovery:** an onboarding step (`OnboardingSearchBackupScreen`) for new users and a one-time
  promo (`OfflineBackupPromoCard`) above Zemer search results for existing installs; declining the
  onboarding offer also silences the promo.

### Tracking (anonymous usage telemetry)

Six events (`open`/`search`/`play`/`click`/`action`/`impression`) POSTed to `tracking.zemer.io`;
full detail in `docs/tracking/README.md`. The rules that must not regress:

- **Telemetry may never break the app**: every `Tracker` entry point is a fire-and-forget
  `scope.launch`; failures are silent; the on-disk queue caps at 500 dropping oldest; a 400 drops
  the batch. It's a JSONL file under `filesDir` — deliberately NOT a Room table.
- **Identity is one random UUID** (`TrackingDeviceIdKey`) and nothing else — the server 400s
  non-canonical ids, so only `UUID.randomUUID()` output is ever sent. Never add account/device/
  location identifiers to an event.
- **Debug builds send `debug: true` in the batch envelope and the SERVER discards them** — the
  client path is identical in debug and release; never gate the tracker on `BuildConfig.DEBUG`.
- **`play` fires for EVERY listen, however short** (`MusicService.onPlaybackStatsReady`), one per
  listen when it ends; `source` comes from `Queue.playSource` + `Tracker.playSources`
  (context vs radio-fill vs other) — new queue types/surfaces must declare their source, and radio
  continuation must keep registering as `radio`. `ZemerRadioQueue` hardcodes the answer (fill =
  `radio`, the preloaded seed = the queue's declared source; the menus' Start radio declares
  `PlaySource.RADIO`); since every single-song tap now runs a seed-first radio queue, the `radio`
  share of `play` events shifted UP by design — a dashboard reader should expect it.
- **`action` hooks live at chokepoints** (entity `toggleLike()`s, `DownloadUtil` download entry
  with `fromUser=false` for machine enqueues, `DatabaseDao.addSongToPlaylist`, share buttons) —
  don't add per-surface duplicates, and keep machine-initiated work out of the user-intent signal.
- **`impression` counts what was SHOWN, and OVER-counting silently penalises a song** — the server's
  exposure dampener docks a song for being widely shown, so the definition is deliberately strict:
  inside the viewport AND settled there ~300ms, deduped per `(surface, videoId)`. Never count a
  composed-but-offscreen row (Compose composes ahead of the viewport), never count a row a fling
  passed through, and a row nested in a lazy parent must check the PARENT's viewport too — its own
  says nothing about whether it is on screen. When in doubt, do not report.
- **Impressions are the only event type that may be DROPPED rather than queued** — they outnumber
  plays by an order of magnitude and share the one 500-event drop-oldest queue, so they are
  discarded while the upload backoff window is open and past half the queue cap. Both drops are
  song-independent, which is what makes them free; the per-POST row cap exists because the server's
  truncation would NOT be.
- **Surface slugs are the server's coverage-gate vocabulary** (`TrackingSurface`) — renaming one
  reads as a surface disappearing and re-closes the gate. Treat them as append-only, and send the
  tracking maintainer an updated declared list whenever a release instruments a new surface.
- One `search` event per executed query (the per-query ViewModel guard) — never per keystroke or
  per chip switch. Everything is tracked (KidZone included), no opt-out — a product decision,
  2026-07-05. Each event carries `provider` (a Zemer extension per
  `handoff-docs/zemer-tracking-search-provider-request.md`); the server contract accepts
  `"zemer"`/`"youtube"` and stores anything else NULL, and since the YouTube engine's removal the
  app is single-engine so the value is the pinned constant `SEARCH_TRACKED_PROVIDER = "zemer"`
  (unit-tested) — keep sending the field, the dashboard splits on it.
- The one-shot **history backfill** (`PlayHistoryBackfill`) uploads the local listen history as
  `play_backfill` events through `Tracker.uploadBackfill` — NEVER through the live queue (its 500
  cap must not be flooded) but sharing the single-in-flight + backoff discipline; row-ID cursor
  (loss-free resume), a persisted max-id bound so live-tracked rows never double-upload, device-zone
  timestamp conversion (wall-clock-as-UTC drops east-of-UTC users' freshest history), permanently
  off once its done-flag is set, paced under the server's per-device batch limit.
- The one-shot **library-action backfill** (`LibraryActionBackfill`) uploads the currently-liked /
  currently-downloaded song **snapshot** as `action_backfill` events (`favorite`|`download` only)
  through the same `Tracker.uploadBackfill` path and pacing. Differences from plays that must not
  regress: **10-year** acceptance window, not 3 (an old `likedDate` on a still-liked song is a
  long-standing favorite — don't "fix" the constant back); resume is by persisted **acked-line
  count**, not a row cursor — snapshot timestamps are NOT stable across attempts (zone changes
  shift every `t`; `SyncUtils.likedSongs` rewrites `likedDate` to sync time), so server dedup
  cannot absorb a full replay and the prefix skip is what bounds it; favorites upload before
  downloads (stable order the prefix skip depends on); pacing sleeps only BETWEEN batches (the
  done-flag lands immediately after the last ack); downloads include machine enqueues (snapshot
  can't see `fromUser`) so the server weights them as weak corroboration. Same device-zone
  timestamp conversion; 90 s start delay (load spreading, NOT an ordering guarantee). Full
  contract: `handoff-docs/zemer-tracking-action-backfill-request.md` (SETTLED).

### The player background system (one effective style, one extractor)

The full player (`ui/player/Player.kt`) and the mini player (`ui/player/MiniPlayer.kt`) share a
single source of truth in **`ui/player/PlayerBackground.kt`** — never re-derive any of this per
surface (the two drifting out of sync is exactly what bit a past change):

- **`PlayerBackgroundStyle.effective()`** downgrades **BLUR → DEFAULT below Android 12**. The blur is
  a `RenderEffect`, a no-op before API 31, so a raw BLUR there renders the bright artwork under the
  light-on-dark transport — illegible. Every *render* decision (background, text/icon colors, status
  bar, gradient enable) must read the **effective** style. `Player.kt` shadows the preference
  (`val playerBackground = playerBackgroundPref.effective()`) so all downstream sites are covered for
  free; the settings list hides BLUR when **`isBlurSupported`** is false. `effective(blurSupported)`
  takes the flag explicitly so the rule is unit-tested without an Android runtime
  (`app/src/test/.../ui/player/PlayerBackgroundTest.kt`).
- **`rememberPlayerGradient(mediaId, thumbnailUrl, enabled, fallbackColor)`** is the *only* gradient
  extractor: one bitmap-decode + Palette pass per track, memoised in a shared bounded `LruCache`, so
  the two surfaces never decode the same artwork twice and the cache can't grow unbounded. The
  previous palette is held while a new one extracts (and on a decode failure) to avoid a flash.
- **`playerGradientStops(colors)`** is the *only* place the gradient color stops are built (3-stop
  for ≥3 swatches, else a single-hue fade to black) — both surfaces call it so the gradient shape
  can never drift between them.
- **Light (white) content only when a dark backdrop is actually painted.** A blur layer needs a
  `thumbnailUrl`; a gradient layer needs non-empty `gradientColors`. Until then the surface stays on
  the solid `surfaceContainer` with theme-colored text — flipping to white before the backdrop
  exists puts white text over the light Home screen showing through the (transparent) mini bar.
- Status-bar legibility is a `DisposableEffect` in `Player.kt` keyed on **(background, theme,
  `state.isExpanded`)**: it forces light icons only while the sheet is **expanded** (the dark
  background actually covers the screen); collapsed/dragging follows the theme. It hands the bar back
  to the theme-correct appearance — matching `MainActivity.setSystemBarAppearance`
  (`isAppearanceLightStatusBars = !isDark`) — on dispose, never a stale captured snapshot.
- The new-design transport cluster caps the labelled play button via `BoxWithConstraints` (to the
  width left after the two skip buttons + gaps) so it shrinks to fit narrow widths instead of
  overflowing; `TransportSkipButton` cancels its long-press repeat the moment the press is released.

This UI is **Material 3 *standard*** (`MaterialTheme`, not `MaterialExpressiveTheme`): Expressive-only
APIs (e.g. `LinearWavyProgressIndicator`) need a newer material3 and are deliberately not used. New
transport buttons reuse `TransportSkipButton` + the accent focus border; new D-pad rows reuse
`Modifier.focusBorder()`. `scripts/ui-audit.sh` ratchets raw `Modifier.blur(` in `ui/` (R12) — route
player blur through the effective style.

### The download system (ONE unified path — never fork it)

Downloads go **exclusively** through `MediaStoreDownloadManager` (file saved to MediaStore, durable
truth is `SongEntity.isDownloaded` + `mediaStoreUri`; live progress in its in-memory `downloadStates`).
The legacy ExoPlayer download map (`DownloadUtil.downloads` / `getDownload()`) is **dead** for
status — nothing the UI reads should touch it.

Every download/progress affordance reads ONE path; do not re-implement per surface:
- **State (pure, tested):** `playback/DownloadStateResolver.kt` — `forSong`/`aggregateSongs`/
  `aggregateByIds` combine persisted `isDownloaded` **OR** live MediaStore state (so a download
  survives a process restart — reading the live map *alone* is the bug that makes downloads "vanish"
  after relaunch). `songProgress`/`aggregateProgress[ByIds]` for the progress fraction.
- **UI:** `ui/component/DownloadStatusUi.kt` — `rememberSongDownloadStatus/Progress`,
  `SongDownloadBadge` (default song-row badge), `AggregateDownloadButton` (album/playlist header),
  `DownloadStatusIcon`.
- **Menu rows:** `ui/menu/DownloadMenuItems.kt` `downloadMenuItem(...)`, decided by
  `playback/DownloadMenuLogic.kt` (`songRow`/`collectionRow`, pure + tested). A download row **never
  dismisses the menu** (it animates Download → progress → Remove in place). Videos use the same path
  (`DOWNLOAD_VIDEO`, hidden when videos blocked).
- **A collection NEVER shows a FAILED/retry row** (`collectionRow` takes only the aggregate status —
  REMOVE / DOWNLOADING / DOWNLOAD). A failed member just leaves the aggregate NOT_DOWNLOADED, so the
  collection offers DOWNLOAD again, which re-enqueues only the not-yet-downloaded members (= retry)
  and stays removable once everything is on disk. A dedicated collection "retry" row is a **dead end**
  — it hid Download AND Remove and re-failed the dead track forever with no escape. Only *single*
  songs get a FAILED row (`songRow`). Don't reintroduce an `anyFailed` arg on `collectionRow`.
- **Downloading a collection** whose songs load async (online album/playlist/selection menus):
  resolve/fetch the songs **at click time** (fetch-if-empty) so the first tap downloads — a
  captured-empty list is the "press once does nothing, twice works" bug. **EVERY action in that menu
  — Download, Remove, *and* the aggregate status — must read the SAME resolved/fetched list, never the
  original (possibly-empty) `songs` prop.** A Remove that iterates the empty prop while Download
  iterates the fetched list silently removes nothing (was a real bug on the Home long-press playlist
  menu). For online items aggregate by videoId (`aggregateByIds` + a persisted-downloaded id set) so
  progress animates without Room entities, and on Download **persist each `MediaMetadata`
  (`database.insert`/`transaction { insert(...) }`) THEN download** — a bare `database.song(id).first()`
  returns null for a not-yet-persisted id and the tap silently no-ops.
- **Playback of a downloaded file** (`MusicService.createDataSourceFactory`): use the local file when
  it opens; if it's genuinely gone, **stream this play AND re-enqueue a download to self-repair** —
  never crash with ENOENT, and never silently delete the `isDownloaded` flag (that makes downloads
  vanish from the Downloaded playlist). Two non-obvious rules here: (1) the self-repair must **skip
  re-enqueueing a download whose live state is already FAILED this session** (check
  `downloadUtil.mediaStoreDownloadState(id)`) — the manager only no-ops for active/complete, not
  FAILED, so a permanently-unrecoverable source would otherwise fire a fresh full download on *every*
  play; (2) the file-open probe (`downloadedFileOpens`) returns false on **any** open failure
  (FileNotFound *or* SecurityException/other) so playback streams — handing ExoPlayer a URI we just
  failed to open only fails again.
- **`database.query {}` is fire-and-forget** (it posts to an executor, doesn't suspend). NEVER split a
  single logical mutation across two `query {}` blocks that touch the same row — they race and the
  wrong one can land last. The download-mark bug was exactly this: `markSongAsDownloaded` upserted the
  row twice (relations with `isDownloaded=false`, then `isDownloaded=true`), so a downloaded song
  intermittently persisted `isDownloaded=0` with no `mediaStoreUri` — the file saved but it "didn't
  download" / streamed / vanished. Do the whole mutation in one `database.transaction {}` whose final
  write is authoritative.
- **`markSongAsDownloaded` must NOT clobber user state.** It bases the persisted row on the **existing
  DB row** (read first) and overwrites only the download-owned columns (`isDownloaded`, `dateDownload`,
  `mediaStoreUri`, `isVideo`) — a full-row `@Upsert` of the caller's `Song` would silently reset
  `liked` / `inLibrary` / library tokens when the caller handed a stale/partial `Song` (e.g. an
  album-page entity, or the like-then-auto-download race). It also backfills `duration` AND
  `thumbnailUrl` only when the existing row lacks them.
- **Backfill `duration` AND `thumbnailUrl` from the playback response** in `performDownload`
  (`playbackData.videoDetails`) — songs reached via an album/playlist page, and standalone videos
  opened from the Video player, often carry neither (showed "0:00" / no artwork in the Downloaded list).
- **A per-download video bitrate must survive a failed attempt.** `requestedVideoBitrate` is cleared on
  success / cancel / delete, **never** in the per-attempt `finally` — else `retryDownload` re-issues the
  download with no bitrate and silently falls back to best/default quality (a large file over a metered
  connection the user explicitly capped).
- **Remove must delete the actual file on EVERY backend.** A custom download path saves a SAF document
  uri; `ContentResolver.delete` silently no-ops on those, so `MediaStoreHelper.deleteFromMediaStore`
  routes document uris through `DocumentsContract.deleteDocument`.

Enforcement (so this can't regress): `scripts/check-download-unification.sh` (whole-app, wired into
the UI-audit workflow) + `scripts/ui-audit.sh` rule **R13** fail CI on any `downloadUtil.downloads` /
`getDownload(` read, any `Download.STATE_*` outside the legacy infra (`DownloadUtil.kt` /
`ExoDownloadService.kt`), or any per-surface `Icon.Download(`. Full rules: `docs/ui/standards.md §12`.
When you touch downloads run both scripts and add pure regression tests next to the resolver/menu
logic (the manager/playback layer needs Robolectric, which the project does not have — say so rather
than skip silently).

### tests/ — the hard-data streaming harness

Node ≥20 scripts (deps vendored in `tests/node_modules`, no install needed) that reproduce the app's *exact* stream path (same `/player` request as `InnerTube.kt`, same cipher run in jsdom, same poTokens) against the live CDN — so playback is measured, not guessed. Needs `innertube_cookie.txt` at the repo root (a dumped logged-in session; **gitignored**, never commit).

- Run one: `node tests/cipher.mjs` (live player health), `node tests/validate-player-config.mjs <hash>`, `node tests/web-remix-stream.mjs`. Pin a player with `PLAYER_HASH=<hash>`.
- `tests/README.md` + `tests/INVESTIGATION.md` are the methodology and the symptom-indexed runbook — read them first when streaming breaks.
- The harness mirrors app constants on purpose; when `YouTubeClient.kt` / `PoTokenGenerator.kt` change, update the matching mirror (`clients.mjs` / `potoken.mjs`). Player configs are **not** mirrored — `tests/player-configs.mjs` reads the same `player_configs.json` the app bundles (requires the cipher submodule checked out; if missing, scripts fail with an actionable message).
- Loader unit tests (no cookie or network needed): `node --test tests/player-configs.test.mjs` — validation rules, collision rejection, the `config-covers.mjs` CLI, and the cross-language parity fixtures shared with the cipher repo's Kotlin tests.
- **`tests/search/`** is the same idea for the *search* path: faithful Node ports of the app's four search functions (`searchSuggestions`/`searchSummary`/`search(filter)`×6/`searchContinuation`) run against live YouTube Music — `node tests/search/run.mjs [query...]`. It reproduces the app's exact request (WEB_REMIX, `setLogin=false` → visitorData only, no cookie/auth) and reports any error: a strict-deserialization break (a non-null field YouTube dropped → whole response fails → "No results"), a parser drop (with the exact field), the `searchContinuation` NPE, or an empty result. `node --test tests/search/self-test.mjs` proves the checker catches breaks (no network). The kotlinx strict-field table in `tests/search/schema.mjs` is transcribed from the innertube models — keep it in sync when their nullability changes. Zemer's artist-whitelist filter runs *after* these functions (needs the app DB) and is the next suspect when they're healthy but search still looks empty. See `tests/search/README.md`.

### Modules & app layout

- **`:app`** (`com.jtech.zemer`) — single-activity Jetpack Compose UI, Hilt DI (`App.kt` `@HiltAndroidApp`, modules under `di/`), Media3. `MainActivity` + `NavigationBuilder.kt` host the Compose nav graph; `MusicService` (a Media3 `MediaLibraryService`) owns ExoPlayer and is bridged to the UI by `PlayerConnection`, with `playback/queues/` implementations. State is Room (`db/MusicDatabase.kt`, `song.db`) + DataStore preferences (`utils/DataStore.kt` — holds the auth cookie / visitorData / dataSyncId and all settings). Content-filtering (whitelist, KidZone) lives in `sync/` + `utils/SyncUtils.kt`. The offline search-backup snapshot (sync engine + read-layer port) lives in `offline/` (on-disk store under `filesDir/subset/` — see §Offline search backup). Downloads via Media3 `ExoDownloadService` plus a MediaStore path. Crash/error telemetry is Firebase Crashlytics: `utils/CrashReportingTree.kt` (planted in `App.kt`) turns every Timber log (DEBUG+) into a breadcrumb and `reportException()` calls into non-fatal issues — so report errors via `reportException()`/`Timber`, never `printStackTrace`; release CI uploads R8 mappings and native symbols automatically.
- **`:innertube`** (`com.metrolist.innertube`) — the YouTube Music InnerTube API client (Ktor): request building, auth context, page parsers that turn YouTube renderer trees into typed models. Holds the `YouTubeClient` definitions and the NewPipe bridge for signatureTimestamp.
- **`:lrclib`** / **`:simpmusic`** (`com.metrolist.*`) — lyrics provider clients (LrcLib.net and api-lyrics.simpmusic.org).
- **`cipher`** — see "Cipher / player rotation" above.

## Documentation

`docs/` is a **code-derived docset** — most of it is generated, not hand-written:

- `docs/generate.py` regenerates `docs/repository-map.md`, `docs/build-release.md`, and `docs/reference/*.md` from tracked source (file inventory; Gradle / CI / native / JVM-module facts). It is idempotent — converges in one run — and needs PyYAML (`pip install pyyaml`) for `build-release.md`. **Never hand-edit those generated files**; change the source or the generator.
- `.github/workflows/docs-regenerate.yml` runs the generator on every push to `main` and commits any change back (`[skip ci]`), so the generated docs stay current automatically. Running `python3 docs/generate.py` locally before a commit is still good practice.
- Hand-authored docs are the exception — this `AGENTS.md`, `docs/ui/standards.md` (the UI rulebook), and prose/rationale carry intent a generator can't derive.

## Verifying your changes

- **Build both** `:app:assembleDebug` and `:app:assembleRelease` (release catches R8/shrink breakage).
- **Streaming / cipher / poToken changes** must be proven with the `tests/` harness against the live CDN (HTTP 206 / whole-song drain), and ideally confirmed on-device via the `YTPlayerUtils` logcat (`Playback: client=…, itag=…`).
- **UI changes** must comply with `docs/ui/standards.md` (the UI rulebook — Material 3 standard, design tokens, shared `Dialog.kt` dialogs, shared grouped-list components `Material3SettingsGroup`/`Material3MenuItem` per section 11) and stay 100% D-pad navigable — any new row/list component must carry the `.focusable()` + focus-border treatment, since upstream (Metrolist) rows omit it. Update the doc when a rule changes. Run `bash scripts/ui-audit.sh` — it ratchets sections 5, 7, 8 and 11 (no *new* hardcoded user-facing strings, raw `AlertDialog`s, raw font sizes, hardcoded hex colors, or raw `ListItem(` action rows under `ui/menu/`; strings and dialogs are baselined at zero, menus build from `Material3MenuGroup`).
