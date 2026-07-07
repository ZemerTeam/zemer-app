// Faithful Node port of the app's Zemer cipher (sig deobfuscation + n-transform + STS).
//
// It does EXACTLY what cipher/library/.../CipherWebView.kt does, just in jsdom instead of
// an Android WebView:
//   1. fetch the SAME base.js (iframe_api -> hash -> player_ias.vflset/en_GB/base.js)
//   2. inject the SAME exports into the IIFE closure ("})(_yt_player);"):
//        window._cipherSigFunc  = function(sig){ try { return <sigExpr> } catch(e){ return null } }
//        window._nTransformFunc = function(n)  { try { return <nExpr>  } catch(e){ return n } }
//      using the SAME hardcoded VM-dispatch expressions (Tl(48,5831,sig) / Qp(25,37,sig),
//      and the g.W_/g.W1 ".get('n')" URL-param trick) keyed by player hash.
//   3. run base.js, then call those two functions.
//   4. assemble the URL the SAME way: url + "&{sp}=" + enc(sig), then replace n=, then &pot=.
//
// Because it's the same base.js and the same expressions, the resulting URL is byte-identical
// to what YTPlayerUtils.findUrlOrNull + transformNParamInUrl produce on-device.

import crypto from "node:crypto";
import { JSDOM } from "jsdom";
import { loadKnownPlayerConfigs } from "./player-configs.mjs";

const IFRAME_API_URL = "https://www.youtube.com/iframe_api";
const PLAYER_JS_URL = (hash) =>
  `https://www.youtube.com/s/player/${hash}/player_ias.vflset/en_GB/base.js`;

// Loaded from cipher/library/src/main/assets/player_configs.json — the same file the app
// bundles and fetches remotely. Keyed by BOTH the URL hash and the MD5 fallback alias.
// Lazy + memoized: ~14 scripts import this module, and a missing submodule or a bad
// config file should fail the scripts that USE configs (with the loader's actionable
// message), not kill every import at module-load time.
let knownPlayerConfigsCache = null;
function knownPlayerConfigs() {
  knownPlayerConfigsCache ??= loadKnownPlayerConfigs();
  return knownPlayerConfigsCache;
}

// Anchored pattern = the player's own embedded field (ground truth); the loose \bsts
// pattern can false-match and is fallback-only, tried after the config.
const SIG_TS_ANCHORED = /signatureTimestamp['":\s]+(\d+)/;
const SIG_TS_LOOSE = /\bsts['":\s]+(\d+)/;

function md5hash4(s) {
  return crypto.createHash("md5").update(Buffer.from(s.slice(0, 10000), "utf8")).digest("hex").slice(0, 8);
}

function extractHashesFromJs(playerJs) {
  const out = [];
  const pats = [
    /jsUrl['":\s]+[^"']*?\/player\/([a-f0-9]{8})\//,
    /player_ias\.vflset\/[^/]+\/([a-f0-9]{8})\//,
    /\/s\/player\/([a-f0-9]{8})\//,
  ];
  for (const p of pats) { const m = playerJs.match(p); if (m) out.push(m[1]); }
  out.push(md5hash4(playerJs));
  return [...new Set(out)];
}

export function extractSignatureTimestamp(playerJs, hashes = []) {
  // Precedence (mirrors FunctionNameExtractor.extractSignatureTimestamp on-device):
  // 1. the anchored literal in the JS itself — the field configs are copied from, immune
  //    to config typos/stale aliases; 2. the config; 3. the false-match-prone loose pattern.
  const anchored = playerJs.match(SIG_TS_ANCHORED);
  if (anchored) return Number(anchored[1]);
  for (const h of hashes) if (knownPlayerConfigs()[h]) return knownPlayerConfigs()[h].sts;
  const loose = playerJs.match(SIG_TS_LOOSE);
  return loose ? Number(loose[1]) : null;
}

export async function fetchPlayerJs() {
  // PLAYER_HASH pins a specific player (e.g. a known-configured one) instead of whatever
  // iframe_api currently rotates to — makes tests deterministic. The matching STS is sent in
  // the /player request, so the returned signatureCipher stays compatible with this base.js.
  let urlHash = process.env.PLAYER_HASH;
  if (!urlHash) {
    const iframe = await (await fetch(IFRAME_API_URL, { headers: { "User-Agent": "Mozilla/5.0" } })).text();
    // iframe_api JSON-escapes its slashes: \/s\/player\/HASH\/ — tolerate optional backslashes.
    const m = iframe.match(/\\?\/s\\?\/player\\?\/([a-zA-Z0-9_-]+)\\?\//);
    if (!m) throw new Error("could not extract player hash from iframe_api");
    urlHash = m[1];
  }
  const playerJs = await (await fetch(PLAYER_JS_URL(urlHash), { headers: { "User-Agent": "Mozilla/5.0" } })).text();
  return { playerJs, urlHash };
}

/**
 * Build a cipher bound to the live base.js. Returns:
 *   { hash, sts, sigAvailable, nAvailable, deobfuscateSignature, transformN,
 *     deobfuscateStreamUrl, transformNParamInUrl }
 */
export async function createCipher({ verbose = false } = {}) {
  const { playerJs, urlHash } = await fetchPlayerJs();
  const hashes = [urlHash, ...extractHashesFromJs(playerJs)];
  const uniq = [...new Set(hashes)];
  const cfgKey = uniq.find((h) => knownPlayerConfigs()[h]);
  const cfg = cfgKey ? knownPlayerConfigs()[cfgKey] : null;
  const sts = extractSignatureTimestamp(playerJs, uniq);

  if (verbose) {
    console.error(`cipher: urlHash=${urlHash} hashes=[${uniq.join(",")}] cfg=${cfgKey || "NONE"} sts=${sts}`);
  }
  if (!cfg) {
    throw new Error(
      `no cipher config for live player (hashes: ${uniq.join(", ")}). ` +
      `The app also needs this player — add it to cipher/library/src/main/assets/player_configs.json.`,
    );
  }

  // Inject the exports into the IIFE closure, exactly like CipherWebView.loadPlayerJsFromFile().
  const sigStmt = `window._cipherSigFunc = function(sig){ try { return ${cfg.sigExpr.replace("INPUT", "sig")}; } catch(e){ return null; } };`;
  const nStmt = `window._nTransformFunc = function(n){ try { return ${cfg.nExpr.replace("INPUT", "n")}; } catch(e){ return n; } };`;
  const exportCode = `; ${sigStmt} ${nStmt} `;
  let modified = playerJs.replace("})(_yt_player);", `${exportCode}})(_yt_player);`);
  if (modified === playerJs) {
    if (verbose) console.error("cipher: injection point '})(_yt_player);' not found — appending");
    modified = `${playerJs}\n${exportCode}`;
  }

  // Run base.js in a browser-ish env (jsdom). outside-only lets us window.eval our script
  // while NOT auto-running any <script> tags.
  const dom = new JSDOM("<!DOCTYPE html><html><head></head><body></body></html>", {
    url: "https://www.youtube.com/",
    pretendToBeVisual: true,
    runScripts: "outside-only",
  });
  const win = dom.window;
  win._yt_player = {}; // the IIFE arg `g`
  // A couple of stubs base.js may poke at during init that jsdom lacks.
  if (!win.TextEncoder) win.TextEncoder = TextEncoder;
  if (!win.TextDecoder) win.TextDecoder = TextDecoder;

  let initError = null;
  try {
    win.eval(modified);
  } catch (e) {
    initError = e;
    if (verbose) console.error(`cipher: base.js eval threw: ${e.message}`);
  }

  const sigFn = win._cipherSigFunc;
  const nFn = win._nTransformFunc;
  const sigAvailable = typeof sigFn === "function";
  const nAvailable = typeof nFn === "function";

  // Validate n function with the same probe the app uses.
  let nProbe = null;
  if (nAvailable) {
    try {
      const probeIn = "KdrqFlzJXl9EcCwlmEy";
      const probeOut = nFn(probeIn);
      nProbe = { in: probeIn, out: probeOut, changed: probeOut && probeOut !== probeIn };
    } catch (e) { nProbe = { error: e.message }; }
  }

  function deobfuscateSignature(obfuscatedSig) {
    if (!sigAvailable) throw new Error("sig function not available");
    const r = sigFn(obfuscatedSig);
    if (r == null) throw new Error("sig function returned null");
    return String(r);
  }
  function transformN(nValue) {
    if (!nAvailable) return nValue;
    const r = nFn(nValue);
    return r == null ? nValue : String(r);
  }

  // url + "&{sp}=" + enc(sig) — matches CipherDeobfuscator.deobfuscateInternal.
  function deobfuscateStreamUrl(signatureCipher) {
    const params = {};
    for (const pair of signatureCipher.split("&")) {
      const i = pair.indexOf("=");
      if (i > 0) params[decodeURIComponent(pair.slice(0, i))] = decodeURIComponent(pair.slice(i + 1));
    }
    const s = params["s"], sp = params["sp"] || "signature", baseUrl = params["url"];
    if (s == null || baseUrl == null) throw new Error("signatureCipher missing s/url");
    const sig = deobfuscateSignature(s);
    const sep = baseUrl.includes("?") ? "&" : "?";
    return `${baseUrl}${sep}${sp}=${encodeURIComponent(sig)}`;
  }

  // replace n= with transform(n) — matches CipherDeobfuscator.transformNInternal.
  function transformNParamInUrl(url) {
    const m = url.match(/[?&]n=([^&]+)/);
    if (!m) return url;
    const nValue = decodeURIComponent(m[1]);
    const transformed = transformN(nValue);
    return url.replace(/([?&])n=[^&]+/, `$1n=${encodeURIComponent(transformed)}`);
  }

  return {
    hash: cfgKey, urlHash, sts, sigAvailable, nAvailable, nProbe, initError: initError?.message || null,
    deobfuscateSignature, transformN, deobfuscateStreamUrl, transformNParamInUrl,
    _close: () => dom.window.close(),
  };
}

// ---- CLI self-test ----
if (import.meta.url === `file://${process.argv[1]}`) {
  const c = await createCipher({ verbose: true });
  console.log(JSON.stringify({
    hash: c.hash, urlHash: c.urlHash, sts: c.sts,
    sigAvailable: c.sigAvailable, nAvailable: c.nAvailable, nProbe: c.nProbe, initError: c.initError,
  }, null, 2));
  if (c.nAvailable && c.nProbe?.changed) console.error("n-transform WORKS ✓");
  else console.error("n-transform did NOT transform ✗");
  process.exit(0);
}
