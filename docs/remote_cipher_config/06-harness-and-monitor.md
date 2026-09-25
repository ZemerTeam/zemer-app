# 06 — The harness and the 30-minute monitor

## `tests/player-configs.mjs` — the loader (app repo)

The harness's only source of player configs. Before app commit `1b05655`, `tests/cipher.mjs`
carried a hand-mirrored copy of the Kotlin table — the classic drift bug waiting to happen.
Now the harness reads **the same file the APK bundles and devices fetch**, through the
submodule path:

```
tests/player-configs.mjs → ../cipher/library/src/main/assets/player_configs.json
```

Exports:

- `parsePlayerConfigs(jsonText, label)` — validation mirroring `PlayerConfigParser`
  (same regexes, same integer-only `schemaVersion` gate — app commit `8c18396` — same
  whole-file rejection on duplicate hash/alias — app commit `09a1913`). Difference by
  design: it **throws** on a bad entry instead of skipping ("in tests, loud is right").
  Exported precisely so callers can validate *any* copy of the file — e.g. the live remote
  one — with the exact rules applied to the bundled copy.
- `loadRawPlayerConfigs(path?)` — reads + validates the submodule copy; a missing file
  produces the actionable error "the cipher submodule is not checked out. Run:
  `git submodule update --init`" (app commit `ac1965a`, which also made the load lazy so
  unrelated scripts don't die on a missing submodule).
- `loadKnownPlayerConfigs()` — alias-expanded map in the shape the harness ciphers consume:
  `{ <hash-or-alias>: { sigExpr, nExpr, sts } }`.
- `nTrick(urlClass)` — the JS copy of the n-IIFE template, golden-pinned to the Kotlin one
  (doc 04).

Unit tests: `node --test tests/player-configs.test.mjs` — 10 tests, no cookie or network
needed. They cover the validation rules, collision rejection, the `config-covers.mjs` CLI,
the schemaVersion gate, the template golden, and the cross-language parity fixtures.

## `tests/validate-player-config.mjs` — ground truth for new entries

`node tests/validate-player-config.mjs <hash>` (optionally
`<hash> "<sigExpr>" <nClass>` to test a specific pair):

1. Downloads `https://www.youtube.com/s/player/<hash>/player_ias.vflset/en_GB/base.js`.
2. Auto-enumerates candidate `(sig call, nClass)` pairs from the player's assembler, or
   uses the pair you pass.
3. Rebuilds the cipher exactly like the device: jsdom, the same export shims, the sig
   expression verbatim, the n expression via the shared `nTrick` template (app commit
   `53d2aa0` — before that it had its own template copy, i.e. it could validate bytes
   devices don't run).
4. Requests a real `/player` response (WEB_REMIX, logged-in cookie from
   `innertube_cookie.txt`, STS pinned to the candidate player), deciphers a real
   `signatureCipher`, and **GETs the CDN**. `HTTP 206` = the pair is correct; `403` =
   wrong. This is the only acceptable proof — multiple constant pairs "decipher"
   plausibly, only one is accepted by the server (doc 01).
5. Prints a paste-ready JSON entry including the MD5 alias. If the committed file already
   has an entry for the hash, it re-validates that entry first.

## `.github/workflows/player-monitor.yml` — the 30-minute watchdog

Runs every 30 minutes (`cron: '*/30 * * * *'`) + manual dispatch. Runs are serialized by a
`concurrency` group (`youtube-player-monitor`, `cancel-in-progress: false`) so an overlapping
schedule + manual dispatch can't both scan and open duplicate issues. Step by step, with the
reasoning the file itself documents:

1. **Fetch the config file ONCE, from the live URL** (app commit `8bdb956`):
   `curl -fsS --retry 3` against the same raw zemer-cipher `master` URL devices fetch — so
   the verdict is about *what deployed apps actually self-heal from*, and never
   false-alarms when cipher `master` is ahead of the app's submodule pointer. Fallback to
   the submodule copy only with an explicit `::warning::` (it can lag `master`); if both
   fail, the step fails — a broken monitor is a red run, not silence.
2. **Scan the live player surfaces MANY times** (`node tests/scan-live-players.mjs
   /tmp/player_configs.json 30`): the old monitor took a *single* `iframe_api` sample per
   run, so an A/B **canary** served ~1/6 of the time was missed ~83% of the time and only
   surfaced once it had already rotated in — i.e. once playback was already breaking. The
   scanner samples `iframe_api` (what the app's `PlayerJsFetcher` uses) 30× plus
   `music.youtube.com` a few times, aggregates the distinct hashes with their observed
   frequency, and for each one not directly covered computes the md5-of-first-10000-bytes
   alias and re-checks — all via the **harness loader** (`parsePlayerConfigs`, the app's
   validation rules), so "known" still means "a device would accept and use it". It emits
   machine-readable JSON (`distinct`, `unknown[]`) and exits non-zero only if the live
   config itself is invalid (devices reject such a file wholesale → a red run, the right
   alarm). Its pure core (`aggregate`, `coveredKeys`) is unit-tested in
   `tests/scan-live-players.test.mjs`.
3. **Alert per unknown player** (only when `unknown[]` is non-empty): send a Telegram message
   per unknown hash (the issue title + body; skipped when `TELEGRAM_BOT_TOKEN` is empty), open
   one GitHub issue per hash (labels `player-update`, `cipher`; deduped by title against open
   issues; the body flags low-rate hits as canaries-caught-early) — as the Zemer-Dude GitHub App
   (token from `actions/create-github-app-token`) when `vars.ZEMER_APP_ID` is set, else the
   default `GITHUB_TOKEN` — and send one summary email to
   `dietdroidwp@gmail.com` via Gmail SMTP secrets listing every unknown hash with its
   frequency/md5/sts (sent even if issue creation fails). All contain the exact runbook
   commands (doc 07).

The monitor **never auto-commits** — entries are derived, 206-validated, and pushed by a
human. By design: the validation needs a logged-in cookie and live-CDN judgment that CI
shouldn't hold.

## Drift containment, summarized

| Drift risk | Mechanism that kills it |
|---|---|
| Harness table ≠ app table | both read the one JSON file; mirrors deleted (`1b05655`) |
| Kotlin parser ≠ JS loader (file-level) | shared `config-parity/` fixtures run by both suites |
| n-template Kotlin ≠ JS ≠ validator | byte-pinned to `n-template-Yx.golden`; validator imports `nTrick` |
| Monitor verdict ≠ device behavior | monitor runs the harness loader on the live file |
| Bundled asset invalid | `BundledAssetTest` parses the real asset every build |

Still mirrored on purpose (NOT configs): client definitions and poToken constants —
`tests/clients.mjs` / `tests/potoken.mjs` must be updated by hand when `YouTubeClient.kt` /
`PoTokenGenerator.kt` change.
