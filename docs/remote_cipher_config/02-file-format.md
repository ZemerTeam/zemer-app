# 02 — The file format: `player_configs.json`

Single source of truth: `cipher/library/src/main/assets/player_configs.json` in the
**zemer-cipher** repo (the path inside zemer-app is via the `cipher/` submodule). The raw
`master` URL of this exact file is what every deployed device fetches:

```
https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json
```

(`PlayerConfigStore.REMOTE_URL` — the constant in the code, byte-for-byte.)

## Shape

```json
{
  "schemaVersion": 1,
  "players": {
    "<hash>": { "sig": "<call>", "nClass": "<ident>", "sts": <int>, "aliases": ["<hash>", ...] }
  }
}
```

## Field rules — the exact validation, two implementations

Validation exists in **two readers** that must behave identically at file level:
`PlayerConfigParser.kt` (Kotlin, runs on devices) and `tests/player-configs.mjs` (the
harness loader, runs in tests and the CI monitor). The regexes are byte-identical:

| Field | Rule | Regex / check (both readers) |
|---|---|---|
| `schemaVersion` | required; **JSON integer, not a string** (`"1"` is rejected); `1 ≤ v ≤ SUPPORTED_SCHEMA_VERSION` (currently 1) | Kotlin: `JsonPrimitive.takeIf { !it.isString }?.content?.toIntOrNull()`; JS: `Number.isInteger(root.schemaVersion)` |
| `players` | required; JSON object (not array/null) | both readers |
| hash key | 8 lowercase hex chars | `^[a-f0-9]{8}$` (`HASH_RE`) |
| `sig` | a single call `name(int,int,INPUT)`; name ≤ 8 chars of `[A-Za-z0-9$_]` | `^[A-Za-z0-9$_]{1,8}\(\d+,\d+,INPUT\)$` (`SIG_RE`) |
| `nClass` | bare identifier, ≤ 8 chars | `^[A-Za-z0-9$_]{1,8}$` (`NCLASS_RE`) |
| `sts` | JSON integer (not string), `> 0` | both readers |
| `aliases` | optional array; every element matches `HASH_RE` | both readers |

`INPUT` is a literal placeholder token; it is replaced device-side with the actual sig / n
value (doc 05). These regexes are the **security boundary** — see doc 04 for why nothing
that passes them can carry executable JS.

## Failure semantics: entry-skip vs file-reject

Two distinct severities, deliberately:

- **Bad entry** (regex miss, wrong type, malformed alias) → that entry is *skipped*, the
  rest of the file loads. Device: `ParseResult.Success.skippedEntries` is logged
  (`Zemer_CipherConfig: "skipped invalid entries …"`) and playback continues with the valid
  entries. One typo cannot poison the whole table.
- **File-level defect** → the **whole file is rejected** and the device keeps its previous
  (last-good) table. File-level defects are exactly:
  - malformed JSON / root not an object
  - `schemaVersion` missing, string-typed, ≤ 0, or > supported
  - `players` missing or not an object
  - **any duplicate hash/alias key** — an alias duplicating another entry's hash or alias,
    or its own primary. Which entry "wins" would depend on map iteration order, so an
    ambiguous table is treated as a defect, not a preference
    (`PlayerConfigParser.parse`, the `duplicate` check; cipher commit `5b7ef67`).

The harness reader differs **only at entry level, intentionally**: it *throws* on a bad
entry instead of skipping (`tests/player-configs.mjs` header: "in tests, loud is right").
File-level verdicts are pinned identical across both readers by shared fixtures (doc 04).

## `schemaVersion` policy

Bump it **only on breaking shape changes**. The consequence chain, straight from the code:
an old app parsing a newer-versioned file hits
`"unsupported schemaVersion N (supported: 1)"` → `ParseResult.Failure` → file rejected →
the device keeps its last-good table and keeps playing with what it has. So a bump
quietly *freezes* all already-deployed apps at their current table — they need an APK
update to read the new shape. Adding a new optional field that v1 readers ignore is NOT a
bump; changing the meaning/shape of existing fields is.

## Aliases — the MD5 fallback identity

Primary key = the 8-hex hash from the player URL. Alias = the first 8 hex chars of
`md5(first 10000 bytes of base.js)`, computed by `FunctionNameExtractor.extractPlayerHash()`
when no URL-shaped hash can be found in the player JS, and by the monitor's scanner
(`tests/scan-live-players.mjs`, `playerIdentity`: md5 of the first 10000 bytes, first 8 hex) —
not a separate workflow step. Both keys map to the **same config object**
after parsing (`configs[hash] = config; for (alias in aliases) configs[alias] = config`).
`validate-player-config.mjs` prints entries with the alias already included.

## The table — a historical excerpt (as of cipher `81c7ed8`)

The bundled `player_configs.json` now holds **296 players, sts 20602–20686**; the 10 entries below
are the table as it stood at `81c7ed8`, kept as an illustration of the shape:

```json
{
  "schemaVersion": 1,
  "players": {
    "9c249f6f": { "sig": "Tl(48,5831,INPUT)", "nClass": "W_", "sts": 20602, "aliases": ["a6fc27c5"] },
    "4f38b487": { "sig": "Tl(48,5831,INPUT)", "nClass": "W_", "sts": 20602, "aliases": ["1215646b"] },
    "5cabb421": { "sig": "Qp(25,37,INPUT)",   "nClass": "W1", "sts": 20606, "aliases": ["94f9ca52"] },
    "9d2ef9ef": { "sig": "v0(35,4499,INPUT)", "nClass": "uY", "sts": 20607, "aliases": ["6fb43da5"] },
    "69e2a55d": { "sig": "Jf(20,3699,INPUT)", "nClass": "iE", "sts": 20611, "aliases": ["70d8066f"] },
    "ce74690f": { "sig": "$9(2,6487,INPUT)",  "nClass": "cV", "sts": 20612, "aliases": ["a5669e32"] },
    "16ee6936": { "sig": "mP(4,155,INPUT)",   "nClass": "Yx", "sts": 20613, "aliases": ["ca366632"] },
    "6b8eecd5": { "sig": "mP(4,155,INPUT)",   "nClass": "Yx", "sts": 20613, "aliases": ["6ea478fa"] },
    "445213fb": { "sig": "mP(4,155,INPUT)",   "nClass": "Yx", "sts": 20613, "aliases": ["d62bd338"] },
    "a32660fc": { "sig": "mP(4,155,INPUT)",   "nClass": "Yx", "sts": 20613, "aliases": ["e786ad71"] }
  }
}
```

Note how the four sts-20613 builds share `mP(4,155,…)`/`Yx` — same player generation
re-released under different hashes. Each still needs its own entry (and alias) because
lookup is by hash, but deriving the Nth one is a copy + re-validate.

## Guard tests on the bundled copy

`cipher/library/src/test/kotlin/com/zemer/cipher/BundledAssetTest.kt` parses the actual
asset on every test run — a malformed or colliding bundled file fails the build, it can
never ship. The harness equivalently validates it on every load (`loadRawPlayerConfigs`
throws, with an actionable "run `git submodule update --init`" error if the submodule is
missing — app commit `ac1965a`).
