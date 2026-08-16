# Working with Zemer as an AI agent

Zemer is a "Kosher" YouTube Music client for Android (Kotlin, Jetpack Compose, Material 3), forked from [Metrolist](https://github.com/MetrolistGroup/Metrolist) with content-filtering layered on top (artist whitelist, KidZone, per-artist flags like `isFemale`/`isChasid`). The shared library modules keep the **`com.metrolist.*`** package namespace while the app is **`com.jtech.zemer`** - that split is intentional, don't "fix" it.

## Project rules

1. Pull the latest `main` before starting, to minimize merge conflicts.
2. Commit messages follow `type(scope): short description` (e.g. `fix(player): skip HEAD validation for WEB_REMIX`, `feat(ui): add history button`); the scope is optional.
3. User-facing strings: add/edit **only** the default English `app/src/main/res/values/metrolist_strings.xml`. Do **not** edit `strings.xml` or any translated `metrolist_strings.xml` - other locales are managed separately.
4. Database schema changes (`app/.../db/MusicDatabase.kt` + entities) require a versioned Room migration and are high-risk - confirm with a human before changing the schema.
5. Don't rename the `com.metrolist.*` library namespace, and don't bump the app version - version bumps are a release-team decision.
6. Follow Kotlin/Android best practices; prioritize performance, battery, and maintainability.

## Working agreement

- **Do not commit, push, or merge unless explicitly asked in the current request.** When you are authorized, doing so is fine and the responsibility lies with the requester. Never rewrite git history, force-push (except rebasing your own branch), or delete branches without explicit instruction.
- **Never commit secrets** - `innertube_cookie.txt`, cookies / poTokens, `release.keystore`, `google-services.json` are gitignored; keep them that way.
- Edit README / docs only when that is the task, not as a side effect.
- Ask a human when requirements are unclear; don't assume. Add comments only for complex or non-obvious logic.

## Engineering rules (non-negotiable)

- **Regression tests are required** for every behavioral change or bug fix wherever a test does not demand heavy new infrastructure (plain JVM/unit tests, Robolectric, or the `tests/` streaming harness for stream/cipher/poToken work). "It builds" and "I watched it work once" are not regression protection. If a fix genuinely cannot be tested without heavy new infrastructure, say so explicitly in the change description instead of skipping silently.
- **Keep code modular.** No new god files: split by responsibility (screen scaffolding vs. business logic vs. data access). New logic goes behind small, single-purpose functions/classes - not appended to `MainActivity.kt`, `MusicService.kt`, or other existing giants; shrink them when touching them (`OnboardingScreen.kt` was one such giant and is now split into per-step files under `ui/screens/onboarding/` - keep it that way).
- **Keep it professional.** Code must pass the bar of an external staff-engineer review: layering respected (UI does not run database/network calls inline), errors handled rather than swallowed, user-facing strings localized, no copy-pasted near-duplicates, no dead code left behind.

## Build & run

- **JDK 21**, `compileSdk`/`targetSdk` 36, `minSdk` 26. Native code targets `arm64-v8a` + `armeabi-v7a` only (NDK 27). There are no product flavors.
- `./gradlew :app:assembleDebug` - debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:assembleRelease` - release APK. **Build BOTH after any change**: release runs R8 (`isMinifyEnabled = true`) and catches shrink/keep-rule breakage that debug never will.
- Submodules are required: `git submodule update --init --recursive` (`cipher/` and the native `app/src/main/cpp/bento4`). CI pulls a prebuilt bento4 from `ZemerTeam/zemer-bento4`.
- Install to a connected device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Stream resolution logs under logcat tag `YTPlayerUtils` (also `PoTokenWebView`, `Zemer_CipherFnExtract`).
- CI: `.github/workflows/release-build.yml` builds a signed release on push to `main` / PRs (skips `docs/**`, `tests/**`, `**.md`); keystore + `google-services.json` come from base64 secrets.

## Architecture & the danger zones

### The streaming pipeline (the core; where things break)

`app/.../utils/YTPlayerUtils.kt` `playerResponseForPlayback()` is the heart of the app. It:
1. Tries `WEB_REMIX` (main client), then the `STREAM_FALLBACK_CLIENTS` list - exactly `VISIONOS (1.02)` → `VISIONOS_0_1` (the old config as its second chance) → `WEB_CREATOR` → `ANDROID_VR_1_65_10` → `TVHTML5_SIMPLY` → `MWEB` - enable-state settable per client family in the Stream Sources setting (whose displayed order the array must keep matching). The 2026-08-15 validation pass (whole-song drains via `tests/client-fulldownload.mjs`, yt-dlp-master-exact configs, on-device confirmation) **removed every proven-dead client**: the pre-1.65 ANDROID_VR variants (version-keyed “confirm you’re not a bot” gate - only the 1.65.10 eureka build passes), MOBILE/ANDROID (HTTP 400 with auth, SABR-only without), WEB-as-stream-fallback (SABR-only; the def stays for InnerTube next/transcript), IOS/IPADOS (403 past the 1-MiB wall), ANDROID_CREATOR, TVHTML5_SIMPLY_EMBEDDED_PLAYER (server-killed), and the 7.x TVHTML5 itself (SABR-only; the "TVHTML5" toggle now governs TVHTML5_SIMPLY). Retired configs + verdicts live in `tests/clients-retired.mjs`. ANDROID_VR serves a DIRECT url used AS-IS (no sig/n-transform/pot - web transforms CORRUPT it); `MWEB` (yt-dlp-master iPad UA, own toggle) is a login-REQUIRED cipher fallback re-added 2026-08-15 - whole-song validated authenticated, 403s anonymous, so it sits last and login-less sessions skip it.
2. For web clients, deciphers the `signatureCipher` (sig + n-transform) via the **`cipher` submodule**, then appends a BotGuard `pot=` token.
3. Validates, then hands the URL to ExoPlayer in `MusicService`.

Two hard-won facts that govern this area - always verify against the live CDN via `tests/`, never reason from convention (the convention was wrong here):
- **googlevideo serves the first 1 MiB of a stream free, then 403s every new connection** unless the URL's `&pot=` is bound to the **videoId** (not visitorData). Clients whose attestation the web poToken can't satisfy (IOS/IPADOS and MWEB - all three since removed for this reason) 403 past the wall under every binding.
- **`validateStatus` does a HEAD that false-negatives** (403 on URLs that GET fine), so WEB_REMIX intentionally skips it.

### RELAY playback mode (`playback/relay/` - the filtered-device bypass)

An **opt-in, login-less** mode for devices whose kosher filter blocks `music.youtube.com` / `googlevideo.com`.
Discovery already rides `*.zemer.io`, so only playback breaks behind a filter; RELAY moves **only the media
source** onto the whitelisted relay host `stream.zemer.io`. Full contract + the app↔server thread live in
`handoff-docs/zemer-app-filtered-playback-relay-request.md`. The rules that must not regress:

- **One flag, default off, DIRECT is untouched.** `PlaybackModeKey` → `constants/PlaybackMode` (`DIRECT`/
  `RELAY`), default `DIRECT`. Every relay branch is gated on it; a normal Google/anonymous login runs zero
  relay code. **Isolation is the whole point - never let a relay change touch the DIRECT path.**
- **The seam is `MusicService`.** `createDataSourceFactory()` (DIRECT) is unchanged; a per-open dispatcher
  (`playbackDataSourceFactory`) picks the separate, **cache-free** `RelayDataSourceFactory` only when the
  flag is on. `relayModeNow` mirrors the flag but is seeded **null** and resolved with a one-time
  synchronous DataStore read on the first open, so a relay user's cold-start play is never mis-routed to
  DIRECT. Both factories share **`resolveDownloadedFileUri`** (the DIRECT local-file logic extracted to a
  method): a downloaded song plays from disk, decided once at position 0, with self-repair + `recoverSong`
  + video-mode nudge - so relay never mid-track-switches (the 1f48d89 class of bug).
- **`playback/relay/` holds the pure/isolated pieces:** `RelayStream` (URL builder - `/stream?v=` audio,
  `&kind=video` 360p mp4, `/download?v=`), `RelayDownload` (container-sniff → extension + HTTP-status
  classification, unit-tested), `RelayDataSourceFactory` (the isolated factory). Keep logic here, not in the
  giants.
- **Onboarding + gating.** The login gate has a third option **"I have a filter"** (login-less: sets
  `RELAY`, no cookie). `MainActivity`'s gate must NOT bounce a relay session (it derives login + relay from
  one DataStore snapshot via `produceState`). A **normal login globally resets `RELAY`→`DIRECT`** in
  `App.kt` (from ANY entry point), and the **Settings toggle + the nav-drawer Account entry are hidden when
  not login-less** (relay is accountless). These gates key off the cookie's `SAPISID` (true for anon too),
  the codebase's standard "has a session" idiom.
- **Downloads** pull `/download` (m4a/itag140 → embeds cover art like a normal download; the relay may fall
  back to Opus/webm, which the sniff saves as `.opus` since MediaStore.Audio rejects `.webm`), verify
  completeness against `Content-Length`, and play offline from the local file. **Video** re-uses the normal
  `VideoModeLogic` path pointing at the relay's 360p `&kind=video`; an audio-only id 404s → revert to audio.
- **The song-details sheet stays YouTube-free AND source-opaque in relay** (`ui/utils/ShowMediaInfo.kt`):
  `getMediaInfo()` is an InnerTube call whose Views/Likes/Dislikes/Subscribers/Description are YouTube-sourced,
  so it is **not requested** when `relayMode` (not merely hidden if a filtered device happens to fail the
  call - an unfiltered relay session would otherwise show YouTube stats). Relay also has no local
  `FormatEntity`, so the whole **Information** section is built into a list and rendered only when non-empty
  (`informationItems.isNotEmpty()`) - in relay it is empty, so the section title and rows are omitted
  entirely. The relay sheet shows only General (title/artists/media-id from the local `song`); it never
  surfaces a "playback source" / relay row - the mode is deliberately opaque about how it resolves playback.
- **Relay device counting + debug exclusion (two mutually-exclusive headers by build type).** On every
  RELAY media request - `/stream` (audio and the `&kind=video` variant; a default request property on the
  isolated relay OkHttp factory) and `/download` - the app sends exactly one of:
  - **Release:** `x-zemer-device: <RelayDeviceIdKey>` (`RelayDeviceId.HEADER`) - a per-install random UUID
    **separate** from the zemer-stats `TrackingDeviceIdKey`. The relay pairs it with the request's
    filter-egress IP to count distinct devices per content-filter (Gentech/Techloq/…); the pairing is a
    filter-choice fact, relay-only, and never joined to the PII-free telemetry. `RelayDeviceId.get*` return
    the id only in release.
  - **Debug:** `x-zemer-debug: 1` (`RelayStream.DEBUG_HEADER`) - the relay serves the bytes but does NOT
    count the request, so debug streaming never inflates the relay gauge (`RelayDeviceId.get*` return null
    in debug, so no id is sent then).
  Both are default request properties on the relay OkHttp factory (playback) / the `/download` request, so
  they ride relay hosts only and never leak to googlevideo or DIRECT. The header is the only per-device
  relay signal: zemer-stats stores no IP, so telemetry alone cannot attribute a device to a filter.
- **Errors** surface the contracted copy (404 "not available", 502/503 "try again"); the relay's egress pool
  is server-side, so a transient 502 is retried, not the app's bug.
- **The relay PLAYBACK OkHttpClient carries generous timeouts, NOT OkHttp's default 10s**
  (`RelayDataSourceFactory`): the cache-free relay streams fresh over the same slow rotating-proxy egress as
  downloads, so the 10s default throws `SocketTimeoutException` mid-stream on a hiccup or slow fresh resolve
  → player error → stop, recurring per track in the background (the "keeps stopping" bug). Matched to the
  download client (connect 30s / read 60s) so a slow proxy buffers instead of erroring. Relatedly,
  `onPlayerError` reads `AutoSkipNextOnErrorKey` from a synchronous `@Volatile` mirror (like `relayModeNow`),
  never a main-thread blocking DataStore read, so a relay error burst can't ANR the main thread.
- **Streaming is still the danger zone:** any change here is proven with `tests/` (the DIRECT resolver
  refactor was), and app↔relay contract changes travel as handoff-doc edits, never as guesses.

### Watch-time reporting (the YouTube playback-stats session; DIRECT only)

Every DIRECT listen - music, video-songs and podcast episodes alike - emulates a genuine YouTube Music
stats session: one `cpn`
per listen, a `videostatsPlaybackUrl` ping when playback actually STARTS (`cmt=<start>`, `final=0`),
`videostatsWatchtimeUrl` pings every ~30s of playback plus on pause/seek, and a `final=1` ping when the
listen ends. This replaced the legacy single end-of-listen view ping (fresh random cpn, no watch time) - do NOT reintroduce an end-of-listen `registerPlayback` call, it would double-report the session. The
rules that must not regress:

- **Honesty is the hard rule.** Reported ranges come only from real player positions via the pure,
  JVM-tested `playback/WatchTimeSegments` (drains are DELTAS like the official client, seeks are never
  watched time, a paused player accumulates nothing, sub-500ms jitter is dropped, a backwards position
  without a seek closes rather than fabricates). Fabricated watch time is invalid traffic by YouTube's
  definition and can flag a channel - never widen what gets reported beyond real playback.
- **Confirmed working on a live channel**: a direct Zemer play credits both a real VIEW and real
  WATCH TIME in YouTube Studio (14 views / 0.2 h). Views are durable; watch time from concentrated
  single-account testing is retroactively stripped as invalid traffic - expected, not a bug (real
  distributed users are the payoff; full detail + what to expect in `docs/watchtime/README.md`).
- **CDN-cpn correlation** (`playback/PlaybackNonceRegistry`, `MusicService.stampCpn`): the DIRECT
  googlevideo media request is stamped with the SAME cpn the beacon session uses (base.js
  `cpn=${clientPlaybackNonce}`), keyed by `VideoRendition.baseVideoId` so audio/video/merge-audio
  renditions of one listen share it; the reporter releases the id on finish (fresh cpn per play).
  Applied at every DIRECT `withUri` googlevideo site, NEVER a local-file uri / cache hit / the RELAY
  factory. Proven safe against the live CDN by `tests/watchtime-cpn-stream.mjs` (full-song drain, 206
  throughout). The registry is a bounded access-ordered **LRU that never evicts the live listen's
  cpn** (the reporter `pin`s the active id) - the earlier wholesale `clear()` past 64 entries could
  wipe the currently-playing cpn mid-stream and silently break this correlation. `qoe` and the
  traffic-source params are reproducible but deliberately not sent (a controlled A/B showed zero
  watch-time-survival benefit) - see `docs/watchtime/README.md`.
- **Ping cadence matches the official client, server-driven** (`playback/WatchTimeSchedule`, JVM-tested):
  the `/player` response's `videostatsScheduledFlushWalltimeSeconds` + `videostatsDefaultFlushIntervalSeconds`
  (live-verified `[10,20,30]` then `40`) drive the watchtime ping ticker at those wall-clock offsets, not a
  fixed interval (a fixed interval is a timing fingerprint). Falls back to the base.js `klA` default
  `[10,20,30]`/`40` when the response omits them. Pause/seek pings are extra state-change flushes and never
  advance the scheduled count.
- **`playback/WatchTimeReporter` owns the session** (the `EpisodePositionTracker` extraction pattern:
  state confined to the service main scope; one ordered ping channel per session so the playback ping
  always precedes its watchtime pings; beacons are fire-and-forget and must never affect playback).
  `MusicService` only forwards events: `onIsPlayingChanged`, `onPositionDiscontinuity` (which captures
  the departed item's REAL end position for the final ping - on ANY `AUTO_TRANSITION`, so a **repeat-one
  loop** back to the same item, position wrapped to ~0, is not under-reported; a rendition-swap's
  position-continuous seek, delta < 1s, fires no spurious ping), the real-transition hook placed AFTER
  the video-mode own-swap early-return (an audio↔video swap keeps its session - same listen),
  `STATE_ENDED`, `onDestroy`, and the stream resolver's `onTrackingResolved` seed (no second `/player`
  round-trip; cached/local plays fall back to one metadata fetch, the legacy ping's own behavior). The
  `session` ref is `@Volatile` (read from the resolver's background thread for schedule adoption).
- **The reporter reads the player through the `PlaybackProbe` seam, so its state machine is
  JVM-tested** (no Robolectric): the ~7 `Player` reads (position/isPlaying/playbackState/playWhenReady/
  currentMediaId/hasCurrentMetadata/volume) are behind `playback/PlaybackProbe`, `MusicService` adapts
  the real `Player`, and `onPositionDiscontinuity` takes primitive params instead of `Player.PositionInfo`.
  `WatchTimeReporterTest` drives the whole machine with a pure fake probe (Unconfined scope) and asserts
  via the observable offline-capture sink - played-range capture, seek exclusion, the ≥10s gate,
  paused-at-start privacy, the teardown end-position, rebuffer-is-not-a-pause, and the relay exclusion.
  The extraction is behavior-preserving; keep the probe returning exactly the `Player` values.
- **Boundary-capture hardening (never fabricate, never orphan) - these must not regress:**
  - On a track/queue CHANGE (a real transition, or `ensureSession` replacing a still-open session) the
    end position falls back to the departed item's OWN last-known position
    (`WatchTimeSegments.lastKnownPositionMs`), **never `player.currentPosition`** - after the change the
    player position belongs to the NEW item, so using it would close the departed listen with a
    fabricated range (a station join mid-listen was the worst case). But `onPlaybackEnded` AND
    `onDestroy` pass `player.currentPosition` explicitly: they are the SAME item (end-of-item / teardown,
    not a transition), and the last-known fallback lags the ticker cadence, dropping the tail on a
    swipe-kill.
  - The video-mode own-swap path calls `onOwnSwapTransition()`, which **neutralises** the captured
    end position (else a repeat-one loop's capture is inherited by a LATER real transition and
    fabricates) and **nulls `fmt`** (the single itag is no longer truthful once the rendition changed - omitting is honest, a stale wrong itag is not). `fmt` lives on the session, not the captured URLs.
  - A watchtime/final ping is **never sent for a session whose playback (open) ping was suppressed**
    (`PauseListenHistoryKey` on at start): that orphan half-session shape is one no real client
    produces. The `opened` flag is set INSIDE the playback-ping send, so a tracking block that carries
    only a watchtime URL (null playbackUrl) cannot orphan either.
  - A preloaded tracking resolution older than `TRACKING_MAX_AGE_MS` (1 h) is **re-fetched fresh** - a
    cache-served replay hours later would otherwise beacon a dead baseUrl and lose the credit.
  - A mid-track **rebuffer is not a pause**: `STATE_BUFFERING` while `playWhenReady` fires no
    state-change ping (a timing fingerprint the official client never emits) and drops no sub-500ms
    segment; position does not advance while buffering, so the open segment stays honest.
  - The scheduled-flush ticker **skips overdue offsets** after a long pause/rebuffer (rtMs is wall-clock
    incl. paused time) instead of spinning through a burst of immediate no-op flushes - the pause
    already flushed pending time, so it resumes at the next FUTURE offset.
  - `resolvedTracking` (the tracking-URL cache), like the cpn registry, **preserves the live listen's
    entry** when it clears past `MAX_CACHED_TRACKING`, so a resolution burst never wipes the playing
    session's URLs out from under its consumer.
  - The **deferred** open ping's `cmt` is the listen's REAL start position (`DeferredStatsRecord.openCmt`,
    the first watched-range start), never 0 - a resumed/offline listen begins nonzero, and open-at-0 +
    watchtime-at-60 is an inconsistency the live path never produces.
- **Known limitation (accepted):** a **video-mode** repeat-one loop is an own-swap (one session/cpn
  spans all loops), so it credits ONE view where the same track in audio mode credits N. This is the
  SAFE direction (under-count, never fabrication); fixing it would risk the own-swap session-continuity,
  so it is deliberately left. Do not "fix" it into a per-loop session without weighing that.
- **Hard exclusions:** RELAY mode (the spec's rule - beacons must never ride the relay egress; gated
  fail-safe as `relayModeNow != false`, so the unresolved cold-start window never beacons) and cast
  sessions (the receiver plays). Both are gated at session creation inside the reporter. The
  `PauseListenHistoryKey` privacy switch silences beacons too, re-checked PER PING so enabling it
  mid-listen silences the rest of the in-flight session.
- **Deferred offline recovery** (`playback/DeferredStatsQueue`, additive - the LIVE path is untouched):
  a genuinely OFFLINE listen (no network → no tracking URLs) is captured in the reporter's existing
  offline branch (relay/cast never reach it; an online cached play resolves fresh URLs and reports live)
  and re-pushed on reconnect as a **deferred** stats session (fresh `/player` → fresh cpn → playback +
  `final=1` watchtime, the STORED real ranges). Same honesty rule (`rt` ≤ played time; `PauseListenHistory`
  suppresses capture with the SAME per-ping semantics as the live path - paused-at-start captures nothing,
  and accumulation stops at the first paused ping), ≥10s gate, **never via the relay egress**. JSONL under
  `filesDir` reusing `TrackingQueue` + `FlushSchedule` (no Room/migration); single-flight, connectivity-
  triggered, and self-rescheduling whenever work remains (after a RETRY-backoff, and after a full batch
  of `BATCH_SIZE`=20 with records still queued it waits a short `PACE_MS`) - so a backlog larger than one
  batch fully drains on a stable connection AND a long-offline reconnect trickles the beacons out instead
  of firing the whole backlog as one burst; staleness-capped (7 d, which bounds the backlog size). **The watchtime ping fires only after the playback ping is accepted** (no orphan;
  a partial re-push under a fresh cpn would double-count); classification: playback 400→drop / playback
  not-2xx→retry, then watchtime 2xx→remove / 400→drop / else→retry (400 read from the thrown
  `ResponseException`, so DROP is reachable). The realistic win is the VIEW; details in `docs/watchtime/README.md`.
- The beacon request shapes (`ver=2&c=WEB_REMIX&cpn&st&et&cmt&rt&final`, `s.youtube.com` →
  `music.youtube.com` host swap, WEB_REMIX headers + SAPISIDHASH via the shared `ytClient`) are the
  replica-verified against live YouTube (every beacon HTTP 204).
- **Extra params match the official client, verified from live `base.js`, never guessed.** The web
  `Y2` param builder was read from the deployed `player_es6…/base.js` and only params whose KEY AND
  truthful VALUE are both derivable are sent: `fmt=<streamed itag>` (base.js `n.fmt=y.D.itag`; the
  real resolved itag, omitted on cached/local plays where it is unknown) and `muted`/`mos` (base.js
  `isMuted()?1:0`, `mos == muted`; our player has no mute separate from volume, so `player.volume<=0`
  IS muted). Params requiring a value we could only recall from memory (`volume` scale, `state`
  strings, `fs`/`playerheight`/`playerwidth`/`clipid`) are deliberately NOT sent - adding one later
  requires re-reading base.js for its exact value semantics, not guessing. `muted` is captured on the
  main thread at enqueue time (player access); `fmt` rides the resolver's `onTrackingResolved`.

### Cipher / player rotation (the most common future break)

The `cipher` submodule (package `com.zemer.cipher`, repo `ZemerTeam/zemer-cipher`) deciphers YouTube's `player_ias` signatures in an Android WebView and mints poTokens. It's wired **two ways**: a git submodule *and* a Gradle composite build - `includeBuild("cipher")` in `settings.gradle.kts` substitutes `com.zemer:cipher` → the local `:library`, so the app always builds the working tree.

YouTube rotates `player_ias` frequently. Player configs live in **one JSON file**: `cipher/library/src/main/assets/player_configs.json` - per player the sig call expression (e.g. `mP(4,155,INPUT)`), the n-transform URL class (e.g. `Yx`), the STS, and the md5-of-first-10000-bytes alias. That single file is (1) bundled in the APK as the offline default, (2) **fetched at runtime from raw zemer-cipher `master`** by `PlayerConfigStore` (6 h TTL + ETag, plus a forced refresh + one retry the moment an unknown hash breaks deciphering), and (3) read by the `tests/` harness - so **a config pushed to cipher `master` fixes deployed apps within minutes, no APK release**. Parsing/validation is `PlayerConfigParser` (strict regexes; the n-IIFE is built from a local template - remote data can never inject free-form JS into the WebView; invalid entries are skipped, invalid files - including any duplicate hash/alias key - are rejected wholesale and the previous table kept). The validation rules exist in TWO readers (the Kotlin parser and `tests/player-configs.mjs`); file-level accept/reject verdicts and the n-IIFE template are pinned byte-for-byte by shared fixtures in `cipher/library/src/test/resources/config-parity/` - a rule change must update both readers AND the fixtures, or one of the two test suites goes red. When adding a config:
- **Validate empirically**: `node tests/validate-player-config.mjs <hash>` deciphers a real stream and checks the CDN returns **HTTP 206**. That 206 is ground truth, not regex extraction - multiple constant pairs can decipher correctly, only the live response confirms which the server accepts. It prints a paste-ready JSON entry (and re-validates the committed entry first if one exists).
- Add the entry (with its MD5 alias) to `player_configs.json` only - there are no Kotlin/harness mirrors to sync anymore; unit tests in `cipher/library/src/test/` guard the file's shape. Then run `node tests/gen-player-dates.mjs` to refresh `player_dates.json` (a **separate, cosmetic** file mapping each hash to the commit date support was added, shown in the song-details sheet via `PlayerDatesStore`). It is deliberately decoupled: old apps never fetch it, and a malformed/missing dates file only blanks a UI label - deciphering is never affected.
- **Push to cipher `master` is the deploy**: that is the URL devices fetch. Bump the submodule pointer in `zemer-app` afterwards so bundled defaults stay fresh (push order: `zemer-cipher` first, then the pointer - reverse breaks fresh clones / CI).
- A cipher *scheme* change (new config shape, not just a new hash) still needs code + an APK; bump `schemaVersion` only on breaking shape changes - old apps reject newer schema files and keep their last-good table.
- `.github/workflows/player-monitor.yml` checks hourly: it fetches the live raw `master` file once (the submodule copy is only a warned-about fallback) and **multi-samples** the live player surfaces via `tests/scan-live-players.mjs` (30× `iframe_api` + `music.youtube.com`) so a low-rate A/B **canary** - served ~1/6 of the time, which a single sample misses ~83% of the time - is caught the first hour it appears, not once it has already rotated in. "Known" is still decided by the harness loader (`parsePlayerConfigs`, the app's validation rules) against real keys + md5 aliases, so a pushed-but-invalid entry counts as UNKNOWN and still alerts. Opens one issue per unknown hash + a summary email, but does **not** auto-commit - the config is added by hand.

### Accounts: personal vs anonymous (pooled) - `SAPISID` ≠ logged in

There are two signed-in states and telling them apart is non-obvious. A **personal** Google login sets a **`dataSyncId`**. The **"anonymous"** login signs into a **shared, pooled** account: its cookie **does** carry `SAPISID`, but the flow deliberately clears `dataSyncId` (`App.kt` / `LoginGateScreen` - `onBehalfOfUser`/dataSyncId breaks the pooled player request). So `parseCookieString(cookie).containsKey("SAPISID")` / the cookie-based `Context.isUserLoggedInFlow()` are **true for anonymous** and must **never** gate remote *account* reads or writes - doing so leaks the pooled account's library/likes/subscriptions across every anonymous user. (The old blocking `Context.isUserLoggedIn()`/`isSyncEnabled()` helpers - `runBlocking` around a DataStore read plus, in the login case, a blocking DNS socket - were dead and were deleted; use the reactive `*Flow` variants.)

- The correct discriminator is **`com.jtech.zemer.extensions.AccountState`**: `isPersonalAccountSignedIn` (= non-empty `YouTube.dataSyncId`, usable from context-free entity code) and the reactive `Context.isPersonalAccountFlow()`. Gate remote account sync/writes on these - never on `SAPISID`.
- Already gated: `SyncUtils` account syncs + `likeSong`, the entity `toggleLike` remote side-effects (`Song/Artist/Album/PlaylistEntity`), and the add/remove/create/rename/delete-playlist + library/history-feedback writes in the menus. **Local DB writes always run**, so anonymous keeps likes/subscribes/playlists locally; personal logins are unaffected (each gate is a no-op when the predicate is true). The **Firebase artist-whitelist sync (`syncArtistWhitelist`) is account-independent and stays on for anon** - it powers content filtering.
- **UI account display is gated too** (#137): the Settings → Account "Signed in as" card (name/email/handle/avatar) and the **"More content"** + **"Auto sync with account"** switches render only when `isPersonalAccountSignedIn` (`AccountSettings.kt`) - never the SAPISID-based `isLoggedIn`, which would show the *pooled* account's identity and account-personalization controls to every anonymous user. The Anonymous-login button is hidden once signed in, so there is a single Logout control, not a duplicate.
- **Synced playlists reconcile non-destructively and stay 100% whitelisted** (`SyncUtils.syncSavedPlaylists`/`syncPlaylist`, #130): keep a song only if a whitelisted artist is resolvable from the playlist renderer **or the local DB row** (`filterWhitelistedWithLocalArtists`). Do **not** restore the old `clearPlaylist()` + strict `filterWhitelisted` rebuild - it wiped user-added songs whose YTM renderer carried sparse/topic-channel artist ids while they stayed in YouTube Music. A failed/empty/partial remote read must never delete a playlist or its songs.
- Still personalized-to-the-pool for anon (NOT yet gated): Android-Auto browse still reads pooled-cookie InnerTube surfaces. (`YouTube.home()` no longer feeds the app's Home tab - the home tab is InnerTube-free for content, see §The home tab - so the old "anon Home shows the pooled account's mixes" leak is gone with it.) Note the *Library* "My top 50" is a **local** most-played auto-playlist (`mostPlayedSongs`), not a leak.

### Content filtering (whitelist, conditional id overrides, filtered covers)

The "Kosher" guarantee runs through one chokepoint - `utils/WhitelistFilter.kt` `filterWhitelisted`
(applied on every YouTube/browse/playback surface) over the artist whitelist it reads. Two layers on top
are non-obvious and regression-prone; full detail in `docs/whitelist/README.md`:

- **Conditional id overrides** (`blockedContentIds` Firestore collection → `utils/BlockedIdsCache.kt`,
  #161): a server-listed, read-only table of specific ids hidden *conditionally* by a **reason** - `female` hides only when `!allowFemaleSingers`, `global` (and any unknown reason) hides for everyone,
  all inert when filtering is off. The surgical complement to the artist whitelist: a *mixed* channel
  stays whitelisted while specific items from it are dropped by id. Applied centrally in
  `filterWhitelisted`, in `search/ZemerResultMapper.dropBlocked()`, **and** in the offline read layer
  (`offline/SubsetReadLayer.idDropped` + the shared `contentGatePasses` gate - see §Offline search
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
  `thumbnailUrl` - **never** `playlist.thumbnail`. Mirrors the local-playlist screens; don't revert
  either site.

### Zemer curated playlists (the Home "Zemer Playlists" shelf)

Hand-curated playlists served ready-to-render by the search server's `/zemer-playlists` endpoint;
full detail in `docs/zemer_playlists/README.md`. The rules that must not regress:

- **Ids are server slugs (`"acapella"`), never YouTube playlist ids** - they get their own screens
  (`zemer_playlist/{id}` detail, `zemer_playlists` see-all) and must never enter a YouTube-playlist
  code path (`online_playlist/…`, save-to-library, playlist menus).
- **All three content flags are sent explicitly on every request** (the server is default-OPEN;
  `zemerCuratedPlaylistsParameters()` is the unit-tested contract), and the repository deliberately
  does **not** cache - a plain re-fetch per screen-open is the endpoint's freshness contract and
  guarantees a response fetched under one flag set is never shown under another. No client
  re-filtering beyond the usual `dropBlocked` + `hideExplicit`.
- **Covers are server-generated SVGs at relative URLs** - resolved by `resolveZemerUrl()` and
  decoded by the `SvgDecoder` registered in `App.newImageLoader` (that's why `coil-svg` exists).
- Empty list = hidden section (normal state); detail 404 = back out + Home re-fetch. The Home shelf
  is backed by its own `ZemerCuratedPlaylistsViewModel` (LatestReleases isolation pattern) so a feed
  failure can never affect the rest of Home.
- The detail screen's **All/Albums/Songs chips reuse `LatestReleaseFilter`** and split on the
  server's `fromAlbum`/`albums` fields. The **chip row shows ONLY when the playlist has albums**
  (`curatedChipsVisible(albumCount)`, unit-tested): a direct-picks playlist has no albums, so All ==
  Songs and Albums is an empty dead end - those render the plain track list instead. `effectiveFilter`
  pins the filter to ALL whenever the chips are hidden. Rows, Play and Shuffle all read the same
  filtered list; rows never pass `albumIndex` (the shared row renders a number *instead of* artwork).
- App↔server field changes travel as request docs in `handoff-docs/`, never as direct
  edits to the zemer-search repo.

### Genres (the song-level genre layer: Home chips, catalog, detail, radio)

Song-level genre browsing served by the search server's `/genres` family; full detail in
`docs/genres/README.md`, server contract in `handoff-docs/zemer-app-genres.md`. Genre is
a property of the SONG (via its release), independent of the artist flags - never conflate the two.
The rules that must not regress:

- **Key off the SLUG (`"nigunim"`), render the `title`** - `id` is the stable contract, `title` is a
  display string the server changes freely. Routes carry the raw slug (`[\w-]`, URL-safe, no
  encoding; `search/ZemerRoutes.kt` - `zemerGenresRoute`/`zemerGenreRoute`/`zemerGenreSectionRoute`,
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
  radio). **No Artists shelf** on a genre page - an artist card opens a full, mostly-unrelated
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
  menorah/alef/sukkah; NOT `material-icons-extended`); the drifting weave (`GenreWeaveLayer`)
  rasterizes the vector motif ONCE into a tiny cached tile and per-frame only blits that tile across
  the grid (never re-rasterize the vector per frame - that caused catalog jank; the earlier cached
  `graphicsLayer` variant was replaced because evicted layers left cards blank, so the cheap-blit
  self-healing design is load-bearing); the detail header album-art mosaic
  (`ZemerResultMapper.headerCovers`) is the ONE color source, de-duped, min 3 unique covers
  (songs reuse album art), neutral `ColorPainter` fallback (never a transparent gap), sized by the
  mosaic-only `mosaicVariant` (isolated from the shared `thumbnailFor`). `HeaderFontFamily` (Heebo)
  is used ONLY on genre titles/Play/card titles, never app-wide.
- Home strip (`ui/screens/HomeGenresRow.kt`, own fail-soft `ZemerGenresViewModel`, isolated like
  Stations) sits under Quick Picks, hidden/restored via a Settings → Appearance switch
  (`ShowHomeGenresKey`). App↔server field changes travel as handoff docs, never as zemer-search edits.

### Shared UI components (componentized - import, don't re-roll)

A componentization pass extracted the app's repeated composables into `ui/component/`; reuse them
instead of hand-rolling: `BackNavigationIcon` / `BackTopAppBar` (top-bar back button), `MoreVertMenuButton`
(row 3-dot menu), `TopAppBarActionButton` (plain `TopAppBar` action icon - history/search/now-playing/
refresh), `AppBarTitle` (the shared bold screen title - `titleLarge`+Bold, single-line+ellipsis; put
EVERY screen-level `TopAppBar`/`BackTopAppBar` title through it so weights don't drift), `zemerTopAppBarColors()`
(the one top-bar container color - pure black under AMOLED / `surfaceContainer` otherwise, container ==
scrolled so bars never grey-out on scroll; baked into `BackTopAppBar`, and every hand-rolled screen
`TopAppBar` passes `colors = zemerTopAppBarColors()` - except the full-bleed login/onboarding bars and
ArtistScreen's over-header transparent state; the in-player fullscreen video overlay
(`PlayerVideoFullscreen`) has no `TopAppBar` at all, just an exit icon over the scrim),
`PlaylistPlayShuffleButtons` + `PlaylistHeaderShimmer` (playlist headers/skeletons),
`shimmer/BoxPlaceholder` (the base shimmer slab under `ButtonPlaceholder`/`GridItemPlaceholder`),
`SettingsCardGroup` (the settings grouped-card stack - every settings row run renders through it:
position-shaped per-row cards via the unit-tested `settingsCardCorners`, one geometry shared with
`Material3SettingsGroup`; screens whose column already pads pass `horizontalPadding = 0.dp`),
`ArtistBrowseComponents` (KidZone/whitelist browse header; also `ArtistSearchField` + `SearchHandoffPill` - the browse search pill and its tappable hand-off sibling, one shared geometry so the pair can't drift),
`IconCategoryCard` (the square category
tile - centered gold icon + bold title + count subtitle on one neutral `surfaceContainerHigh` box, with
the D-pad focus treatment; the Downloaded library's Music/Videos/Status tiles all render through it),
`GenreCardGrid` (one genre-catalog section - optional bold title over the two-column `GenreCard` grid
with the odd-card spacer; BOTH the music and podcast genre catalogs render through it, with the shared
`GenreCatalogTopSpacing`/`GenreSectionGap` constants owning the catalog spacing). The
**status viewers** share a family so the live (`StoryScreen`) and saved (`SavedStatusScreen`) viewers
can't drift: `StatusStoryTopOverlay` (segment bars + avatar/name/date), `ExpandableStatusCaption` (the
WhatsApp Read-more caption with clickable links + inline copy), `StatusCopyButton` (icon-only themed copy
circle), `StatusVideoSurface` (the full-bleed ZOOM `PlayerView`, controls/buffering disabled),
`StatusLoadingIndicator` (avatar + M3 progress ring loading state, spinner fallback), plus the
`ui/utils/cubeFace` modifier (the cube swipe transform). `VideoModePill` (the in-player Song/Video
toggle - a sliding-thumb segmented control overlaid on the art slot, see §Video mode) is the one
source for that control; a screen wanting the same audio/video choice imports it, never a hand-rolled
switch. The **onboarding flow** is fully componentized under `ui/component/`: `OnboardingStepHeader`
(centered title + supporting text), `OnboardingStepTitle` / `AppNameTitle` (neutral step title / brand
title, both `onSurface` - never the accent), the `OnboardingActionButton` / `OnboardingPrimaryButton` /
`OnboardingTextButton` button family (one M3 shape, no per-screen shape overrides), `OnboardingChoiceCard`
(radio-select), `OnboardingInfoCard` (title + description + optional leading icon + trailing control +
optional action button - the one shell behind the content-filter toggles, the permission cards and the
sign-in card), `OnboardingStatusPill` (the Done/Needed · Active/Optional chip), and `onboardingCardColors`
(the shared card fill: `secondaryContainer` when active/selected, `surfaceContainer` otherwise - a tone
below `OnboardingActionButton`'s `surfaceContainerHighest` so an in-card pill never blends into its card).
New screens use these; a hand-rolled duplicate is a review miss.

**Componentize on every touch (non-negotiable).** Whenever you touch anything in the app, first check
whether a shared component already covers it - if one exists, use it. If you find yourself writing (or
editing) a second near-copy of a widget that already appears elsewhere, STOP and extract it into
`ui/component/` (or reuse the existing one), then point every site at it in the same pass - never leave
two hand-rolled copies to drift. Extracting the shared piece is part of the change, not a follow-up: the
staff-engineer review bar rejects copy-pasted near-duplicates. When you add a new shared component, list
it in the paragraph above so the next contributor finds it.

**Shared non-visual helpers (de-dup logic too, not just composables).** The same "reuse, don't re-roll"
rule covers repeated *logic*. The current shared helpers - reach for these before hand-writing the pattern:
- **Id-bearing navigation:** `navigateToArtist(id)` / `navigateToAlbum(id)` (`ui/utils/AppNavigation.kt`)
  over `navController.navigate("artist/$id")`. A blank id builds `"artist/"`, matches no destination and
  **crashes** - the helper makes a blank id a no-op; the pure `artistRoute`/`albumRoute` builders are
  unit-tested (`AppNavigationTest`). Query-param routes keep their own builders (`ZemerRoutes.kt`).
  Ratcheted by `R16-navroute` (baseline 0).
- **The row 3-dot menu body:** `ytItemMenu(item, navController, coroutineScope, onDismiss, isVideo)`
  (`ui/menu/YouTubeItemMenu.kt`) returns the `@Composable ColumnScope.() -> Unit` for `menuState.show`,
  dispatching `SongItem`/`AlbumItem`/`ArtistItem`/`PlaylistItem` to the right `YouTube*Menu` - never
  re-write that `when` per screen.
- **The Zemer repository from a leaf composable/queue:** `context.zemerSearchRepository()`
  (`di/ZemerSearchRepositoryEntryPoint.kt`) over a hand-written `EntryPointAccessors.fromApplication(...)`.
  Ratcheted by `R17-entrypoint` (UI-scoped, baseline 0).
- **Sharing a URL/deep link:** `context.shareText(url)` (`extensions/ContextExt.kt`) over a hand-rolled
  `Intent(ACTION_SEND)` + `createChooser`. `Tracker.action(SHARE, …)` and `onDismiss()` stay at the call
  site. File/stream shares (log export, lyric image) keep their own builder. Ratcheted by `R19-share`
  (baseline 0; `component/Lyrics.kt`'s lyric-image `EXTRA_STREAM` share is excluded, not a text share).
- **Copying to the clipboard:** `context.copyToClipboard(label, text, confirmationRes = R.string.copied)`
  (`extensions/ContextExt.kt`) over a hand-rolled `ClipboardManager.setPrimaryClip(...)` - it also shows
  the confirmation toast (`link_copied` for link copies). `text` is a `CharSequence` so an
  `AnnotatedString` copies verbatim. Ratcheted by `R20-clipboard` (baseline 0).
- **Showing a toast:** `context.toast(resId | text, long = false)` (`extensions/ContextExt.kt`) over a
  hand-rolled `Toast.makeText(...).show()` - two overloads mirror the framework (string-resource id /
  `CharSequence`); `long = true` is `LENGTH_LONG`. Ratcheted by `R21-toast` (UI-scoped, baseline 0). Works
  from any `Context` (Activity, Service, Application, `this@MusicService`).
- **Focus visuals and initial D-pad focus:** every focus ring/border/fill conditions on
  `focusVisualsEnabled()` and every screen-open focus grab goes through
  `RequestInitialDpadFocus(requester, enabled, keys)` (both `ui/component/FocusBorder.kt`) - touch
  sessions see no rings and skip the grabs; the grab re-arms when the input mode flips to keys.
  Ratcheted by `R23-focusgate` and `R24-initialfocus` (baseline 0); functional focus (text fields,
  key-event moves, the cast volume-key seed) is never gated. Full rules: `docs/ui/standards.md` §11.
- **The "See all" gate:** `seeAllOnClick(count, action)` / `SEE_ALL_MIN_ITEMS` (`ui/utils/SeeAll.kt`,
  unit-tested `SeeAllTest`) hides a section header's see-all arrow below the shared min-items threshold.
  Gate on the **total the arrow opens, not a truncated preview count**: a full-list row passes its real
  size, but a PREVIEW row (artist-page local sections that open a full online search, search-summary
  sections that switch to the full filter, genre album/singles shelves that open the facet list) shows
  the arrow whenever a fuller view exists - gating those on the preview size wrongly hides a path to more.
- **Playing a single tapped episode:** `ListQueue.episode(item, playSource)` (`playback/queues/ListQueue.kt`)
 - the ONE way an episode tap builds its queue: a plain one-item ListQueue with the surface's declared
  source, never `ZemerRadioQueue.song` (an episode must not seed music radio around its videoId; two call
  sites drifted exactly that way once).
- **Channel deep links:** `channelDeepLinkRoute(channelId, artistWhitelisted, podcastWhitelisted)`
  (`ui/utils/AppNavigation.kt`, unit-tested) - a `channel/UC…` link opens the music artist page when
  artist-whitelisted, the podcast channel page when podcast-whitelisted, else silently no-ops. Share
  links build via `VideoLinkBuilder.channelLink` so the link-out format and this parser live together.
- **Onboarding step flow:** `OnboardingNavigation` (`ui/screens/onboarding/`, pure + unit-tested
  `OnboardingNavigationTest`) holds the skip-when-already-configured transitions
  (`afterWelcome`/`afterDensity`/`backFromContentFilters`/`backFromPermissions`) - lifted out of the flow
  composable so Back never lands on a step skipped as already-set. `rememberOnboardingConnectivity()` is
  the single shared internet-reachability poll the network-gated steps use (one socket-probe loop, not a
  per-screen copy).

**Never `runBlocking` on a UI path.** A composable/UI file that blocks the main thread ANRs. Collect the
value with a suspend function + `LaunchedEffect`/`rememberCoroutineScope`, or a `Flow` (`collectAsState`);
the DataStore sync accessors (`dataStore[Key]`, `dataStore.get(Key, default)`) are the documented
exception and must run OFF the main thread. Ratcheted by `R18-runblocking` (UI-scoped, baseline 0). The
legitimate blocking sites live outside `ui/` and are deliberate - ExoPlayer's `createDataSourceFactory`
(a Media3 contract that must return synchronously), the download thread, the DataStore primitives.

**Loading skeletons must match the real content that replaces them, and a per-tab skeleton must never
render on another tab.** The Home content-type tabs share one `LazyColumn`, so a skeleton placed at the
top level shows on every tab: the Home loading shimmer is shaped like the MUSIC home (title + card row)
and is driven entirely by music-VM state (`isLoading` + the `has*HomeContent` flags), so `shouldShowShimmer`
**must** stay gated on `homeTab == HomeContentTab.MUSIC` (kept on one line) - otherwise it paints a
music-shaped skeleton on Radio/Podcasts/Videos that never resolves. Ratcheted by `R22-home-shimmer`
(`scripts/ui-audit.sh`, a positive assertion, not a shrink-count).

Enforcement lives in `scripts/ui-audit.sh` (see the rule list at the top of that file) + `docs/ui/standards.md`;
when you add a new shared helper with a greppable anti-pattern, add a ratchet rule there in the same pass.

### The home tab (telemetry-ranked rows; zero-InnerTube for content)

`HomeViewModel` + `HomeScreen`. The home tab is **InnerTube-free for content** - every row is served
from the Zemer `/home-rows` endpoint, local Room, or the flipphoneguy Latest-Releases feed. The **only**
`YouTube.*` call left in `HomeViewModel` is the account-card identity lookup (`accountInfo()` for a
signed-in user's name/avatar); do not add others. Full detail in `docs/home_rows/README.md`.

**The content-type selector (Music / Radio / Podcasts / Video)** is driven by the pure, unit-tested
`visibleHomeTabs`/`effectiveHomeTab` (`ui/screens/HomeContentTab.kt`): Block Podcasts is the ONE filter
that removes a tab (a persisted PODCASTS selection falls back to MUSIC); VIDEO is ALWAYS shown, relabeled
"Video songs" for blocked-video users (a visibility gate is a regression). The selected tab is seeded
from ONE async DataStore snapshot reading the tab AND Block Podcasts together (null until it lands, one
frame of background) - never a main-thread `dataStore[key]` read in composition, never a flash of Music
or of a blocked Podcasts tab. R22 (the MUSIC-gated shimmer) rides this selector - see §Loading skeletons.
The **Videos tab's ranked rows** (`/video-home-rows` → Trending Videos / New Videos / Top Video Artists;
handoff `zemer-app-video-home-rows-request.md`) ride the isolated fail-soft `VideoHomeRowsViewModel`
(PodcastHomeRows pattern, see-all via `VideoHomeSeeAllStore`): an absent endpoint leaves the tab on its
`topVideos` lead row. Direct plays declare `PlaySource.HOME_VIDEO_TRENDING`/`HOME_VIDEO_NEW` and the rows
emit impressions on the matching `home:video-*` surfaces (append-only tracking contract:
`zemer-app-video-home-rows-tracking-request.md`); the artists row needs neither (its plays attribute
`artist:UC…` from the artist page). Blocked-video users get both video rows relabeled + audio-gated,
never hidden, like every video shelf.

**Project direction (a real, ongoing goal):** progressively **replace as much InnerTube as we can across
the app** with Zemer-served, whitelist-pure data. The home tab migrated first; since then **artist opens
(`/artist`), album opens (`/album`), ALL radio (`/radio` - see §Zemer Radio), every single-song tap
(seed-first song radio), and search-with-offline-fallback** have followed - each with its InnerTube path
deleted, not kept as fallback. When you touch any surface that reaches YouTube for *discovery* content
(explore feeds, charts, recommendations, related, browse shelves, search-adjacent rows), prefer a Zemer
endpoint - or a handoff request for one (`handoff-docs/`) - over deepening the InnerTube
dependency, and delete the InnerTube path once the Zemer source lands. **Streaming/playback itself still
needs InnerTube + the cipher** (see §The streaming pipeline - that's the irreducible core) and is out of
scope; this goal is about *content discovery*, where YouTube's global feeds carry almost no kosher
content anyway. (The YouTube search *engine* is REMOVED - Zemer is the app's only search engine; the
greenlight and evidence live in `handoff-docs/zemer-app-artist-album-innertube-swap.md`.)

**Remaining InnerTube candidates (the punch list to complete the migration)** - everything still
reaching YouTube for content, in rough priority order. Pick from here before inventing new scope:

- **Whole-screen discovery surfaces:** `ChartsScreen` and `NewReleaseScreen`
  (`FEmusic_new_releases`) remain; each wants a Zemer endpoint (or a handoff request) the way
  home-rows got one. (`MoodAndGenresScreen`, `YouTubeBrowseScreen`, `BrowseScreen` and their
  `YouTube.moodAndGenres`/`explore`/`ExplorePage` InnerTube paths were DELETED with the Genres
  feature - the Zemer catalog is the replacement moods/genres surface. The legacy `ArtistItemsScreen`
  + its ViewModel + the `artist/{id}/items` route were DELETED - the Zemer per-section see-all had
  already replaced it and nothing navigated to the old route anymore.)
- **Non-engine InnerTube *search* users** (survived the engine removal deliberately - each needs its
  own design, not a blind swap): `RecognitionResolver` (fingerprint match → `YouTube.search` →
  whitelist check; a corpus-side match would need server support), the Android Auto **voice search**
  (`MediaLibrarySessionCallback`), and the **add-to-playlist online search** dialog
  (`AddToPlaylistDialogOnline`).
- **Android Auto browse** still reads pooled-cookie InnerTube surfaces (see §Accounts) - the last
  place anon can meet pooled-account personalization.
- **Account-tied InnerTube** (`SyncUtils` library/likes sync, `accountInfo()` for the account card)
  is inherent to the personal-login feature, not discovery - out of this punch list unless the
  feature itself changes.

Rules that must not regress:

- **Featured Albums / Videos / Artists / Playlists come solely from `ZemerSearchRepository.homeRows()`**
  (`GET /home-rows`, mapped by `ZemerResultMapper.homeRows()` → `HomeRows`). Ranked by real
  distinct-device listening (albums/videos/artists) and YouTube view count (community playlists =
  Featured Playlists row), 30-day live window, whitelist-pure + content-filtered server-side. There is
  **no InnerTube scrape fallback** - an empty pool (only if search.zemer.io is unreachable) just hides
  the row. `loadHomeRows()` returning null hides all four featured rows; it never breaks Home.
- **The ranked content gate is female/israeli/blocked-ids ONLY - NOT the famous/american quality
  proxy** (`isAllowedRanked`, distinct from `isBlockedArtist`). Real listening reach supersedes the
  proxy that gates the (now-removed) scrape; applying famous/american here cut the rows to near-empty.
  Cards carry the artist channel id (`ZemerAlbum/Track.artistId`, `ZemerArtist.id`) so the one-per-artist
  `rotateByArtist` dedup and the female/israeli check work; without it both no-op.
- **Featured Videos stays visible when videos are blocked** - retitled "Featured video songs"
  (`R.string.featured_video_songs`) on both the Home row and its see-all page. Every row plays
  audio-first (see §Video mode), so hiding the shelf for blocked users would just hide music; the
  long-press menu is gated to audio-only (`isVideo = item.isVideo && !blockVideos`) on both surfaces
  so a blocked user never gets a video download/share affordance. The artist page's own Videos
  section and the search Videos chip follow the identical pattern - a video section/row is never
  hidden, only relabeled + audio-gated. Don't reintroduce a `!blockVideos` visibility gate here.
- **Zemer-sourced albums/playlists open via the server route** (`onlineAlbumRoute` / `onlinePlaylistRoute`,
  `?zemer=true`), gated on `featuredAlbumsAreZemer` / `featuredPlaylistsAreZemer`, so the opened screen
  is whitelist-scoped and immune to on-device InnerTube bot-gating. The Home shuffle button is **"Radio
  mode"**: `HomeViewModel.shuffleRadioQueue()` → `ZemerRadioQueue(kind = "shuffle", seed = null)`, a
  whole-catalog, whitelist-pure Zemer station (the old lucky-item InnerTube radio and its
  `radioEndpoint != null` pool are GONE - don't reintroduce a per-item radio-endpoint filter here).
- **A brand-new user's empty Quick Picks seeds from Zemer**, not YouTube: `seedQuickPicksFromZemer`
  pulls the `auto-top-50` curated playlist. Returning users seed from local history; the seed is a no-op
  when Quick Picks is non-empty and never breaks Home on failure.
- The **"Zemer Radio" row** (under Zemer Playlists) is the synchronized-broadcast stations shelf - see §Zemer Stations below and `docs/stations/README.md`; its now-playing cards tick every 60s
  while ON SCREEN only (`repeatOnLifecycle(RESUMED)`).
- **Easter egg:** five quick taps on the Home top-bar title (1.5s idle resets) play a fixed song via
  the deep-link path, whitelist-filtered (`ui/utils/HomeTitleEasterEgg.kt`, tap rule unit-tested).
  Deliberate and owner-requested - do not "clean it up".
- **Every content row has a "See all" arrow** → `home_see_all/{row}` (`HomeSeeAllRow`). The pages read a
  process-wide `HomeSeeAllStore` snapshot that `HomeViewModel` publishes each load (the FULL, un-rotated
  filtered pool), so See-all can never disagree with the row it opened from - no re-fetch, no re-filter.
  Featured grids are 2-column (long Hebrew+English titles truncate at 3). Latest Releases / Zemer
  Playlists keep their own see-all screens + ViewModels.
- **The mainstream Trending row is gone** - `YouTube.getChartsPage()` charts carry ~no whitelisted
  artists (it filtered to empty and never displayed); the `auto-trending` / `auto-top-50` Zemer playlists
  in the Zemer-Playlists shelf are the real trending/top surface. Don't reintroduce a charts-scraped row.
- The `/home-rows` contract and every design decision are recorded in
  `handoff-docs/zemer-app-home-rows-request.md` (the app↔server thread) and
  `home-rows-plan.md`. App↔server field changes travel there, never as edits to the zemer-search repo.

### Zemer Radio (`/radio` - every radio surface; SELECTION only)

All radio runs on the Zemer server's `/radio` endpoint (whitelist-pure, blocked-ids filtered
server-side + the client `dropBlocked` pass) via **`playback/queues/ZemerRadioQueue`** - artist /
album / song / playlist seeds and `kind=shuffle` (Home "Radio mode"). `YouTube.next()` is gone from
every radio path. **The audio stream is still InnerTube + the cipher** - this replaced selection
only. Rules that must not regress:

- **The continuation token is opaque** (it encodes seed + flags + position): the queue keeps no
  cursor state; `nextPage()` just echoes the last token back. Continuation pages are PURE fresh
  tracks - never re-apply the old YouTube-style `drop(1)`; `MusicService`'s auto-load dedupes
  against the ids already in the player (`continuationItemsToAppend`) instead.
- **Single-song taps are seed-first** (`ZemerRadioQueue.song()`): the tapped song is the
  `preloadItem` (plays instantly) AND heads the queue at index 0, with the `/radio?kind=song` fill
  deduped around it. Every converted tap site (Home, History, Charts, Stats, artist page, search,
  menus' Start radio, recognition history, latest releases) uses this factory - a bare
  `ZemerRadioQueue("song", …)` without the seed is a station, not a tap.
- **A failed fetch is never silent**: `playQueue()` surfaces it (toast + session error). With no
  preload it also restores the previous queue (only the pointer was swapped); with a preload the
  song keeps playing and the queue's `initialFailed` flag lets `nextPage()` retry the seed page on a
  later transition - the flag is set only AFTER the initial fetch completes, so a retry can never
  run concurrently with it and double-append the fill.
- `LocalAlbumRadio` plays the local album then continues on `/radio?kind=album`; its
  `firstTimeLoaded` flips only after a successful fetch (a transient failure must stay retryable).
- Both queues hold only the **application context** - `MusicService.currentQueue` retains the queue
  for the whole session, so a captured Activity is a leak.
- Tracking: radio fill reports as `radio` (`initialItemsAreContext = false`,
  `continuationIsContext = false`); the preloaded seed is the one user-chosen context item.

**Zemer Stations** (`playback/queues/StationQueue`, the "Zemer Radio" home row) are the OTHER radio
product: one shared, server-programmed wall-clock schedule per station - every listener hears the
same track at the same moment (contract: `handoff-docs/zemer-app-stations.md`). Rules
that must not regress: ALL drift funnels through the BIDIRECTIONAL `resyncStationPlayback` (seek
forward when behind, WAIT - pause until startMs - when ahead, full re-tune when nothing queued is
on-air; never a mid-track jump, never a backward seek into a played/unplayable slot), invoked from
boundaries, pause-resume, error skips and STATE_ENDED; **pause = stop, resume = rejoin live**; a
broadcast is NEVER persisted (`saveQueueToDisk` guard) and a queue MUTATION (Play next / Add to
queue) EXITS broadcast mode (`exitStationOnQueueMutation` - without it station state latches and
queue persistence dies for the process); the session player masks all skip/seek/repeat/shuffle
commands AND no-ops them against stale controllers, notifying command changes on every mask flip
(`CastAwarePlayer.maskTransportForStation`/`notifyStationMaskChanged`); every raw-player transport
surface (mini-player swipes, the full player's thumbnail swipe, queue-sheet taps, lyrics buttons,
the widget's onStartCommand skips, repeat/shuffle toggles, and the Start-radio affordances - player
menu row hidden, notification button disabled, `startRadioSeamlessly` chokepoint guard) is gated on
`isStationBroadcast`, and `PlayerConnection.seekTo/Next/
Previous` early-return during a broadcast; the station runway top-up ignores the Auto-load-more
preference and repeat is forced OFF at station start; station items map through
`ZemerResultMapper.toSongItem` (coverless slots get the derived artwork fallback); the row's
now-playing ticker is LIFECYCLE-scoped (`repeatOnLifecycle(RESUMED)` - nothing polls while
backgrounded); no content flags are sent (pools pre-filtered server-side; blocked-ids still run
client-side as the third layer); a failed slot is marked unplayable and produces no play event
(zero-play-time guard); every station play tags `PlaySource.station(id)`.

### Corpus-native artist/album opens (no InnerTube fallback)

Artist (`/artist`) and album (`/album`) screens load purely from the Zemer server
(`ZemerSearchRepository.artist/album` → `ZemerResultMapper.toArtistPage/toAlbumPage`), with the
offline snapshot as outage fallback - there is deliberately **no InnerTube fallback** (a non-corpus
item is non-whitelisted and shouldn't open). A 404 renders the neutral "not available" state. Two
non-obvious rules:

- **The stale-row delete is flag-aware** (`AlbumViewModel`, `staleAlbumGoneForEveryone`): the server
  404s an album that is merely FULLY BLOCKED under the user's content flags, so a 404 under
  restrictive flags is re-probed with open flags before the local `AlbumEntity` row is deleted - a
  flag-hidden album must never be destroyed by its own filter, and a failed probe keeps the row.
- **An opener-threaded playlistId equal to the browseId never wins** (`toAlbumPage`): cards fall
  their playlistId back to the browseId, and persisting that MPRE as `AlbumEntity.playlistId`
  dead-presses album radio and mis-ids share links; the server's real OLAK id (or the browseId
  fallback, whose only consumer is the disabled automix) is used instead.
- **Artist credits resolve by ID, and name resolution prefers a whitelisted row** (the stuck-skeleton
  fix, handoff `zemer-app-album-open-stuck-skeleton.md`): `/album`, `/artist` cards and `/search`
  album rows carry `artistId` (live 2026-08-11) and `toAlbumPage` threads it into the album + matching
  track credits, so Zemer inserts never hit the name lookup. For id-less credits (account-sync paths),
  `artistByName` deterministically prefers a whitelisted row over a generated local one - devices held
  BOTH under one name, and resolving to the generated row starved every whitelist-JOINed query (the
  infinite album skeleton, the doubled artist credit). `insert(AlbumPage)` deliberately does NOT
  early-return on an existing row: every caller reaches it only when the whitelist-visible album read
  was null (absent OR poisoned), so it recreates the artist maps - the self-heal for poisoned rows.
  Diagnostic "AlbumOpen" Timber breadcrumbs stay in (client + ViewModel + post-insert map dump) for
  the in-app Log viewer workflow that confirmed this.

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
- **The source filter is server-driven, SERVER-ONLY** (`statuses/StatusSourcesConfig.kt`, contract
  `handoff-docs/zemer-status-sources-config-request.md`): which JewishStatus category UUIDs + YidStatus
  keywords count as "music" sync (version-gated) from `content.zemer.io/status-sources`, so they retune
  without an APK. Typed descriptors with **one handler per `type`** (`supabase-category` / `keyword-feed`);
  the two clients take `baseUrl`/`apiKey`/filter as params, but each `type`'s PROTOCOL details (the R2 CDN
  host, the YidStatus feed path + `Origin` header) stay baked into its handler, never in a descriptor.
  **There is NO baked-in fallback config** - the mirror is the single source of truth; the app persists the
  last-good config (reloaded at startup) and is simply hidden until the first successful sync (fail-soft,
  never wrong content). Fail-soft parse: null ONLY when a valid config can't be obtained (unreachable /
  `503` / non-JSON / no `providers` array) -> caller keeps its last-good (or stays hidden); a VALID config
  is honored as-is even when its usable set is empty (all `enabled:false` / unknown-type / empty-filter = an
  intentional dark, row hidden); unknown `type` / disabled / empty-filter providers are skipped non-fatally.
  Config synced by `StatusesRepository.syncStatusSources()` on the refresh path - AWAITED only for the
  first-ever sync (so a fresh install's first load runs with a real config), non-blocking after; throttled
  to one poll per `STALE_MS` window and QUIET on network failure (no `reportException` - matches the other
  mirror syncs); the installed version is endpoint-capped (`minOf(body, endpoint)`) so a stale CDN body
  can't suppress future syncs; family caches are version-stamped so a config change invalidates them
  immediately; per-provider fail-soft keeps a failed provider's previous creators while siblings refresh;
  persisted (`StatusSourcesConfigKey`/`StatusSourcesVersionKey`) + reloaded at startup, and
  `StatusSourcesCache.update()` never rolls back to an older version (restore/sync race safe). Adding a
  provider of an existing type is config-only; a new `type` is the one thing that needs an app change.
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
- **Pure timeline math is extracted and JVM-tested - keep it that way.** `StatusTimeline.kt`
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
- **Third-party platforms have no handoff doc** (they are not Zemer services): API shape, the
  OkHttp/Origin gotcha, and the feed cost caps are recorded in `docs/status/` instead. The ONE Zemer-side
  piece - the server-driven source config on the content mirror - IS covered by a handoff contract
  (`handoff-docs/zemer-status-sources-config-request.md`, see the server-driven bullet above).

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

### Podcasts (browse → show → episodes → play; a "Kosher" podcast client on top)

A full podcast feature ported from Metrolist onto Zemer main. **DISCOVERY is now the whitelist-pure Zemer
server** (`search.zemer.io`), exactly like artist/album/home-rows/radio - the app carries **no InnerTube
podcast-discovery path and no direct-first Firestore whitelist read**. The endpoints
(`handoff-docs/zemer-app-podcasts-request.md`, both sides SETTLED 2026-08-01) are `GET /podcast?id=MPSP…&offset=`
(show + episode page), `GET /podcast-channel?id=UC…` (host channel → an `ArtistPage`), `GET
/podcasts/new-episodes` (Library New Episodes), and podcast/episode groups folded into `/search`. The
**whitelist allow-set + version gate + browse-grid data (art + channelId)** all come from the **content
mirror** - the CHANNEL-level `content.zemer.io/podcastChannelsWhitelist` + `/podcastChannelsWhitelist/version`,
mirror-first with a Firestore `podcastChannelsWhitelist` fallback - exactly like the artist whitelist
(`ZemerContentClient` / `WhitelistFetcher`): each mirror doc carries `thumbnailUrl` + `channelId`, so the
grid renders straight from the mirror (the app does NOT call `zemer-search /podcasts`). All are wired in
`ZemerSearchClient`/`ZemerResultMapper`/`ZemerSearchRepository`, server-first with the offline-snapshot
fallback (`OfflineReadProvider` podcast reads; only `/podcast-home-rows` is live-only). The endpoints are
**LIVE on `search.zemer.io`** (deployed + device-verified 2026-08-11); `syncPodcastWhitelist` still
preserves the last-good table on any fetch failure (never unblocks). **Podcast genre catalog sections
are SERVER-OWNED** (`zemer-app-podcast-genre-kinds-request.md`, live 2026-08-11): `/podcast-genres`
carries a per-row `kind` slug plus an ordered `kinds: [{id,title}]` catalog, grouped by the pure
`podcastGenreSections` (unit-tested) and rendered through the shared `GenreCardGrid`. Unlike music's
fail-closed kind drop, an unknown/blank podcast kind falls to a trailing headerless section (everything
here is already whitelisted podcast content); no `kinds` (older server / offline snapshot) = flat grid.
Per-genre icons are an owner-reviewed set in `podcastGenreIcon` (bespoke motifs + Material Symbols +
two owner-supplied traced figures for tefilla/comedy).
**Two things stay InnerTube:** (1) **PLAYBACK NEVER MOVES** - an episode is a YouTube video with a real
`videoId`, played through the existing InnerTube + cipher pipeline exactly like a song/video (the
irreducible core); (2) **ACCOUNT SYNC** - subscriptions + episodes-for-later sync bidirectionally to the
YouTube account (`SyncUtils.syncPodcastSubscriptions`/`syncEpisodesForLater`, gated on
`isPersonalAccountSignedIn`), the music-library model, never the server. The rules that must not regress
(full detail in the handoff doc + `docs/` if generated):

- **An episode IS a `SongEntity` with `isEpisode = 1`.** Its "saved for later" state is `inLibrary != null`
  (NOT `liked`); it plays by `videoId`; `EpisodeItem.asSongItem()`/`toMediaMetadata()`/`toMediaItem()`
  carry `isEpisode = true` through the whole pipeline. Regular downloaded-songs queries EXCLUDE
  `isEpisode = 1` - downloaded episodes surface only in Library → Podcasts → Downloaded.
- **Show vs. host channel are different things** (the #1 gotcha): a **show** is `MPSP…` (a series of
  episodes, what the whitelist lists, opened via `OnlinePodcastScreen`/`online_podcast/{id}`); a **host
  channel** is `UC…` (publishes several shows, opened via `artist/{id}?isPodcastChannel=true`). The
  browse grid, Search, and the show screen operate on shows; "View channel" and the Library → Channels
  tab operate on host channels. `whitelistedPodcastRoute(podcastId, channelId)` (unit-tested) routes a
  browsed podcast to the channel when a channelId is known, else the show.
- **The host-channel page reuses `ArtistScreen`, loaded from the Zemer server** (`isPodcastChannel` nav
  arg → `ArtistViewModel` calls `zemerRepository.podcastChannel(id)` → `GET /podcast-channel`, mapped to
  an `ArtistPage`: the channel's shows become a "Podcasts" section (PodcastItem), latest episodes an
  "Episodes" section). NOT InnerTube `YouTube.artist` anymore (that path was deleted). A 404/null →
  the not-available state. Radio is HIDDEN for `isPodcastChannel` (no corpus radio for a host).
- **The podcast whitelist source is the content mirror, not Firestore** (`SyncUtils.syncPodcastWhitelist`
  → `WhitelistFetcher.fetchPodcastWhitelist`/`fetchPodcastVersion`, mirror-first from the channel-level
  `content.zemer.io/podcastChannelsWhitelist` with a Firestore `podcastChannelsWhitelist` fallback,
  exactly like the artist whitelist). Each
  mirror doc carries `thumbnailUrl` + `channelId` (`ContentPodcastDoc`), so the browse grid renders cover
  art + routes to the host channel straight from the allow-set - no per-row fetch. The old per-row
  `requestPodcastThumbnail` (InnerTube) fetch AND the interim `zemer-search /podcasts` art overlay are both
  **gone** (the mirror carries art now, matching the artist path). An empty/failed fetch never wipes the
  local table (never unblocks). **New Episodes** is the global `GET /podcasts/new-episodes` feed scoped
  CLIENT-SIDE to locally-subscribed shows (`PodcastLibrarySources.whitelistedNewEpisodes`), so it works
  for anonymous sessions too (no account gate - it is discovery, not account state). **Search** folds
  podcast/episode groups into `/search` (rendered by `YouTubeListItem`; a show row opens the
  channel/show via `whitelistedPodcastRoute`, an episode row plays by videoId). The episode-aware
  `filterWhitelisted` branch gates an `isEpisode` `SongItem` on the podcast whitelist (never the artist one).
- **Subscribe (channel) vs. save-to-library (show) are distinct.** Channel Subscribe writes a bookmarked
  `ArtistEntity` with `isPodcastChannel = 1` (→ the Channels tab via `bookmarkedPodcastChannels()`), and
  hits `YouTube.subscribeChannel` which **must send `params="EgIIAhgA"`** or the server no-ops it. The
  subscribe button reads the bare `artistEntity(id)` query (NOT `artist(id)`, which whitelist-INNER-JOINs
  and always returns null for a non-whitelisted host). Save-a-show writes a `PodcastEntity` bookmark.
- **Account-leak gate:** every podcast ACCOUNT read/write (new-episodes, library channels, save/subscribe,
  episode save-for-later) gates on `isPersonalAccountSignedIn` - NEVER SAPISID/`isUserLoggedIn`, and there
  is no `YouTube.isAnonLogin` (that flag was dead; deleted). Anon = local-only (pooled-account leak rule).
  Save/subscribe toggles are OPTIMISTIC (flip local first, revert + toast on server failure).
- **Episode resume** (podcasts are long): `MusicService` saves `song.lastPositionMs` from every exit path
  (periodic 15s - episode-gated, never a music wakeup; on-pause; on track-SWITCH via
  `previousEpisodeId`/`previousEpisodePosition`; on-destroy) and seeks on load. The pure decision is
  `EpisodeResume` (unit-tested): don't resume within `RESUME_EDGE_MS` of the start, and a FINISHED episode
  (within `COMPLETION_EDGE_MS` of the end) restarts from 0. **NEVER read `player.*` inside `database.query{}`**
  (background executor → "Player accessed on the wrong thread"); capture on the main/caller thread first.
  Episode rows show a **"N left"** hint (`episodeResumePositions()` Map query, gated by `EpisodeResume`);
  the song menu has a local **mark-played/unplayed** row (sets `lastPositionMs` to end/0).
- **EPISODE-ONLY player controls** (`Player.kt` `EpisodePlaybackControls`, shown only when
  `mediaMetadata.isEpisode`): a speed pill (1×→1.25×→1.5×→1.75×→2×) + ±30s skip (fast-rewind/fast-forward
  icons). MusicService **resets `playbackSpeed` to 1× on any non-episode** so episode speed never leaks
  into music.
- **Home "Continue Listening" row** (`HomeContinueListeningRow` + isolated fail-soft
  `ContinueListeningViewModel`, placed BELOW the Podcasts row): in-progress episodes, most-recent first
  via `continueListeningEpisodes()` - recency comes from the play `event` table, so **no new column**.
- **Episodes NEVER appear in the MUSIC discovery rows.** An episode is a `SongEntity` in the `event`
  table, so Keep-Listening / Quick-Picks fallbacks / Forgotten-favorites all filter `!isEpisode`
  (`HomeViewModel`); regular `downloadedSongs*` queries exclude `isEpisode` too. Episodes live ONLY on
  the podcast surfaces.
- **Library → Podcasts = three sub-filter tabs** (`PodcastFilter` EPISODES/CHANNELS/DOWNLOADED, own
  `PodcastSortTypeKey`/`PodcastSortDescendingKey` - must NOT reuse the Songs sort keys). New-Episodes /
  Episodes-for-Later are `AutoPlaylistCard`s (personal → `online_playlist/RDPN`,`/SE`; anon → local
  inline). The shared podcast data sources (whitelist filter + leak gate) live in ONE place,
  `utils/PodcastLibrarySources`, so the two podcast VMs can't drift.
- **Whitelist:** podcasts have their OWN channel-level whitelist (`podcastChannelsWhitelist` → `PodcastWhitelistCache`),
  separate from the artist whitelist. `filterWhitelisted` gates `PodcastItem`/`EpisodeItem` against it too
  (respecting filters-off) as defense-in-depth. Play source/surface: episode plays tag
  `PlaySource.podcast(id)` / `TrackingSurface.podcast|channel` (append-only slugs).
- **Block Podcasts is a CATEGORY gate enforced like the female block** (the v37 leak fix, v38). One
  shared decision - `PodcastSyncLogic.podcastCategoryAllowed` - backs every enforcement layer, all
  keyed off the live `BlockPodcastsKey`/`ContentFilterState` so existing users are enforced the moment
  they update: (1) `filterWhitelisted`'s `podcastPasses` drops ALL podcast/episode items when blocked
  (whitelist membership is irrelevant for a blocked category); (2) BOTH nav surfaces hide the Podcasts
  entry - the drawer's `navigationItems` and the bottom bar filter off the same preference (the drawer
  previously read the static `Screens.MainScreens`, which was the leak); (3) every podcast nav
  destination (`podcasts` browse, `online_podcast`, `podcast_genres`, `podcast_genre`, and
  `artist`/`artist_section` with `isPodcastChannel=true`) carries a `podcastsBlockedRedirect` guard that
  bounces a restored back stack/deep link to Home; (4) PLAYBACK itself is gated in `MusicService` - `podcastsBlocked()` + `filterBlockedEpisodes`/`Status.filterBlockedPodcasts` (the `filterExplicit`
  pattern in `playback/queues/Queue.kt`) drop episodes at `playQueue` (preload + initial items, start
  index re-clamped via the unit-tested `clampStartIndex`), `playNext`, `addToQueue`, the automix
  restore and the auto-load-more append, so an episode can't play even from a persisted queue. Every
  layer is a strict no-op while the flag is off. Regression tests: `PodcastSyncLogicTest`
  (category-gate truth table) + `BlockedPodcastsQueueTest` (identity + index clamp).

### Offline search backup (`offline/` - the outage fallback)

A downloaded, incrementally-synced snapshot of the corpus serves `/search`, `/artist`, `/album`,
`/home-rows` and `/zemer-playlists` when `search.zemer.io` is unreachable - a faithful Kotlin port of
the zemer-search read layer returning the SAME wire models, so a fallback response is consumed
identically to a live one. Full detail in `docs/offline/README.md`. The invariants:

- **Server-first, always.** `serverOrOffline` falls back only on `isZemerServerUnreachable()` - `IOException` **or** `UnresolvedAddressException` (Ktor CIO signals no-network/DNS that way; it is
  NOT an IOException). A 404-null is returned as-is and never triggers the fallback; a non-network
  exception is never masked. Only SERVER responses enter the search LRU (a cached offline result
  would outlive the outage). `/playlist` and `/radio` are live-only (not in the snapshot).
- **Kosher defenses:** a 14-day staleness cap (`subsetSnapshotIsFresh`), the live Firestore-synced
  whitelist overlaid at corpus load (`SubsetCorpus.withLiveWhitelist` - de-whitelisted artists drop
  the moment the app's whitelist sync lands, `isFemale` comes from the live flag), and ONE shared
  content gate (`contentGatePasses`) + `idDropped` across every offline surface - never hand-inline
  the female/KidZone/video predicate per site.
- **Sync is staged and crash-safe:** shards are content-hash diffed, downloaded to `.staged` files,
  verified, promoted only when ALL verified, and the manifest commits last; `loadCorpus` re-verifies
  shard hashes at read time, and an unknown manifest `schema` generation is rejected wholesale
  (cipher precedent). Enabled = **daily auto-update on ANY connection** (no metered gate - a product
  decision), running on the syncer's OWN scope so leaving a screen never cancels a download.
- **Parity is the correctness bar:** the port is verified against captured live responses (id-set +
  order) and thumbnails deliberately match the server's `mqdefault` variant - do not "fix" them to
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
  the batch. It's a JSONL file under `filesDir` - deliberately NOT a Room table.
- **Identity is one random UUID** (`TrackingDeviceIdKey`) and nothing else - the server 400s
  non-canonical ids, so only `UUID.randomUUID()` output is ever sent. Never add account/device/
  location identifiers to an event.
- **Debug builds send `debug: true` in the batch envelope and the SERVER discards them** - the
  client path is identical in debug and release; never gate the tracker on `BuildConfig.DEBUG`.
- **`play` fires for EVERY listen, however short** (`MusicService.onPlaybackStatsReady`), one per
  listen when it ends; `source` comes from `Queue.playSource` + `Tracker.playSources`
  (context vs radio-fill vs other) - new queue types/surfaces must declare their source, and radio
  continuation must keep registering as `radio`. `ZemerRadioQueue` hardcodes the answer (fill =
  `radio`, the preloaded seed = the queue's declared source; the menus' Start radio declares
  `PlaySource.RADIO`); since every single-song tap now runs a seed-first radio queue, the `radio`
  share of `play` events shifted UP by design - a dashboard reader should expect it.
- **`action` hooks live at chokepoints** (entity `toggleLike()`s, `DownloadUtil` download entry
  with `fromUser=false` for machine enqueues, `DatabaseDao.addSongToPlaylist`, share buttons) - don't add per-surface duplicates, and keep machine-initiated work out of the user-intent signal.
- **`impression` counts what was SHOWN, and OVER-counting silently penalises a song** - the server's
  exposure dampener docks a song for being widely shown, so the definition is deliberately strict:
  inside the viewport AND settled there ~300ms, deduped per `(surface, videoId)`. Never count a
  composed-but-offscreen row (Compose composes ahead of the viewport), never count a row a fling
  passed through, and a row nested in a lazy parent must check the PARENT's viewport too - its own
  says nothing about whether it is on screen. When in doubt, do not report.
- **Impressions are the only event type that may be DROPPED rather than queued** - they outnumber
  plays by an order of magnitude and share the one 500-event drop-oldest queue, so they are
  discarded while the upload backoff window is open and past half the queue cap. Both drops are
  song-independent, which is what makes them free; the per-POST row cap exists because the server's
  truncation would NOT be.
- **Surface slugs are the server's coverage-gate vocabulary** (`TrackingSurface`) - renaming one
  reads as a surface disappearing and re-closes the gate. Treat them as append-only, and send the
  tracking maintainer an updated declared list whenever a release instruments a new surface.
- One `search` event per executed query (the per-query ViewModel guard) - never per keystroke or
  per chip switch. Everything is tracked (KidZone included), no opt-out - a product decision,
  2026-07-05. Each event carries `provider` (a Zemer extension per
  `handoff-docs/zemer-tracking-search-provider-request.md`); the server contract accepts
  `"zemer"`/`"youtube"` and stores anything else NULL, and since the YouTube engine's removal the
  app is single-engine so the value is the pinned constant `SEARCH_TRACKED_PROVIDER = "zemer"`
  (unit-tested) - keep sending the field, the dashboard splits on it.
- The one-shot **history backfill** (`PlayHistoryBackfill`) uploads the local listen history as
  `play_backfill` events through `Tracker.uploadBackfill` - NEVER through the live queue (its 500
  cap must not be flooded) but sharing the single-in-flight + backoff discipline; row-ID cursor
  (loss-free resume), a persisted max-id bound so live-tracked rows never double-upload, device-zone
  timestamp conversion (wall-clock-as-UTC drops east-of-UTC users' freshest history), permanently
  off once its done-flag is set, paced under the server's per-device batch limit.
- The one-shot **library-action backfill** (`LibraryActionBackfill`) uploads the currently-liked /
  currently-downloaded song **snapshot** as `action_backfill` events (`favorite`|`download` only)
  through the same `Tracker.uploadBackfill` path and pacing. Differences from plays that must not
  regress: **10-year** acceptance window, not 3 (an old `likedDate` on a still-liked song is a
  long-standing favorite - don't "fix" the constant back); resume is by persisted **acked-line
  count**, not a row cursor - snapshot timestamps are NOT stable across attempts (zone changes
  shift every `t`; `SyncUtils.likedSongs` rewrites `likedDate` to sync time), so server dedup
  cannot absorb a full replay and the prefix skip is what bounds it; favorites upload before
  downloads (stable order the prefix skip depends on); pacing sleeps only BETWEEN batches (the
  done-flag lands immediately after the last ack); downloads include machine enqueues (snapshot
  can't see `fromUser`) so the server weights them as weak corroboration. Same device-zone
  timestamp conversion; 90 s start delay (load spreading, NOT an ordering guarantee). Full
  contract: `handoff-docs/zemer-tracking-action-backfill-request.md` (SETTLED).

### The player background system (one effective style, one extractor)

The full player (`ui/player/Player.kt`) and the mini player (`ui/player/MiniPlayer.kt`) share a
single source of truth in **`ui/player/PlayerBackground.kt`** - never re-derive any of this per
surface (the two drifting out of sync is exactly what bit a past change):

- **`PlayerBackgroundStyle.effective()`** downgrades **BLUR → DEFAULT below Android 12**. The blur is
  a `RenderEffect`, a no-op before API 31, so a raw BLUR there renders the bright artwork under the
  light-on-dark transport - illegible. Every *render* decision (background, text/icon colors, status
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
  for ≥3 swatches, else a single-hue fade to black) - both surfaces call it so the gradient shape
  can never drift between them.
- **Light (white) content only when a dark backdrop is actually painted.** A blur layer needs a
  `thumbnailUrl`; a gradient layer needs non-empty `gradientColors`. Until then the surface stays on
  the solid `surfaceContainer` with theme-colored text - flipping to white before the backdrop
  exists puts white text over the light Home screen showing through the (transparent) mini bar.
- Status-bar legibility is a `DisposableEffect` in `Player.kt` keyed on **(background, theme,
  `state.isExpanded`)**: it forces light icons only while the sheet is **expanded** (the dark
  background actually covers the screen); collapsed/dragging follows the theme. It hands the bar back
  to the theme-correct appearance - matching `MainActivity.setSystemBarAppearance`
  (`isAppearanceLightStatusBars = !isDark`) - on dispose, never a stale captured snapshot.
- The new-design transport cluster caps the labelled play button via `BoxWithConstraints` (to the
  width left after the two skip buttons + gaps) so it shrinks to fit narrow widths instead of
  overflowing; `TransportSkipButton` cancels its long-press repeat the moment the press is released.

This UI is **Material 3 *standard*** (`MaterialTheme`, not `MaterialExpressiveTheme`): Expressive-only
APIs (e.g. `LinearWavyProgressIndicator`) need a newer material3 and are deliberately not used. New
transport buttons reuse `TransportSkipButton` + the accent focus border; new D-pad rows reuse
`Modifier.focusBorder()`. `scripts/ui-audit.sh` ratchets raw `Modifier.blur(` in `ui/` (R12) - route
player blur through the effective style.

### Video mode (the in-player Song/Video toggle; audio-first everywhere)

A video-classified song ("video-song") plays as **ordinary audio by default on every surface** - search, home, artist, genre, library, downloads. Watching it is a per-play, in-player opt-in: an icon
pill (`VideoModePill`) on the art slot swaps the current queue item's rendition between audio and
video without changing the queue, the track, or the tracking identity. There is **no standalone video
screen or nav route** - the old `VideoPlayerScreen` / `video/{videoId}` route / `ArtistItemsScreen`
were deleted with this redesign; a fullscreen video is `PlayerVideoFullscreen`, an in-player overlay
(I6), not a destination. The numbered invariants below (I1 - I8, D3/D4/D5/D7/D8) are cited by the same
labels in the source comments (`VideoModeController.kt`, `VideoModeLogic.kt`, `PlayerVideoUiLogic.kt`)
 - there is no separate design doc, the code comments ARE the spec.

**Classification (know it, don't guess).** `SongItem.isVideo` is the ONE flag, set exactly once at the
mapper boundary - `ZemerResultMapper.songItems(..., isVideo = true)` for every Zemer videos-category
row (artist Videos section, home-rows videos, genre videos, search Videos chip/section) and by
InnerTube's own `musicVideoType` for the (mostly dormant) non-engine search users. Every UI surface - the `Icon.Video()` badge (`ui/component/Items.kt`), menu `isVideo` gating, the search `clickKind`
telemetry, the artist/genre section relabel - reads this ONE flag; never re-derive it per screen (a
title-sniff like `section.title.contains("video")` is a compensation smell that means the mapper
missed a spot). `SongItem.isVideo` deliberately does **not** flow into `MediaMetadata`/playback - a
video-song downloads, persists, and plays exactly like a plain song; only `VideoModeController` cares
that it's video-capable.

**Availability - `VideoModeLogic.availability()` is the single source of truth** (pure, JVM-tested;
`VideoModeController` never re-derives its conditions). Hard gates, checked FIRST and unconditionally:
casting, `BlockVideosKey`, and a Zemer Station broadcast - any one returns null (no toggle) regardless
of what rendition would otherwise exist, including an already-downloaded LOCAL file. Renditions, in
order: **LOCAL** (a downloaded muxed file - no source swap, works offline), **SELF** (a KNOWN non-ATV
`musicVideoType` - never guessed on unknown), **COUNTERPART** (an audio song with a known video
counterpart id - the `next()` counterpart source is dormant in production, plumbing only). A fourth,
corpus-sourced path grants SELF **instantly** while YouTube's own type is still unknown:
`VideoSongIds` (a small process-wide LRU, marked at the `SongItem.toMediaMetadata()` boundary) lets
the pill be already-decided the moment a corpus-discovered video-song is tapped, instead of waiting on
a network round-trip - but a later-LEARNED `musicVideoType` (including ATV) always overrides it.
`blockVideosNow` (the controller's cached mirror of the live preference, read off the async DataStore
collector) is seeded **unknown** (`null`), not `false` - a synchronous `recomputeNow()` during a
**restored** queue (a persisted item's `SongEntity.isVideo` and local-file resolution are both
independent of the async collector) can otherwise run before the real value lands; unknown reads
fail-safe as **blocked**. `videoModeAvailable` is published TWO ways: an async `combine` over every
input that can change out-of-band, and a **synchronous republish** (`recomputeNow()`, called from
`MusicService.onEvents` in the same stack that updates `currentMediaMetadata`) - the pill's visibility
must change atomically with the current item, or it visibly flashes in mid-way through the
player-open animation.

**The swap mechanics (`VideoModeController`, media3-literal).** Entering video mode replaces the
current `MediaItem` with a `video:<id>`-keyed rendition (`VideoRendition`, `buildUpon()` - same
mediaId, different URI/cache key) and seeks to the captured position; exiting reverses it,
position-continuous. `pendingSwap` + `videoModeItemId` classify the OWN swap's own
`onMediaItemTransition` so it never fires the queue's real-transition side effects (cast reload,
auto-load-more, save-queue) or double-counts the listen - `ListenAccumulator` suppresses a
swap-caused `PlaybackStats` end and emits once, accumulated, at the real end (I4). A **repeat-one loop
of the SAME video item** classifies the SAME way (`isRepeatOfSameItem`, from media3's
`MEDIA_ITEM_TRANSITION_REASON_REPEAT`) - treating it as a real track change reverted video to audio,
un-seeked, on every single loop, which is exactly the opposite of what repeat-one promises; per-loop
tracking still fires correctly since `ListenAccumulator` isn't touched by this classification.
**`exitVideoModeSameItem` verifies the current index actually holds the video-mode item before
touching it** (mirrors `revertDepartedItem`'s existing by-identity check) - media3 MASKS
`seekToNext`/`Previous` synchronously, updating `currentMediaItemIndex` before the matching transition
callback dispatches, so an unrelated exit trigger (block flag, cast, error) landing in that gap could
otherwise clobber the user's freshly-selected track with the departed audio item.

**A player error during video mode hands off, never crashes into silence:**
`VideoModeController.onPlayerError` reverts to audio for a STREAMING rendition failure (SELF/
COUNTERPART) - must run before `MusicService`'s own 403-refresh path, which would otherwise invalidate
the wrong (audio) cache entry. A **LOCAL** rendition error is NOT handled here (`onPlayerError` returns
`false`) - LOCAL never swapped the source, so the failure is the downloaded file itself, and the
service's normal error pipeline (self-repair, network wait, auto-skip) is what must run; swallowing it
here would strand the player in `ERROR` with nothing to re-prepare it. Separately, a **STREAMING** item
(not yet downloaded, no video mode involved) whose file exists hands playback over to it on ANY player
error - the mid-play-download-then-offline case (the sticky-source rule keeps a mid-play download
streaming until the item restarts; going offline afterward would otherwise stall on the network wait
with a good file on disk). Async (`scope.launch` + `withContext(IO)`), never `runBlocking` - a
`Player.Listener` callback dispatches on the main thread by default.

**Cache correctness (two corruption classes, both fixed, both load-bearing).** (1) The data-source
chain reads a downloaded local file THROUGH `playerCache`, keyed by the same mediaId as the item's
STREAM - and `CacheDataSource` serves cached spans regardless of the resolved URI. Historically
harmless (a download used the streaming itag, byte-identical spans); Option A muxed video downloads
(and itag drift) broke that identity, so a downloaded item whose id had streamed spans mixed two
containers in one extractor pass (negative-offset arraycopy, "No valid varint length mask found",
black video). **Every position-0 open that picks the local file purges the id's `playerCache`
resource first** (`removeResource`), and `DownloadUtil.removeDownload` purges it again on delete
(bare id + the `video:` namespace) - a file play must read ONLY file bytes, always. (2) A download's
resolved stream URL is a `forDownload` format (muxed for videos, generally a different itag than
what's streaming) and downloads never read the shared playback URL cache themselves - writing it there
(even into an apparently-empty slot) let a mid-play download poison a later seek's stream source with
a foreign container. **Downloads must never write `DownloadUtil.sharedUrlCache`.**

**Downloads - Option A: a video-capable item downloads its MUXED video, not audio-only.**
`VideoModeController.currentItemIsVideo` (musicVideoType OR the corpus flag) drives the player-menu
download decision, gated `!blockVideos` (a blocked user's download stays plain audio - `songRow`
otherwise HIDES the row entirely for a video item, leaving no download at all). The muxed file plays
as ordinary audio in the music queue (`Mp4Extractor` added to `createMediaSourceFactory` for the
plain-moov container the fragmented/mkv extractors can't parse) and the shared song row stays
addable to music playlists; the Song/Video toggle then works fully offline via LOCAL, and one
"Remove" truthfully covers both renditions. Every video download (streamed or muxed) shares ONE
metered-aware bitrate cap with the streaming swap (`VideoRendition.defaultMaxBitrateKbps`) - 1500 kbps
metered / 6000 unmetered by default, overridable per-download via `requestedVideoBitrate` (which must
survive a failed attempt, see §download system) - a video fetch must never silently pull the largest
available file on a metered connection. See §The download system for `VideoDownloadsInMusicKey` (the
library-wide "show video downloads as music too" preference this enables).

**The quality ladder (beyond-720p; `VideoQualityLogic` + the in-player switcher).** Streaming video
is no longer capped at the progressive muxed formats (which top out at 360p/720p): the quality ladder
spans progressive PLUS the adaptive video-only formats - measured live (`tests/video-qualities.mjs`),
the music client serves avc1 144p…1080p and vp9-only 1440p/2160p for uploads that have them. Full
feature map: `docs/video_quality/README.md`. The rules that must not regress:
- **`VideoQualityLogic` is the ONE ladder/selection authority** (pure, JVM-tested): one rung per
  qualityLabel (progressive wins its label; then avc1 > vp9 > av01, then bitrate), targets resolve to
  the best rung at-or-below ("1080p" on a 720p-max video → 720p, never null for an explicit pick),
  AUTO = the pre-switcher automatic progressive pick (the metered bitrate cap governs ONLY that).
- **The rung's itag lives IN the cache key** (`video:<id>:p<itag>` progressive / `:q<itag>` adaptive,
  `VideoRendition`): two rungs' bytes can never share cache spans (the container-mixing corruption
  class) - which is also why the exact-itag resolution deliberately has NO fallback format (a
  different container under an itag key would reintroduce the corruption; total failure surfaces
  through the video error path instead). The **two keys that CAN drift are guarded**: the plain
  `video:<id>` automatic key (its itag flips 18/22 with the metered cap) and the `videoaudio:<id>`
  merge-audio key (the audio pick flips with metered state) each track their last-resolved itag
  (`videoKeyItagCache`/`mergeAudioItagCache`) and purge their cached spans on any change - seeded
  through the shared `seedPlainVideoKey` so the resolver AND the prefetch path both run the guard.
  Every `preferVideo=true` resolution (video branch, live merge branch, prefetch) resolves the merge
  audio at **HIGH** so its itag agrees across all three and the purge never thrashes. The `:q` mark
  routes an item through the `MergingMediaSource` in `createMediaSourceFactory` - an adaptive rung is
  video-only, so playback merges it with the audio stream under `videoaudio:<id>` (never the bare
  id's spans). `removeDownload` purges the WHOLE key family (`VideoRendition.allRenditionKeys`, one
  runCatching PER key). Only WEB-client resolutions seed the rung-URL table (`videoRungUrls`) - a
  non-web fallback's URLs 403 past the 1 MiB wall, so a non-web success leaves the table empty and a
  switch re-resolves; the ladder-URL block is skipped entirely for `forDownload` (never consumed).
  A video error invalidates the plain key AND the rung key AND the merge-audio key (all seeded from
  the same now-dead response), so a re-entry always re-resolves fresh.
- **The switcher** (`VideoQualitySelector`, shared by the inline art slot at BottomStart and the
  fullscreen overlay at TopEnd - a VideoModePill-family over-media chip, never a floating dropdown)
  shows the CURRENT item's live ladder, decoder-capability-filtered. The picker BODY
  (`VideoQualityMenu` - `NavigationTitle` heading over shared `OnboardingChoiceCard` rows) is one
  composable presented two ways: the INLINE player opens it as the root bottom-sheet menu
  (`LocalMenuState`, portrait), the FULLSCREEN overlay renders it in a fullscreen-LOCAL centered
  scrollable panel (`onOpen`) - the root bottom sheet is a portrait sheet that fought the immersive
  landscape window's orientation/insets/z-order, so fullscreen must present its own panel inside the
  overlay (Back closes the panel before exiting fullscreen). It
  (`VideoDecoderCaps` - never offer vp9 2160p to a SoC that can't decode it); a pick applies to that
  item for the session. **An explicit quality the user chose - in Settings OR the in-player switcher
 - is HONORED on every connection, metered included.** It is a deliberate choice, not something to
  silently override: no metered gate, no bandwidth pre-gate, no error-time AUTO pin discards it (a
  video error just invalidates the stale URL so a re-entry re-resolves fresh at the chosen quality;
  the decoder-caps filter already keeps undecodable rungs off the menu). Data/stutter protection
  lives where it belongs: the AUTOMATIC pick (AUTO - the default-default) keeps its metered bitrate
  cap, and the reactive rebuffer guard drops the CURRENT video a rung when it actually stalls
  (per-item; a new video still starts at the user's setting). Two CONCURRENT maps: `qualityOverrides`
  is the effective session state (an in-player pick OR the guard's reactive downgrade) driving
  streaming; `userQualityPicks` holds ONLY explicit switcher choices and is what DOWNLOADS read
  (`downloadVideoQuality` → `userQualityPicks[id] ?: defaultVideoQuality`), so the guard's downgrade
  never leaks into a download. The Settings default (`VideoQualityKey`, Player settings, hidden when
  videos are blocked) applies to new plays via `effectiveQualityTarget` (`qualityOverrides ?:
  default`). A same-itag switcher tap (tapping the quality already playing, including the automatic
  pick's true label) is a no-op - never a redundant re-swap. First entry plays AUTO instantly and upgrades
  position-continuously when the ladder lands with the resolution - never a blocking wait, and never a
  redundant re-swap when the automatic pick already streams the target rung (the resolved itag rides
  the ladder callback). Quality re-swaps ride the same `pendingSwap` + `listenAccumulator.onSwap`
  discipline as enter/exit (a swap is never a track change, never a double-counted listen). LOCAL
  renditions and RELAY mode have no switcher (one baked/fixed rendition; quality keys must never reach
  the relay resolver). A video-mode player error pins the item's session pick to AUTO so neither the
  failed pick NOR the persisted default can loop a re-entry back into the failing rung.
- **Fast entry + instant switching (the perf contract).** ONE video resolution resolves EVERY ladder
  rung's URL plus the merge-audio partner from the same response (`PlaybackData.videoRungUrls` /
  `mergeAudioUrl` - pure local sig/n/pot computation, no extra network; exactly what
  `tests/video-qualities.mjs` proves per rung) and `MusicService.seedVideoUrlCaches` seeds them all,
  so a quality switch and the adaptive audio track never pay a second player round-trip. The expanded
  player PREFETCHES the rendition in the background while the Song/Video pill is showing
  (`prefetchVideoRendition` - deduped, expiry-aware, relay-gated, silent on failure), so a Video tap
  starts with a single CDN range request. The web-URL finalization lives in ONE helper
  (`applyWebUrlTransforms`) shared by the main resolution path and the rung table - never fork it.
- **The rebuffer guard (never keep stalling mid-play; fast AND slow connections).** A STATE_BUFFERING
  after READY on a STREAMING rendition is a mid-play stall; TWO stalls within 45s trigger a downgrade
  (`VideoQualityLogic.shouldDowngradeForRebuffer`, pure + tested). Two is deliberate - a single
  transient blip (a routine momentary hiccup) must NOT permanently drop the user's chosen quality;
  only a repeated pattern means the rung genuinely can't sustain. The drop is exactly ONE
  rung down (`rungBelow`), so playback settles on the HIGHEST rung that actually plays (2160p → 1440p
  → 1080p → 720p, stopping the moment 720p is stable). It deliberately does NOT bandwidth-gate a
  multi-rung jump: a rung's `bitrate` is its PEAK and media3's estimate is depressed right after a
  stall, so a bandwidth jump over-dropped (2160p → 480p when 720p was fine). Each step is
  position-continuous and pins the item there so the ladder callback can't bounce back up. Seek-caused buffering is exempt via a **timestamp grace window**
  (`onSeekDiscontinuity` stamps the time; a stall within `SEEK_GRACE_MS` is ignored) - NOT a boolean
  flag, because a seek into an already-buffered region fires no state change to clear a flag and a
  stale flag would swallow the next real stall. A swap's own prepare never counts (history resets on
  every swap), LOCAL/audio playback never counts, and AUTO never downgrades (already the cheapest
  single-stream pick). The shared LoadControl is left at media3's rebuffer default (2s) - video
  stutter is handled by the quality downgrade, NOT by widening the shared buffer, which would regress
  audio/RELAY rebuffer latency. The buffering spinner lives INSIDE the shared `PlayerVideoSurface`
  so the inline art slot and the fullscreen overlay show one identical treatment.
- **Prefetch is skipped where it can't help** (`prefetchVideoRendition`): RELAY (fixed rendition), a
  downloaded LOCAL video (plays from disk - a resolution would be pure waste, and offline it just
  fails), or when offline. The relay guard is also enforced at the swap chokepoint itself
  (`swapToVideoKey`) so a late pre-relay-toggle prefetch callback can never install a `:q` key in a
  relay session.
- **Download remux failures are classified** (`VideoMuxer.Result`): a TRANSIENT I/O failure (disk
  full) preserves the requested quality so a retry re-attempts the same rung (never silently
  downgrade what the user asked to save); only a DETERMINISTIC format-incompatibility clears the
  request and falls back to the automatic progressive pick.
- **Downloads above the progressive ceiling fetch video+audio separately and REMUX on-device**
  (`VideoMuxer` - framework MediaExtractor/MediaMuxer, zero new dependencies: avc1+AAC → MP4,
  vp9+Opus → WebM on API 29+ only - `selectRung(opusWebmMuxSupported)`; av01 is stream-only). The
  download target is decoder-capability-gated (a rung the device can't decode must never become a
  committed LOCAL file that errors on every play) but is NOT metered-capped - an explicit download
  quality is honored as chosen; only the automatic (AUTO) pick keeps the metered bitrate cap. The
  audio partner is resolved from the SAME video
  response and client (`PlaybackData.downloadAudioUrl` - container-matched: mp4/avc video → AAC,
  webm/vp9 video → Opus; NO second `/player` round-trip and no client-disagreement mux failure, with
  a defensive second resolution only when the response carried no usable audio), and each stream is
  verified against its declared contentLength before muxing (a truncated track fails the attempt with
  the quality preserved). The requested quality label (`requestedVideoQuality`) follows the
  `requestedVideoBitrate` lifecycle rules (survives failed attempts, cleared on
  success/cancel/delete) - EXCEPT a DETERMINISTIC mux-incompatibility (`VideoMuxer.Result.INCOMPATIBLE`)
  which clears it so the retry falls back to the automatic progressive pick; a TRANSIENT mux failure
  (disk I/O) preserves the quality for the retry. The player-menu download passes the user's EXPLICIT
  pick (`downloadVideoQuality` reads `userQualityPicks`, never a machine downgrade) so the saved file
  is the quality the user chose.

**UI-only rules (`PlayerVideoUiLogic`, pure, JVM-tested - the inline surface and the fullscreen
overlay must never disagree about which one is live).** Opening the lyrics sheet reverts video mode
to audio (position-continuous) - the sheet covers the inline video slot, leaving it decoding
invisibly behind the sheet; closing lyrics does NOT auto-restore video (it's a per-play opt-in the
user re-toggles). Fullscreen force-exits the instant video mode ends (track advance/skip/error revert)
or the player sheet collapses - a fullscreen video must never survive past the content it was showing.
Backgrounding the app (`MainActivity.onStop`) reverts video mode to audio too - the same "don't decode
invisibly" reasoning, orientation changes never pass here (`configChanges` handles them, no Activity
restart).

**The blocked-user guarantee, end to end (verified, not assumed).** There is exactly ONE code path
that can ever set video mode true - `VideoModeController.enterVideoMode`, reachable only through
`setVideoMode(true)`, which unconditionally re-checks `computeAvailability()` first. Every actual
video-rendering surface in the app (`PlayerVideoSurface`, composed only inside `Thumbnail`'s inline
slot and `PlayerVideoFullscreen`) requires `isVideoMode == true` with no `||` bypass. So a blocked
user's video-songs are rows - relabeled ("Video songs"), badged, played as audio - never a moving
pixel: no code path exists to enter video mode while blocked, casting, or during a station broadcast.
When touching this system, don't reintroduce a `!blockVideos` *visibility* gate on a video row/section
anywhere (the current design is "always shown, relabeled + audio-gated, never hidden" - see the Home
tab's Featured Videos rule) - hiding was the pre-redesign behavior and is a regression, not a fix.

### The theme system (palette picker, cohesive Material You, pure-black)

The app's colors are one cohesive materialKolor scheme generated from a **single seed accent**, chosen
in **Settings → Appearance → Theme** (`ui/screens/settings/ThemeScreen.kt`, ported from Metrolist). Rules
that must not regress:

- **One seed, no neutralized surfaces.** `ZemerTheme` (`ui/theme/Theme.kt`) seeds the WHOLE scheme
  (surfaces, containers, accents) from the selected color so the app is tonally cohesive - the
  system-wallpaper "Material You" look. Do **not** re-introduce surface neutralization (copying
  `darkColorScheme()` surfaces over the seed) - that greys the surfaces while the accent stays
  saturated, so bars can't pick up the theme and containers clash. The only override is the **brand
  accent** pinning its exact dark primary family (`BrandPrimaryDark`/`…Container` in `ThemePalettes.kt`,
  the design hexes `FFAFB7`/`60383E` materialKolor's tones drift from).
- **The picker is a single control.** `SelectedThemeColorKey` (int ARGB) holds the accent;
  `DynamicThemeKey` is the album-art toggle. Pure selection logic is `ThemePaletteSelection`
  (JVM-tested). Palette entries (`PaletteColors` in `ui/theme/ThemePalettes.kt`, R8-hex-exempt because
  it's `ui/theme/`): **Dynamic** (album-art, `Color.Transparent` sentinel), **System** (wallpaper,
  `SystemWallpaperThemeColor` sentinel - shown only on Android 12+ via `visiblePaletteColors`), **Zemer**
  (brand), then the accent spectrum. **Default = `DefaultAccentColor` = the Zemer brand palette on every
  device** (System/wallpaper stays in the picker, just not the default). The `SystemWallpaperThemeColor`
  sentinel routes `ZemerTheme` to the platform `dynamicDark/LightColorScheme` (the ONLY wallpaper path).
- **Pure-black ("BLACK" mode) is all-black via the scheme, not hardcoded.** `ColorScheme.pureBlack(true)`
  drives every surface/surfaceContainer/surfaceVariant token to `Color.Black`, so dark mode stays tinted
  grey and BLACK mode is truly black - a clear difference. Consequently **no `if (pureBlack) Color.Black
  else <token>` conditionals** anywhere: bars, drawer, dialogs, snackbar, mini-player and search just use
  the plain surface token and let the scheme black it. Don't reintroduce per-site pure-black hardcodes.
- **Top bars are neutral chrome.** `zemerTopAppBarColors()` (`ui/component/BackTopAppBar.kt`) = plain
  `surfaceContainer` container + neutral title/icons; the accent lives in content, never the app-bar
  chrome. Every screen bar (including MainActivity's Home bar) routes through it - one source, no
  per-screen drift.
- **Activities outside MainActivity use `ZemerAppTheme`** (`ui/theme/Theme.kt`), which resolves the
  user's saved palette + dark mode + pure-black, so popups (the recognition dialog) follow the chosen
  theme instead of the brand default. `ZemerTheme` with default params = brand pink and is only correct
  inside MainActivity (which drives `themeColor` itself, incl. album-art). The home-screen **widget**
  (`widget/MusicWidget.kt`) is RemoteViews and can't read the Compose theme - it uses the static
  `@color/widget_accent` (brand).

### The download system (ONE unified path - never fork it)

Downloads go **exclusively** through `MediaStoreDownloadManager` (file saved to MediaStore, durable
truth is `SongEntity.isDownloaded` + `mediaStoreUri`; live progress in its in-memory `downloadStates`).
The legacy ExoPlayer download map (`DownloadUtil.downloads` / `getDownload()`) is **dead** for
status - nothing the UI reads should touch it.

Every download/progress affordance reads ONE path; do not re-implement per surface:
- **State (pure, tested):** `playback/DownloadStateResolver.kt` - `forSong`/`aggregateSongs`/
  `aggregateByIds` combine persisted `isDownloaded` **OR** live MediaStore state (so a download
  survives a process restart - reading the live map *alone* is the bug that makes downloads "vanish"
  after relaunch). `songProgress`/`aggregateProgress[ByIds]` for the progress fraction.
- **UI:** `ui/component/DownloadStatusUi.kt` - `rememberSongDownloadStatus/Progress`,
  `SongDownloadBadge` (default song-row badge), `AggregateDownloadButton` (album/playlist header),
  `DownloadStatusIcon`.
- **Menu rows:** `ui/menu/DownloadMenuItems.kt` `downloadMenuItem(...)`, decided by
  `playback/DownloadMenuLogic.kt` (`songRow`/`collectionRow`, pure + tested). A download row **never
  dismisses the menu** (it animates Download → progress → Remove in place). Videos use the same path
  (`DOWNLOAD_VIDEO`, hidden when videos blocked). **Option A (§Video mode): a video-capable song
  downloads its MUXED video, not audio-only** - the player-menu download reads
  `VideoModeController.currentItemIsVideo` (gated `!blockVideos`) so the toggle works fully offline
  afterward; see §Video mode for the shared metered-bitrate cap and the playerCache-corruption
  invariants a download must respect.
- **A collection NEVER shows a FAILED/retry row** (`collectionRow` takes only the aggregate status - REMOVE / DOWNLOADING / DOWNLOAD). A failed member just leaves the aggregate NOT_DOWNLOADED, so the
  collection offers DOWNLOAD again, which re-enqueues only the not-yet-downloaded members (= retry)
  and stays removable once everything is on disk. A dedicated collection "retry" row is a **dead end**
 - it hid Download AND Remove and re-failed the dead track forever with no escape. Only *single*
  songs get a FAILED row (`songRow`). Don't reintroduce an `anyFailed` arg on `collectionRow`.
- **Downloading a collection** whose songs load async (online album/playlist/selection menus):
  resolve/fetch the songs **at click time** (fetch-if-empty) so the first tap downloads - a
  captured-empty list is the "press once does nothing, twice works" bug. **EVERY action in that menu
 - Download, Remove, *and* the aggregate status - must read the SAME resolved/fetched list, never the
  original (possibly-empty) `songs` prop.** A Remove that iterates the empty prop while Download
  iterates the fetched list silently removes nothing (was a real bug on the Home long-press playlist
  menu). For online items aggregate by videoId (`aggregateByIds` + a persisted-downloaded id set) so
  progress animates without Room entities, and on Download **persist each `MediaMetadata`
  (`database.insert`/`transaction { insert(...) }`) THEN download** - a bare `database.song(id).first()`
  returns null for a not-yet-persisted id and the tap silently no-ops.
- **Playback of a downloaded file** (`MusicService.createDataSourceFactory`): use the local file when
  it opens; if it's genuinely gone, **stream this play AND re-enqueue a download to self-repair** - never crash with ENOENT, and never silently delete the `isDownloaded` flag (that makes downloads
  vanish from the Downloaded playlist). Two non-obvious rules here: (1) the self-repair must **skip
  re-enqueueing a download whose live state is already FAILED this session** (check
  `downloadUtil.mediaStoreDownloadState(id)`) - the manager only no-ops for active/complete, not
  FAILED, so a permanently-unrecoverable source would otherwise fire a fresh full download on *every*
  play; (2) the file-open probe (`downloadedFileOpens`) returns false on **any** open failure
  (FileNotFound *or* SecurityException/other) so playback streams - handing ExoPlayer a URI we just
  failed to open only fails again.
- **`database.query {}` is fire-and-forget** (it posts to an executor, doesn't suspend). NEVER split a
  single logical mutation across two `query {}` blocks that touch the same row - they race and the
  wrong one can land last. The download-mark bug was exactly this: `markSongAsDownloaded` upserted the
  row twice (relations with `isDownloaded=false`, then `isDownloaded=true`), so a downloaded song
  intermittently persisted `isDownloaded=0` with no `mediaStoreUri` - the file saved but it "didn't
  download" / streamed / vanished. Do the whole mutation in one `database.transaction {}` whose final
  write is authoritative.
- **`markSongAsDownloaded` must NOT clobber user state.** It bases the persisted row on the **existing
  DB row** (read first) and overwrites only the download-owned columns (`isDownloaded`, `dateDownload`,
  `mediaStoreUri`, `isVideo`) - a full-row `@Upsert` of the caller's `Song` would silently reset
  `liked` / `inLibrary` / library tokens when the caller handed a stale/partial `Song` (e.g. an
  album-page entity, or the like-then-auto-download race). It also backfills `duration` AND
  `thumbnailUrl` only when the existing row lacks them.
- **Backfill `duration` AND `thumbnailUrl` from the playback response** in `performDownload`
  (`playbackData.videoDetails`) - songs reached via an album/playlist page often carry neither
  (showed "0:00" / no artwork in the Downloaded list).
- **A per-download video bitrate must survive a failed attempt.** `requestedVideoBitrate` is cleared on
  success / cancel / delete, **never** in the per-attempt `finally` - else `retryDownload` re-issues the
  download with no bitrate and silently falls back to best/default quality (a large file over a metered
  connection the user explicitly capped).
- **Remove must delete the actual file on EVERY backend.** A custom download path saves a SAF document
  uri; `ContentResolver.delete` silently no-ops on those, so `MediaStoreHelper.deleteFromMediaStore`
  routes document uris through `DocumentsContract.deleteDocument`.
- **Downloaded video-songs also appear in the downloaded MUSIC surfaces by default** - `VideoDownloadsInMusicKey` (default ON), since Option A means one muxed file serves both renditions
  (see §Video mode). `DatabaseDao.downloadedSongs*` take `includeVideos`; the phone library list, the
  Downloaded auto-playlist, the library mix, AND Android Auto's downloaded browse (`MediaLibrarySessionCallback`,
  `downloadedSongsWhitelistedByCreateDateAsc`) all read the SAME preference - don't let Auto and the
  phone disagree about which songs are in the list again. The opt-out switch lives on
  `DownloadedVideosScreen` ("Show in downloaded music"), which stays reachable even when videos are
  blocked (same reasoning as the Home Featured Videos shelf above - it never renders watchable video).

Enforcement (so this can't regress): `scripts/check-download-unification.sh` (whole-app, wired into
the UI-audit workflow) + `scripts/ui-audit.sh` rule **R13** fail CI on any `downloadUtil.downloads` /
`getDownload(` read, any `Download.STATE_*` outside the legacy infra (`DownloadUtil.kt` /
`ExoDownloadService.kt`), or any per-surface `Icon.Download(`. Full rules: `docs/ui/standards.md §12`.
When you touch downloads run both scripts and add pure regression tests next to the resolver/menu
logic (the manager/playback layer needs Robolectric, which the project does not have - say so rather
than skip silently).

### tests/ - the hard-data streaming harness

Node ≥20 scripts (deps vendored in `tests/node_modules`, no install needed) that reproduce the app's *exact* stream path (same `/player` request as `InnerTube.kt`, same cipher run in jsdom, same poTokens) against the live CDN - so playback is measured, not guessed. Needs `innertube_cookie.txt` at the repo root (a dumped logged-in session; **gitignored**, never commit).

- Run one: `node tests/cipher.mjs` (live player health), `node tests/validate-player-config.mjs <hash>`, `node tests/web-remix-stream.mjs`. Pin a player with `PLAYER_HASH=<hash>`.
- `tests/README.md` + `tests/INVESTIGATION.md` are the methodology and the symptom-indexed runbook - read them first when streaming breaks.
- The harness mirrors app constants on purpose; when `YouTubeClient.kt` / `PoTokenGenerator.kt` change, update the matching mirror (`clients.mjs` / `potoken.mjs`). Player configs are **not** mirrored - `tests/player-configs.mjs` reads the same `player_configs.json` the app bundles (requires the cipher submodule checked out; if missing, scripts fail with an actionable message).
- Loader unit tests (no cookie or network needed): `node --test tests/player-configs.test.mjs` - validation rules, collision rejection, the `config-covers.mjs` CLI, and the cross-language parity fixtures shared with the cipher repo's Kotlin tests.
- **`tests/search/`** is the same idea for the *search* path: faithful Node ports of the app's four search functions (`searchSuggestions`/`searchSummary`/`search(filter)`×6/`searchContinuation`) run against live YouTube Music - `node tests/search/run.mjs [query...]`. It reproduces the app's exact request (WEB_REMIX, `setLogin=false` → visitorData only, no cookie/auth) and reports any error: a strict-deserialization break (a non-null field YouTube dropped → whole response fails → "No results"), a parser drop (with the exact field), the `searchContinuation` NPE, or an empty result. `node --test tests/search/self-test.mjs` proves the checker catches breaks (no network). The kotlinx strict-field table in `tests/search/schema.mjs` is transcribed from the innertube models - keep it in sync when their nullability changes. Zemer's artist-whitelist filter runs *after* these functions (needs the app DB) and is the next suspect when they're healthy but search still looks empty. See `tests/search/README.md`.

### Modules & app layout

- **`:app`** (`com.jtech.zemer`) - single-activity Jetpack Compose UI, Hilt DI (`App.kt` `@HiltAndroidApp`, modules under `di/`), Media3. `MainActivity` + `NavigationBuilder.kt` host the Compose nav graph; `MusicService` (a Media3 `MediaLibraryService`) owns ExoPlayer and is bridged to the UI by `PlayerConnection`, with `playback/queues/` implementations. State is Room (`db/MusicDatabase.kt`, `song.db`) + DataStore preferences (`utils/DataStore.kt` - holds the auth cookie / visitorData / dataSyncId and all settings). Content-filtering (whitelist, KidZone) lives in `sync/` + `utils/SyncUtils.kt`. The offline search-backup snapshot (sync engine + read-layer port) lives in `offline/` (on-disk store under `filesDir/subset/` - see §Offline search backup). Downloads via Media3 `ExoDownloadService` plus a MediaStore path. Crash/error telemetry is Firebase Crashlytics: `utils/CrashReportingTree.kt` (planted in `App.kt`) turns every Timber log (DEBUG+) into a breadcrumb and `reportException()` calls into non-fatal issues - so report errors via `reportException()`/`Timber`, never `printStackTrace`; release CI uploads R8 mappings and native symbols automatically.
- **`:innertube`** (`com.metrolist.innertube`) - the YouTube Music InnerTube API client (Ktor): request building, auth context, page parsers that turn YouTube renderer trees into typed models. Holds the `YouTubeClient` definitions and the NewPipe bridge for signatureTimestamp.
- **`:lrclib`** / **`:simpmusic`** (`com.metrolist.*`) - lyrics provider clients (LrcLib.net and api-lyrics.simpmusic.org).
- **`cipher`** - see "Cipher / player rotation" above.

## Documentation

`docs/` is a **code-derived docset** - most of it is generated, not hand-written:

- `docs/generate.py` regenerates `docs/repository-map.md`, `docs/build-release.md`, and `docs/reference/*.md` from tracked source (file inventory; Gradle / CI / native / JVM-module facts). It is idempotent - converges in one run - and needs PyYAML (`pip install pyyaml`) for `build-release.md`. **Never hand-edit those generated files**; change the source or the generator.
- `.github/workflows/docs-regenerate.yml` runs the generator on every push to `main` and commits any change back (`[skip ci]`), so the generated docs stay current automatically. Running `python3 docs/generate.py` locally before a commit is still good practice.
- Hand-authored docs are the exception - this `AGENTS.md`, `docs/ui/standards.md` (the UI rulebook), and prose/rationale carry intent a generator can't derive.

## Verifying your changes

- **Build both** `:app:assembleDebug` and `:app:assembleRelease` (release catches R8/shrink breakage).
- **Streaming / cipher / poToken changes** must be proven with the `tests/` harness against the live CDN (HTTP 206 / whole-song drain), and ideally confirmed on-device via the `YTPlayerUtils` logcat (`Playback: client=…, itag=…`).
- **UI changes** must comply with `docs/ui/standards.md` (the UI rulebook - Material 3 standard, design tokens, shared `Dialog.kt` dialogs, shared grouped-list components `Material3SettingsGroup`/`Material3MenuItem` per section 11) and stay 100% D-pad navigable - any new row/list component must carry the `.focusable()` + focus-border treatment, since upstream (Metrolist) rows omit it. Update the doc when a rule changes. Run `bash scripts/ui-audit.sh` - it ratchets sections 5, 7, 8 and 11 (no *new* hardcoded user-facing strings, raw `AlertDialog`s, raw font sizes, hardcoded hex colors, or raw `ListItem(` action rows under `ui/menu/`; strings and dialogs are baselined at zero, menus build from `Material3MenuGroup`).
