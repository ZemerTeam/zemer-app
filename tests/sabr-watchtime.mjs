// Does YouTube's stats ingestion ACCEPT a SABR-transported listen's session — end to end, live?
//
// The app's SABR stats parity claim is: a SABR listen beacons EXACTLY like DIRECT — one cpn per
// listen, every SABR media POST stamped with that cpn (the CDN correlation DIRECT gets via
// stampCpn), then the same videostatsPlaybackUrl/videostatsWatchtimeUrl pings. DIRECT's half was
// proven by the watchtime replica (every beacon HTTP 204) + tests/watchtime-cpn-stream.mjs; this
// script proves the SABR half in ONE flow:
//
//   1. WEB_REMIX /player (the app's main client; auth + sts + web pot) -> SABR inputs + tracking URLs.
//   2. Drain the WHOLE song over SABR with `&cpn=<cpn>` stamped on every media POST (the app's
//      SabrConfig.cpn behavior) — byte-exact coverage required (the transport must still be whole).
//   3. Send the SAME cpn's stats session: playback ping (cmt=0, final=0), scheduled watchtime pings
//      (10/20/30s, the base.js klA offsets), final=1 — every ping must 204.
//
// A 204 on every beacon + a whole cpn-stamped drain = the ingestion accepted a session whose media
// traffic rode SABR — the same acceptance bar the DIRECT replica set. (Crediting confirmation stays
// Studio-only, owner-side, as with DIRECT.)
//
//   node tests/sabr-watchtime.mjs [videoId]

import crypto from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const CLIENT_NAME = "WEB_REMIX";
const CLIENT_VERSION = "1.20260213.01.00";
const CLIENT_ID = 67;
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const randCpn = () => Array.from({ length: 16 }, () =>
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Math.floor(Math.random() * 64)]).join("");

// ---- protobuf writers (standard LEB128) ----
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : Buffer.alloc(0)]);
const clientInfo = () => Buffer.concat([fV(16, CLIENT_ID), fS(17, CLIENT_VERSION)]);
const streamerCtx = (pot, cookie, ctxs) => Buffer.concat([fB(1, clientInfo()), fB(2, pot), cookie ? fB(3, cookie) : Buffer.alloc(0), ...ctxs.map((x) => fB(5, x))]);
const bufRange = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);
function buildAbrRequest(ust, fmt, pot, playerTimeMs, ranges, cookie, selected, ctxs) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(playerTimeMs)), fV(40, 1)])),
    selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : Buffer.alloc(0),
    ...ranges.map((r) => fB(3, bufRange(fmt, r.endMs, r.endSeg))),
    playerTimeMs ? fV(4, Math.round(playerTimeMs)) : Buffer.alloc(0),
    fB(5, ust),
    fB(16, fmtId(fmt.itag, fmt.lastModified)),
    fB(19, streamerCtx(pot, cookie, ctxs)),
  ]);
}

// ---- UMP framing + minimal proto reader ----
function umpVar(buf, pos) {
  const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5;
  let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216;
  return [v, sz];
}
const PART = { 20: "MEDIA_HEADER", 21: "MEDIA", 35: "NEXT", 42: "FMT_INIT", 43: "REDIR", 44: "SABR_ERROR", 57: "CTX" };
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ name: PART[t] || `#${t}`, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }
function pbv(buf, pos) { let sh = 0n, r = 0n, p = pos; for (;;) { const b = buf[p++]; r |= BigInt(b & 0x7f) << sh; if (!(b & 0x80)) break; sh += 7n; } return [r, p - pos]; }
function readProto(buf) { const o = {}; let p = 0; while (p < buf.length) { const [t, ts] = pbv(buf, p); p += ts; const tn = Number(t), f = tn >> 3, w = tn & 7; let val; if (w === 0) { const [v, vs] = pbv(buf, p); p += vs; val = v; } else if (w === 2) { const [l, ls] = pbv(buf, p); p += ls; const ln = Number(l); val = buf.subarray(p, p + ln); p += ln; } else if (w === 5) { val = BigInt(buf.readUInt32LE(p)); p += 4; } else if (w === 1) { val = buf.readBigUInt64LE(p); p += 8; } else break; (o[f] ||= []).push(val); } return o; }
const N = (x) => (x == null ? 0 : Number(x));
function timeRangeMs(buf) { if (!buf) return { startMs: 0, durMs: 0 }; const t = readProto(buf); const ts = N(t[3]?.[0]) || 1000; return { startMs: (N(t[1]?.[0]) / ts) * 1000, durMs: (N(t[2]?.[0]) / ts) * 1000 }; }

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  const cm = Object.fromEntries(cred.cookie.split(";").map((s) => s.trim()).filter(Boolean).map((kv) => { const i = kv.indexOf("="); return [kv.slice(0, i), kv.slice(i + 1)]; }));
  const SAP = cm["SAPISID"] || cm["__Secure-3PAPISID"];
  const auth = () => { const t = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${t}_${crypto.createHash("sha1").update(`${t} ${SAP} ${ORIGIN}`).digest("hex")}`; };
  const mh = () => ({ "content-type": "application/json", "X-YouTube-Client-Name": String(CLIENT_ID), "X-YouTube-Client-Version": CLIENT_VERSION, "X-Origin": ORIGIN, Referer: ORIGIN + "/", cookie: cred.cookie, Authorization: auth(), "User-Agent": UA, "X-Goog-Visitor-Id": visitorData });
  console.log(describeCred(cred), `\nclient=${CLIENT_NAME} video=${VIDEO_ID}`);

  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const videoPot = await minter.mint(VIDEO_ID);
  const cipher = await createCipher({});

  const body = {
    context: { client: { clientName: CLIENT_NAME, clientVersion: CLIENT_VERSION, gl: "US", hl: "en", visitorData } },
    videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true,
    playbackContext: { contentPlaybackContext: { signatureTimestamp: Number(cipher.sts) } },
    serviceIntegrityDimensions: { poToken: webPot },
  };
  const pr = await fetch(PLAYER_URL, { method: "POST", headers: mh(), body: JSON.stringify(body) });
  const j = await pr.json();
  const sd = j.streamingData || {};
  const pt = j.playbackTracking || {};
  const ustB64 = j.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const fmt = (sd.adaptiveFormats || []).filter((f) => f.width == null && (!f.audioTrack || f.audioTrack.isAutoDubbed == null)).sort((a, b) => b.bitrate - a.bitrate)[0];
  console.log(`/player http ${pr.status} play=${j.playabilityStatus?.status} sabr=${!!sd.serverAbrStreamingUrl} tracking=${!!pt.videostatsPlaybackUrl?.baseUrl}/${!!pt.videostatsWatchtimeUrl?.baseUrl}`);
  if (!sd.serverAbrStreamingUrl || !ustB64 || !fmt || !pt.videostatsPlaybackUrl?.baseUrl || !pt.videostatsWatchtimeUrl?.baseUrl) { console.log("missing SABR/tracking inputs"); process.exit(1); }
  const clen = Number(fmt.contentLength);

  // ONE cpn for the whole listen — stamped on every SABR media POST AND on every stats beacon,
  // exactly the app's SabrConfig.cpn / WatchTimeReporter pairing (DIRECT's stampCpn correlation).
  const cpn = randCpn();
  const pot = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  const ust = Buffer.from(ustB64, "base64");
  const stamp = (u) => u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(videoPot) + "&cpn=" + cpn;

  // ---- the cpn-stamped SABR drain (must still be WHOLE — the stamp must not disturb transport) ----
  const segs = new Map(); const ctxByType = new Map();
  let initBytes = 0, url = stamp(cipher.transformNParamInUrl(sd.serverAbrStreamingUrl)), ptMs = 0, bufEndMs = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0;
  while (iter < 200 && dry < 5) {
    iter++;
    const ranges = lastSeq ? [{ endMs: bufEndMs, endSeg: lastSeq }] : [];
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": UA, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, ptMs, ranges, cookie, lastSeq > 0, [...ctxByType.values()]) });
    if (res.status >= 400) { console.log(`drain iter ${iter}: HTTP ${res.status}`); break; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; }
      else if (p.name === "FMT_INIT") { endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg; }
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.name === "CTX") { const u2 = readProto(p.payload); const t = N(u2[1]?.[0]); const val = u2[3]?.[0] || Buffer.alloc(0); ctxByType.set(t, Buffer.concat([fV(1, t), fB(2, val)])); }
      else if (p.name === "SABR_ERROR") { console.log("SABR_ERROR"); process.exit(1); }
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (!initBytes) initBytes = h.clen; continue; } if (!segs.has(h.seq)) segs.set(h.seq, h.clen); if (h.seq > lastSeq) lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > bufEndMs) bufEndMs = end; newSeg = true; }
    ptMs = bufEndMs;
    if (redirect && !newSeg) { url = stamp(cipher.transformNParamInUrl(redirect)); iter--; continue; }
    dry = newSeg || parts.some((p) => p.name === "CTX") ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }
  const total = initBytes + [...segs.values()].reduce((a, b) => a + b, 0);
  const whole = endSeg > 0 && total === clen;
  console.log(`cpn-stamped SABR drain: ${segs.size}/${endSeg} segments, init+Σ=${total} vs ${clen}  ${whole ? "WHOLE ✓" : "PARTIAL ✗"}`);

  // ---- the SAME cpn's stats session (the app's beacon shape; DIRECT-replica-identical) ----
  const tm = (u) => u.replace("https://s.youtube.com", "https://music.youtube.com");
  const ping = async (base, params) => {
    const x = new URL(tm(base));
    for (const [k, v] of Object.entries(params)) x.searchParams.set(k, String(v));
    try { const r = await fetch(x.toString(), { headers: mh() }); r.body?.cancel?.(); return r.status; } catch (e) { return `ERR:${e.message}`; }
  };
  const statuses = [];
  statuses.push(["playback cmt=0", await ping(pt.videostatsPlaybackUrl.baseUrl, { ver: 2, c: CLIENT_NAME, cpn, cmt: 0, final: 0 })]);
  for (const s of [10, 20, 30]) {
    await sleep(1000); // compressed schedule (the replica proved compressed pacing still 204s)
    statuses.push([`watchtime et=${s}${s === 30 ? " final=1" : ""}`, await ping(pt.videostatsWatchtimeUrl.baseUrl, { ver: 2, c: CLIENT_NAME, cpn, st: s - 10, et: s, cmt: s, rt: s, final: s === 30 ? 1 : 0 })]);
  }
  for (const [label, code] of statuses) console.log(`  ${label.padEnd(24)} HTTP ${code}`);
  const allOk = statuses.every(([, code]) => code === 204);
  console.log(allOk && whole
    ? `>>> SABR STATS SESSION ACCEPTED ✓ — whole cpn-stamped drain + every beacon 204 (cpn=${cpn})`
    : `>>> NOT ACCEPTED — drain whole=${whole}, beacons=${statuses.map(([, c]) => c).join(",")}`);
  process.exit(allOk && whole ? 0 : 1);
})();
