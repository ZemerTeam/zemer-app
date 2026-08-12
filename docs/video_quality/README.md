# Video quality (beyond 720p: streaming, downloads, and the in-player switcher)

Video mode used to play whatever **progressive muxed** format YouTube served — which for a music
video is usually **360p** (itag 18), occasionally 720p (itag 22), because only `streamingData.formats`
was ever considered. This feature exposes the **full quality ladder** — the adaptive video-only
formats **144p … 2160p** plus progressive — as a selectable, streamable, downloadable set, with **no
new APK dependencies** (framework `MediaMuxer`/`MediaExtractor` + media3's `MergingMediaSource`).

The non-negotiable invariants live in `AGENTS.md` §The quality ladder / §The switcher / §The rebuffer
guard / §Downloads. This doc is the map. Everything was proven against the live CDN first
(`tests/video-qualities.mjs`) before the app code was written.

## The pieces

| File | Role |
| --- | --- |
| `playback/VideoQualityLogic.kt` | **Pure, JVM-tested.** Builds the ladder (one rung per qualityLabel, avc1 > vp9 > av01, progressive wins its label), resolves a target to the best rung at-or-below, the rebuffer-downgrade math, codec→decoder-mime mapping. |
| `playback/VideoRendition.kt` | The cache-key grammar: `video:<id>` (automatic), `video:<id>:p<itag>` / `:q<itag>` (explicit progressive/adaptive rung), `videoaudio:<id>` (the merge-audio partner). The itag lives IN the key so rungs can't share spans. |
| `playback/VideoDecoderCaps.kt` | `MediaCodecList` capability gate — never offer a rung this device can't decode (WEB_REMIX's 1440p/2160p are vp9-only). |
| `playback/VideoModeController.kt` | Owns the swap state machine: entry at the target rung, the switcher, the rebuffer guard, error handling. All main-thread-confined. |
| `utils/YTPlayerUtils.kt` | Resolution: one video response yields the chosen format + **every rung's URL** + the **merge-audio** partner + the **download-audio** partner (all from the same response, pure-local cipher work). |
| `playback/MusicService.kt` | The `ResolvingDataSource` branches (`video:` / `videoaudio:`), URL-cache seeding + itag-drift purge, `MergingMediaSource` wiring, `prefetchVideoRendition`. |
| `utils/VideoMuxer.kt` | Framework remux of video-only + audio → MP4 (avc1) / WebM (vp9, API 29+). Classifies failures transient vs incompatible. |
| `playback/MediaStoreDownloadManager.kt` | The two-stream adaptive download + mux. |
| `ui/player/VideoQualitySelector.kt` + `ui/menu/VideoQualityMenu.kt` | The over-media pill + the shared picker body (`NavigationTitle` + `OnboardingChoiceCard`). |

## Streaming a rung

`VideoModeController.enterVideoMode` picks the entry rung from `effectiveQualityTarget` (the in-player
pick if any, else the Settings default) against the item's ladder. When the ladder is already known
(prefetched — see below) it enters DIRECTLY at that rung; otherwise it enters on the plain `video:<id>`
automatic pick and `onVideoQualitiesResolved` upgrades position-continuously when the ladder lands.

A **progressive** rung is one muxed stream. An **adaptive** rung (`:q`) is video-only, so
`createMediaSourceFactory` wraps it in a `MergingMediaSource` that pairs it with the item's audio
stream under the `videoaudio:<id>` namespace. Both halves resolve through the same
`ResolvingDataSource`; the merge audio is always resolved at **HIGH** so its itag agrees across the
video branch, the live merge branch, and prefetch (the itag-drift purge depends on that).

### Honor the user's choice

An explicit quality — **Settings default OR in-player pick** — is honored on **every** connection,
metered included. It is a deliberate choice, never silently overridden: no metered gate, no bandwidth
pre-gate, no error-time AUTO pin. Data/stutter protection is confined to where it does not override
the user: the **AUTO** pick (the default-default) keeps its metered bitrate cap, and the reactive
rebuffer guard drops the CURRENT video one rung when it *actually* stalls (per-video; a new video
starts fresh at the user's setting).

### The rebuffer guard (never keep stuttering)

A `STATE_BUFFERING` after `READY` on a streaming rendition is a mid-play stall. Two stalls within 45s
— or a single stall within 15s of first reaching READY — drop exactly ONE rung (`rungBelow`), so
playback settles on the highest rung that actually plays (2160p → 1440p → 1080p → 720p, stopping the
moment 720p is stable). It deliberately does NOT bandwidth-gate a multi-rung jump: a rung's `bitrate`
is its PEAK (above its sustained average) and media3's estimate is depressed right after a stall, so a
bandwidth jump over-dropped (2160p → 480p when 720p was fine). Seek-caused buffering is exempt via a
timestamp grace window; a swap's own prepare never counts; LOCAL/audio never count; AUTO never
downgrades. The shared LoadControl is left at media3's rebuffer default (widening it would regress
audio/RELAY recovery).

## Fast entry + instant switching

One resolution resolves **every rung's URL** plus the merge-audio partner from the same response
(`videoRungUrls` / `mergeAudioUrl` — pure-local, no extra network), seeded into the URL cache. So a
quality switch is a local `replaceMediaItem` + one CDN range request — no second `/player` round-trip.
The expanded player also **prefetches** the rendition while the Song/Video pill is showing
(`prefetchVideoRendition`, deduped/relay-gated/offline-and-LOCAL-skipped), so a Video tap starts with a
single range request. A same-itag switcher tap (tapping the quality already playing) is a no-op.

## Cache-key safety (the container-mixing corruption class)

`CacheDataSource` serves cached spans regardless of the resolved URI, so two containers must never
share a key. The itag-suffixed rung keys can't drift (the itag IS the key). The **two keys that can**
drift — the plain `video:<id>` automatic key (its itag flips 18/22 with the metered cap) and
`videoaudio:<id>` (the audio itag flips) — each track their last-resolved itag and **purge their
cached spans on any change** (`videoKeyItagCache` / `mergeAudioItagCache`, via the shared
`seedPlainVideoKey`). Only WEB-client resolutions seed the rung-URL table (a non-web fallback's URLs
403 past the 1 MiB wall). A video error invalidates the plain key AND the rung key AND the merge-audio
key (all from the same now-dead response) so a re-entry re-resolves fresh. `removeDownload` purges the
whole key family.

## Downloads above the progressive ceiling

A progressive rung downloads as one file. An **adaptive** rung downloads video-only + a
**container-matched audio partner resolved from the SAME video response and client**
(`PlaybackData.downloadAudioUrl` — mp4/avc → AAC, webm/vp9 → Opus; no second `/player`, no
client-disagreement mux failure), verifies each stream against its declared `contentLength`, then
remuxes on-device (`VideoMuxer`, interleaved sample copy — no re-encode). The download target is
decoder-capability-gated but honored as chosen (only AUTO is metered-capped). A **transient** mux
failure (disk I/O) preserves the quality for a retry; only a **deterministic** incompatibility clears
it and falls back to the automatic progressive pick.

## The switcher UI

`VideoQualitySelector` is an over-media pill (the `VideoModePill` family — theme scrim + hairline
ring, one `overMediaChrome` modifier). It opens ONE picker body, `VideoQualityMenu` (`NavigationTitle`
heading + shared `OnboardingChoiceCard` rows — Auto with "Adjusts to your connection", each rung with
its resolution), presented two ways:
- **Inline** (portrait): the root bottom-sheet menu via `LocalMenuState`.
- **Fullscreen** (landscape, immersive): a fullscreen-LOCAL centered scrollable `Surface` panel
  (`onOpen`) drawn inside the overlay, so it inherits the landscape/immersive window and z-order — the
  root bottom sheet fought all three. Back closes the panel before exiting fullscreen.

The Settings default lives in **Settings → Player → Video quality** (hidden when videos are blocked).

## Untouched paths

RELAY (one fixed server rendition — quality keys never reach it, enforced at the `swapToVideoKey`
chokepoint), LOCAL (a downloaded muxed file, one baked quality — no switcher), cast, and station
broadcasts are all left exactly as before.

## Proving it

`node tests/video-qualities.mjs <videoId>` resolves every rung + the audio + both download-mux audio
partners (mp4/AAC, webm/Opus) the app's exact way, and per rung verifies initial 206, a
fresh-connection sweep past the 1 MiB pot wall, a 75% seek, and a full byte-verified drain to EOF
(the download proof). PASS/FAIL exit. Measured live (2026-08-11): every rung on a full-avc1 video and
a 2160p/1440p-vp9 video passed, including the 369.6 MB 4K drain, and both mux-audio partners stream.

**Not testable here** (no Robolectric): the muxed file actually *playing* with correct A/V sync,
`MergingMediaSource` sync mid-stream, quality-swap seek continuity, `VideoDecoderCaps`, and the
fullscreen orientation/inset behavior — these need an on-device pass.
