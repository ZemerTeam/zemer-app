// Bot-gate probe: from THIS network, which request properties make an ANONYMOUS client's /player
// return "Sign in to confirm you're not a bot"? GitHub-hosted runners get it on every login-less
// client (VISIONOS, TVHTML5_SIMPLY) while the app on a phone does not. This isolates the variable:
//
//   A  app-exact: the session's visitorData, no cookie, no pot        (what the scan sends today)
//   B  no visitorData at all
//   C  a FRESH visitorData minted by this runner (a first /player without one returns one)
//   D  A + the session-bound web poToken in serviceIntegrityDimensions (NOT app-exact for direct clients)
//   E  A + the cookie / SAPISIDHASH                                    (NOT app-exact: the app never sends it)
//
//   node tests/probe-bot-gate.mjs [videoId]      env: YT_COOKIE / YT_VISITOR_DATA, optional SCAN_PROXY
// stdout: one line per client x variant: OK / bot-gated / <status>; then a JSON summary.

import "./egress.mjs";
import crypto from "node:crypto";
import { getCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { loadStreamClients, needsWebTransforms } from "./stream-clients.mjs";
import { createCipher } from "./cipher.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const ORIGIN = "https://music.youtube.com";
const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const sapisidHash = (cookie) => { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const ts = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`; };

async function player(c, { visitorData, cookie, poToken, sts }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  // App-exact for WEB clients (TVHTML5_SIMPLY): sts + the session web pot in the body.
  if (needsWebTransforms(c) && sts) body.playbackContext = { contentPlaybackContext: { signatureTimestamp: Number(sts) } };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const h = { "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1", "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent };
  if (visitorData) h["X-Goog-Visitor-Id"] = visitorData;
  if (cookie) { h.cookie = cookie; const a = sapisidHash(cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await res.text()); } catch {}
  const st = j?.playabilityStatus?.status || `HTTP${res.status}`;
  const reason = j?.playabilityStatus?.reason || "";
  const gated = /confirm you.re not a bot/i.test(reason);
  const usable = st === "OK" && (j?.streamingData?.adaptiveFormats || []).some((f) => f.url || f.signatureCipher);
  const direct = (j?.streamingData?.adaptiveFormats || []).filter((f) => f.url && f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
  return { st, gated, usable, directUrl: direct?.url || null, freshVisitor: j?.responseContext?.visitorData || null };
}

const cred = await getCred();
const visitorData = dec(cred.visitorData);
const { clients } = loadStreamClients();
const anon = clients.filter((c) => !c.loginSupported);

// QUICK=1: one app-exact /player for the first anonymous DIRECT client (no cipher, no pot needed).
// Exit 0 = this network is NOT bot-gated for anonymous clients, 1 = gated, 2 = other failure.
// The monitor's egress step uses it to verify (and re-roll) the egress before the drains.
if (process.env.QUICK) {
  const c = anon.find((x) => !needsWebTransforms(x)) || anon[0];
  if (!c) { console.log("QUICK: no anonymous client in the table"); process.exit(2); }
  try {
    const r = await player(c, { visitorData });
    if (r.gated || !r.usable) { console.log(`QUICK ${c.key}: ${r.gated ? "BOT-GATED" : r.st}`); process.exit(r.gated ? 1 : 2); }
    // The wall can be on the CDN, not on /player ("403 after 0KB" on every anonymous client from a
    // bad egress): drain the first 256 KiB of the direct url as the app would. A direct (no-cipher)
    // client keeps this dependency-free; a clean egress gets 200/206 with bytes.
    const url = r.directUrl;
    if (!url) { console.log(`QUICK ${c.key}: OK (consumable) but no direct url to drain`); process.exit(2); }
    const g = await fetch(url, { headers: { "User-Agent": c.userAgent, Range: "bytes=0-262143" } });
    let got = 0; if (g.status === 200 || g.status === 206) { const rd = g.body.getReader(); for (;;) { const { done, value } = await rd.read(); if (done) break; got += value.length; if (got >= 262144) { await rd.cancel(); break; } } }
    const cdnOk = (g.status === 200 || g.status === 206) && got > 0;
    console.log(`QUICK ${c.key}: /player OK, CDN ${g.status} ${Math.round(got / 1024)}KB -> ${cdnOk ? "OK" : "WALLED"}`);
    process.exit(cdnOk ? 0 : 1);
  } catch (e) { console.log(`QUICK ${c.key}: ERR ${e.message}`); process.exit(2); }
}
const minter = await createMinter(visitorData);
const sessionPot = await minter.mint(visitorData);
const cipher = await createCipher({});
const sts = cipher.sts;
let fresh = null;
try { fresh = (await player(anon[0], {})).freshVisitor; } catch {}
const variantsFor = (c) => {
  const web = needsWebTransforms(c);   // web clients get sts + session pot in EVERY variant (app-exact)
  const base = web ? { sts, poToken: sessionPot } : {};
  return {
    "A app-exact (session visitorData)": { ...base, visitorData },
    "B no visitorData": { ...base },
    "C fresh visitorData from this network": fresh ? { ...base, visitorData: fresh } : null,
    "D + session poToken (not app-exact for direct)": web ? null : { ...base, visitorData, poToken: sessionPot },
    "E + cookie (not app-exact)": cred.cookie ? { ...base, visitorData, cookie: cred.cookie } : null,
  };
};
const summary = {};
for (const c of anon) {
  summary[c.key] = {};
  for (const [name, opts] of Object.entries(variantsFor(c))) {
    if (!opts) { console.log(`${c.key.padEnd(16)} ${name.padEnd(46)} (skipped)`); continue; }
    try {
      const r = await player(c, opts);
      const verdict = r.gated ? "BOT-GATED" : r.usable ? "OK (consumable)" : r.st;
      summary[c.key][name] = verdict;
      console.log(`${c.key.padEnd(16)} ${name.padEnd(46)} ${verdict}`);
    } catch (e) { console.log(`${c.key.padEnd(16)} ${name.padEnd(46)} ERR ${e.message.slice(0, 40)}`); }
  }
}
console.log("\nSUMMARY " + JSON.stringify({ video: VIDEO_ID, freshVisitor: Boolean(fresh), anonymousClients: anon.map((c) => c.key), results: summary }));
cipher._close?.();
process.exit(0);
