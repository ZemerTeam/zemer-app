# Remote stream-client config — full plan

> **Post-plan update (2026-09-01, at merge time):** `ANDROID_VR_1_65_10` (403s after 0 bytes on a
> whole-song drain, removed from main 2026-08-25) and `MWEB` (attestation-walled on both transports,
> removed with the SABR work 2026-09) were proven dead before this landed, so the shipped table,
> the compiled floor, the families list and the legacy-key migration cover FOUR families:
> WEB_REMIX, VISIONOS, WEB_CREATOR, TVHTML5. The SABR roster (added on main meanwhile) is table data too:
> entries carrying a `sabr` object, with per-family `streamSabrFamily_<id>` toggles. The chain/family
> lists below are the plan as written.

> **Status 2026-08-16: IMPLEMENTED** on `feat/remote-stream-clients` (zemer-app) +
> `feat/remote-stream-clients` (zemer-cipher). PR 1 (flag-ification), PR 2 (parser/store/asset)
> and PR 3 (end-to-end wiring, dynamic settings, migration, harness loader + parity + deploy
> gate) are all in; the living rulebook is now the "Remote stream-client config" section of
> AGENTS.md. Remaining before merge: on-device verification pass + release build via CI.

A cipher-style remote config for the streaming-client chain: client definitions, behavior flags,
and fallback order become a JSON file pushed to the zemer-cipher repo, so YouTube killing/rotating
a client is fixed on deployed apps in minutes with no APK release — exactly the recovery model
`player_configs.json` already proves.

**Settled decisions (owner, 2026-08-16):**
- NO innertube repo extraction. `:innertube` stays a Gradle module; compiled Kotlin can't be
  hot-loaded, so a repo split buys nothing remotely.
- The config's home and deploy channel is the **existing zemer-cipher repo** (raw-URL fetch,
  push-is-deploy, submodule-bundled offline default — all already in place).
- DATA not CODE (the cipher's load-bearing safety rule): the config may add/remove/reorder clients,
  change versions/UAs/flags, and pick a protocol from a compiled-in enum. It must NEVER carry
  request logic, headers, endpoints, or anything evaluated as code. The API origin stays hardcoded.

---

## 1. Current state (what this replaces)

| Thing | Where it is hardcoded today |
|---|---|
| Client definitions (name/version/clientId/UA/device fields/flags) | `innertube/.../models/YouTubeClient.kt` companion constants |
| Main client (`WEB_REMIX`) | `YTPlayerUtils.MAIN_CLIENT` |
| Fallback order (VISIONOS → VISIONOS_0_1 → WEB_CREATOR → ANDROID_VR_1_65_10 → TVHTML5_SIMPLY → MWEB) | `YTPlayerUtils.ALL_FALLBACK_CLIENTS` |
| "Web client" (cipher + n-transform + pot) vs "direct URL as-is" split | `YTPlayerUtils.clientNeedsNTransform()` — `useWebPoTokens` OR a hardcoded name list |
| WEB_REMIX skip-HEAD-validation rule | `clientName == "WEB_REMIX" && clientIndex == -1` in the resolution loop |
| Ladder/rung-URL seeding web-only rule | `clientNeedsNTransform(successClient)` |
| Stream Sources toggles | Six hardcoded `StreamSource*Key` prefs + hardcoded `MusicService` mapping (family → clientName set) + fully hardcoded `StreamSourceSettings.kt` rows/groups/chip order |
| Harness mirror | `tests/clients.mjs` `CLIENTS` array (comment: keep in sync by hand) |

Every 2026-08-15 change (add VR 1.65.10, bump VISIONOS, remove 8 dead clients, re-add MWEB,
reorder) was pure data. Under this system each would have been a JSON push.

## 2. Scope

**In scope (remote-governed):** the DIRECT-playback stream-resolution chain in
`YTPlayerUtils.playerResponseForPlayback` — which clients, their definitions, their order, which is
main, per-client behavior flags. Both streaming and download resolutions (same chain).

**Out of scope (stays compiled):**
- Browse/search/account InnerTube usage of `WEB_REMIX`/`WEB` (`playerResponseForMetadata`,
  `registerPlayback` watch-time beacons, `next`/transcript). Version skew between a remote
  stream WEB_REMIX and the compiled browse WEB_REMIX is harmless (separate requests).
- RELAY mode (bypasses the client chain entirely; the Stream Sources screen already hides the
  client list under RELAY).
- The cipher/poToken machinery itself (already remotely governed by `player_configs.json`).
- A genuinely NEW protocol (SABR, new attestation, OAuth) = new `protocol` enum value = code + APK.
  Exact cipher parallel: new hash = remote, new scheme = code.

## 3. The config file

**Repo/URL:** `zemer-cipher` `master`:
`library/src/main/assets/stream_clients.json`, fetched from
`https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/stream_clients.json`.
Bundled in the APK as the offline default via the existing submodule (same file, same path — the
asset ships inside the cipher library AAR exactly like `player_configs.json`).

**Shape (schemaVersion 1):**

```json
{
  "schemaVersion": 1,
  "clients": [
    {
      "key": "WEB_REMIX",
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20260213.01.00",
      "clientId": "67",
      "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
      "protocol": "web_cipher_pot",
      "family": "WEB_REMIX",
      "loginSupported": true,
      "skipHeadValidation": true
    },
    {
      "key": "VISIONOS",
      "clientName": "VISIONOS",
      "clientVersion": "1.02",
      "clientId": "101",
      "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
      "protocol": "direct",
      "family": "VISIONOS",
      "osName": "visionOS",
      "osVersion": "26.5.23O471",
      "deviceMake": "Apple",
      "deviceModel": "RealityDevice17,1"
    }
  ],
  "families": [
    { "id": "WEB_REMIX",  "title": "YouTube Music Web", "group": "web" },
    { "id": "VISIONOS",   "title": "visionOS",          "group": "native" },
    { "id": "ANDROID_VR", "title": "Android VR",        "group": "native" },
    { "id": "TVHTML5",    "title": "TVHTML5",           "group": "web" },
    { "id": "WEB_CREATOR","title": "YouTube Studio",    "group": "creator" },
    { "id": "MWEB",       "title": "Mobile Web",        "group": "web" }
  ]
}
```

**Semantics:**
- `clients` is ORDERED. Entry 0 is the main client; the rest are the fallback chain in order.
  (Second-chance entries — VISIONOS 1.02 then 0.1 — are just adjacent entries sharing a family.)
- `key` is the unique entry id (stable across version bumps of the same slot is NOT required —
  keys are per-entry, families are the stable identity).
- `protocol` picks a COMPILED-IN behavior bundle (see §4). Unknown value → **skip the entry**
  (forward compat: a future file can list a `sabr` client; old apps drop it, new apps use it).
- `family` keys the user toggle + settings grouping. Several entries may share one family (the
  VISIONOS pair; a future ANDROID_VR second chance). `families[]` carries display metadata; a
  client whose family has no `families[]` row gets a fallback title (the family id itself) in the
  "web" group — never dropped.
- Optional per-entry fields: `osName`, `osVersion`, `deviceMake`, `deviceModel`,
  `androidSdkVersion`, `loginSupported`, `loginRequired`, `isEmbedded`, `skipHeadValidation`
  (all defaulting to today's `YouTubeClient` defaults / false).
- Deliberately ABSENT: any URL/host/endpoint, any header dictionary, any script/expression, any
  per-client timeout/retry knobs. Origin, referer, SAPISIDHASH minting, poToken binding, cipher
  invocation all stay in Kotlin, selected only via `protocol`.

**Validation rules (file-level reject → keep last-good, exactly the cipher's wholesale rule):**
- Malformed JSON / root not an object.
- `schemaVersion` missing, non-int (string "1" rejected — parity with the cipher parser's
  primitive rule), ≤ 0, or > supported.
- `clients` missing / not an array / **zero usable entries after per-entry skips** (the
  never-zero-clients invariant starts at the parser).
- Duplicate `key`.
- Entry 0 (main) skipped/invalid — the file's most load-bearing row must be sound. (If YouTube
  ever kills WEB_REMIX, the pushed file simply promotes another entry to slot 0.)

**Per-entry skip (reported, non-fatal — mirrors `skippedEntries`):**
- Unknown `protocol` (forward compat), missing/invalid required field, regex violations below.

**Field shapes (locked, all unit-tested):**
- `key`, `family`: `^[A-Z0-9_]{1,32}$`
- `clientName`: `^[A-Z0-9_]{1,32}$` (it is sent as a JSON body value and the `c=` beacon param)
- `clientId`: `^[0-9]{1,4}$` (sent as the `X-YouTube-Client-Name` header)
- `clientVersion`, `osVersion`, `androidSdkVersion`: `^[A-Za-z0-9._-]{1,32}$`
- `userAgent`, `deviceMake`, `deviceModel`, `osName`: printable ASCII 0x20–0x7E, no CR/LF
  (header-injection guard), length caps (UA ≤ 300, others ≤ 64)
- booleans strictly JSON booleans

## 4. `protocol` — the compiled behavior bundles

Two values at launch, replacing today's name-keyed special cases:

| protocol | Meaning (all logic stays in Kotlin) |
|---|---|
| `web_cipher_pot` | The web path: sig decipher via the cipher submodule, n-transform, `pot=` append, `serviceIntegrityDimensions.poToken` in the /player body, `useSignatureTimestamp` true. Eligible to seed the video quality ladder / rung URLs / merge audio. Failing validation triggers the cipher self-heal (`onStreamRejected`). |
| `direct` | Direct-URL clients (VISIONOS/ANDROID_VR): URL used AS-IS — no sig, no n-transform, no pot (web transforms CORRUPT it). No STS in the body. Never seeds the ladder. |

Derived facts (not config fields): `useSignatureTimestamp` ≡ `useWebPoTokens` ≡
(protocol == web_cipher_pot). Today's `YouTubeClient` keeps the two booleans (innertube's request
builder reads them) but the app maps them FROM the protocol — one source of truth.

`skipHeadValidation` stays a separate flag (it is a WEB_REMIX-specific CDN quirk, not a protocol
property), honored only for the MAIN slot + non-download + not-in-`webRemixFailedIds`, exactly
today's condition with the name test replaced by the flag.

## 5. Kotlin architecture

### 5.1 Cipher library (zemer-cipher repo) — the store + parser

- **`StreamClientParser`** (pure JVM, zero Android imports — the `PlayerConfigParser` twin):
  `parse(jsonText): ParseResult(Success(config: StreamClientConfig, skippedKeys) | Failure(reason))`.
  `StreamClientConfig` = ordered `List<StreamClientDef>` + `Map<family, FamilyMeta>`.
  `StreamClientDef` is a cipher-library data class (the cipher lib has NO innertube dependency;
  the app maps defs → `YouTubeClient`).
- **`StreamClientStore`** (the `PlayerConfigStore` twin): bundled asset load at `initialize()`,
  cached-overlay, TTL(6h)+ETag conditional fetch, startup refresh, atomic temp+rename writes,
  memory-first apply, `withinWindow` clock-skew-safe stamps, cooldown that only arms when the
  server was reached, `configEpoch`.
  - **Divergence from the cipher store — wholesale REPLACE, not per-key merge.** Removal and
    reordering are primary use cases; merging bundled+remote per key would resurrect removed
    clients and scramble order. A VALID remote file replaces the table entirely; anything invalid
    keeps last-good (cached remote if present, else bundled). Cache-load failure deletes cache
    body + ETag together (the 304-lock defense, verbatim).
  - **Failure-triggered refresh:** `refreshAfterResolutionFailure()` — called (fire-and-forget,
    own scope, mirroring `cipherRefreshScope`) when `playerResponseForPlayback` exhausts ALL
    clients. Own cooldown stamp (5 min), shared single-flight mutex. This is the mid-session
    self-heal: YouTube kills a client → all-fail → fetch → corrected table → next resolution works.
  - Shared plumbing: reuse `ZemerCipher.httpClient` and `PlayerConfigStore.writeAtomic`. Where
    clone-vs-generalize is a judgment call, prefer extracting the small shared helpers
    (meta read/write, atomic write, `withinWindow`) into one internal object over duplicating —
    but do NOT force a premature generic RemoteConfigStore abstraction if it obscures either
    store's specific rules (per-key merge vs wholesale replace, forceRefresh(hash) vs
    resolution-failure trigger).
- Cache files: `stream_clients_remote.json` / `stream_clients_remote.meta` in the same
  `cipher_cache` dir. MUST NOT start with `player_` (PlayerJsFetcher purge rule) — the existing
  naming comment in `PlayerConfigStore` gains these two names.
- Bundled asset: `library/src/main/assets/stream_clients.json` seeded with EXACTLY today's
  compiled chain (WEB_REMIX main; VISIONOS, VISIONOS_0_1, WEB_CREATOR, ANDROID_VR_1_65_10,
  TVHTML5_SIMPLY, MWEB; families matching the six current toggles).

### 5.2 innertube module

- `YouTubeClient` stays (the request builder's input) and gains nothing remote-aware. The
  companion constants REMAIN for the out-of-scope users (WEB for next/transcript, WEB_REMIX for
  browse/metadata/beacons) — clearly commented that the STREAM chain no longer reads them.
- No new innertube ↔ cipher dependency: the app does the `StreamClientDef → YouTubeClient` map.

### 5.3 App — YTPlayerUtils refactor

- **Pure chain builder** (new, JVM-tested):
  `StreamClientChain.resolve(config, disabledFamilies, isLoggedIn): Chain(main, fallbacks)` —
  encodes: family-disable filtering, main promotion when slot 0's family is disabled
  ("All stream sources are disabled" only when literally nothing remains), loginRequired
  filtering happens in the loop (unchanged — it logs per-skip). This extracts the currently
  untestable `mainClient`/`fallbackClients` head of `playerResponseForPlayback`.
- `playerResponseForPlayback` snapshots `StreamClientStore.config()` ONCE at entry (a resolution
  must run against one consistent table; the next resolution picks up changes — instant apply, no
  epoch-rebuild machinery needed since there is no WebView analog).
- Name-keyed special cases become flag/protocol reads:
  - `clientNeedsNTransform(client)` → `client.protocol == WEB_CIPHER_POT` (drop the name list).
  - The WEB_REMIX HEAD-skip → `client.skipHeadValidation && clientIndex == -1 && !forDownload
    && videoId !in webRemixFailedIds` (rename `webRemixFailedIds` → `mainClientFailedIds`; the
    ExoPlayer-403 marker is about the main slot, whoever occupies it).
  - Ladder/rung seeding + explicit-itag web-only rule → protocol test (same sites).
- `prewarmPoToken` reads the CURRENT main client's protocol from the store snapshot.
- `disabledStreamClients` becomes `disabledStreamFamilies` (see §6) — the MusicService collector
  maps prefs → family ids, and the chain builder maps families → entries. The hardcoded
  family→names sets in MusicService (e.g. TVHTML5 → {TVHTML5, TVHTML5_SIMPLY}) die; the config's
  `family` field IS that mapping.
- `ZemerCipher`/app init: `StreamClientStore.initialize(context)` +
  `scheduleStartupRefresh()` alongside the existing `PlayerConfigStore` calls in `App.kt`
  (cheap synchronous asset load, same guarantee: a table exists before first playback).

## 6. Settings UI + preference migration

- **Dynamic screen.** `StreamSourceSettings` renders from the store's config:
  - Chip row = enabled families in CONFIG order (main first) — the "displayed order must match
    the array" rule becomes structural instead of a comment.
  - One `SwitchPreference` per family, grouped by `families[].group` (`web` / `native` /
    `creator` — unknown group → appended as its own section), titled from `families[].title`.
    Config-carried titles are server-driven display strings (the genres/home-rows precedent);
    the per-client localized description strings are DROPPED in favor of one generic per-group
    description — a remotely-added family cannot have a translated bespoke description, and
    stale bespoke text under a changed client is worse than generic text. (Owner call; see §12.)
  - Screen recomposes from a `StateFlow`/snapshot of the store config (collectAsState — no
    main-thread store reads beyond the in-memory map).
- **Preferences.** New dynamic keys `booleanPreferencesKey("streamSourceFamily_<familyId>")`,
  default true (absent = enabled — a remotely-added family is ON for everyone until toggled).
- **One-time migration** (in the MusicService collector's first pass or a small
  `StreamSourcePrefsMigration` helper, unit-tested): legacy key → family id:
  `streamSourceWebRemix→WEB_REMIX`, `streamSourceTVHTML5→TVHTML5`,
  `streamSourceAndroidVR→ANDROID_VR`, `streamSourceVisionOS→VISIONOS`,
  `streamSourceWebCreator→WEB_CREATOR`, `streamSourceMWEB→MWEB`. Copy only values explicitly
  `false` (default-true needs no write), stamp a `streamSourcePrefsMigratedKey`, leave legacy keys
  in place (harmless, old-version rollback safe).
- **Relay interplay unchanged:** the client list is still hidden under RELAY; the relay group
  logic is untouched.

## 7. Harness + tooling (tests/)

- `tests/clients.mjs` becomes a LOADER: parse the same `stream_clients.json` the app bundles
  (via the cipher submodule path, like `tests/player-configs.mjs` does for player configs) and
  export the same `CLIENTS`/order. The hand-maintained mirror comment dies. Scripts that
  reference retired/experimental clients keep `clients-retired.mjs` (untouched).
- **Parity fixtures** (the config-parity precedent): a shared fixture dir in the cipher repo
  (`library/src/test/resources/stream-clients-parity/`) with accept/reject verdict files pinned
  byte-for-byte by BOTH the Kotlin `StreamClientParserTest` and a new Node
  `tests/stream-clients.test.mjs` — a validation-rule change must touch both readers + fixtures
  or one suite goes red. Fixture cases: valid file, string schemaVersion, dup key, unknown
  protocol (skip, not reject), zero-usable (reject), invalid main (reject), CR/LF in UA (skip),
  unknown family (accept with fallback), future schemaVersion (reject).
- **The pre-push gate:** `node tests/client-fulldownload.mjs` (whole-song drain vs the live CDN)
  against every client in the CANDIDATE file before any push to cipher master — the 206-ground-
  truth rule. Add a small `tests/validate-stream-clients.mjs` wrapper: load candidate file →
  schema-validate (Node parser) → full-drain each entry → print PASS/FAIL table. This is the
  client analog of `validate-player-config.mjs`.
- **CI in zemer-cipher:** a workflow validating `stream_clients.json` on PR/push (schema +
  parity fixtures; the live-drain stays a manual pre-push step — CI runners' IPs get bot-gated,
  a fact already learned with the streaming harness).

## 8. Deploy workflow (runbook)

1. Edit `library/src/main/assets/stream_clients.json` in the zemer-cipher checkout.
2. `node tests/validate-stream-clients.mjs <path>` in zemer-app (schema + whole-song drain per
   client; needs `innertube_cookie.txt` for login-required clients).
3. Push zemer-cipher `master` — **that is the deploy**; devices converge within the 6h TTL, or
   minutes for anyone hitting the all-clients-failed refresh.
4. Bump the submodule pointer in zemer-app (push order: cipher first, then pointer — the
   existing rule) so bundled defaults stay fresh.
5. Observability: `tracking.zemer.io` play events already carry `client`
   (zemer-tracking-play-client-fields) — the dashboard's client split shows a pushed change
   taking effect / a dying client's share collapsing.

## 9. Failure-mode matrix (every angle)

| Failure | Behavior |
|---|---|
| Malformed/invalid remote JSON | File-level reject → keep last-good (cached, else bundled). Same as cipher. |
| GitHub down / 404 / network error | Keep table; cooldown NOT armed on pure network failure (`lastAttemptReachedServer`), so a rotation-while-offline retries on the next trigger. |
| Remote file removes ALL clients / all entries invalid | Parser rejects (zero-usable rule) → last-good stands. Config can never brick playback to zero clients. |
| Entry 0 (main) invalid | File rejected — a half-valid file that would silently promote a different main is refused wholesale. |
| Unknown `protocol` on a non-main entry | Entry skipped, logged — future clients coexist with old apps. |
| User disables every family | Runtime "All stream sources are disabled" error — pre-existing, user-caused, unchanged. |
| Config removes a family the user had toggled off | Its pref is orphaned (harmless); if re-added later the old choice still applies. |
| Corrupt cache body + surviving ETag | Cache+meta deleted together on load failure (304-lock defense, inherited). |
| Process death mid-cache-write | Atomic temp+rename (inherited). |
| Disk full | Memory-first apply; the fetched fix works now, refetch next start (inherited). |
| Clock stepped backward | `withinWindow` treats negative deltas as expired — no wedged cooldown/TTL (inherited). |
| Bad-but-valid push (a client that schema-passes but doesn't stream) | The resolution loop is ALREADY per-client fail-through; worst case = wasted attempts. The pre-push drain gate exists to prevent it; the all-failed refresh recovers if a later push fixes it. |
| Repo compromise | Same trust boundary as `player_configs.json` today (which is strictly worse: it feeds a WebView). Blast radius here is bounded by the DATA-not-CODE schema: attacker-controlled fields are header/body VALUES to a hardcoded origin. CR/LF locks prevent header injection; no URL/script fields exist. |
| Old APK + future schemaVersion | Rejected wholesale, keeps last-good — old apps ride bundled/cached until updated (cipher precedent). |
| First run offline | Bundled asset table (synchronous initialize). |
| Device synced once, then config host permanently unreachable (e.g. filter blocks GitHub) | 14-day staleness cap: the cached table is dropped past it and the BUNDLED table (which tracks APK updates) wins - a frozen cache can never mask newer bundled chains indefinitely. |
| Fetch races a resolution | Resolution snapshots the config once at entry; `@Volatile` swap; next resolution sees the new table. |
| Login-required client in a login-less session | Loop-level skip, unchanged (`loginRequired && !isLoggedIn`). |
| Remote bumps WEB_REMIX version | Stream chain uses it; browse/metadata keep the compiled constant (scope rule §2). Harmless skew. |

## 10. Security summary

- Nothing from the file is ever evaluated as code (unlike the cipher's JS, which needs the IIFE
  template defense). All values are JSON body fields / header values to the hardcoded
  `music.youtube.com` origin.
- Header-value injection blocked by the printable-ASCII/no-CRLF regexes; lengths capped.
- Auth material (cookie, SAPISIDHASH) is attached by compiled code, gated on `loginSupported`,
  and only ever sent to the hardcoded origin — a config cannot redirect it.
- `protocol` is a closed enum; unknown values are inert.
- The file cannot influence RELAY, the whitelist, tracking, or any non-streaming surface.

## 11. Work breakdown (PR sequence)

**PR 1 — zemer-app: flag-ification refactor (behavior-preserving, no remote anything).**
- `protocol` enum on `YouTubeClient` (or an app-side wrapper — decide in-PR; prefer the
  innertube field since `useSignatureTimestamp`/`useWebPoTokens` then derive from it).
- Replace `clientNeedsNTransform`'s name list, the WEB_REMIX HEAD-skip name test
  (→ `skipHeadValidation`), rename `webRemixFailedIds` → `mainClientFailedIds`.
- Extract the pure `StreamClientChain` builder + JVM tests (main promotion, family disable,
  all-disabled error).
- Prove zero behavior change: existing streaming works (debug build + on-device sanity), all
  unit tests, `tests/` harness spot-run.

**PR 2 — zemer-cipher: parser + store + bundled asset + parity fixtures.**
- `StreamClientParser` + `StreamClientStore` + `stream_clients.json` (today's chain verbatim).
- Kotlin unit tests (parser truth table, store cooldown/TTL/replace/cache rules — reuse the
  `cacheDirForTest` seam pattern) + the parity fixture set.
- Node side in zemer-app tests/: loader rewrite of `clients.mjs`, `stream-clients.test.mjs`,
  `validate-stream-clients.mjs`. (These land in PR 3's repo but are developed against PR 2.)

**PR 3 — zemer-app: wire it end to end.**
- Submodule bump to PR 2's cipher master.
- `YTPlayerUtils` reads the store snapshot; `App.kt` init; all-failed refresh trigger.
- Dynamic `StreamSourceSettings` + family prefs + migration + MusicService collector rewrite.
- `tests/` harness changes ride here.
- Docs: AGENTS.md streaming-pipeline + settings sections, `docs/generate.py` regen, this plan
  file updated to "shipped" or folded into docs.

Each PR: assembleDebug + assembleRelease (CI), `bash scripts/ui-audit.sh` where UI is touched,
full unit suites, and for PR 3 an on-device pass: play via each client (toggle the others off),
toggle migration from a v37-installed build, relay session untouched, Stream Sources D-pad
navigation. Zero-regressions mandate applies; the streaming pipeline is the danger zone — every
resolution-loop change is proven against the live CDN with the harness before merge.

## 12. Resolved questions (owner, 2026-08-16)

1. **Settings text: HYBRID.** The six known families keep their bespoke localized title +
   description strings via a compiled map keyed by family id; a remotely-added family shows the
   config's English `families[].title` with a generic per-group description.
2. **TTL: 6 hours** (cipher parity; breaking changes recover via the all-failed trigger).
3. **Kill switch: YES.** Optional per-entry `"enabled": false` — parsed as a skip (like unknown
   protocol), lets a client be benched while its entry/notes stay in the file. Subject to the
   same file-level rules (a file whose usable set is empty, or whose entry 0 is disabled, is
   rejected wholesale).
4. **Timing: whole stack now** — PRs 1-3 implemented back to back.
