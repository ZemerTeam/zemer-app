# MWEB Client Investigation

## TL;DR (resolved 2026-06-09)
The cipher was never the problem. MWEB's 403 has **two** independent causes, both proven with
hard data against the live CDN (`tests/probe-mweb-*.mjs`):

1. **MWEB stream URLs hit the 1-MiB free-window wall and NO poToken binding crosses it.** A
   sequential ExoPlayer-style drain 403s at byte 1,048,576 for **both** itag 140 (m4a) and itag
   251 (opus/webm), with `pot=` bound to none / videoId / visitorData alike. The web BotGuard
   poToken that unlocks WEB_REMIX does **not** unlock MWEB on the same video — MWEB is gated like
   IOS/IPADOS (attestation the app's web pot can't satisfy).
2. **The app's HEAD validation (`validateStatus`) is a false-negative** for MWEB URLs: HEAD=403
   while a real GET of byte 0 returns 206 (8–10 of every 10 tracks). So even on the rare ungated
   video where MWEB *could* serve, `validateStatus` demotes it.

**Why the old terminal test showed "200":** `tests/test-mweb-cipher.mjs` defaulted to
`dQw4w9WgXcQ` — an **ungated** official video that streams on any client — and picked the **first**
audio format (itag 140) via a HEAD check. The app plays real YouTube *Music* tracks (gated ATV art
tracks) and picks itag 251 via `findFormat`. Different video + different format + HEAD-vs-GET =
the entire "terminal 200 / app 403" gap. There was never a WebView-vs-jsdom cipher discrepancy.

**Verdict:** MWEB is **strictly dominated** by WEB_REMIX (the app's main client, tried first),
which delivered the whole song for 9/9 and 3/3 gated tracks in every run, on the same pinned
player. There is no measured video where MWEB succeeds and the existing clients fail. MWEB cannot
be "fixed" into a working fallback; the correct action is to remove it from the chain.

---

## Evidence

### The controlled drain (the arbiter) — `tests/probe-mweb-drain.mjs`
Short gated songs, sequential 256-KiB bounded ranges on fresh connections (exactly how ExoPlayer
streams), with WEB_REMIX itag 251 as a same-player control:

```
=== short gated tracks, pinned player 69e2a55d (and re-confirmed on 16ee6936) ===
  MWEB-140(m4a)  none     FAIL at byte 1048576 (1.00MiB) 403
  MWEB-251(webm) none     FAIL at byte 1048576 (1.00MiB) 403   (sometimes 0.75MiB)
  MWEB-140(m4a)  vidPot   FAIL at byte 1048576 (1.00MiB) 403
  WEB_REMIX-251  vidPot   WHOLE SONG ✓ (15–19 chunks)   [CONTROL]
```
The control draining the whole song on the same pinned player proves the pin is sound and the MWEB
failure is real (client/format), not a test artifact.

### Per-binding matrix — `tests/probe-mweb-verdict.mjs` (9 tracks)
```
MWEB returned cipher URL: 9/9      (cipher works perfectly — sig 104 chars, n applied, GET0=206)
MWEB delivered past 1-MiB wall: 2/9  (only the 2 ungated videos; both 206 even with NO pot)
WEB_REMIX past-wall (videoId pot): 9/9 whole song
HEAD(validateStatus)=403 while GET byte0=206 (false-negative): 8/9
```

### Single-isolated-range was a red herring — `tests/probe-mweb-wall-absolute.mjs`
A lone `bytes=1048576-` request can return 206 on itag 140 (the CDN grants a fresh window to a
cold connection), which briefly looked like "itag 140 is the fix." The **sequential** drain above
(cumulative bytes on one playback session) is the faithful test and shows itag 140 also 403s at
the wall. Don't trust isolated range probes — drain like the player does.

### App-exact request bisect — `tests/probe-mweb-app-exact.mjs`
Flipping every request/validation difference between the app and the old probe (visitorData,
cookie+SAPISIDHASH, request poToken, X-Goog headers, prettyPrint vs key URL, full body shape,
itag 140 vs 251, url-pot none vs videoId, HEAD UA/cookie combos) changed nothing: the gated music
track 403s in every combination. The variable that mattered was the **video** (gated vs ungated),
not the request.

---

## The fix (recommended)
- **Remove MWEB from the fallback chain** (`YTPlayerUtils.ALL_FALLBACK_CLIENTS`) and the Stream
  Sources setting — i.e. revert `feat: add MWEB client as stream source fallback` (af6a5a4). When
  reached it only burns a `/player` round-trip + cipher + a 403 HEAD before falling through.
- Separately, **`validateStatus`'s HEAD is a latent false-negative** (also noted in the harness
  gotchas and the WEB_REMIX path): it 403s on URLs that GET fine. Worth removing/replacing with a
  small ranged GET, but that does **not** rescue MWEB (MWEB fails the real GET past 1 MiB too).

## Related: player rotation found during this work
`iframe_api` was A/B-serving a new `player_ias` **16ee6936** (md5 alias `ca366632`, STS 20613)
alongside the live `69e2a55d`. Its cipher config was derived and **empirically validated** (real
signatureCipher → 206; full WEB_REMIX drain = whole song) and added to both
`cipher/.../FunctionNameExtractor.kt` and `tests/cipher.mjs`:
`sig = mP(4,155,INPUT)`, `n = g.Yx` trick, `sts = 20613`. New tooling for next time:
`tests/derive-player-config.mjs` (regex candidates), `tests/validate-player-config.mjs`
(ground-truth 206 check — the one to trust), `tests/check-live-player.mjs` (is a rotation real?).

---

## Update (2026-09): MWEB over SABR — same wall, different signal

The above is the PROGRESSIVE path. MWEB over the **SABR/UMP** transport hits the identical
attestation wall, just signalled differently — proven live (`tests/probe-mweb-sabr.mjs`,
`tests/sabr-clients.mjs`):

```
gl9VXSMZwTo (gated ATV), MWEB over SABR, itag 251:
  iter 1: +2 segs   6%   STREAM_PROTECTION_STATUS=2
  iter 2: +2 segs  19%   STREAM_PROTECTION_STATUS=2
  iter 3: +2 segs  28% (6/23)  STREAM_PROTECTION_STATUS=2
  iter 4+: NO media — only STREAM_PROTECTION_STATUS=2 + policy parts, forever
  final: 6/23 segments (28%)     [WEB_REMIX/VISIONOS/TVHTML5_SIMPLY = WHOLE 23/23]
```

So the SABR server grants the same ~free window, then serves only `STREAM_PROTECTION_STATUS=2`
("attestation pending") with no media. The web BotGuard poToken does **not** satisfy it — every pot
binding fails identically (`tests/probe-mweb-pot.mjs`): streamerContext=web, =video, url-pot=web or
video → all 6/23, PROT=2. MWEB's attestation needs a MOBILE device-integrity token the app cannot
mint, exactly like the progressive 1-MiB wall. (innertubex reached the same conclusion — it ships
`MWEB_SABR`/`IOS_SABR` disabled "after SABR attestation errors".)

**App behaviour (2026-09):** `SabrSession`/`SabrVideoSession` detect the sustained cap
(`SabrProtection`: STREAM_PROTECTION_STATUS>=2 with no media for 3 responses) and bail FAST with an
`attestation-capped` reason, so the per-id stall fallback moves to a client that can attest instead
of grinding to the dry cap. MWEB stays a conditional last-resort client (it DOES drain ungated
videos whole, e.g. dQw4w9WgXcQ); it is not a reliable download client for gated content on either
transport, and the whole-capable clients (WEB_REMIX / TVHTML5_SIMPLY / VISIONOS) cover it.
