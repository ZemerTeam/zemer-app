// Empirically validate a candidate cipher config for a player by DECIPHERING A REAL STREAM and
// GETting bytes from the CDN. 206 = the sig+n are correct; 403 = wrong. Ground truth, not regex.
//
// Replicates cipher.mjs's jsdom injection (byte-identical to the app's CipherWebView) but lets us
// try ARBITRARY sigExpr / nClass candidates, and auto-enumerates candidates from the assembler.
//
//   node tests/validate-player-config.mjs <hash>                       # auto-derive + validate
//   node tests/validate-player-config.mjs <hash> "Jf(20,3699,INPUT)" iE   # test a specific pair
//
// Uses the logged-in cookie (cred.mjs) and WEB_REMIX (ciphered URLs) pinned to <hash>'s STS.

import crypto from "node:crypto";
import { JSDOM } from "jsdom";
import { getCred, describeCred } from "./cred.mjs";
import { loadRawPlayerConfigs, nTrick } from "./player-configs.mjs";

const ORIGIN = "https://music.youtube.com";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const WEB_REMIX = { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: "67" };

const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const PLAYER_JS_URL = (h) => `https://www.youtube.com/s/player/${h}/player_ias.vflset/en_GB/base.js`;
const md5hash4 = (s) => crypto.createHash("md5").update(Buffer.from(s.slice(0, 10000), "utf8")).digest("hex").slice(0, 8);

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}

async function fetchJs(hash) {
  const r = await fetch(PLAYER_JS_URL(hash), { headers: { "User-Agent": WEB_UA } });
  if (!r.ok) throw new Error(`player ${hash}: HTTP ${r.status}`);
  return r.text();
}

// Build a cipher from base.js with explicit sigExpr + nClass, exactly like cipher.mjs/CipherWebView.
function buildCipher(playerJs, sigExpr, nClass) {
  // The single JS copy of the n-IIFE template — what this script 206-validates must be
  // byte-identical to what devices evaluate (PlayerConfigParser.buildNJsExpression).
  const nExpr = nTrick(nClass);
  const sigStmt = `window._cipherSigFunc = function(sig){ try { return ${sigExpr.replace("INPUT", "sig")}; } catch(e){ return null; } };`;
  const nStmt = `window._nTransformFunc = function(n){ try { return ${nExpr.replace("INPUT", "n")}; } catch(e){ return n; } };`;
  const exportCode = `; ${sigStmt} ${nStmt} `;
  let modified = playerJs.replace("})(_yt_player);", `${exportCode}})(_yt_player);`);
  if (modified === playerJs) modified = `${playerJs}\n${exportCode}`;

  const dom = new JSDOM("<!DOCTYPE html><html><head></head><body></body></html>", {
    url: "https://www.youtube.com/", pretendToBeVisual: true, runScripts: "outside-only",
  });
  const win = dom.window;
  win._yt_player = {};
  if (!win.TextEncoder) win.TextEncoder = TextEncoder;
  if (!win.TextDecoder) win.TextDecoder = TextDecoder;
  let initError = null;
  try { win.eval(modified); } catch (e) { initError = e.message; }
  const sigFn = win._cipherSigFunc, nFn = win._nTransformFunc;
  const nProbe = (() => { try { const i = "KdrqFlzJXl9EcCwlmEy"; const o = nFn(i); return { in: i, out: o, changed: o && o !== i }; } catch (e) { return { error: e.message }; } })();
  return {
    initError,
    sigAvailable: typeof sigFn === "function",
    nAvailable: typeof nFn === "function",
    nProbe,
    deob(signatureCipher) {
      const p = {};
      for (const pair of signatureCipher.split("&")) { const i = pair.indexOf("="); if (i > 0) p[decodeURIComponent(pair.slice(0, i))] = decodeURIComponent(pair.slice(i + 1)); }
      const s = p.s, sp = p.sp || "signature", url = p.url;
      if (s == null || url == null) throw new Error("missing s/url");
      const sig = sigFn(s);
      if (sig == null) throw new Error("sig returned null");
      let out = `${url}${url.includes("?") ? "&" : "?"}${sp}=${encodeURIComponent(String(sig))}`;
      const m = out.match(/[?&]n=([^&]+)/);
      if (m) { const t = nFn(decodeURIComponent(m[1])); out = out.replace(/([?&])n=[^&]+/, `$1n=${encodeURIComponent(t == null ? m[1] : String(t))}`); }
      return out;
    },
    close: () => win.close(),
  };
}

// Enumerate candidate (sigExpr, nClass) pairs from the assembler region.
function candidates(js) {
  const out = [];
  const alr = js.indexOf('.set("alr","yes")');
  // n-class: any g.<X> used with .get("n")
  const nClasses = [...new Set([...js.matchAll(/new\s+g\.([A-Za-z0-9$_]{2,5})\([^)]*\)\)?\s*\.\s*get\("n"\)/g)].map((m) => m[1]))];
  // also classes appearing right before an alr set
  if (alr >= 0) {
    const w = js.slice(Math.max(0, alr - 120), alr + 10);
    const m = w.match(/new\s+g\.([A-Za-z0-9$_]{2,5})\([^,()]+,\s*!0\s*\)\s*;\s*[A-Za-z0-9$_]+\.set\("alr"/);
    if (m && !nClasses.includes(m[1])) nClasses.unshift(m[1]);
  }
  // sig exprs: SIGFUNC(a,b, INNER(c,d, var)) within ~200 chars after an alr set (the apply site)
  const sigExprs = [];
  for (const m of js.matchAll(/\.set\("alr","yes"\)/g)) {
    const w = js.slice(m.index, m.index + 220);
    const sm = w.match(/=\s*([A-Za-z0-9$_]{2,5})\((\d+),(\d+),\s*[A-Za-z0-9$_]{2,5}\(\d+,\d+,/);
    if (sm) { const e = `${sm[1]}(${sm[2]},${sm[3]},INPUT)`; if (!sigExprs.includes(e)) sigExprs.push(e); }
  }
  for (const s of sigExprs) for (const n of nClasses) out.push({ sigExpr: s, nClass: n });
  return { sigExprs, nClasses, pairs: out };
}

async function realCipherUrl(hash, sts, cred) {
  // WEB_REMIX /player pinned to this player's STS -> a real signatureCipher format.
  const h = { "Content-Type": "application/json", "User-Agent": WEB_UA, "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": WEB_REMIX.clientId, "X-YouTube-Client-Version": WEB_REMIX.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "X-Goog-Visitor-Id": cred.visitorData, cookie: cred.cookie };
  const a = sapisidHash(cred.cookie); if (a) h.Authorization = a;
  const body = { context: { client: { clientName: WEB_REMIX.clientName, clientVersion: WEB_REMIX.clientVersion, gl: "US", hl: "en", visitorData: cred.visitorData } },
    videoId: "dQw4w9WgXcQ", playbackContext: { contentPlaybackContext: { signatureTimestamp: sts } } };
  const res = await fetch(`${ORIGIN}/youtubei/v1/player?prettyPrint=false`, { method: "POST", headers: h, body: JSON.stringify(body) });
  const j = JSON.parse(await res.text());
  const f = (j?.streamingData?.adaptiveFormats || []).find((x) => x.signatureCipher && (x.mimeType || "").startsWith("audio/"));
  return f?.signatureCipher || null;
}

async function get(url, range = "bytes=0-262143") {
  try { const r = await fetch(url, { headers: { "User-Agent": WEB_UA, Range: range, Connection: "close" } }); r.body?.cancel?.(); return r.status; } catch (e) { return "ERR:" + e.message.slice(0, 30); }
}

async function main() {
  const hash = process.argv[2];
  const fixedSig = process.argv[3], fixedN = process.argv[4];
  if (!hash) { console.error("usage: node tests/validate-player-config.mjs <hash> [sigExpr nClass]"); process.exit(1); }
  const cred0 = await getCred();
  console.log(describeCred(cred0));
  const cred = { visitorData: dec(cred0.visitorData), cookie: cred0.cookie };

  const js = await fetchJs(hash);
  const sts = Number((js.match(/signatureTimestamp[':\s"]+(\d{4,6})/) || [])[1]);
  const md5 = md5hash4(js);
  console.log(`player ${hash}  md5Alias=${md5}  sts=${sts}  size=${js.length}`);

  const sigCipher = await realCipherUrl(hash, sts, cred);
  if (!sigCipher) { console.error("could not get a signatureCipher format (server returned url/sabr?) — retry"); process.exit(1); }
  console.log(`got real signatureCipher (s len=${new URLSearchParams(sigCipher).get("s")?.length})\n`);

  let pairs;
  if (fixedSig && fixedN) pairs = [{ sigExpr: fixedSig, nClass: fixedN }];
  else {
    const c = candidates(js);
    console.log(`candidates: sig=[${c.sigExprs.join(", ")}]  nClass=[${c.nClasses.join(", ")}]`);
    pairs = c.pairs;
    // If this player is already committed to player_configs.json (by hash or md5 alias),
    // validate the committed pair FIRST — the common case is re-checking a shipped config.
    // A broken committed file must not kill this tool: it is the one needed to FIX that
    // file, so fall back to extracted candidates with a warning.
    let committed = null;
    try {
      committed = Object.entries(loadRawPlayerConfigs())
        .find(([h, e]) => h === hash || h === md5 || (e.aliases ?? []).includes(hash) || (e.aliases ?? []).includes(md5));
    } catch (e) {
      console.warn(`warning: committed player_configs.json unusable (${e.message}) — validating extracted candidates only`);
    }
    if (committed) {
      const [, e] = committed;
      console.log(`committed config found in player_configs.json: sig=${e.sig} nClass=${e.nClass}`);
      pairs = [{ sigExpr: e.sig, nClass: e.nClass }, ...pairs.filter((p) => p.sigExpr !== e.sig || p.nClass !== e.nClass)];
    }
  }
  if (!pairs.length) { console.error("no candidates extracted"); process.exit(1); }

  let winner = null;
  // Every candidate that BOTH returned 206 AND ran a real n-transform. Bare 206 is not enough:
  // a correct-sig / wrong-n pair still 206s inside googlevideo's first-1-MiB free window, so a
  // pair is only "working" when nProbe.changed is also true.
  const working = [];
  for (const { sigExpr, nClass } of pairs) {
    const cipher = buildCipher(js, sigExpr, nClass);
    let url, status = "n/a", nChanged = cipher.nProbe?.changed;
    try { url = cipher.deob(sigCipher); status = await get(url); } catch (e) { status = "deob-fail:" + e.message.slice(0, 30); }
    cipher.close();
    const ok = status === 206 || status === 200;
    console.log(`  sig=${sigExpr.padEnd(20)} n=g.${(nClass + "").padEnd(4)} nProbe.changed=${String(nChanged).padEnd(5)} GET=${status}  ${ok ? "✓ WORKS" : ""}`);
    if (ok && nChanged) { working.push({ sigExpr, nClass }); if (!winner) winner = { sigExpr, nClass, sts, md5, urlHash: hash }; }
  }

  // Structured, machine-readable verdict — the STABLE CONTRACT the automation consumes, so
  // tools/propose-config.mjs never screen-scrapes this human log text. `ambiguous` = more than one
  // candidate both deciphered AND ran a real n-transform; the pipeline refuses those rather than
  // guess which one the server truly wants.
  console.log("VALIDATOR_RESULT=" + JSON.stringify({
    ok: working.length === 1,
    hash,
    workingCount: working.length,
    ambiguous: working.length > 1,
    entry: winner ? { sig: winner.sigExpr, nClass: winner.nClass, sts: winner.sts, aliases: [winner.md5] } : null,
  }));

  console.log();
  if (winner) {
    console.log(`✓ VALIDATED CONFIG for ${hash}:`);
    console.log(`    urlHash=${winner.urlHash}  md5Alias=${winner.md5}  sts=${winner.sts}`);
    console.log(`    paste into cipher/library/src/main/assets/player_configs.json under "players":`);
    console.log(`    "${winner.urlHash}": { "sig": "${winner.sigExpr}", "nClass": "${winner.nClass}", "sts": ${winner.sts}, "aliases": ["${winner.md5}"] }`);
  } else {
    console.log(`✗ no candidate produced a 206 with a working n-transform — inspect manually`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
