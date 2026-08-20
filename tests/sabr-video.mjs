// SABR video+audio whole-stream harness — hard data against the live CDN (the video counterpart of
// tests/sabr-stream.mjs). SABR interleaves MULTIPLE tracks in one UMP response: to play video we must
// request BOTH a video-only adaptive format AND an audio format, then reassemble the two byte streams
// separately (routed by each MEDIA_HEADER's itag) and prove FULL distinct-segment coverage for EACH.
//
// This is the proof step for video-over-SABR (danger zone: never reason from convention). It uses a
// DIRECT client (VISIONOS, no cipher) exactly like sabr-stream.mjs; the web path (n-transform) is added
// in the roster script once the dual-track request shape is proven here.
//
// KEY FINDING (2026-08-19, live, proven below): SABR video quality IS pinnable — the exact itag maps
// through, so the progressive-style quality ladder carries over to SABR. The lever is the request's
// preferred-format fields: preferredAudioFormatId = field 16 (pinned), preferredVideoFormatId = field 17
// (found by sweeping field numbers with a forced target: only field 17 made the server honor the ask;
// the earlier guess of 15 was ignored, which looked like uncontrollable server-ABR). Verified across
// 240p/360p/480p/720p/1080p on VISIONOS: request itag 133/134/135/136/137 -> server serves EXACTLY that
// itag, whole and byte-exact (so we can pin avc1 720p instead of the server's av01 default). Set the
// enabledTrackTypesBitfield to 0 (video+audio) and route each interleaved MEDIA by its MEDIA_HEADER itag.
//
// The request knobs are ENV-configurable for reproducibility of the probe:
//   BITS = clientAbrState.enabledTrackTypesBitfield (0 = request video+audio; 1 = audio only)
//   VFMT = request field number for the preferred VIDEO formatId (17 = the working lever)
//   DEBUG=1 logs, per iteration, the part-type counts + which itags' headers arrived
//
// USAGE (needs innertube_cookie.txt at repo root):
//   node tests/sabr-video.mjs [videoId] [VISIONOS|ANDROID_VR] [maxHeightPx]
//   node tests/sabr-video-clients.mjs [videoId] [maxHeightPx]   # validate the WHOLE roster

import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || "dQw4w9WgXcQ";
const CLIENT = (process.argv[3] || "VISIONOS").toUpperCase();
const MAX_H = Number(process.argv[4] || 720);
const BITS = process.env.BITS != null ? Number(process.env.BITS) : 0; // 0 = request both tracks
const VFMT = process.env.VFMT != null ? Number(process.env.VFMT) : 17; // preferred VIDEO formatId field (17 = the working lever; 16 = audio)
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";

const CLIENTS = {
  VISIONOS: { clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" },
  ANDROID_VR: { clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: 28, ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: 32 },
};
const CFG = CLIENTS[CLIENT] || CLIENTS.VISIONOS;

// ---- protobuf writers (standard LEB128) ----
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : Buffer.alloc(0)]);
const clientInfo = (c) => Buffer.concat([fS(12, c.deviceMake), fS(13, c.deviceModel), fV(16, c.clientId), fS(17, c.clientVersion), fS(18, c.osName), fS(19, c.osVersion), c.androidSdkVersion ? fV(64, c.androidSdkVersion) : Buffer.alloc(0)]);
const streamerCtx = (c, pot, cookie) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : Buffer.alloc(0)]);
const bufRange = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);

// VideoPlaybackAbrRequest { clientAbrState=1, selectedFormatId=2(repeated), bufferedRange=3(repeated),
//   playerTimeMs=4, videoPlaybackUstreamerConfig=5, preferredAudioFormatId=16, preferredVideoFormatId=VFMT, streamerContext=19 }
// ClientAbrState { playerTimeMs=28, enabledTrackTypesBitfield=40 }
function buildAbrRequest(ust, video, audio, pot, c, playerTimeMs, ranges, cookie, selected) {
  return Buffer.concat([
    fB(1, Buffer.concat([fV(28, Math.round(playerTimeMs)), fV(40, BITS)])),
    selected ? Buffer.concat([fB(2, fmtId(video.itag, video.lastModified)), fB(2, fmtId(audio.itag, audio.lastModified))]) : Buffer.alloc(0),
    ...ranges.map((r) => fB(3, bufRange(r.fmt, r.endMs, r.endSeg))),
    playerTimeMs ? fV(4, Math.round(playerTimeMs)) : Buffer.alloc(0),
    fB(5, ust),
    fB(16, fmtId(audio.itag, audio.lastModified)),
    fB(VFMT, fmtId(video.itag, video.lastModified)),
    fB(19, streamerCtx(c, pot, cookie)),
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

async function playerRequest(c, visitorData) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData, osName: c.osName, osVersion: c.osVersion, deviceMake: c.deviceMake, deviceModel: c.deviceModel };
  if (c.androidSdkVersion) client.androidSdkVersion = String(c.androidSdkVersion);
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  const res = await fetch(PLAYER_URL, { method: "POST", headers: { "Content-Type": "application/json", "X-YouTube-Client-Name": String(c.clientId), "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.ua, "X-Goog-Visitor-Id": visitorData }, body: JSON.stringify(body) });
  return res.json();
}
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;
// best video-only adaptive format at or below MAX_H (prefer avc1 for broad decode, then bitrate)
const bestVideo = (j) => (j?.streamingData?.adaptiveFormats || [])
  .filter((f) => f.width != null && (f.height || 0) <= MAX_H && /video\//.test(f.mimeType || ""))
  .sort((a, b) => (b.height - a.height) || (/avc1/.test(b.mimeType) - /avc1/.test(a.mimeType)) || (b.bitrate - a.bitrate))[0] || null;

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nclient=${CLIENT} video=${VIDEO_ID} maxH=${MAX_H} BITS=${BITS} VFMT=${VFMT}`);
  const j = await playerRequest(CFG, visitorData);
  const sd = j.streamingData || {};
  const ustB64 = j.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const vfmt = bestVideo(j), afmt = bestAudio(j);
  if (!sd.serverAbrStreamingUrl || !ustB64 || !vfmt || !afmt) { console.log("no SABR inputs (serverAbrStreamingUrl/ust/video/audio missing)", { url: !!sd.serverAbrStreamingUrl, ust: !!ustB64, vfmt: !!vfmt, afmt: !!afmt }); process.exit(1); }
  // itag -> format metadata (for buffer sizing + completion proof of whatever tracks the server serves).
  const byItag = {};
  for (const f of (sd.adaptiveFormats || [])) byItag[f.itag] = { itag: f.itag, lastModified: f.lastModified, clen: Number(f.contentLength) || 0, kind: f.width == null ? "audio" : "video", label: `${f.qualityLabel || (f.width ? f.height + "p" : "")} ${(f.mimeType || "").split(";")[0]}`.trim() };
  // Tracks are DISCOVERED from the served MEDIA_HEADER itags (the server picks the video format; we
  // requested video=136 + audio=251 as preferred). Each track reassembles its own byte stream.
  const tracks = {};
  const track = (itag) => (tracks[itag] ||= { ...(byItag[itag] || { itag, kind: "?", clen: 0, label: "itag " + itag }), segs: new Map(), assembled: Buffer.alloc(byItag[itag]?.clen || 0), init: 0, cursor: {}, lastSeq: 0, bufEndMs: 0, endSeg: 0 });
  console.log(`play=${j.playabilityStatus?.status}  requested video=${vfmt.itag}(${byItag[vfmt.itag]?.label}) audio=${afmt.itag}(${byItag[afmt.itag]?.label})\n`);

  const minter = await createMinter(visitorData);
  const pot = Buffer.from((await minter.mint(visitorData)).replace(/-/g, "+").replace(/_/g, "/"), "base64");
  const ust = Buffer.from(ustB64, "base64");

  const vPref = { itag: vfmt.itag, lastModified: vfmt.lastModified }, aPref = { itag: afmt.itag, lastModified: afmt.lastModified };
  let url = sd.serverAbrStreamingUrl, playerTimeMs = 0, cookie = null, iter = 0, dry = 0;
  const t0 = performance.now();
  while (iter < 400 && dry < 6) {
    iter++;
    const ranges = Object.values(tracks).filter((t) => t.lastSeq > 0).map((t) => ({ fmt: { itag: t.itag, lastModified: t.lastModified }, endMs: t.bufEndMs, endSeg: t.lastSeq }));
    const anySelected = Object.values(tracks).some((t) => t.lastSeq > 0);
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": CFG.ua, "Content-Type": "application/x-protobuf" }, body: buildAbrRequest(ust, vPref, aPref, pot, CFG, playerTimeMs, ranges, cookie, anySelected) });
    if (res.status >= 400) { console.log(`iter ${iter}: HTTP ${res.status} — stop`); break; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const hdr = {}; let newSeg = false, redirect = null, sabrError = false;
    for (const p of parts) {
      if (p.name === "MEDIA_HEADER") { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { itag: N(h[3]?.[0]), seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs, off: N(h[6]?.[0]) }; if (hdr[id].itag) track(hdr[id].itag); }
      else if (p.name === "MEDIA") { const [id, hs] = umpVar(p.payload, 0); const h = hdr[id]; const t = h && tracks[h.itag]; if (t) { const bytes = p.payload.subarray(hs); const isNew = h.init ? t.init === 0 : !t.segs.has(h.seq); if (isNew) { const off = t.cursor[id] ?? h.off; if (off + bytes.length <= t.assembled.length) bytes.copy(t.assembled, off); t.cursor[id] = off + bytes.length; } } }
      else if (p.name === "FMT_INIT") { const m = readProto(p.payload); const it = N(m[1]?.[0] && readProto(m[1][0])[1]?.[0]); const end = N(m[4]?.[0]); if (end && it) { const t = track(it); t.endSeg = Math.max(t.endSeg, end); } }
      else if (p.name === "REDIR") { const [len, s] = umpVar(p.payload, 1); redirect = p.payload.subarray(1 + s, 1 + s + len).toString("utf8"); }
      else if (p.name === "NEXT") { const np = readProto(p.payload); if (np[7]?.[0]) cookie = np[7][0]; }
      else if (p.name === "SABR_ERROR") sabrError = true;
    }
    if (process.env.DEBUG) {
      const counts = {}; for (const p of parts) counts[p.name] = (counts[p.name] || 0) + 1;
      const hitags = Object.values(hdr).map((h) => `${h.itag}${h.init ? "i" : "#" + h.seq}`);
      console.log(`  iter ${iter}: parts ${JSON.stringify(counts)} headers[${hitags.join(",")}] pt=${Math.round(playerTimeMs)}`);
    }
    if (sabrError) { console.log(`iter ${iter}: SABR_ERROR`); break; }
    for (const id in hdr) { const h = hdr[id]; const t = tracks[h.itag]; if (!t) continue; if (h.init) { if (t.init === 0) t.init = h.clen; continue; } if (!t.segs.has(h.seq)) t.segs.set(h.seq, h.clen); if (h.seq > t.lastSeq) t.lastSeq = h.seq; const end = h.startMs + h.durMs; if (end > t.bufEndMs) t.bufEndMs = end; newSeg = true; }
    playerTimeMs = Math.min(...Object.values(tracks).map((t) => t.bufEndMs || 0));
    if (redirect && !newSeg) { url = redirect; iter--; continue; }
    dry = newSeg ? 0 : dry + 1;
    if (Object.values(tracks).every((t) => t.endSeg > 0 && t.lastSeq >= t.endSeg)) break;
  }

  // PROOF: init + Σ(distinct per-segment content_lengths) == the format's declared contentLength. Since
  // every segment length is positive and the total is fixed, only the COMPLETE set can sum to it exactly
  // (a subset is strictly smaller) — so an exact match proves the whole track arrived, byte-for-byte.
  let allWhole = true, gotVideo = false, gotAudio = false;
  for (const it in tracks) {
    const t = tracks[it];
    if (t.clen === 0) continue; // FMT_INIT-only phantom (no media requested for it)
    const total = t.init + [...t.segs.values()].reduce((a, b) => a + b, 0);
    const whole = t.clen > 0 && total === t.clen;
    allWhole = allWhole && whole;
    if (t.kind === "video" && whole) gotVideo = true;
    if (t.kind === "audio" && whole) gotAudio = true;
    console.log(`${t.kind} itag=${it} (${t.label}): ${t.segs.size} segments, init+Σ=${total} vs contentLength ${t.clen}  ${whole ? "WHOLE ✓" : "PARTIAL (capped)"}`);
  }
  console.log(`${iter} SABR requests, ${((performance.now() - t0) / 1000).toFixed(1)}s`);
  console.log(gotVideo && gotAudio
    ? `>>> WHOLE VIDEO + AUDIO over SABR, byte-exact ✓ (${CLIENT}) — server served video itag ${Object.values(tracks).find((t) => t.kind === "video")?.itag}`
    : `>>> INCOMPLETE — video=${gotVideo ? "ok" : "missing/capped"} audio=${gotAudio ? "ok" : "missing/capped"}`);
})();
