// Does each client actually deliver a WHOLE song right now? The old probe only checked
// `Range: bytes=0-1` (first 2 bytes), which says nothing about the 1-MiB pot wall. This
// resolves the best audio URL per client the app's way and drains the ENTIRE file on one
// open-ended GET, reporting how many bytes/seconds actually arrive before any 403.
//
//   node tests/client-fulldownload.mjs            # JTF9fLJvniI
//   node tests/client-fulldownload.mjs <videoId>

import crypto from "node:crypto";
import { pathToFileURL } from "node:url";
import { CLIENTS, STREAM_CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { needsWebTransforms } from "./stream-clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

// Module + CLI: the drain logic is exported (drainClient / createDrainContext) so the unattended
// client monitor (tests/scan-stream-clients.mjs, run by zemer-cipher's client-monitor workflow)
// runs EXACTLY the check a human runs here — never a second copy of "what counts as delivered".
const CLI_VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
// Clients to test for full delivery: the app's MAIN_CLIENT + ALL_FALLBACK_CLIENTS in the same
// order as YTPlayerUtils.kt ("ANDROID" here = the app's MOBILE client). Override with
// CLIENTS=WEB_REMIX,TVHTML5,... to test a subset.
const TEST = process.env.CLIENTS?.split(",").map((s) => s.trim()).filter(Boolean)
  // Default: the WHOLE chain from the stream-client table, in table order — never a hand-kept
  // copy, which would silently skip a newly added client (`if (!c) continue` below).
  || STREAM_CLIENTS.map((c) => c.key);

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function playerRequest(c, videoId, visitorData, dataSyncId, cookie, { sts, poToken, auth }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId, playlistId: null, contentCheckOk: true, racyCheckOk: true };
  // InnerTube.player(): embedded clients carry thirdParty.embedUrl in the context.
  if (c.isEmbedded) body.context.thirdParty = { embedUrl: `https://www.youtube.com/watch?v=${videoId}` };
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


/**
 * One shared context per process: the credential, the cipher (player JS in jsdom), the pot minter
 * and the session-bound pot. `forVideo` mints the videoId-bound stream pot each video needs.
 */
export async function createDrainContext() {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const potVisitor = await minter.mint(visitorData);  // visitorData-bound (player request)
  return {
    cred, visitorData, cipher, minter, potVisitor,
    hasCookie: Boolean(cred.cookie),
    forVideo: async (videoId) => ({ videoId, potVideo: await minter.mint(videoId) }),
    close: () => cipher._close?.(),
  };
}

/**
 * Resolve [c]'s best audio the app's way and drain the whole file. Returns a verdict row:
 *   kind: "whole" | "partial" | "sabr-only" | "no-format" | "not-ok" | "http-error" | "error"
 *         | "skipped-login" (login-required client, no cookie: INCONCLUSIVE, never a kill signal)
 * Only "whole" is success; "partial"/"sabr-only"/"no-format"/"not-ok"/"http-error" are definitive
 * failures the app would also see; "error" (transport) and "skipped-login" are inconclusive.
 */
export async function drainClient(ctx, c, { videoId, potVideo }) {
  const row = { key: c.key, video: videoId, http: null, status: "-", itag: null, clen: null, read: 0, secs: null, kind: "error", reason: "" };
  if (c.loginRequired && !ctx.hasCookie) return { ...row, kind: "skipped-login", reason: "login required, no cookie" };
  try {
    // YTPlayerUtils.clientNeedsNTransform: the protocol decides the n-transform; the `pot=` append
    // rides ONLY useWebPoTokens (applyWebUrlTransforms).
    const needsN = needsWebTransforms(c);
    const { http, j } = await playerRequest(c, videoId, ctx.visitorData, ctx.cred.dataSyncId, ctx.cred.cookie, {
      sts: c.useSignatureTimestamp ? ctx.cipher.sts : null,
      poToken: c.useWebPoTokens ? ctx.potVisitor : null,   // player-request pot (session-bound)
      auth: !!c.loginSupported,
    });
    row.http = http;
    row.status = j?.playabilityStatus?.status || "-";
    if (http !== 200) return { ...row, kind: "http-error", reason: `player HTTP ${http}` };
    if (row.status !== "OK") return { ...row, kind: "not-ok", reason: `${row.status}${j?.playabilityStatus?.reason ? ": " + j.playabilityStatus.reason : ""}` };
    const fmt = findFormat(j);
    if (!fmt) return { ...row, kind: "no-format", reason: "no original audio format" };
    row.itag = fmt.itag;
    if (!fmt.url && !fmt.signatureCipher) {
      const sabr = j?.streamingData?.serverAbrStreamingUrl ? "SABR-only" : "no url/signatureCipher";
      return { ...row, kind: "sabr-only", reason: `${sabr} (app can't consume)` };
    }
    let url = fmt.url || ctx.cipher.deobfuscateStreamUrl(fmt.signatureCipher);
    if (needsN) { url = ctx.cipher.transformNParamInUrl(url); if (c.useWebPoTokens) url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(potVideo)}`; } // videoId-bound stream pot
    const clen = fmt.contentLength ? Number(fmt.contentLength) : null;
    const durMs = fmt.approxDurationMs ? Number(fmt.approxDurationMs) : null;
    const d = await drainWhole(url, c.userAgent, clen ? clen + 1 : 50 * 1048576);
    row.clen = clen; row.read = d.read;
    row.secs = clen && durMs ? Math.round((d.read / clen) * (durMs / 1000)) : null;
    if (d.status === "ERR") return { ...row, kind: "error", reason: d.error || "fetch error" };
    const whole = clen && d.read >= clen;
    return whole
      ? { ...row, kind: "whole", reason: "" }
      : { ...row, kind: "partial", reason: `${d.status} after ${kb(d.read)}` };
  } catch (e) {
    return { ...row, kind: "error", reason: String(e.message).slice(0, 80) };
  }
}

/** The CLI table row for a verdict (the human-facing view the monitor's logs also print). */
export function formatRow(r) {
  const result = r.kind === "whole" ? "✓ WHOLE SONG"
    : r.kind === "skipped-login" ? "skipped (login required, no cookie)"
    : r.kind === "no-format" ? `no format${r.reason ? " (" + r.reason + ")" : ""}`
    : `✗ ${r.reason}`;
  return [
    r.key.padEnd(20), String(r.http ?? "ERR").padEnd(5), String(r.status).padEnd(5), String(r.itag ?? "-").padEnd(5),
    String(r.clen ?? "-").padEnd(9), (r.read ? kb(r.read) : "-").padEnd(12), (r.secs != null ? r.secs + "s" : "-").padEnd(7), result,
  ].join(" ");
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) (async () => {
  const ctx = await createDrainContext();
  console.log(describeCred(ctx.cred));
  console.log(`video=${CLI_VIDEO_ID}\n`);
  const video = await ctx.forVideo(CLI_VIDEO_ID);
  console.log("client".padEnd(20), "http".padEnd(5), "play".padEnd(5), "itag".padEnd(5), "clen".padEnd(9), "delivered".padEnd(12), "secs".padEnd(7), "result");
  for (const key of TEST) {
    const c = CLIENTS.find((x) => x.key === key);
    if (!c) continue;
    console.log(formatRow(await drainClient(ctx, c, video)));
  }
  ctx.close();
  process.exit(0);
})();
