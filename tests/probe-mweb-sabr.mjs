// MWEB-over-SABR diagnostic: drain MWEB and log, per iteration, EVERY UMP part type, the
// STREAM_PROTECTION_STATUS value, any SABR_ERROR (type/code), context updates (type/sendByDefault/
// writePolicy), and the NEXT_REQUEST_POLICY, to see EXACTLY what the server does at the cap.
//   node tests/probe-mweb-sabr.mjs [videoId]
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";
import crypto from "node:crypto";

const VIDEO_ID = process.argv[2] || "gl9VXSMZwTo";
const ORIGIN = "https://m.youtube.com";
const PLAYER_ORIGIN = "https://music.youtube.com";
const PLAYER_URL = PLAYER_ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const C = { clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: 2, ua: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)", web: 1, auth: 1 };
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w);
const fV = (f, val) => Buffer.concat([tag(f, 0), varint(val)]);
const fB = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const fS = (f, s) => fB(f, Buffer.from(s, "utf8"));
const EMPTY = Buffer.alloc(0);
const fmtId = (itag, lm) => Buffer.concat([fV(1, itag), lm ? fV(2, BigInt(lm)) : EMPTY]);
const clientInfo = (c) => Buffer.concat([fV(16, c.clientId), fS(17, c.clientVersion)]);
const streamerCtx = (c, pot, cookie, ctx) => Buffer.concat([fB(1, clientInfo(c)), fB(2, pot), cookie ? fB(3, cookie) : EMPTY, ...(ctx || []).map((u) => fB(5, u))]);
const bufRange = (fmt, endMs, endSeg) => Buffer.concat([fB(1, fmtId(fmt.itag, fmt.lastModified)), fV(2, 0), fV(3, Math.round(endMs)), fV(4, 1), fV(5, endSeg)]);
function buildReq(ust, fmt, pot, c, ptMs, ranges, cookie, selected, ctx) {
  return Buffer.concat([fB(1, Buffer.concat([fV(28, Math.round(ptMs)), fV(40, 1)])), selected ? fB(2, fmtId(fmt.itag, fmt.lastModified)) : EMPTY, ...ranges.map((r) => fB(3, bufRange(fmt, r.endMs, r.endSeg))), ptMs ? fV(4, Math.round(ptMs)) : EMPTY, fB(5, ust), fB(16, fmtId(fmt.itag, fmt.lastModified)), fB(19, streamerCtx(c, pot, cookie, ctx))]);
}
function umpVar(buf, pos) { const b0 = buf[pos]; let sz = 1; if (b0 >= 128) sz = 2; if (b0 >= 192) sz = 3; if (b0 >= 224) sz = 4; if (b0 >= 240) sz = 5; let v; if (sz === 1) v = b0; else if (sz === 2) v = (b0 & 0x3f) + buf[pos + 1] * 64; else if (sz === 3) v = (b0 & 0x1f) + buf[pos + 1] * 32 + buf[pos + 2] * 8192; else if (sz === 4) v = (b0 & 0x0f) + buf[pos + 1] * 16 + buf[pos + 2] * 4096 + buf[pos + 3] * 1048576; else v = buf[pos + 1] + buf[pos + 2] * 256 + buf[pos + 3] * 65536 + buf[pos + 4] * 16777216; return [v, sz]; }
function parseUmp(buf) { const parts = []; let p = 0; while (p < buf.length) { const [t, ts] = umpVar(buf, p); p += ts; if (p >= buf.length) break; const [sz, ss] = umpVar(buf, p); p += ss; parts.push({ type: t, payload: buf.subarray(p, p + sz) }); p += sz; } return parts; }
function pbv(buf, pos) { let sh = 0n, r = 0n, p = pos; for (;;) { const b = buf[p++]; r |= BigInt(b & 0x7f) << sh; if (!(b & 0x80)) break; sh += 7n; } return [r, p - pos]; }
function readProto(buf) { const o = {}; let p = 0; while (p < buf.length) { const [t, ts] = pbv(buf, p); p += ts; const tn = Number(t), f = tn >> 3, w = tn & 7; let val; if (w === 0) { const [v, vs] = pbv(buf, p); p += vs; val = v; } else if (w === 2) { const [l, ls] = pbv(buf, p); p += ls; const ln = Number(l); val = buf.subarray(p, p + ln); p += ln; } else if (w === 5) { val = BigInt(buf.readUInt32LE(p)); p += 4; } else if (w === 1) { val = buf.readBigUInt64LE(p); p += 8; } else break; (o[f] ||= []).push(val); } return o; }
const N = (x) => (x == null ? 0 : Number(x));
function timeRangeMs(buf) { if (!buf) return { startMs: 0, durMs: 0 }; const t = readProto(buf); const ts = N(t[3]?.[0]) || 1000; return { startMs: (N(t[1]?.[0]) / ts) * 1000, durMs: (N(t[2]?.[0]) / ts) * 1000 }; }
const PART = { 20:"MEDIA_HEADER",21:"MEDIA",22:"MEDIA_END",35:"NEXT",42:"FMT_INIT",43:"REDIR",44:"SABR_ERROR",45:"?45",46:"RELOAD_PLAYER",57:"CTX_UPDATE",58:"PROTECTION",59:"CTX_SEND_POLICY",60:"?60",61:"SABR_SEEK?",62:"END_OF_TRACK",66:"?66",67:"?67" };

function sapisidHash(cookie) { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const ts = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${PLAYER_ORIGIN}`).digest("hex")}`; }
async function player(visitorData, cred, webPot, sts) {
  const client = { clientName: C.clientName, clientVersion: C.clientVersion, hl: "en", gl: "US", visitorData };
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (cred.dataSyncId) body.context.user = { onBehalfOfUser: cred.dataSyncId };
  if (sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (webPot) body.serviceIntegrityDimensions = { poToken: webPot };
  const h = { "Content-Type": "application/json", "X-YouTube-Client-Name": String(C.clientId), "X-YouTube-Client-Version": C.clientVersion, "X-Origin": PLAYER_ORIGIN, Referer: PLAYER_ORIGIN + "/", "User-Agent": C.ua, "X-Goog-Visitor-Id": visitorData };
  if (cred.cookie) { h.cookie = cred.cookie; const a = sapisidHash(cred.cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  return { http: res.status, j: JSON.parse(await res.text()) };
}
const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => f.width == null && (!f.audioTrack || f.audioTrack.isAutoDubbed == null)).sort((a, b) => b.bitrate - a.bitrate)[0] || null;
const withPot = (u, p) => (p ? u + (u.includes("?") ? "&" : "?") + "pot=" + encodeURIComponent(p) : u);

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nvideo=${VIDEO_ID} client=MWEB\n`);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const webPot = await minter.mint(visitorData);
  const videoPot = await minter.mint(VIDEO_ID);
  const potBytes = Buffer.from(webPot.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  const { http, j } = await player(visitorData, cred, webPot, cipher.sts);
  const ps = j?.playabilityStatus?.status;
  const sd = j?.streamingData || {};
  const ustB64 = j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const fmt = bestAudio(j);
  console.log(`play=${ps} http=${http} hasSabr=${!!sd.serverAbrStreamingUrl} itag=${fmt?.itag} clen=${fmt?.contentLength}`);
  if (ps !== "OK" || !sd.serverAbrStreamingUrl || !ustB64 || !fmt) { console.log("not usable"); return; }
  const ust = Buffer.from(ustB64, "base64");
  const xform = (u) => cipher.transformNParamInUrl(u);
  let url = withPot(xform(sd.serverAbrStreamingUrl), videoPot), ptMs = 0, bufEndMs = 0, lastSeq = 0, endSeg = 0, cookie = null, iter = 0, dry = 0;
  const ctxByType = new Map(); const segs = new Map(); let initBytes = 0;
  const clen = Number(fmt.contentLength);
  while (iter < 60 && dry < 8) {
    iter++;
    const ranges = lastSeq ? [{ endMs: bufEndMs, endSeg: lastSeq }] : [];
    const res = await fetch(url, { method: "POST", headers: { "User-Agent": C.ua, "Content-Type": "application/x-protobuf", "Accept": "application/vnd.yt-ump", "Accept-Encoding": "identity", "Origin": ORIGIN, "Referer": ORIGIN + "/" }, body: buildReq(ust, fmt, potBytes, C, ptMs, ranges, cookie, lastSeq > 0, [...ctxByType.values()]) });
    if (res.status !== 200) { console.log(`iter ${iter}: HTTP ${res.status} — STOP`); break; }
    const parts = parseUmp(Buffer.from(await res.arrayBuffer()));
    const census = {}; const hdr = {}; let newSeg = 0; let prot = null; let sabrErr = null; const ctxLog = []; let np = null;
    for (const p of parts) {
      const nm = PART[p.type] || `#${p.type}`;
      census[nm] = (census[nm] || 0) + 1;
      if (p.type === 20) { const h = readProto(p.payload); const id = N(h[1]?.[0]); const tr = timeRangeMs(h[15]?.[0]); hdr[id] = { seq: N(h[9]?.[0]), init: !!N(h[8]?.[0]), clen: N(h[14]?.[0]), startMs: tr.startMs, durMs: tr.durMs }; }
      else if (p.type === 42) endSeg = N(readProto(p.payload)[4]?.[0]) || endSeg;
      else if (p.type === 43) { const [len, s] = umpVar(p.payload, 1); url = withPot(xform(p.payload.subarray(1 + s, 1 + s + len).toString("utf8")), videoPot); }
      else if (p.type === 35) { const u = readProto(p.payload); if (u[7]?.[0]) cookie = u[7][0]; np = { backoff: N(u[4]?.[0]), targetRA: N(u[1]?.[0]) }; }
      else if (p.type === 57) { const u = readProto(p.payload); const t = N(u[1]?.[0]); const val = u[3]?.[0] || EMPTY; ctxByType.set(t, Buffer.concat([fV(1, t), fB(2, val)])); ctxLog.push(`type=${t} sendDefault=${N(u[4]?.[0])} writePolicy=${N(u[5]?.[0])} valLen=${val.length}`); }
      else if (p.type === 58) { const u = readProto(p.payload); prot = { status: N(u[1]?.[0]), maxRetries: N(u[2]?.[0]) }; }
      else if (p.type === 44) { const u = readProto(p.payload); sabrErr = { type: (u[1]?.[0] ? Buffer.from(u[1][0]).toString() : ""), code: N(u[2]?.[0]) }; }
    }
    for (const id in hdr) { const h = hdr[id]; if (h.init) { if (!initBytes) initBytes = h.clen; continue; } if (!segs.has(h.seq)) { segs.set(h.seq, h.clen); newSeg++; } if (h.seq > lastSeq) lastSeq = h.seq; const e = h.startMs + h.durMs; if (e > bufEndMs) bufEndMs = e; }
    ptMs = bufEndMs;
    dry = newSeg > 0 ? 0 : dry + 1;
    const sum = [...segs.values()].reduce((a, b) => a + b, 0);
    console.log(`iter ${String(iter).padStart(2)}: parts={${Object.entries(census).map(([k,v])=>k+":"+v).join(",")}} newSeg=${newSeg} lastSeq=${lastSeq}/${endSeg} bytes=${initBytes+sum}/${clen} (${Math.round((initBytes+sum)/clen*100)}%)`
      + (prot ? ` PROTECTION{status=${prot.status},maxRetries=${prot.maxRetries}}` : "")
      + (sabrErr ? ` SABR_ERROR{type=${sabrErr.type},code=${sabrErr.code}}` : "")
      + (np ? ` NEXT{backoff=${np.backoff},targetRA=${np.targetRA}}` : "")
      + (ctxLog.length ? ` CTX[${ctxLog.join(" | ")}]` : ""));
    if (endSeg && lastSeq >= endSeg) { console.log(">>> WHOLE ✓"); break; }
  }
  const sum = [...segs.values()].reduce((a, b) => a + b, 0);
  console.log(`\nfinal: ${segs.size}/${endSeg} segments, ${initBytes+sum}/${clen} bytes (${Math.round((initBytes+sum)/clen*100)}%)`);
})();
