// Probe the YouTube Music /player endpoint with every client to discover,
// per client, what must be sent for it to return a STREAMABLE audio URL.
//
//   node tests/run.mjs
//
// Env:
//   CRED_URL          credential worker (default https://mc.alltech.dev/credentials)
//   YT_COOKIE         override cookie instead of fetching from CRED_URL
//   YT_VISITOR_DATA   override visitorData
//   VIDEO_IDS         comma-separated video ids (default two well-known songs)
//
// For each client it sends a player request (varying auth/STS), classifies the
// best audio format (direct url / ciphered / none), and — for direct urls —
// does a Range GET to confirm the stream actually serves bytes (206/200).
// poToken is NOT generated here (needs BotGuard); web clients are expected to
// come back ciphered / require pot=, which the report makes explicit.

import crypto from "node:crypto";
import fs from "node:fs";
import { CLIENTS, USER_AGENT_WEB, ORIGIN, PLAYER_URL } from "./clients.mjs";

const CRED_URL = process.env.CRED_URL || "https://mc.alltech.dev/credentials";
const VIDEO_IDS = (process.env.VIDEO_IDS || "dQw4w9WgXcQ,kJQP7kiw5Fk").split(",");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getCred() {
  if (process.env.YT_COOKIE) {
    return { cookie: process.env.YT_COOKIE, visitorData: process.env.YT_VISITOR_DATA || "", dataSyncId: "" };
  }
  const j = await (await fetch(CRED_URL)).json();
  return { cookie: j.cookie || "", visitorData: j.visitorData || "", dataSyncId: j.dataSyncId || "" };
}

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  const hash = crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex");
  return `SAPISIDHASH ${ts}_${hash}`;
}

// Best-effort signatureTimestamp from the live base.js (needed by web clients
// so the returned URLs deobfuscate correctly).
async function fetchSts() {
  try {
    const ifr = await (await fetch(ORIGIN + "/iframe_api")).text();
    const ver = ifr.match(/\/player\/([0-9a-fA-F]{8})\//)?.[1];
    if (!ver) return null;
    const js = await (await fetch(`https://www.youtube.com/s/player/${ver}/player_ias.vflset/en_US/base.js`)).text();
    return js.match(/signatureTimestamp[:=](\d+)/)?.[1] || js.match(/[,{]sts[:=](\d+)/)?.[1] || null;
  } catch {
    return null;
  }
}

function buildBody(c, videoId, visitorData, { onBehalf, sts, poToken } = {}) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) {
    if (c[k]) client[k] = c[k];
  }
  const body = { context: { client }, videoId, contentCheckOk: true, racyCheckOk: true };
  if (onBehalf) body.context.user = { onBehalfOfUser: onBehalf };
  if (c.useSignatureTimestamp && sts) {
    body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  }
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  return body;
}

function buildHeaders(c, visitorData, { cookie, auth } = {}) {
  const h = {
    "Content-Type": "application/json",
    "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId,
    "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN,
    Referer: ORIGIN + "/",
    "User-Agent": c.userAgent,
  };
  if (visitorData) h["X-Goog-Visitor-Id"] = visitorData;
  if (auth && cookie) {
    h.cookie = cookie;
    const a = sapisidHash(cookie);
    if (a) h.Authorization = a;
  }
  return h;
}

async function player(c, videoId, visitorData, opts = {}) {
  const res = await fetch(PLAYER_URL, {
    method: "POST",
    headers: buildHeaders(c, visitorData, opts),
    body: JSON.stringify(buildBody(c, videoId, visitorData, opts)),
  });
  const text = await res.text();
  let j = {};
  try { j = JSON.parse(text); } catch {}
  return { http: res.status, j };
}

function bestAudio(j) {
  const fmts = j?.streamingData?.adaptiveFormats || [];
  return fmts.filter((f) => (f.mimeType || "").startsWith("audio")).sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0))[0] || null;
}

function classifyAudio(a) {
  if (!a) return "none";
  if (a.url) return "direct";
  if (a.signatureCipher) return "ciphered";
  return "no-url";
}

async function streamable(url, ua) {
  try {
    const r = await fetch(url, { headers: { Range: "bytes=0-1", "User-Agent": ua } });
    r.body?.cancel?.();
    return String(r.status);
  } catch (e) {
    return "ERR:" + e.message;
  }
}

const pad = (s, n) => String(s).padEnd(n);

(async () => {
  const cred = await getCred();
  const sts = await fetchSts();
  console.log(
    `cred: cookie=${cred.cookie ? "yes" : "NO"} | SAPISID=${/SAPISID=/.test(cred.cookie) ? "yes" : "NO"} | ` +
    `visitorData=${(cred.visitorData || "").slice(0, 14)}… | sts=${sts || "UNKNOWN"} | videos=${VIDEO_IDS.join(",")}`,
  );

  const results = [];
  for (const videoId of VIDEO_IDS) {
    console.log(`\n=== videoId ${videoId} ===`);
    console.log(pad("client", 30), pad("mode", 5), pad("http", 5), pad("playability", 13), pad("fmts", 5), pad("audio", 9), "stream / reason");
    for (const c of CLIENTS) {
      const modes = c.loginSupported ? ["anon", "auth"] : ["anon"];
      for (const mode of modes) {
        let res;
        try {
          res = await player(c, videoId, cred.visitorData, { cookie: cred.cookie, auth: mode === "auth", sts });
        } catch (e) {
          console.log(pad(c.key, 30), pad(mode, 5), "ERR  ", "", "", "", e.message);
          continue;
        }
        const a = bestAudio(res.j);
        const cls = classifyAudio(a);
        const ps = res.j?.playabilityStatus?.status || "-";
        const reason = res.j?.playabilityStatus?.reason || "";
        const nf = (res.j?.streamingData?.adaptiveFormats || []).length;
        let strm = cls === "direct" ? await streamable(a.url, c.userAgent) : "";
        const tail = cls === "direct" ? strm : (ps !== "OK" ? reason : cls);
        console.log(pad(c.key, 30), pad(mode, 5), pad(res.http, 5), pad(ps, 13), pad(nf, 5), pad(cls, 9), tail);
        results.push({ videoId, client: c.key, mode, http: res.http, playability: ps, reason, formats: nf, audio: cls, stream: strm });
        await sleep(150);
      }
    }
  }

  // Demonstrate the onBehalfOfUser / dataSyncId effect (the playback-breaking bug).
  if (cred.dataSyncId && /SAPISID=/.test(cred.cookie)) {
    console.log(`\n=== onBehalfOfUser effect (WEB_REMIX auth, dataSyncId=${cred.dataSyncId}) ===`);
    const c = CLIENTS.find((x) => x.key === "WEB_REMIX");
    for (const onBehalf of [null, cred.dataSyncId]) {
      const res = await player(c, VIDEO_IDS[0], cred.visitorData, { cookie: cred.cookie, auth: true, sts, onBehalf });
      console.log(`  onBehalfOfUser=${onBehalf ? "set" : "absent"} -> HTTP ${res.http}, playability=${res.j?.playabilityStatus?.status || "-"}`);
      await sleep(150);
    }
  }

  const streamers = results.filter((r) => r.audio === "direct" && (r.stream === "200" || r.stream === "206"));
  console.log(`\n=== STREAMABLE without poToken (playability OK + direct url + bytes served) ===`);
  for (const r of [...new Set(streamers.map((s) => `${s.client} [${s.mode}]`))]) console.log("  ✓ " + r);
  if (!streamers.length) console.log("  (none — every working client needs poToken or deobfuscation)");

  const outPath = new URL("./results.json", import.meta.url);
  fs.writeFileSync(outPath, JSON.stringify(results, null, 2));
  console.log(`\nFull matrix -> tests/results.json`);
})();
