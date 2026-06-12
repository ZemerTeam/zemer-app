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

### tests/ — the hard-data streaming harness

Node ≥20 scripts (deps vendored in `tests/node_modules`, no install needed) that reproduce the app's *exact* stream path (same `/player` request as `InnerTube.kt`, same cipher run in jsdom, same poTokens) against the live CDN — so playback is measured, not guessed. Needs `innertube_cookie.txt` at the repo root (a dumped logged-in session; **gitignored**, never commit).

- Run one: `node tests/cipher.mjs` (live player health), `node tests/validate-player-config.mjs <hash>`, `node tests/web-remix-stream.mjs`. Pin a player with `PLAYER_HASH=<hash>`.
- `tests/README.md` + `tests/INVESTIGATION.md` are the methodology and the symptom-indexed runbook — read them first when streaming breaks.
- The harness mirrors app constants on purpose; when `YouTubeClient.kt` / `PoTokenGenerator.kt` change, update the matching mirror (`clients.mjs` / `potoken.mjs`). Player configs are **not** mirrored — `tests/player-configs.mjs` reads the same `player_configs.json` the app bundles (requires the cipher submodule checked out; if missing, scripts fail with an actionable message).
- Loader unit tests (no cookie or network needed): `node --test tests/player-configs.test.mjs` — validation rules, collision rejection, the `config-covers.mjs` CLI, and the cross-language parity fixtures shared with the cipher repo's Kotlin tests.

### Modules & app layout

- **`:app`** (`com.jtech.zemer`) — single-activity Jetpack Compose UI, Hilt DI (`App.kt` `@HiltAndroidApp`, modules under `di/`), Media3. `MainActivity` + `NavigationBuilder.kt` host the Compose nav graph; `MusicService` (a Media3 `MediaLibraryService`) owns ExoPlayer and is bridged to the UI by `PlayerConnection`, with `playback/queues/` implementations. State is Room (`db/MusicDatabase.kt`, `song.db`) + DataStore preferences (`utils/DataStore.kt` — holds the auth cookie / visitorData / dataSyncId and all settings). Content-filtering (whitelist, KidZone) lives in `sync/` + `utils/SyncUtils.kt`. Downloads via Media3 `ExoDownloadService` plus a MediaStore path. Crash/error telemetry is Firebase Crashlytics: `utils/CrashReportingTree.kt` (planted in `App.kt`) turns every Timber log (DEBUG+) into a breadcrumb and `reportException()` calls into non-fatal issues — so report errors via `reportException()`/`Timber`, never `printStackTrace`; release CI uploads R8 mappings and native symbols automatically.
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
- **UI changes** must comply with `docs/ui/standards.md` (the UI rulebook — Material 3 standard, design tokens, shared `Dialog.kt` dialogs) and stay 100% D-pad navigable; update the doc when a rule changes. Run `bash scripts/ui-audit.sh` — it ratchets sections 5, 7 and 8 (no *new* hardcoded user-facing strings, raw `AlertDialog`s, raw font sizes, or hardcoded hex colors; strings and dialogs are baselined at zero).
