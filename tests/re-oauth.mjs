// RE: can ANDROID_CREATOR / ANDROID be fixed with a real YouTube OAuth Bearer token?
// Android clients reject the web cookie+SAPISIDHASH (HTTP 400); they want `Authorization: Bearer
// ya29...`. This mints one via the TV-app OAuth device flow (yt-dlp's method), then tests the
// Android clients with it — playability + whether the stream URL is spc-gated.
//
// Two phases (the device flow needs YOU to authorize in a browser between them):
//   node tests/re-oauth.mjs start   -> prints a code + URL; go authorize with the cookie's account
//   node tests/re-oauth.mjs poll    -> exchanges for the Bearer token, then tests ANDROID clients
//
// The device code + access token are written to tests/_oauth_device.json (gitignored, transient).

import fs from "node:fs";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";

const MIB = 1048576;
const VID = process.argv[3] || "JTF9fLJvniI";
const STORE = new URL("./_oauth_device.json", import.meta.url);
// YouTube TV-app OAuth client (public, used by yt-dlp/NewPipe for device-flow login).
const CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
const CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
const SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube";

async function start() {
  const r = await fetch("https://oauth2.googleapis.com/device/code", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: CLIENT_ID, scope: SCOPE }),
  });
  const j = await r.json();
  if (!j.device_code) { console.log("device/code failed:", JSON.stringify(j)); return; }
  fs.writeFileSync(STORE, JSON.stringify({ device_code: j.device_code, interval: j.interval || 5 }));
  console.log("\n==================== AUTHORIZE ====================");
  console.log(`  1. open:  ${j.verification_url}`);
  console.log(`  2. enter: ${j.user_code}`);
  console.log(`  3. sign in with the SAME account as the cookie, allow.`);
  console.log(`  (expires in ${Math.round((j.expires_in || 1800) / 60)} min)`);
  console.log("then run:  node tests/re-oauth.mjs poll");
  console.log("==================================================\n");
}

async function getToken() {
  const store = JSON.parse(fs.readFileSync(STORE));
  if (store.access_token) return store; // already obtained — reuse without re-polling
  const { device_code, interval } = store;
  for (let i = 0; i < 40; i++) {
    const r = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ client_id: CLIENT_ID, client_secret: CLIENT_SECRET, device_code, grant_type: "urn:ietf:params:oauth:grant-type:device_code" }),
    });
    const j = await r.json();
    if (j.access_token) { fs.writeFileSync(STORE, JSON.stringify({ ...store, ...j })); return j; }
    if (j.error && j.error !== "authorization_pending" && j.error !== "slow_down") throw new Error(JSON.stringify(j));
    await new Promise((res) => setTimeout(res, (interval || 5) * 1000));
  }
  throw new Error("timed out waiting for authorization");
}

// OAuth player request: hit the youtubei endpoint (configurable), Bearer auth, NO X-Origin / NO
// cookie / NO mismatched visitorData. yt-dlp's android-OAuth shape.
async function oauthPlayer(c, bearer, { endpoint, visitorData } = {}) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true };
  const headers = { "Content-Type": "application/json", "X-Goog-Api-Format-Version": "2", "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion, "User-Agent": c.userAgent, Authorization: `Bearer ${bearer}` };
  if (visitorData) headers["X-Goog-Visitor-Id"] = visitorData;
  const r = await fetch(endpoint, { method: "POST", headers, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await r.text()); } catch {}
  return { http: r.status, j };
}
const audio = (j) => (j.streamingData?.adaptiveFormats || []).filter((f) => f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
async function range(u, ua, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": ua, Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }

async function poll() {
  const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  console.log("waiting for authorization…");
  const tok = await getToken();
  console.log(`got Bearer token (${tok.access_token.slice(0, 10)}…, expires_in=${tok.expires_in}s)\n`);
  const endpoints = {
    "youtubei.googleapis": "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
    "www.youtube": "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
    "music.youtube": PLAYER_URL,
  };
  for (const key of ["ANDROID_CREATOR", "ANDROID"]) {
    const c = CLIENTS.find((x) => x.key === key);
    if (!c) continue;
    console.log(`── ${key} (OAuth Bearer) ──`);
    for (const [en, ep] of Object.entries(endpoints)) {
      for (const [vn, vv] of [["no-vd", null], ["cookie-vd", vd]]) {
        const { http, j } = await oauthPlayer(c, tok.access_token, { endpoint: ep, visitorData: vv });
        const ps = j?.playabilityStatus || {};
        const f = audio(j);
        let info;
        if (f?.url) {
          const spc = (new URL(f.url).searchParams.get("sparams") || "").includes("spc") ? "spc" : "NO-spc";
          const at1 = await range(f.url, c.userAgent, MIB, MIB + 262143);
          info = `${spc} @1MiB=${at1}${spc === "NO-spc" && (at1 === 206 || at1 === 200) ? "  <<< FULL via OAuth!" : ""}`;
        } else info = f?.signatureCipher ? "ciphered" : "no-url";
        console.log(`  ${en.padEnd(20)} ${vn.padEnd(10)} http=${http} ${(ps.status || "-").padEnd(14)} ${ps.reason ? `(${ps.reason}) ` : ""}${info}`);
      }
    }
  }
  process.exit(0);
}

const cmd = process.argv[2];
if (cmd === "start") await start();
else if (cmd === "poll") await poll();
else console.log("usage: node tests/re-oauth.mjs start|poll");
