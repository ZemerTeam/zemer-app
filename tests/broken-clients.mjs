// Full diagnostic suite for the app's "broken" fallback clients: ANDROID_CREATOR, IOS, IPADOS.
//
// For each client, two phases against the live CDN with the real cookie:
//   1) Playability matrix — every relevant /player request variant (anon/auth × request-pot
//      none/videoId/visitorData, with STS where the client uses it): HTTP + playabilityStatus +
//      whether a usable audio format comes back (direct url vs signatureCipher).
//   2) Stream matrix — for the first playable resolution, resolve the URL (direct, or sig+n via the
//      cipher) and test HEAD + ranges at 0 / 1 MiB / 2 MiB + a full drain, under url-pot variants
//      (none / videoId / visitorData). Tells us exactly what (if anything) makes each stream a
//      whole song.
//
//   PLAYER_HASH=4f38b487 node tests/broken-clients.mjs            # pin a known player (deterministic)
//   node tests/broken-clients.mjs <videoId>

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL, USER_AGENT_WEB } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const TEST = ["ANDROID_CREATOR", "IOS", "IPADOS"];
const MIB = 1048576;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function playerRequest(c, visitorData, dataSyncId, cookie, { sts, poToken, auth }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  if (auth && c.loginSupported && dataSyncId) body.context.user = { onBehalfOfUser: dataSyncId };
  if (c.useSignatureTimestamp && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent,
  };
  if (visitorData) h["X-Goog-Visitor-Id"] = visitorData;
  if (auth && cookie && c.loginSupported) { h.cookie = cookie; const a = sapisidHash(cookie); if (a) h.Authorization = a; }
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
  if (fmt.url) return fmt.url;                                  // direct (iOS/Android)
  if (fmt.signatureCipher) return cipher.transformNParamInUrl(cipher.deobfuscateStreamUrl(fmt.signatureCipher));
  return null;
}
function withPot(url, pot) {
  const base = url.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, "");
  return pot ? `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(pot)}` : base;
}
const ua = (c) => c.userAgent || USER_AGENT_WEB;
async function status(url, c, start, end) {
  try { const r = await fetch(url, { headers: { "User-Agent": ua(c), Range: `bytes=${start}-${end}`, Connection: "close" } }); r.body?.cancel?.(); return r.status; }
  catch { return "ERR"; }
}
async function head(url, c) {
  try { const r = await fetch(url, { method: "HEAD", headers: { "User-Agent": ua(c) } }); r.body?.cancel?.(); return r.status; }
  catch { return "ERR"; }
}
async function drain(url, c, cap) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua(c), Range: "bytes=0-" } });
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
  console.log(`video=${VIDEO_ID}\n`);
  const cipher = await createCipher({ verbose: true });
  const minter = await createMinter(visitorData);
  const potVideo = await minter.mint(VIDEO_ID);
  const potVisitor = await minter.mint(visitorData);
  console.log(`cipher hash=${cipher.hash} sts=${cipher.sts}  pots: video(${potVideo.length}) visitor(${potVisitor.length})`);

  for (const key of TEST) {
    const c = CLIENTS.find((x) => x.key === key);
    console.log(`\n════════════ ${key} ════════════`);
    console.log(`flags: clientId=${c.clientId} loginSupported=${!!c.loginSupported} loginRequired=${!!c.loginRequired} useSignatureTimestamp=${!!c.useSignatureTimestamp} useWebPoTokens=${!!c.useWebPoTokens}`);

    // ---- Phase 1: playability matrix ----
    console.log("── playability (http / status / format) ──");
    const authOpts = c.loginSupported ? [false, true] : [false];
    const reqPots = [["no pot", null], ["videoId", potVideo], ["visitorData", potVisitor]];
    let best = null;
    for (const auth of authOpts) {
      for (const [pn, pp] of reqPots) {
        const { http, j } = await playerRequest(c, visitorData, cred.dataSyncId, cred.cookie, { sts: cipher.sts, poToken: pp, auth });
        const ps = j?.playabilityStatus || {};
        const fmt = findFormat(j);
        const kind = fmt?.url ? "direct" : fmt?.signatureCipher ? "ciphered" : "-";
        console.log(`  ${(auth ? "auth" : "anon").padEnd(4)} req=${pn.padEnd(11)} -> ${http} ${(ps.status || "-").padEnd(14)} ${ps.reason ? `(${ps.reason}) ` : ""}itag=${fmt?.itag ?? "-"} ${kind}`);
        if (ps.status === "OK" && fmt && !best) best = { auth, pn, fmt };
      }
    }
    if (!best) { console.log("  RESULT: no playable stream for this account → dead client."); continue; }

    // ---- Phase 2: stream matrix on the first OK resolution ----
    const baseUrl = resolveUrl(cipher, best.fmt);
    const clen = best.fmt.contentLength ? Number(best.fmt.contentLength) : null;
    console.log(`── stream URL (from ${best.auth ? "auth" : "anon"}/${best.pn}) itag=${best.fmt.itag} ${best.fmt.url ? "direct" : "ciphered"} clen=${clen ?? "?"} ──`);
    if (!baseUrl) { console.log("  could not resolve a URL"); continue; }
    let winner = null;
    for (const [pn, pp] of [["none", null], ["videoId", potVideo], ["visitorData", potVisitor]]) {
      const u = withPot(baseUrl, pp);
      const hd = await head(u, c), s0 = await status(u, c, 0, 262143), s1 = await status(u, c, MIB, MIB + 262143), s2 = await status(u, c, 2 * MIB, 2 * MIB + 262143);
      const pass = [s1, s2].every((s) => s === 200 || s === 206);
      console.log(`  url pot=${pn.padEnd(11)} HEAD=${hd}  @0=${s0}  @1MiB=${s1}  @2MiB=${s2}  ${pass ? "✓ past 1 MiB" : ""}`);
      if (pass && !winner) winner = { pn, url: u };
    }
    if (winner) {
      const d = await drain(winner.url, c, clen ? clen + 1 : 50 * MIB);
      const whole = clen && d.read >= clen;
      console.log(`  full drain [pot=${winner.pn}] -> ${d.status} ${kb(d.read)}${clen ? `/${kb(clen)}` : ""} ${whole ? "✓ WHOLE SONG" : "✗ stopped early"}`);
      console.log(`  RESULT: streams a full song with url pot=${winner.pn}.`);
    } else {
      console.log("  RESULT: resolves a URL but NO pot serves past the 1 MiB window → broken for full playback.");
    }
  }
  cipher._close?.();
  process.exit(0);
})();
