# Zemer → Google Play readiness plan

> **Status:** planning artifact on branch `feat/gp`. Nothing here is implemented yet.
> **Scope:** what it takes to publish a compliant Zemer build on Google Play, while keeping
> the GitHub/Obtainium build (full features, self-updater) as the primary distribution.

---

## 0. Context & strategy

**Precedent.** *Disco* (`com.omarkarimli.disco`, ~96k downloads, freemium w/ subscription) is a
Metrolist sibling fork — same lineage as Zemer — and is live on Play. This is empirical proof that
the inherited streaming pipeline (innertube `/player` + cipher deciphering + poToken) passes Play's
automated review and survives in production. So the streaming itself is **not** treated here as a
listing blocker; it is treated as an ongoing **reactive-takedown risk** (enforced on complaint, not
at submission).

**Core strategy.**
1. Ship Play as a **separate, stripped build flavor** (`play`) — not the GitHub build.
2. Use a **dedicated Google Play developer account** you are willing to lose, so a reactive
   takedown of this app cannot cascade to your main identity / other apps.
3. Keep **GitHub + Obtainium** as the canonical home (full feature set incl. self-updater) and the
   fallback if Play pulls the app.

**Why a flavor, not a fork:** several features are deterministic Play rejections (self-updater,
accessibility button-mapper, install-packages permission). They must be *absent* from the Play
artifact but *retained* in the GitHub artifact. A `distribution` flavor dimension does exactly this
from one codebase.

---

## 0.1 Decisions log (locked by owner)

| # | Decision | Choice | Section |
|---|---|---|---|
| 1 | Monetization v1 | **FREE** — no Play Billing | §8 |
| 2 | Anonymous login | **KEEP** the anonymous login option on Play (pooled-anon retained) | §5 |
| 3 | `applicationId` for Play | **SHARE `com.jtech.zemer`** (single id across both channels) | §3.5 |
| 4 | Accessibility button-mapper | **FULLY REMOVE** from the `play` flavor — all code/UI/manifest/res | §1.2 |
| 5 | Target audience | **13+ target audience + Everyone content rating** (NOT child-directed; no COPPA) | §6 |

---

## 0.2 Implementation status (branch `feat/gp`, uncommitted)

**Done & verified (both `githubDebug` + `playDebug` build; play APK dex-checked):**
- ✅ `distribution` flavor dimension (`github` default / `play`) + `ENABLE_INAPP_UPDATER` /
  `ENABLE_BUTTON_MAPPER` BuildConfig flags. — §9
- ✅ `play` manifest overlay strips install-perm + install receiver + Shizuku provider +
  AccessibilityService (`tools:node="remove"`). — §1
- ✅ In-app updater **fully excluded from `play`**: leaf files moved to `src/github`; Shizuku/libsu/
  rikka deps scoped to `githubImplementation`; call sites (App startup, MainActivity notifier +
  download dialog, NavigationBuilder route, AccountSettings row, SettingsScreen entry) routed through
  flavored seams (`distribution/UpdateIntegration.kt`, github real / play no-op). — §1.1
- ✅ Accessibility button-mapper removed from `play` per **option B**: service/bridge/setup-UI/
  AccessibilityUtils/ViewModel moved to `src/github`; `ButtonMapperBridge` no-op stub in `src/play`;
  setup route + Settings entry + onboarding step flavor-gated; inert input helpers left shared. — §1.2
- ✅ Dex verification: play APK contains **0** of UpdateChecker/AppInstaller/ButtonSetupScreen/
  ShizukuProvider/libsu/rikka (github contains all). Only an inert string literal of the service
  name remains in shared onboarding code (expected under option B).
- ✅ `scripts/ui-audit.sh` passes; no new string/dialog/color violations.

**Still TODO (deferred, not blocking the build):**
- ✅ `SYSTEM_ALERT_WINDOW` removed from `play` (overlay perm requested in onboarding but **no system
  overlay is ever created** — the floating mini player is in-app; verified). Stripped via play
  manifest overlay + onboarding step gated behind `REQUEST_OVERLAY_PERMISSION` flag. github unchanged.
  — §1.3
- ✅ Vestigial-permission review: `RECEIVE_BOOT_COMPLETED` removed from `play` (no boot receiver
  exists). `NEARBY_WIFI_DEVICES` **kept** (actively requested/used in onboarding). Legacy storage
  (`WRITE/READ_EXTERNAL_STORAGE`, already maxSdk-gated) kept — justify in Data Safety. — §2
- ✅ **Release (R8) verified** — `assembleGithubRelease` + `assemblePlayRelease` both build clean
  (`minifyGithubReleaseWithR8` / `minifyPlayReleaseWithR8` pass); no keep-rule breakage from the dep/
  code removal. Play **AAB** builds: `bundlePlayRelease` → `app/build/outputs/bundle/playRelease/
  app-play-release.aab` (~23 MB). github release stays an APK (`app-github-release.apk`) for sideload.
- ✅ **CI updated**: `release-build.yml` → `assembleGithubRelease` (paths/Crashlytics task repointed
  to the github flavor); `debug-build.yml` → `assembleGithubDebug`. A separate **play-AAB pipeline**
  (`bundlePlayRelease` → Play Console upload) is still TODO. — §11
- ⏳ Privacy/data-safety/account model/listing work (§§3–7, 10–12) — all still pending.

---

## 1. Hard policy blockers (must be resolved or the app is rejected/removed)

These are independent of the YouTube-ToS question.

### 1.1 In-app self-updater — Device & Network Abuse (HARD)
Play forbids an app updating itself by any path other than Play. The multi-method updater (#65,
Native / Root / Shizuku) is a direct violation and must be **completely absent** from the `play`
flavor.

Files/wiring to exclude (gate behind `BuildConfig.ENABLE_INAPP_UPDATER == false` and/or `src/play`):
- `utils/updater/AppInstaller.kt`, `ApkInstallController.kt`, `InstallReceiver.kt`, `Installer.kt`,
  `AppRestarter.kt`
- `utils/Updater.kt`, `utils/UpdateChecker.kt`
- `ui/component/UpdateDownloadDialog.kt`
- `ui/screens/settings/UpdaterSettings.kt`
- Entry points / nav: `App.kt`, `MainActivity.kt`, `ui/screens/NavigationBuilder.kt`,
  `ui/screens/settings/SettingsScreen.kt`, `ui/screens/settings/AccountSettings.kt`
- Manifest: `InstallReceiver` (`<receiver>` ~L198–204), Shizuku `<provider>` blocks (~L206–215),
  `tools:overrideLibrary` Shizuku entry (L36)
- Permission: `REQUEST_INSTALL_PACKAGES` (L27) — remove from `play` manifest overlay
- Deps (remove from `play`): `shizuku.api`, `shizuku.provider`, `libsu.core`

### 1.2 Accessibility button-mapper — Accessibility API misuse (HARD / HIGH RISK)
Play restricts `AccessibilityService` to **genuine accessibility use**; remapping hardware/media
buttons is a well-known rejection/removal trigger and requires an in-Console **Accessibility
declaration** (routinely rejected for non-a11y use). Hardware media keys already work via
`MediaButtonReceiver` / Media3 `MediaSession` without an accessibility service, so nothing of value
is lost on Play.

**DECISION (owner): FULLY REMOVE all button-mapper / accessibility code & assets from the `play`
flavor — zero trace in the Play artifact.** Full surface to eliminate:

*Code (github-only):*
- `accessibility/ButtonMapperAccessibilityService.kt`
- `utils/AccessibilityUtils.kt`
- `utils/ButtonMapperBridge.kt`
- `viewmodels/ButtonSetupViewModel.kt`
- `ui/screens/settings/ButtonSetupScreen.kt`

*Shared-code hooks to neutralize for `play` (these live in `src/main`):*
- `MainActivity.kt` — `ButtonMapperBridge` import (L228), `register`/`unregister` (L356/L368),
  `handleAccessibilityKey(event)` (L2048)
- `OnboardingScreen.kt` — accessibility-enable onboarding step + `isAccessibilityServiceEnabled`
  check (L2044)
- `NavigationBuilder.kt` — `ButtonSetup` route; `SettingsScreen.kt` — entry that opens it

*Manifest / resources (github-only overlay):*
- `<service>` + `BIND_ACCESSIBILITY_SERVICE` permission + `<meta-data>` (manifest ~L257–264)
- `res/xml/accessibility_service_config.xml`
- button-mapper user-facing strings in `metrolist_strings.xml`

*Implementation approach:* move the five code files into `src/github/`; provide `src/play/` no-op
stubs for any symbol shared code calls (a no-op `ButtonMapperBridge`, a `handleAccessibilityKey`
returning `false`); flavor-gate the onboarding step + Settings entry so they never render in `play`.
Net: the Play APK contains no accessibility service, permission, config, or button-setup UI.

*⚠️ Scope discovery (deeper than the file list above):* the button-mapper is also woven into
`MainActivity`'s **input dispatch**, which cannot move to a flavor source set (members of the shared
`MainActivity`):
- fields `dpadKeyMap` (+ its flow collector ~L418), `hatTracker` (`HatInputTracker`)
- methods `handleAccessibilityKey`, `handleMappedKeyEvent`, `isProtectedKey`,
  `dispatchGenericMotionEvent` override
- shared util `utils/ButtonInputCapture.kt`

Two ways to handle the `MainActivity` part:
- **(A) Deep removal / zero trace:** extract all remap input-dispatch into a flavored
  `InputRemap` seam (github = real, play = no-op) that `MainActivity` delegates to. Cleanest "zero
  trace" but a non-trivial refactor of the input path (carries D-pad/key-handling risk).
- **(B) Policy-relevant removal:** remove from `play` the AccessibilityService + bridge + button-setup
  UI + manifest/perm (the actual Play blockers), and leave the inert local input helpers shared
  (`dpadKeyMap` stays empty in `play` → dispatch overrides are pass-through, never triggered). Lower
  risk; not literally zero-trace but no policy surface remains. **← owner decision needed.**
(NB: `values/strings.xml` / `values-iw/strings.xml` "accessibility" hits are content-descriptions,
not the button-mapper, and are managed separately — do not edit per project rules.)

### 1.3 `SYSTEM_ALERT_WINDOW` (overlay) — REVIEW (likely remove)
Manifest L16. Heavily scrutinized; justify the exact feature or remove from `play`. Identify the
consumer before deciding (likely droppable for the Play flavor).

---

## 2. Full permission audit (per-permission verdict for the `play` overlay)

| Permission | Line | Verdict for `play` | Notes |
|---|---|---|---|
| `INTERNET` | 5 | keep | core |
| `ACCESS_NETWORK_STATE` | 7 | keep | connectivity checks |
| `POST_NOTIFICATIONS` | 6 | keep | playback notification |
| `WAKE_LOCK` | 9 | keep | playback |
| `FOREGROUND_SERVICE` | 10 | keep | media service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 11 | keep | declare FGS use (see §3.1) |
| `FOREGROUND_SERVICE_DATA_SYNC` | 12 | keep/justify | sync + downloads; declare FGS use |
| `RECORD_AUDIO` | 30 | keep + disclose | recognition; needs prominent disclosure (see §4.2) |
| `READ_MEDIA_AUDIO` | 26 | keep/justify | local audio |
| `WRITE_EXTERNAL_STORAGE` | 22 | scope/remove | legacy; ensure maxSdk gate, justify |
| `READ_EXTERNAL_STORAGE` | 24 | scope/remove | legacy; prefer scoped/media perms |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 13 | keep/justify | allowed for players; justify in form |
| `PICTURE_IN_PICTURE` | 15 | keep if used | verify |
| `NEARBY_WIFI_DEVICES` | 14 | verify/remove | only if cast/nearby actually used |
| `RECEIVE_BOOT_COMPLETED` | 8 | verify/remove | only if a boot receiver is genuinely needed |
| `REQUEST_INSTALL_PACKAGES` | 27 | **REMOVE** | updater-only — §1.1 |
| `SYSTEM_ALERT_WINDOW` | 16 | **REMOVE/justify** | §1.3 |
| `BIND_ACCESSIBILITY_SERVICE` | 257 | **REMOVE** | §1.2 |

Action: produce the final `play` manifest overlay; every retained permission must map to a feature
and (where sensitive) to a Data Safety / declaration entry.

---

## 3. Android platform / Play technical requirements

### 3.1 Foreground service declarations
Manifest declares `foregroundServiceType="mediaPlayback"` (Media3 service ~L229–236) and
`dataSync` (~L242, L252). Android 14+ / Play require **per-type justification in Console**:
- `mediaPlayback` — straightforward for a music player.
- `dataSync` — must justify (library/whitelist sync, downloads). Confirm both `dataSync` services
  are still needed; minimize.

### 3.2 App Bundle, target API, ABIs, page size
- [ ] Output **AAB** (Play requires App Bundle), not APK, for `playRelease`.
- [ ] `targetSdk 36` — current/ahead, OK.
- [ ] ABIs `arm64-v8a` + `armeabi-v7a` — AAB ABI splits handle this.
- [ ] 16 KB page-size alignment already addressed (libcoverart fix) — re-verify in AAB.
- [ ] R8 / `isMinifyEnabled = true` — `playRelease` must build clean (keep rules intact).

### 3.3 Deep links / App Links
Multiple `intent-filter android:autoVerify="true"` VIEW filters (e.g. L82, L112, L138, L158).
- [ ] Confirm verified hosts and that Digital Asset Links (`assetlinks.json`) are hosted if you
      want verified App Links; otherwise they degrade to regular links. No blocker, but review for
      any YT-host framing.

### 3.4 Exported components
Exported: main activity (L65), `RecognizeMusicDialogActivity` (L288/L229), widget receivers
(`MusicWidgetReceiver` L276), `MediaButtonReceiver` (L268), Media3 service (L229).
- [ ] Audit each exported component for intent-redirection / unprotected actions before submission.

### 3.5 Cross-channel signature migration (UX caveat)
GitHub builds are signed with your `release` key; Play re-signs via **Play App Signing**. A user
with the GitHub build installed **cannot** in-place update to the Play build (signature mismatch),
and vice-versa, if `applicationId` is shared. Decide:
- Keep `applicationId = com.jtech.zemer` for Play (clean store identity, but cross-channel switch
  requires uninstall/reinstall — document for users), **or**
- Use a distinct `applicationId` for the Play flavor (`applicationIdSuffix = ".play"`); two
  installable apps that can coexist; isolates the Play experiment from the GitHub install base.

**Recommendation: distinct id (`com.jtech.zemer.play`).** Rationale:
- Play App Signing already forces a *different* signing cert, so in-place cross-channel migration is
  impossible **either way** — "same id for seamless upgrade" is a non-benefit here.
- A distinct id isolates the throwaway-account Play experiment from your established identity and
  GitHub users; if Play pulls the app, the canonical `com.jtech.zemer` and its users are untouched.
- Cleaner telemetry (separate crash/analytics streams per channel) and side-by-side install for
  testing.
- Cost: a **second Firebase app registration** (add `com.jtech.zemer.play` to the Firebase project;
  ship a flavor-specific `google-services.json`), and deep-link `assetlinks.json` must list the Play
  signing-cert fingerprint. Both are one-time and small.
- ⚠️ `applicationId` is permanent after first Play upload — choose deliberately.

**DECISION (owner): SHARE `applicationId = com.jtech.zemer` across both channels** (no suffix; the
`play`/`github` split is build/manifest/deps only, not identity). Implications & to-dos:
- [ ] Document for users: **cross-channel switch needs uninstall/reinstall** (GitHub-signed ↔
      Play-signed certs differ; no in-place update). Within a channel, updates are normal.
- [ ] **Single Firebase app** — existing `google-services.json` (package `com.jtech.zemer`) serves
      both flavors; no second registration needed.
- [ ] `assetlinks.json` for deep links must list **both** signing-cert SHA-256 fingerprints (your
      GitHub `release` key **and** the Play App Signing key) so App Links verify on either channel.
- [ ] Crash/analytics from both channels land in the **same Firebase app**; add a `BuildConfig`
      channel custom-key (e.g. `distribution = github|play`) to disambiguate in Crashlytics.
- [ ] `versionCode` scheme (§3.6) must stay collision-free across channels since they share an id.

### 3.6 Versioning across flavors
- [ ] Define a `versionCode` scheme so `github` and `play` artifacts never collide (e.g. offset, or
      Play-only monotonic codes managed in Console). Avoid uploading a code ≤ an existing one.

---

## 4. Privacy, data safety & disclosures (required to publish)

### 4.1 Privacy policy (required URL)
Public privacy policy covering: microphone audio (recognition → `amp.shazam.com`),
account/cookie/`visitorData` data, Firebase **Crashlytics + Crashlytics NDK + Analytics**,
**Firestore** (artist-whitelist sync), downloads/cache. Link in Console + in-app.

### 4.2 Microphone prominent disclosure
Recognition (`ui/screens/recognition/RecognizeMusicDialogActivity.kt`,
`recognition/RecognitionAudioCapture.kt`) must show an **in-context disclosure + consent before the
first capture**, separate from the OS permission dialog. Required because mic audio leaves the
device (sent to Shazam).

### 4.3 Data Safety form
Declare accurately: audio (recognition), app activity, device/identifiers, crash logs; transit
encryption; data-deletion path. Must match the actual SDKs (Firebase set above).

### 4.4 Account deletion (required when accounts exist)
Provide BOTH an in-app deletion path AND a **publicly reachable web URL** for account/data deletion
(Play requirement for apps with accounts). Document what "delete" means for personal login vs the
pooled anonymous account vs local DB.

### 4.5 Content rating
Complete the IARC questionnaire. Note KidZone (see §6).

---

## 5. Account model review (Zemer-specific risk)

The **pooled "anonymous" account** (one shared Google account across all anon users; has `SAPISID`,
cleared `dataSyncId`) is a Zemer modification, not vanilla Metrolist. On Play it can read as
credential / account-sharing abuse if examined.

**DECISION (owner): KEEP the anonymous login option on Play** (pooled-anon retained). Accepted as an
incremental reactive-review risk, not a listing blocker. Mitigations to keep in mind: ensure the
pooled account holds no PII, and that the Data Safety / privacy policy describe shared-session
behavior accurately.

(See `extensions/AccountState`, `App.kt`/`LoginGateScreen`. Whitelist sync via Firestore is
account-independent and stays. **Note interaction with §6:** if the audience ends up including
under-13 children, anonymous/Google login for child users pulls in COPPA parental-consent rules.)

---

## 6. Target audience — DECIDED: 13+ target audience + "Everyone" content rating

**DECISION (owner): target audience 13+, content rating Everyone. NOT child-directed → COPPA /
Families do NOT apply.** Easiest path; no per-child engineering.

Two independent Play settings — keep them straight:
- **Content rating (IARC):** driven by content maturity. Zemer has **no explicit content** (Kosher
  whitelist / KidZone filtering), so it earns **Everyone** automatically. ✅
- **Target audience:** drives COPPA/Families, *independently of the content rating*. Selecting **13+**
  (no under-13 band) keeps the app out of the child-directed bucket — even though the content is
  clean enough for any age.

Consequences / to-dos:
- [ ] Console "Target audience and content": select **13+**; do **not** tick any under-13 age band.
- [ ] Complete IARC questionnaire → expect **Everyone**.
- [ ] **No neutral age screen, no per-child feature gating** — recognition, analytics, and login all
      stay as-is for all users. (This is the whole point of choosing 13+.)
- [ ] **Keep the listing credible as a general music app:** frame KidZone as a *parental curation*
      tool, not "an app for young children." If store copy/branding/screenshots read as
      child-directed, Play review can reclassify you as child-directed and pull you into COPPA anyway
      (see §10 branding).

Younger children may still use the app (e.g. on a parent's device) — the 13+ setting only means
under-13 is not the *declared* target audience, which is what avoids the Families/COPPA regime.

*(Path not taken: selecting an under-13 band would have required a neutral age screen, disabling
recognition/analytics/login for child users, and Families declarations — only worth it to get listed
in Play's Kids/Families section, which is not a goal here.)*

---

## 7. Licensing / open-source obligations

Project is **GPL-3.0** (`LICENSE`), inherited from Metrolist.
- [ ] **Source offer:** GPL requires providing corresponding source to recipients. Keep the public
      repo (or a written offer) and reference it.
- [ ] **GPLv3 + Play tension:** GPLv3's anti-tivoization/installation-information clauses have
      historically conflicted with app stores; GPL apps *are* distributable on Play, but ensure the
      listing doesn't impose terms that contradict the GPL. Provide install/source info via the repo.
- [ ] **Third-party attributions:** confirm bundled components (bento4 native, Shizuku [excluded in
      `play`], libsu [excluded], NewPipe-derived code, vibra/MusicRecognizer port in recognition)
      have license notices surfaced (About screen / NOTICE).
- [ ] Trademark: avoid YouTube / YouTube Music marks and confusingly-similar branding in name/icon.

---

## 8. Monetization — FREE (owner decision)

**DECISION (owner): ship FREE for v1, no Play Billing.** Declare "no in-app purchases" and "no ads"
in the listing/Data Safety. (Billing can be added later if desired; out of scope now.)

---

## 9. Build & flavor implementation (concrete)

- [ ] Add `flavorDimensions += "distribution"` in `app/build.gradle.kts`.
- [ ] `productFlavors { github { isDefault = true } ; play { } }`.
- [ ] `BuildConfig.ENABLE_INAPP_UPDATER` = true (`github`) / false (`play`).
- [ ] Source sets `src/github/` and `src/play/` with separate `AndroidManifest.xml` overlays
      (permissions/services per §1–§2) and any flavor-specific Kotlin (updater/accessibility stubs).
- [ ] Dependency scoping: `githubImplementation(shizuku/libsu)`; none in `play`.
- [ ] Keep `applicationId` decision from §3.5 consistent with signing/migration.
- [ ] CI: add a `playRelease` **AAB** job (own workflow or extend release-build); ensure
      `google-services.json` + keystore secrets feed it; upload R8 mapping + NDK symbols.
- [ ] Build matrix green: `assembleGithubDebug`, `assembleGithubRelease`, `assemblePlayDebug`,
      `bundlePlayRelease`.

---

## 10. Store listing & assets

- [ ] App title (≤30 chars), short desc (≤80), full desc — generic music framing; **no** "YouTube
      Music client" language or YT marks anywhere in metadata.
- [ ] Hi-res icon (512), feature graphic (1024×500), phone + 7"/10" tablet screenshots.
- [ ] Category: Music & Audio. Contact email, website, privacy policy URL.
- [ ] Declarations: ads (none if free), in-app purchases (none for v1), data safety, target audience,
      government/financial = no.

---

## 11. Submission & rollout mechanics

- [ ] Create dedicated developer account (§0); pay $25.
- [ ] Enroll **Play App Signing**; safeguard upload key (existing `release` keystore).
- [ ] Upload `bundlePlayRelease` AAB to **Internal testing**; smoke test: playback, search,
      recognition (mic consent), downloads, widget; **verify no updater/accessibility UI present**.
- [ ] Satisfy **new-account testing requirement** (currently closed testing with a tester cohort for
      ~14 days before production — verify exact current threshold in Console at submission time).
- [ ] Review **pre-launch report** (automated device run) for crashes/permission flags.
- [ ] Production **staged rollout** (e.g. 10% → 50% → 100%).

---

## 12. Post-launch risk management

- [ ] Keep GitHub/Obtainium build as canonical (full features + updater) and as the takedown fallback.
- [ ] Monitor Play Console policy notices; keep a re-publish/appeal plan.
- [ ] Don't tie YT-client framing to the Play dev account in any public channel.
- [ ] Track which features were stripped for `play` so future changes don't accidentally re-add a
      blocker (updater / accessibility / install-packages).

---

## Master checklist (one glance)

**HARD blockers (Play will reject/remove):**
- [ ] Self-updater fully excluded from `play` (code + manifest + perms + deps) — §1.1
- [ ] Accessibility button-mapper excluded from `play` — §1.2
- [ ] `REQUEST_INSTALL_PACKAGES` removed — §1.1 / §2
- [ ] `BIND_ACCESSIBILITY_SERVICE` removed — §1.2 / §2
- [ ] AAB output for `playRelease` — §3.2
- [ ] Privacy policy URL — §4.1
- [ ] Mic prominent disclosure before capture — §4.2
- [ ] Data Safety form accurate — §4.3
- [ ] Account-deletion in-app + web URL — §4.4
- [ ] Content rating completed — §4.5
- [ ] Foreground-service-type justifications — §3.1

**Strongly advised:**
- [ ] `SYSTEM_ALERT_WINDOW` removed/justified — §1.3
- [ ] Prune vestigial perms (`NEARBY_WIFI_DEVICES`, `RECEIVE_BOOT_COMPLETED`, legacy storage) — §2
- [ ] Pooled-anon account decision — §5
- [ ] Target audience = adults / not child-directed (avoid Families/COPPA) — §6
- [ ] GPL source offer + third-party attributions surfaced — §7
- [ ] Dedicated developer account — §0/§11

**Mechanics:**
- [ ] `distribution` flavor (`github`/`play`) + `ENABLE_INAPP_UPDATER` — §9
- [ ] Cross-channel signature/`applicationId` decision documented — §3.5
- [ ] `versionCode` scheme across flavors — §3.6
- [ ] Play App Signing — §11
- [ ] Internal → closed (cohort/14d) → staged rollout — §11
- [ ] Pre-launch report clean — §11

---

## Open decisions — status

1. **Monetization v1:** ✅ DECIDED — **free**, no Billing. — §8
2. **Anonymous login on Play:** ✅ DECIDED — **keep** the anon option (pooled-anon). — §5
3. **`applicationId` for Play:** ✅ DECIDED — **share `com.jtech.zemer`** across both channels. — §3.5
4. **Accessibility button-mapper:** ✅ DECIDED — **fully removed** from `play`. — §1.2
5. **Target audience:** ✅ DECIDED — **13+ target audience + Everyone content rating**, not
   child-directed (no COPPA). — §6

> Policy specifics (testing-cohort size/duration, FGS declaration UI, account-deletion URL rules)
> change over time — re-verify against current Play Console requirements at submission.
