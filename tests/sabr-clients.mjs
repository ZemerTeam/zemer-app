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

import "./egress.mjs"; // FIRST: routes every fetch through SCAN_PROXY when set
import crypto from "node:crypto";
import { pathToFileURL } from "node:url";
import { getCred, describeCred } from "./cred.mjs";
import { loadStreamClientsIncludingBenched } from "./stream-clients.mjs";
import { RETIRED } from "./clients-retired.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const CLI_VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

// Module + CLI: drainClientSabr / createSabrContext are what the unattended client monitor runs
// (tests/scan-stream-clients.mjs, SABR pass) — exactly this drain, never a second copy of "whole".
// The CLI roster is DYNAMIC: the table's live + benched entries and every retired client, plus the
// research-only defs below that no table ever carried (WEB, ANDROID_MUSIC, IOS_MUSIC, ...).
// clientName/clientVersion/clientId + os fields; flags: web (send web pot+sts in /player), auth (send cookie).
const RESEARCH_EXTRAS = [
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
async function playerRequest(c, videoId, visitorData, cred, webPot, sts) {
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
    let res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, c, ptMs, ranges, cookie, lastSeq > 0, [...ctxByType.values()]) });
    // A 403/5xx MID-SESSION (segments already flowing) is usually a transient throttle on the
    // egress: retry the same request twice with a pause before calling the session capped.
    for (let retry = 0; res.status >= 400 && lastSeq > 0 && retry < 2; retry++) {
      await new Promise((r) => setTimeout(r, 2000 * (retry + 1)));
      res = await fetch(url, { method: "POST", headers: { "User-Agent": c.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, c, ptMs, ranges, cookie, lastSeq > 0, [...ctxByType.values()]) });
    }
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


/** A table/retired def -> the shape this harness drains: ua, web (cipher + pots), auth (cookie). */
export function toSabrDef(c) {
  const web = c.protocol ? c.protocol === "web_cipher_pot" : Boolean(c.useWebPoTokens);
  const d = { key: c.key, clientName: c.clientName, clientVersion: c.clientVersion, clientId: Number(c.clientId), ua: c.userAgent, web: web ? 1 : 0, auth: c.loginSupported ? 1 : 0 };
  // The SABR streamerContext identity: the entry's `sabr` overrides win over its own fields.
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) {
    const v = (c.sabr && c.sabr[k]) ?? c[k];
    if (v !== undefined && v !== null) d[k] = v;
  }
  return d;
}

export async function createSabrContext() {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  return {
    cred, visitorData, cipher, minter, webPot, potBytes, hasCookie: Boolean(cred.cookie),
    forVideo: async (videoId) => ({ videoId, videoPot: await minter.mint(videoId) }),
    close: () => cipher._close?.(),
  };
}

/**
 * Drain [c] (a toSabrDef shape) over SABR for one video. Verdict `kind` mirrors the progressive
 * drain: "whole" | "partial" (capped) | "sabr-error" | "no-sabr" (no serverAbrStreamingUrl /
 * ustreamer config: the client has no SABR path) | "no-format" | "not-ok" | "http-error" |
 * "bot-gated" | "skipped-login" | "error". Definitive failures: partial, sabr-error, no-sabr,
 * no-format, not-ok, http-error. Inconclusive: bot-gated, skipped-login, error.
 */
export async function drainClientSabr(ctx, c, video, { transportRetries = 2 } = {}) {
  // A transport error mid-session (a tunnel termination, a reset) says nothing about the client:
  // redo the whole SABR drain a couple of times before reporting it as inconclusive.
  let r = await drainClientSabrOnce(ctx, c, video);
  for (let i = 0; i < transportRetries && r.kind === "error"; i++) {
    await new Promise((res) => setTimeout(res, 3000 * (i + 1)));
    r = await drainClientSabrOnce(ctx, c, video);
  }
  return r;
}

async function drainClientSabrOnce(ctx, c, { videoId, videoPot }) {
  const row = { key: c.key, video: videoId, transport: "sabr", http: null, status: "-", itag: null, segs: null, endSeg: null, kind: "error", reason: "" };
  if (c.auth && !ctx.hasCookie) return { ...row, kind: "skipped-login", reason: "login required, no cookie" };
  try {
    const { http, j } = await playerRequest(c, videoId, ctx.visitorData, ctx.cred, ctx.webPot, ctx.cipher.sts);
    row.http = http; row.status = j?.playabilityStatus?.status || "-";
    if (http !== 200) return { ...row, kind: "http-error", reason: `player HTTP ${http}` };
    if (row.status !== "OK") {
      const reason = `${row.status}${j?.playabilityStatus?.reason ? ": " + j.playabilityStatus.reason : ""}`;
      if (/confirm you.re not a bot/i.test(j?.playabilityStatus?.reason || "")) return { ...row, kind: "bot-gated", reason };
      if (c.auth && ctx.hasCookie && (row.status === "LOGIN_REQUIRED" || /sign in/i.test(j?.playabilityStatus?.reason || ""))) return { ...row, kind: "auth-failed", reason };
      return { ...row, kind: "not-ok", reason };
    }
    const sd = j?.streamingData || {};
    const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
    if (!sd.serverAbrStreamingUrl || !ustB64) return { ...row, kind: "no-sabr", reason: !sd.serverAbrStreamingUrl ? "no serverAbrStreamingUrl" : "no ustreamer config" };
    const fmt = bestAudio(j);
    if (!fmt) return { ...row, kind: "no-format", reason: "no original audio format" };
    row.itag = fmt.itag;
    const r = await drainSabr(c, sd, Buffer.from(ustB64, "base64"), fmt, ctx.potBytes, c.web ? videoPot : null, c.web ? ((u) => ctx.cipher.transformNParamInUrl(u)) : ((u) => u));
    row.segs = r.segs.size; row.endSeg = r.endSeg;
    if (r.err) return { ...row, kind: r.err.startsWith("HTTP") ? "partial" : "sabr-error", reason: `${r.err} (${r.segs.size}/${r.endSeg})` };
    return r.whole ? { ...row, kind: "whole", reason: "" } : { ...row, kind: "partial", reason: `capped ${r.segs.size}/${r.endSeg} (~${r.secs}s)` };
  } catch (e) {
    return { ...row, kind: "error", reason: String(e.message).slice(0, 80) };
  }
}

export function formatSabrRow(r) {
  const result = r.kind === "whole" ? `✓ WHOLE SONG (${r.segs}/${r.endSeg})`
    : r.kind === "skipped-login" ? "skipped (login required, no cookie)"
    : r.kind === "bot-gated" ? `? bot-gated (${r.reason}) — runner IP, not the client`
    : `✗ ${r.kind}${r.reason ? " " + r.reason : ""}`;
  return [r.key.padEnd(20), String(r.status).padEnd(14), String(r.itag ?? "-").padEnd(5), result].join(" ");
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) (async () => {
  const table = loadStreamClientsIncludingBenched();
  const known = new Set([...table.clients, ...table.benched].map((c) => c.key));
  const roster = [
    ...table.clients.map(toSabrDef), ...table.benched.map(toSabrDef),
    ...RETIRED.filter((c) => !known.has(c.key)).map(toSabrDef),
    ...RESEARCH_EXTRAS.filter((c) => !known.has(c.key) && !RETIRED.some((r) => r.key === c.key)),
  ];
  const ctx = await createSabrContext();
  console.log(describeCred(ctx.cred), `\nvideo=${CLI_VIDEO_ID}\n`);
  const video = await ctx.forVideo(CLI_VIDEO_ID);
  console.log("client".padEnd(20), "play".padEnd(14), "itag".padEnd(5), "result");
  const usable = [];
  for (const c of roster) {
    const r = await drainClientSabr(ctx, c, video);
    console.log(formatSabrRow(r));
    if (r.kind === "whole") usable.push(c.key + (c.auth ? " (auth)" : ""));
  }
  console.log(`\nUSABLE over SABR (whole song, app's bgutils pot): ${usable.length ? usable.join(", ") : "none"}`);
  ctx.close();
  process.exit(0);
})();
