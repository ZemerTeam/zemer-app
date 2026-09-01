// Validate the WHOLE client roster over SABR: which clients stream a whole song via
// serverAbrStreamingUrl with the app's bgutils pot (usable), and which the server caps.
//
//   node tests/sabr-clients.mjs [videoId]
//
// Reports one row per client: player status, whether it exposes SABR inputs (serverAbrStreamingUrl
// + ustreamer config), and the drain result (whole-song N/N or capped at K). Handles the two things
// the direct clients don't need: web clients' serverAbrStreamingUrl is CIPHERED, so its `n` param
// must be n-transformed (via the cipher) before POSTing - that was the missing piece that made the
// whole web family usable; and some clients gate media behind a SABR_CONTEXT_UPDATE that must be
// echoed back as streamerContext.sabr_contexts (SabrContext{type,value}).
//
// FINDINGS (2026-08-19, live, itag 251, multiple videos):
//   RELIABLE over SABR (whole song on every video, app's bgutils pot):
//     WEB_REMIX (auth), TVHTML5_SIMPLY, VISIONOS, VISIONOS_0_1     <- WEB_REMIX = the app's MAIN client
//   INCONSISTENT: MWEB (whole on some videos, context-challenge stall on others)
//   CONTENT-CAPPED (~60s on most videos; whole only on rare unrestricted ones e.g. dQw4w9WgXcQ):
//     IOS, IPADOS, WEB_CREATOR, ANDROID_VR
//   NOT REACHABLE app-side: WEB (desktop; needs browser-grade attestation), ANDROID/*_MUSIC (app-
//     context auth), TVHTML5 7.x (server-killed).
// Bottom line: SABR is a proven whole-song transport for the app's real clients (WEB_REMIX +
// TVHTML5_SIMPLY + VISIONOS) - the fallback for when progressive gets walled for them too.

import crypto from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

// clientName/clientVersion/clientId + os fields; flags: web (send web pot+sts in /player), auth (send cookie).
const ROSTER = [
  { key: "WEB", clientName: "WEB", clientVersion: "2.20260213.00.00", clientId: 1, ua: WEB_UA, web: 1 },
  { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: 67, ua: WEB_UA, web: 1, auth: 1 },
  { key: "WEB_CREATOR", clientName: "WEB_CREATOR", clientVersion: "1.20260213.00.00", clientId: 62, ua: WEB_UA, web: 1, auth: 1 },
  { key: "TVHTML5", clientName: "TVHTML5", clientVersion: "7.20260213.00.00", clientId: 7, ua: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown", web: 1 },
  { key: "TVHTML5_SIMPLY", clientName: "TVHTML5_SIMPLY", clientVersion: "1.0", clientId: 75, ua: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown", web: 1 },
  { key: "MWEB", clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: 2, ua: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)", web: 1, auth: 1 },
  { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  { key: "VISIONOS_0_1", clientName: "VISIONOS", clientVersion: "0.1", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15", osName: "visionOS", osVersion: "1.3.21O771", deviceMake: "Apple", deviceModel: "RealityDevice14,1" },
  { key: "ANDROID_VR", clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: 28, ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: 32 },
  { key: "IOS", clientName: "IOS", clientVersion: "21.03.1", clientId: 5, ua: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" },
  { key: "IPADOS", clientName: "IOS", clientVersion: "21.03.3", clientId: 5, ua: "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)", osName: "iPadOS", osVersion: "17.7.10.21H450", deviceMake: "Apple", deviceModel: "iPad7,6" },
  { key: "ANDROID", clientName: "ANDROID", clientVersion: "21.03.38", clientId: 3, ua: "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip", osName: "Android", osVersion: "14", androidSdkVersion: 34, auth: 1 },
  { key: "ANDROID_MUSIC", clientName: "ANDROID_MUSIC", clientVersion: "8.12.53", clientId: 21, ua: "com.google.android.apps.youtube.music/8.12.53 (Linux; U; Android 14) gzip", osName: "Android", osVersion: "14", androidSdkVersion: 34, auth: 1 },
  { key: "IOS_MUSIC", clientName: "IOS_MUSIC", clientVersion: "8.12.5", clientId: 26, ua: "com.google.ios.youtubemusic/8.12.5 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" },
];

const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : Buffer.alloc(0)]);
const clientInfo = (c) => Buffer.concat([c.deviceMake ? fS(12, c.deviceMake) : Buffer.alloc(0), c.deviceModel ? fS(13, c.deviceModel) : Buffer.alloc(0), fV(16, c.clientId), fS(17, c.clientVersion), c.osName ? fS(18, c.osName) : Buffer.alloc(0), c.osVersion ? fS(19, c.osVersion) : Buffer.alloc(0), c.androidSdkVersion ? fV(64, c.androidSdkVersion) : Buffer.alloc(0)]);
const streamerCtx = (c, pot, cookie, ctx) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : Buffer.alloc(0), ...(ctx || []).map((u) => fB(5, u))]);
const bufRange = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);
function buildAbrRequest(ust, fmt, pot, c, ptMs, ranges, cookie, selected, ctx) {
  return Buffer.concat([fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 1)])), selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : Buffer.alloc(0), ...ranges.map((r) => fB(3, bufRange(fmt, r.endMs, r.endSeg))), ptMs ? fV(4, Math.round(ptMs)) : Buffer.alloc(0), fB(5, ust), fB(16, fmtId(fmt.itag, fmt.lastModified)), fB(19, streamerCtx(c, pot, cookie, ctx))]);
}
function umpVar(buf, pos) { const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5; let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216; return [v, sz]; }
const PART = { 20: "MEDIA_HEADER", 21: "MEDIA", 42: "FMT_INIT", 43: "REDIR", 44: "SABR_ERROR", 35: "NEXT", 57: "CTX" };
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ name: PART[t] || `#${t}`, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }
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
  let jj = {}; try { jj = JSON.parse(await res.text()); } catch {}
  return { http: res.status, j: jj };
}
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => f.width == null && (!f.audioTrack || f.audioTrack.isAutoDubbed == null)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;

const withPot = (u, p) => p ? u + (u.includes('?') ? '&' : '?') + 'pot=' + encodeURIComponent(p) : u;
async function drainSabr(c, sd, ust, fmt, pot, urlPot, xform) {
  const clen = Number(fmt.contentLength);
  const segs = new Map(); let initBytes = 0, url = withPot(xform(sd.serverAbrStreamingUrl), urlPot), ptMs = 0, bufEndMs = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0; const ctxByType = new Map();
  while (iter < 120 && dry < 4) {
    iter++;
    const ranges = lastSeq ? [{ endMs: bufEndMs, endSeg: lastSeq }] : [];
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, c, ptMs, ranges, cookie, lastSeq > 0, [...ctxByType.values()]) });
    if (res.status >= 400) return { http: res.status, segs, endSeg, clen, err: `HTTP ${res.status}` };
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrErr = false;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; }
      else if (p.name === "FMT_INIT") { endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg; }
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.name === "CTX") { const u = readProto(p.payload); const t = N(u[1]?.[0]); const val = u[3]?.[0] || Buffer.alloc(0); ctxByType.set(t, Buffer.concat([fV(1, t), fB(2, val)])); }
      else if (p.name === "SABR_ERROR") sabrErr = true;
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (initBytes === 0) initBytes = h.clen; continue; } if (!segs.has(h.seq)) segs.set(h.seq, h.clen); if (h.seq > lastSeq) lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > bufEndMs) bufEndMs = end; newSeg = true; }
    ptMs = bufEndMs;
    if (sabrErr) return { segs, endSeg, clen, initBytes, err: "SABR_ERROR" };
    if (redirect && !newSeg) { url = withPot(xform(redirect), urlPot); iter--; continue; }
    const gotCtx = parts.some((p) => p.name === 'CTX');
    dry = (newSeg || gotCtx) ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }
  const sum = [...segs.values()].reduce((a, b) => a + b, 0);
  return { segs, endSeg, clen, initBytes, whole: endSeg > 0 && segs.size === endSeg && initBytes + sum === clen, secs: Math.round(bufEndMs / 1000) };
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nvideo=${VIDEO_ID}\n`);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const videoPot = await minter.mint(VIDEO_ID);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  console.log("client".padEnd(16), "play".padEnd(7), "sabr".padEnd(5), "cfg".padEnd(4), "itag".padEnd(5), "result");
  const usable = [];
  for (const c of ROSTER) {
    let line;
    try {
      const { http, j } = await playerRequest(c, visitorData, cred, webPot, cipher.sts);
      const ps = j?.playabilityStatus?.status || `HTTP${http}`;
      const sd = j?.streamingData || {};
      const hasSabr = !!sd.serverAbrStreamingUrl;
      const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
      const fmt = bestAudio(j);
      if (ps !== "OK" || !hasSabr || !ustB64 || !fmt) {
        line = [c.key.padEnd(16), ps.padEnd(7), (hasSabr ? "yes" : "no").padEnd(5), (ustB64 ? "yes" : "no").padEnd(4), String(fmt?.itag ?? "-").padEnd(5), ps !== "OK" ? `not playable (${j?.playabilityStatus?.reason?.slice(0, 40) || ""})` : "no SABR inputs"];
      } else {
        const r = await drainSabr(c, sd, Buffer.from(ustB64, "base64"), fmt, potBytes, c.web ? videoPot : null, c.web ? ((u) => cipher.transformNParamInUrl(u)) : ((u) => u));
        const verdict = r.err ? `✗ ${r.err} (${r.segs.size}/${r.endSeg})` : r.whole ? `✓ WHOLE SONG (${r.segs.size}/${r.endSeg})` : `✗ capped ${r.segs.size}/${r.endSeg} (~${r.secs}s)`;
        if (r.whole) usable.push(c.key + (c.auth ? " (auth)" : ""));
        line = [c.key.padEnd(16), ps.padEnd(7), "yes".padEnd(5), "yes".padEnd(4), String(fmt.itag).padEnd(5), verdict];
      }
    } catch (e) { line = [c.key.padEnd(16), "ERR".padEnd(7), "".padEnd(5), "".padEnd(4), "".padEnd(5), e.message.slice(0, 50)]; }
    console.log(...line);
  }
  console.log(`\nUSABLE over SABR (whole song, app's bgutils pot): ${usable.length ? usable.join(", ") : "none"}`);
})();
