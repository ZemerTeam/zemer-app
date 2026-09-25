# MWEB Client Investigation

## Verdict

MWEB was **removed** from the DIRECT fallback chain (`YTPlayerUtils.ALL_FALLBACK_CLIENTS`), the Stream
Sources setting, and the SABR roster (`SabrPlayerResolver`). It is attestation-walled on gated content
on BOTH transports and only ever served ungated videos that WEB_REMIX / VISIONOS / TVHTML5_SIMPLY
already cover whole, so it was strictly dominated: reaching it only burnt a `/player` round-trip, a
cipher run and a failed validation. Do not re-add it unless a whole-song drain of a gated music track
proves it alive (`tests/probe-mweb-drain.mjs`, `tests/sabr-clients.mjs`).

The cipher was never the problem (MWEB returned a correctly deciphered URL for every probed track).
Two independent causes:

1. **Progressive: the 1-MiB wall, and NO poToken binding crosses it.** A sequential ExoPlayer-style
   drain 403s at byte 1,048,576 for both itag 140 (m4a) and itag 251 (opus/webm), with `pot=` bound to
   none / videoId / visitorData alike. The web BotGuard pot that unlocks WEB_REMIX does not unlock MWEB -
   it needs a mobile attestation the app cannot mint (like IOS/IPADOS).
2. **`validateStatus`'s HEAD is a false-negative** for MWEB URLs (HEAD 403 while GET byte 0 returns 206),
   so even an ungated MWEB URL was demoted. This does not rescue MWEB: it fails the real GET past 1 MiB too.

## Evidence (probe scripts)

- **`probe-mweb-drain.mjs` - the arbiter.** Sequential 256-KiB ranges on fresh connections on gated
  tracks, with WEB_REMIX itag 251 on the same pinned player as control: MWEB 140/251 (no pot) and 140
  (videoId pot) fail at 1 MiB; the control drains the whole song (so the pin is sound and the failure is real).
- **`probe-mweb-verdict.mjs`** - per-binding matrix: MWEB passes the wall only on ungated videos (even
  with no pot); WEB_REMIX with the videoId pot drains every track whole.
- **`probe-mweb-wall-absolute.mjs` - isolated ranges mislead.** A lone `bytes=1048576-` request can
  return 206 (a cold connection gets a fresh window); only a cumulative sequential drain is faithful.
- **`probe-mweb-app-exact.mjs`** - flipping every app-vs-probe request difference (visitorData,
  cookie+SAPISIDHASH, request pot, headers, body shape, itag, url-pot, HEAD UA) changed nothing; the
  variable that mattered was the video (gated vs ungated).
- **`test-mweb-cipher.mjs`'s "200" was an artifact:** it used an ungated official video, the first
  audio format and a HEAD check, while the app plays gated music tracks at itag 251.

## SABR: same wall, different signal

`probe-mweb-sabr.mjs` / `sabr-clients.mjs`: over SABR/UMP, MWEB on a gated track gets a free window
(~28% of segments), then only `STREAM_PROTECTION_STATUS=2` ("attestation pending") with no media,
forever. Every pot binding (`probe-mweb-pot.mjs`: streamerContext web/video, url-pot web/video) fails
identically; WEB_REMIX / VISIONOS / TVHTML5_SIMPLY drain the same track whole.

The guard this justifies stays in the code for any future gated client: `SabrProtection` (pure,
`SabrProtectionTest`) counts consecutive no-media responses under `STREAM_PROTECTION_STATUS >= 2`
(`STALL_LIMIT = 3`), and `SabrSession` / `SabrVideoSession` then fail FAST with an `attestation-capped`
reason so the per-id stall fallback moves to a client that can attest.
