// SABR (Server-side ABR / UMP) whole-song streaming harness — hard data against the live CDN.
//
// WHY THIS EXISTS
// YouTube has migrated several clients OFF progressive delivery: their `formats[].url` is now a
// ~1-MiB preview stub (past it every range 403s), and the real media lives behind the
// `serverAbrStreamingUrl` SABR/UMP protocol. This harness reproduces the app's exact path and
// STREAMS A WHOLE SONG over SABR, proving delivery by full distinct-segment coverage (init +
// every media segment 1..N, whose content-lengths sum EXACTLY to the format contentLength — a
// resend can't inflate a distinct-segment set, unlike a naive byte counter).
//
// WHAT IT PROVED (2026-08-19, live, itag 251 opus). This script is the DIRECT-client reference
// (no cipher); the full roster + the web path (n-transform, sts, context updates) is in the
// companion `tests/sabr-clients.mjs`, which validated whole-song SABR delivery per client:
//   RELIABLE (whole song on every video tested, app's bgutils pot):
//     WEB_REMIX (auth), TVHTML5_SIMPLY, VISIONOS, VISIONOS_0_1   <- WEB_REMIX is the app's MAIN client
//   INCONSISTENT: MWEB (whole on some videos, context-challenge stall on others)
//   CONTENT-CAPPED (~60s on most videos, whole only on rare unrestricted ones like dQw4w9WgXcQ):
//     IOS, IPADOS, WEB_CREATOR, ANDROID_VR
// So the cap is NOT a pure per-client wall: it varies by (client x video). The identical loop and
// bgutils pot drain VISIONOS/WEB_REMIX/TVHTML5_SIMPLY whole on all content; the sensitive clients
// (android_vr/ios/creator) are throttled to ~60s on most (esp. premium/label) content. The web
// family only works once the serverAbrStreamingUrl is n-transformed (its URL is ciphered, unlike
// the direct clients here) - that was the missing piece for WEB_REMIX/TVHTML5_SIMPLY/MWEB.
//
// USAGE (needs innertube_cookie.txt at repo root, like the other harness scripts):
//   node tests/sabr-stream.mjs                 # VISIONOS JTF9fLJvniI (whole song)
//   node tests/sabr-stream.mjs <videoId> <VISIONOS|ANDROID_VR|IOS|IPADOS>
//   node tests/sabr-clients.mjs [videoId]      # validate the WHOLE roster (incl. the web clients)
//
// PROTOCOL (reference for a native port): POST a protobuf VideoPlaybackAbrRequest to
// serverAbrStreamingUrl -> parse the UMP-framed response (MEDIA_HEADER/MEDIA/FORMAT_INIT/
// NEXT_REQUEST_POLICY/SABR_REDIRECT) -> track received segments + buffered time -> re-POST with
// the advanced clientAbrState.playerTimeMs + a bufferedRange until every segment arrives. Field
// numbers are pinned inline below.

import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || "JTF9fLJvniI";
const CLIENT = (process.argv[3] || "VISIONOS").toUpperCase();
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";

// Client identity for the /player request AND the SABR streamerContext.clientInfo.
const CLIENTS = {
  VISIONOS: { clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  ANDROID_VR: { clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: 28, ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: 32 },
  IOS: { clientName: "IOS", clientVersion: "21.03.1", clientId: 5, ua: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" },
  IPADOS: { clientName: "IOS", clientVersion: "21.03.3", clientId: 5, ua: "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)", osName: "iPadOS", osVersion: "17.7.10.21H450", deviceMake: "Apple", deviceModel: "iPad7,6" },
};
const CFG = CLIENTS[CLIENT] || CLIENTS.VISIONOS;

// ---- protobuf writers (standard LEB128) ----
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);           // varint field
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);     // length-delimited field
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
// misc.FormatId { itag=1, lastModified=2 }
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : Buffer.alloc(0)]);
// StreamerContext.ClientInfo { deviceMake=12, deviceModel=13, clientName=16, clientVersion=17, osName=18, osVersion=19, androidSdkVersion=64 }
const clientInfo = (c) => Buffer.concat([fS(12, c.deviceMake), fS(13, c.deviceModel), fV(16, c.clientId), fS(17, c.clientVersion), fS(18, c.osName), fS(19, c.osVersion), c.androidSdkVersion ? fV(64, c.androidSdkVersion) : Buffer.alloc(0)]);
// StreamerContext { clientInfo=1, poToken=2, playbackCookie=3 }
const streamerCtx = (c, pot, cookie) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : Buffer.alloc(0)]);
// BufferedRange { formatId=1, startTimeMs=2, durationMs=3, startSegmentIndex=4, endSegmentIndex=5 }
const bufRange = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);
// VideoPlaybackAbrRequest { clientAbrState=1, selectedFormatId=2, bufferedRange=3(repeated), playerTimeMs=4,
//                           videoPlaybackUstreamerConfig=5, preferredAudioFormatId=16, streamerContext=19 }
// ClientAbrState we set: playerTimeMs=28, enabledTrackTypesBitfield=40 (1 = audio only)
function buildAbrRequest(ust, fmt, pot, c, playerTimeMs, ranges, cookie, selected) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(playerTimeMs)), fV(40, 1)])),
    selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : Buffer.alloc(0),
    ...ranges.map((r) => fB(3, bufRange(fmt, r.endMs, r.endSeg))),
    playerTimeMs ? fV(4, Math.round(playerTimeMs)) : Buffer.alloc(0),
    fB(5, ust),
    fB(16, fmtId(fmt.itag, fmt.lastModified)),
    fB(19, streamerCtx(c, pot, cookie)),
  ]);
}

// ---- UMP framing (googlevideo custom varint: leading byte width bits) ----
function umpVar(buf, pos) {
  const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5;
  let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216;
  return [v, sz];
}
const PART = { 20: "MEDIA_HEADER", 21: "MEDIA", 22: "MEDIA_END", 35: "NEXT", 42: "FMT_INIT", 43: "REDIR", 44: "SABR_ERROR", 58: "PROTECTION" };
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ name: PART[t] || `#${t}`, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }

// ---- minimal protobuf reader (field -> [values]) ----
function pbv(buf, pos) { let sh = 0n, r = 0n, p = pos; for (;;) { const b = buf[p++]; r |= BigInt(b & 0x7f) << sh; if (!(b & 0x80)) break; sh += 7n; } return [r, p - pos]; }
function readProto(buf) { const o = {}; let p = 0; while (p < buf.length) { const [t, ts] = pbv(buf, p); p += ts; const tn = Number(t), f = tn >> 3, w = tn & 7; let val; if (w === 0) { const [v, vs] = pbv(buf, p); p += vs; val = v; } else if (w === 2) { const [l, ls] = pbv(buf, p); p += ls; const ln = Number(l); val = buf.subarray(p, p + ln); p += ln; } else if (w === 5) { val = BigInt(buf.readUInt32LE(p)); p += 4; } else if (w === 1) { val = buf.readBigUInt64LE(p); p += 8; } else break; (o[f] ||= []).push(val); } return o; }
const N = (x) => (x == null ? 0 : Number(x));
// TimeRange { startTicks=1, durationTicks=2, timescale=3 } -> ms
function timeRangeMs(buf) { if (!buf) return { startMs: 0, durMs: 0 }; const t = readProto(buf); const ts = N(t[3]?.[0]) || 1000; return { startMs: (N(t[1]?.[0]) / ts) * 1000, durMs: (N(t[2]?.[0]) / ts) * 1000 }; }

async function playerRequest(c, visitorData, poToken) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData, osName: c.osName, osVersion: c.osVersion, deviceMake: c.deviceMake, deviceModel: c.deviceModel };
  if (c.androidSdkVersion) client.androidSdkVersion = String(c.androidSdkVersion);
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const res = await fetch(PLAYER_URL, { method: "POST", headers: { "Content-Type": "application/json", "X-YouTube-Client-Name": String(c.clientId), "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.ua, "X-Goog-Visitor-Id": visitorData }, body: JSON.stringify(body) });
  return res.json();
}
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nclient=${CLIENT} video=${VIDEO_ID}`);
  const j = await playerRequest(CFG, visitorData);
  const sd = j.streamingData || {};
  const ustB64 = j.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const fmt = bestAudio(j);
  if (!sd.serverAbrStreamingUrl || !ustB64 || !fmt) { console.log("no SABR inputs (serverAbrStreamingUrl/ustreamerConfig/audio format missing)"); process.exit(1); }
  const clen = Number(fmt.contentLength);
  console.log(`play=${j.playabilityStatus?.status} audio itag=${fmt.itag} contentLength=${clen} (${(clen / 1024).toFixed(0)}KB) dur=${(N(fmt.approxDurationMs) / 1000).toFixed(0)}s\n`);

  const minter = await createMinter(visitorData);
  const pot = Buffer.from((await minter.mint(visitorData)).replace(/-/g, "+").replace(/_/g, "/"), "base64"); // GVS pot: bgutils, visitorData-bound
  const ust = Buffer.from(ustB64, "base64");

  const segs = new Map();                 // sequenceNumber -> content_length (distinct segments)
  const assembled = Buffer.alloc(clen);   // reassembled media, written at each segment's byte offset
  let initBytes = 0, covered = 0, url = sd.serverAbrStreamingUrl, playerTimeMs = 0, bufEndMs = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0;
  const t0 = performance.now();
  while (iter < 200 && dry < 5) {
    iter++;
    const ranges = lastSeq ? [{ endMs: bufEndMs, endSeg: lastSeq }] : [];
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": CFG.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, fmt, pot, CFG, playerTimeMs, ranges, cookie, lastSeq > 0) });
    if (res.status >= 400) { console.log(`iter ${iter}: HTTP ${res.status} — stop`); break; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs, off: N(h[6]?.[0]) }; }
      else if (p.name === "MEDIA") { const [id, hs] = umpVar(p.payload, 0); const h = hdr[id]; if (h) { const bytes = p.payload.subarray(hs); const isNew = h.init ? initBytes === 0 : !segs.has(h.seq); if (isNew) { bytes.copy(assembled, h.off); h.off += bytes.length; if (!h.init) covered += bytes.length; } } }
      else if (p.name === "FMT_INIT") { endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg; }        // end_segment_number
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }  // playback_cookie
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (initBytes === 0) initBytes = h.clen; continue; } if (!segs.has(h.seq)) segs.set(h.seq, h.clen); if (h.seq > lastSeq) lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > bufEndMs) bufEndMs = end; newSeg = true; }
    playerTimeMs = bufEndMs;
    if (redirect && !newSeg) { url = redirect; iter--; continue; }
    dry = newSeg ? 0 : dry + 1;
    if (endSeg && lastSeq >= endSeg) break;
  }

  // PROOF: distinct segments 1..endSeg all present, and init + Σ(segment content_lengths) == contentLength.
  const missing = []; for (let s = 1; s <= endSeg; s++) if (!segs.has(s)) missing.push(s);
  const sumSeg = [...segs.values()].reduce((a, b) => a + b, 0);
  const total = initBytes + sumSeg;
  const whole = endSeg > 0 && missing.length === 0 && total === clen;
  console.log(`segments received: ${segs.size}/${endSeg}${missing.length ? "  (capped; missing " + missing.length + ")" : ""}`);
  console.log(`init(${initBytes}) + Σsegments(${sumSeg}) = ${total}  vs contentLength ${clen}  ${total === clen ? "EXACT MATCH" : "PARTIAL"}`);
  console.log(`${iter} SABR requests, ${((performance.now() - t0) / 1000).toFixed(1)}s`);
  console.log(whole
    ? `>>> WHOLE SONG over SABR, proven by full distinct-segment coverage ✓ (${CLIENT}, app's bgutils pot)`
    : `>>> CAPPED at ${segs.size} segments (~${Math.round(bufEndMs / 1000)}s) — ${CLIENT} is server-side identity-capped; needs native attestation (see header)`);
})();
