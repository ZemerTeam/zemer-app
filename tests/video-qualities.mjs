// Prove EVERY available video quality with HARD DATA, using the app's EXACT stream path.
//
// This extends tests/video-progressive-stream.mjs from "the one progressive muxed format" to the FULL
// quality ladder a beyond-720p quality switcher exposes:
//   - PROGRESSIVE muxed formats (streamingData.formats, video mime + audio) — 360p/720p, play/download as-is.
//   - ADAPTIVE video-only formats (streamingData.adaptiveFormats, width != null) — 1080p/1440p/2160p…,
//     played by merging with the best adaptive audio (and downloaded as two files muxed on-device).
//   - The BEST ADAPTIVE AUDIO format (the app's existing audio pick) — the merge/mux partner.
//
// Per quality (high → low) it resolves the URL exactly like the app (cipher sig + n-transform, then
// &pot=), and runs:
//   A  initial range request           -> must be 206
//   B  fresh-connection chunk sweep    -> every chunk 206 past the 1 MiB pot wall (COVER_SECONDS worth)
//   C  seek at 75%                     -> must be 206 (ExoPlayer seeks open mid-file connections)
//   D  one open GET bytes=0-          -> full drain to EOF, read == content-range total ("download proof")
// The audio partner runs the same battery. Any failed step fails the script (exit 1) so this can gate.
//
//   node tests/video-qualities.mjs <videoId>                 # full battery, every quality, high→low
//   MODE=stream node tests/video-qualities.mjs <videoId>     # skip the full-drain download step
//   LABELS=2160p,1080p node tests/video-qualities.mjs <id>   # only these qualityLabels
//   URL_POT=streaming|player|none                            # pot binding on the stream URL (default
//                                                            #   player = videoId-bound, the binding that
//                                                            #   survives past the 1 MiB wall)
//   PLAYER_HASH=<8 hex>                                      # pin a player across a rotation
//
// Needs innertube_cookie.txt at the repo root (gitignored).

import crypto from "node:crypto";
import { CLIENTS, USER_AGENT_WEB, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { mintWebPoTokens } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const CHUNK = Number(process.env.CHUNK || 262144);
const COVER_SECONDS = Number(process.env.COVER_SECONDS || 30);
const MODE = process.env.MODE || "all"; // all | stream (skip full drains)
const LABELS = process.env.LABELS ? process.env.LABELS.split(",").map((s) => s.trim()) : null;
const URL_POT = process.env.URL_POT || "player";
// CLIENT env picks which InnerTube client's ladder to test (default the app's main WEB_REMIX).
// The music client caps at 1080p avc1; WEB/TVHTML5 may expose the vp9 1440p/2160p ladder.
const CLIENT_KEY = process.env.CLIENT || "WEB_REMIX";
const WEB_REMIX = CLIENTS.find((c) => c.key === CLIENT_KEY);
if (!WEB_REMIX) { console.error(`unknown CLIENT=${CLIENT_KEY}`); process.exit(1); }

const msOf = (a, b) => `${(b - a).toFixed(0)}ms`;
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
const mb = (n) => `${(n / 1048576).toFixed(1)}MB`;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

// ---- faithful /player request (InnerTube.kt) — same block as web-remix-stream.mjs ----
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

// ---- quality-ladder selection (mirrors VideoQualityLogic.kt) ----
const codecOf = (mime) => {
  const m = /codecs="([^".]+)/.exec(mime || "");
  return m ? m[1] : "?";
};
// avc1 first (universal hw decode), then vp9, then av01 — the app's codec preference per rung.
const CODEC_RANK = { avc1: 0, vp9: 1, vp09: 1, av01: 2 };
const codecRank = (mime) => CODEC_RANK[codecOf(mime)] ?? 3;

function qualityLadder(j) {
  const sd = j?.streamingData || {};
  const progressive = (sd.formats || []).filter(
    (f) => (f.mimeType || "").startsWith("video") && (f.audioQuality != null || f.audioChannels != null),
  );
  const videoOnly = (sd.adaptiveFormats || []).filter(
    (f) => (f.mimeType || "").startsWith("video") && f.width != null && f.audioQuality == null && f.audioChannels == null,
  );
  // One rung per qualityLabel; within a rung prefer avc1 > vp9 > av01, then higher bitrate. A
  // progressive rung wins over an adaptive rung with the same label (single file, no merge needed).
  const rungs = new Map();
  const consider = (f, kind) => {
    const label = f.qualityLabel || `${f.height}p`;
    const cur = rungs.get(label);
    const cand = { ...f, kind, codec: codecOf(f.mimeType) };
    if (!cur) { rungs.set(label, cand); return; }
    if (cur.kind === "progressive" && kind === "adaptive") return;
    if (cur.kind === "adaptive" && kind === "progressive") { rungs.set(label, cand); return; }
    const better = codecRank(f.mimeType) < codecRank(cur.mimeType)
      || (codecRank(f.mimeType) === codecRank(cur.mimeType) && (f.bitrate || 0) > (cur.bitrate || 0));
    if (better) rungs.set(label, cand);
  };
  for (const f of progressive) consider(f, "progressive");
  for (const f of videoOnly) consider(f, "adaptive");
  return [...rungs.values()].sort((a, b) => (b.height || 0) - (a.height || 0));
}
// The app's existing audio pick (YTPlayerUtils / web-remix-stream.mjs): best-bitrate adaptive audio,
// +10240 bias for webm/opus.
function findAudioFormat(j) {
  const formats = (j?.streamingData?.adaptiveFormats || []).filter(
    (f) => f.width == null && !f.audioTrack?.isAutoDubbed,
  );
  return formats.reduce((best, f) => {
    const score = (x) => (x.bitrate || 0) + ((x.mimeType || "").includes("audio/webm") ? 10240 : 0);
    return best == null || score(f) > score(best) ? f : best;
  }, null);
}

function resolveUrl(fmt, cipher, tokens) {
  let url = null, sigUsed = false, nUsed = false;
  if (fmt.url) url = fmt.url;
  else if (fmt.signatureCipher) { url = cipher.deobfuscateStreamUrl(fmt.signatureCipher); sigUsed = true; }
  if (!url) return { url: null, sigUsed, nUsed };
  const before = url;
  url = cipher.transformNParamInUrl(url);
  nUsed = url !== before;
  const urlPot = URL_POT === "player" ? tokens?.playerRequestPoToken
    : URL_POT === "none" ? null
    : tokens?.streamingDataPoToken;
  if (urlPot) url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(urlPot)}`;
  return { url, sigUsed, nUsed };
}

async function fetchRange(url, start, end, { ua } = {}) {
  const headers = { "User-Agent": ua, Range: `bytes=${start}-${end ?? ""}`, Connection: "close" };
  const t0 = performance.now();
  try {
    const r = await fetch(url, { headers });
    const contentRange = r.headers.get("content-range");
    r.body?.cancel?.();
    return { status: r.status, contentRange, ms: performance.now() - t0 };
  } catch (e) { return { status: "ERR", error: e.message, ms: performance.now() - t0 }; }
}
async function drainWhole(url, ua) {
  const t0 = performance.now();
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0, ms: performance.now() - t0, whole: false };
    const cr = r.headers.get("content-range");
    const total = cr && cr.includes("/") ? Number(cr.split("/").pop()) : Number(r.headers.get("content-length")) || null;
    let read = 0; const reader = r.body.getReader();
    for (;;) { const { done, value } = await reader.read(); if (done) break; read += value.length; }
    const whole = total != null ? read >= total : read > 0;
    return { status: r.status, read, total, ms: performance.now() - t0, whole };
  } catch (e) { return { status: "ERR", error: e.message, read: 0, ms: performance.now() - t0, whole: false }; }
}

const secAt = (off, clen, durMs) => (!clen || !durMs ? null : (off / clen) * (durMs / 1000));

// Runs the battery for one resolved format. Returns { label, ok, steps } — ok only when EVERY step passed.
async function qualityBattery(label, fmt, resolved, durMs, ua) {
  const clen = fmt.contentLength ? Number(fmt.contentLength) : null;
  const steps = {};
  console.log(`\n----- ${label} (${fmt.kind || "audio"} itag=${fmt.itag} ${codecOf(fmt.mimeType)} ${fmt.width ? `${fmt.width}x${fmt.height}` : "audio"} br=${fmt.bitrate} clen=${clen ? mb(clen) : "?"}) -----`);
  if (!resolved.url) { console.log("  NO URL — resolution failed"); return { label, ok: false, steps: { resolve: false } }; }

  const a = await fetchRange(resolved.url, 0, CHUNK - 1, { ua });
  steps.initial = a.status === 206;
  console.log(`A initial 0-${CHUNK - 1}        -> ${a.status} ${a.contentRange || ""} ${msOf(0, a.ms)}`);

  let firstFail = null, offset = CHUNK;
  while (clen && offset < clen && (secAt(offset, clen, durMs) ?? 0) < COVER_SECONDS) {
    let res = await fetchRange(resolved.url, offset, offset + CHUNK - 1, { ua });
    if (res.status === "ERR") {
      // A fetch exception is a transport hiccup (conn reset under rapid fresh connections), not a
      // CDN verdict like 403 — one retry separates the two. ExoPlayer likewise retries transport
      // errors; only an HTTP rejection is the signal this battery hunts.
      res = await fetchRange(resolved.url, offset, offset + CHUNK - 1, { ua });
    }
    if (res.status !== 200 && res.status !== 206) { firstFail = { offset, status: res.status, error: res.error }; break; }
    offset += CHUNK;
  }
  steps.sweep = firstFail == null;
  console.log(`B sweep (fresh conns)        -> ${firstFail
    ? `FAIL @byte ${firstFail.offset} (${mb(firstFail.offset)}) -> ${firstFail.status}`
    : `all 206 through ${mb(offset)}${offset > 1048576 ? " [past the 1 MiB wall ✓]" : ""}`}`);

  const seekOff = clen ? Math.floor(clen * 0.75) : CHUNK * 40;
  const c = await fetchRange(resolved.url, seekOff, seekOff + CHUNK - 1, { ua });
  steps.seek = c.status === 206;
  console.log(`C seek @75% (byte ${seekOff}) -> ${c.status}`);

  if (MODE !== "stream") {
    const d = await drainWhole(resolved.url, ua);
    steps.download = d.whole && (d.status === 206 || d.status === 200);
    const rate = d.read && d.ms ? `${((d.read / 1048576) / (d.ms / 1000)).toFixed(1)}MB/s` : "";
    console.log(`D full download bytes=0-     -> ${d.status} ${mb(d.read)}${d.total ? ` / ${mb(d.total)}` : ""} ${d.whole ? "[COMPLETE ✓]" : "[INCOMPLETE ✗]"} ${msOf(0, d.ms)} ${rate}`);
  }

  const ok = Object.values(steps).every(Boolean);
  console.log(`   => ${label}: ${ok ? "PASS" : "FAIL"} (${Object.entries(steps).map(([k, v]) => `${k}=${v ? "✓" : "✗"}`).join(" ")})`);
  return { label, ok, steps };
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));
  console.log(`video=${VIDEO_ID} chunk=${kb(CHUNK)} cover=${COVER_SECONDS}s mode=${MODE} urlPot=${URL_POT}`);

  let t = performance.now();
  const cipher = await createCipher({ verbose: true });
  console.log(`cipher ready ${msOf(t, performance.now())}  hash=${cipher.hash} sts=${cipher.sts}`);

  t = performance.now();
  let tokens = {};
  try {
    tokens = await mintWebPoTokens({ visitorData, videoId: VIDEO_ID });
    console.log(`poTokens minted ${msOf(t, performance.now())}`);
  } catch (e) { console.log(`poToken mint FAILED: ${e.message}`); }

  const { http, j } = await playerRequest(WEB_REMIX, VIDEO_ID, visitorData, cred.dataSyncId, {
    cookie: cred.cookie, auth: true, sts: cipher.sts, poToken: tokens.playerRequestPoToken,
  });
  const ps = j?.playabilityStatus || {};
  console.log(`player http=${http} playability=${ps.status}${ps.reason ? ` (${ps.reason})` : ""} musicVideoType=${j?.videoDetails?.musicVideoType} title="${j?.videoDetails?.title}"`);
  if (ps.status !== "OK") { cipher._close?.(); process.exit(1); }
  const durMs = j?.videoDetails?.lengthSeconds ? Number(j.videoDetails.lengthSeconds) * 1000 : null;

  let ladder = qualityLadder(j);
  const audio = findAudioFormat(j);
  console.log(`\nquality ladder (${ladder.length} rungs, high→low):`);
  for (const f of ladder) {
    console.log(`  ${String(f.qualityLabel || "?").padEnd(6)} ${f.kind.padEnd(11)} itag=${String(f.itag).padEnd(4)} ${codecOf(f.mimeType).padEnd(5)} ${f.width}x${f.height} br=${f.bitrate} clen=${f.contentLength ? mb(Number(f.contentLength)) : "?"}`);
  }
  console.log(`  audio  adaptive    itag=${String(audio?.itag).padEnd(4)} ${codecOf(audio?.mimeType).padEnd(5)} br=${audio?.bitrate} clen=${audio?.contentLength ? mb(Number(audio.contentLength)) : "?"}`);
  if (LABELS) ladder = ladder.filter((f) => LABELS.includes(f.qualityLabel));

  const results = [];
  for (const fmt of ladder) {
    const resolved = resolveUrl(fmt, cipher, tokens);
    results.push(await qualityBattery(fmt.qualityLabel || `${fmt.height}p`, fmt, resolved, durMs, USER_AGENT_WEB));
  }
  if (audio) {
    const resolved = resolveUrl(audio, cipher, tokens);
    results.push(await qualityBattery("audio", { ...audio, kind: "audio" }, resolved, durMs, USER_AGENT_WEB));
  }

  console.log(`\n========== SUMMARY (${VIDEO_ID}) ==========`);
  let allOk = true;
  for (const r of results) {
    allOk = allOk && r.ok;
    console.log(`  ${r.label.padEnd(6)} ${r.ok ? "PASS ✓" : "FAIL ✗"}`);
  }
  console.log(allOk ? "\nALL QUALITIES PASS ✓" : "\nSOME QUALITIES FAILED ✗");
  cipher._close?.();
  process.exit(allOk ? 0 : 1);
})();
