# tests/ — YouTube Music streaming harness (terminal, hard data)

Node scripts that reproduce the app's streaming pipeline **exactly** (same `/player` request,
same cipher, same poToken minting - but not yet the app's token slots, see Findings) so playback is
measured against the live CDN without building the APK.

All scripts run from the repo root or `tests/`. Node >= 20. Deps (`bgutils-js`, `jsdom`,
`youtubei.js`) are declared in `tests/package.json` with a lockfile (`tests/package-lock.json`);
`tests/node_modules` is **not** vendored (it is gitignored), so install once with
`npm ci --prefix tests`.

> **When streaming breaks** (player rotation, pot scheme change, a client stops working), see
> **[`INVESTIGATION.md`](./INVESTIGATION.md)** — the symptom-indexed runbook. SABR scripts
> (`sabr-*.mjs`) are documented in `docs/sabr/README.md`.

---

## Setup

### 1. Credentials — `innertube_cookie.txt` (gitignored)

Drop the dumped session at the repo root as `innertube_cookie.txt`:

```
***INNERTUBE COOKIE*** =HSID=...; SAPISID=...; SID=...; __Secure-1PSID=...; ...
***VISITOR DATA*** =<your-visitor-data, e.g. Cgs...%3D%3D>
***DATASYNC ID*** =
***ACCOUNT NAME*** =
***ACCOUNT EMAIL*** =
***ACCOUNT CHANNEL HANDLE*** =
```

`cred.mjs` parses this. It is **gitignored** (`innertube_cookie.txt`, `*.cookie.txt`,
`po_token.json`, `potoken.json`) — these are live Google account cookies, never commit them.

Overrides (take precedence over the file): `YT_COOKIE`, `YT_VISITOR_DATA`, `YT_DATASYNC_ID`,
`COOKIE_FILE`, or `CRED_URL` (remote worker, only used if nothing local is found).

### 2. Test song

Default video is `JTF9fLJvniI` (long enough to cross the 1-MiB pot wall). Pass a different id as
the first CLI arg or via `VIDEO_ID`.

---

## Scripts

| script | what it does |
|---|---|
| `cred.mjs` | Loads cookie/visitorData/dataSyncId from `innertube_cookie.txt` (+ env overrides). |
| `cipher.mjs` | Faithful Node port of the app's **Zemer cipher** (sig deobfuscation + n-transform + STS). Fetches the **same** `base.js` (iframe_api -> `player_ias.vflset/en_GB/base.js`), injects the **same** per-player sig call expression + n-transform class, looked up by player hash in `cipher/library/src/main/assets/player_configs.json`, into the IIFE, and runs it in jsdom. Byte-identical to `CipherWebView`. |
| `potoken.mjs` | BotGuard **poToken** minter (`bgutils-js` + jsdom), request key `O43z0dpjhgX20SCx4KAo`. Mints the streaming token bound to visitorData and the player token bound to videoId — the **pre-fix** mapping (harness drift); the app appends the videoId-bound pot, so use `URL_POT=player` to reproduce its media URL (`web-remix-stream.mjs`'s `/player` request still carries the videoId token; see Findings). |
| `web-remix-stream.mjs` | Reproduces the WEB_REMIX **1-MiB drop + seek** failure via the app's exact resolve path, then exercises the URL like ExoPlayer (sequential range chunks on fresh connections, seek, one open GET, re-resolve, pot-variant probe) + a (retired-)IOS control from `clients-retired.mjs`. |
| `pot-probe.mjs` | The **definitive poToken-binding matrix**: request-pot × url-pot × {none/videoId/visitorData-raw/visitorData-enc}, fetched past the 1-MiB window. |
| `client-fulldownload.mjs` | Drains the **whole** file per client to show which clients actually deliver a full song. Defaults to the app's main + fallback clients **plus `MWEB`** (harness drift: the app removed MWEB); `CLIENTS=A,B` to subset. |
| `sts-mismatch.mjs` | Regression test for **STS/cipher player coherence**: `/player` with the pinned player's own STS must stream past the wall; another live generation's STS 403s (why `CipherDeobfuscator.signatureTimestamp()` feeds `YTPlayerUtils`). |
| `video-qualities.mjs` | The **beyond-720p quality ladder** prover: enumerates every video quality (progressive muxed + adaptive video-only, mirroring `VideoQualityLogic.ladderFormats`) plus the audio merge partner, resolves each URL the app's exact way, and per rung (high→low) verifies initial 206, a fresh-connection sweep past the 1-MiB pot wall, a 75% seek, and a **full drain to EOF** (the download proof). PASS/FAIL exit — can gate. |
| `clients.mjs` / `clients-retired.mjs` | The live client mirror of `innertube/.../models/YouTubeClient.kt` (keep in sync!; it still carries an `MWEB` def the app removed) / the RETIRED client defs + dead-verdicts. |
| `run.mjs`, `full-stream.mjs`, `retest-web.mjs` | Older player-endpoint probes (kept for reference). |

### Run them

```bash
node tests/cipher.mjs                 # self-test: live player hash, STS, sig/n working?
node tests/potoken.mjs JTF9fLJvniI    # mint the token pair, print bindings/lengths
node tests/web-remix-stream.mjs                      # reproduce the wall (default URL_POT=streaming = pre-fix binding)
URL_POT=player node tests/web-remix-stream.mjs       # the app's current URL (videoId-bound pot)
node tests/pot-probe.mjs                             # the binding matrix
node tests/client-fulldownload.mjs                   # per-client whole-song delivery
node tests/sts-mismatch.mjs                          # STS/cipher player coherence (403 regression)
node tests/video-qualities.mjs <videoId>             # every quality rung: stream + full-download proof
MODE=stream LABELS=2160p,1080p node tests/video-qualities.mjs <videoId>  # subset, skip full drains
```

Useful env: `URL_POT=streaming|player|none`, `CHUNK=262144`, `COVER_SECONDS=90`,
`PLAYER_HASH=9d2ef9ef` (pin a known-configured player so a freshly-rotated player can't break the
cipher mid-test — the matching STS is sent in the `/player` request, keeping decipher valid).

> Player configs come from `cipher/library/src/main/assets/player_configs.json` (loaded by
> `tests/player-configs.mjs`) — the SAME file the app bundles and fetches remotely from cipher
> `master` at runtime, so harness and app cannot drift. When YouTube rotates to a hash not in it,
> `cipher.mjs` throws — derive + validate with `node tests/validate-player-config.mjs <hash>`
> (prints a paste-ready JSON entry), add it to the JSON, push cipher `master` → deployed apps
> self-heal without an APK update. Pin a known hash (`PLAYER_HASH=`) to keep testing meanwhile.

---

## Findings the app's code depends on

### The 1-MiB wall and the videoId-bound pot

googlevideo serves the first **1 MiB** of a stream free; past it, for WEB_REMIX with no URL pot or a
visitorData-bound one, every *new connection* 403'd in the measured runs below (the next range chunk,
a seek, a fresh open GET). That one failure is both the "drops after ~45-60 s" and the "seek triggers a
fallback" symptom. A lone cold range past 1 MiB can still 206 (seen with MWEB, `MWEB-INVESTIGATION.md`),
so only a sequential drain proves a URL. `pot-probe.mjs`, fetching past 1 MiB with everything else
fixed:

```
                       @1 MiB   @2 MiB
url pot = none          403      403
url pot = videoId       206      206   -> full file downloads
url pot = visitorData   403      403   (raw "==" and url-encoded "%3D%3D" both fail)
```

**The stream URL's `pot=` must be bound to the videoId**, independent of the `/player` request's pot.
This is why `PoTokenGenerator.getWebClientPoToken` (cipher) returns `streamingDataPoToken = videoPot`
(appended as `&pot=`) and `playerRequestPoToken = sessionPot` (visitorData-bound, sent in `/player`).
`tests/potoken.mjs` still mints the swapped (pre-fix) mapping, so `URL_POT=player` reproduces the
app's current media URL and the default `URL_POT=streaming` reproduces the bug. Its `/player` request
still sends the videoId-bound token (the app sends the visitorData one), so `web-remix-stream.mjs` is
not full token parity; only `pot-probe.mjs`'s `req=visRaw` × `url pot=video` cell carries both of the
app's tokens.

### Which clients deliver a WHOLE song

Measure with `client-fulldownload.mjs` (a full drain). A `Range: bytes=0-1` check is **not** evidence
of playability: IOS passed it yet 403s past 1 MiB.

| client | full song? | needs |
|---|---|---|
| **VISIONOS (1.02) + VISIONOS_0_1** | yes | nothing - direct url, no pot/cipher/BotGuard (the most reliable fallback) |
| **WEB_REMIX / WEB_CREATOR** | yes | videoId-bound pot + sig/n cipher |
| **TVHTML5_SIMPLY** (clientId 75) | yes | videoId-bound pot + sig/n cipher; the "TVHTML5" toggle governs it |

App order: `MAIN_CLIENT` (`WEB_REMIX`) then `YTPlayerUtils.ALL_FALLBACK_CLIENTS`:
`WEB_REMIX -> VISIONOS -> VISIONOS_0_1 -> WEB_CREATOR -> TVHTML5_SIMPLY` (user-disabled families are
filtered out into `STREAM_FALLBACK_CLIENTS`).

Proven-dead clients were removed from the app; their defs + verdicts live in `clients-retired.mjs` so the
probes still run: the ANDROID_VR family (pre-1.65 variants are version-bot-gated; 1.65.10 resolves a URL
but 403s after 0 bytes), MOBILE/ANDROID (400 with auth, SABR-only without), IOS/IPADOS
(403 past the wall), ANDROID_CREATOR, TVHTML5_SIMPLY_EMBEDDED_PLAYER (server-killed), the 7.x TVHTML5
(SABR-only). WEB is dropped as a stream fallback (SABR-only) but its def stays in `clients.mjs`
(the app keeps it for other calls). `tv_downgraded` was also probed dead (no def kept). MWEB was removed too - see `MWEB-INVESTIGATION.md`. Re-add one only
after a whole-song drain proves it alive.
