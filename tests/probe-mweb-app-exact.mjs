// MWEB 403 bisect: reproduce the APP's exact MWEB path in the terminal, then flip one
// dimension at a time toward the known-good anonymous probe until the cause isolates.
//
// Background (tests/MWEB-INVESTIGATION.md): the anonymous terminal probe gets HEAD 200,
// the app gets 403 on validateStatus. The old probes never replicated the app's /player
// request (visitorData + cookie/SAPISIDHASH + request poToken + X-Goog headers), the app's
// format pick (max-bitrate webm-biased -> 251 vs first-audio -> 140), the app's pot= append
// (videoId-bound), or the app's validation HEAD (Firefox desktop UA + account cookie).
// This probe does, with each difference as an independent toggle.
//
//   node tests/probe-mweb-app-exact.mjs [videoId]
//
// Request-side toggles (fresh /player + decipher per variant):
//   visitor    X-Goog-Visitor-Id header + context.client.visitorData
//   auth       cookie + SAPISIDHASH Authorization
//   reqPot     serviceIntegrityDimensions.poToken = sessionPot (visitorData-bound, app binding)
//   xHeaders   X-Goog-Api-Format-Version / X-YouTube-Client-Name/-Version / X-Origin / Referer
//   appUrl     ?prettyPrint=false (app) vs ?key=AIza... (old probes)
//   fullBody   context.request{internalExperimentFlags,useSsl} + user{lockedSafetyMode}
//              + playlistId:null + contentCheckOk/racyCheckOk (encodeDefaults=true shape)
// URL-side: format 251(app)/140(probe), pot none/videoId-bound.
// Validation-side (HEADs on the same URL): UA web/mweb x cookie on/off.

import crypto from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const ORIGIN = "https://music.youtube.com";
const APP_PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const KEY_PLAYER_URL = ORIGIN + "/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
const MWEB = { clientName: "MWEB", clientVersion: "2.20260213.00.00", clientId: "2" };
const MWEB_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}

function buildRequest(t, { videoId, visitorData, cookie, sts, sessionPot }) {
  const headers = { "Content-Type": "application/json", "User-Agent": MWEB_UA };
  if (t.xHeaders) {
    headers["X-Goog-Api-Format-Version"] = "1";
    headers["X-YouTube-Client-Name"] = MWEB.clientId;
    headers["X-YouTube-Client-Version"] = MWEB.clientVersion;
    headers["X-Origin"] = ORIGIN;
    headers["Referer"] = ORIGIN + "/";
  } else {
    headers["Origin"] = ORIGIN;
  }
  if (t.visitor) headers["X-Goog-Visitor-Id"] = visitorData;
  if (t.auth) {
    headers.cookie = cookie;
    const a = sapisidHash(cookie);
    if (a) headers.Authorization = a;
  }
  const client = { clientName: MWEB.clientName, clientVersion: MWEB.clientVersion, gl: "US", hl: "en" };
  if (t.visitor) client.visitorData = visitorData;
  const body = { context: { client }, videoId };
  if (t.fullBody) {
    body.context.request = { internalExperimentFlags: [], useSsl: true };
    body.context.user = { lockedSafetyMode: false };
    body.contentCheckOk = true;
    body.racyCheckOk = true;
  }
  body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (t.reqPot) body.serviceIntegrityDimensions = { poToken: sessionPot };
  return { url: t.appUrl ? APP_PLAYER_URL : KEY_PLAYER_URL, headers, body };
}

const isAudio = (f) => (f.mimeType || "").startsWith("audio/");
const isOriginal = (f) => !f.audioTrack || f.audioTrack.audioIsDefault !== false;
function appPick(fmts) {
  return fmts.filter((f) => isAudio(f) && isOriginal(f)).sort((a, b) =>
    (b.bitrate + ((b.mimeType || "").startsWith("audio/webm") ? 10240 : 0)) -
    (a.bitrate + ((a.mimeType || "").startsWith("audio/webm") ? 10240 : 0)))[0] || null;
}
const firstPick = (fmts) => fmts.find(isAudio) || null;

async function playerCall(req) {
  const res = await fetch(req.url, { method: "POST", headers: req.headers, body: JSON.stringify(req.body) });
  const text = await res.text();
  let j = {}; try { j = JSON.parse(text); } catch {}
  return { http: res.status, j };
}

// /player with up to 3 retries when the wanted format has neither url nor signatureCipher
// (MWEB responses are intermittently sabr-only -- count it as flakiness data).
async function playerWithCipher(req, pick) {
  let sabrOnly = 0;
  for (let attempt = 1; attempt <= 3; attempt++) {
    const { http, j } = await playerCall(req);
    const status = j?.playabilityStatus?.status;
    if (status !== "OK") return { http, status, fmt: null, sabrOnly, j };
    const fmt = pick(j?.streamingData?.adaptiveFormats || []);
    if (fmt && (fmt.url || fmt.signatureCipher)) return { http, status, fmt, sabrOnly, j };
    sabrOnly++;
    await sleep(700);
  }
  return { http: 0, status: "NO_CIPHER_3x", fmt: null, sabrOnly, j: null };
}

async function head(url, { ua, cookie }) {
  const headers = { "User-Agent": ua };
  if (cookie) headers.Cookie = cookie;
  try {
    const r = await fetch(url, { method: "HEAD", headers });
    return r.status;
  } catch (e) { return "ERR:" + e.message.slice(0, 40); }
}

function describeUrl(u) {
  try {
    const q = new URL(u).searchParams;
    return `c=${q.get("c")} itag=${q.get("itag")} clen=${q.get("clen")} sig=${(q.get("sig") || "").length} n=${(q.get("n") || "").length} pot=${q.has("pot")}`;
  } catch { return "?"; }
}

async function main() {
  const cred = await getCred();
  console.log(describeCred(cred));
  const visitorData = dec(cred.visitorData);
  const cookie = cred.cookie;
  if (!cookie || !visitorData) { console.error("need cookie + visitorData"); process.exit(1); }

  console.log(`video=${VIDEO_ID}`);
  const cipher = await createCipher({ verbose: true });
  console.log(`cipher ready: hash=${cipher.hash} sts=${cipher.sts} sig=${cipher.sigAvailable} n=${cipher.nAvailable}`);

  console.log("minting poTokens (sessionPot<-visitorData, videoPot<-videoId, app bindings)...");
  const minter = await createMinter(visitorData);
  const sessionPot = await minter.mint(visitorData);  // app: playerRequestPoToken
  const videoPot = await minter.mint(VIDEO_ID);       // app: streamingDataPoToken (-> &pot=)
  console.log(`sessionPot len=${sessionPot.length} videoPot len=${videoPot.length}`);

  const ctx = { videoId: VIDEO_ID, visitorData, cookie, sts: cipher.sts, sessionPot };

  // Request variants. APP = everything on; ANON = old probe shape (everything off).
  const APP = { visitor: true, auth: true, reqPot: true, xHeaders: true, appUrl: true, fullBody: true };
  const ANON = { visitor: false, auth: false, reqPot: false, xHeaders: false, appUrl: false, fullBody: false };
  const variants = [
    ["ANON(old-probe)", ANON],
    ["APP(exact)", APP],
    ["APP-auth", { ...APP, auth: false }],
    ["APP-visitor", { ...APP, visitor: false }],
    ["APP-reqPot", { ...APP, reqPot: false }],
    ["APP-xHeaders", { ...APP, xHeaders: false }],
    ["APP-fullBody", { ...APP, fullBody: false }],
    ["APP-appUrl", { ...APP, appUrl: false }],
    ["ANON+visitor", { ...ANON, visitor: true }],
    ["ANON+auth", { ...ANON, auth: true }],
    ["ANON+reqPot", { ...ANON, reqPot: true }],
  ];

  // Validation variants (HEAD), app's validateStatus = webUA+cookie.
  const validations = [
    ["webUA+cookie(app)", { ua: WEB_UA, cookie }],
    ["mwebUA-nocookie(probe)", { ua: MWEB_UA, cookie: null }],
    ["webUA-nocookie", { ua: WEB_UA, cookie: null }],
    ["mwebUA+cookie", { ua: MWEB_UA, cookie }],
  ];

  console.log("\nrequest-variant x format x urlPot -> HEAD status per validation");
  console.log("variant".padEnd(16) + "fmt".padEnd(6) + "urlPot".padEnd(9) + validations.map(([n]) => n.padEnd(24)).join(""));

  for (const [name, t] of variants) {
    for (const [fmtName, pick] of [["app", appPick], ["140", firstPick]]) {
      const req = buildRequest(t, ctx);
      const r = await playerWithCipher(req, pick);
      if (!r.fmt) {
        console.log(`${name.padEnd(16)}${fmtName.padEnd(6)} -- ${r.status} (http=${r.http}, sabrOnly=${r.sabrOnly})`);
        continue;
      }
      let base;
      try {
        base = r.fmt.url || cipher.deobfuscateStreamUrl(r.fmt.signatureCipher);
        base = cipher.transformNParamInUrl(base);
      } catch (e) {
        console.log(`${name.padEnd(16)}${fmtName.padEnd(6)} -- decipher failed: ${e.message}`);
        continue;
      }
      for (const [potName, potVal] of [["none", null], ["videoId", videoPot]]) {
        const url = potVal ? `${base}${base.includes("?") ? "&" : "?"}pot=${encodeURIComponent(potVal)}` : base;
        const cells = [];
        for (const [, v] of validations) cells.push(String(await head(url, v)).padEnd(24));
        console.log(`${name.padEnd(16)}${fmtName.padEnd(6)}${potName.padEnd(9)}${cells.join("")} ${r.sabrOnly ? `(sabrOnly=${r.sabrOnly})` : ""}`);
        if (potName === "none") console.log(`  url: ${describeUrl(url)}`);
      }
      await sleep(300);
    }
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
