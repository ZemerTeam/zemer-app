# Streaming investigation & runbook

How to diagnose streaming with hard data when YouTube changes something: songs drop, seeking fails,
a player rotates, or poTokens stop working. Companion to [`README.md`](./README.md) (quick reference +
the findings the app depends on).

---

## 0. Principles (why the harness exists)

1. **Hard data only.** Every claim is an HTTP status at a specific byte offset against the live
   googlevideo CDN, with the real logged-in cookie. Never reason from convention - the convention was
   wrong here (the stream URL wants a **videoId**-bound poToken, not the visitorData one yt-dlp/NewPipe use).
2. **Reproduce the app's EXACT path.** Same `/player` request as `InnerTube.kt`, same sig+n cipher as
   the `cipher` submodule (run in jsdom instead of an Android WebView), same poTokens as
   `PoTokenGenerator`. A test that diverges from the app proves nothing about the app.
3. **Isolate one variable at a time.** `pot-probe.mjs` holds the request constant and varies only the
   URL pot; `client-fulldownload.mjs` holds the video constant and varies only the client.

---

## 1. How the harness mirrors the app

The scripts are deliberate ports of specific app code; when the app changes, the mirror must change too (§6).

| App (Kotlin) | Harness (Node) | What it reproduces |
|---|---|---|
| `YouTube.cookie` / `visitorData` (session) | `cred.mjs` | the logged-in session, from `innertube_cookie.txt` |
| `InnerTube.kt` `player()` + `ytClient()` | `web-remix-stream.mjs` / `pot-probe.mjs` `playerRequest()` | the `/player` POST: context, `X-Goog-*` headers, `SAPISIDHASH`, `signatureTimestamp`, `serviceIntegrityDimensions.poToken` |
| `models/YouTubeClient.kt` | `clients.mjs` | client name/version/id/UA/flags (WEB_REMIX id 67, TVHTML5_SIMPLY id 75, …; retired clients in `clients-retired.mjs`) |
| `cipher/.../PlayerJsFetcher.kt` | `cipher.mjs` `fetchPlayerJs()` | iframe_api -> `player_ias.vflset/en_GB/base.js`, STS |
| `cipher/library/src/main/assets/player_configs.json` | `player-configs.mjs` reads the SAME file | per-player sig expression + n-trick class + STS + MD5 alias, looked up by player hash and injected by `cipher.mjs` |
| `cipher/.../CipherWebView.kt` (Android WebView) | `cipher.mjs` (jsdom) | injects exports into the base.js IIFE, calls `_cipherSigFunc` / `_nTransformFunc` |
| `cipher/.../CipherDeobfuscator.kt` | `cipher.mjs` `deobfuscateStreamUrl` / `transformNParamInUrl` | parse `s/sp/url`, apply sig, replace `n=` |
| `cipher/.../potoken/PoTokenGenerator.kt` + `PoTokenWebView.kt` | `potoken.mjs` (bgutils-js) | BotGuard mint, request key `O43z0dpjhgX20SCx4KAo`. `potoken.mjs` mints the **pre-fix** mapping (streaming pot <- visitorData); the app appends the videoId-bound pot, so `URL_POT=player` reproduces the app's URL |
| `YTPlayerUtils.playerResponseForPlayback()` | `web-remix-stream.mjs` `resolveAppUrl()` | full resolve: player -> findFormat -> sig -> n -> pot |
| `YTPlayerUtils.findFormat()` | `findFormat()` in the scripts | best audio by bitrate + a webm bias (+10240 when webm is allowed) -> itag 251 opus |
| ExoPlayer HTTP data source (`MusicService` data-source chain) | `fetchRange()` / `drainWhole()` | range GETs on fresh connections; seek = range at a far offset |

The Node cipher downloads the same `base.js` and runs it; only the JS host differs. `cipher.mjs`
reports an n-transform probe (`KdrqFlzJXl9EcCwlmEy` in, `nProbe.changed`) to confirm it works.

---

## 2. Setup

1. **Cookie.** Put the dumped logged-in session at the repo root as `innertube_cookie.txt`
   (gitignored - never commit it). Format in [`README.md`](./README.md) Setup §1. A stale cookie shows up as
   `playability != OK`; re-dump it from the account.
2. **Deps:** `npm ci --prefix tests` once (`tests/node_modules` is gitignored). Node >= 20.
3. **Test video:** default `JTF9fLJvniI` (long enough to cross the 1 MiB wall); override with argv[1]
   or `VIDEO_ID`.

Env knobs: `URL_POT=streaming|player|none`, `PLAYER_HASH=<hash>` (pin a player), `CHUNK`,
`COVER_SECONDS`, `YT_COOKIE`/`YT_VISITOR_DATA` (override the file).

---

## 3. The method (repeat it when the specifics change)

1. **Drive the URL like ExoPlayer, never trust a 2-byte check or a HEAD.** Sequential range chunks on
   fresh connections until something 403s (`web-remix-stream.mjs`). The first failure was at byte
   1,048,576; a seek (range at 75 %) hit the same wall - both symptoms, one cause.
2. **Check whether the wall is client-specific.** Even the IOS direct URL (no pot) 403'd past 1 MiB,
   so it is a googlevideo rule.
3. **Isolate the gate** with the full matrix (`pot-probe.mjs`: request-pot × url-pot ×
   {none/videoId/visitorData-raw/visitorData-enc}). Only url-pot=videoId served past the wall.
4. **Find where the app diverges** and fix the mapping (it was the token swap in
   `PoTokenGenerator.getWebClientPoToken`), then **verify on-device** (Runbook F).

---

## 4. Running each test (and reading the output)

### `cipher.mjs` - is the cipher alive on the current player?
```bash
node tests/cipher.mjs
```
Prints the live player hash, STS, and whether sig + n work (`nProbe.changed: true`). **Run this first
whenever streaming breaks** - if it throws "no cipher config for live player", YouTube rotated to a
player `player_configs.json` has no entry for (Runbook A).

### `potoken.mjs` - does BotGuard still mint?
```bash
node tests/potoken.mjs JTF9fLJvniI
```
Prints the tokens + lengths. If it errors, bgutils/BotGuard changed (Runbook D).

### `web-remix-stream.mjs` - reproduce drop/seek; verify a fix
```bash
node tests/web-remix-stream.mjs                 # URL_POT=streaming (pre-fix binding) - reproduces the wall
URL_POT=player node tests/web-remix-stream.mjs  # videoId pot (the app's URL) - should serve past the window
```
Read the `B/B2/C/D` lines: `B continuation` should be "no drop"; `C seek` should be 206; `D one open
GET` should deliver the whole file. The `P pot-variant probe` shows which binding the CDN wants now.

### `pot-probe.mjs` - the definitive binding matrix
```bash
node tests/pot-probe.mjs
```
The source of truth for "which pot does the URL want" (Runbook B).

### `client-fulldownload.mjs` - which clients deliver a whole song
```bash
node tests/client-fulldownload.mjs
PLAYER_HASH=<known hash> node tests/client-fulldownload.mjs   # pin a player for determinism
```
Drains the entire file per client. Use it to re-pick the fallback order (Runbook C).

---

## 5. Runbook - "streaming broke again, now what?"

Work top-down. `cipher.mjs` and `potoken.mjs` are the two health checks; run them first.

### A. `cipher.mjs` throws `no cipher config for live player (hashes: XXXX, YYYY)`
YouTube rotated `player_ias` and `player_configs.json` has no entry for it - the most common break
(`.github/workflows/player-monitor.yml` watches for it hourly).

- **To keep testing immediately:** pin a still-served known player (`PLAYER_HASH=<hash>` from
  `player_configs.json`). The STS sent in `/player` makes YouTube return a signatureCipher compatible
  with that base.js, so decipher stays valid.
- **To support the new player:** add ONE entry to `cipher/library/src/main/assets/player_configs.json`
  (bundled by the app, fetched by deployed apps from cipher `master`, read by the harness).
  `node tests/validate-player-config.mjs <hash>` derives candidates, proves the winner against the live
  CDN (HTTP 206 - the ground truth; `derive-player-config.mjs` only proposes regex candidates), and
  prints the paste-ready entry. `node tests/scan-live-players.mjs <player_configs.json> [samples]` tells a real rotation from a
  low-rate A/B canary. Fields:
  - `sig`: the VM-dispatch expression that transforms `s`, e.g. `Tl(48,5831,INPUT)`. Found by reversing
    base.js: the URL-assembler function sits near `set("alr","yes")`; `INPUT` replaces the inner
    `decodeURIComponent` (CipherDeobfuscator already decodes).
  - `nClass`: the URL-parser class of the n-trick, `new g.CLASS(url,!0).get("n")` (changes per player).
  - `sts`: from `signatureTimestamp:(\d+)` in base.js.
  - `aliases`: the MD5-of-first-10000-bytes fallback hash (needed because some players have no
    self-referencing URL inside the JS; validate-player-config prints it).
  - Confirm with `node tests/cipher.mjs` (`sig=true n=true`, n probe `changed:true`), push cipher
    `master`, then bump the zemer-app submodule pointer.

### B. Songs drop after N seconds / 403 mid-playback / seek fails
The throttle/pot rule changed. Re-derive it:
1. `node tests/web-remix-stream.mjs` -> note the `B continuation` first-failure offset (the free window;
   currently 1,048,576).
2. `node tests/pot-probe.mjs` -> which `url pot=` column serves past that offset. If no longer
   `videoId`, that is the new required binding.
3. Apply it in `cipher/.../PoTokenGenerator.kt` (which token becomes `streamingDataPoToken`), mirror it
   into `potoken.mjs`, re-verify with `URL_POT=… node tests/web-remix-stream.mjs`.
4. If *no* pot works, the gate moved: check the `c=` client, the `n` transform (wrong n -> throttle/403),
   or a new required URL param.

### C. A client that used to work now 403s
`node tests/client-fulldownload.mjs` (pin a player for a clean comparison) shows per client whether the
whole song downloads. Re-rank `ALL_FALLBACK_CLIENTS` in `YTPlayerUtils.kt` toward what still returns
"WHOLE SONG", keeping the array order matching the Stream Sources settings display. Current set and
the retired-client verdicts: [`README.md`](./README.md) "Which clients deliver a WHOLE song" and
`clients-retired.mjs`.

### D. `potoken.mjs` errors / poToken rejected (`UNPLAYABLE` even with pot)
BotGuard/`bgutils-js` drift, or the web request key changed.
- Update `bgutils-js` (`tests/package.json`).
- Confirm the request key matches: `O43z0dpjhgX20SCx4KAo` in `potoken.mjs` (`WEB_REQUEST_KEY`) and
  `PoTokenWebView.kt` (`REQUEST_KEY`).
- The harness mints via bgutils, the app via an Android WebView; if only one breaks, suspect that path.

### E. `playability != OK` (`LOGIN_REQUIRED` / `UNPLAYABLE` / HTTP 400)
- Cookie expired -> refresh `innertube_cookie.txt`.
- Client version stale -> bump in `clients.mjs` *and* `YouTubeClient.kt` (compare against a fresh
  `music.youtube.com` `clientVersion`).
- HTTP 400 on a logged-in request -> check you are not sending `onBehalfOfUser`/`dataSyncId` where it
  isn't wanted; the session id must be `visitorData`.

### F. On-device disagrees with the harness
Capture the app's real URL and curl it from the **same network** (the URL has `ip=` in `sparams`):
1. Temporarily log the full URL in `YTPlayerUtils` after the pot append
   (`android.util.Log.i(TAG, "ZURL … :: \$streamUrl")`), rebuild.
2. `U=$(adb logcat -d | grep 'ZURL' | tail -1 | sed 's/^.*:: //')`
3. `curl -s -o /dev/null -w "%{http_code}\n" -r 1048576-1310719 "$U"` (and HEAD, and `&range=`).
4. Remove the temp log before committing.

---

## 6. Keeping the harness in sync with the app

| App change | Update in harness |
|---|---|
| `YouTubeClient.kt` client versions/UAs | `clients.mjs` |
| new player config | nothing - `player_configs.json` is read by both (`player-configs.mjs`) |
| `PoTokenGenerator.kt` token bindings | `potoken.mjs` `mintWebPoTokens` + the `URL_POT` mapping in `web-remix-stream.mjs` |
| `PoTokenWebView.kt` `REQUEST_KEY` | `potoken.mjs` `WEB_REQUEST_KEY` |
| `YTPlayerUtils.findFormat()` selection | `findFormat()` in the scripts |
| `VideoQualityLogic` ladder/codec rules | `qualityLadder()` + `CODEC_RANK` in `video-qualities.mjs` |
| `PlayerJsFetcher` base.js URL (locale path) | `cipher.mjs` `PLAYER_JS_URL` |

After any of these, `node tests/cipher.mjs && node tests/potoken.mjs && URL_POT=player node tests/web-remix-stream.mjs`
confirms the harness still resolves a playable stream.

---

## 7. Reference facts

- **Free window:** googlevideo serves the first **1,048,576 bytes** of a stream without a valid content
  pot; past that, every new connection 403s.
- **Required pot binding:** stream URL `&pot=` bound to the **videoId**; the `/player` request pot bound
  to the **session (visitorData)**.
- **base.js:** `https://www.youtube.com/s/player/<hash>/player_ias.vflset/en_GB/base.js`, hash from
  `https://www.youtube.com/iframe_api`, which rotates frequently.
- **Chosen audio format:** itag 251 (opus/webm) via the `findFormat` webm bias.

## 8. Gotchas

- **2-byte checks lie.** `Range: bytes=0-1` returning 206 says nothing about full playback - the 1 MiB
  gate is past it. Always drain or range past the window.
- **visitorData encoding.** Stored URL-encoded (`…%3D%3D`); decode once and use the same string for the
  request header *and* the pot binding. (Encoding was ruled out as the pot cause: neither form works on
  the URL.)
- **`ip=` in `sparams` is not strictly enforced**, but for an apples-to-apples on-device check still curl
  from the same network (Runbook F).
- **HEAD validation false-negatives.** `YTPlayerUtils.validateStatus` does a HEAD that can 403 on URLs
  that GET fine, so WEB_REMIX streaming skips it unless the videoId is already in `webRemixFailedIds`
  (a real GET failure); keep that skip.
- **logcat truncates** long lines (~4 kB) and **duplicates** under `su -c logcat` - grep/tail for the latest.
- **Signed URLs expire** (`expire=`, ~6 h). Re-resolve if a curl suddenly 403s everywhere.
