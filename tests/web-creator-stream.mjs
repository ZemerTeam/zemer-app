// Hard-data probe for WEB_CREATOR streaming.
//
// WEB_CREATOR is loginRequired + useSignatureTimestamp, and (in the app) useWebPoTokens=false.
// Two questions, answered against the live CDN with the real logged-in cookie:
//   1. Is WEB_CREATOR even PLAYABLE with this account? Tested with no / videoId / visitorData
//      poToken in the /player request.
//   2. If it returns a stream, does the URL serve the WHOLE song, and which &pot= (if any) does
//      it need to get past googlevideo's 1 MiB free window?
//
//   PLAYER_HASH=4f38b487 node tests/web-creator-stream.mjs            # pin a known player
//   node tests/web-creator-stream.mjs <videoId>

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL, USER_AGENT_WEB } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const WEB_CREATOR = CLIENTS.find((c) => c.key === "WEB_CREATOR");
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
const MIB = 1048576;

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function playerRequest(c, visitorData, dataSyncId, cookie, { sts, poToken }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (dataSyncId) body.context.user = { onBehalfOfUser: dataSyncId };
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
const findFormat = (j) => (j?.streamingData?.adaptiveFormats || []).filter((f) => isAudio(f) && isOriginal(f))
  .sort((a, b) => (b.bitrate + ((b.mimeType || "").startsWith("audio/webm") ? 10240 : 0)) -
                  (a.bitrate + ((a.mimeType || "").startsWith("audio/webm") ? 10240 : 0)))[0] || null;
function resolveUrl(cipher, fmt) {
  if (!fmt) return null;
  let url = fmt.url || (fmt.signatureCipher ? cipher.deobfuscateStreamUrl(fmt.signatureCipher) : null);
  if (!url) return null;
  return cipher.transformNParamInUrl(url);
}
function withPot(url, pot) {
  const base = url.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, "");
  return pot ? `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(pot)}` : base;
}
async function status(url, start, end) {
  try { const r = await fetch(url, { headers: { "User-Agent": USER_AGENT_WEB, Range: `bytes=${start}-${end}`, Connection: "close" } }); r.body?.cancel?.(); return r.status; }
  catch { return "ERR"; }
}
async function headStatus(url) {
  try { const r = await fetch(url, { method: "HEAD", headers: { "User-Agent": USER_AGENT_WEB } }); r.body?.cancel?.(); return r.status; }
  catch { return "ERR"; }
}
async function drainWhole(url, cap) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": USER_AGENT_WEB, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0 };
    let read = 0; const rd = r.body.getReader();
    for (;;) { const { done, value } = await rd.read(); if (done) break; read += value.length; if (cap && read >= cap) { await rd.cancel(); break; } }
    return { status: r.status, read };
  } catch { return { status: "ERR", read: 0 }; }
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));
  console.log(`video=${VIDEO_ID}  client=WEB_CREATOR (loginRequired=true, useWebPoTokens=false in app)\n`);
  if (!/SAPISID=/.test(cred.cookie)) console.log("WARNING: no SAPISID in cookie — WEB_CREATOR is login-required, expect LOGIN_REQUIRED\n");

  const cipher = await createCipher({ verbose: true });
  console.log(`cipher hash=${cipher.hash} sts=${cipher.sts}\n`);
  const minter = await createMinter(visitorData);
  const potVideo = await minter.mint(VIDEO_ID);
  const potVisitor = await minter.mint(visitorData);

  // 1) Playability under each request-pot variant
  console.log("========== 1) WEB_CREATOR /player playability ==========");
  const reqPots = { "no pot": null, "videoId pot": potVideo, "visitorData pot": potVisitor };
  let best = null;
  for (const [name, rp] of Object.entries(reqPots)) {
    const { http, j } = await playerRequest(WEB_CREATOR, visitorData, cred.dataSyncId, cred.cookie, { sts: cipher.sts, poToken: rp });
    const ps = j?.playabilityStatus || {};
    const fmt = findFormat(j);
    const nFmt = (j?.streamingData?.adaptiveFormats || []).length;
    console.log(`  req=${name.padEnd(16)} http=${http} playability=${ps.status}${ps.reason ? ` (${ps.reason})` : ""} formats=${nFmt} itag=${fmt?.itag ?? "-"} cipher=${fmt?.signatureCipher ? "yes" : fmt?.url ? "direct" : "-"}`);
    if (ps.status === "OK" && fmt && !best) best = { name, fmt, j };
  }

  if (!best) {
    console.log("\nRESULT: WEB_CREATOR returned no playable stream with this account (no OK + format). It is a dead fallback.");
    cipher._close?.(); process.exit(0);
  }

  // 2) URL-pot matrix on the playable resolution
  console.log(`\n========== 2) WEB_CREATOR stream URL (from req=${best.name}) ==========`);
  const base = resolveUrl(cipher, best.fmt);
  if (!base) { console.log("could not deobfuscate a URL"); cipher._close?.(); process.exit(0); }
  const clen = best.fmt.contentLength ? Number(best.fmt.contentLength) : null;
  const dur = best.fmt.approxDurationMs ? Number(best.fmt.approxDurationMs) : null;
  let host = "?"; try { host = new URL(base).host; } catch {}
  console.log(`itag=${best.fmt.itag} mime="${best.fmt.mimeType}" host=${host} clen=${clen ?? "?"} dur=${dur ? (dur / 1000).toFixed(0) + "s" : "?"}`);
  const urlPots = { "none": null, "videoId": potVideo, "visitorData": potVisitor };
  let winner = null;
  for (const [name, up] of Object.entries(urlPots)) {
    const u = withPot(base, up);
    const hd = await headStatus(u);
    const s0 = await status(u, 0, 262143);
    const s1 = await status(u, MIB, MIB + 262143);
    const s2 = await status(u, 2 * MIB, 2 * MIB + 262143);
    const pass = [s1, s2].every((s) => s === 200 || s === 206);
    console.log(`  url pot=${name.padEnd(11)} HEAD=${hd}  @0=${s0}  @1MiB=${s1}  @2MiB=${s2}  ${pass ? "PAST FREE WINDOW" : ""}`);
    if (pass && !winner) winner = { name, url: u };
  }

  if (winner) {
    const d = await drainWhole(winner.url, clen ? clen + 1 : 50 * MIB);
    const whole = clen && d.read >= clen;
    console.log(`\nFull download [url pot=${winner.name}] -> ${d.status} ${kb(d.read)}${clen ? ` / ${kb(clen)}` : ""}  ${whole ? "WHOLE SONG" : "stopped early"}`);
    console.log(`\nRESULT: WEB_CREATOR CAN stream (req=${best.name}, url pot=${winner.name}).`);
  } else {
    console.log(`\nRESULT: WEB_CREATOR resolves a URL but NO pot serves past 1 MiB — playback would drop at the wall.`);
  }
  cipher._close?.();
  process.exit(0);
})();
