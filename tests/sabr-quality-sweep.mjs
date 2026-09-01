// EMPIRICAL quality sweep: drain EVERY audio itag and EVERY video itag over SABR and prove
// byte-exact whole delivery — for zemer's request (MIN) vs innertubex's request (RICH). Audio is a
// single-track drain; video is zemer's DUAL-TRACK (video+audio in one request) vs innertubex's
// approach of two INDEPENDENT single-track streams (video-only, discarding audio).
//
//   node tests/sabr-quality-sweep.mjs [videoId] [CLIENT,CLIENT]
//
// Reports, per itag: whole (byte-exact) / capped / error, for each request style.

import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";
import crypto from "node:crypto";

const VIDEO_ID = process.argv[2] || "dQw4w9WgXcQ";
const CLIENTS = (process.argv[3] || "VISIONOS,WEB_REMIX").split(",");
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

const ROSTER = {
  WEB_REMIX: { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: 67, ua: WEB_UA, web: 1, auth: 1 },
  TVHTML5_SIMPLY: { key: "TVHTML5_SIMPLY", clientName: "TVHTML5_SIMPLY", clientVersion: "1.0", clientId: 75, ua: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown", web: 1 },
  VISIONOS: { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  MWEB: { key: "MWEB", clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: 2, ua: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)", web: 1, auth: 1 },
};

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

// ---- AUDIO single-track requests ----
const streamerAll = (c, pot, cookie, ctxAll) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : EMPTY, ...ctxAll.map((x) => fB(5, Buffer.concat([fV(1, x.type), fB(2, x.value)])))]);
function audioMin(ust, fmt, pot, c, ptMs, ranges, cookie, selected, ctxAll) {
  return Buffer.concat([fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 1)])), selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : EMPTY, ...ranges.map((r) => fB(3, Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(r.endMs)), fV(4, 1), fV(5, r.endSeg)]))), ptMs ? fV(4, Math.round(ptMs)) : EMPTY, fB(5, ust), fB(16, fmtId(fmt.itag, fmt.lastModified)), fB(19, streamerAll(c, pot, cookie, ctxAll))]);
}
function audioRich(ust, fmt, vfmt, vH, drc, atid, pot, c, ptMs, ranges, cookie, selected, active, inactive) {
  const cas = Buffer.concat([vH ? fV(21, vH) : EMPTY, ptMs ? fV(28, Math.round(ptMs)) : EMPTY, fV(34, 1), fF(35, 1), fV(40, 1), drc ? fV(46, 1) : EMPTY, atid ? fS(69, atid) : EMPTY]);
  const disc = fB(3, Buffer.concat([fB(1, fmtId(vfmt.itag, vfmt.lastModified)), fV(3, SENTINEL), fV(4, SENTINEL), fV(5, SENTINEL), fB(6, Buffer.concat([fV(2, SENTINEL), fV(3, 1000)]))]));
  const strm = Buffer.concat([fB(1, Buffer.concat([fV(16, c.clientId), fS(17, c.clientVersion)])), fB(2, pot), cookie ? fB(3, cookie) : EMPTY, ...active.map((x) => fB(5, Buffer.concat([fV(1, x.type), fB(2, x.value)]))), inactive.length ? fB(6, Buffer.concat(inactive.map((t) => varint(t)))) : EMPTY]);
  return Buffer.concat([fB(1, cas), selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : EMPTY, fB(2, fmtId(vfmt.itag, vfmt.lastModified)), ...ranges.map((r) => fB(3, Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(3, Math.round(r.endMs)), fV(4, 1), fV(5, r.endSeg), fB(6, Buffer.concat([fV(2, Math.round(r.endMs)), fV(3, 1000)]))]))), disc, fB(5, ust), fB(16, fmtId(fmt.itag, fmt.lastModified)), fB(17, fmtId(vfmt.itag, vfmt.lastModified)), fB(19, strm)]);
}

// ---- DUAL-TRACK (video+audio) request: zemer abrRequestVideo ----
function videoDual(ust, v, a, pot, c, ptMs, ranges, cookie, selected, ctxAll) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 0)])), // 0 => video+audio
    selected ? Buffer.concat([fB(2, fmtId(v.itag, v.lastModified)), fB(2, fmtId(a.itag, a.lastModified))]) : EMPTY,
    ...ranges.map((r) => fB(3, Buffer.concat([fB(1, fmtId(r.itag, r.lastModified)), fV(2, Math.round(r.startMs)), fV(3, Math.round(Math.max(0, r.endMs - r.startMs))), fV(4, r.startSeg), fV(5, r.endSeg)]))),
    ptMs ? fV(4, Math.round(ptMs)) : EMPTY,
    fB(5, ust), fB(16, fmtId(a.itag, a.lastModified)), fB(17, fmtId(v.itag, v.lastModified)),
    fB(19, streamerAll(c, pot, cookie, ctxAll)),
  ]);
}

function sapisidHash(cookie) { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const ts = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`; }
async function playerRequest(c, visitorData, cred, webPot, sts) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (c.auth && cred.dataSyncId) body.context.user = { onBehalfOfUser: cred.dataSyncId };
  if (c.web && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (c.web && webPot) body.serviceIntegrityDimensions = { poToken: webPot };
  const h = { "Content-Type": "application/json", "X-YouTube-Client-Name": String(c.clientId), "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.ua, "X-Goog-Visitor-Id": visitorData };
  if (c.auth && cred.cookie) { h.cookie = cred.cookie; const a = sapisidHash(cred.cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await res.text()); } catch {}
  return { http: res.status, j };
}
const withPot = (u, p) => (p ? u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(p) : u);

async function drainAudio(style, c, sd, ust, fmt, vfmt, pot, urlPot, xform) {
  const segs = new Map(); let initBytes = 0, url = withPot(xform(sd.serverAbrStreamingUrl), urlPot), ptMs = 0, bufEndMs = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0; const ctx = new Map();
  const vH = vfmt?.height || 0, drc = !!fmt.isDrc, atid = fmt.audioTrack?.id || null;
  while (iter < 200 && dry < 5) {
    iter++;
    const ranges = lastSeq ? [{ endMs: bufEndMs, endSeg: lastSeq }] : [];
    const ctxAll = [...ctx.entries()].map(([type, value]) => ({ type, value }));
    const body = style === "MIN" ? audioMin(ust, fmt, pot, c, ptMs, ranges, cookie, lastSeq > 0, ctxAll)
      : audioRich(ust, fmt, vfmt, vH, drc, atid, pot, c, ptMs, ranges, cookie, lastSeq > 0, ctxAll, []);
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body });
    if (res.status >= 400) return { err: `HTTP ${res.status}`, segs, endSeg, secs: Math.round(bufEndMs / 1000) };
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrErr = false, gotCtx = false;
    for (const p of parts) {
      if (p.type === 20) { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; }
      else if (p.type === 42) endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg;
      else if (p.type === 43) { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.type === 35) { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.type === 57) { const u = readProto(p.payload); ctx.set(N(u[1]?.[0]), u[3]?.[0] || EMPTY); gotCtx = true; }
      else if (p.type === 44) sabrErr = true;
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (!initBytes) initBytes = h.clen; continue; } if (!segs.has(h.seq)) segs.set(h.seq, h.clen); if (h.seq > lastSeq) lastSeq = h.seq; const e = h.startMs + h.durMs; if (e > bufEndMs) bufEndMs = e; newSeg = true; }
    ptMs = bufEndMs;
    if (sabrErr) return { err: "SABR_ERROR", segs, endSeg, secs: Math.round(bufEndMs / 1000) };
    if (redirect && !newSeg) { url = withPot(xform(redirect), urlPot); iter--; continue; }
    dry = (newSeg || gotCtx) ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }
  const sum = [...segs.values()].reduce((a, b) => a + b, 0);
  const clen = Number(fmt.contentLength);
  return { segs, endSeg, whole: endSeg > 0 && segs.size === endSeg && initBytes + sum === clen, secs: Math.round(bufEndMs / 1000), bytes: initBytes + sum, clen };
}

// dual-track video: routes interleaved MEDIA_HEADERs by itag; per-track byte-exact completion.
async function drainVideoDual(c, sd, ust, v, a, pot, urlPot, xform) {
  const T = {}; const mk = (itag, clen) => (T[itag] ||= { segs: new Map(), init: 0, lastSeq: 0, bufEndMs: 0, firstSeq: 0, firstMs: 0, endSeg: 0, clen });
  mk(v.itag, Number(v.contentLength)); mk(a.itag, Number(a.contentLength));
  let url = withPot(xform(sd.serverAbrStreamingUrl), urlPot), ptMs = 0, cookie = null, iter = 0, dry = 0; const ctx = new Map();
  while (iter < 300 && dry < 5) {
    iter++;
    const ranges = Object.entries(T).filter(([, t]) => t.lastSeq > 0).map(([itag, t]) => ({ itag: Number(itag), lastModified: itag == v.itag ? v.lastModified : a.lastModified, endMs: t.bufEndMs, endSeg: t.lastSeq, startMs: t.firstMs, startSeg: t.firstSeq || 1 }));
    const anySel = Object.values(T).some((t) => t.lastSeq > 0);
    const ctxAll = [...ctx.entries()].map(([type, value]) => ({ type, value }));
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body: videoDual(ust, v, a, pot, c, ptMs, ranges, cookie, anySel, ctxAll) });
    if (res.status >= 400) return { err: `HTTP ${res.status}`, T };
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrErr = false, gotCtx = false;
    for (const p of parts) {
      if (p.type === 20) { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { itag: N(h[3]?.[0]), seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; if (hdr[id].itag) mk(hdr[id].itag, 0); }
      else if (p.type === 43) { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.type === 35) { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.type === 57) { const u = readProto(p.payload); ctx.set(N(u[1]?.[0]), u[3]?.[0] || EMPTY); gotCtx = true; }
      else if (p.type === 44) sabrErr = true;
    }
    for (const id in hdr) { const h = hdr[id]; const t = T[h.itag]; if (!t) continue; if (h.init) { if (!t.init) t.init = h.clen; continue; } if (!t.segs.has(h.seq)) t.segs.set(h.seq, h.clen); if (t.firstSeq === 0 || h.seq < t.firstSeq) { t.firstSeq = h.seq; t.firstMs = h.startMs; } if (h.seq > t.lastSeq) t.lastSeq = h.seq; const e = h.startMs + h.durMs; if (e > t.bufEndMs) t.bufEndMs = e; newSeg = true; }
    ptMs = Math.min(...Object.values(T).filter((t) => t.lastSeq > 0).map((t) => t.bufEndMs));
    if (!isFinite(ptMs)) ptMs = 0;
    if (sabrErr) return { err: "SABR_ERROR", T };
    if (redirect && !newSeg) { url = withPot(xform(redirect), urlPot); iter--; continue; }
    dry = (newSeg || gotCtx) ? 0 : dry + 1;
    const done = [v.itag, a.itag].every((it) => { const t = T[it]; const sum = [...t.segs.values()].reduce((x, y) => x + y, 0); return t.clen > 0 && t.init + sum === t.clen; });
    if (done) break;
  }
  const res = {};
  for (const it of [v.itag, a.itag]) { const t = T[it]; const sum = [...t.segs.values()].reduce((x, y) => x + y, 0); res[it] = { whole: t.clen > 0 && t.init + sum === t.clen, bytes: t.init + sum, clen: t.clen, segs: t.segs.size }; }
  return { T: res };
}

const isAudio = (f) => f.width == null;
const label = (f) => `${f.qualityLabel || (f.width ? f.height + "p" : (Math.round((f.bitrate || 0) / 1000) + "k"))} ${(f.mimeType || "").split(";")[0].replace("audio/", "").replace("video/", "")}`.trim();

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nvideo=${VIDEO_ID}\n`);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const videoPot = await minter.mint(VIDEO_ID);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");

  for (const key of CLIENTS) {
    const c = ROSTER[key]; if (!c) { console.log(`(unknown client ${key})`); continue; }
    const { http, j } = await playerRequest(c, visitorData, cred, webPot, cipher.sts);
    const ps = j?.playabilityStatus?.status || `HTTP${http}`;
    const sd = j?.streamingData || {};
    const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
    if (ps !== "OK" || !sd.serverAbrStreamingUrl || !ustB64) { console.log(`\n### ${key}: not usable (${ps})`); continue; }
    const ust = Buffer.from(ustB64, "base64");
    const xform = c.web ? ((u) => cipher.transformNParamInUrl(u)) : ((u) => u);
    const urlPot = c.web ? videoPot : null;
    const fmts = sd.adaptiveFormats || [];
    const audios = fmts.filter(isAudio);
    const videos = fmts.filter((f) => f.width != null && /video\//.test(f.mimeType || ""));
    const discardV = videos.slice().sort((x, y) => (x.height - y.height) || (x.bitrate - y.bitrate))[0];
    const bestA = audios.slice().sort((x, y) => y.bitrate - x.bitrate)[0];

    console.log(`\n### ${key} — AUDIO qualities (single-track SABR)`);
    console.log("  itag  label".padEnd(30), "MIN".padEnd(22), "RICH");
    for (const a of audios) {
      const rMin = await drainAudio("MIN", c, sd, ust, a, discardV, potBytes, urlPot, xform);
      const rRich = await drainAudio("RICH", c, sd, ust, a, discardV, potBytes, urlPot, xform);
      const vv = (r) => r.err ? `✗ ${r.err} (${r.segs.size}/${r.endSeg})` : r.whole ? `✓ WHOLE ${r.segs.size}` : `✗ cap ${r.segs.size}/${r.endSeg} ~${r.secs}s`;
      console.log(`  ${String(a.itag).padEnd(5)} ${label(a).padEnd(22)}`.padEnd(30), vv(rMin).padEnd(22), vv(rRich));
    }

    console.log(`\n### ${key} — VIDEO qualities (dual-track video+audio, pinned itag) [audio partner itag ${bestA?.itag}]`);
    console.log("  itag  label".padEnd(30), "video".padEnd(24), "audio");
    for (const v of videos) {
      const r = await drainVideoDual(c, sd, ust, v, bestA, potBytes, urlPot, xform);
      if (r.err) { console.log(`  ${String(v.itag).padEnd(5)} ${label(v).padEnd(22)}`.padEnd(30), `✗ ${r.err}`); continue; }
      const tv = r.T[v.itag], ta = r.T[bestA.itag];
      const vv = (t) => t.whole ? `✓ WHOLE ${t.segs}` : `✗ ${t.bytes}/${t.clen} (${t.segs})`;
      console.log(`  ${String(v.itag).padEnd(5)} ${label(v).padEnd(22)}`.padEnd(30), vv(tv).padEnd(24), vv(ta));
    }
  }
})();
