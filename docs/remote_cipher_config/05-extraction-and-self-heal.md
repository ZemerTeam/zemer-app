# 05 — From config to playing stream: extraction, precedence, and the mid-session self-heal

## Where configs are consumed: `FunctionNameExtractor`

File: `cipher/library/src/main/kotlin/com/zemer/cipher/FunctionNameExtractor.kt`. Before
commit `fdb1219` this file *contained* the config table as ~227 lines of hardcoded Kotlin;
now `getHardcodedConfig(hash)` is one line — `PlayerConfigStore.get(playerHash)` — plus
diagnostics (on a miss it logs the full known-hash list under tag
`Zemer_CipherFnExtract`, which is what you grep in logcat).

`extractSigFunctionInfo(playerJs, knownHash)` and `extractNFunctionInfo(playerJs, knownHash)`
both follow the same precedence, written into the code as a comment (cipher commit
`4a79a96`):

1. **Validated config first.** Resolve the hash (`knownHash` from the fetcher's URL if
   available, else `extractPlayerHash()` → URL patterns, else the MD5 alias) and look it
   up. A hit returns an expression-based `SigFunctionInfo`/`NFunctionInfo`
   (`isHardcoded = true`, `jsExpression` set).
2. **Legacy regex heuristics only on a miss.** The old `SIG_FUNCTION_PATTERNS` /
   `N_FUNCTION_PATTERNS` lists still run as a fallback for old-style players.

The rationale in the comment is the contract: config entries are proven against the live
CDN (HTTP 206) before they ship, while the legacy patterns are unanchored heuristics that
can false-match anywhere in ~2 MB of player JS. **A heuristic must never shadow a
validated config** — and a heuristic false positive must not block the unknown-player
forced refresh (next section). `FunctionNameExtractorPrecedenceTest.kt` pins this.

`extractSignatureTimestamp()` prefers the literal `signatureTimestamp`/`sts` in the player
JS and falls back to the config's `sts` — so the value sent in the InnerTube `/player`
request matches the player generation that will decipher the response.

## The self-heal: `CipherDeobfuscator.getOrCreateWebView()`

File: `cipher/library/src/main/kotlin/com/zemer/cipher/CipherDeobfuscator.kt`. This is the
moment the remote system pays off — **mid-session, at the exact playback attempt that
would otherwise fail**:

```kotlin
var sigInfo  = FunctionNameExtractor.extractSigFunctionInfo(playerJs, hash)
var nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs, hash)

if (sigInfo == null || nFuncInfo == null) {                 // EITHER side missing
    if (PlayerConfigStore.forceRefresh(missingHash = hash)) {
        sigInfo  = FunctionNameExtractor.extractSigFunctionInfo(playerJs, hash) ?: sigInfo
        nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs, hash) ?: nFuncInfo
    }
}
```

Three deliberate properties (the code comment spells them out, commit `4a79a96`):

- **EITHER side missing triggers the refresh** — a legacy-regex false positive on the sig
  side must not block recovery of the n side, and vice versa.
- **After a successful refresh BOTH sides re-extract** — so a validated config replaces
  any heuristic guess, not just fills the hole.
- `forceRefresh` returns true iff the hash is now in the table (doc 03), so the re-extract
  runs exactly when it can succeed and is skipped when it can't.

End-to-end timeline of a rotation, assuming the new entry is already on cipher `master`:
YouTube ships player `xxxxxxxx` → `PlayerJsFetcher` downloads it (its own 6 h TTL /
invalidation) → extraction misses → `forceRefresh("xxxxxxxx")` fetches the JSON →
table now covers it → re-extract → WebView built with the validated expressions → the song
plays. The user never sees the rotation. If the entry is NOT yet on `master`, the refresh
returns false, the 5-minute cooldown arms (only if GitHub was actually reached), and the
hourly monitor + email is what gets a human to push the entry (doc 06).

A second, blunter retry wraps every decipher call: `deobfuscateStreamUrl` catches any
exception, calls `PlayerJsFetcher.invalidateCache()` + `closeWebView()` and retries once
with fresh player JS (`isRetry = true` → `forceRefresh` of the JS, not the config).

## Keeping the cached WebView fresh: config-epoch rebuild + stream-rejection refresh

The triggers above only fire while *building* a WebView (incomplete extraction) or on a
decipher *exception*. Once a `CipherWebView` is built it is reused for the life of the
process — and that one WebView is shared by **every cipher (web) client**: WEB_REMIX,
WEB_CREATOR, TVHTML5, WEB. Two gaps in that reuse let a wrong cipher survive until a
force-stop+reopen, breaking *all* of those clients at once (cipher commit `2826208`):

1. **A config that self-heals AFTER the WebView was built was ignored until a restart.** The
   startup refresh — or a `forceRefresh` triggered by a *different* miss — could land a
   corrected entry for the current player while the already-built WebView kept using the old
   extraction. Fix: `PlayerConfigStore.configEpoch` advances whenever a refresh actually
   changes the table (doc 03); `getOrCreateWebView` records the epoch it built under and
   rebuilds when it advances. The epoch is snapshotted **before** the build, so a change
   landing mid-build (e.g. the async startup refresh racing prewarm) forces a rebuild on the
   next decipher instead of being masked as already-incorporated.

2. **A wrong-but-non-throwing signature was invisible.** A stale/wrong config — or a
   legacy-regex false positive that resolves to a real function — produces a wrong signature
   *without throwing*, so the exception-retry never fires and `forceRefresh` is never
   triggered (extraction looked complete). The only signal it was wrong is the CDN rejecting
   the stream — surfaced from **two** call sites so every cipher client is covered:
   - `MusicService.handleExpiredUrlError` (the ExoPlayer 403/410 path). Only **WEB_REMIX** skips
     HEAD validation, so only its bad URL reaches ExoPlayer and 403s.
   - `YTPlayerUtils`, when a `needsNTransform` (cipher) client fails `validateStatus` during
     resolution. **WEB_CREATOR / TVHTML5 / WEB** are HEAD-validated, so a wrong sig is caught here
     and never reaches ExoPlayer — without this site a WEB_REMIX-disabled user would never
     self-heal. Fired off a dedicated scope so the network refresh can't block the fall-through.

   Both call `CipherDeobfuscator.onStreamRejected()` → `PlayerConfigStore.refreshAfterStreamRejection()`
   (doc 03): a rate-limited re-fetch that — unlike `forceRefresh` — does NOT short-circuit when the
   hash is already present (it may be present but WRONG). A changed table advances the epoch → the
   shared WebView rebuilds on the next decipher → **every cipher client recovers, no force-stop**.
   On a real change the `webRemixFailedIds` set is cleared via `clearWebRemixFailures()` so
   resolution returns to WEB_REMIX rather than staying on a lower fallback.

`PlayerConfigStoreEpochTest` (epoch advances only on a real change) and
`PlayerConfigStoreCooldownTest` (the rejection cooldown is independent of `forceRefresh`'s)
pin the store-side guarantees; the WebView rebuild itself is integration-verified on-device.

## How the expressions execute: `CipherWebView`

`CipherWebView.create(context, playerJs, sigInfo, nFuncInfo)` loads the player JS into an
invisible WebView and appends export shims. For expression-based (config) entries:

```js
window._cipherSigFunc  = function(sig) { try { return mP(4,155,sig); } catch(e) { return null; } };
window._nTransformFunc = function(n)   { try { return (function(n){...g.Yx...})(n); } catch(e) { return n; } };
```

— the literal `INPUT` token is replaced with the parameter name (`.replace("INPUT", "sig")`
/ `.replace("INPUT", "n")`). Named-function (legacy) entries export the named function
instead, and a brute-force discovery pass exists as a last resort for the n-transform.
Runtime calls then are `webView.deobfuscateSignature(s)` and `webView.transformN(n)`, both
serialized by `CipherDeobfuscator.deobfuscateMutex` (the WebView has single-shot
continuation slots).

## The app side: who calls all this

`app/src/main/kotlin/com/jtech/zemer/utils/YTPlayerUtils.kt` (`playerResponseForPlayback`):

- `getSignatureTimestampOrNull()` → `CipherDeobfuscator.signatureTimestamp()` — fetches
  (or reuses) the player JS and returns *its* STS, sent in every `/player` request
  (`InnerTube.kt` `playbackContext`).
- `findUrlOrNull()` → `CipherDeobfuscator.deobfuscateStreamUrl(format.signatureCipher!!, videoId)`
  for ciphered web-client formats.
- post-selection → `CipherDeobfuscator.transformNParamInUrl(streamUrl)` (with
  `EjsNTransformSolver` as an alternate path).

The library is initialized once from the app's `Application` class via
`ZemerCipher.initialize(context, …)`, which is what calls `PlayerConfigStore.initialize`
+ `scheduleStartupRefresh` (doc 03). On-device verification: logcat tags
`Zemer_CipherConfig` (store), `Zemer_CipherFnExtract` (extraction), `YTPlayerUtils`
(`Playback: client=…, itag=…`).
