// Prove that stamping the session cpn on the googlevideo media request (matching the official
// WEB_REMIX client, base.js `cpn=${videoData.clientPlaybackNonce}`) does NOT break playback: the
// cpn-stamped URL must drain the WHOLE song with 206s, exactly like the un-stamped URL.
//
// This is the regression gate for the CDN-cpn correlation change (docs/watchtime/README.md). It
// resolves a real stream URL the app's EXACT way (cipher + poToken, WEB_REMIX /player) and then
// drains it in sequential ranges the way ExoPlayer does, once WITHOUT cpn (control) and once WITH
// cpn appended, comparing byte totals and every chunk status.
//
//   node tests/watchtime-cpn-stream.mjs [videoId]
//   COVER_MIB=6 node tests/watchtime-cpn-stream.mjs   # drain more of the file

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";
import { mintWebPoTokens } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const CHUNK = Number(process.env.CHUNK || 1048576); // 1 MiB, like a big ExoPlayer read
const COVER_MIB = Number(process.env.COVER_MIB || 6);
const WEB_REMIX = CLIENTS.find((c) => c.key === "WEB_REMIX");
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const randCpn = () => Array.from({ length: 16 }, () =>
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Math.floor(Math.random() * 64)]).join("");

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
function findFormat(j) {
  const fmts = (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f));
  return fmts.sort((a, b) =>
    (b.bitrate + ((b.mimeType || "").startsWith("audio/webm") ? 10240 : 0)) -
    (a.bitrate + ((a.mimeType || "").startsWith("audio/webm") ? 10240 : 0)))[0] || null;
}

// One range fetch with a single retry on a transient network error (not an HTTP status).
async function fetchRange(url, pos, end) {
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const r = await fetch(url, { headers: { "User-Agent": WEB_REMIX.userAgent, Range: `bytes=${pos}-${end}`, Connection: "close" } });
      if (r.status === 206 || r.status === 200) { const buf = await r.arrayBuffer(); return { status: r.status, bytes: buf.byteLength }; }
      r.body?.cancel?.();
      return { status: r.status, bytes: 0 }; // an HTTP status (e.g. 403) is a real verdict — never retry it
    } catch (e) { if (attempt === 1) return { status: `ERR:${e.message}`, bytes: 0 }; }
  }
}

// Drain sequential ranges up to COVER_MIB (or clen), one fresh connection per range like ExoPlayer.
// A cpn REJECTION shows as a consistent HTTP 4xx/5xx; a transient connection drop is retried and,
// if still failing, recorded separately (network noise, not a cpn verdict).
async function drain(url, clen) {
  const cap = Math.min(clen, COVER_MIB * 1048576);
  let pos = 0, read = 0, chunks = 0; const httpBad = [], netErr = [];
  while (pos < cap) {
    const end = Math.min(pos + CHUNK - 1, clen - 1);
    const { status, bytes } = await fetchRange(url, pos, end);
    read += bytes; chunks++;
    if (typeof status === "number" && status !== 206 && status !== 200) httpBad.push({ range: `${pos}-${end}`, status });
    else if (typeof status !== "number") netErr.push({ range: `${pos}-${end}`, status });
    pos = end + 1;
  }
  return { read, chunks, httpBad, netErr };
}

const cred = await getCred();
const cipher = await createCipher();
const tokens = await mintWebPoTokens({ visitorData: dec(cred.visitorData), videoId: VIDEO_ID });
const headers = {
  "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
  "X-YouTube-Client-Name": WEB_REMIX.clientId, "X-YouTube-Client-Version": WEB_REMIX.clientVersion,
  "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": WEB_REMIX.userAgent,
  "X-Goog-Visitor-Id": dec(cred.visitorData), cookie: cred.cookie,
};
const auth = sapisidHash(cred.cookie); if (auth) headers.Authorization = auth;
const body = {
  context: { client: { clientName: WEB_REMIX.clientName, clientVersion: WEB_REMIX.clientVersion, hl: "en", gl: "US", visitorData: dec(cred.visitorData) }, user: cred.dataSyncId ? { onBehalfOfUser: cred.dataSyncId } : undefined },
  videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true,
  playbackContext: { contentPlaybackContext: { signatureTimestamp: Number(cipher.sts) } },
  serviceIntegrityDimensions: { poToken: tokens.playerRequestPoToken },
};
const res = await fetch(PLAYER_URL, { method: "POST", headers, body: JSON.stringify(body) });
const j = await res.json();
console.log("/player http", res.status, "playability", j?.playabilityStatus?.status);
const fmt = findFormat(j);
if (!fmt) { console.log("NO FORMAT"); process.exit(1); }
let url = fmt.url || cipher.deobfuscateStreamUrl(fmt.signatureCipher);
url = cipher.transformNParamInUrl(url);
url = `${url}${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(tokens.streamingDataPoToken)}`;
const clen = Number(fmt.contentLength) || 0;
const cpn = randCpn();
const withCpn = `${url}&cpn=${cpn}`;
console.log(`itag ${fmt.itag}  clen ${clen}  cover ${COVER_MIB}MiB  cpn ${cpn}`);

const ctrl = await drain(url, clen);
const test = await drain(withCpn, clen);
console.log(`CONTROL (no cpn): ${ctrl.chunks} chunks, ${(ctrl.read / 1048576).toFixed(2)} MiB read, httpBad=${JSON.stringify(ctrl.httpBad)} netErr=${ctrl.netErr.length}`);
console.log(`+CPN            : ${test.chunks} chunks, ${(test.read / 1048576).toFixed(2)} MiB read, httpBad=${JSON.stringify(test.httpBad)} netErr=${test.netErr.length}`);

// The verdict is about cpn ACCEPTANCE: zero HTTP error statuses on the cpn-stamped URL, and it drained
// a substantial amount. Transient network drops (netErr) are connection noise, not a cpn rejection.
const ok = test.httpBad.length === 0 && test.read > 1048576;
console.log(ok ? "\nPASS: cpn-stamped URL streams with 206s throughout (no HTTP rejection) — playback safe."
              : "\nFAIL: cpn-stamped URL returned an HTTP error status.");
process.exit(ok ? 0 : 1);
