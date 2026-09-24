// SABR SEEK proof — does a session COLD-STARTED at playerTimeMs = T serve segments from T?
//
// WHY THIS EXISTS
// The app's SABR engine reassembles positionally (absolute startRange offsets), so a mid-track seek
// could be served by RESTARTING the session at the target time instead of draining everything before
// it (a resumed 2-hour podcast episode must not download 2 hours of bytes before playing). That only
// works if the server honours a nonzero playerTimeMs on a fresh session (no bufferedRange, nothing
// selected yet) by serving the segment containing T — this script proves exactly that, live:
//
//   1. /player -> best audio format (contentLength, approxDurationMs).
//   2. POST a COLD abr request with clientAbrState.playerTimeMs = T (no ranges, not selected).
//   3. Assert the first media segments start at ~T (startMs <= T < startMs+dur), NOT at segment 1,
//      and carry a nonzero absolute startRange (so positional reassembly Just Works).
//   4. Keep draining with bufferedRange = [firstMs .. bufEnd] (startSegmentIndex = firstSeq — the
//      range echo for a seeked session starts at OUR first segment, not 1) until end_segment_number.
//   5. Prove the TAIL is whole: distinct segments firstSeq..endSeg, byte-contiguous from the first
//      segment's startRange to contentLength.
//
// PROVEN (2026-08-20, live, VISIONOS itag 251, JTF9fLJvniI T=200s): the server serves the segment
// containing T immediately (plus the init segment), startRange is absolute, and the tail drains
// whole and byte-contiguous to contentLength. This is the reference for the app's seek-restart.
//
// USAGE:  node tests/sabr-seek.mjs [videoId] [seekSeconds] [VISIONOS|WEB_REMIX|ANDROID_VR|IOS|IPADOS]
// (WEB_REMIX = the app's MAIN client: ciphered SABR url -> n-transform + videoId url-pot + sts/web-pot
//  in /player + SABR context-update echo; proven seek-honouring like the direct clients.)
//
// PACE_PAUSE_S / PACE_EVERY: sleep PAUSE seconds every EVERY iterations — the DEMAND-PACING proof
// (the app throttles the drain to what playback consumes, so minutes can pass between POSTs; this
// proves the server session survives the idle gap and keeps serving).

import crypto from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const SEEK_S = Number(process.argv[3] || 200);
const CLIENT = (process.argv[4] || "VISIONOS").toUpperCase();
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";

const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
const CLIENTS = {
  WEB_REMIX: { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: 67, ua: WEB_UA, web: true, auth: true },
  VISIONOS: { clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  ANDROID_VR: { clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: 28, ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: 32 },
  IOS: { clientName: "IOS", clientVersion: "21.03.1", clientId: 5, ua: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" },
  IPADOS: { clientName: "IOS", clientVersion: "21.03.3", clientId: 5, ua: "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)", osName: "iPadOS", osVersion: "17.7.10.21H450", deviceMake: "Apple", deviceModel: "iPad7,6" },
};
const CFG = CLIENTS[CLIENT] || CLIENTS.VISIONOS;

// ---- protobuf writers (standard LEB128) ----
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : Buffer.alloc(0)]);
const clientInfo = (c) => Buffer.concat([c.deviceMake ? fS(12, c.deviceMake) : Buffer.alloc(0), c.deviceModel ? fS(13, c.deviceModel) : Buffer.alloc(0), fV(16, c.clientId), fS(17, c.clientVersion), c.osName ? fS(18, c.osName) : Buffer.alloc(0), c.osVersion ? fS(19, c.osVersion) : Buffer.alloc(0), c.androidSdkVersion ? fV(64, c.androidSdkVersion) : Buffer.alloc(0)]);
const streamerCtx = (c, pot, cookie, ctxs = []) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : Buffer.alloc(0), ...ctxs.map((x) => fB(5, x))]);
// BufferedRange { formatId=1, startTimeMs=2, durationMs=3, startSegmentIndex=4, endSegmentIndex=5 } —
// for a SEEKED session the range starts at OUR first received segment, not (0, 1).
const bufRange = (fmt, startMs, endMs, startSeg, endSeg) => Buffer.concat([
  fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, Math.round(startMs)), fV(3, Math.round(Math.max(0, endMs - startMs))), fV(4, startSeg), fV(5, endSeg),
]);
function buildAbrRequest(ust, fmt, pot, c, playerTimeMs, range, cookie, selected, ctxs) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(playerTimeMs)), fV(40, 1)])),
    selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : Buffer.alloc(0),
    range ? fB(3, bufRange(fmt, range.startMs, range.endMs, range.startSeg, range.endSeg)) : Buffer.alloc(0),
    playerTimeMs ? fV(4, Math.round(playerTimeMs)) : Buffer.alloc(0),
    fB(5, ust),
    fB(16, fmtId(fmt.itag, fmt.lastModified)),
    fB(19, streamerCtx(c, pot, cookie, ctxs)),
  ]);
}

// ---- UMP framing ----
function umpVar(buf, pos) {
  const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5;
  let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216;
  return [v, sz];
}
const PART = { 20: "MEDIA_HEADER", 21: "MEDIA", 22: "MEDIA_END", 35: "NEXT", 42: "FMT_INIT", 43: "REDIR", 44: "SABR_ERROR", 57: "CTX", 58: "PROTECTION" };
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ name: PART[t] || `#${t}`, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }

// ---- minimal protobuf reader ----
function pbv(buf, pos) { let sh = 0n, r = 0n, p = pos; for (;;) { const b = buf[p++]; r |= BigInt(b & 0x7f) << sh; if (!(b & 0x80)) break; sh += 7n; } return [r, p - pos]; }
function readProto(buf) { const o = {}; let p = 0; while (p < buf.length) { const [t, ts] = pbv(buf, p); p += ts; const tn = Number(t), f = tn >> 3, w = tn & 7; let val; if (w === 0) { const [v, vs] = pbv(buf, p); p += vs; val = v; } else if (w === 2) { const [l, ls] = pbv(buf, p); p += ls; const ln = Number(l); val = buf.subarray(p, p + ln); p += ln; } else if (w === 5) { val = BigInt(buf.readUInt32LE(p)); p += 4; } else if (w === 1) { val = buf.readBigUInt64LE(p); p += 8; } else break; (o[f] ||= []).push(val); } return o; }
const N = (x) => (x == null ? 0 : Number(x));
function timeRangeMs(buf) { if (!buf) return { startMs: 0, durMs: 0 }; const t = readProto(buf); const ts = N(t[3]?.[0]) || 1000; return { startMs: (N(t[1]?.[0]) / ts) * 1000, durMs: (N(t[2]?.[0]) / ts) * 1000 }; }

function sapisidHash(cookie) { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const ts = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`; }
async function playerRequest(c, visitorData, cred, webPot, sts) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel"]) if (c[k]) client[k] = c[k];
  if (c.androidSdkVersion) client.androidSdkVersion = String(c.androidSdkVersion);
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (c.auth && cred.dataSyncId) body.context.user = { onBehalfOfUser: cred.dataSyncId };
  if (c.web && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (c.web && webPot) body.serviceIntegrityDimensions = { poToken: webPot };
  const h = { "Content-Type": "application/json", "X-YouTube-Client-Name": String(c.clientId), "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.ua, "X-Goog-Visitor-Id": visitorData };
  if (c.auth && cred.cookie) { h.cookie = cred.cookie; const a = sapisidHash(cred.cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  return res.json();
}
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nclient=${CLIENT} video=${VIDEO_ID} seek=${SEEK_S}s`);
  const minter = await createMinter(visitorData);
  const webPot = CFG.web ? await minter.mint(visitorData) : null;
  const videoPot = CFG.web ? await minter.mint(VIDEO_ID) : null;
  const cipher = CFG.web ? await createCipher({}) : null;
  const j = await playerRequest(CFG, visitorData, cred, webPot, cipher?.sts);
  const sd = j.streamingData || {};
  const ustB64 = j.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const fmt = bestAudio(j);
  if (!sd.serverAbrStreamingUrl || !ustB64 || !fmt) { console.log("no SABR inputs"); process.exit(1); }
  const clen = Number(fmt.contentLength);
  const durMs = N(fmt.approxDurationMs);
  const T = SEEK_S * 1000;
  if (T >= durMs) { console.log(`seek ${SEEK_S}s past duration ${(durMs / 1000).toFixed(0)}s`); process.exit(1); }
  console.log(`play=${j.playabilityStatus?.status} audio itag=${fmt.itag} contentLength=${clen} dur=${(durMs / 1000).toFixed(0)}s -> cold-start playerTimeMs=${T}\n`);

  const pot = Buffer.from((webPot ?? await minter.mint(visitorData)).replace(/-/g, "+").replace(/_/g, "/"), "base64");
  const ust = Buffer.from(ustB64, "base64");
  const xform = CFG.web ? (u) => cipher.transformNParamInUrl(u) : (u) => u;
  const withPot = (u) => (CFG.web && videoPot ? u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(videoPot) : u);

  const segs = new Map(); // seq -> { clen, off, startMs, durMs }
  const ctxByType = new Map();
  let initBytes = 0, url = withPot(xform(sd.serverAbrStreamingUrl)), playerTimeMs = T, bufEndMs = 0, cookie = null, iter = 0, dry = 0;
  let firstSeq = 0, firstMs = 0, firstOff = -1, lastSeq = 0, endSeg = 0, sawPreSeekSegment = false;
  const PAUSE_S = Number(process.env.PACE_PAUSE_S || 0), PAUSE_EVERY = Number(process.env.PACE_EVERY || 5);
  const t0 = performance.now();
  while (iter < 200 && dry < 5) {
    iter++;
    if (PAUSE_S > 0 && iter > 1 && (iter - 1) % PAUSE_EVERY === 0) {
      console.log(`  (pacing: idle ${PAUSE_S}s before iter ${iter} — lastSeq=${lastSeq})`);
      await new Promise((r) => setTimeout(r, PAUSE_S * 1000));
    }
    const range = lastSeq ? { startMs: firstMs, endMs: bufEndMs, startSeg: firstSeq, endSeg: lastSeq } : null;
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": CFG.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, CFG, playerTimeMs, range, cookie, lastSeq > 0, [...ctxByType.values()]) });
    if (res.status >= 400) { console.log(`iter ${iter}: HTTP ${res.status} — stop`); break; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, gotCtx = false;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs, off: N(h[6]?.[0]) }; }
      else if (p.name === "FMT_INIT") { endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg; }
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.name === "CTX") { const u = readProto(p.payload); const t = N(u[1]?.[0]); const val = u[3]?.[0] || Buffer.alloc(0); ctxByType.set(t, Buffer.concat([fV(1, t), fB(2, val)])); gotCtx = true; }
    }
    for (const id in hdr) {
      const h = hdr[id];
      if (h.init) { if (!initBytes) initBytes = h.clen; continue; }
      if (!segs.has(h.seq)) segs.set(h.seq, h);
      if (iter === 1 && !firstSeq) { firstSeq = h.seq; firstMs = h.startMs; firstOff = h.off; }
      if (h.seq < firstSeq) { firstSeq = h.seq; firstMs = h.startMs; firstOff = h.off; }
      if (h.startMs + h.durMs < T - 30000) sawPreSeekSegment = true; // way-before-T = server ignored the seek
      if (h.seq > lastSeq) lastSeq = h.seq;
      const end = h.startMs + h.durMs;
      if (end > bufEndMs) bufEndMs = end;
      newSeg = true;
    }
    if (bufEndMs > playerTimeMs) playerTimeMs = bufEndMs;
    if (redirect && !newSeg) { url = withPot(xform(redirect)); iter--; continue; }
    dry = newSeg || gotCtx ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }

  // PROOF 1: the first served segment CONTAINS (or nearly abuts) T — the server honoured the cold seek.
  const seekHonoured = firstSeq > 1 && firstMs <= T + 1000 && firstMs + 30000 > T && !sawPreSeekSegment;
  // PROOF 2: the tail is whole and BYTE-CONTIGUOUS from the first segment's absolute startRange to clen.
  const ordered = [...segs.values()].sort((a, b) => a.off - b.off);
  let contiguous = ordered.length > 0; let expect = firstOff;
  for (const s of ordered) { if (s.off !== expect) { contiguous = false; break; } expect = s.off + s.clen; }
  // A seeked session may get NO end_segment_number (proven on the dual-track variant) — byte coverage
  // reaching contentLength is the completion truth (the app engine judges the same way).
  const tailWhole = (endSeg === 0 || lastSeq >= endSeg) && contiguous && expect === clen;
  console.log(`first segment: seq=${firstSeq} (of ${endSeg}) startMs=${Math.round(firstMs)} (T=${T}) startRange=${firstOff}`);
  console.log(`init(${initBytes}) tail segments ${firstSeq}..${lastSeq}: ${segs.size} distinct, byte range [${firstOff}..${expect}) vs contentLength ${clen}`);
  console.log(`${iter} SABR requests, ${((performance.now() - t0) / 1000).toFixed(1)}s`);
  console.log(seekHonoured
    ? `>>> COLD-START SEEK HONOURED: server served the segment containing T, absolute startRange ✓`
    : `>>> seek NOT honoured (first seg ${firstSeq} @${Math.round(firstMs)}ms${sawPreSeekSegment ? ", served pre-seek segments" : ""})`);
  console.log(tailWhole
    ? `>>> TAIL WHOLE + BYTE-CONTIGUOUS to contentLength ✓ — seek-restart is a valid app strategy (${CLIENT})`
    : `>>> tail incomplete/non-contiguous — do NOT build seek-restart on this client without more data`);
  process.exit(seekHonoured && tailWhole ? 0 : 1);
})();
