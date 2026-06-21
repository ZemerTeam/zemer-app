# 07 — Runbook: operating the system

## A. New player detected (the routine case)

Trigger: the player-monitor issue/email "New YouTube player detected: `<hash>`", or
deciphering failures in the field (`Zemer_CipherFnExtract: No hardcoded config for hash …`).

1. **Derive + validate** (needs `innertube_cookie.txt` at the repo root, gitignored):
   ```
   node tests/validate-player-config.mjs <hash>
   ```
   Accept nothing less than **HTTP 206** from the live CDN. The script prints a
   paste-ready entry including the MD5 alias.
2. **Edit the one file** — `library/src/main/assets/player_configs.json` in the
   **zemer-cipher** repo (via the submodule checkout is fine). Add the printed entry.
   There are no Kotlin or harness mirrors to sync; they all read this file.
   Then **regenerate the (cosmetic) dates file** so the song-details "Cipher support added"
   label stays in sync: `node tests/gen-player-dates.mjs` (commits the config commit's date
   into `player_dates.json`). This is a *separate* file old apps never fetch — see the note
   below; a mistake here only changes a UI label, never deciphering.
3. **Check the file** locally:
   ```
   cd cipher && ./gradlew :library:testDebugUnitTest     # parser + bundled-asset guards
   cd .. && node --test tests/player-configs.test.mjs    # harness loader (10 tests)
   node tests/config-covers.mjs <hash> cipher/library/src/main/assets/player_configs.json
   ```
   Remember: a duplicate hash/alias anywhere rejects the WHOLE file on every device —
   the tests catch this, run them.
4. **Push zemer-cipher `master` — this is the deploy.** Deployed apps pick the entry up
   within minutes-to-hours (forced refresh on breakage is immediate when the song fails;
   the startup TTL refresh is ≤ 6 h). No APK release.
5. **Bump the submodule pointer in zemer-app** afterwards so bundled defaults stay fresh
   for new installs. **Push order is load-bearing: zemer-cipher first, then the pointer**
   — the reverse leaves fresh clones / CI referencing a commit that isn't on the remote.
   (Per the working agreement: pushes only when explicitly authorized in the request.)

## B. Scheme change (new config shape)

A *scheme* change — fields added/changed in a way old parsers can't honor — still needs
code + an APK:

1. Change `PlayerConfigParser.kt` AND `tests/player-configs.mjs` AND the
   `config-parity/` fixtures together; one of the two test suites goes red if they drift.
2. Bump `SUPPORTED_SCHEMA_VERSION` (Kotlin) + `SUPPORTED_SCHEMA_VERSION` (JS) and the
   file's `schemaVersion` **only if the shape is breaking**. Consequence: every deployed
   old app rejects the new file and freezes on its last-good table until users update the
   APK — so prefer backward-compatible additions (old readers ignoring a new optional
   field is NOT a bump).
3. Build both APK variants (`:app:assembleDebug` + `:app:assembleRelease`) and run the
   full harness checks.

## C. Diagnosing a device that isn't healing

Logcat tags, in causal order:

| Tag | What to look for |
|---|---|
| `Zemer_CipherConfig` | `Loaded bundled configs (N hashes)`, `Overlaying cached remote configs`, `Remote configs applied (… changed=true)`, `Remote configs rejected: <reason>`, `forceRefresh skipped (cooldown)`, `Remote config fetch HTTP <code>` |
| `Zemer_CipherFnExtract` | `No hardcoded config for hash: <h>` + `Known hashes: …` (is the new hash in the list?), `USING EXPRESSION-BASED SIG` |
| `Zemer_CipherDeobfusc` | `Incomplete extraction for player <h> … forcing remote config refresh` |
| `YTPlayerUtils` | `Playback: client=…, itag=…` (the final outcome) |

Checks, cheapest first:

1. Is the entry actually on the **live URL**? Devices fetch raw `master`, not your local
   branch:
   `curl -s https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json`
2. Would a device **accept the whole file**?
   `node tests/config-covers.mjs <hash> <(curl -s …)` — an invalid file is rejected
   wholesale, and devices keep their last-good table; the textual presence of the hash
   means nothing.
3. Cooldown: a failure-triggered refresh runs at most once per 5 min (only when GitHub was
   actually reachable). A device that missed remotely will retry on the next failure after
   the cooldown; the startup refresh retries on any launch where the table is > 6 h old.
4. Cache state on device: `filesDir/cipher_cache/configs_remote.json` + `.meta`. A 304
   loop with stale content is impossible by construction (corrupt-body⇒meta purged
   together; atomic writes), so if you suspect it anyway, re-verify those two defenses
   (`PlayerConfigStoreCacheTest`) before inventing a new theory.
5. The entry deciphers but streams still die mid-song around ~1 MiB → that's the **pot
   binding** problem, not a config problem (see `tests/INVESTIGATION.md`; the pot must be
   bound to the videoId).

## D. Invariants to never break (each is enforced by a named test)

| Invariant | Enforced by |
|---|---|
| Bundled asset always valid | `BundledAssetTest.kt` |
| Kotlin parser ⇔ JS loader file-verdict parity | `ConfigParityFixturesTest.kt` + `player-configs.test.mjs` over `config-parity/` |
| n-IIFE template byte-identical in Kotlin / JS / validator | `NJsExpressionTemplateTest.kt` + template test in `player-configs.test.mjs` (golden file) |
| Config beats heuristic; heuristic never blocks self-heal | `FunctionNameExtractorPrecedenceTest.kt` |
| Validated remote table reaches memory even if disk fails | `PlayerConfigStoreApplyRemoteTest.kt` |
| No 304-lock (ETag without body / torn writes) | `PlayerConfigStoreCacheTest.kt` |
| forceRefresh: single-flight, cooldown under lock, offline doesn't arm cooldown | `PlayerConfigStoreForceRefreshTest.kt` |
| configEpoch advances only when the table actually changes (the cipher-WebView rebuild signal) | `PlayerConfigStoreEpochTest.kt` |
| forced and stream-rejection cooldowns are independent (neither starves the other) | `PlayerConfigStoreCooldownTest.kt` |
| Duplicate keys reject the whole file (both readers) | `PlayerConfigParserTest.kt` + `player-configs.test.mjs` |

Also non-negotiable, from the code comments rather than tests: config cache filenames must
never start with `player_` (PlayerJsFetcher purges that prefix in the shared
`cipher_cache/` dir), and `REFRESH_TTL_MS` mirrors `PlayerJsFetcher.CACHE_TTL_MS` — change
them together or config staleness and player staleness diverge.
