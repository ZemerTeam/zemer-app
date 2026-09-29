# 3 · Extraction, precedence & the self-heal

## Config over heuristic (`FunctionNameExtractor.kt`)

`getHardcodedConfig(hash)` is `PlayerConfigStore.get(hash)` plus diagnostics (on a miss it logs
`No hardcoded config for hash: <h>` and `Known hashes: …` under `Zemer_CipherFnExtract`).

`extractSigFunctionInfo(playerJs, knownHash)` and `extractNFunctionInfo(playerJs, knownHash)` follow
one precedence:

1. **Validated config first** - resolve the hash (`knownHash` from the fetcher's URL, else
   `extractPlayerHash()`: URL patterns, else the md5 alias) and look it up. A hit returns an
   expression-based info (`isHardcoded = true`, `jsExpression` set).
2. **Legacy regex patterns only on a miss.**

Config entries are CDN-proven; the patterns are unanchored heuristics that can false-match anywhere in
the ~2 MB player. **A heuristic must never shadow a validated config**
(`FunctionNameExtractorPrecedenceTest`), **and a false positive on one side must never block the
forced refresh the other side's miss triggers.** If both sides false-match an unknown player, no
forced refresh runs; the config then arrives via the stream-rejection refresh (below, once a wrong
decipher yields a URL the CDN rejects) or the startup TTL refresh.

`extractSignatureTimestamp()` order: (1) the anchored `signatureTimestamp` literal in the player JS
(immune to config typos and bad pushes - a config's `sts` is not CDN-validated); (2) the config's
`sts`; (3) the loose `sts` pattern, which must never shadow the other two.

## The self-heal (`CipherDeobfuscator.getOrCreateWebView`)

```kotlin
var sigInfo   = FunctionNameExtractor.extractSigFunctionInfo(playerJs, hash)
var nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs, hash)
if (sigInfo == null || nFuncInfo == null) {
    if (PlayerConfigStore.forceRefresh(missingHash = hash)) {
        sigInfo   = FunctionNameExtractor.extractSigFunctionInfo(playerJs, hash) ?: sigInfo
        nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs, hash) ?: nFuncInfo
        builtEpoch = PlayerConfigStore.configEpoch
    }
}
```

- **Either side missing triggers the refresh** - a false positive on one side must not block the other.
- **Both sides re-extract after a successful refresh**, so a validated config replaces any heuristic
  guess.
- It runs mid-session, at the playback attempt that would otherwise fail. If the entry isn't on
  `master` yet, the refresh returns false (cooldown armed only if GitHub was reached) and the monitor
  is what gets a human to push it ([04](04-operations.md)).

Other retries around it:

- `deobfuscateStreamUrl` catches any exception, calls `PlayerJsFetcher.invalidateCache()` +
  `closeWebView()`, and retries once with fresh player JS (`isRetry = true`).
- **Except a renderer death**: `CipherRendererGoneException` fails fast (a re-parse under the same
  memory pressure would die again) so playback falls through to non-cipher clients.
  `RendererRecoveryPolicy` (pure, JVM-tested) opens a short, half-open backoff after repeated
  consecutive deaths, during which `getOrCreateWebView` skips WebView creation; a success resets it.

## Keeping the cached WebView fresh

One `CipherWebView` is reused for the process and shared by **every cipher client** (WEB_REMIX,
WEB_CREATOR, TVHTML5_SIMPLY), so a wrong cipher would otherwise break all of them until a restart:

1. **Config epoch rebuild.** `getOrCreateWebView` records the `configEpoch` it built under and rebuilds
   when the live epoch advances. The epoch is snapshotted **before** the build, so a refresh landing
   mid-build forces a rebuild next time instead of being masked.
2. **Stream-rejection refresh** from two app call sites, so every cipher client is covered:
   - `MusicService.handleExpiredUrlError` (ExoPlayer 403/410) - only WEB_REMIX skips HEAD validation,
     so only its bad URLs reach ExoPlayer;
   - `YTPlayerUtils`, when a `needsNTransform` client fails `validateStatus` during resolution
     (WEB_CREATOR / TVHTML5_SIMPLY), fired on `cipherRefreshScope` so the refresh can't block the
     fall-through.

   Both call `CipherDeobfuscator.onStreamRejected()`; when the table changed, the epoch advanced (→
   rebuild) and the app calls `YTPlayerUtils.clearWebRemixFailures()` so resolution returns to
   WEB_REMIX - no force-stop.

The WebView rebuild itself is verified on-device; the store side is pinned by
`PlayerConfigStoreEpochTest` and `PlayerConfigStoreCooldownTest`.

## Execution (`CipherWebView`)

`CipherWebView.create(context, playerJs, sigInfo, nFuncInfo)` loads the player JS into a hidden
WebView and appends export shims. For expression entries the literal `INPUT` is replaced with the
parameter name:

```js
window._cipherSigFunc  = function(sig) { try { return mP(4,155,sig); } catch(e) { return null; } };
window._nTransformFunc = function(n)   { try { return (function(n){…g.Yx…})(n); } catch(e) { return n; } };
```

Legacy entries export the named function instead, and a brute-force discovery pass is the last resort
for the n-transform. Calls are serialized by `CipherDeobfuscator.deobfuscateMutex` (the WebView has
single-shot continuation slots).

## App call sites (`app/.../utils/YTPlayerUtils.kt`)

- `getSignatureTimestampOrNull()` → `CipherDeobfuscator.signatureTimestamp()`, sent in every `/player`
  request.
- `findUrlOrNull()` → `CipherDeobfuscator.deobfuscateStreamUrl(format.signatureCipher!!, videoId)`.
- After selection → `CipherDeobfuscator.transformNParamInUrl(url)` (the sole n-transform source).

Logcat: `Zemer_CipherConfig` (store), `Zemer_CipherFnExtract` (extraction), `Zemer_CipherDeobfusc`
(self-heal), `YTPlayerUtils` (`Playback: client=…, itag=…`).
