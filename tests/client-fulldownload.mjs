// Does each client actually deliver a WHOLE song right now? The old probe only checked
// `Range: bytes=0-1` (first 2 bytes), which says nothing about the 1-MiB pot wall. This
// resolves the best audio URL per client the app's way and drains the ENTIRE file on one
// open-ended GET, reporting how many bytes/seconds actually arrive before any 403.
//
//   node tests/client-fulldownload.mjs            # JTF9fLJvniI
//   node tests/client-fulldownload.mjs <videoId>

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
// Clients to test for full delivery: WEB_REMIX (with fix), plus the direct-url fallbacks.
const TEST = ["WEB_REMIX", "IOS", "IPADOS", "ANDROID_VR_1_43_32", "ANDROID_VR_NO_AUTH", "ANDROID_VR_1_61_48"];

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
  if (c.loginSupported && dataSyncId) body.context.user = { onBehalfOfUser: dataSyncId };
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

async function drainWhole(url, ua, cap) {
  const t0 = performance.now();
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0, ms: performance.now() - t0 };
    let read = 0; const rd = r.body.getReader();
    for (;;) { const { done, value } = await rd.read(); if (done) break; read += value.length; if (cap && read >= cap) { await rd.cancel(); break; } }
    return { status: r.status, read, ms: performance.now() - t0 };
  } catch (e) { return { status: "ERR", error: e.message, read: 0, ms: performance.now() - t0 }; }
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));
  console.log(`video=${VIDEO_ID}\n`);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const potVideo = await minter.mint(VIDEO_ID);       // videoId-bound (the one the URL needs)
  const potVisitor = await minter.mint(visitorData);  // visitorData-bound (player request)

  console.log("client".padEnd(20), "http".padEnd(5), "play".padEnd(5), "itag".padEnd(5), "clen".padEnd(9), "delivered".padEnd(12), "secs".padEnd(7), "result");
  for (const key of TEST) {
    const c = CLIENTS.find((x) => x.key === key);
    if (!c) continue;
    try {
      const isWeb = c.useWebPoTokens;
      const { http, j } = await playerRequest(c, visitorData, cred.dataSyncId, cred.cookie, {
        sts: c.useSignatureTimestamp ? cipher.sts : null,
        poToken: isWeb ? potVisitor : null,   // player-request pot (session)
        auth: !!c.loginSupported,
      });
      const ps = j?.playabilityStatus?.status || "-";
      const fmt = findFormat(j);
      if (!fmt) { console.log(key.padEnd(20), String(http).padEnd(5), ps.padEnd(5), "-".padEnd(5), "-".padEnd(9), "-".padEnd(12), "-".padEnd(7), "no format"); continue; }
      let url = fmt.url || cipher.deobfuscateStreamUrl(fmt.signatureCipher);
      if (isWeb) { url = cipher.transformNParamInUrl(url); url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(potVideo)}`; } // FIX: videoId pot
      const clen = fmt.contentLength ? Number(fmt.contentLength) : null;
      const durMs = fmt.approxDurationMs ? Number(fmt.approxDurationMs) : null;
      const d = await drainWhole(url, c.userAgent, clen ? clen + 1 : 50 * 1048576);
      const secs = clen && durMs ? ((d.read / clen) * (durMs / 1000)).toFixed(0) + "s" : "?";
      const whole = clen && d.read >= clen;
      console.log(
        key.padEnd(20), String(http).padEnd(5), ps.padEnd(5), String(fmt.itag).padEnd(5),
        String(clen ?? "?").padEnd(9), kb(d.read).padEnd(12), String(secs).padEnd(7),
        whole ? "✓ WHOLE SONG" : `✗ ${d.status} after ${kb(d.read)}`,
      );
    } catch (e) {
      console.log(key.padEnd(20), "ERR", String(e.message).slice(0, 50));
    }
  }
  cipher._close?.();
  process.exit(0);
})();
