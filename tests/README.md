# tests/ — YouTube Music streaming harness (terminal, hard data)

Node scripts that reproduce the app's streaming pipeline **exactly** (same `/player` request,
same cipher, same poTokens) so playback behaviour can be measured against the live CDN without
building the APK. Source of truth is the terminal output, not guesses.

All scripts run from the repo root or `tests/`. Node >= 20. Deps (`bgutils-js`, `jsdom`,
`youtubei.js`) are vendored in `tests/node_modules`.

> **When streaming breaks again** (player rotation, pot scheme change, a client stops working),
> see **[`INVESTIGATION.md`](./INVESTIGATION.md)** — the full methodology + a symptom-indexed
> runbook ("songs drop after N seconds -> run X", "cipher throws no-config -> do Y", etc.).

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

Default video is `JTF9fLJvniI` (a 330s track — long enough to cross the 1-MiB pot wall). Pass a
different id as the first CLI arg or via `VIDEO_ID`.

---

## Scripts

| script | what it does |
|---|---|
| `cred.mjs` | Loads cookie/visitorData/dataSyncId from `innertube_cookie.txt` (+ env overrides). |
| `cipher.mjs` | Faithful Node port of the app's **Zemer cipher** (sig deobfuscation + n-transform + STS). Fetches the **same** `base.js` (iframe_api -> `player_ias.vflset/en_GB/base.js`), injects the **same** VM-dispatch expressions (`Tl(48,5831,sig)` / `Qp(25,37,sig)` / `v0(35,4499,sig)` / `Jf(20,3699,sig)`, `g.W_`/`g.W1`/`g.uY`/`g.iE` n-trick) into the IIFE, and runs it in jsdom. Byte-identical to `CipherWebView`. |
| `potoken.mjs` | BotGuard **poToken** minter (`bgutils-js` + jsdom), request key `O43z0dpjhgX20SCx4KAo`. Mirrors the app's `PoTokenGenerator`: **streaming token bound to visitorData** (minted first), **player token bound to videoId**. |
| `web-remix-stream.mjs` | Reproduces the WEB_REMIX **45s-drop + seek** bug via the app's exact resolve path, then exercises the URL like ExoPlayer (sequential range chunks on fresh connections, seek, one open GET, re-resolve, pot-variant probe) + an IOS control. |
| `pot-probe.mjs` | The **definitive poToken-binding matrix**: request-pot × url-pot × {none/videoId/visitorData-raw/visitorData-enc}, fetched past the 1-MiB window. |
| `client-fulldownload.mjs` | Drains the **whole** file per client to show which clients actually deliver a full song right now (vs the old 2-byte check). Defaults to the app's MAIN+fallback client set; `CLIENTS=A,B` to subset. |
| `sts-mismatch.mjs` | Regression test for **STS/cipher player coherence**: `/player` with the pinned player's own STS must stream past the wall; another live generation's STS 403s (the A/B-rollout bug — app fix: `CipherDeobfuscator.signatureTimestamp()` feeds `YTPlayerUtils`). |
| `video-qualities.mjs` | The **beyond-720p quality ladder** prover: enumerates every video quality (progressive muxed + adaptive video-only, mirroring `VideoQualityLogic.ladderFormats`) plus the audio merge partner, resolves each URL the app's exact way, and per rung (high→low) verifies initial 206, a fresh-connection sweep past the 1-MiB pot wall, a 75% seek, and a **full drain to EOF** (the download proof). PASS/FAIL exit — can gate. |
| `run.mjs`, `full-stream.mjs`, `retest-web.mjs`, `clients.mjs` | Older player-endpoint probes / client matrix (kept for reference). |

### Run them

```bash
node tests/cipher.mjs                 # self-test: live player hash, STS, sig/n working?
node tests/potoken.mjs JTF9fLJvniI    # mint the token pair, print bindings/lengths
node tests/web-remix-stream.mjs                      # reproduce the bug (URL_POT=streaming, default)
URL_POT=player node tests/web-remix-stream.mjs       # verify the fix (videoId-bound pot)
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

## Results — the WEB_REMIX hiccup (2026-06-04, video `JTF9fLJvniI`, logged-in cookie)

### Reproduced

The app's exact WEB_REMIX path resolves fine (`playability=OK`, itag 251 opus, sig+n applied,
`pot=` appended). The CDN URL is `clen=5,878,798` bytes / 330s (~17.8 KB/s).

```
A initial   bytes=0-262143               -> 206
B continuation (fresh conn / chunk)      -> 403 at byte 1,048,576  (= 1 MiB ~ 59s)
C seek @75%                              -> 403 (header AND query range)
D one open GET bytes=0-                  -> 403, 0 bytes delivered
```

- **45s drop** = the CDN serves the first **1 MiB** then 403s every *new connection* (next chunk).
- **Seek -> fallback** = a seek is just a new connection at a far offset; same 403.

Both are the same failure: any connection past the 1-MiB free window is rejected.

### Root cause — the appended poToken is bound to the wrong thing

`pot-probe.mjs`, holding everything else constant and fetching past 1 MiB:

```
                       @1 MiB   @2 MiB
url pot = none          403      403
url pot = videoId       206      206   OK  -> full file 5,878,798 B downloads
url pot = visitorData    403      403       (raw "==" AND url-encoded "%3D%3D" both fail)
```

- Independent of what's sent in the `/player` request, and reproduced across multiple player versions
  (`4f38b487` STS 20602, `5cabb421` STS 20606, `9d2ef9ef` STS 20607, `69e2a55d` STS 20611). visitorData encoding ruled out.
- **The stream URL's `pot=` must be bound to the videoId.**

The app does the opposite. `PoTokenGenerator.getWebClientPoToken(videoId, sessionId)` returns
`PoTokenResult(playerPot = generate(videoId), streamingPot = generate(visitorData))`, and
`YTPlayerUtils` appends `streamingDataPoToken` (= `streamingPot` = **visitorData-bound**) to the
URL while sending `playerRequestPoToken` (= **videoId-bound**) in the request. The two are swapped
relative to what googlevideo enforces, so every WEB_REMIX stream 403s at 1 MiB -> fallback restart.

### Fix — verified end to end

Append the **videoId-bound** token to the stream URL (`URL_POT=player`):

```
A initial                     -> 206
B  continuation (header)      -> all 206 through 132.5s — no drop
B2 continuation (query)       -> all 206 through 132.5s — no drop
C  seek @75%                  -> 206 / 200
D  one open GET               -> 206, whole file 5741KB/5741KB (330s)
```

Code fix options (pick one):
- **App side (smallest):** in `YTPlayerUtils.playerResponseForPlayback`, append
  `poTokenResult.playerRequestPoToken` (videoId-bound) to `streamUrl` instead of
  `streamingDataPoToken`.
- **Cipher side (clearer names):** in `PoTokenGenerator.getWebClientPoToken`, swap the bindings so
  `streamingDataPoToken` is `generate(videoId)` and `playerRequestPoToken` is `generate(visitorData)`.

### Which clients deliver a WHOLE song right now

`client-fulldownload.mjs` (full drain, not a 2-byte check):

| client | full song? | needs |
|---|---|---|
| **ANDROID_VR_1_43_32 / _NO_AUTH / _1_61_48** | yes whole song | nothing — anon, direct url, no pot/cipher/BotGuard |
| **WEB_REMIX** | yes **only with the videoId-pot fix** | poToken (videoId-bound) + sig/n cipher |
| **IOS / IPADOS** | no 403 (hits the 1-MiB wall, no pot) | — no longer reliable for full playback |

Current `STREAM_FALLBACK_CLIENTS` order (as of 2026-06-08):
`WEB_REMIX -> VISIONOS -> WEB_CREATOR -> ANDROID_VR_1_43_32 -> ANDROID_VR_1_61_48 -> TVHTML5 -> IOS -> IPADOS -> ANDROID_CREATOR -> ANDROID_VR_NO_AUTH -> MOBILE -> WEB`

- **VISIONOS / ANDROID_VR are the reliable no-pot paths** — direct url, no BotGuard, no decipher, whole song confirmed via `re-apple.mjs` / `client-fulldownload.mjs`.
- **WEB_CREATOR** streams the full song with videoId pot (no streaming pot needed) — ranks above TVHTML5.
- **IOS/IPADOS 403 past 1 MiB** — spc-gated, no pot binding that satisfies it; deprioritised.
- **TVHTML5** is a web/pot client subject to the same 1-MiB rule — needs videoId-pot fix.

> Note: the earlier "Findings (2026-06-02)" in this file concluded IOS/ANDROID_VR "stream in one
> request". That was based on `Range: bytes=0-1` (first 2 bytes) and does **not** hold for full
> playback — IOS 403s past 1 MiB. Use `client-fulldownload.mjs` for the real picture.
