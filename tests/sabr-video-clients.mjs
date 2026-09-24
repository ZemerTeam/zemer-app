// Validate the WHOLE client roster over SABR for VIDEO + AUDIO (the video counterpart of
// tests/sabr-clients.mjs). SABR interleaves multiple tracks in one UMP response; this drains BOTH a
// video-only adaptive format and an audio format per client, reassembles each byte stream separately
// (routed by MEDIA_HEADER itag), and proves FULL byte-exact coverage for EACH track independently.
//
//   node tests/sabr-video-clients.mjs [videoId] [maxHeightPx]
//
// Per client it reports: player status, SABR inputs present, the SERVED video itag, and a WHOLE/capped
// verdict for video and for audio. The video itag is PINNED via preferredVideoFormatId = field 17
// (proven in tests/sabr-video.mjs: the server serves exactly the requested itag, 240p..1080p), so this
// requests the same avc1 rung (<= maxHeightPx) from every client for an apples-to-apples comparison.
// Web clients' serverAbrStreamingUrl is CIPHERED (n-transform + videoId pot); direct clients use it
// as-is. A track is WHOLE when init + Σ(distinct per-segment content_lengths) == the format's declared
// contentLength (only the complete set can sum to the fixed total, since every segment length is positive).

import crypto from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const MAX_H = Number(process.argv[3] || 720);
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

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
// Request both tracks: bitfield 0 (audio+video), preferredAudioFormatId = field 16, preferredVideoFormatId
// = field 17 (BOTH pinned — the server serves exactly these itags). selected_format_ids (field 2) once locked.
function buildAbrRequest(ust, vPref, aPref, pot, c, ptMs, ranges, cookie, selectedFmts, ctx) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 0)])),
    ...selectedFmts.map((f) => fB(2, fmtId(f.itag, f.lastModified))),
    ...ranges.map((r) => fB(3, bufRange(r.fmt, r.endMs, r.endSeg))),
    ptMs ? fV(4, Math.round(ptMs)) : Buffer.alloc(0),
    fB(5, ust),
    fB(16, fmtId(aPref.itag, aPref.lastModified)),
    fB(17, fmtId(vPref.itag, vPref.lastModified)), // preferredVideoFormatId (field 17: pins the exact video itag)
    fB(19, streamerCtx(c, pot, cookie, ctx)),
  ]);
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
const bestVideo = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => f.width != null && (f.height || 0) <= MAX_H && /video\//.test(f.mimeType || "")).sort((a, b) => (b.height - a.height) || (/avc1/.test(b.mimeType) - /avc1/.test(a.mimeType)) || (b.bitrate - a.bitrate))[0] || null;

const withPot = (u, p) => (p ? u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(p) : u);
async function drainSabrVideo(c, sd, ust, vfmt, afmt, pot, urlPot, xform) {
  const byItag = {};
  for (const f of (sd.adaptiveFormats || [])) byItag[f.itag] = { itag: f.itag, lastModified: f.lastModified, clen: Number(f.contentLength) || 0, kind: f.width == null ? "audio" : "video", label: `${f.qualityLabel || (f.width ? f.height + "p" : "")} ${(f.mimeType || "").split(";")[0]}`.trim() };
  const tracks = {};
  const track = (itag) => (tracks[itag] ||= { ...(byItag[itag] || { itag, kind: "?", clen: 0, label: "itag " + itag }), segs: new Map(), init: 0, cursor: {}, lastSeq: 0, bufEndMs: 0 });
  const vPref = { itag: vfmt.itag, lastModified: vfmt.lastModified }, aPref = { itag: afmt.itag, lastModified: afmt.lastModified };
  let url = withPot(xform(sd.serverAbrStreamingUrl), urlPot), ptMs = 0, cookie = null, iter = 0, dry = 0; const ctxByType = new Map();
  while (iter < 400 && dry < 6) {
    iter++;
    const active = Object.values(tracks).filter((t) => t.lastSeq > 0);
    const ranges = active.map((t) => ({ fmt: { itag: t.itag, lastModified: t.lastModified }, endMs: t.bufEndMs, endSeg: t.lastSeq }));
    const selectedFmts = active.length ? active.map((t) => ({ itag: t.itag, lastModified: t.lastModified })) : [];
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, vPref, aPref, pot, c, ptMs, ranges, cookie, selectedFmts, [...ctxByType.values()]) });
    if (res.status >= 400) return { err: `HTTP ${res.status}`, tracks };
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrErr = false;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { itag: N(h[3]?.[0]), seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs, off: N(h[6]?.[0]) }; if (hdr[id].itag) track(hdr[id].itag); }
      else if (p.name === "MEDIA") { const [id, hs] = umpVar(p.payload, 0); const h = hdr[id]; const t = h && tracks[h.itag]; if (t) { const bytes = p.payload.subarray(hs); const isNew = h.init ? t.init === 0 : !t.segs.has(h.seq); if (isNew) { const off = t.cursor[id] ?? h.off; t.cursor[id] = off + bytes.length; } } }
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.name === "CTX") { const u = readProto(p.payload); const t = N(u[1]?.[0]); const val = u[3]?.[0] || Buffer.alloc(0); ctxByType.set(t, Buffer.concat([fV(1, t), fB(2, val)])); }
      else if (p.name === "SABR_ERROR") sabrErr = true;
    }
    for (const id in hdr) { const h = hdr[id]; const t = tracks[h.itag]; if (!t) continue; if (h.init) { if (t.init === 0) t.init = h.clen; continue; } if (!t.segs.has(h.seq)) t.segs.set(h.seq, h.clen); if (h.seq > t.lastSeq) t.lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > t.bufEndMs) t.bufEndMs = end; newSeg = true; }
    ptMs = Math.min(...Object.values(tracks).map((t) => t.bufEndMs || 0), Number.MAX_SAFE_INTEGER);
    if (!isFinite(ptMs) || ptMs === Number.MAX_SAFE_INTEGER) ptMs = 0;
    if (sabrErr) return { err: "SABR_ERROR", tracks };
    if (redirect && !newSeg) { url = withPot(xform(redirect), urlPot); iter--; continue; }
    const gotCtx = parts.some((p) => p.name === "CTX");
    dry = (newSeg || gotCtx) ? 0 : dry + 1;
    // Done when both a video and an audio track have fully arrived (byte-exact).
    const done = ["video", "audio"].every((kind) => Object.values(tracks).some((t) => t.kind === kind && t.clen > 0 && t.init + [...t.segs.values()].reduce((a, b) => a + b, 0) === t.clen));
    if (done) break;
  }
  return { tracks, iter };
}
const wholeTrack = (t) => t.clen > 0 && t.init + [...t.segs.values()].reduce((a, b) => a + b, 0) === t.clen;

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nvideo=${VIDEO_ID} maxH=${MAX_H}\n`);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const videoPot = await minter.mint(VIDEO_ID);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  console.log("client".padEnd(16), "play".padEnd(7), "sabr".padEnd(5), "vitag".padEnd(6), "video".padEnd(24), "audio");
  const usable = [];
  for (const c of ROSTER) {
    let line;
    try {
      const { http, j } = await playerRequest(c, visitorData, cred, webPot, cipher.sts);
      const ps = j?.playabilityStatus?.status || `HTTP${http}`;
      const sd = j?.streamingData || {};
      const hasSabr = !!sd.serverAbrStreamingUrl;
      const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
      const vfmt = bestVideo(j), afmt = bestAudio(j);
      if (ps !== "OK" || !hasSabr || !ustB64 || !vfmt || !afmt) {
        line = [c.key.padEnd(16), ps.padEnd(7), (hasSabr ? "yes" : "no").padEnd(5), "-".padEnd(6), (ps !== "OK" ? "not playable" : "no SABR/video inputs").padEnd(24), ""];
      } else {
        const r = await drainSabrVideo(c, sd, Buffer.from(ustB64, "base64"), vfmt, afmt, potBytes, c.web ? videoPot : null, c.web ? ((u) => cipher.transformNParamInUrl(u)) : ((u) => u));
        const v = Object.values(r.tracks).find((t) => t.kind === "video");
        const a = Object.values(r.tracks).find((t) => t.kind === "audio");
        const vv = v ? (wholeTrack(v) ? `✓ WHOLE ${v.label}` : `✗ capped (${v.segs.size}s)`) : (r.err || "none");
        const av = a ? (wholeTrack(a) ? `✓ WHOLE` : `✗ capped (${a.segs.size}s)`) : (r.err || "none");
        if (v && a && wholeTrack(v) && wholeTrack(a)) usable.push(c.key + (c.auth ? " (auth)" : ""));
        line = [c.key.padEnd(16), ps.padEnd(7), "yes".padEnd(5), String(v?.itag ?? "-").padEnd(6), vv.padEnd(24), av];
      }
    } catch (e) { line = [c.key.padEnd(16), "ERR".padEnd(7), "".padEnd(5), "".padEnd(6), (e.message || "").slice(0, 24).padEnd(24), ""]; }
    console.log(...line);
  }
  console.log(`\nUSABLE for VIDEO+AUDIO over SABR (both whole, app's bgutils pot): ${usable.length ? usable.join(", ") : "none"}`);
})();
