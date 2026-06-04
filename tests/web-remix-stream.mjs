// Reproduce the WEB_REMIX playback hiccup with HARD DATA, using the app's EXACT path.
//
// Pipeline (identical to YTPlayerUtils.playerResponseForPlayback for the WEB_REMIX main client):
//   1. cipher.mjs fetches the SAME base.js -> STS + sig/n (run in jsdom, byte-identical to the
//      app's CipherWebView).
//   2. potoken.mjs mints the SAME two tokens: player(videoId) + streaming(visitorData).
//   3. raw /player POST to music.youtube.com, faithful to InnerTube.kt (WEB_REMIX context,
//      X-Goog-* headers, SAPISIDHASH auth, signatureTimestamp, serviceIntegrityDimensions.poToken).
//   4. findFormat: adaptiveFormats.filter(isAudio && isOriginal).maxBy(bitrate + webm bias).
//   5. findUrlOrNull: signatureCipher -> cipher.deobfuscateStreamUrl (sig).
//   6. transformNParamInUrl (n) -> append &pot=enc(streamingDataPoToken).
//   7. Then exercise the URL like ExoPlayer and record real HTTP results:
//        A initial chunk, B sequential continuation (header ranges), B2 (query ranges),
//        C seek (~75%), D one open-ended GET, E re-resolve-on-403, plus a pot-variant probe.
//   8. IOS direct-url control to prove the harness is sound.
//
//   node tests/web-remix-stream.mjs                 # JTF9fLJvniI
//   node tests/web-remix-stream.mjs <videoId>
//   CHUNK=262144 COVER_SECONDS=90 node tests/web-remix-stream.mjs

import crypto from "node:crypto";
import { CLIENTS, USER_AGENT_WEB, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { mintWebPoTokens } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const CHUNK = Number(process.env.CHUNK || 262144);
const COVER_SECONDS = Number(process.env.COVER_SECONDS || 90);
const WEB_REMIX = CLIENTS.find((c) => c.key === "WEB_REMIX");
const IOS = CLIENTS.find((c) => c.key === "IOS");

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const msOf = (a, b) => `${(b - a).toFixed(0)}ms`;
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

// ---- faithful /player request (InnerTube.kt) ----
function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  const hash = crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex");
  return `SAPISIDHASH ${ts}_${hash}`;
}
function buildBody(c, videoId, visitorData, dataSyncId, { sts, poToken } = {}) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId, contentCheckOk: true, racyCheckOk: true };
  if (c.loginSupported && dataSyncId) body.context.user = { onBehalfOfUser: dataSyncId };
  if (c.useSignatureTimestamp && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  return body;
}
function buildHeaders(c, visitorData, { cookie, auth } = {}) {
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent,
  };
  if (visitorData) h["X-Goog-Visitor-Id"] = visitorData;
  if (auth && cookie && c.loginSupported) {
    h.cookie = cookie;
    const a = sapisidHash(cookie);
    if (a) h.Authorization = a;
  }
  return h;
}
async function playerRequest(c, videoId, visitorData, dataSyncId, opts = {}) {
  const res = await fetch(PLAYER_URL, {
    method: "POST", headers: buildHeaders(c, visitorData, opts),
    body: JSON.stringify(buildBody(c, videoId, visitorData, dataSyncId, opts)),
  });
  const text = await res.text();
  let j = {}; try { j = JSON.parse(text); } catch {}
  return { http: res.status, j };
}

// ---- app format selection (YTPlayerUtils.findFormat, streaming/HIGH) ----
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
function findFormat(j) {
  const fmts = (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f));
  return fmts.sort((a, b) =>
    (b.bitrate + ((b.mimeType || "").startsWith("audio/webm") ? 10240 : 0)) -
    (a.bitrate + ((a.mimeType || "").startsWith("audio/webm") ? 10240 : 0)))[0] || null;
}

function urlAnatomy(u) {
  let q; try { q = new URL(u).searchParams; } catch { return {}; }
  return {
    host: (() => { try { return new URL(u).host; } catch { return "?"; } })(),
    hasPot: q.has("pot"), hasN: q.has("n"), itag: q.get("itag"), mime: q.get("mime"),
    clen: q.get("clen") ? Number(q.get("clen")) : null,
    expire: q.get("expire") ? Number(q.get("expire")) : null,
  };
}

// One HTTP request on a fresh connection.
async function fetchRange(url, start, end, { ua, useQueryParam = false } = {}) {
  let target = url;
  const headers = { "User-Agent": ua, Connection: "close" };
  if (useQueryParam) { target = `${url}${url.includes("?") ? "&" : "?"}range=${start}-${end ?? ""}`; }
  else { headers.Range = `bytes=${start}-${end ?? ""}`; }
  const t0 = performance.now();
  try {
    const r = await fetch(target, { headers });
    const contentRange = r.headers.get("content-range");
    r.body?.cancel?.();
    return { status: r.status, contentRange, ms: performance.now() - t0 };
  } catch (e) { return { status: "ERR", error: e.message, ms: performance.now() - t0 }; }
}
async function drainWhole(url, ua, capBytes) {
  const t0 = performance.now();
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0, ms: performance.now() - t0 };
    let read = 0; const reader = r.body.getReader();
    for (;;) { const { done, value } = await reader.read(); if (done) break; read += value.length; if (capBytes && read >= capBytes) { await reader.cancel(); break; } }
    return { status: r.status, read, ms: performance.now() - t0, capped: capBytes && read >= capBytes };
  } catch (e) { return { status: "ERR", error: e.message, read: 0, ms: performance.now() - t0 }; }
}

// ---- resolve a playable URL the app's exact way ----
async function resolveAppUrl(c, { cipher, tokens, cred }) {
  const visitorData = dec(cred.visitorData);
  const auth = !!c.loginSupported;
  const playerPot = c.useWebPoTokens ? tokens?.playerRequestPoToken : null;
  const { http, j } = await playerRequest(c, VIDEO_ID, visitorData, cred.dataSyncId, {
    cookie: cred.cookie, auth, sts: cipher?.sts, poToken: playerPot,
  });
  const ps = j?.playabilityStatus || {};
  const fmt = findFormat(j);
  let url = null, sigUsed = false, nUsed = false;
  if (fmt) {
    if (fmt.url) url = fmt.url;
    else if (fmt.signatureCipher) { url = cipher.deobfuscateStreamUrl(fmt.signatureCipher); sigUsed = true; }
    const isWeb = c.useWebPoTokens || ["WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5"].includes(c.clientName);
    if (url && isWeb) {
      const before = url;
      url = cipher.transformNParamInUrl(url);
      nUsed = url !== before;
      // URL_POT selects which token to append:
      //   streaming (default) = visitorData-bound = the app's CURRENT behavior (reproduces the 403)
      //   player              = videoId-bound      = the FIX
      //   none                = no pot
      const urlPotMode = process.env.URL_POT || "streaming";
      const urlPot = urlPotMode === "player" ? tokens?.playerRequestPoToken
        : urlPotMode === "none" ? null
        : tokens?.streamingDataPoToken;
      if (c.useWebPoTokens && urlPot) {
        url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(urlPot)}`;
      }
    }
  }
  return {
    http, ps: { status: ps.status, reason: ps.reason },
    nFormats: (j?.streamingData?.adaptiveFormats || []).length,
    sabrUrl: j?.streamingData?.serverAbrStreamingUrl ? "PRESENT" : "none",
    fmt, url, sigUsed, nUsed,
    contentLength: fmt?.contentLength ? Number(fmt.contentLength) : urlAnatomy(url).clen,
    approxDurationMs: fmt?.approxDurationMs ? Number(fmt.approxDurationMs) : null,
    itag: fmt?.itag, mime: fmt?.mimeType, bitrate: fmt?.bitrate, anatomy: url ? urlAnatomy(url) : {},
  };
}

const secAt = (off, clen, durMs) => (!clen || !durMs ? null : (off / clen) * (durMs / 1000));

async function battery(label, r, ua, { reResolve, potVariants } = {}) {
  const { url, contentLength, approxDurationMs } = r;
  const toSec = (o) => { const s = secAt(o, contentLength, approxDurationMs); return s == null ? "?" : s.toFixed(1) + "s"; };
  console.log(`\n----- ${label}: range battery -----`);
  console.log(`url host=${r.anatomy.host} pot=${r.anatomy.hasPot} n=${r.anatomy.hasN} itag=${r.anatomy.itag} clen=${contentLength ?? "?"} dur=${approxDurationMs ? (approxDurationMs / 1000).toFixed(0) + "s" : "?"} expire=${r.anatomy.expire || "?"}`);
  if (!url) { console.log("  no url — cannot test"); return; }

  const a = await fetchRange(url, 0, CHUNK - 1, { ua });
  console.log(`A initial    bytes=0-${CHUNK - 1}  -> ${a.status} ${a.contentRange ? `(cr ${a.contentRange})` : ""} ${msOf(0, a.ms)}`);

  const targetBytes = contentLength || CHUNK * 64;
  async function sweep(useQueryParam) {
    let offset = CHUNK, firstFail = null;
    while (offset < targetBytes && (secAt(offset, contentLength, approxDurationMs) ?? 0) < COVER_SECONDS) {
      const res = await fetchRange(url, offset, offset + CHUNK - 1, { ua, useQueryParam });
      if (res.status !== 200 && res.status !== 206) { firstFail = { offset, status: res.status, error: res.error }; break; }
      offset += CHUNK;
    }
    return { firstFail, lastOffset: offset };
  }
  const bh = await sweep(false);
  console.log(`B  continuation (fresh conn / chunk, HEADER Range): ${bh.firstFail
    ? `FAIL @${toSec(bh.firstFail.offset)} (byte ${bh.firstFail.offset}) -> ${bh.firstFail.status} ${bh.firstFail.error || ""}`
    : `all 200/206 through ${toSec(bh.lastOffset)} — no drop`}`);
  const bq = await sweep(true);
  console.log(`B2 continuation (fresh conn / chunk, QUERY &range=): ${bq.firstFail
    ? `FAIL @${toSec(bq.firstFail.offset)} (byte ${bq.firstFail.offset}) -> ${bq.firstFail.status}`
    : `all 200/206 through ${toSec(bq.lastOffset)} — no drop`}`);

  const seekOff = contentLength ? Math.floor(contentLength * 0.75) : CHUNK * 40;
  const cH = await fetchRange(url, seekOff, seekOff + CHUNK - 1, { ua });
  const cQ = await fetchRange(url, seekOff, seekOff + CHUNK - 1, { ua, useQueryParam: true });
  console.log(`C  seek @${toSec(seekOff)} (byte ${seekOff})  HEADER -> ${cH.status}   QUERY -> ${cQ.status}`);

  const d = await drainWhole(url, ua, contentLength ? contentLength + 1 : CHUNK * 200);
  console.log(`D  one open GET bytes=0-  -> ${d.status} delivered ${kb(d.read)}${contentLength ? ` / ${kb(contentLength)}` : ""} (${secAt(d.read, contentLength, approxDurationMs)?.toFixed(1) ?? "?"}s) ${d.capped ? "[whole file]" : "[server ended early]"} ${msOf(0, d.ms)}`);

  if (potVariants) {
    const off = (bh.firstFail || bq.firstFail || { offset: seekOff }).offset;
    console.log(`P  pot-variant probe @${toSec(off)} (byte ${off}) on a fresh connection:`);
    for (const [name, u] of Object.entries(potVariants(url))) {
      const res = await fetchRange(u, off, off + CHUNK - 1, { ua });
      console.log(`     ${name.padEnd(22)} -> ${res.status}`);
    }
  }

  if ((bh.firstFail || bq.firstFail) && reResolve) {
    const off = (bh.firstFail || bq.firstFail).offset;
    console.log(`E  re-resolve @${toSec(off)} (byte ${off}) with fresh url + fresh tokens:`);
    try {
      const fresh = await reResolve();
      const rr = await fetchRange(fresh.url, off, off + CHUNK - 1, { ua });
      console.log(`     fresh url pot=${fresh.anatomy.hasPot} -> ${rr.status} ${rr.contentRange ? `(cr ${rr.contentRange})` : ""} ${rr.status === 206 || rr.status === 200 ? "✓ recovers" : "✗ still fails"}`);
    } catch (e) { console.log(`     re-resolve failed: ${e.message}`); }
  }
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));
  console.log(`video=${VIDEO_ID} chunk=${kb(CHUNK)} coverTarget=${COVER_SECONDS}s`);

  let t = performance.now();
  const cipher = await createCipher({ verbose: true });
  console.log(`cipher ready ${msOf(t, performance.now())}  hash=${cipher.hash} sts=${cipher.sts} sig=${cipher.sigAvailable} n=${cipher.nAvailable}`);

  t = performance.now();
  let tokens;
  try {
    tokens = await mintWebPoTokens({ visitorData, videoId: VIDEO_ID });
    console.log(`poTokens minted ${msOf(t, performance.now())}  player(videoId)=${tokens.playerRequestPoToken?.slice(0, 16)}…(${tokens.playerRequestPoToken?.length})  streaming(visitorData)=${tokens.streamingDataPoToken?.slice(0, 16)}…(${tokens.streamingDataPoToken?.length})`);
  } catch (e) { console.log(`poToken mint FAILED: ${e.message}`); tokens = {}; }

  console.log(`\n========== WEB_REMIX (app main client, exact path) ==========`);
  console.log(`URL_POT=${process.env.URL_POT || "streaming"} (streaming=visitorData/app default, player=videoId/fix, none)`);
  try {
    const r = await resolveAppUrl(WEB_REMIX, { cipher, tokens, cred });
    console.log(`http=${r.http} playability=${r.ps.status}${r.ps.reason ? ` (${r.ps.reason})` : ""} formats=${r.nFormats} sabr=${r.sabrUrl}`);
    console.log(`format itag=${r.itag} mime="${r.mime}" bitrate=${r.bitrate} sigUsed=${r.sigUsed} nUsed=${r.nUsed}`);
    if (r.url) {
      await battery("WEB_REMIX", r, USER_AGENT_WEB, {
        potVariants: (u) => {
          const base = u.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, "");
          return {
            "no pot": base,
            "streaming pot (app)": `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(tokens.streamingDataPoToken)}`,
            "player pot (videoId)": `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(tokens.playerRequestPoToken)}`,
          };
        },
        reResolve: async () => {
          const freshTok = await mintWebPoTokens({ visitorData, videoId: VIDEO_ID });
          return resolveAppUrl(WEB_REMIX, { cipher, tokens: freshTok, cred });
        },
      });
    }
  } catch (e) { console.log(`WEB_REMIX FAILED: ${e.message}\n${e.stack}`); }

  console.log(`\n========== IOS (control, direct url, no pot/cipher) ==========`);
  try {
    const r = await resolveAppUrl(IOS, { cipher, tokens, cred: { ...cred, cookie: "" } });
    console.log(`http=${r.http} playability=${r.ps.status}${r.ps.reason ? ` (${r.ps.reason})` : ""} formats=${r.nFormats} sabr=${r.sabrUrl} itag=${r.itag} mime="${r.mime}"`);
    if (r.url) await battery("IOS", r, IOS.userAgent, {});
  } catch (e) { console.log(`IOS FAILED: ${e.message}`); }

  cipher._close?.();
  process.exit(0);
})();
