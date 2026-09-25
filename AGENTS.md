# Working with Zemer as an AI agent

Zemer is a "Kosher" YouTube Music client for Android (Kotlin, Jetpack Compose, Material 3), forked from [Metrolist](https://github.com/MetrolistGroup/Metrolist) with content-filtering layered on top (artist whitelist, KidZone, per-artist flags like `isFemale`/`isChasid`). The shared library modules keep the **`com.metrolist.*`** package namespace while the app is **`com.jtech.zemer`** - that split is intentional, don't "fix" it.

## Project rules

1. Pull the latest `main` before starting, to minimize merge conflicts.
2. Commit messages follow `type(scope): short description` (e.g. `fix(player): skip HEAD validation for WEB_REMIX`, `feat(ui): add history button`); the scope is optional.
3. User-facing strings live in two default-English files, `app/src/main/res/values/strings.xml` and `app/src/main/res/values/metrolist_strings.xml` - **both are editable** (the old Metrolist-fork "never touch `strings.xml`" rule is retired). Do **not** edit the translated files under `values-iw/` - other locales are managed separately.
4. Database schema changes (`app/.../db/MusicDatabase.kt` + entities) require a versioned Room migration and are high-risk - confirm with a human before changing the schema.
5. Don't rename the `com.metrolist.*` library namespace, and don't bump the app version - version bumps are a release-team decision.
6. Follow Kotlin/Android best practices; prioritize performance, battery, and maintainability.

## Working agreement

- **Do not commit, push, or merge unless explicitly asked in the current request.** When you are authorized, doing so is fine and the responsibility lies with the requester. Never rewrite git history, force-push (except rebasing your own branch), or delete branches without explicit instruction.
- **Never commit secrets** - `innertube_cookie.txt`, cookies / poTokens, `release.keystore`, `google-services.json` are gitignored; keep them that way.
- Edit README / docs only when that is the task, not as a side effect.
- Ask a human when requirements are unclear; don't assume. Add comments only for complex or non-obvious logic.

## Engineering rules (non-negotiable)

- **Regression tests are required** for every behavioral change or bug fix wherever a test does not demand heavy new infrastructure (plain JVM unit tests over pure logic or fakes, or the `tests/` streaming harness for stream/cipher/poToken work; the project has no Robolectric). "It builds" and "I watched it work once" are not regression protection. If a fix genuinely cannot be tested without heavy new infrastructure, say so explicitly in the change description instead of skipping silently.
- **Keep code modular.** No new god files: split by responsibility (screen scaffolding vs. business logic vs. data access). New logic goes behind small, single-purpose functions/classes - not appended to `MainActivity.kt`, `MusicService.kt`, or other existing giants; shrink them when touching them (onboarding steps live as per-step files under `ui/screens/onboarding/` - keep it that way).
- **Keep it professional.** Code must pass the bar of an external staff-engineer review: layering respected (UI does not run database/network calls inline), errors handled rather than swallowed, user-facing strings localized, no copy-pasted near-duplicates, no dead code left behind.

## Build & run

- **JDK 21**, `compileSdk`/`targetSdk` 36, `minSdk` 26. Ships `arm64-v8a` + `armeabi-v7a` ABIs (for bundled dependency native libs only - the app has no NDK build or C/C++ of its own). No product flavors.
- `./gradlew :app:assembleDebug` - debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:assembleRelease` - release APK. **Build BOTH after any change**: release runs R8 (`isMinifyEnabled = true`) and catches shrink/keep-rule breakage that debug never will.
- Submodules are required: `git submodule update --init --recursive` (`cipher/`). Download metadata embedding is pure Kotlin (`utils/mp4/Mp4MetadataWriter`, `utils/ogg/OggOpusTagger`, framework-based `utils/mp4/AudioRemux`); audio downloads may keep Opus (itag 251, saved as tagged `.ogg` on API 29+) - see §The download system.
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Stream resolution logs under logcat tag `YTPlayerUtils` (also `PoTokenWebView`, `Zemer_CipherFnExtract`).
- CI: `.github/workflows/release-build.yml` builds a signed release on push to `main` / PRs to `main` (skipped for changes only under its `paths-ignore` - docs, `tests/`, unit tests, scripts, `.github/`, `*.md`, …); keystore + `google-services.json` come from base64 secrets.

## Architecture & the danger zones

### The streaming pipeline (the core; where things break)

`app/.../utils/YTPlayerUtils.kt` `playerResponseForPlayback()` is the heart of the app. It:
1. Tries `WEB_REMIX` (main client), then `STREAM_FALLBACK_CLIENTS` - exactly `VISIONOS` (1.02) → `VISIONOS_0_1` (the previous config as second chance) → `WEB_CREATOR` → `TVHTML5_SIMPLY` (governed by the "TVHTML5" toggle), each family enable-able in the Stream Sources setting, whose displayed order the `ALL_FALLBACK_CLIENTS` array must keep matching. Every other client was removed after whole-song drains (`tests/client-fulldownload.mjs`) proved it dead (ANDROID_VR family, MOBILE/ANDROID, WEB as a stream fallback - its def stays for InnerTube next/transcript, IOS/IPADOS, ANDROID_CREATOR, TVHTML5_SIMPLY_EMBEDDED_PLAYER, 7.x TVHTML5, MWEB); retired configs + verdicts live in `tests/clients-retired.mjs` (MWEB: `tests/MWEB-INVESTIGATION.md`). Don't re-add one without a passing whole-song drain.
2. For web clients, deciphers the `signatureCipher` (sig + n-transform) via the **`cipher` submodule**, then appends a BotGuard `pot=` token.
3. Validates, then hands the URL to ExoPlayer in `MusicService`.

Two hard-won facts that govern this area - always verify against the live CDN via `tests/`, never reason from convention:
- **googlevideo serves the first 1 MiB of a stream free, then 403s every new connection** unless the URL's `&pot=` is bound to the **videoId** (not visitorData). Clients whose attestation the web poToken can't satisfy (the reason IOS/IPADOS/MWEB were removed) 403 past the wall under every binding.
- **`validateStatus` does a HEAD that false-negatives** (403 on URLs that GET fine), so WEB_REMIX streaming intentionally skips it.

### RELAY playback mode (`playback/relay/` - the filtered-device bypass)

An **opt-in, login-less** mode for devices whose kosher filter blocks `music.youtube.com` / `googlevideo.com`: discovery already rides `*.zemer.io`, so RELAY moves **only the media source** onto the whitelisted relay host `stream.zemer.io`. Contract + app↔server thread: the handoff doc `zemer-app-filtered-playback-relay-request.md`. Rules that must not regress:

- **One flag, default off, DIRECT is untouched.** `PlaybackModeKey` → `constants/PlaybackMode` (`DIRECT`/`RELAY`), default `DIRECT`. Every relay branch is gated on it; a normal login runs zero relay code. **Never let a relay change touch the DIRECT path.**
- **The seam is `MusicService`.** `createDataSourceFactory()` (DIRECT) is unchanged; the per-open `playbackDataSourceFactory` (a `RoutingDataSource`) picks the separate, **cache-free** `RelayDataSourceFactory` only when the flag is on. `relayModeNow` mirrors the flag but is seeded **null** and resolved with a one-time synchronous DataStore read on the first open, so a relay user's cold-start play is never mis-routed to DIRECT. Both factories share **`resolveDownloadedFileUri`**: a downloaded song plays from disk, decided once at position 0, with self-repair + `recoverSong` + video-mode nudge - so relay never switches source mid-track.
- **`playback/relay/` holds the pure/isolated pieces:** `RelayStream` (URL builder - `/stream?v=` audio, `&kind=video` 360p mp4, `/download?v=`), `RelayDownload` (container sniff → extension + HTTP-status classification, unit-tested), `RelayDeviceId`, `RelayDataSourceFactory`. Keep logic here, not in the giants.
- **Onboarding + gating.** The login gate's third option **"I have a filter"** is login-less (sets `RELAY`, no cookie). `MainActivity`'s gate must NOT bounce a relay session (it derives login + relay from one DataStore snapshot via `produceState`). The redirect decision is the pure, tested `ui/screens/LoginGateRedirect`: a **null route means `NavHost` has not set the graph yet** and the effect must NOT navigate (Navigation throws "Navigation graph has not been set" - a warm start / recreate delivers the snapshot before the graph exists, which crash-looped launch); the effect is keyed on the route so it re-runs on the first real one. A **normal login globally resets `RELAY`→`DIRECT`** in `App.kt` (from ANY entry point), and the **Settings toggle + nav-drawer Account entry are hidden when not login-less**. These gates key off the cookie's `SAPISID` (true for anon too), the standard "has a session" idiom.
- **Downloads** pull `/download` (m4a → embeds cover art like a normal download; an Opus/webm fallback is saved as `.opus` since MediaStore.Audio rejects `.webm`), verify completeness against `Content-Length`, and play offline from the local file. **Video** reuses the normal `VideoModeLogic` path pointing at `&kind=video`; an audio-only id 404s → revert to audio.
- **The song-details sheet stays YouTube-free AND source-opaque in relay** (`ui/utils/ShowMediaInfo.kt`): the InnerTube `getMediaInfo()` call is **not requested** when `relayMode` (not merely hidden on failure - an unfiltered relay session would otherwise show YouTube stats), and the **Information** section renders only when `informationItems.isNotEmpty()` (empty in relay - no local `FormatEntity`). The sheet shows only General (title/artists/media-id) and never a "playback source"/relay row.
- **Relay device counting (two mutually-exclusive headers by build type)** on every RELAY media request (`/stream`, incl. `&kind=video`, and `/download`), as default request properties of the relay OkHttp factory / the `/download` request, so they never reach googlevideo or DIRECT:
  - **Release:** `x-zemer-device: <RelayDeviceIdKey>` (`RelayDeviceId.HEADER`) - a per-install random UUID **separate** from the telemetry `TrackingDeviceIdKey`; the relay pairs it with the filter-egress IP to count devices per content filter, and it is never joined to telemetry. It is the only per-device relay signal (zemer-stats stores no IP).
  - **Debug:** `x-zemer-debug: 1` (`RelayStream.DEBUG_HEADER`) - served but not counted; `RelayDeviceId.get*` return null in debug so no id is sent.
- **Errors** surface the contracted copy (404 "not available", 502/503 "try again"); the relay egress pool is server-side, so a transient 502 is retried.
- **The relay playback OkHttpClient keeps generous timeouts (connect 30s / read 60s, matching downloads), never OkHttp's default 10s** - cache-free relay streams over a slow rotating-proxy egress, and the default threw `SocketTimeoutException` mid-stream (the "keeps stopping" bug). `onPlayerError` reads `AutoSkipNextOnErrorKey` from a synchronous `@Volatile` mirror, never a main-thread blocking DataStore read, so a relay error burst can't ANR.
- **Streaming is still the danger zone:** prove changes with `tests/`; app↔relay contract changes travel as handoff-doc edits, never as guesses.

### SABR playback (`playback/sabr/` - the experimental UMP transport; opt-in, OFF by default)

The alternative to progressive URLs for clients YouTube moved to SABR/UMP (`serverAbrStreamingUrl`: POST a `VideoPlaybackAbrRequest`, parse a UMP response, repeat). A Kotlin port of `tests/sabr-stream.mjs`, **isolated exactly like RELAY**: every branch is gated on `StreamSabrKey`; with it off DIRECT is byte-for-byte unchanged. **Full detail (protocol, field numbers, engine, integration, video, downloads, harness): `docs/sabr/README.md`** - read it before touching this package. Rules that must not regress:

- **Pure, JVM-tested engine** (`SabrProto`, `SabrUmp`, `SabrMessages`, `SabrSession`, `SabrSeekLogic`, `SabrProtection`, `SabrBuffer`, `SabrSpool`, the `SabrAudioStream`/`SabrStreamRegistry`/`SabrDataSource` trio in `SabrDataSource.kt`; tests `Sabr*Test`). The UMP varint is NOT the protobuf varint - `SabrMessages.mediaHeaderId` (the MEDIA part's header-id prefix) must use the UMP one (they agree only below 128).
- **Reassembly is by ABSOLUTE byte offset, never sequential append** (append corrupted the container → garbage duration → a `getBufferedPercentage` crash). `SabrBuffer` is DISK-backed (never a heap array), serves any covered region, refuses an out-of-range contentLength, and `writeAt` CLAMPS an overshooting final segment to contentLength instead of dropping it.
- **Honesty:** an INCOMPLETE drain marks the buffer ERRORED, never complete (serve every reassembled byte, then a real player error at the gap); every stream-destroy path marks its buffers so a parked reader is always woken. A PLAYBACK session is `restartable` and never marks the shared buffer on its own failure (the stream owns the terminal error).
- **Seek-restart (`SabrSeekLogic`, `SabrSeekLogicTest`):** a session that landed AT OR BEFORE the target drains forward (never restarted); only one that landed PAST it widens the margin and re-aims; an unknown duration estimates 0 (never an endless restart-at-0); `SabrBuffer.resetDemandFrom` re-anchors demand pacing to the seek target on every restart (else the fresh session blocks before its first POST). A seeked session's range echo anchors at its own first segment.
- **`CastAwarePlayer.getBufferedPercentage` is crash-safe for ANY source** (double math, clamped 0..100, guards TIME_UNSET/zero/NaN) - media3's default throws on a pathological value and the session polls it on restore, which would crash-loop launch.
- **Roster = SABR-usable clients only** (`SabrPlayerResolver`): WEB_REMIX → VISIONOS → TVHTML5_SIMPLY, each toggleable (`StreamSabr{WebRemix,VisionOS,TVHTML5}Key`), first-working wins, validated by `tests/sabr-clients.mjs`. Don't add throttled/attestation-walled clients (ANDROID_VR/IOS/IPADOS/WEB_CREATOR/MWEB); `SabrProtection` bails a sustained `STREAM_PROTECTION_STATUS>=2` cap fast (`attestation-capped`) so the fallback moves on. Web clients n-transform the ciphered `serverAbrStreamingUrl` and append the videoId-bound `&pot=`; VISIONOS does neither; the streamerContext poToken is the session token for all; decode the pot tolerantly (standard OR url-safe base64). The audio pick mirrors DIRECT (`pickAudio`, `SabrAudioPickTest`; `opusAllowed=false` for a COMPATIBLE / pre-API-29 download). On playback `resolve` rethrows network-class failures (`classifyErrors=true`) so `MusicService` maps them to `NETWORK_CONNECTION_FAILED` and waits instead of skipping.
- **The seam is `MusicService`** (RELAY pattern): `sabrDataSourceFactory` wraps the SABR source in `DefaultDataSource.Factory` so a downloaded `content://` file still plays from disk; `RoutingDataSource` routes a DIRECT `video:`/`videoaudio:` key to DIRECT even under SABR, and reads the flag from the `sabrModeNow` mirror. A fresh resolve persists a `FormatEntity` (streamClient `… (SABR)`, `loudnessDb`) and runs `recoverSong`.
- **Stream lifetime is registry-owned, NEVER per DataSource open** (audio `SabrStreamRegistry`, video `SabrVideoRegistry`): a seek's close→reopen or a repeat-one replay reuses the live stream (no second /player, no re-drain, no duplicate FormatEntity/watch-time seed). Streams end only on registry replace / evict / remove / `MusicService.onDestroy` (both registries cleared). Replays ride the persistent `SabrSpool` replay cache and the 45-min resolve cache; a stalling client is recorded (`recordStall`) and deprioritized.
- **Downloads over SABR** (`MediaStoreDownloadManager` `sabrAudioMode`/`sabrVideoMode`) resolve with `register=false` (never touch the playback registry), return null → retry on an INCOMPLETE drain, run under `runInterruptible` with the throttled `sabrProgressReporter`. Video downloads drain two tracks and remux via `VideoMuxer` with DIRECT's gates (`pickRung(downloadable=true)`: remux-capable rungs, container-matched audio).
- **Video over SABR** pins the exact itag (`preferredVideoFormatId`, field 17) and has a live quality switcher (`setVideoQuality`/`downgradeForStall` share `resolveAndSwapSabr`; AUTO caps at 720p). **The resolve returns a READY, UNREGISTERED stream, installed in the registry only at the main-thread swap COMMIT after the `stillOurs` guard**, with position + playWhenReady captured AT COMMIT. The SABR DataSources call `transferEnded()` only after `transferStarted()`.
- **Full DIRECT parity - never call SABR a reduced mode:** stats/views/watch time (resolve seeds `watchTimeReporter.onTrackingResolved`; every media POST is stamped with the listen's cpn via `MusicService.sabrCpnFor`), audio quality, instant switch + `prefetchVideoRendition`, metered AUTO cap, loudness, replay caches, demand-paced data usage, seeking, and online/offline stats (docs/sabr/README.md §7.2).
- **The harness is the proof** (`tests/sabr-stream.mjs`, `sabr-clients.mjs`, `sabr-video.mjs`, `sabr-video-clients.mjs`, `sabr-seek.mjs`, `sabr-watchtime.mjs`): prove any change against the live CDN there first, then on-device. Settings: Stream Sources → Experimental (SABR toggle) + "SABR clients".

### Watch-time reporting (the YouTube playback-stats session; DIRECT and SABR, never RELAY/cast)

Every listen (music, video-songs, episodes) emulates a genuine YouTube Music stats session: one `cpn` per listen, a `videostatsPlaybackUrl` ping when playback actually STARTS, `videostatsWatchtimeUrl` pings on the server-driven schedule plus pause/seek, and a `final=1` ping when the listen ends. **Do NOT reintroduce an end-of-listen `registerPlayback` call** - it would double-report the session. Full detail: `docs/watchtime/README.md`. Rules that must not regress:

- **Honesty is the hard rule.** Reported ranges come only from real player positions via the pure, JVM-tested `playback/WatchTimeSegments` (deltas like the official client, seeks never count, paused accumulates nothing, sub-500ms jitter dropped, a backwards position without a seek closes rather than fabricates). Fabricated watch time is invalid traffic and can flag a channel - never widen what gets reported. (Watch time stripped from concentrated single-account testing is expected, not a bug.)
- **CDN-cpn correlation** (`playback/PlaybackNonceRegistry`, `MusicService.stampCpn`): the DIRECT googlevideo request carries the SAME cpn as the beacons, keyed by `VideoRendition.baseVideoId` so all renditions of one listen share it; applied at every DIRECT googlevideo `withUri`, NEVER a local file / cache hit / RELAY. The registry is a bounded LRU that **never evicts the pinned live listen's cpn**. `qoe` and traffic-source params are deliberately not sent.
- **Ping cadence is server-driven** (`playback/WatchTimeSchedule`, JVM-tested): the `/player` response's flush schedule, falling back to the base.js `klA` default `[10,20,30]`/`40`; never a fixed interval (a timing fingerprint). Pause/seek pings never advance the scheduled count, and the ticker **skips overdue offsets** after a long pause/rebuffer instead of bursting.
- **`playback/WatchTimeReporter` owns the session** (state confined to the service main scope; one ordered ping channel per session; beacons fire-and-forget, never affecting playback; `session` is `@Volatile`). `MusicService` only forwards events: `onIsPlayingChanged`, `onPositionDiscontinuity` (captures the departed item's real end position on ANY `AUTO_TRANSITION`, incl. a repeat-one loop; a rendition swap's <1s jump fires no ping), the real-transition hook placed AFTER the video-mode own-swap early-return (an audio↔video swap keeps its session), `STATE_ENDED`, `onDestroy`, and the resolver's `onTrackingResolved` seed (no second `/player`; cached/local plays fall back to one metadata fetch). The reporter reads the player only through the `PlaybackProbe` seam so `WatchTimeReporterTest` drives it with a pure fake - keep the probe returning exactly the `Player` values.
- **Boundary capture - never fabricate, never orphan:**
  - On a track/queue CHANGE the end position falls back to the departed item's own `WatchTimeSegments.lastKnownPositionMs()`, **never the player's current position** (it belongs to the new item). `onPlaybackEnded` and `onDestroy` DO pass the probe's current position (same item; the last-known fallback would drop the tail on a swipe-kill).
  - The own-swap path calls `onOwnSwapTransition()`, which neutralises the captured end position (else a later real transition inherits it) and nulls the session's `fmt` (a stale itag is dishonest).
  - No watchtime/final ping for a session whose playback ping was suppressed (`PauseListenHistoryKey` on at start); `opened` is set INSIDE the playback-ping send.
  - A preloaded tracking resolution older than `TRACKING_MAX_AGE_MS` (1 h) is re-fetched; the `resolvedTracking` cache preserves the live listen's entry when it clears past `MAX_CACHED_TRACKING`.
  - A mid-track **rebuffer is not a pause** (`STATE_BUFFERING` while `playWhenReady` sends no state-change ping and drops no segment).
- **Known limitation (accepted):** a video-mode repeat-one loop is an own-swap (one session spans all loops), so it credits ONE view where audio mode credits N - the safe under-count direction; don't "fix" it into per-loop sessions without weighing own-swap continuity.
- **Hard exclusions**, gated at session creation in the reporter: RELAY (beacons must never ride the relay egress; fail-safe `relayModeNow != false`, so the unresolved cold-start window never beacons) and cast. `PauseListenHistoryKey` silences beacons too, re-checked PER PING.
- **Deferred offline recovery** (`playback/DeferredStatsQueue` + `DeferredStatsPush`, additive - the live path is untouched): a genuinely OFFLINE listen is captured in the reporter's offline branch and re-pushed on reconnect as a fresh-cpn session (playback ping at the listen's real start, `DeferredStatsRecord.openCmt()`, never 0; then `final=1` watchtime with the STORED real ranges). Same honesty and `PauseListenHistory` semantics, the ≥500ms segment floor and no minimum-duration gate (a view counts from the first frame), never via relay. JSONL under `filesDir` (no Room); single-flight, connectivity-triggered, self-rescheduling after a RETRY backoff and after a full `BATCH_SIZE` batch (paced by `PACE_MS`, so a backlog trickles out rather than bursting); 7-day staleness cap. **Watchtime fires only after the playback ping is accepted**; classification: playback 400→drop / other non-2xx→retry, then watchtime 2xx→remove / 400→drop / else→retry.
- **Beacon shapes and extra params come from live-verified base.js, never guessed:** `ver=2&c=WEB_REMIX&cpn&st&et&cmt&rt&final`, `s.youtube.com` → `music.youtube.com` host swap, WEB_REMIX headers + SAPISIDHASH via the shared client. Only `fmt=<streamed itag>` (omitted when unknown) and `muted`/`mos` (`player.volume<=0`, captured on the main thread) are sent; any other param requires re-reading base.js for its exact value semantics first.

### Queue persistence (`saveQueueToDisk` - crash-safe resume; issue #515)

The queue + player-state snapshots persist to two files under `filesDir`. The player is snapshotted on the main thread, but serialization + writes run on the single-threaded `queuePersistScope` - the TEARDOWN write also goes THROUGH that scope (joined), never inline, so it queues FIFO behind any in-flight write (inline raced it: concurrent truncating writes corrupt the file, a stale snapshot finishing last rolled the position back). Every write is ATOMIC (`AtomicFile`; reads via `AtomicFile.openRead` for the rollback). Restore failures are REPORTED (`reportException`; a missing file stays quiet) - silent queue loss must never be invisible. Write decisions are the pure, tested `QueuePersist` helpers against the last WRITTEN snapshot: the queue file rewrites only on content change (`signature`), the state file only when the state moved (a paused idle service is write-silent); teardown always writes. The persisted classes pin `serialVersionUID` (`models/PersistQueue.kt`) - an unpinned edit breaks every updating user's restore, guarded by `PersistQueueCompatTest`'s v37 blob. A Zemer Station broadcast is never persisted (see §Zemer Stations).

### The media notification is gated on user intent (#109)

media3 posts the notification for ANY prepared player with a queue, and `onCreate` restores + prepares the persisted queue on every creation - including SystemUI's post-boot media-resumption bind, which showed a "paused" notification nobody asked for. Rules:
- `onUpdateNotification` is vetoed through the pure, tested `playback/NotificationGate` until USER INTENT: the app's UI bound (`onBind` handing out the in-app binder), an explicit start command (`onStartCommand`), or `playWhenReady` true. A foreground start requested by media3 is never vetoed.
- A controller merely CONNECTING (`onGetSession`: the boot scanner, a headset, Android Auto) is NOT intent. Don't reintroduce a caller-package allowlist; the veto holds for any creator.
- Once intent is seen with a queue loaded, the paused notification is posted.
- **"Stop music on task clear" raises the veto (`stoppingOnTaskClear`) BEFORE it pauses**, then removes the notification both ways media3 does (`stopForeground(STOP_FOREGROUND_REMOVE)` AND `NotificationManagerCompat.cancel(NOTIFICATION_ID)`), then `stopSelf()` - media3 posts asynchronously, so pausing first let a post land on the dead service. Keep that order. The veto blocks only non-foreground updates and is dropped by any later engagement (`markUserIntent`), since a still-bound client (Android Auto, a headset app) must get its foreground start as before.
- **A post in flight is invalidated, not raced:** `onDestroy` calls `removeSession(mediaSession)` + `super.onUpdateNotification(mediaSession, false)` BEFORE `mediaSession.release()` (media3 drops pending callbacks only in that removal path). `onTaskRemoved` schedules one bounded second `cancel(NOTIFICATION_ID)` (`TASK_CLEAR_LATE_POST_WINDOW_MS`) guarded on `stoppingOnTaskClear` still being raised, so a notification the user asked for is never touched. Re-engagement after a lingering task clear re-posts the paused notification (`markUserIntent`).

### Cipher / player rotation (the most common future break)

The `cipher` submodule (package `com.zemer.cipher`, repo `ZemerTeam/zemer-cipher`) deciphers YouTube's `player_ias` signatures in an Android WebView and mints poTokens. It is wired as a git submodule *and* a Gradle composite build (`includeBuild("cipher")` in `settings.gradle.kts` substitutes `com.zemer:cipher` → the local `:library`), so the app always builds the working tree. Full detail: `docs/remote_cipher_config/`.

Player configs live in **one JSON file**, `cipher/library/src/main/assets/player_configs.json` (per player: sig call expression, n-transform URL class, STS, md5-of-first-10000-bytes alias). It is (1) bundled in the APK as the offline default, (2) **fetched at runtime from raw zemer-cipher `master`** by `PlayerConfigStore` (6 h TTL + ETag, plus a forced refresh + one retry when an unknown hash breaks deciphering), and (3) read by the `tests/` harness - so **a config pushed to cipher `master` fixes deployed apps with no APK release**. `PlayerConfigParser` validates strictly (the n-IIFE is built from a local template - remote data can never inject free-form JS; invalid entries are skipped; an invalid file, including any duplicate hash/alias key, is rejected wholesale and the previous table kept). The rules exist in TWO readers (the Kotlin parser and `tests/player-configs.mjs`), pinned byte-for-byte by the shared fixtures in `cipher/library/src/test/resources/config-parity/` - a rule change must update both readers AND the fixtures. When adding a config:
- **Validate empirically**: `node tests/validate-player-config.mjs <hash>` deciphers a real stream and requires **HTTP 206** from the CDN - several constant pairs can decipher, only the live response proves which the server accepts. It prints a paste-ready entry.
- Add the entry (with its MD5 alias) to `player_configs.json` only (no mirrors to sync; `cipher/library/src/test/` guards the shape), then run `node tests/gen-player-dates.mjs` to refresh `player_dates.json` - a **separate, cosmetic** hash → support-date map (`PlayerDatesStore`, song-details sheet); a bad/missing dates file only blanks a label, never deciphering.
- **Push to cipher `master` is the deploy.** Then bump the submodule pointer in `zemer-app` (order: `zemer-cipher` first, then the pointer - reverse breaks fresh clones / CI).
- A cipher *scheme* change (new config shape) needs code + an APK; bump `schemaVersion` only on breaking shape changes (old apps reject newer schemas and keep their last-good table).
- `.github/workflows/player-monitor.yml` (every 30 min) fetches the live `master` file and multi-samples the live player surfaces via `tests/scan-live-players.mjs` (iframe_api plus music/watch/embed) so a low-rate A/B canary is caught early; "known" is decided by the harness loader (`parsePlayerConfigs`, the app's rules), so a pushed-but-invalid entry still alerts. It opens an issue per unknown hash + email + Telegram, but never auto-commits - configs are added by hand.

### The in-app updater (stable via `ghtrack.zemer.io`, nightlies via `nightly.zemer.io`)

`utils/UpdateChecker.kt` (network/UI flow) + `utils/updater/NightlyUpdates.kt` (pure, JVM-tested). Stable reads `ghtrack.zemer.io` `/api` + `/changelog` + `/download`. The nightly channel reads ONE document, `GET https://nightly.zemer.io/api` (contract: `handoff-docs/zemer-nightly-updates-server-request.md`); no GitHub API / raw / nightly.link calls. Rules (they close #543, a Room-downgrade crash from installing a stale "latest artifact"):
- **Check and download are one object.** Update available = `/api.sha` ≠ `BuildConfig.COMMIT_HASH` AND `/api.runNumber` > `BuildConfig.RUN_NUMBER` (`GITHUB_RUN_NUMBER` from CI; 0 outside CI = SHA-only). A lower run number is never an upgrade, whatever the mirror says. The download re-reads `/api`, re-applies the rule (`requireUpdate`: a newer build is accepted, anything else aborts), fetches its SHA-pinned `downloadUrl`, and **verifies size + SHA-256 before offering install** (`verifyDownload`: mismatch = `CORRUPT_ARTIFACT`, part file dropped, `apkFile` untouched). Never download a "latest" URL. A cancelled check/notes fetch rethrows `CancellationException`.
- A non-2xx `/api` is "could not check", never "up to date".
- No "release coming soon" hold: a version-bumped nightly is offered like any other (the old versionCode/versionName skip parked nightly users forever). Don't reintroduce it.
- Release notes = `/changelog?since=<installed sha>` (newest first, capped at `MAX_CHANGELOG_ENTRIES`, entries ≤ `BuildConfig.RUN_NUMBER` dropped client-side since a mirror lacking the installed build returns its whole history), falling back to `/api.commitMessage`; a notes failure never blocks the update.
- App↔mirror contract changes travel as handoff-doc edits, never as edits to the `zemer-nightly` repo.

### Accounts: personal vs anonymous (pooled) - `SAPISID` ≠ logged in

A **personal** Google login sets **`dataSyncId`**. The **anonymous** login signs into a **shared, pooled** account whose cookie **does** carry `SAPISID` but whose `dataSyncId` is deliberately cleared (`App.kt` / `LoginGateScreen` - it breaks the pooled player request). So `parseCookieString(cookie).containsKey("SAPISID")` / `Context.isUserLoggedInFlow()` are **true for anonymous** and must **never** gate remote *account* reads/writes - that leaks the pooled account's library/likes/subscriptions to every anonymous user. Use the reactive `*Flow` helpers; don't reintroduce blocking `runBlocking` login checks.

- The discriminator is **`com.jtech.zemer.extensions.AccountState`**: `isPersonalAccountSignedIn` (non-empty `YouTube.dataSyncId`, usable from entity code) and `Context.isPersonalAccountFlow()`.
- Gated today: `SyncUtils` account syncs + `likeSong`, the entity `toggleLike` remote side-effects (`Song/Artist/Album/PlaylistEntity`), and the menus' playlist add/remove/create/rename/delete + library/history-feedback writes. **Local DB writes always run** (anon keeps likes/playlists locally). The artist-whitelist sync (`syncArtistWhitelist`) is account-independent and stays on for anon.
- **UI account display is gated too** (#137): Settings → Account's "Signed in as" card and the "More content" / "Auto sync with account" switches render only for a personal login (`AccountSettings.kt`, non-blank `DataSyncIdKey`) - never the SAPISID-based `isLoggedIn`. The Anonymous-login button hides once signed in (one Logout control).
- **Synced playlists reconcile non-destructively and stay whitelisted** (`SyncUtils.syncSavedPlaylists`/`syncPlaylist`, #130): keep a song if a whitelisted artist resolves from the renderer **or the local DB row** (`filterWhitelistedWithLocalArtists`). Don't restore a `clearPlaylist()` + strict `filterWhitelisted` rebuild (it wiped user-added songs with sparse/topic-channel artist ids). A failed/empty/partial remote read must never delete a playlist or its songs.
- Not yet gated: Android Auto browse still reads pooled-cookie InnerTube surfaces. Library "My top 50" is local (`mostPlayedSongs`), not a leak.

### Content filtering (whitelist, conditional id overrides, filtered covers)

The "Kosher" guarantee runs through one chokepoint - `utils/WhitelistFilter.kt` `filterWhitelisted` - over the artist whitelist. Full detail: `docs/whitelist/README.md`.

- **Conditional id overrides** (`WhitelistFetcher.fetchBlockedIds`: `content.zemer.io` mirror-first, Firestore `blockedContentIds` fallback → `utils/BlockedIdsCache.kt`, #161): server-listed ids hidden by **reason** - `female` only when `!allowFemaleSingers`, `global` and any unknown reason for everyone; all inert when filtering is off. Lets a *mixed* channel stay whitelisted while specific items drop. Enforced in THREE sites that must move together: `filterWhitelisted`, `search/ZemerResultMapper.dropBlocked()`, and the offline read layer (`offline/SubsetReadLayer.idDropped` + the shared `contentGatePasses`). The artist whitelist is deliberately never run over raw Zemer search results (it clips legitimate Hebrew/community hits); an id drop is safe there. Synced inside `syncArtistWhitelist`, persisted to DataStore, loaded at startup; a failed sync keeps the previous table (never unblocks). Managed by the separate **zemer-admin** app.
- **Display names** (`displayName`/`altName`, contract `handoff-docs/zemer-whitelist-display-names.md`): some docs keep a legacy dual "English - עברית" `name` (frozen - old installs and wire credits depend on it); `displayName` (only on those docs) is AUTHORITATIVE for the artist row, applied by the set-based, idempotent DAO UPDATE `applyWhitelistDisplayNames` after every full fetch, and preferred over the YTM channel title by the stale library-artist refresh. Never reintroduce a one-shot legacy-name-guarded rename (it froze installs on stale names). `altName` is matched by library artist search, the Artists/KidZone browse pills (`WhitelistCache`) and `artistByName`. `DisplayNamesBackfilledKey` re-enables the version-gated sync fast path only after a fetch that carried split names (`whitelistCarriesDisplayNames`, tested).
- **KidZone is a two-tab browse (Artists | Podcasts; `ui/screens/KidZoneTab.kt`, pure + tested):** Artists = local whitelist slice (`WHERE isKidZone = 1`); Podcasts = `/podcasts?kidZone=1` (`ZemerSearchRepository.kidZonePodcasts`, server-first with offline fallback, fail-soft). `kidZone` is a NAVIGATION-context flag riding the show/channel routes, so every server call from a KidZone-opened screen sends `kidZone=1` (server filters as second layer), with offline parity via the subset's per-show `isKidZone`. The Podcasts chip is removed under Block Podcasts. The chip row is the shared `ContentTabChipsRow` (`ui/component/ChipsRow.kt`) in the scaffold's `topSections` slot. The normal Podcasts browse filters wholly-kid channels out (`!isKidZone`). Contract: `handoff-docs/zemer-app-kidzone-redesign-request.md`.
- **Playlist covers come from the filtered tracks, never `playlist.thumbnail`** (YouTube curator art bypasses the filter). `filteredPlaylistCover(filteredSongs, thumbnailOf)` (`ui/screens/playlist/PlaylistHeaderCover.kt`) derives both the header cover and the saved-to-Library cover from the first content-filtered track, falling back to the `queue_music` placeholder / null `thumbnailUrl`. Don't revert either site.

### Zemer curated playlists (the Home "Zemer Playlists" shelf)

Served by the search server's `/zemer-playlists`; detail: `docs/zemer_playlists/README.md`. Rules:

- **Ids are server slugs (`"acapella"`), never YouTube playlist ids** - own screens (`zemer_playlist/{id}`, `zemer_playlists`); never enter a YouTube-playlist path (`online_playlist/…`, save-to-library, playlist menus).
- **All three content flags on every request** (server is default-OPEN; `zemerCuratedPlaylistsParameters()`, unit-tested); the repository deliberately does **not** cache (re-fetch per screen-open, so a response is never shown under another flag set). No client re-filtering beyond `dropBlocked`.
- **Covers are server SVGs at relative URLs** - `resolveZemerUrl()` + the `SvgDecoder` in `App.newImageLoader` (why `coil-svg` exists).
- Empty list = hidden section; detail 404 = back out + Home re-fetch. The shelf has its own `ZemerCuratedPlaylistsViewModel` so a failure never affects the rest of Home.
- Detail All/Albums/Songs chips reuse `LatestReleaseFilter`, split on `fromAlbum`/`albums`, and show ONLY when the playlist has albums (`curatedChipsVisible`, unit-tested); `effectiveFilter` pins ALL when hidden. Rows, Play and Shuffle read the same filtered list; rows never pass `albumIndex` (it replaces the artwork with a number).
- App↔server field changes travel as `handoff-docs/` requests, never as zemer-search edits.

### Genres (the song-level genre layer: Home chips, catalog, detail, radio)

Served by the search server's `/genres` family; detail: `docs/genres/README.md`. Genre is a property of the SONG, independent of the artist flags - never conflate them. Rules:

- **Key off the SLUG (`"nigunim"`), render the `title`** (display-only). Routes carry the raw slug (`search/ZemerRoutes.kt` `zemerGenresRoute`/`zemerGenreRoute`/`zemerGenreSectionRoute`, unit-tested).
- **`kind` grouping is fail-closed**: `musicGenres()` drops `non-music` and any unknown kind (`GenreKind.fromSlug` → null). `HIDDEN_GENRE_SLUGS` (lullaby/carlebach/workout/kids) are hidden from browse (owner decision); `acapella` pinned last (`pinLast()`). All in `search/ZemerGenresModels.kt`, tested in `ZemerGenresTest`.
- **All three content flags on every call** (`zemerGenresParameters` / `zemerGenreFacetParameters`, unit-tested). Genre endpoints are **live-only** (no offline snapshot; offline `/radio` excludes `kind=genre`). The catalog has a 60 s flag-keyed memo in `ZemerSearchRepository.genres()`; detail/facet are uncached.
- **The detail Play button is genre RADIO** (`ZemerRadioQueue.genre(slug)` → `/radio?kind=genre`), never the tracklist; its plays report `radio`. `PlaySource.genre`/`TrackingSurface.genre` ride only tracklist row taps (seed-first song radio). **No Artists shelf** on a genre page (deliberate).
- **Tracklist** (`viewmodels/ZemerGenreViewModel`): near-edge prefetch (pure `shouldPrefetchNearEnd` in `ui/screens/GenreScreen.kt`, off-composition `snapshotFlow`); a track in BOTH the song and video arrays is de-duped across lists (page 0 AND `loadMore`) with disjoint `song_`/`video_` keys.
- **See-all uses the facet endpoint** (`viewmodels/ZemerGenreSectionViewModel`, `GenreSectionScreen.kt`, route `genre_section/{genreId}?section=`): pages `/genres?id=&facet=albums|singles` (limit 200) until `nextOffset` is null. Reuses `YtItemGrid` + `BackTopAppBar`; the arrow shows on any non-empty shelf.
- **Visuals** (`docs/genres/README.md` §visual): monochrome + one gold accent; motif drawables via `ui/component/GenreIcons.kt` → `res/drawable/genre_*.xml` (NOT `material-icons-extended`). `GenreWeaveLayer` rasterizes the motif ONCE into a cached tile and per frame only blits it - never re-rasterize per frame (jank) and don't go back to a cached `graphicsLayer` (evicted layers left cards blank). The header mosaic (`ZemerResultMapper.headerCovers`) is the one color source (de-duped, min 3 covers, neutral `ColorPainter` fallback, sized by `mosaicVariant`, isolated from `thumbnailFor`). `HeaderFontFamily` (Heebo) is only for genre titles/Play/card titles and the Music Status surfaces, never app-wide.
- The Home strip (`ui/screens/HomeGenresRow.kt`, own fail-soft `ZemerGenresViewModel`) sits at the top of the Music tab above Quick Picks, toggled by `ShowHomeGenresKey` (Appearance). App↔server changes travel as handoff docs.

### Lyrics (the provider chain, the Zemer resolver, sync) — `docs/lyrics/README.md`

Detail (resolver source types and rank table, `lineTimes`/`lineExtras` semantics, parser ports, cache policy) lives in `docs/lyrics/README.md`. Rules an agent must hold:

- **Layout:** `lyrics/` root = the chain core only (`LyricsProvider`, `LyricsHelper`, `LyricsChainWalk`, `SyncedFirstPicker`, `LyricsProviderRegistry`/`Ordering`, `LyricsStore`/`LyricsEntry`, `LyricsUtils`, `LineExtras`/`LineExtrasStore`, `LyricsUnavailableException`, `LyricsHttp`); each source lives in one package with its client, models and provider (`lrclib/`, `simpmusic/`, `youtube/`, `zemer/`). All network sources share the ONE Ktor client `LyricsHttp` (lenient JSON, 15/10/15 s timeouts, never throws on status) - don't add a per-source `HttpClient`. `lrclib/LrcLibTrack` is the one LRCLIB record model.
- **Chain order:** Zemer resolver first (`lyrics/zemer/`: `/lyrics/resolve` returns source POINTERS; the app fetches and parses them itself through golden-pinned ports of the server parsers), then SimpMusic, LrcLib, YouTube subtitles, YouTube lyrics tab. Inside the resolver, `ZemerLyricsProvider.order` puts synced sources first, then `rank` (server order breaks ties); an unknown source type is skipped, never guessed; one source's failure skips that source, never the walk. A pointer outranks the same text inline (inline is the outage fallback).
- **Identity gates, never "probably right":** SimpMusic matches only a KNOWN duration within 5 s (`sameRecording`; synced within 1 s); LrcLib requires title AND artist agreement (`identityMatches`, any credited artist of a joined credit via `creditedArtists`). A server-vouched SimpMusic `entryId` whose timings vanished yields nothing (`entryBody`), never plain text in the synced slot; a vouched Apple row with no timings serves its plain text.
- **Sync honesty:** `lineTimes` apply only via `LineTimesLrc` (text-free `lineKey` pairing pinned to the server by vectors, monotone, only to the named source, only when ≥ `MIN_MATCHED_SHARE` (85 %) of lines match, else plain); unmatched lines ride the preceding tag; NO estimated timings anywhere (word sync renders only measured tags). `lineExtras` pair by the same `lineKey`, never by index; one language at a time from `LyricsLineExtrasKey` (**default OFF**); stored per videoId in `LineExtrasStore` (no lyrics-table migration), never outliving its row; `LyricsStore.ensureExtras` asks `POST /lyrics/extras` only once a language is picked; every ask and a refetch share one per-videoId lock.
- **Pick + schedule:** `SyncedFirstPicker` - among TRUSTED providers a synced body beats a higher provider's plain one; the YouTube providers are `lowTrust` and served ONLY when no trusted provider answered. `LyricsChainWalk` changes only the wait: primary trusted provider alone (synced ends the walk), then the other trusted ones concurrently, then low-trust. The walk runs STRUCTURED under its caller (`LyricsHelper.getLyrics` → `LyricsChainWalk.run`), so cancelling the caller cancels the fetches (`LyricsChainWalkTest`); the fetch lambda rethrows `CancellationException` before its catch-all. The chain reads ONE DataStore snapshot per walk (`LyricsHelper.enabledProviders(prefs)`), never a blocking read per provider.
- **Cache:** Room via `LyricsStore` is the only cache (no in-memory cache in the helper). `LyricsStore` is the ONE fetch-and-persist path (`ensure` on pane open, `prefetch(current, next, connected)` on every track start - `MusicService.LYRICS_PREFETCH_DELAY_MS` deferred, skipped offline - so pane open is a Room read; never regress it to a live walk; `refetch` deletes FIRST, fetches are single-flight per videoId). Policy lives in `LyricsEntity` (`needsFetch`/`resolved`): not-found is a negative cache; a PLAIN legacy body is kept (stamped `legacy`), a SYNCED legacy body is replaced when the chain answers. When the chain gains sources/sync, bump `LyricsEntity.CHAIN_GENERATION` (vs `LyricsChainGenerationKey`): each step runs `DatabaseDao.purgeRefreshableLyrics` once (drops not-found + auto-cached plain; keeps synced, `manual`, `legacy`). Do not add DB migrations for lyrics (the 35→36 `provider` column is the only one).
- **Misc:** `LyricsUtils.cleanLrc` formats with `Locale.US` (comma-decimal locales broke LRC). The provider label persists in `LyricsEntity.provider` ("Lyrics from …"). Edits POST to the server's submission queue and Report POSTs after the shared `ConfirmDialog` (`ui/component/MenuDialogs.kt`), both via `LyricsMenuViewModel.feedback` (`lyrics/zemer/LyricsFeedback` on `viewModelScope` - the sheet's own scope dies on dismiss and dropped reports). The lyrics view (`ui/player/LyricsScreen.kt`, in `Player.kt`'s lyrics bottom sheet) reuses the player's transport/slider/identity row via `ui/component/lyrics/LyricsComponents.kt` - never re-roll them. Settings provider rows are the shared `SwitchPreference`; the priority dialog is `ReorderableList` over the pure `LyricsProviderOrdering`.

### Shared UI components (componentized - import, don't re-roll)

Every entry is the ONE implementation; files are under `ui/component/` unless stated. New screens use these; a hand-rolled duplicate is a review miss.

- **Rows/dialogs:** `Preference.kt` - `PreferenceEntry`/`SwitchPreference` take `contentPadding` (`PreferenceEntryDefaults.contentPadding` in settings, `compactContentPadding` in a dialog list; never fork the row); a dialog list scrolls inside the dialog cap via `weight(1f, fill = false)`. `ReorderableList.kt` - `ReorderableList` (drag-to-reorder, rows are `PreferenceEntry` so D-pad focusable) + `ReorderDragHandle` (the one drag-handle glyph; Android Auto rows pass `longPressDraggableHandle`) - never hand-roll a `drag_handle` icon or reorder row. `MenuDialogs.kt` - `ConfirmDialog` (the one Cancel/OK confirmation) + `RemoveDownloadConfirmDialog`.
- **Lyrics:** `lyrics/LyricsComponents.kt` - `LyricsSourceHeader` ("Lyrics from X · synced"; `legacy` shows as unknown), `LyricsNowPlayingBar`, `LyricsLineExtra`.
- **Top bars:** `IconButton.kt` - `BackNavigationIcon`, `MoreVertMenuButton` (row 3-dot), `TopAppBarActionButton` (plain action icon); `BackTopAppBar.kt` - `BackTopAppBar` + `zemerTopAppBarColors()` (the one container color; container == scrolled so bars never grey on scroll; every screen `TopAppBar` passes it except the full-bleed login/onboarding bars and ArtistScreen's transparent over-header state); `AppBarTitle.kt` - `AppBarTitle` (EVERY screen-level bar title goes through it).
- **Playlists:** `ui/screens/playlist/PlaylistDetailShared.kt` (not `ui/component/`) - `PlaylistDetailHeader`, `PlaylistPlayShuffleButtons`, `PlaylistHeaderShimmer`. `shimmer/BoxPlaceholder` is the base slab under `ButtonPlaceholder`/`GridItemPlaceHolder`.
- **Settings:** `SettingsCardGroup` (every settings row run renders through it; per-row corners via unit-tested `settingsCardCorners`, geometry shared with `Material3SettingsGroup`; pre-padded columns pass `horizontalPadding = 0.dp`).
- **Browse:** `ArtistBrowseComponents.kt` - browse header + `ArtistSearchField` + `SearchHandoffPill` (one geometry). `BrowseScreenScaffold` - the ONE whitelist-browse scaffold (Artists / Kid Zone / Podcasts are each a VM + item composables): search pill, count header with the LIST|GRID `TonalToggleButton` pair, `topSections` slot, sticky letter headers, shimmer-vs-empty split (null flow = shimmer), pull-to-refresh via `PullRefreshLoadingIndicator` (in `MediaLoadingSpinner.kt`, shared with Home; a whitelist sync shows as this spinner - no full-screen overlay), back-to-top, fast scroller; math pure + tested in `BrowseScreenScaffoldTest` - browse changes land here once. `IconCategoryCard` (square category tile; Downloaded library's Music/Videos/Podcasts/Status tiles). `GenreCardGrid` (one genre-catalog section; music AND podcast catalogs, spacing via `GenreCatalogTopSpacing`/`GenreSectionGap`), `GenreCatalogShimmer`.
- **Status viewers** (live `StoryScreen` and saved `SavedStatusScreen` must not drift): `StatusStoryTopOverlay`, `ExpandableStatusCaption`, `StatusCopyButton`, `StatusVideoSurface`, `StatusLoadingIndicator`, plus `ui/utils/CubeFace.kt` `cubeFace`.
- **Player:** `ui/player/VideoModePill.kt` `VideoModePill` - the one Song/Video toggle (see §Video mode).
- **Onboarding:** `OnboardingStepHeader`, `OnboardingStepTitle` / `AppNameTitle` (both `onSurface`, never the accent), `OnboardingActionButton.kt` (`OnboardingActionButton` / `OnboardingPrimaryButton` / `OnboardingTextButton`, one shape, no per-screen overrides), `OnboardingChoiceCard.kt` (`OnboardingChoiceCard` + `onboardingCardColors`: `secondaryContainer` active / `surfaceContainer` otherwise, a tone below the button's `surfaceContainerHighest`), `OnboardingInfoCard` (the one shell for filter toggles, permission and sign-in cards), `OnboardingStatusPill`.
- **Loading + heroes:** `ZemerLoadingIndicator.kt` - `ZemerLoadingIndicator` (CONTAINED expressive content spinner; **R25**) + `ZemerLoadingSection` (centered full-width loading block); `MediaLoadingSpinner.kt` - `MediaLoadingSpinner` (BARE neutral over-media spinner; **R26**) + `PreparingOverlay` (scrim + spinner while a tapped item resolves); `HeroTitleOverlay`; `CarouselHeroFrame` (whole carousel-hero frame, shared by Latest Releases and Featured Videos).
- **Marquee:** `Marquee.kt` `gentleMarquee(focused)` - pass the row's D-pad focus state; its spec `gentleMarqueeParams` (JVM-tested) must keep focus-LOSS at `iterations = 0` (else the glide replays on every row the cursor leaves). `ListItem`/`GridItem` (`Items.kt`) expose it as `titleMarquee` (GridItem wraps the title slot - slot content must not add its own); `GridItem` `centerContent` is opt-in (browse tiles only).
- **Selection:** `SearchableSelectableTopAppBar` (the one search + multi-select bar of the seven playlist/history screens; `selectionCount` is a LAMBDA read only inside the bar - never regress it to an inline Int, which recomposes the whole screen per tap), `SelectionTopActions.kt` (`SelectionTopActions` + `SelectionActions`), over `ui/utils/ItemWrapper.kt`.
- **Messaging/progress:** `ErrorRetryState` (error + Retry, optional `detail`), `MarkdownText` (renders `utils/markdown/LiteMarkdown` for server-authored prose; nightly messages via `NightlyUpdates.commitMessageMarkdown`), `LabeledWavyProgress` (the one label + percent + wavy bar row), `InlineMessagePanel.kt` (`InlineMessagePanel` / `InlineErrorPanel` - an actionable error renders here, never as a bare red `Text`).

**Componentize on every touch (non-negotiable).** Before writing a widget, check for a shared one and use it. If you would write (or are editing) a second near-copy, extract it into `ui/component/` and point every site at it in the same pass - never leave two copies to drift. List any new shared component above.

**Shared non-visual helpers - reach for these before hand-writing the pattern:**
- **Id navigation:** `NavController.navigateToArtist(id)` / `navigateToAlbum(id)` (`ui/utils/AppNavigation.kt`) - a blank id would build `"artist/"` and **crash**; the helper no-ops. Pure `artistRoute`/`albumRoute` tested in `AppNavigationTest`; query-param routes keep their builders (`ZemerRoutes.kt`). Ratchet `R16-navroute`.
- **Row 3-dot menu body:** `ytItemMenu(item, navController, coroutineScope, onDismiss, isVideo, kidZone)` (`ui/menu/YouTubeItemMenu.kt`) - never re-write the `YTItem` → `YouTube*Menu` `when` per screen.
- **Zemer repository in a leaf composable/queue:** `context.zemerSearchRepository()` (`di/ZemerSearchRepositoryEntryPoint.kt`). Ratchet `R17-entrypoint`.
- **Text share:** `context.shareText(url)` (`extensions/ContextExt.kt`); `Tracker.action(SHARE, …)` + `onDismiss()` stay at the call site; file/stream shares keep their own builder. Ratchet `R19-share` (lyric-image share in `component/Lyrics.kt` excluded).
- **Clipboard:** `context.copyToClipboard(label, text, confirmationRes = R.string.copied)` (also toasts; `text` is a `CharSequence`). Ratchet `R20-clipboard`.
- **Toast:** `context.toast(resId | text, long = false)` from any `Context`. Ratchet `R21-toast`.
- **Focus:** every focus visual conditions on `focusVisualsEnabled()` and every screen-open grab uses `RequestInitialDpadFocus(requester, enabled, keys)`; a focusable row in a scroller uses `Modifier.bringIntoViewOnFocus()` (all `ui/component/FocusBorder.kt`). Functional focus (text fields, key-event moves, cast volume keys) is never gated. Ratchets `R23-focusgate`, `R24-initialfocus`. Full rules: `docs/ui/standards.md` §11.
- **See-all gate:** `seeAllOnClick(count, action)` / `SEE_ALL_MIN_ITEMS` (`ui/utils/SeeAll.kt`, `SeeAllTest`). Gate on the total the arrow OPENS, not a truncated preview: a preview row (artist-page local sections, search-summary sections, genre album/singles shelves) shows the arrow whenever a fuller view exists.
- **Single episode tap:** `ListQueue.episode(item, playSource)` - never `ZemerRadioQueue.song` (an episode must not seed music radio).
- **Player nav ids:** `MediaMetadata.withResolvedNavIds(currentSong)` (`models/MediaMetadata.kt`, `MediaMetadataNavResolutionTest`), applied once by the full player and queue bar: fills a name-only item's artist id / album ref only from a name-matched DB row with a REAL channel id (#519); never overwrites a wire-provided id.
- **Sort labels:** `songSortTypeLabel(sortType)` (`ui/component/SortHeader.kt`). **Library grid scroll reset:** `LibraryScrollToTopEffect` (`ui/utils/ScrollUtils.kt`).
- **Channel deep links:** `channelDeepLinkRoute(channelId, artistWhitelisted, podcastWhitelisted)` (`ui/utils/AppNavigation.kt`, tested) - artist page, else podcast channel, else no-op; share links via `VideoLinkBuilder.channelLink`.
- **Onboarding flow:** `OnboardingNavigation` (`ui/screens/onboarding/`, `OnboardingNavigationTest`) owns the skip-when-configured transitions (`afterWelcome`/`afterDensity`/`backFromContentFilters`/`backFromPermissions`) so Back never lands on a skipped step; `rememberOnboardingConnectivity()` is the one reachability poll.

**Never `runBlocking` on a UI path** (ANR). Use a suspend function + `LaunchedEffect`/`rememberCoroutineScope`, or a `Flow` (`collectAsState`); the DataStore sync accessors (`dataStore[Key]`, `dataStore.get(Key, default)`) must run OFF the main thread. Ratchet `R18-runblocking`. The deliberate blocking sites live outside `ui/` (ExoPlayer's synchronous `createDataSourceFactory`, the download thread, the DataStore primitives).

**Loading skeletons must match the content that replaces them, and never render on another tab.** The Home content tabs share one `LazyColumn`, and the Home shimmer is music-shaped and driven by music-VM state, so `shouldShowShimmer` **must** stay gated on `homeTab == HomeContentTab.MUSIC` (kept on one line) - else a never-resolving skeleton paints on Radio/Podcasts/Videos. Ratchet `R22-home-shimmer` (a positive assertion).

Enforcement lives in `scripts/ui-audit.sh` (see the rule list at the top of that file; R13 and R23-R26 are described inline beside their greps) + `docs/ui/standards.md`;
when you add a new shared helper with a greppable anti-pattern, add a ratchet rule there in the same pass.

### The home tab (telemetry-ranked rows; zero-InnerTube for content)

`HomeViewModel` + `HomeScreen`. Every content row comes from Zemer (`/home-rows`, `/zemer-playlists`,
`/stations`), local Room, or the flipphoneguy Latest-Releases feed. The **only** `YouTube.*` call in
`HomeViewModel` is `accountInfo()` (the signed-in account card); do not add others. Details:
`docs/home_rows/README.md`.

**The content-type selector (Music / Radio / Podcasts / Video)** is the pure, tested
`visibleHomeTabs`/`effectiveHomeTab` (`ui/screens/HomeContentTab.kt`): Block Podcasts is the ONE filter
that removes a tab (a persisted PODCASTS selection falls back to MUSIC); VIDEO is ALWAYS shown, relabeled
"Video songs" for blocked-video users (a visibility gate is a regression). The selected tab is seeded
from ONE async DataStore snapshot reading the tab AND Block Podcasts together (null until it lands) -
never a main-thread `dataStore[key]` read in composition, never a flash of Music or of a blocked
Podcasts tab. R22 (the MUSIC-gated shimmer) rides this selector - see §Loading skeletons.
The **Video tab**: **Featured Videos LEADS it as a full 16:9 hero carousel** (`ui/screens/VideoHeroCarousel.kt`,
shared `HeroTitleOverlay`) on the SAME `home:featured-videos` surface - the M3 carousel is not a
`LazyList`, so each hero reports its OWN impression once it is the fully-revealed, SETTLED item ~300ms
(`carouselItemDrawInfo`; `Tracker.impression` dedups per surface+videoId), keeping the exposure-dampener
signal. Below it, `/video-home-rows` (Trending Videos / New Videos / Top Video Artists) ride the isolated
fail-soft `VideoHomeRowsViewModel` (see-all via `VideoHomeSeeAllStore`; an absent endpoint leaves just the
hero). Trending/New plays declare `PlaySource.HOME_VIDEO_TRENDING`/`HOME_VIDEO_NEW` with impressions on
the matching `home:video-*` surfaces; the artists row needs neither (plays attribute `artist:UC…`).
Blocked-video users get these rows relabeled + audio-gated, never hidden.

**Project direction:** progressively **replace InnerTube for content discovery** with Zemer-served,
whitelist-pure data, deleting each InnerTube path once its Zemer source lands (not keeping it as a
fallback). Done so far: the home tab, artist/album opens, all radio, every single-song tap, search (Zemer
is the ONLY search engine). When you touch a surface that reaches YouTube for discovery, prefer a Zemer
endpoint (or a `handoff-docs/` request for one). **Streaming/playback stays InnerTube + the cipher**
(§The streaming pipeline) - out of scope. **Remaining InnerTube candidates** (pick from here before
inventing scope):
- **Non-engine InnerTube search users** (each needs its own design): `RecognitionResolver`, the Android
  Auto voice search (`MediaLibrarySessionCallback`), the add-to-playlist online search
  (`AddToPlaylistDialogOnline`).
- **`YouTube.next` selection:** `YouTubeQueue` (playlist/artist Shuffle menus, the artist-page shuffle,
  playlist deep links, the Home easter egg) and `MusicService`'s related-songs pipeline
  (`YouTube.next` + `YouTube.related`).
- **Android Auto browse** reads pooled-cookie InnerTube surfaces (`YouTube.home`; see §Accounts).
- Account-tied InnerTube (`SyncUtils` library sync, `accountInfo()`) is inherent to personal login - not
  on this list.
Deleted and not to be reintroduced: the charts, new-release, mood/genres/browse/explore screens and
`ArtistItemsScreen` (Genres and the Latest Releases shelf replace them).

Rules that must not regress:

- **Featured Albums / Videos / Artists / Playlists come solely from `ZemerSearchRepository.homeRows()`**
  (`GET /home-rows` → `ZemerResultMapper.homeRows()` → `HomeRows`). There is **no InnerTube scrape
  fallback**: `loadHomeRows()` returning null hides the four featured rows, never breaks Home.
- **The ranked content gate is female/israeli/blocked-ids ONLY - NOT the famous/american quality
  proxy** (`isAllowedRanked` / `RankedContentGate`, distinct from `isBlockedArtist`); applying the proxy
  cut the rows to near-empty. Cards carry the artist channel id (`ZemerAlbum/Track.artistId`,
  `ZemerArtist.id`) - without it the one-per-artist `rotateByArtist` dedup and the female/israeli check
  both no-op.
- **Featured Videos stays visible when videos are blocked** - retitled `R.string.featured_video_songs`
  on the Home row and its see-all; the long-press menu is audio-only (`isVideo = item.isVideo &&
  !blockVideos`). The artist Videos section and the search Videos chip follow the same pattern. Never a
  `!blockVideos` visibility gate here.
- **Zemer-sourced albums/playlists open via the server route** (`zemerAlbumRoute` / `zemerPlaylistRoute`
  in `search/ZemerRoutes.kt`, `?zemer=true`), gated on `featuredAlbumsAreZemer` /
  `featuredPlaylistsAreZemer`, so the opened screen is whitelist-scoped and immune to InnerTube
  bot-gating. The Home shuffle button is **"Radio mode"**: `HomeViewModel.shuffleRadioQueue()` →
  `ZemerRadioQueue(kind = "shuffle", seed = null)` - don't reintroduce the old InnerTube lucky-item radio
  or a per-item `radioEndpoint` filter.
- **A brand-new user's empty Quick Picks seeds from Zemer** (`seedQuickPicksFromZemer`, the `auto-top-50`
  curated playlist), not YouTube; a no-op when Quick Picks is non-empty, never breaks Home.
- **Quick Picks must not churn on a pull-to-refresh** (pure, tested `viewmodels/QuickPicksPresentation`):
  a refresh over displayed rows skips the intermediate local-first publish (`showLocalRowsFirst` - two
  publishes meant two shuffles); surviving items keep their position, newcomers append
  (`keepDisplayedOrder`, applied to the once-shuffled final list so See-all still holds); a pool under
  `MIN_POOL_FOR_ROTATION` (8) is shown WHOLE (rotation flipped a small library between subsets).
- **Home song rows read the live Room row through `ui/utils/rememberLiveSong`** (Quick Picks, Forgotten
  Favorites, their See-all) - never `database.song(id)` + `!!`: the whitelist sync can delete the row
  under the composable; the helper falls back to the snapshot.
- The **Radio tab** is the Zemer Stations grid (see §Zemer Stations, `docs/stations/README.md`); its
  now-playing cards tick every 60s while ON SCREEN only (`repeatOnLifecycle(RESUMED)`).
- **Easter egg:** five quick taps on the Home top-bar title (1.5s idle resets) play a fixed song,
  whitelist-filtered (`ui/utils/HomeTitleEasterEgg.kt`, tap rule tested). Owner-requested - keep it.
- **Every content row has a "See all" arrow** → `home_see_all/{row}` (`HomeSeeAllRow`), reading the
  process-wide `HomeSeeAllStore` snapshot `HomeViewModel` publishes each load (the FULL, un-rotated
  filtered pool) - no re-fetch, no re-filter, so it can never disagree with its row. Latest Releases /
  Zemer Playlists keep their own see-all screens.
- **No mainstream Trending/charts row** (charts carry ~no whitelisted artists); the `auto-trending` /
  `auto-top-50` Zemer playlists are the trending/top surface.
- The `/home-rows` contract lives in `handoff-docs/zemer-app-home-rows-request.md`; app↔server field
  changes travel there, never as zemer-search edits.

### Zemer Radio (`/radio` - every radio surface; SELECTION only)

All radio runs on the Zemer `/radio` endpoint (whitelist-pure, blocked-ids filtered server-side + the
client `dropBlocked` pass) via **`playback/queues/ZemerRadioQueue`** - artist / album / song / playlist /
genre seeds and `kind=shuffle`. No radio path uses `YouTube.next()`; the audio stream is still
InnerTube + the cipher. Rules:

- **The continuation token is opaque**: the queue keeps no cursor, `nextPage()` echoes the last token.
  Continuation pages are PURE fresh tracks - never re-apply a YouTube-style `drop(1)`; `MusicService`'s
  auto-load dedupes against the player's ids (`continuationItemsToAppend`).
- **Single-song taps are seed-first** (`ZemerRadioQueue.song()`): the tapped song is the `preloadItem`
  (plays instantly) AND index 0, with the `/radio?kind=song` fill deduped around it. Every tap site uses
  this factory - a bare `ZemerRadioQueue("song", …)` is a station, not a tap.
- **A failed fetch is never silent**: `playQueue()` reports it and calls `onStartRadioFailed()`. With no
  preload it restores the previous queue (only the pointer was swapped); with a preload the song keeps
  playing and `initialFailed` lets `nextPage()` retry the seed page later - set only AFTER the initial
  fetch completes, so a retry never runs concurrently and double-appends.
- `LocalAlbumRadio` plays the local album then continues on `/radio?kind=album`; `firstTimeLoaded` flips
  only after a successful fetch (a transient failure stays retryable).
- Both queues hold only the **application context** (`MusicService.currentQueue` retains the queue for
  the session; a captured Activity leaks).
- Tracking: fill reports as `radio` (`initialItemsAreContext = false`, `continuationIsContext = false`);
  the preloaded seed is the one user-chosen context item.

**Zemer Stations** (`playback/queues/StationQueue`, the Home Radio tab) are the OTHER radio product: one
shared, server-programmed wall-clock schedule per station - every listener hears the same track at the
same moment. Map + design: `docs/stations/README.md`. Rules that must not regress:
- ALL drift funnels through the BIDIRECTIONAL `resyncStationPlayback` (seek forward when behind, WAIT -
  pause until startMs - when ahead, full re-tune when nothing queued is on-air; never a mid-track jump,
  never a backward seek into a played/unplayable slot), invoked from boundaries, pause-resume, error
  skips and STATE_ENDED. **Pause = stop, resume = rejoin live.**
- A broadcast is NEVER persisted (`saveQueueToDisk` guard), and a queue MUTATION (Play next / Add to
  queue) EXITS broadcast mode (`exitStationOnQueueMutation` - else station state latches and queue
  persistence dies for the process).
- The session player masks all skip/seek/repeat/shuffle commands AND no-ops them against stale
  controllers, notifying command changes on every mask flip (`CastAwarePlayer.maskTransportForStation` /
  `notifyStationMaskChanged`, set from the `currentQueue` setter). Every raw-player transport surface is
  gated on `isStationBroadcast` (mini-player swipes, the full player's thumbnail swipe, queue-sheet taps,
  lyrics buttons, the widget's `onStartCommand` skips, repeat/shuffle toggles, the Start-radio
  affordances: player-menu row hidden, notification button disabled, `startRadioSeamlessly` guard), and
  `PlayerConnection.seekTo/Next/Previous` early-return during a broadcast.
- The runway top-up ignores the Auto-load-more preference; repeat is forced OFF at station start.
- Station items map through `ZemerResultMapper.toSongItem` (coverless slots get the derived artwork).
- No content flags are sent (pools pre-filtered server-side); blocked-ids still run client-side.
- A failed slot is marked unplayable and produces no play event (zero-play-time guard); every station
  play tags `PlaySource.station(id)`.

### Corpus-native artist/album opens (no InnerTube fallback)

Artist (`/artist`) and album (`/album`) screens load purely from the Zemer server
(`ZemerSearchRepository.artist/album` → `ZemerResultMapper.toArtistPage/toAlbumPage`), with the offline
snapshot as outage fallback and deliberately **no InnerTube fallback** (a non-corpus item is
non-whitelisted and shouldn't open). A 404 renders the neutral "not available" state. Rules:

- **The stale-row delete is flag-aware** (`AlbumViewModel`, `staleAlbumGoneForEveryone`): the server 404s
  an album that is merely FULLY BLOCKED under the user's flags, so a 404 under restrictive flags is
  re-probed with open flags before the local `AlbumEntity` is deleted; a failed probe keeps the row.
- **An opener-threaded playlistId equal to the browseId never wins** (`toAlbumPage`): cards fall their
  playlistId back to the browseId, and persisting that MPRE as `AlbumEntity.playlistId` breaks album
  radio and share links; the server's OLAK id (else the browseId) is used.
- **Artist credits resolve by ID; name resolution prefers a whitelisted row.** `/album`, `/artist` and
  `/search` album rows carry `artistId`, threaded by `toAlbumPage` into the album + matching track
  credits. For id-less credits, `DatabaseDao.artistByName` prefers a whitelisted row over a generated
  local one (resolving to the generated row starved every whitelist-JOINed query - the infinite album
  skeleton, the doubled credit) AND matches the whitelist doc's `artistName`/`displayName`/`altName`, so a
  legacy dual "English - עברית" wire credit still lands on the renamed row (§Content filtering).
- `insert(AlbumPage)` deliberately does NOT early-return on an existing row: callers reach it only when
  the whitelist-visible album read was null (absent OR poisoned), so re-mapping the artists is the
  self-heal. The "AlbumOpen" Timber breadcrumbs (`ZemerSearchClient`, `AlbumViewModel`) stay in for the
  in-app Log viewer.

### Music Status (the Home "Music Status" row + story viewer; third-party sourced)

A Stories-style Home row of creator "status" circles (under Quick Picks), a full-screen story viewer,
and a See-all grid. Package `statuses/`, UI `ui/screens/statuses/`; the platform APIs and the source
config are in `docs/status/`. The feature is **fail-soft and isolated**; the rules:

- **Two sources, merged + deduped, music-only.** JewishStatus (`StatusesApi.kt`, Supabase PostgREST + R2
  CDN) and YidStatus (`YidStatusApi.kt`). **YidStatus MUST use OkHttp**: its feed needs an
  `Origin: https://yidstatus.com` header that `HttpURLConnection` silently drops → 403.
  `mergeStatusCreators` drops cross-platform duplicates by normalized name (`statusNameKey`); the Home row
  is uniform, the See-all groups by `source`. Creators with empty `recentPostIds` are dropped.
- **The source filter is server-driven, SERVER-ONLY** (`statuses/StatusSourcesConfig.kt`, synced
  version-gated from `content.zemer.io/status-sources`; full rules in `docs/status/README.md`
  §Server-driven source config): one handler per `type` (`supabase-category` / `keyword-feed`) with
  protocol details (R2 host, feed path + `Origin`) baked into the handler, never a descriptor; **NO
  baked-in fallback config** - the last-good config is persisted and the row stays hidden until the first
  successful sync; `parseStatusSourcesConfig` returns null only when no valid config can be obtained
  (caller keeps last-good), while a VALID config with an empty usable set is an intentional dark; unknown
  / disabled / empty-filter providers are skipped non-fatally. `StatusesRepository.syncStatusSources()`
  is AWAITED only for the first-ever sync, throttled to one poll per `STALE_MS`, quiet on network failure
  (no `reportException`); installed version endpoint-capped (`minOf(body, endpoint)`); family caches
  version-stamped; per-provider fail-soft; `StatusSourcesCache.update()` never rolls back. A new provider
  of an existing type is config-only; a new `type` needs an app change.
- **Fail-soft + isolated.** `ZemerStatusesViewModel` (Stations pattern): a failure leaves the row empty
  and hidden; one failed source still lets the other populate (progressive `republish`). Gated by
  `ShowHomeStatusesKey`.
- **Hidden when videos are blocked** (statuses are video-first): gate BOTH the Home row AND the Music
  Status group in Appearance settings on `BlockVideosKey` together (else an un-hideable row or an orphan
  settings group).
- **One source of truth.** `StatusesRepository` (per-source caches + mutexes, merged publish, seen set in
  `StatusSeenStore`). Refresh is three-layer: `STALE_MS` staleness, pull-to-refresh
  (`refresh(force = true)`), and `refreshPosts` on creator open (appends newer statuses in place).
- **Pure timeline math stays extracted + JVM-tested** in `StatusTimeline.kt` (`resumePos`,
  `statusDateGroups`, `formatPostedAt`, `statusLocalDate`; zone-injectable) - never inline it back into
  `StoryScreen`. Gate: `StatusTimelineTest`, `StatusesApiTest`, `StatusNavigationTest`.
- **WhatsApp read/resume semantics.** Open on TODAY's date window (else the newest date) at the first
  UNSEEN status; caught-up (NEWEST seen) sinks the creator to the end (`sortedByUnseenFirst` /
  `caughtUpOnLatest`). Finishing a date rolls FORWARD into the creator's next date; only the newest status
  advances to the next creator; back is floored at the entry date (the jump-to-date sheet goes earlier).
  `StatusCreatorCircle`'s per-segment ring colors seen (`outlineVariant`) vs unseen (accent).
- **The ring respects the content filter.** `StatusCreator.recentPostKinds` (YidStatus from the feed;
  JewishStatus via a batched `public_posts?id=in.(...)&select=id,kind`) feeds `visibleRecentIds(filter)`;
  the ring, `caughtUpOnLatest` and `sortedByUnseenFirst` key off the VISIBLE ids and a creator with
  nothing viewable drops. Unknown kinds show all - never hide more than we can prove.
- **Viewer no-flash invariants** (`StoryScreen`): creators live in a cube `HorizontalPager`; the active
  face renders only when `postsCreatorIdx == creatorIdx`; both neighbors are prefetched (posts AND
  thumbnail bytes); the resume position is resolved EXACTLY ONCE against the AWAITED `seenSnapshot()`
  (never the `seenPostIds` StateFlow, empty for the first frames); the play effect keys on the CURRENT
  status id so an appending refresh doesn't restart the video; progress runs on `withFrameNanos`
  (dt-capped). Video fills the screen (`RESIZE_MODE_ZOOM` in `StatusVideoSurface`, controller auto-show +
  buffering spinner disabled BEFORE the player binds) and `StatusLoadingIndicator` covers it until the
  first frame - never a low-res poster. Its own short-lived ExoPlayer: the music player pauses on open and
  resumes on close; the video pauses on `ON_STOP`.
- **Caption + text are interactive** (shared with the saved viewer): `ExpandableStatusCaption` (3-line
  collapse with Read more/less - expanding freezes auto-advance; links via `linkifyStatusText`; inline
  `StatusCopyButton`); a text status gets its own copy pill. Links open in the EXTERNAL default browser
  via `Context.openStatusLink` UNLESS they match one of the app's own deep links (YouTube /
  music·video.zemer.io) - never an in-app webview. The save FAB shows a DETERMINATE byte-progress ring
  (from `StatusDownloadManager`) tracing its rounded-square outline.
- **Content filter** (`HideTextStatusKey` default ON / `HideImageStatusKey` default off, Appearance) is
  applied at the `StoryViewModel` chokepoints (`applyStatusFilter` on `loadPosts`/`refreshPosts`/
  `cachedPosts`) so driver, cube preview and resume math see ONE list; a fully-filtered creator is
  auto-skipped. `StoryViewModel.contentFilter` starts **null** and the driver waits for the real
  DataStore value - a guessed default re-ran the driver and restarted playback. The See-all gear opens
  `settings/appearance?scrollTo=status`.
- **Media URLs are source-agnostic** (`statusMediaUrl`/`statusAvatarUrl`): a full `http(s)` URL passes
  through (YidStatus), a relative path gets the R2 prefix (JewishStatus).
- Third-party platforms get no handoff doc (`docs/status/` instead); only the Zemer-side source config
  has one (`handoff-docs/zemer-status-sources-config-request.md`).

**Status downloads (save to the device gallery).** Rules:
- **A gallery-media concern, SEPARATE from song downloads**: `StatusGallery` (MediaStore
  `Images`/`Video` under `Pictures|Movies/Zemer/Status/<creator>`; delete reuses
  `MediaStoreHelper.deleteFromMediaStore`), never `MediaStoreDownloadManager`. The FAB uses a
  drawable-painter icon, never the Material download icon (keeps the download-unification ratchets
  green). `StatusDownloadManager` runs fetch → save → index off the main thread, fail-soft.
- **No Room migration**: the index is `StatusDownloadsStore`, a JSON array in DataStore (records
  `{id, kind, creatorId, creatorName, creatorAvatar, postedAt, caption, textBody, mediaUri, savedAt}`).
  Pure naming/index/view logic (`StatusDownloadNaming`, `StatusDownload`, `StatusDownloadsView`) is
  JVM-tested.
- **Filename = posted time (device zone, `yyyy-MM-dd HH-mm-ss`), creator = folder.** Text statuses render
  to a PNG (`StatusTextImage`, colors passed in) and stay `kind == "text"` for the chip filter.
- **Gated on `BlockVideosKey`** everywhere (FAB, the Downloaded card, the library screen).
- **The saved viewer is at FULL PARITY with the live one** (`SavedStatusScreen` +
  `SavedStatusViewModel` grouping by creator): cube-pager creators, `StatusStoryTopOverlay`,
  auto-advance, tap/press-hold, background-pause, `ExpandableStatusCaption` / copy pill,
  `StatusLoadingIndicator`, the `cubeFace` transform; only the media comes from the files. The
  `faceCreator` gate + the byte-bounded `rememberVideoThumbnail` poster cache keep swipes flash-free.
- **The library is a flat grid** (no grouped view): kind chips (All/Video/Image/Text), a
  Recently-saved/Recently-posted sort, a creator-avatar filter row when >1 creator; long-press opens
  `SavedStatusMenu` (`Material3MenuGroup` Select / Remove); Select enters multi-select via the shared
  `SelectionTopActions` + `ItemWrapper`. Text tiles render natively, video tiles via
  `rememberVideoThumbnail`.

### Podcasts (browse → show → episodes → play; a "Kosher" podcast client on top)

**Discovery is the whitelist-pure Zemer server** (`/podcast`, `/podcast-channel`, `/podcasts/new-episodes`,
`/podcast-genres`, `/podcast-home-rows`, podcast groups in `/search`; server-first with the offline
fallback except the live-only `/podcast-home-rows` and `/podcast-channel` offset paging) - there is no
InnerTube podcast-discovery path. The **allow-set** is the content mirror. **Two things stay InnerTube:
playback** (an episode is a YouTube video played through the normal pipeline) and **account sync**
(`SyncUtils.syncPodcastSubscriptions`/`syncEpisodesForLater`, gated on `isPersonalAccountSignedIn`).
App map: `docs/podcasts/README.md`. Rules that must not regress:

- **An episode IS a `SongEntity` with `isEpisode = 1`**: "saved for later" is `inLibrary != null` (NOT
  `liked`), it plays by `videoId`, and `EpisodeItem.asSongItem()`/`toMediaMetadata()`/`toMediaItem()`
  carry `isEpisode = true` end to end. Regular downloaded-songs queries EXCLUDE episodes (they surface
  only in Library → Podcasts → Downloaded), and Keep Listening / Quick Picks fallbacks / Forgotten
  Favorites filter `!isEpisode` - episodes never enter the MUSIC discovery rows.
- **Show vs. host channel** (the #1 gotcha): a **show** is `MPSP…` (`OnlinePodcastScreen`,
  `online_podcast/{id}`); a **host channel** is `UC…` (`artist/{id}?isPodcastChannel=true`). Browse,
  Search and the show screen operate on shows; "View channel" and Library → Channels on host channels.
  `whitelistedPodcastRoute(podcastId, channelId)` (tested) routes to the channel when known, else the show.
- **The host-channel page reuses `ArtistScreen`**, loaded via `zemerRepository.podcastChannel(id)` →
  `GET /podcast-channel` → `ArtistPage` (shows as a "Podcasts" section, latest episodes as "Episodes") -
  never `YouTube.artist`. 404/null → not-available. Radio is HIDDEN for `isPodcastChannel`.
- **The podcast whitelist is channel-level, from the content mirror** (`SyncUtils.syncPodcastWhitelist` →
  `WhitelistFetcher.fetchPodcastWhitelist`/`fetchPodcastVersion`, mirror-first
  `content.zemer.io/podcastChannelsWhitelist` with a Firestore fallback → `PodcastWhitelistCache`,
  separate from the artist whitelist). Mirror docs carry `thumbnailUrl` + `channelId`
  (`ContentPodcastDoc`), so the normal browse grid renders straight from the allow-set - no per-row
  InnerTube art fetch, no `/podcasts` call (only the KidZone Podcasts tab uses `/podcasts?kidZone=1`). An
  empty/failed fetch never wipes the local table (never unblocks). `filterWhitelisted` gates
  `PodcastItem`/`EpisodeItem` and an `isEpisode` `SongItem` on the podcast whitelist (never the artist
  one), respecting filters-off.
- **New Episodes** is the global `/podcasts/new-episodes` feed scoped CLIENT-side to locally-subscribed
  shows (`PodcastLibrarySources.whitelistedNewEpisodes`) - works for anonymous sessions (discovery, not
  account state).
- **Podcast genre sections are SERVER-OWNED**: `/podcast-genres` rows carry a `kind` slug plus an ordered
  `kinds` catalog, grouped by the pure, tested `podcastGenreSections` and rendered through `GenreCardGrid`.
  Unlike music's fail-closed kind drop, an unknown/blank podcast kind falls to a trailing headerless
  section; no `kinds` (older server / offline) = flat grid. Icons: the owner-reviewed `podcastGenreIcon`.
- **Subscribe (channel) vs. save (show) are distinct.** Channel Subscribe writes a bookmarked
  `ArtistEntity` with `isPodcastChannel = 1` (→ `bookmarkedPodcastChannels()`) and calls
  `YouTube.subscribeChannel`, which **must send `params="EgIIAhgA"`** or the server no-ops. The button
  reads the bare `artistEntity(id)` (NOT `artist(id)`, whose whitelist INNER JOIN is always null for a
  host). Save-a-show writes a `PodcastEntity` bookmark.
- **Account-leak gate:** every podcast ACCOUNT read/write (library channels, save/subscribe, episode
  save-for-later sync) gates on `isPersonalAccountSignedIn` - never SAPISID. Anon = local-only.
  Save/subscribe toggles are OPTIMISTIC (flip local first, revert + toast on server failure).
- **Episode resume** (`playback/EpisodePositionTracker`, driven by `MusicService`): saves
  `song.lastPositionMs` on every exit path (periodic 15s - episode-only, never a music wakeup; pause;
  track SWITCH via `previousEpisodeId`/`previousEpisodePosition`; destroy) and seeks on load. The pure,
  tested `EpisodeResume` decides: no resume within `RESUME_EDGE_MS` of the start, a FINISHED episode
  (within `COMPLETION_EDGE_MS` of the end) restarts at 0. **NEVER read `player.*` inside
  `database.query{}`** (background executor → "Player accessed on the wrong thread"). Rows show an "N
  left" hint (`episodeResumePositions()`); the song menu has local mark-played/unplayed.
- **Episode-only player controls** (`EpisodePlaybackControls`, only when `mediaMetadata.isEpisode`): a
  speed pill (`EPISODE_SPEEDS` 1×–2×) + ±30s skips. `MusicService` resets `playbackSpeed` to 1× when an
  episode gives way to a non-episode, so speed never leaks into music.
- **"Continue Listening"** (`HomeContinueListeningRow` + isolated fail-soft `ContinueListeningViewModel`,
  on the Podcasts tab under the genre strip): in-progress episodes, recency from the play `event` table
  (`continueListeningEpisodes()`) - **no new column**.
- **Library → Podcasts** = EPISODES/CHANNELS/DOWNLOADED (`PodcastFilter`) with their own
  `PodcastSortTypeKey`/`PodcastSortDescendingKey` (never the Songs keys). New Episodes is an
  `AutoPlaylistCard` playing the `/podcasts/new-episodes` feed (never InnerTube `RDPN` - unfiltered, a
  kosher leak); Episodes-for-Later is ALWAYS the local saved list (never the online `SE` playlist). Same
  for personal and anon. The shared data sources (whitelist filter + leak gate) live ONLY in
  `utils/PodcastLibrarySources`.
- Episode plays tag `PlaySource.podcast(id)` / `TrackingSurface.podcast|channel` (append-only slugs); a
  tapped episode plays via `ListQueue.episode` (see §Shared non-visual helpers).
- **Block Podcasts is a CATEGORY gate enforced like the female block.** One decision -
  `PodcastSyncLogic.podcastCategoryAllowed` - backs every layer, keyed off the live
  `BlockPodcastsKey`/`ContentFilterState`: (1) `filterWhitelisted`'s `podcastPasses` drops ALL
  podcast/episode items (whitelist membership irrelevant); (2) BOTH nav surfaces hide Podcasts - the
  drawer's `navigationItems` and the bottom bar read the same preference (a static `Screens.MainScreens`
  drawer list was the leak); (3) every podcast destination (`podcasts`, `online_podcast`,
  `podcast_genres`, `podcast_genre`, and `artist`/`artist_section` with `isPodcastChannel=true`) runs
  `podcastsBlockedRedirect` to bounce a restored back stack/deep link to Home; (4) PLAYBACK is gated in
  `MusicService` - `podcastsBlocked()` + `filterBlockedEpisodes`/`Status.filterBlockedPodcasts`
  (`playback/queues/Queue.kt`) drop episodes at `playQueue` (preload + initial items, start index
  re-clamped by the tested `clampStartIndex`), `playNext`, `addToQueue` and the auto-load-more append,
  so even a persisted queue can't play one. Every layer is a no-op while the flag is off. Tests:
  `PodcastSyncLogicTest`, `BlockedPodcastsQueueTest`.

### Offline search backup (`offline/` - the outage fallback)

A downloaded, incrementally-synced corpus snapshot serves `/search`, `/artist`, `/album`, `/home-rows`,
`/zemer-playlists`, the podcast reads and `/podcast-genres` (and PARTIAL `/radio`) when `search.zemer.io`
is unreachable - a faithful Kotlin port of the zemer-search read layer returning the SAME wire models.
Full detail (shards, layers, tests): `docs/offline/README.md`. Invariants:

- **Server-first, always.** `serverOrOffline` falls back only on `isZemerServerUnreachable()` -
  `IOException` **or** `UnresolvedAddressException` (Ktor CIO's no-network/DNS signal, NOT an
  IOException). A 404-null is returned as-is; a non-network exception is never masked. Only SERVER
  responses enter the search LRU (a cached offline result would outlive the outage). Live-only:
  `/playlist`, `/genres`, `/stations`, `/podcast-home-rows`, `/video-home-rows`, `/podcast-channel` paging.
- **Offline `/radio` is PARTIAL** (`SubsetRadio.kt`): `song`/`artist`/`album`/`playlist`/`shuffle` run a
  scoped port of the server's ranking; `kind == "genre"` and the tiers the shards don't carry
  (related-artist, skip-dock, acapella exclusion) stay live-only - disclosed, never faked; never claim
  full parity. An offline continuation token is self-describing (`OfflineRadioToken`) and its whole
  paging chain stays offline - never a live hand-off mid-station.
- **Shards are additive/optional**: an older app ignores unknown shards, an older snapshot just serves
  less. The `lyricsflags` hint only drives a fire-and-forget lyrics prefetch on download completion
  (`MediaStoreDownloadManager.prefetchLyricsIfLikely`) and never gates the other lyrics providers.
- **Kosher defenses:** a 14-day staleness cap (`subsetSnapshotIsFresh`), the live synced whitelist
  overlaid at corpus load (`SubsetCorpus.withLiveWhitelist` - de-whitelisted artists drop at once,
  `isFemale` from the live flag), and ONE shared content gate (`contentGatePasses`) + `idDropped` across
  every offline surface - never hand-inline the female/KidZone/video predicate per site.
- **Sync is staged and crash-safe:** content-hash diffed shards download to `.staged` files, are
  verified, and promote only when ALL verified; the manifest commits last; `loadCorpus` re-verifies
  hashes at read time; an unknown manifest `schema` is rejected wholesale. Enabled = **daily auto-update
  on ANY connection** (no metered gate - a product decision), on the syncer's OWN scope so leaving a
  screen never cancels a download.
- **Parity with the live server is the correctness bar** (id-set + order); thumbnails deliberately use
  the server's `mqdefault` variant - do not "fix" them to `thumbnailFor`'s `hqdefault`.
- **Discovery:** an onboarding step (`OnboardingSearchBackupScreen`) and a one-time promo
  (`OfflineBackupPromoCard`) above Zemer search results; declining in onboarding also silences the promo.

### Tracking (anonymous usage telemetry)

Six events (`open`/`search`/`play`/`click`/`action`/`impression`) POSTed to `tracking.zemer.io`; full
detail in `docs/tracking/README.md`. Rules that must not regress:

- **Telemetry may never break the app**: every `Tracker` entry point is a fire-and-forget `scope.launch`;
  failures are silent; the on-disk JSONL queue under `filesDir` (deliberately NOT a Room table) caps at
  500 dropping oldest; a 400 drops the batch.
- **Identity is one random UUID** (`TrackingDeviceIdKey`, only `UUID.randomUUID()` output - the server
  400s non-canonical ids). Never add account/device/location identifiers.
- **Debug builds send `debug: true` in the batch envelope and the SERVER discards them** - never gate the
  tracker on `BuildConfig.DEBUG`.
- **`play` fires for EVERY listen, however short** (`MusicService.onPlaybackStatsReady`), once when it
  ends; `source` comes from `Queue.playSource` + `Tracker.playSources` - new queue types/surfaces must
  declare their source, and radio continuation must keep reporting `radio` (`ZemerRadioQueue`: fill =
  `radio`, the seed = the queue's declared source; menu Start radio declares `PlaySource.RADIO`). Seed-first
  taps mean `radio` is a large share of plays by design.
- **`action` hooks live at chokepoints** (entity `toggleLike()`s, `MediaStoreDownloadManager`'s download
  entry - `fromUser=false` machine enqueues never report, `DatabaseDao.addSongToPlaylist`, share buttons) -
  no per-surface duplicates, no machine-initiated work in the user-intent signal.
- **`impression` counts what was SHOWN, and over-counting penalises a song** (the server's exposure
  dampener): inside the viewport AND settled ~300ms, deduped per `(surface, videoId)`. Never count a
  composed-but-offscreen row or one a fling passed through; a row nested in a lazy parent must check the
  PARENT's viewport too. When in doubt, do not report.
- **Impressions are the only droppable event type**: discarded while the upload backoff window is open
  and past half the queue cap (`IMPRESSION_QUEUE_CEILING`) - both drops are song-independent; the
  per-POST row cap (`MAX_IMPRESSION_ROWS_PER_POST`) exists because server truncation would not be.
- **Surface slugs (`TrackingSurface`) are the server's coverage-gate vocabulary** - append-only; send the
  tracking maintainer the updated declared list whenever a release instruments a new surface.
- One `search` event per executed query (per-query ViewModel guard), never per keystroke or chip switch.
  Everything is tracked (KidZone included), no opt-out (product decision). The `search` event carries
  `provider` = the pinned `SEARCH_TRACKED_PROVIDER = "zemer"` (tested) - keep sending it.
- **Backfills** (`PlayHistoryBackfill` → `play_backfill`, `LibraryActionBackfill` → `action_backfill`,
  `favorite`|`download` only) upload through `Tracker.uploadBackfill` - NEVER the live queue - sharing its
  single-in-flight + backoff discipline, converting timestamps in the DEVICE zone, and permanently off
  once their done-flag is set. Plays: row-ID cursor + a persisted max-id bound (live-tracked rows never
  double-upload), 3-year window. Actions (a snapshot): resume by persisted **acked-line count** (snapshot
  `t`s are unstable - zone changes, `SyncUtils.likedSongs` rewriting `likedDate` - so server dedup can't
  absorb a replay), favorites before downloads, a **10-year** window (don't shrink it), pacing sleeps only
  BETWEEN batches, 90 s start delay (load spreading, not ordering), machine downloads included (weak
  signal server-side). Detail + rationale: `docs/tracking/README.md`.

### The player background system (one effective style, one extractor)

The full player (`ui/player/Player.kt`), the mini player (`ui/player/MiniPlayer.kt`) and the lyrics
screen (`ui/player/LyricsScreen.kt`) share ONE source of truth, **`ui/player/PlayerBackground.kt`** -
never re-derive any of this per surface:

- **`PlayerBackgroundStyle.effective()`** downgrades **BLUR → DEFAULT below Android 12** (the blur is a
  `RenderEffect`, a no-op before API 31, so raw BLUR there puts bright artwork under the light-on-dark
  transport). Every *render* decision (background, text/icon colors, status bar, gradient enable) reads
  the **effective** style - each surface shadows the preference (`val playerBackground =
  playerBackgroundPref.effective()`); the settings list hides BLUR when `isBlurSupported` is false.
  `effective(blurSupported)` takes the flag explicitly so it is JVM-tested (`PlayerBackgroundTest`).
- **`rememberPlayerGradient(mediaId, thumbnailUrl, enabled, fallbackColor)`** is the ONLY gradient
  extractor: one decode + Palette pass per track, memoised in a shared bounded `LruCache`; the previous
  palette is held while a new one extracts (and on decode failure) to avoid a flash.
- **`playerGradientStops(colors)`** is the ONLY place gradient stops are built, so the shape can't drift.
- **Light (white) content only once a dark backdrop is actually painted** (blur needs a `thumbnailUrl`,
  gradient needs non-empty `gradientColors`); until then stay on `surfaceContainer` with theme-colored
  text - else white text lands over the light Home screen showing through the transparent mini bar.
- Status-bar icons: a `DisposableEffect` in `Player.kt` keyed on (background, theme, `state.isExpanded`)
  forces light icons only while the sheet is **expanded**, and on dispose restores the theme-correct
  appearance (as `MainActivity.setSystemBarAppearance` does), never a stale captured snapshot.
- The transport cluster (`PlayerTransport.kt`) caps the labelled play button via `BoxWithConstraints` so
  it shrinks on narrow widths; `TransportSkipButton` cancels its long-press repeat on release. New
  transport buttons reuse `TransportSkipButton` + the accent focus border; new D-pad rows reuse
  `Modifier.focusBorder()`. ui-audit **R12** bans raw `Modifier.blur(` in `ui/` - route player blur
  through the effective style.

**Material 3 Expressive: adopt per component, never globally.** The app stays on standard
`MaterialTheme` (never `MaterialExpressiveTheme`, never a global `MotionScheme.expressive()` - the app's
hand-rolled animations ignore the theme motion scheme, so springiness is added per-interaction).
When an Expressive component fits, use it behind a per-site
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` and through a SHARED wrapper (material3
`1.5.0-alpha18`, compose `1.11.4`). In use:
- **Loaders:** the contained content spinner `ZemerLoadingIndicator` (ui-audit **R25**: no raw
  `ContainedLoadingIndicator(` outside it) and the bare over-media `MediaLoadingSpinner` (**R26**); Home
  pull-to-refresh = `PullToRefreshDefaults.LoadingIndicator`; loading bars = `LinearWavyProgressIndicator`
  (`AppStateViews`, `LabeledWavyProgress`). Tiny in-button spinners and determinate download rings stay
  standard `CircularProgressIndicator`.
- **Play-preparing spinner:** a tapped song/video/episode shows `PreparingOverlay` (scrim +
  `MediaLoadingSpinner` in the NEUTRAL now-playing-equalizer color, never the accent) from tap until
  READY. Driven by `PlayerConnection.preparingMediaId`, set in `playQueue` from the queue's
  `preparingItemId` (the preload, or a `ListQueue`'s tapped startIndex item so preload-less plays are
  covered), cleared on READY / error / a 30 s timeout, skipped when the tapped item is already current;
  read via `rememberIsPreparing(id)` by `ItemThumbnail` and the video hero. `YouTubeGridItem` shows the
  centred play button for `EpisodeItem`s too. `AlbumPlayButton` keeps its OWN spinner (an album's play
  id is a track, so it can't ride the shared signal). The in-player VIDEO buffering spinner is instead
  the contained, theme-colored `ZemerLoadingIndicator` (a content load).
- **Chips:** `ChipsRow` + `LibraryFilterChip` render as `TonalToggleButton`, keeping the D-pad focus
  treatment.
- **Carousel heroes** (Latest Releases `HorizontalMultiBrowseCarousel` via
  `latestreleases/LatestReleaseCarouselItem`; Featured Videos): one shared `CarouselHeroFrame` (focus
  ring drawn with the carousel's `maskBorder` so it follows the morph) + `HeroTitleOverlay` (Latest Releases badges
  forced white on the scrim). Both advance ONE item per swipe via `heroCarouselFlingBehavior` =
  `singleAdvanceFlingBehavior` with its DEFAULT spring snap - never pass a fixed-duration `tween` (it
  ignores release velocity and hitches every swipe). `LatestReleaseCard` is the See-all list card.
- **Shapes + pops:** `MaterialShapes` cookie clips via `ui/component/ExpressiveShapes` - About credits
  avatars (`expressiveAvatarShape`) and the currently-playing card art (`expressivePlayingShape`); artist
  avatars app-wide stay circles. `rememberPopScale` pops the player like heart, driven by the USER'S TAP
  counter, never the liked flag (it flips on every track transition). `rememberActivationPopScale` pops a
  card ONCE as it becomes the playing item. **Never add a press-scale to the shared `GridItem` /
  `ListItem`** - `awaitFirstDown` also fires when a touch starts a scroll, so every flung row bounced.

**Refresh rate** (Appearance; `RefreshRateModeKey` / `RefreshRateMode`): **System** (default, no forced
mode), **Standard** (~60 Hz, battery), **High** (max rate at the current resolution). `MainActivity`
applies it through the pure, tested `utils/RefreshRateSelection`: `selectRefreshRateMode` returns null
for SYSTEM so switching back clears a previously-forced mode; `migrateRefreshRateMode` maps the legacy
boolean's explicit OFF to Standard and ON/unset to System (the old default force-pinned max rate - a
constant battery cost).

### Video mode (the in-player Song/Video toggle; audio-first everywhere)

A video-classified song plays as **ordinary audio by default on every surface**. Watching is a
per-play, in-player opt-in: `VideoModePill` on the art slot swaps the current queue item's rendition
between audio and video without changing the queue, track or tracking identity. There is **no video
screen or nav route** (don't reintroduce one); fullscreen is the in-player overlay
`PlayerVideoFullscreen`. The I1-I8 / D3/D4/D7 labels are cited in source comments
(`VideoModeController.kt`, `VideoModeLogic.kt`, `PlayerVideoUiLogic.kt`, …) - the code comments ARE the
spec (the "unified-video DESIGN §n" some cite is not in the repo). Quality ladder detail:
`docs/video_quality/README.md`.

**Classification.** `SongItem.isVideo` is set once at the mapper boundary
(`ZemerResultMapper.songItems(..., isVideo = true)` for every Zemer videos-category row; InnerTube's
`musicVideoType` for the non-engine search users). Per-item surfaces (the `Icon.Video()` badge, menu
`isVideo` gating, search `clickKind`) read that flag - never re-derive it per item with a title-sniff
(a sniff means the mapper missed a spot; the artist page's section-level `isVideoSection` title check is
the one existing exception). `SongItem.toMediaMetadata()` deliberately does NOT carry `isVideo` into
playback - a video-song plays/downloads/persists like a plain song; it only marks `VideoSongIds` for the
toggle.

**Availability - `VideoModeLogic.availability()` is the single source of truth** (pure, JVM-tested).
Hard gates first, unconditionally: casting, `BlockVideosKey`, a Zemer Station broadcast → null (no
toggle), even for a downloaded LOCAL file. Renditions in order: **LOCAL** (downloaded muxed file, no
source swap, offline), **SELF** (a KNOWN non-ATV `musicVideoType`, never guessed), **COUNTERPART**
(known video counterpart id; plumbing only, dormant). `VideoSongIds` (process LRU) grants SELF instantly
for a corpus video-song while YouTube's type is unknown; a later-learned `musicVideoType` (incl. ATV)
overrides it. `blockVideosNow` is seeded **null** and unknown reads as **blocked** (a restored queue can
recompute before the DataStore collector lands). `videoModeAvailable` is published by an async `combine`
AND a synchronous `recomputeNow()` from `MusicService.onEvents` (same stack as `currentMediaMetadata`),
so the pill never flashes mid player-open.

**Swap mechanics (`VideoModeController`).** Enter replaces the current `MediaItem` with a
`video:<id>`-keyed rendition (`VideoRendition`, `buildUpon()`: same mediaId, different URI/cache key) and
seeks to the captured position; exit reverses it, position-continuous. `pendingSwap` + `videoModeItemId`
classify the OWN swap's `onMediaItemTransition` so it never fires real-transition side effects (cast
reload, auto-load-more, save-queue); `ListenAccumulator` suppresses a swap-caused `PlaybackStats` end and
emits once at the real end (I4). A repeat-one loop of the same video item (`isRepeatOfSameItem`, media3
`MEDIA_ITEM_TRANSITION_REASON_REPEAT`) classifies the same way (else every loop reverted to audio).
`exitVideoModeSameItem` verifies by identity that the current index still holds the video item - media3
masks `seekToNext`/`Previous` synchronously, so an exit trigger in that gap would clobber the new track.

**Errors hand off, never strand the player.** `VideoModeController.onPlayerError` runs BEFORE
`MusicService`'s 403-refresh (which would invalidate the wrong, audio, entry). A STREAMING rendition
error invalidates the stale URLs (rendition key, plain key, merge-audio key, SABR resolve cache), reverts
to audio and returns true; it never touches the user's quality. A **LOCAL** error returns `false` so the
service's normal pipeline (self-repair, network wait, auto-skip) runs. Separately, a STREAMING item whose
downloaded file now exists hands over to the file on ANY player error (mid-play download then offline) -
async (`scope.launch` + `withContext(IO)`), never `runBlocking` on the listener's main thread.

**Cache correctness (both load-bearing).** (1) Local files are read THROUGH `playerCache` under the same
mediaId as the stream, and `CacheDataSource` serves cached spans regardless of URI - so a muxed download
+ streamed spans mixed two containers (black video / extractor errors). **Every position-0 open that
picks the local file purges the id's `playerCache` resource first**, and `DownloadUtil.removeDownload`
purges the bare id + the whole `VideoRendition.allRenditionKeys` family on delete. (2) **Downloads must
never write `DownloadUtil.sharedUrlCache`** - a download's `forDownload` format is a different
itag/container and poisons a later seek's stream source.

**Option A downloads:** a video-capable item downloads its MUXED video (player menu: `!blockVideos &&
(isVideo || currentItemIsVideo)`; a blocked user gets plain audio, since `songRow` HIDES the row for a
video item). The muxed file plays as audio in the music queue (`Mp4Extractor` is registered in
`createMediaSourceFactory` for its plain-moov container), stays addable to playlists, and makes the
toggle work offline via LOCAL; one Remove covers both renditions. The AUTOMATIC video pick (stream or
download) shares one metered-aware cap, `VideoRendition.defaultMaxBitrateKbps` (1500 metered / 6000
unmetered), overridable per download via `requestedVideoBitrate` - never silently pull the largest file
on a metered connection.

**The quality ladder** (`VideoQualityLogic` + the in-player switcher; progressive + adaptive
video-only rungs up to 2160p). Rules that must not regress:
- **`VideoQualityLogic` is the ONE ladder/selection authority** (pure, JVM-tested): one rung per
  qualityLabel (progressive wins its label, then avc1 > vp9 > av01, then bitrate); a target resolves to
  the best rung at-or-below (never null for an explicit pick); AUTO = the automatic progressive pick, the
  only thing the metered cap governs.
- **The rung's itag lives IN the cache key** (`video:<id>:p<itag>` / `:q<itag>`) so rungs never share
  spans, which is why exact-itag resolution has NO fallback format. The two drift-prone keys - plain
  `video:<id>` and merge-audio `videoaudio:<id>` - track their last itag (`videoKeyItagCache` /
  `mergeAudioItagCache`, via the shared `seedPlainVideoKey`, used by resolver AND prefetch) and purge
  spans on change. Every `preferVideo=true` resolution resolves merge audio at **HIGH** so its itag
  agrees everywhere. A `:q` key routes through the `MergingMediaSource` (video-only rung + audio under
  `videoaudio:<id>`, never the bare id's spans). Only WEB-client resolutions seed `videoRungUrls` (a
  non-web URL 403s past the 1 MiB wall); the ladder block is skipped for `forDownload`.
- **An explicit quality (Settings `VideoQualityKey` OR the switcher) is HONORED on every connection,
  metered included** - no metered gate, no bandwidth pre-gate, no error-time AUTO pin. Protection lives in
  the AUTO cap and the rebuffer guard. `qualityOverrides` (switcher pick OR guard downgrade) drives
  streaming; `userQualityPicks` (explicit picks only) is what DOWNLOADS read (`downloadVideoQuality`), so
  a guard downgrade never leaks into a download. New plays use `effectiveQualityTarget`. A same-itag tap
  is a no-op. Entry plays AUTO instantly and upgrades position-continuously when the ladder lands (no
  blocking wait, no redundant re-swap). Quality re-swaps use the same `pendingSwap` +
  `listenAccumulator.onSwap` discipline. LOCAL and RELAY have no switcher (quality keys must never reach
  the relay resolver - enforced at `swapToVideoKey`).
- **Switcher UI:** `VideoQualitySelector` (over-media chip: inline art slot BottomStart, fullscreen
  TopEnd), decoder-filtered by `VideoDecoderCaps`. One picker body `VideoQualityMenu`: inline = the root
  bottom-sheet menu (`LocalMenuState`); fullscreen = a fullscreen-LOCAL panel (`onOpen`) inside the
  overlay (the root sheet fought the immersive landscape window); Back closes the panel first.
- **Fast entry:** one resolution yields every rung URL + the merge-audio partner (`videoRungUrls` /
  `mergeAudioUrl`, pure-local cipher work) seeded by `MusicService.seedVideoUrlCaches`, so switching
  never pays a second `/player`. The expanded player prefetches while the pill shows
  (`prefetchVideoRendition`: deduped, expiry-aware, silent; skipped for RELAY, a LOCAL download, and
  offline). URL finalization is ONE helper, `applyWebUrlTransforms` - never fork it.
- **Rebuffer guard:** a `STATE_BUFFERING` after READY on a STREAMING rendition is a stall; TWO within
  `REBUFFER_WINDOW_MS` (45 s) drop exactly ONE rung (`shouldDowngradeForRebuffer`, `rungBelow`) and pin
  the item there. One blip never downgrades; never bandwidth-jump several rungs (a rung's bitrate is its
  peak and the estimate is depressed after a stall). Seeks are exempt via a **timestamp** grace window
  (`onSeekDiscontinuity`, `SEEK_GRACE_MS`) - not a boolean flag, which a seek into buffered data would
  never clear. A swap's own prepare, LOCAL/audio, and AUTO never count. Don't widen the shared
  LoadControl (it would regress audio/RELAY rebuffer latency). The buffering spinner lives inside the
  shared `PlayerVideoSurface`.
- **Downloads above the progressive ceiling** fetch video + a container-matched audio partner from the
  SAME response/client (`PlaybackData.downloadAudioUrl`: mp4/avc → AAC, webm/vp9 → Opus), verify each
  against its contentLength, and remux on-device (`VideoMuxer`; avc1+AAC → MP4, vp9+Opus → WebM on API
  29+ only via `opusWebmMuxSupported`; av01 is stream-only). The target is decoder-gated but NOT
  metered-capped. `requestedVideoQuality` follows `requestedVideoBitrate`'s lifecycle (survives failed
  attempts) EXCEPT `VideoMuxer.Result.INCOMPATIBLE`, which clears it so the retry falls back to the
  automatic progressive pick; a transient (disk I/O) mux failure keeps it.

**UI rules (`PlayerVideoUiLogic`, pure, JVM-tested).** Opening the lyrics sheet reverts to audio
(closing it does not restore video). Fullscreen force-exits when video mode ends or the sheet
collapses. `MainActivity.onStop` reverts to audio (no invisible decode). **Leaving via Home/recents in
video mode enters picture-in-picture** (`ui/player/VideoPip.kt`): API 31+ arms platform auto-enter while
video mode is on (`setVideoPipAutoEnter`); below, `onUserLeaveHint` enters under
`shouldEnterPipOnLeave`, NEVER while an Activity we launched is opening (`ownActivityLaunchInFlight`, set
by the `startActivity`/`startActivityForResult` overrides, cleared on resume - the hint also fires for
our dialogs, share sheets, pickers). `VideoPipOverlay` is the window's ONE content owner (inline +
fullscreen yield via `inPip`): video when in video mode (`showPipVideo`), else cover art
(`showPipArtwork`). `smallestScreenSize` in the manifest `configChanges` is load-bearing (without it a
PiP transition can recreate the Activity, whose onStop revert leaves an empty window).

**The blocked-user guarantee.** The ONLY path that sets video mode true is
`VideoModeController.enterVideoMode`, reached only via `setVideoMode(true)`, which re-checks
`computeAvailability()` first; every `PlayerVideoSurface` site (`Thumbnail`, `PlayerVideoFullscreen`,
`VideoPipOverlay`) requires `isVideoMode` with no bypass. So no code path enters video while blocked,
casting or broadcasting. **Don't reintroduce a `!blockVideos` visibility gate** on a video row/section:
the design is "always shown, relabeled + audio-gated, never hidden".

### The theme system (palette picker, cohesive Material You, pure-black)

One materialKolor scheme generated from a **single seed accent**, picked in Settings → Appearance →
Theme (`ui/screens/settings/ThemeScreen.kt`). Rules that must not regress:

- **One seed, no neutralized surfaces.** `ZemerTheme` (`ui/theme/Theme.kt`) seeds the WHOLE scheme from
  the selected color. Never copy `darkColorScheme()` surfaces over the seed (greys the surfaces while the
  accent stays saturated). The only override: the brand accent pins its exact dark primary family
  (`BrandPrimaryDark`/`…Container` in `ThemePalettes.kt`).
- **The picker is one control.** `SelectedThemeColorKey` (int ARGB) + `DynamicThemeKey` (album-art);
  selection logic is the JVM-tested `ThemePaletteSelection`. `PaletteColors` (`ui/theme/`, hence
  R8-hex-exempt): **Dynamic** (`Color.Transparent` sentinel), **System** (`SystemWallpaperThemeColor`,
  Android 12+ only via `visiblePaletteColors`; the ONLY route to `dynamicDark/LightColorScheme`),
  **Zemer** (brand), then the spectrum. Default = `DefaultAccentColor` = the Zemer brand on every device.
- **Pure-black is via the scheme, not hardcodes.** `ColorScheme.pureBlack(true)` blacks
  surface/background/surfaceVariant/surfaceDim and surfaceContainer{Lowest,Low,} but keeps
  surfaceContainerHigh/Highest and surfaceBright so cards and dialogs stay visible. So **no `if
  (pureBlack) Color.Black else <token>`** - use the plain token. The only remaining ones are in the queue
  sheet (`ui/player/Queue.kt`); don't add more.
- **Top bars are neutral chrome:** `zemerTopAppBarColors()` (`ui/component/BackTopAppBar.kt`) =
  `surfaceContainer` + neutral title/icons, used by every screen bar incl. MainActivity's Home bar.
- **Activities outside MainActivity use `ZemerAppTheme`** (resolves saved palette + dark mode +
  pure-black); `ZemerTheme` with default params is brand pink and only correct inside MainActivity. The
  RemoteViews **widget** uses static `@color/widget_accent`. `MusicWidget.hasPlacedWidget` must stay
  wrapped in try/catch: Glance NPEs on ROMs without an AppWidgetService, and the escape killed background
  playback on every play transition.

### The download system (ONE unified path - never fork it)

Downloads go **exclusively** through `MediaStoreDownloadManager` (durable truth `SongEntity.isDownloaded`
+ `mediaStoreUri`; live progress in its in-memory `downloadStates`). The legacy ExoPlayer download map,
media3 `DownloadManager` and `ExoDownloadService` are gone; the ratchets keep them out. State/UI/menu
detail: `docs/ui/standards.md §12`. Rules:
- **One read path:** state from `playback/DownloadStateResolver` (`forSong` / `aggregateSongs` /
  `aggregateByIds`, `songProgress` / `aggregateProgress[ByIds]`) = persisted `isDownloaded` **OR** live
  state (reading the live map alone makes downloads vanish after relaunch); UI through
  `ui/component/DownloadStatusUi.kt` (`rememberSongDownloadStatus/Progress`, `SongDownloadBadge`,
  `AggregateDownloadButton`, `DownloadStatusIcon`); menu rows through `downloadMenuItem(...)`
  (`ui/menu/DownloadMenuItems.kt`) decided by `playback/DownloadMenuLogic` (`songRow`/`collectionRow`). A
  download row **never dismisses the menu**. Videos use the same path (`DOWNLOAD_VIDEO`, hidden when
  blocked; Option A muxed downloads - see §Video mode).
- **A collection NEVER shows a FAILED/retry row** (`collectionRow` takes only the aggregate status; don't
  add an `anyFailed` arg): a failed member leaves the aggregate NOT_DOWNLOADED, so DOWNLOAD re-enqueues
  the missing members; a retry row was a dead end. Only single songs get FAILED.
- **Async collection menus:** fetch the songs **at click time** (fetch-if-empty), and Download, Remove
  AND the aggregate status all read that SAME resolved list, never the possibly-empty `songs` prop. Online
  items aggregate by videoId (`aggregateByIds` + a persisted-downloaded id set) and **persist each
  `MediaMetadata` THEN download** (a lookup of a not-yet-persisted id no-ops).
- **Playing a downloaded file** (`MusicService.resolveDownloadedFileUri`, shared by DIRECT and relay):
  use the file when it opens; if gone, **stream this play AND re-enqueue a download** - never crash,
  never clear `isDownloaded` - but skip the re-enqueue when `downloadUtil.mediaStoreDownloadState(id)` is
  already FAILED this session (else every play re-downloads). `downloadedFileOpens` returns false on ANY
  open failure so playback streams.
- **Every DAO `WHERE x IN (:list)` goes through `db/SqliteBindLimit.chunks`** (a `...Chunk` `@Query` + a
  splitting default method): Android < 11 binds at most 999 variables (`SqliteBindLimitTest`).
- **`database.query {}` is fire-and-forget** - never split one logical mutation across two `query {}`
  blocks on the same row (they race; this once persisted `isDownloaded=0`). Use one
  `database.transaction {}` whose final write is authoritative.
- **`markSongAsDownloaded` must not clobber user state:** base the row on the EXISTING DB row and write
  only download columns (`isDownloaded`, `dateDownload`, `mediaStoreUri`, `isVideo`); backfill
  `duration`/`thumbnailUrl` only when missing. `performDownload` backfills both from
  `playbackData.videoDetails`.
- **`requestedVideoBitrate` survives a failed attempt** - cleared on success/cancel/delete, never in the
  per-attempt `finally` (else `retryDownload` falls back to uncapped quality).
- **Remove deletes the file on every backend:** `MediaStoreHelper.deleteFromMediaStore` routes SAF
  document uris (custom download path) through `DocumentsContract.deleteDocument`.
- **Downloaded video-songs appear in downloaded MUSIC by default** (`VideoDownloadsInMusicKey`, default
  true): `DatabaseDao.downloadedSongs*` take `includeVideos`, and the library list, Downloaded
  auto-playlist, library mix AND Android Auto (`MediaLibrarySessionCallback`) read the SAME preference.
  The opt-out lives on `DownloadedVideosScreen`, reachable even when videos are blocked.

**Audio format + metadata embedding (pure Kotlin, no NDK).**
- **Routing is `utils/CoverArtEmbedder`** (`supportsEmbedding` + embed) by extension: MP4/M4A →
  `AudioRemux.flattenMp4` then `utils/mp4/Mp4MetadataWriter` (ilst atoms incl. `covr`, `aART`, `trkn`,
  lyrics); WebM/Ogg Opus → `AudioRemux.webmOpusToOgg` then `utils/ogg/OggOpusTagger` (OpusTags +
  `METADATA_BLOCK_PICTURE`). **The Ogg tagger STREAMS page by page** (`PageReader` over a
  `RandomAccessFile`) - a whole-file read was an OOM site with concurrent downloads; trailing garbage or a
  truncated page yields no output. Tests: `Mp4MetadataWriterTest`, `OggOpusTaggerTest`, on-device
  `OpusDevicePipelineTest`.
- **Opus needs API 29+** (`AudioRemux.oggMuxSupported`; `minSdk` 26): `MediaStoreDownloadManager` sets
  `downloadOpusOk = !videoDownload && oggMuxSupported && DownloadAudioFormat == BEST`, and `YTPlayerUtils`
  allows itag 251 for a download only then; 26-28 get best AAC/m4a. Never save a raw `.webm`
  (MediaStore.Audio rejects it).
- **`OggExtractor` MUST stay registered** in `createMediaSourceFactory` (else downloaded `.ogg` files hit
  "Source error" → a self-repair re-download loop).
- **`DownloadAudioFormatKey` ("downloadAudioFormat") is permanent** (Player settings, API 29+): `BEST`
  (default) / `COMPATIBLE` (always AAC). Renaming it strands updating users.
- **A download is never reduced by streaming quality or metering:** audio downloads resolve at
  `AudioQuality.HIGH` (`if (videoDownload) audioQuality else AudioQuality.HIGH`).

Enforcement: `scripts/check-download-unification.sh` (whole app, run by the UI-audit workflow; bans
`downloadUtil.downloads` / `getDownload(` reads, `Download.STATE_*` outside `DownloadUtil.kt`, and
`Icon.Download(`) + ui-audit **R13** (`ui/`). When touching downloads run both and add pure tests next to
the resolver/menu logic (the manager/playback layer would need Robolectric, which the project lacks - say
so rather than skip silently).

### tests/ - the hard-data streaming harness

Node ≥20 scripts that reproduce the app's *exact* stream path (same `/player` request as
`InnerTube.kt`, same cipher in jsdom, same poTokens) against the live CDN. Setup: `npm ci --prefix
tests` once (`tests/node_modules` is gitignored) and `innertube_cookie.txt` at the repo root (a logged-in
session; **gitignored**, never commit). Methodology + the symptom-indexed runbook: `tests/README.md`,
`tests/INVESTIGATION.md` - read them first when streaming breaks.

- Run one: `node tests/cipher.mjs`, `node tests/validate-player-config.mjs <hash>`,
  `node tests/web-remix-stream.mjs`; pin a player with `PLAYER_HASH=<hash>`.
- The harness mirrors app constants on purpose: when `YouTubeClient.kt` / `PoTokenGenerator.kt` change,
  update `clients.mjs` / `potoken.mjs`. Player configs are NOT mirrored - `tests/player-configs.mjs`
  reads the bundled `player_configs.json` (needs the cipher submodule).
- Offline loader tests: `node --test tests/player-configs.test.mjs` (incl. the cross-language parity
  fixtures shared with the cipher repo).
- **`tests/search/`** ports the app's ONE remaining InnerTube search function, `YouTube.search(filter)`
  (used by `RecognitionResolver`, Android Auto voice search, `AddToPlaylistDialogOnline`):
  `node tests/search/run.mjs [query...]` reports strict-deserialization breaks, parser drops and empty
  results; `node --test tests/search/self-test.mjs` proves the checker (no network). The app sends no
  visitorData/cookie/auth (`sendVisitorData = false`); the harness still sends visitorData (known drift).
  Keep the strict-field table in `tests/search/schema.mjs` in sync with innertube model nullability.
  Details: `tests/search/README.md`.

### Modules & app layout

- **`:app`** (`com.jtech.zemer`) - single-activity Compose UI, Hilt DI (`App.kt`, modules in `di/`),
  Media3. `MainActivity` + `NavigationBuilder.kt` host the nav graph; `MusicService` (a
  `MediaLibraryService`) owns ExoPlayer, bridged to the UI by `PlayerConnection`, queues in
  `playback/queues/`. State: Room (`db/MusicDatabase.kt`, `song.db`) + DataStore (`utils/DataStore.kt` -
  cookie / visitorData / dataSyncId and all settings). Content filtering: `sync/` + `utils/SyncUtils.kt`.
  Offline search backup: `offline/` (store under `filesDir/subset/`). Downloads:
  `MediaStoreDownloadManager` / `MediaStoreDownloadService`. Crash telemetry is Firebase Crashlytics via
  `utils/CrashReportingTree.kt` (Timber DEBUG+ → breadcrumbs, `reportException()` → non-fatals) - report
  errors via `reportException()`/`Timber`, never `printStackTrace`. `App.kt` sets custom keys `commit` and
  `run_number` because nightly and stable builds share one versionCode/versionName.
- **`:innertube`** (`com.metrolist.innertube`) - the Ktor InnerTube client (requests, auth context,
  renderer parsers) and the `YouTubeClient` definitions. The cipher is the single sts/decipher source (no
  NewPipe extractor - don't reintroduce it).
- Lyrics provider clients live in `:app` (`lyrics/lrclib/`, `lyrics/simpmusic/`), not separate modules.
- **`cipher`** - see "Cipher / player rotation" above.

## Documentation

`handoff-docs/...` paths cited in this file are the app↔server contract docs; they live outside this
repo (the maintainers' handoff-docs folder).

- `docs/generate.py` regenerates `docs/reference/{kotlin-files,non-kotlin-files,resource-index}.md`,
  `docs/build-release.md`, the `### Counts` / inventory tail of `docs/repository-map.md`, and the
  `<!-- generated:NAME -->` … `<!-- /generated:NAME -->` regions inside `docs/app/{README,database,
  playback,preferences-sync-auth,viewmodels}.md`, `docs/ui/README.md` and `docs/innertube/README.md`
  (`REGION_DOCS`). Idempotent; needs PyYAML for `build-release.md`. **Never hand-edit generated files or
  regions** - change the source or the generator.
- `.github/workflows/docs-regenerate.yml` reruns it on every push to `main` and commits changes
  (`[skip ci]`); running `python3 docs/generate.py` before a commit is still good practice.
- Everything else (this file, `docs/ui/standards.md`, the feature READMEs, the prose around generated
  regions) is hand-authored and drifts unless updated with the code.

## Verifying your changes

- **Build both** `:app:assembleDebug` and `:app:assembleRelease` (release catches R8/shrink breakage).
- **Streaming / cipher / poToken changes** must be proven with the `tests/` harness against the live CDN (HTTP 206 / whole-song drain), and ideally confirmed on-device via the `YTPlayerUtils` logcat (`Playback: client=…, itag=…`).
- **UI changes** must comply with `docs/ui/standards.md` (the UI rulebook - Material 3 standard, design tokens, shared `Dialog.kt` dialogs, shared grouped-list components `Material3SettingsGroup`/`Material3MenuItem` per section 11) and stay 100% D-pad navigable - any new row/list component must carry the `.focusable()` + focus-border treatment, since upstream (Metrolist) rows omit it. Update the doc when a rule changes. Run `bash scripts/ui-audit.sh` - it ratchets sections 5, 7, 8, 11 and the R12-R26 reuse rules (listed in `scripts/ui-audit.sh`) (no *new* hardcoded user-facing strings, raw `AlertDialog`s, raw font sizes, hardcoded hex colors, or raw `ListItem(` action rows under `ui/menu/`; strings and dialogs are baselined at zero, menus build from `Material3MenuGroup`).
