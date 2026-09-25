# 01 — Why this exists: player rotation from zero

## What breaks, mechanically

YouTube's media URLs are protected by two obfuscated transforms embedded in a ~2 MB
JavaScript file (`player_ias.vflset/.../base.js`, "the player"):

1. **Signature decipher (`sig`)** — web clients receive `signatureCipher` instead of a
   direct URL: a query string of `s=` (an obfuscated signature), `sp=` (the param name to
   put the fixed signature under, usually `sig`), and `url=`. The app must run YouTube's
   own scrambling function in reverse to turn `s` into a valid signature. Done by
   `CipherDeobfuscator.deobfuscateStreamUrl()` (cipher repo,
   `library/src/main/kotlin/com/zemer/cipher/CipherDeobfuscator.kt`), called from
   `app/.../utils/YTPlayerUtils.kt` (`findUrlOrNull`).
2. **n-transform** — every stream URL carries an `n=` query param. If it is not replaced
   with the player's transformed value, the CDN throttles or 403s. Done by
   `CipherDeobfuscator.transformNParamInUrl()`, called from `YTPlayerUtils.kt` right after
   URL selection.

Both transforms are *defined inside the player JS* and are renamed/restructured every time
YouTube ships a new player build. Each build is identified by an 8-hex-char hash in its URL
(e.g. `https://www.youtube.com/s/player/16ee6936/player_ias.vflset/en_GB/base.js`).
Rotations are frequent — the current bundled table covers 296 player builds spanning
sts 20602→20686 (see `player_configs.json`), i.e. multiple rotations per month.

There is a third per-player value: **`signatureTimestamp` (STS)** — an integer the app must
send in the InnerTube `/player` request (`InnerTube.kt`, `playbackContext`). Critically, it
must match the player that will *decipher* the response: during A/B rollouts, different
fetch paths can land on different player generations, and a signature minted for player A
but deciphered with player B produces a URL the CDN 403s. That is why
`YTPlayerUtils.getSignatureTimestampOrNull()` asks `CipherDeobfuscator.signatureTimestamp()`
— the STS of the *exact player JS the cipher WebView will use* (cipher commit `6b47be6`).

## Why modern players can't be solved by regex extraction

Older players exposed the transforms as named global functions; regex extraction
(`FunctionNameExtractor.SIG_FUNCTION_PATTERNS` / `N_FUNCTION_PATTERNS` — still present as a
legacy fallback) could find them. Current players are **VM-dispatched**: the logic lives in
an interpreter loop over an obfuscated instruction array, and there is no named function to
extract. What still works is *calling into* the loaded player with the right entry points:

- **sig**: somewhere in the player there is a dispatcher callable as
  `name(intA, intB, scrambledSig)` — e.g. `mP(4,155,INPUT)` for player `16ee6936`. The two
  integer constants select the decipher routine inside the VM.
- **n**: the player defines a URL-wrapper class (e.g. `g.Yx`) whose constructor+`get('n')`
  round-trip applies the n-transform as a side effect. The app exploits that with a fixed
  IIFE template (see doc 04 for the exact bytes).

So a "config" for a player is exactly three values + an alias:

```json
"16ee6936": { "sig": "mP(4,155,INPUT)", "nClass": "Yx", "sts": 20613, "aliases": ["ca366632"] }
```

The alias is the **md5-of-first-10000-bytes fallback hash**: when the player hash can't be
parsed from a URL, `FunctionNameExtractor.extractPlayerHash()` computes
`md5(playerJs.take(10000))` truncated to 8 hex chars and looks that up instead — so every
entry carries both identities of the same player.

## Why entries must be validated empirically, not derived by inspection

Hard-won fact (memory + `tests/INVESTIGATION.md`): **VM constant pairs are not unique**.
Multiple `(intA, intB)` pairs make the dispatcher return *a* plausibly-deciphered string;
only one is the signature the CDN accepts. A config that "deciphers correctly" by string
inspection can still 403 on every stream. The only ground truth is the live CDN:

```
node tests/validate-player-config.mjs <hash>
```

deciphers a *real* stream with the candidate config and GETs bytes — **HTTP 206 = correct,
403 = wrong** (`tests/validate-player-config.mjs`, header comment). It also auto-enumerates
candidates and prints a paste-ready JSON entry.

A related CDN fact that shapes the whole streaming area (memory, validated in `tests/`):
googlevideo serves the **first 1 MiB of any stream free, then 403s every new connection**
unless the URL's `pot=` token is bound to the videoId. A config can therefore look fine for
the first megabyte and die mid-song — the harness drains past the wall to prove otherwise.

## The cost model that motivated remote configs

Before (`FunctionNameExtractor` pre-`fdb1219`): a rotation → edit hardcoded Kotlin → build
→ sign → release → users update. Playback broken fleet-wide for the entire pipeline latency.

After: a rotation → derive + 206-validate an entry → **push one JSON line to zemer-cipher
`master`** → every deployed app self-heals on its next config fetch — at startup (6 h TTL)
or *immediately* when deciphering breaks (the forced refresh, doc 03/05). APK releases are
only needed for *scheme* changes (new config shape ⇒ `schemaVersion` bump, doc 02).
