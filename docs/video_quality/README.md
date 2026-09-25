# Video quality (beyond 720p: streaming, downloads, and the in-player switcher)

Video mode exposes the **full quality ladder** — the adaptive video-only formats (**144p … 2160p**)
plus the progressive muxed ones (itag 18/22) — as a selectable, streamable, downloadable set, with no
extra dependencies (framework `MediaMuxer`/`MediaExtractor` + media3's `MergingMediaSource`).

The agent-facing invariants are also summarised in `AGENTS.md` §Video mode ("The quality ladder").
Under SABR (`StreamSabrKey` on), rungs are pinned per request by `SabrVideoResolver` (field-17
`preferredVideoFormatId`) over the same `VideoQualityLogic` ladder — see `docs/sabr/README.md` sec 9.4.
Prove any change against the live CDN with `tests/video-qualities.mjs` first.

## The pieces

| File | Role |
| --- | --- |
| `playback/VideoQualityLogic.kt` | **Pure, JVM-tested.** Builds the ladder (one rung per qualityLabel, progressive wins its label, then avc1 > vp9 > av01), resolves a target to the best rung at-or-below, the rebuffer-downgrade math, codec→decoder-mime mapping. |
| `playback/VideoRendition.kt` | The cache-key grammar: `video:<id>` (automatic), `video:<id>:p<itag>` / `:q<itag>` (explicit progressive/adaptive rung), `videoaudio:<id>` (the merge-audio partner); `allRenditionKeys` lists the family. The itag lives IN the key so rungs can't share spans. |
| `playback/VideoDecoderCaps.kt` | `MediaCodecList` capability gate — never offer a rung this device can't decode (1440p/2160p are vp9-only). |
| `playback/VideoModeController.kt` | The swap state machine: entry at the target rung, the switcher, the rebuffer guard, error handling. Swap state is main-thread-confined; the quality maps are concurrent. |
| `utils/YTPlayerUtils.kt` | Resolution: one streaming video response yields the chosen format + every rung's URL (`videoRungUrls`, web clients only) + the merge-audio partner (`mergeAudioUrl`); a download response yields the download-audio partner (`downloadAudioUrl`) instead - all pure-local cipher work. |
| `playback/MusicService.kt` | The `ResolvingDataSource` branches (`video:` / `videoaudio:`), URL-cache seeding + itag-drift purge, `MergingMediaSource` wiring, `prefetchVideoRendition`. |
| `utils/VideoMuxer.kt` | Framework remux of video-only + audio → MP4 (avc1) / WebM (vp9, API 29+). `Result.TRANSIENT` vs `Result.INCOMPATIBLE`. |
| `playback/MediaStoreDownloadManager.kt` | The two-stream adaptive download + mux. |
| `ui/player/VideoQualitySelector.kt` + `ui/menu/VideoQualityMenu.kt` | The over-media pill + the shared picker body. |

## Streaming a rung

`VideoModeController.enterVideoMode` picks the target from `effectiveQualityTarget` (the session
override if any, else the Settings default `VideoQualityKey`). When the item's ladder is already known
(a prefetch or earlier entry this session) it enters DIRECTLY at that rung; otherwise it enters on the
plain `video:<id>` automatic pick and `onVideoQualitiesResolved` upgrades position-continuously when the
ladder lands.

A **progressive** rung is one muxed stream. An **adaptive** rung (`:q`) is video-only, so
`createMediaSourceFactory` wraps it in a `MergingMediaSource` with the item's audio under the
`videoaudio:<id>` key. Every merge-audio resolution (video-branch seed, live merge branch, prefetch)
resolves at **HIGH** so its itag agrees across all three — the itag-drift purge depends
on that.

### Honor the user's choice

An explicit quality — **Settings default OR in-player pick** — is honored on **every** connection,
metered included: no metered gate, no bandwidth pre-gate, and a video error never pins AUTO (it only
invalidates the dead URLs; undecodable rungs are already filtered by `VideoDecoderCaps`). Data/stutter
protection lives where it does not override the user: the **AUTO** pick keeps its metered bitrate cap
(`VideoRendition.defaultMaxBitrateKbps`), and the rebuffer guard drops the CURRENT video one rung when it
actually stalls (per item; a new video starts at the user's setting).

Two maps: `qualityOverrides` is the effective session state (a switcher pick OR a guard downgrade) and
drives streaming; `userQualityPicks` holds ONLY explicit switcher picks and is what downloads read
(`downloadVideoQuality`), so a machine downgrade never leaks into a download.

### The rebuffer guard

A `STATE_BUFFERING` after `READY` on a streaming rendition is a mid-play stall.
`VideoQualityLogic.shouldDowngradeForRebuffer`: `REBUFFER_DOWNGRADE_COUNT` (2) stalls within
`REBUFFER_WINDOW_MS` (45 s) — a single blip must not drop the user's quality. The drop is exactly ONE
rung (`rungBelow`), so playback settles on the highest rung that actually plays. Never bandwidth-gate a
multi-rung jump: a rung's `bitrate` is its PEAK and media3's estimate is depressed right after a stall,
so a jump over-drops. Exempt: seek-caused buffering (a timestamp grace window, `SEEK_GRACE_MS` — not a
boolean flag, which a seek into buffered data never clears), a swap's own prepare, LOCAL/audio, and AUTO
(nothing cheaper to drop to). The shared LoadControl stays at media3's rebuffer default (widening it
would regress audio/RELAY recovery).

## Fast entry + instant switching

One resolution yields every rung's URL plus the merge-audio partner, seeded into the URL cache, so a
quality switch is a local `replaceMediaItem` + one CDN range request — no second `/player`. The expanded
player **prefetches** the rendition while the Song/Video pill is showing (`prefetchVideoRendition`,
deduped, expiry-aware; skipped under RELAY, for a downloaded LOCAL video, and offline). A same-itag
switcher tap is a no-op.

## Cache-key safety (the container-mixing corruption class)

`CacheDataSource` serves cached spans regardless of the resolved URI, so two containers must never share
a key. Rung keys can't drift (the itag IS the key), which is also why exact-itag resolution has no
fallback format. The two keys that CAN drift — plain `video:<id>` (its itag flips 18/22 with the metered
cap) and `videoaudio:<id>` — track their last-resolved itag and **purge their cached spans on any
change** (`videoKeyItagCache` / `mergeAudioItagCache`, via the shared `seedPlainVideoKey`). Only
WEB-client resolutions seed the rung-URL table (a non-web fallback's URLs 403 past the 1 MiB wall). A
video error invalidates the rung key in play, the plain key and the merge-audio key (all from the same
dead response). `removeDownload` purges the whole key family.

## Downloads above the progressive ceiling

A progressive rung downloads as one file. An **adaptive** rung downloads video-only + a
**container-matched audio partner from the SAME response and client** (`PlaybackData.downloadAudioUrl` —
mp4/avc → AAC, webm/vp9 → Opus; no second `/player`, no client-disagreement mux failure - only when
that response carried no usable audio does a defensive second resolution run), verifies each
stream against its declared `contentLength`, then remuxes on-device (`VideoMuxer`, timestamp-interleaved
sample copy, no re-encode). The target is decoder-capability-gated but not metered-capped. A
`TRANSIENT` mux failure preserves `requestedVideoQuality` for the retry; only `INCOMPATIBLE` clears it
and falls back to the automatic progressive pick.

## The switcher UI

`VideoQualitySelector` is an over-media pill (the `VideoModePill` family, shared `overMediaChrome`
modifier). It opens ONE picker body, `VideoQualityMenu` (`NavigationTitle` heading + shared
`OnboardingChoiceCard` rows), presented two ways:
- **Inline** (portrait): the root bottom-sheet menu via `LocalMenuState`.
- **Fullscreen**: a fullscreen-LOCAL centered scrollable panel (`onOpen`) inside the overlay — the root
  bottom sheet fights the immersive landscape window's orientation, insets and z-order. Back closes the
  panel before exiting fullscreen.

The Settings default is **Settings → Player → Video quality** (hidden when videos are blocked).

## Backgrounding

`MainActivity.onStop` reverts video→audio (`setVideoMode(false)`). The re-prepared bare-`<id>` audio
must fetch bytes the audio cache never held, and that fetch can fail transiently without a clean 403. So
`MusicService.onPlayerError` routes an error within `REVERT_RECOVERY_WINDOW_MS` of the revert
(`VideoModeController.revertedToAudioWithin`, one-shot) to the URL-refresh recovery instead of parking
the player in an error — except under RELAY (deterministic URL; refreshing loops) and in a station.

## Untouched paths

RELAY (one fixed server rendition — quality keys never reach it, enforced at the `swapToVideoKey`
chokepoint), LOCAL (one baked quality, no switcher), cast and station broadcasts have no quality ladder.

## Proving it

`node tests/video-qualities.mjs <videoId>` resolves every rung + the audio + both download-mux audio
partners the app's exact way and, per rung, verifies the initial 206, a fresh-connection sweep past the
1 MiB pot wall, a 75% seek, and a full byte-verified drain to EOF. PASS/FAIL exit.

**Not testable here** (no Robolectric): muxed-file A/V sync, `MergingMediaSource` sync mid-stream,
quality-swap seek continuity, `VideoDecoderCaps`, and fullscreen orientation/insets — these need an
on-device pass.
