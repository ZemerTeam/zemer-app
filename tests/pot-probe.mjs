// Definitive poToken-binding matrix for the WEB_REMIX 1-MiB / seek 403.
//
// Question: which poToken makes a googlevideo stream URL serve PAST the 1 MiB free window
// (i.e. the whole song)? Tests every combination of:
//   - request pot  : the token sent in the /player serviceIntegrityDimensions (videoId vs visitorData)
//   - url pot       : the &pot= appended to the media URL (none / videoId / visitorData-raw / visitorData-enc)
// For each cell it fetches a fresh connection at 1 MiB and 2 MiB (past the free window) and,
// for any cell that passes, drains the whole file to confirm full download.
//
// "visitorData-raw" = decoded (…==).  "visitorData-enc" = as stored in the cookie dump (…%3D%3D).
// This rules out a visitorData URL-encoding artifact as the cause.
//
//   node tests/pot-probe.mjs            # JTF9fLJvniI
//   node tests/pot-probe.mjs <videoId>

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL, USER_AGENT_WEB } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const WEB_REMIX = CLIENTS.find((c) => c.key === "WEB_REMIX");
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function playerRequest(c, visitorData, cookie, { sts, poToken }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (c.useSignatureTimestamp && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent, "X-Goog-Visitor-Id": visitorData,
  };
  if (cookie) { h.cookie = cookie; const a = sapisidHash(cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await res.text()); } catch {}
  return { http: res.status, j };
}
const isAudio = (f) => f.width == null;
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
function findFormat(j) {
  return (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f))
    .sort((a, b) => (b.bitrate + ((b.mimeType || "").startsWith("audio/webm") ? 10240 : 0)) -
                    (a.bitrate + ((a.mimeType || "").startsWith("audio/webm") ? 10240 : 0)))[0] || null;
}
async function resolveBaseUrl(cipher, visitorData, cookie, requestPot) {
  const { http, j } = await playerRequest(WEB_REMIX, visitorData, cookie, { sts: cipher.sts, poToken: requestPot });
  const ps = j?.playabilityStatus?.status;
  const fmt = findFormat(j);
  if (!fmt) return { http, ps, url: null, fmt: null };
  let url = fmt.url || cipher.deobfuscateStreamUrl(fmt.signatureCipher);
  url = cipher.transformNParamInUrl(url); // n-transform, NO pot appended
  return { http, ps, url, fmt };
}
function withPot(url, pot) {
  const base = url.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, "");
  return pot ? `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(pot)}` : base;
}
async function status(url, start, end) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": USER_AGENT_WEB, Range: `bytes=${start}-${end}`, Connection: "close" } });
    r.body?.cancel?.();
    return r.status;
  } catch (e) { return "ERR"; }
}
async function drainWhole(url, cap) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": USER_AGENT_WEB, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0 };
    let read = 0; const rd = r.body.getReader();
    for (;;) { const { done, value } = await rd.read(); if (done) break; read += value.length; if (cap && read >= cap) { await rd.cancel(); break; } }
    return { status: r.status, read };
  } catch (e) { return { status: "ERR", read: 0 }; }
}

const MIB = 1048576;

(async () => {
  const cred = await getCred();
  const visRaw = dec(cred.visitorData);          // decoded …==
  const visEnc = cred.visitorData;               // as stored …%3D%3D
  console.log(describeCred(cred));
  console.log(`video=${VIDEO_ID}`);

  const cipher = await createCipher({ verbose: true });
  console.log(`cipher hash=${cipher.hash} sts=${cipher.sts}`);

  // Mint all tokens from ONE BotGuard run (session identifier = decoded visitorData).
  const minter = await createMinter(visRaw);
  const potVideo = await minter.mint(VIDEO_ID);
  const potVisRaw = await minter.mint(visRaw);
  const potVisEnc = await minter.mint(visEnc);
  console.log(`minted: video(${potVideo.length}) visRaw(${potVisRaw.length}) visEnc(${potVisEnc.length})\n`);

  const urlPots = {
    none: null,
    "video    ": potVideo,
    "visRaw   ": potVisRaw,
    "visEnc   ": potVisEnc,
  };
  const requestPots = { "req=video": potVideo, "req=visRaw": potVisRaw };

  let best = null;
  for (const [rname, rpot] of Object.entries(requestPots)) {
    const base = await resolveBaseUrl(cipher, visRaw, cred.cookie, rpot);
    console.log(`[${rname}] player http=${base.http} playability=${base.ps} itag=${base.fmt?.itag} clen=${base.fmt?.contentLength}`);
    if (!base.url) { console.log("   no url\n"); continue; }
    const clen = Number(base.fmt.contentLength);
    for (const [uname, upot] of Object.entries(urlPots)) {
      const u = withPot(base.url, upot);
      const s1 = await status(u, MIB, MIB + 262143);          // 1 MiB
      const s2 = await status(u, 2 * MIB, 2 * MIB + 262143);  // 2 MiB
      const pass = (s1 === 200 || s1 === 206) && (s2 === 200 || s2 === 206);
      console.log(`   url pot=${uname}  @1MiB=${s1}  @2MiB=${s2}  ${pass ? "✓ PAST FREE WINDOW" : ""}`);
      if (pass && !best) best = { rname, uname: uname.trim(), url: u, clen };
    }
    console.log();
  }

  if (best) {
    console.log(`Confirming full download for [${best.rname}] url pot=${best.uname} ...`);
    const d = await drainWhole(best.url, best.clen + 1);
    const ok = d.read >= best.clen;
    console.log(`   -> ${d.status} delivered ${kb(d.read)} / ${kb(best.clen)}  ${ok ? "✓ WHOLE FILE" : "✗ stopped early"}`);
  } else {
    console.log("No pot combination served past the 1 MiB free window.");
  }
  cipher._close?.();
  process.exit(0);
})();
