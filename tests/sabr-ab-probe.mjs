// EMPIRICAL A/B: does innertubex's richer SABR request / extra protocol handling deliver ANY
// real advantage over zemer's minimal request, against the LIVE CDN?
//
// For each (video x client) it drains the audio stream TWICE:
//   MIN  = zemer-app's exact request encoding (abrRequest): clientAbrState{28,40}, selected fmt,
//          bufferedRange{1..5}, top-level playerTimeMs(4), ustreamer(5), preferredAudio(16),
//          streamerContext echoing ALL contexts as field 5.
//   RICH = innertubex's encodeRequest: clientAbrState{21 videoHeight,28,34,35 float,40,46 drc,69
//          audioTrackId}, selected audio + discard-video fmt(2), bufferedRange WITH nested
//          timeRange(6), a discardRange(3) sentinel, NO top-level field 4, preferredAudio(16) +
//          preferredVideo(17), streamerContext with active contexts(5) + packed inactive types(6),
//          and honoring SABR_CONTEXT_SENDING_POLICY(59).
// It also CENSUSES every UMP part type the server emits, and flags the parts innertubex handles but
// zemer doesn't: 22 MEDIA_END, 46 RELOAD_PLAYER, 59 CTX_SENDING_POLICY, 62 END_OF_TRACK, plus
// STREAM_PROTECTION_STATUS(58) values >= 2 (attestation) and any non-200 HTTP.
//
//   node tests/sabr-ab-probe.mjs [videoId,videoId,...] [CLIENT,CLIENT,...]

import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";
import crypto from "node:crypto";

const VIDEOS = (process.argv[2] || "JTF9fLJvniI,dQw4w9WgXcQ,kJQP7kiw5Fk").split(",");
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

const ROSTER = [
  { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: 67, ua: WEB_UA, web: 1, auth: 1 },
  { key: "TVHTML5_SIMPLY", clientName: "TVHTML5_SIMPLY", clientVersion: "1.0", clientId: 75, ua: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown", web: 1 },
  { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  { key: "MWEB", clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: 2, ua: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)", web: 1, auth: 1 },
  { key: "WEB_CREATOR", clientName: "WEB_CREATOR", clientVersion: "1.20260213.00.00", clientId: 62, ua: WEB_UA, web: 1, auth: 1 },
  { key: "IOS", clientName: "IOS", clientVersion: "21.03.1", clientId: 5, ua: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" },
  { key: "ANDROID_VR", clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: 28, ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: 32 },
  { key: "WEB", clientName: "WEB", clientVersion: "2.20260213.00.00", clientId: 1, ua: WEB_UA, web: 1 },
];
const SEL = process.argv[3] ? new Set(process.argv[3].split(",")) : null;

// ---- protobuf / ump primitives ----
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const fF = (f, val) => { const b = Buffer.alloc(4); b.writeFloatLE(val, 0); return Buffer.concat([tag(f, 5), b]); };
const EMPTY = Buffer.alloc(0);
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : EMPTY]);
const clientInfo = (c) => Buffer.concat([c.deviceMake ? fS(12, c.deviceMake) : EMPTY, c.deviceModel ? fS(13, c.deviceModel) : EMPTY, fV(16, c.clientId), fS(17, c.clientVersion), c.osName ? fS(18, c.osName) : EMPTY, c.osVersion ? fS(19, c.osVersion) : EMPTY, c.androidSdkVersion ? fV(64, c.androidSdkVersion) : EMPTY]);
const SENTINEL = 2147483647;

function umpVar(buf, pos) { const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5; let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216; return [v, sz]; }
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ type: t, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }
function pbv(buf, pos) { let sh = 0n, r = 0n, p = pos; for (;;) { const b = buf[p++]; r |= BigInt(b & 0x7f) << sh; if (!(b & 0x80)) break; sh += 7n; } return [r, p - pos]; }
function readProto(buf) { const o = {}; let p = 0; while (p < buf.length) { const [t, ts] = pbv(buf, p); p += ts; const tn = Number(t), f = tn >> 3, w = tn & 7; let val; if (w === 0) { const [v, vs] = pbv(buf, p); p += vs; val = v; } else if (w === 2) { const [l, ls] = pbv(buf, p); p += ls; const ln = Number(l); val = buf.subarray(p, p + ln); p += ln; } else if (w === 5) { val = BigInt(buf.readUInt32LE(p)); p += 4; } else if (w === 1) { val = buf.readBigUInt64LE(p); p += 8; } else break; (o[f] ||= []).push(val); } return o; }
const N = (x) => (x == null ? 0 : Number(x));
function timeRangeMs(buf) { if (!buf) return { startMs: 0, durMs: 0 }; const t = readProto(buf); const ts = N(t[3]?.[0]) || 1000; return { startMs: (N(t[1]?.[0]) / ts) * 1000, durMs: (N(t[2]?.[0]) / ts) * 1000 }; }
function unpackVarints(buf) { const out = []; let p = 0; while (p < buf.length) { const [v, s] = pbv(buf, p); p += s; out.push(Number(v)); } return out; }

// ---- MIN request (zemer-app abrRequest) ----
const bufRangeMin = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);
const streamerCtxMin = (c, pot, cookie, ctxAll) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : EMPTY, ...ctxAll.map((x) => fB(5, Buffer.concat([fV(1, x.type), fB(2, x.value)])))]);
function buildMin(ust, fmt, pot, c, ptMs, ranges, cookie, selected, ctxAll) {
  return Buffer.concat([fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 1)])), selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : EMPTY, ...ranges.map((r) => fB(3, bufRangeMin(fmt, r.endMs, r.endSeg))), ptMs ? fV(4, Math.round(ptMs)) : EMPTY, fB(5, ust), fB(16, fmtId(fmt.itag, fmt.lastModified)), fB(19, streamerCtxMin(c, pot, cookie, ctxAll))]);
}

// ---- RICH request (innertubex encodeRequest) ----
function bufRangeRich(fmt, startMs, durMs, startSeg, endSeg) {
  return Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), startMs ? fV(2, Math.round(startMs)) : EMPTY, fV(3, Math.round(durMs)), fV(4, startSeg), fV(5, endSeg), fB(6, Buffer.concat([startMs ? fV(1, Math.round(startMs)) : EMPTY, fV(2, Math.round(durMs)), fV(3, 1000)]))]);
}
function discardRange(v) { return fB(3, Buffer.concat([fB(1, fmtId(v.itag, v.lastModified)), fV(3, SENTINEL), fV(4, SENTINEL), fV(5, SENTINEL), fB(6, Buffer.concat([fV(2, SENTINEL), fV(3, 1000)]))])); }
function clientInfoRich(c) { return Buffer.concat([fV(16, c.clientId), fS(17, c.clientVersion)]); }
function streamerCtxRich(c, pot, cookie, active, inactiveTypes) {
  return Buffer.concat([fB(1, clientInfoRich(c)), fB(2, pot), cookie ? fB(3, cookie) : EMPTY, ...active.map((x) => fB(5, Buffer.concat([fV(1, x.type), fB(2, x.value)]))), inactiveTypes.length ? fB(6, Buffer.concat(inactiveTypes.map((t) => varint(t)))) : EMPTY]);
}
function buildRich(ust, afmt, vfmt, vHeight, drc, audioTrackId, pot, c, ptMs, ranges, cookie, initialized, active, inactiveTypes) {
  const cas = Buffer.concat([vHeight ? fV(21, vHeight) : EMPTY, ptMs ? fV(28, Math.round(ptMs)) : EMPTY, fV(34, 1), fF(35, 1), fV(40, 1), drc ? fV(46, 1) : EMPTY, audioTrackId ? fS(69, audioTrackId) : EMPTY]);
  return Buffer.concat([
    fB(1, cas),
    initialized ? fB(2, fmtId(afmt.itag, afmt.lastModified)) : EMPTY,
    fB(2, fmtId(vfmt.itag, vfmt.lastModified)),
    ...ranges.map((r) => bufRangeRich(afmt, r.startMs, r.durMs, r.startSeg, r.endSeg)),
    discardRange(vfmt),
    fB(5, ust),
    fB(16, fmtId(afmt.itag, afmt.lastModified)),
    fB(17, fmtId(vfmt.itag, vfmt.lastModified)),
    fB(19, streamerCtxRich(c, pot, cookie, active, inactiveTypes)),
  ]);
}

// ---- player ----
function sapisidHash(cookie) { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const ts = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`; }
async function playerRequest(videoId, c, visitorData, cred, webPot, sts) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel"]) if (c[k]) client[k] = c[k];
  if (c.androidSdkVersion) client.androidSdkVersion = String(c.androidSdkVersion);
  const body = { context: { client }, videoId, contentCheckOk: true, racyCheckOk: true };
  if (c.auth && cred.dataSyncId) body.context.user = { onBehalfOfUser: cred.dataSyncId };
  if (c.web && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (c.web && webPot) body.serviceIntegrityDimensions = { poToken: webPot };
  const h = { "Content-Type": "application/json", "X-YouTube-Client-Name": String(c.clientId), "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.ua, "X-Goog-Visitor-Id": visitorData };
  if (c.auth && cred.cookie) { h.cookie = cred.cookie; const a = sapisidHash(cred.cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await res.text()); } catch {}
  return { http: res.status, j };
}
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => f.width == null && (!f.audioTrack || f.audioTrack.isAutoDubbed == null)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;
const discardVideo = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => f.width != null && f.height != null).sort((a, b) => (a.height - b.height) || (a.bitrate - b.bitrate))[0] || null;
const withPot = (u, p) => (p ? u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(p) : u);

// ---- generic drain, MIN or RICH ----
const KEEP_EXISTING = 2;
async function drain(mode, c, sd, ust, afmt, vfmt, pot, urlPot, xform) {
  const clen = Number(afmt.contentLength);
  const vHeight = vfmt?.height || 0, drc = !!afmt.isDrc, audioTrackId = afmt.audioTrack?.id || null;
  const segs = new Map(); const census = new Map(); const protStatuses = new Set(); const httpErrs = [];
  let initBytes = 0, url = withPot(xform(sd.serverAbrStreamingUrl), urlPot), ptMs = 0, bufEndMs = 0, firstMs = 0, firstSeq = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0;
  const ctxByType = new Map(); const activeTypes = new Set();
  while (iter < 200 && dry < 5) {
    iter++;
    const ctxAll = [...ctxByType.entries()].map(([type, v]) => ({ type, value: v.value }));
    const active = [...activeTypes].filter((t) => ctxByType.has(t)).map((t) => ({ type: t, value: ctxByType.get(t).value }));
    const inactiveTypes = [...ctxByType.keys()].filter((t) => !activeTypes.has(t));
    const ranges = lastSeq ? [{ endMs: bufEndMs, durMs: bufEndMs - firstMs, startMs: firstMs, startSeg: firstSeq || 1, endSeg: lastSeq }] : [];
    const body = mode === "MIN"
      ? buildMin(ust, afmt, pot, c, ptMs, ranges, cookie, lastSeq > 0, ctxAll)
      : buildRich(ust, afmt, vfmt, vHeight, drc, audioTrackId, pot, c, ptMs, ranges, cookie, lastSeq > 0, active, inactiveTypes);
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf", "Accept": "application/vnd.yt-ump", "Accept-Encoding": "identity" }, body });
    if (res.status !== 200) { httpErrs.push(res.status); if (res.status >= 400) return { mode, segs, endSeg, clen, initBytes, census, protStatuses, httpErrs, err: `HTTP ${res.status}`, secs: Math.round(bufEndMs / 1000) }; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrErr = false, gotCtx = false;
    for (const p of parts) {
      census.set(p.type, (census.get(p.type) || 0) + 1);
      if (p.type === 20) { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; }
      else if (p.type === 42) { endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg; }
      else if (p.type === 43) { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.type === 35) { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.type === 57) { const u = readProto(p.payload); const t = N(u[1]?.[0]); const val = u[3]?.[0] || EMPTY; const sendDefault = !!N(u[4]?.[0]); const writePolicy = N(u[5]?.[0]); if (writePolicy !== KEEP_EXISTING || !ctxByType.has(t)) { ctxByType.set(t, { value: val }); if (sendDefault) activeTypes.add(t); } gotCtx = true; }
      else if (p.type === 59) { const u = readProto(p.payload); for (const b of (u[1] || [])) unpackVarints(b).forEach((t) => activeTypes.add(t)); for (const b of (u[2] || [])) unpackVarints(b).forEach((t) => activeTypes.delete(t)); for (const b of (u[3] || [])) unpackVarints(b).forEach((t) => { activeTypes.delete(t); ctxByType.delete(t); }); }
      else if (p.type === 58) { protStatuses.add(N(readProto(p.payload)[1]?.[0])); }
      else if (p.type === 44) sabrErr = true;
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (initBytes === 0) initBytes = h.clen; continue; } if (!segs.has(h.seq)) segs.set(h.seq, h.clen); if (firstSeq === 0 || h.seq < firstSeq) { firstSeq = h.seq; firstMs = h.startMs; } if (h.seq > lastSeq) lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > bufEndMs) bufEndMs = end; newSeg = true; }
    ptMs = bufEndMs;
    if (sabrErr) return { mode, segs, endSeg, clen, initBytes, census, protStatuses, httpErrs, err: "SABR_ERROR", secs: Math.round(bufEndMs / 1000) };
    if (redirect && !newSeg) { url = withPot(xform(redirect), urlPot); iter--; continue; }
    dry = (newSeg || gotCtx) ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }
  const sum = [...segs.values()].reduce((a, b) => a + b, 0);
  return { mode, segs, endSeg, clen, initBytes, census, protStatuses, httpErrs, whole: endSeg > 0 && segs.size === endSeg && initBytes + sum === clen, secs: Math.round(bufEndMs / 1000) };
}

const PARTNAME = { 20: "MEDIA_HEADER", 21: "MEDIA", 22: "MEDIA_END", 35: "NEXT_REQ_POLICY", 42: "FMT_INIT", 43: "REDIRECT", 44: "SABR_ERROR", 45: "?45", 46: "RELOAD_PLAYER", 57: "CTX_UPDATE", 58: "PROTECTION", 59: "CTX_SEND_POLICY", 60: "?60", 61: "?61", 62: "END_OF_TRACK" };
const verdict = (r) => r.err ? `✗ ${r.err} ${r.segs.size}/${r.endSeg} (~${r.secs}s)` : r.whole ? `✓ WHOLE ${r.segs.size}/${r.endSeg}` : `✗ capped ${r.segs.size}/${r.endSeg} (~${r.secs}s)`;

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), "\n");
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  const allCensus = new Map(); const notable = [];
  for (const videoId of VIDEOS) {
    const videoPot = await minter.mint(videoId);
    console.log(`\n=== video ${videoId} ===`);
    console.log("client".padEnd(16), "MIN".padEnd(26), "RICH".padEnd(26), "delta");
    for (const c of ROSTER) {
      if (SEL && !SEL.has(c.key)) continue;
      try {
        const { http, j } = await playerRequest(videoId, c, visitorData, cred, webPot, cipher.sts);
        const ps = j?.playabilityStatus?.status || `HTTP${http}`;
        const sd = j?.streamingData || {};
        const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
        const afmt = bestAudio(j); const vfmt = discardVideo(j);
        if (ps !== "OK" || !sd.serverAbrStreamingUrl || !ustB64 || !afmt || !vfmt) { console.log(c.key.padEnd(16), `not usable (${ps}${vfmt ? "" : ", no video fmt"})`); continue; }
        const ust = Buffer.from(ustB64, "base64");
        const xform = c.web ? ((u) => cipher.transformNParamInUrl(u)) : ((u) => u);
        const urlPot = c.web ? videoPot : null;
        const rMin = await drain("MIN", c, sd, ust, afmt, vfmt, potBytes, urlPot, xform);
        const rRich = await drain("RICH", c, sd, ust, afmt, vfmt, potBytes, urlPot, xform);
        for (const [t, n] of [...rMin.census, ...rRich.census]) allCensus.set(t, (allCensus.get(t) || 0) + n);
        const flags = [];
        for (const r of [rMin, rRich]) { for (const t of r.census.keys()) if ([22, 46, 59, 62].includes(t)) flags.push(`${r.mode}:${PARTNAME[t]}`); for (const s of r.protStatuses) if (s >= 2) flags.push(`${r.mode}:PROT=${s}`); for (const h of r.httpErrs) flags.push(`${r.mode}:HTTP${h}`); }
        const dSeg = rRich.segs.size - rMin.segs.size;
        const delta = dSeg === 0 ? "same" : (dSeg > 0 ? `RICH +${dSeg}` : `RICH ${dSeg}`);
        if (flags.length) notable.push(`${videoId} ${c.key}: ${[...new Set(flags)].join(", ")}`);
        console.log(c.key.padEnd(16), verdict(rMin).padEnd(26), verdict(rRich).padEnd(26), delta + (flags.length ? "  ⚑ " + [...new Set(flags)].join(",") : ""));
      } catch (e) { console.log(c.key.padEnd(16), "ERR", e.message.slice(0, 60)); }
    }
  }
  console.log("\n=== UMP part-type census (all responses, all clients/videos) ===");
  for (const [t, n] of [...allCensus.entries()].sort((a, b) => a[0] - b[0])) console.log(`  ${String(t).padStart(3)} ${(PARTNAME[t] || "?").padEnd(18)} ${n}`);
  console.log("\n=== NOTABLE (parts/handling innertubex adds that the server actually emitted) ===");
  console.log(notable.length ? notable.map((x) => "  " + x).join("\n") : "  (none — server never emitted MEDIA_END/RELOAD_PLAYER/CTX_SEND_POLICY/END_OF_TRACK/attestation/HTTP-error for any tested client)");
})();
