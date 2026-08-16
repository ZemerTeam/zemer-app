// Does YouTube's stats ingestion ACCEPT a DEFERRED session — the offline→reconnect replay?
//
// A deferred replay fires, all at once on reconnect: a fresh WEB_REMIX /player (fresh ei/expire/cpn),
// then the playback ping (cmt=0, final=0) and ONE watchtime ping reporting a COMPLETED past listen
// (st=0, et=<watched>, cmt=<watched>, rt=<watched>, final=1). If the ingestion 204s that, the deferred
// flow is accepted at the wire (the crediting confirmation is Studio-only, owner-side).
//
// Compares three shapes against the SAME fresh /player so a difference is the shape, not the video:
//   A LIVE-paced   : playback, then watchtime with real sleeps (the control — proven to credit)
//   B DEFERRED      : playback + one final watchtime, fired back-to-back, no sleeps (the replay)
//   C WATCHTIME-ONLY: only the final watchtime (no playback ping) — a lower bound
//
//   node tests/watchtime-deferred-accept.mjs [videoId]

import crypto from "node:crypto";
import { ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const WATCHED = Number(process.env.WATCHED || 90); // seconds "played offline"
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const randCpn = () => Array.from({ length: 16 }, () =>
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Math.floor(Math.random() * 64)]).join("");

const cred = await getCred();
const cm = Object.fromEntries(cred.cookie.split(";").map((s) => s.trim()).filter(Boolean).map((kv) => { const i = kv.indexOf("="); return [kv.slice(0, i), kv.slice(i + 1)]; }));
const SAP = cm["SAPISID"] || cm["__Secure-3PAPISID"];
const A = (o) => { const t = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${t}_${crypto.createHash("sha1").update(`${t} ${SAP} ${o}`).digest("hex")}`; };
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const mh = () => ({ "content-type": "application/json", "X-YouTube-Client-Name": "67", "X-YouTube-Client-Version": "1.20260213.01.00", "X-Origin": ORIGIN, Referer: ORIGIN + "/", cookie: cred.cookie, Authorization: A(ORIGIN), "User-Agent": UA, "X-Goog-Visitor-Id": dec(cred.visitorData) });
const tm = (u) => u.replace("https://s.youtube.com", "https://music.youtube.com");

async function player() {
  const body = { context: { client: { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", gl: "US", hl: "en", visitorData: dec(cred.visitorData) } }, videoId: VIDEO_ID };
  const r = await fetch(PLAYER_URL, { method: "POST", headers: mh(), body: JSON.stringify(body) });
  const j = await r.json();
  return { http: r.status, pt: j?.playbackTracking || {}, viewCount: Number(j?.videoDetails?.viewCount) || -1 };
}
async function get(url) { try { const r = await fetch(url, { headers: mh() }); r.body?.cancel?.(); return r.status; } catch (e) { return `ERR:${e.message}`; } }
function u(base, params) { const x = new URL(tm(base)); for (const [k, v] of Object.entries(params)) x.searchParams.set(k, String(v)); return x.toString(); }

async function playbackPing(pt, cpn) { return get(u(pt.videostatsPlaybackUrl.baseUrl, { ver: 2, c: "WEB_REMIX", cpn, cmt: 0, final: 0 })); }
async function watchPing(pt, cpn, { st = 0, et, cmt, rt, final }) { return get(u(pt.videostatsWatchtimeUrl.baseUrl, { ver: 2, c: "WEB_REMIX", cpn, st, et, cmt, rt, final: final ? 1 : 0 })); }

const p = await player();
console.log(`/player http ${p.http}  viewCount ${p.viewCount}  hasPlayback ${!!p.videostatsPlaybackUrl?.baseUrl ?? !!p.pt.videostatsPlaybackUrl}  hasWatchtime ${!!p.pt.videostatsWatchtimeUrl?.baseUrl}`);
if (!p.pt.videostatsPlaybackUrl?.baseUrl || !p.pt.videostatsWatchtimeUrl?.baseUrl) { console.log("no tracking urls"); process.exit(1); }

// A: LIVE-paced control (the shape proven to credit) — playback then watchtime with real gaps.
const cpnA = randCpn(); const a = [];
a.push(await playbackPing(p.pt, cpnA));
for (const s of [10, 20, 30]) { await sleep((s - (a.length > 1 ? [10, 20][a.length - 2] : 0)) * 100); a.push(await watchPing(p.pt, cpnA, { et: s, cmt: s, rt: s, final: s === 30 })); }
console.log(`A LIVE-paced    playback+watchtime : ${a.join(",")}`);

// B: DEFERRED replay — fresh cpn, playback + ONE final watchtime for a completed WATCHED-sec listen, no sleeps.
const cpnB = randCpn();
const b1 = await playbackPing(p.pt, cpnB);
const b2 = await watchPing(p.pt, cpnB, { et: WATCHED, cmt: WATCHED, rt: WATCHED, final: true });
console.log(`B DEFERRED      playback+final wt  : ${b1},${b2}  (reports ${WATCHED}s watched offline)`);

// C: WATCHTIME-ONLY deferred — no playback ping (lower bound).
const cpnC = randCpn();
const c1 = await watchPing(p.pt, cpnC, { et: WATCHED, cmt: WATCHED, rt: WATCHED, final: true });
console.log(`C WATCHTIME-only final             : ${c1}`);

const ok = (x) => x === 204 || x === 200;
const bOk = ok(b1) && ok(b2);
console.log(`\nDEFERRED accepted at ingestion: ${bOk ? "YES (both 204)" : "NO"}  |  live-control accepted: ${a.every(ok) ? "yes" : "no"}`);
console.log("NOTE: 204 == accepted for ingestion. Whether it CREDITS Studio watch time is owner-only and must be confirmed on a channel you control.");
process.exit(bOk ? 0 : 1);
