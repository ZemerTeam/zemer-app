# 1 · Concepts & the file format

## What a config is

YouTube stream URLs depend on two transforms defined inside the player JS
(`https://www.youtube.com/s/player/<hash>/player_ias.vflset/en_GB/base.js`, identified by an 8-hex
hash):

1. **Signature decipher (`sig`)** - web clients get a `signatureCipher` (`s=`, `sp=`, `url=`); the app
   runs the player's own routine to turn `s` into a valid signature
   (`CipherDeobfuscator.deobfuscateStreamUrl`, called from `YTPlayerUtils.findUrlOrNull`).
2. **n-transform** - every URL's `n=` must be replaced with the player's transformed value or the CDN
   throttles / 403s (`CipherDeobfuscator.transformNParamInUrl`).

A third per-player value, the **signature timestamp (STS)**, is sent in the InnerTube `/player`
request and must match the player that will decipher the response - a signature minted for player A
but deciphered with player B 403s. So `YTPlayerUtils.getSignatureTimestampOrNull()` asks
`CipherDeobfuscator.signatureTimestamp()`, the STS of the exact player JS the cipher WebView uses.

Modern players are VM-dispatched: there is no named function to regex out (the legacy
`SIG_FUNCTION_PATTERNS` / `N_FUNCTION_PATTERNS` remain only as a fallback). What works is calling into
the loaded player with the right entry points, so an entry is:

```json
"16ee6936": { "sig": "mP(4,155,INPUT)", "nClass": "Yx", "sts": 20613, "aliases": ["ca366632"] }
```

- `sig` - a dispatcher call whose two integers select the decipher routine inside the VM.
- `nClass` - the player's URL-wrapper class; the device wraps it in a fixed IIFE (below).
- `aliases` - the **md5 fallback identity**: `FunctionNameExtractor.extractPlayerHash()` uses the first
  8 hex of `md5(first 10000 chars of base.js)` when neither a known URL hash nor a hash embedded in the
  JS is available. Both keys map to the same
  parsed config.

Builds re-released under different hashes often share the same `sig`/`nClass`; each hash still needs
its own entry.

### Only the live CDN proves an entry

**VM constant pairs are not unique**: several `(int, int)` pairs return a plausible-looking string,
and only one is accepted by the CDN. The ground truth is `node tests/validate-player-config.mjs <hash>`,
which deciphers a real stream and GETs it: **a pair works only on 206/200 plus a real n-transform**;
any other status needs investigation ([04](04-operations.md)). (A config can also look
fine for the first 1 MiB and die later if the `pot=` token isn't bound to the videoId - that is a
poToken problem, not a config problem; see `tests/INVESTIGATION.md`.)

APK releases are needed only for *scheme* changes (a new config shape); new players are data.

## The file

```
https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json
```

(`PlayerConfigStore.REMOTE_URL`.)

```json
{
  "schemaVersion": 1,
  "players": {
    "<hash>": { "sig": "<call>", "nClass": "<ident>", "sts": <int>, "aliases": ["<hash>", ...] }
  }
}
```

### Validation - two readers, identical file-level verdicts

`PlayerConfigParser.kt` (devices; pure JVM, no Android imports) and `tests/player-configs.mjs`
(harness + monitor) apply the same rules:

| Field | Rule |
|---|---|
| `schemaVersion` | required; a JSON **integer** (the string `"1"` is rejected); `1 ≤ v ≤ SUPPORTED_SCHEMA_VERSION` (1) |
| `players` | required JSON object |
| hash key | `^[a-f0-9]{8}$` (`HASH_RE`) |
| `sig` | `^[A-Za-z0-9$_]{1,8}\(\d+,\d+,INPUT\)$` (`SIG_RE`) |
| `nClass` | `^[A-Za-z0-9$_]{1,8}$` (`NCLASS_RE`) |
| `sts` | JSON integer (not string), `> 0` |
| `aliases` | optional array of `HASH_RE` strings |

Two severities, deliberately:

- **Bad entry** → skipped on devices (logged as `skipped invalid entries`), the rest loads - one typo
  can't poison the table. The harness instead **throws** ("in tests, loud is right").
- **File-level defect** → the whole file is rejected and the device keeps its last-good table:
  malformed JSON / non-object root; `schemaVersion` missing, string-typed, ≤ 0 or > supported;
  `players` missing or not an object; **any duplicate hash/alias key** (which entry wins would depend
  on map order, so ambiguity is a defect).

Every consumer of a `ParseResult.Failure` keeps its previous state (store: memory + disk cache;
monitor: the scan fails, a red run; harness: aborts), so no file-level defect degrades a
device below its last-good table. A `Success` with skipped entries is applied as the new remote map
(`bundled + remote`), so a skipped hash that only the previous remote copy carried drops out.

### `schemaVersion` policy

Bump only on a **breaking** shape change. An old app seeing a newer version logs
`unsupported schemaVersion N (supported: 1)`, rejects the file, and stays frozen on its last-good table
until an APK update. A new optional field that v1 readers ignore is **not** a bump.

## The security boundary

Config values end up in JavaScript evaluated inside the cipher WebView, and the remote file is treated
as untrusted (compromised repo, poisoned cache, tampered response). The defense is **shape, not
sanitization**: `sig` can express only one call of a short name of identifier characters
(`[A-Za-z0-9$_]{1,8}`) with two integer literals and `INPUT`; `nClass` only such a bare name. The worst
a malicious value can do is call the wrong player-internal function (or fail to parse, e.g. a
digit-leading name) → deciphering fails → playback falls back.

`nClass` is interpolated into a **locally built template** (`PlayerConfigParser.buildNJsExpression`);
the file can never supply the expression itself:

```js
(function(n){try{var u=new g.$nClass('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)
```

It wraps a fake googlevideo URL in `g.<nClass>`, reads `n` back (the class canonicalizes it through
the n-transform), and falls back to the input on no change or any exception.

A parsed entry becomes a `FunctionNameExtractor.HardcodedPlayerConfig` in expression form
(`sigFuncName = "_expr_sig"`, `sigJsExpression = sig`, `nFuncName = "_expr_n"`,
`nJsExpression = buildNJsExpression(nClass)`, `signatureTimestamp = sts`, legacy fields null).

## Parity fixtures - what keeps the two readers honest

`cipher/library/src/test/resources/config-parity/` holds `accept-*.json`, `reject-*.json` (malformed,
root array, players missing, alias collision, schemaVersion missing / string / zero / future) and
`n-template-Yx.golden`. Kotlin's `ConfigParityFixturesTest` and the harness's
`tests/player-configs.test.mjs` iterate the **same directory**: every `accept-*` must parse and every
`reject-*` must fail in both. The n-IIFE is pinned byte-for-byte to the golden file in both
(`NJsExpressionTemplateTest` / the harness's `nTrick` test), so what `validate-player-config.mjs`
proves with a 206 is byte-identical to what devices evaluate.

**Rule:** a validation or template change must update both readers **and** the fixtures, or one test
suite goes red. Only file-level verdicts are pinned; entry-level behavior intentionally differs.
