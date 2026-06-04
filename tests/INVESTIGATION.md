# Streaming investigation & runbook

How we diagnosed the WEB_REMIX playback hiccups with hard data, and — more importantly —
**how to do it again** when YouTube changes something and streaming breaks. This is the
reference to reach for when songs start dropping, seeking fails, a player rotates, or poTokens
stop working.

Companion to [`README.md`](./README.md) (quick reference + the headline result). This file is
the deep version: methodology, the full story, and a symptom-indexed runbook.

---

## 0. Principles (why the harness exists)

1. **Hard data only.** Every claim is an HTTP status at a specific byte offset against the live
   googlevideo CDN, with the real logged-in cookie. No reasoning from convention — YouTube's
   behaviour changes faster than any blog/yt-dlp/NewPipe lore, and the lore was *wrong* here
   (the stream URL wants a **videoId**-bound poToken, not the conventional visitorData one).
2. **Reproduce the app's EXACT path**, not an approximation. Same `/player` request as
   `InnerTube.kt`, same sig+n cipher as the `cipher` submodule (run in jsdom instead of an
   Android WebView, byte-identical output), same two poTokens as `PoTokenGenerator`. A test that
   diverges from the app proves nothing about the app.
3. **Isolate one variable at a time.** The binding matrix (`pot-probe.mjs`) holds the request
   constant and varies only the URL pot; the client survey (`client-fulldownload.mjs`) holds the
   video constant and varies only the client. That's how you turn "it's broken" into "byte
   1,048,576 returns 403 unless pot is bound to the videoId."

---

## 1. How the harness mirrors the app

The test scripts are deliberate ports of specific app code. When the app changes, the mirror
must change too (see §6). The mapping:

| App (Kotlin) | Harness (Node) | What it reproduces |
|---|---|---|
| `YouTube.cookie` / `visitorData` (session) | `cred.mjs` | the logged-in session, from `innertube_cookie.txt` |
| `InnerTube.kt` `player()` + `ytClient()` | `web-remix-stream.mjs` / `pot-probe.mjs` `playerRequest()` | the `/player` POST: context, `X-Goog-*` headers, `SAPISIDHASH`, `signatureTimestamp`, `serviceIntegrityDimensions.poToken` |
| `models/YouTubeClient.kt` | `clients.mjs` | client name/version/id/UA/flags (WEB_REMIX id 67, IOS id 5, …) |
| `cipher/.../PlayerJsFetcher.kt` | `cipher.mjs` `fetchPlayerJs()` | iframe_api → `player_ias.vflset/en_GB/base.js`, STS |
| `cipher/.../FunctionNameExtractor.kt` `KNOWN_PLAYER_CONFIGS` | `cipher.mjs` `KNOWN_PLAYER_CONFIGS` | per-player sig expression (`Tl(48,5831,…)` / `Qp(25,37,…)`) + n-trick (`g.W_`/`g.W1`) + STS |
| `cipher/.../CipherWebView.kt` (Android WebView) | `cipher.mjs` (jsdom) | injects exports into the IIFE `})(_yt_player);`, runs base.js, calls `_cipherSigFunc` / `_nTransformFunc` |
| `cipher/.../CipherDeobfuscator.kt` | `cipher.mjs` `deobfuscateStreamUrl` / `transformNParamInUrl` | parse `s/sp/url`, apply sig, replace `n=`, append `&pot=` |
| `cipher/.../potoken/PoTokenGenerator.kt` + `PoTokenWebView.kt` | `potoken.mjs` (bgutils-js) | BotGuard mint: streaming pot ← visitorData, player pot ← videoId, request key `O43z0dpjhgX20SCx4KAo` |
| `YTPlayerUtils.playerResponseForPlayback()` | `web-remix-stream.mjs` `resolveAppUrl()` | full resolve: player → findFormat → sig → n → pot |
| `YTPlayerUtils.findFormat()` | `findFormat()` in the scripts | `adaptiveFormats.filter(isAudio && isOriginal).maxBy(bitrate + webm bias)` → itag 251 opus |
| ExoPlayer `DefaultHttpDataSource` + `RetryOn403DataSource` | `fetchRange()` / `drainWhole()` | range GETs on fresh connections; seek = range at a far offset |

> The Node cipher is **not** an approximation. It downloads the same `base.js` and runs it; only
> the JS host differs (jsdom vs Android WebView). Confirmed identical: the n-transform probe
> `KdrqFlzJXl9EcCwlmEy → -_L5LE0VOwTkzf` matches on both, and the bgutils pot and the WebView pot
> both unlock the same URL.

---

## 2. Setup

1. **Cookie.** Put the dumped logged-in session at the repo root as `innertube_cookie.txt`
   (gitignored — never commit it). Format in [`README.md` §1](./README.md). `cred.mjs` parses it.
   - To refresh: re-dump from the app/account (the values are `HSID/SAPISID/SID/__Secure-*PSID/…`
     cookies + `VISITOR DATA`). A stale cookie shows up as `playability != OK`.
2. **Deps** are vendored in `tests/node_modules` (`bgutils-js`, `jsdom`, `youtubei.js`). Node ≥ 20.
3. **Test video:** default `JTF9fLJvniI` (330 s — long enough to cross the 1 MiB wall). Override with
   argv[1] or `VIDEO_ID`.

Env knobs: `URL_POT=streaming|player|none`, `PLAYER_HASH=<hash>` (pin a player), `CHUNK`,
`COVER_SECONDS`, `YT_COOKIE`/`YT_VISITOR_DATA` (override the file).

---

## 3. The investigation (what we actually did)

The story, so the *method* is repeatable even if the specifics change.

1. **Reproduce, don't trust HEAD.** The old probe checked `Range: bytes=0-1` (2 bytes) and
   declared clients "streamable". That's meaningless for full playback. We instead drove the URL
   like ExoPlayer: sequential range chunks on fresh connections until something 403s.
   → `web-remix-stream.mjs` found the first failure at **byte 1,048,576 (1 MiB)**, ~the reported
   "45 s drop". Seek (range at 75 %) also 403'd. Both symptoms = the same wall.
2. **The free window is universal.** Even the IOS direct URL (no pot) 403'd past 1 MiB. So this
   is a googlevideo rule, not WEB_REMIX-specific.
3. **Isolate the gate.** The pot-variant probe at the failing offset: `no pot → 403`,
   `visitorData pot → 403`, `videoId pot → 206`. The URL wants a **videoId-bound** pot.
4. **Kill the alternatives.** `pot-probe.mjs` ran the full matrix (request-pot × url-pot ×
   {none/videoId/visitorData-raw/visitorData-enc}) across two player versions. Only url-pot=videoId
   served past 1 MiB → full file. visitorData encoding ruled out; request-pot irrelevant.
5. **Find the bug in the app.** `PoTokenGenerator.getWebClientPoToken` returned
   `PoTokenResult(playerPot=generate(videoId), streamingPot=generate(visitorData))`, and
   `YTPlayerUtils` appends `streamingDataPoToken` (visitorData) to the URL → always 403 past 1 MiB.
   The bindings were swapped relative to what the CDN enforces.
6. **Fix + verify on-device.** Swapped the mapping in the cipher. Rebuilt, logged the full resolved
   URL on device, curled it from a terminal: **206 at every range, HEAD 200, query-range 200, whole
   file** — including from a different IP. All three songs then played on WEB_REMIX with no fallback.

The fix is one mapping swap. Everything else here is the apparatus that proved it.

---

## 4. Running each test (and reading the output)

```bash
cd tests   # or run with tests/ prefix from repo root
```

### `cipher.mjs` — is the cipher alive on the current player?
```bash
node cipher.mjs
```
Prints the live player hash, STS, and whether sig + n work (`nProbe.changed: true`). **Run this
first whenever streaming breaks** — if it throws "no hardcoded cipher config", YouTube rotated to
a player neither the app nor the harness knows yet (see §5 Runbook A).

### `potoken.mjs` — does BotGuard still mint?
```bash
node potoken.mjs JTF9fLJvniI
```
Prints the two tokens + lengths. If it errors, bgutils/BotGuard changed (Runbook D).

### `web-remix-stream.mjs` — reproduce drop/seek; verify a fix
```bash
node web-remix-stream.mjs                 # URL_POT=streaming (app default) — reproduces the bug
URL_POT=player node web-remix-stream.mjs  # videoId pot — should serve clean past the window
```
Read the `B/B2/C/D` lines: `B continuation` should be "no drop"; `C seek` should be 206; `D one
open GET` should deliver the whole file. The `P pot-variant probe` shows which binding the CDN
wants *right now*.

### `pot-probe.mjs` — the definitive binding matrix
```bash
node pot-probe.mjs
```
The source of truth for "which pot does the URL want". If the answer ever stops being "videoId",
this is what tells you the new answer (Runbook B).

### `client-fulldownload.mjs` — which clients deliver a whole song
```bash
node client-fulldownload.mjs              # uses the live player
PLAYER_HASH=4f38b487 node client-fulldownload.mjs   # pin a known player for determinism
```
Drains the entire file per client. Use it to re-pick the fallback order when a client breaks
(Runbook C).

---

## 5. Runbook — "streaming broke again, now what?"

Work top-down. `cipher.mjs` and `potoken.mjs` are the two health checks; run them first.

### A. `cipher.mjs` throws `no hardcoded cipher config for live player (hashes: XXXX, YYYY)`
YouTube rotated to a new `player_ias` and neither the app's `FunctionNameExtractor` nor
`cipher.mjs` has its sig/n recipe. This is the **most common** future break (the app has an hourly
hash monitor for exactly this).

- **To keep testing immediately:** pin a still-served known player —
  `PLAYER_HASH=4f38b487 node web-remix-stream.mjs`. The STS you send in the `/player` request makes
  YouTube return a signatureCipher compatible with that base.js, so decipher stays valid even if it
  isn't the "current" rotated player. (Verified: pinning 4f38b487 worked while the live player was
  the unconfigured `0006d355`.)
- **To actually support the new player:** add an entry to **both**
  `cipher/library/.../FunctionNameExtractor.kt` `KNOWN_PLAYER_CONFIGS` (app, the real fix) and
  `tests/cipher.mjs` `KNOWN_PLAYER_CONFIGS` (harness mirror), keyed by both the URL hash and the
  MD5-of-first-10000-bytes alias. Each entry needs:
  - `sigExpr`: the VM-dispatch expression that transforms `s`, e.g. `Tl(48,5831,INPUT)` (player
    4f38b487/9c249f6f) or `Qp(25,37,INPUT)` (5cabb421). Derive by reversing the new base.js: find
    the function that consumes `decodeURIComponent(...s)` and note the global dispatch name + its two
    integer constants.
  - `nExpr`: the URL-parser trick — `new g.W_('…?n='+n,true).get('n')` (the class name `W_`/`W1`
    changes per player; it's the player's internal URL parser).
  - `sts`: from `signatureTimestamp:(\d+)` in base.js.
  - Confirm with `node cipher.mjs` → `sig=true n=true`, n probe `changed:true`.

### B. Songs drop after N seconds / 403 mid-playback / seek fails
The throttle/pot rule changed. Re-derive it empirically:
1. `node web-remix-stream.mjs` → note the `B continuation` first-failure byte offset (the new "free
   window"; was 1,048,576).
2. `node pot-probe.mjs` → read which `url pot=` column serves past that offset. If it's no longer
   `videoId`, that's the new required binding.
3. Apply it in `cipher/.../PoTokenGenerator.kt` (which token goes to `streamingDataPoToken`) and
   re-verify with `URL_POT=… node web-remix-stream.mjs`. Mirror any token-binding change into
   `potoken.mjs`.
4. If *no* pot works, the gate moved elsewhere — check `c=` client, the `n` transform (wrong n →
   throttle/403), or a new required URL param.

### C. A client that used to work now 403s (e.g. IOS broke)
`node client-fulldownload.mjs` (pin a player for a clean comparison). It shows, per client,
whether the whole song downloads. Re-rank `STREAM_FALLBACK_CLIENTS` in `YTPlayerUtils.kt` toward
whatever still returns "WHOLE SONG" (as of this writing: ANDROID_VR with no pot; WEB_REMIX with the
videoId pot; IOS/IPADOS were broken past 1 MiB).

### D. `potoken.mjs` errors / poToken rejected (`UNPLAYABLE` even with pot)
BotGuard/`bgutils-js` drift, or the web request key changed.
- Update `bgutils-js` (`tests/package.json`).
- Confirm the request key still matches the app: `O43z0dpjhgX20SCx4KAo` in both
  `potoken.mjs` (`WEB_REQUEST_KEY`) and `PoTokenWebView.kt` (`REQUEST_KEY`).
- Remember the harness mints via bgutils while the app mints via an Android WebView — both have
  worked interchangeably, but if only one breaks, suspect that path specifically.

### E. `playability != OK` (`LOGIN_REQUIRED` / `UNPLAYABLE` / HTTP 400)
- Cookie expired → refresh `innertube_cookie.txt`.
- Client version stale → bump in `clients.mjs` *and* `YouTubeClient.kt` (compare against a fresh
  `music.youtube.com` to get the current `clientVersion`).
- HTTP 400 on a logged-in request → check you're not sending `onBehalfOfUser`/`dataSyncId` where it
  isn't wanted (historically broke WEB_REMIX; session id must be `visitorData`).

### F. On-device disagrees with the harness
Capture the app's real URL and curl it from the **same network** (the URL has `ip=` in `sparams`;
test from the phone via termux, or a box on the same NAT):
1. Temporarily log the full URL in `YTPlayerUtils` after the pot append
   (`android.util.Log.i(TAG, "ZURL … :: \$streamUrl")`), rebuild.
2. `U=$(adb logcat -d | grep 'ZURL' | tail -1 | sed 's/^.*:: //')`
3. `curl -s -o /dev/null -w "%{http_code}\n" -r 1048576-1310719 "$U"` (and HEAD, and `&range=`).
4. Remove the temp log before committing.

---

## 6. Keeping the harness in sync with the app

The Node mirror duplicates app constants on purpose. When these app files change, update the mirror:

| App change | Update in harness |
|---|---|
| `YouTubeClient.kt` client versions/UAs | `clients.mjs` |
| `FunctionNameExtractor.kt` `KNOWN_PLAYER_CONFIGS` (new player) | `cipher.mjs` `KNOWN_PLAYER_CONFIGS` |
| `PoTokenGenerator.kt` token bindings | `potoken.mjs` `mintWebPoTokens` + the `URL_POT` mapping in `web-remix-stream.mjs` |
| `PoTokenWebView.kt` `REQUEST_KEY` | `potoken.mjs` `WEB_REQUEST_KEY` |
| `YTPlayerUtils.findFormat()` selection | `findFormat()` in the scripts |
| `PlayerJsFetcher` base.js URL (locale path) | `cipher.mjs` `PLAYER_JS_URL` |

A quick `node cipher.mjs && node potoken.mjs && URL_POT=player node web-remix-stream.mjs` after any
of these confirms the harness still resolves a playable stream.

---

## 7. Reference facts (as of 2026-06-04)

- **Free window:** googlevideo serves the first **1,048,576 bytes (1 MiB)** of a stream without a
  valid content pot; past that, every new connection 403s.
- **Required pot binding:** stream URL `&pot=` must be bound to the **videoId**; the `/player`
  request pot is bound to the **session (visitorData)**. (Opposite of the common convention.)
- **Player rotation:** iframe_api rotates `player_ias` hashes frequently (saw `4f38b487` STS 20602,
  `5cabb421` STS 20606, and a fresh unconfigured `0006d355` within one session). The MD5-of-first-
  10000-bytes alias matters because some players have no self-referencing URL inside the JS.
- **Chosen format:** itag 251 (opus, webm) — `findFormat` adds a +10240 webm bias.
- **Request key (web BotGuard):** `O43z0dpjhgX20SCx4KAo`.
- **base.js:** `https://www.youtube.com/s/player/<hash>/player_ias.vflset/en_GB/base.js`, hash from
  `https://www.youtube.com/iframe_api`.
- **Full-song delivery (this date):** ANDROID_VR ✅ (no pot/cipher), WEB_REMIX ✅ (with videoId pot),
  IOS/IPADOS ❌ (403 past 1 MiB).

## 8. Gotchas (things that wasted time, so they don't again)

- **2-byte checks lie.** `Range: bytes=0-1` returning 206 says nothing about full playback — the
  1 MiB gate is past it. Always drain or range past the window.
- **visitorData encoding.** Stored URL-encoded (`…%3D%3D`). Decode once and use the same string for
  the request header *and* the pot binding. Both raw `==` and encoded `%3D%3D` were tested — neither
  visitorData binding works on the URL; only videoId does (so encoding wasn't the cause).
- **`ip=` in `sparams` is not strictly enforced.** The on-device URL served 206 from a different
  IP. Don't assume IP-lock; but for an apples-to-apples on-device check, still curl from the same
  network (Runbook F).
- **HEAD validation is a false-negative trap.** `YTPlayerUtils.validateStatus` does a HEAD; it has
  historically 403'd on URLs that GET fine. With the pot fixed, HEAD now returns 200 — but the
  `webRemixFailedIds` + HEAD path can still spuriously demote WEB_REMIX after one transient failure.
  Candidate for removal now that the pot is correct.
- **logcat truncates** long lines (~4 kB) and **duplicates** under `su -c logcat` — a ~1.8 kB
  stream URL fits, but grep/tail for the latest.
- **Signed URLs expire** ~6 h (`expire=` / "Expires in: 21540s"). Re-resolve if a curl suddenly
  403s everywhere — it may just be stale.
- **Don't trust convention over the matrix.** yt-dlp/NewPipe say streaming=visitorData; the CDN
  said videoId. `pot-probe.mjs` is the arbiter.
