// Regression test for STS/cipher player coherence (the A/B-rollout 403).
//
// The signatureTimestamp sent in /player decides which player generation YouTube mints the
// signatureCipher for. The app deciphers with the player its OWN PlayerJsFetcher loaded, so
// the request STS must come from that same player (CipherDeobfuscator.signatureTimestamp(),
// used by YTPlayerUtils.getSignatureTimestampOrNull) — NOT from an independently
// fetched player, which can be a different generation mid-rollout.
//
// Scenarios (both decipher with the PINNED player, mirroring the app):
//   matched    — /player sent the pinned player's own STS  → must stream past the 1-MiB wall.
//   mismatched — /player sent ANOTHER live player's STS    → expected to 403 (this half is
//                informational: it only demonstrates the bug while two generations are live).
//
//   node tests/sts-mismatch.mjs [videoId]
//   PLAYER_HASH=<ours> OTHER_PLAYER_HASH=<theirs> node tests/sts-mismatch.mjs
//
// Observed 2026-06-09 (the bug this guards): device sent NewPipe sts=20611 (69e2a55d) while
// the cipher WebView loaded ce74690f (sts 20612) → wrong sig/n → CDN 403 → fallback client.

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VIDEO_ID = process.argv[2] || process.env.VIDEO_ID || "JTF9fLJvniI";
const PINNED = process.env.PLAYER_HASH || "ce74690f";
const OTHER = process.env.OTHER_PLAYER_HASH || "69e2a55d";
const WALL = 1.5 * 1048576; // past the 1-MiB free window
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;

async function fetchSts(hash) {
  const url = `https://www.youtube.com/s/player/${hash}/player_ias.vflset/en_GB/base.js`;
  const js = await (await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } })).text();
  const m = js.match(/signatureTimestamp['":\s]+(\d+)/);
  if (!m) throw new Error(`no signatureTimestamp in base.js for ${hash}`);
  return Number(m[1]);
}

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}

async function playerRequest(c, sts, { visitorData, dataSyncId, cookie, poToken }) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  const body = {
    context: { client, ...(dataSyncId ? { user: { onBehalfOfUser: dataSyncId } } : {}) },
    videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true,
    playbackContext: { contentPlaybackContext: { signatureTimestamp: sts } },
    serviceIntegrityDimensions: { poToken },
  };
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent,
    "X-Goog-Visitor-Id": visitorData, cookie,
  };
  const a = sapisidHash(cookie); if (a) h.Authorization = a;
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  return JSON.parse(await res.text());
}

const bestAudio = (j) => (j?.streamingData?.adaptiveFormats || [])
  .filter((f) => f.width == null && (!f.audioTrack || f.audioTrack.isAutoDubbed == null))
  .sort((a, b) => b.bitrate - a.bitrate)[0] || null;

async function drainPastWall(url, ua) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0 };
    let read = 0; const rd = r.body.getReader();
    for (;;) { const { done, value } = await rd.read(); if (done) break; read += value.length; if (read >= WALL) { await rd.cancel(); break; } }
    return { status: r.status, read };
  } catch (e) { return { status: "ERR", error: e.message, read: 0 }; }
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));

  process.env.PLAYER_HASH = PINNED; // decipher with OUR player, like the app's CipherWebView
  const cipher = await createCipher({});
  const otherSts = await fetchSts(OTHER);
  console.log(`video=${VIDEO_ID}  cipher player=${PINNED} (sts=${cipher.sts})  other player=${OTHER} (sts=${otherSts})\n`);

  const minter = await createMinter(visitorData);
  const potVideo = await minter.mint(VIDEO_ID);
  const potVisitor = await minter.mint(visitorData);
  const remix = CLIENTS.find((c) => c.key === "WEB_REMIX");

  async function scenario(label, sts) {
    const j = await playerRequest(remix, sts, { visitorData, dataSyncId: cred.dataSyncId, cookie: cred.cookie, poToken: potVisitor });
    const ps = j?.playabilityStatus?.status;
    const fmt = bestAudio(j);
    if (ps !== "OK" || !fmt) { console.log(`${label}: play=${ps} no format`); return { ok: false, ciphered: false }; }
    const ciphered = !!fmt.signatureCipher;
    let url = fmt.url || cipher.deobfuscateStreamUrl(fmt.signatureCipher);
    url = cipher.transformNParamInUrl(url);
    url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(potVideo)}`;
    const d = await drainPastWall(url, remix.userAgent);
    const ok = (d.status === 206 || d.status === 200) && d.read >= Math.min(WALL, Number(fmt.contentLength) || WALL);
    console.log(`${label}: sts=${sts} ciphered=${ciphered} GET=${d.status} delivered=${kb(d.read)} → ${ok ? "✓ streams past wall" : "✗ blocked"}`);
    return { ok, ciphered };
  }

  const matched = await scenario("matched   ", cipher.sts);
  let mismatchNote = "skipped (no second live player generation)";
  if (otherSts !== cipher.sts) {
    const mismatched = await scenario("mismatched", otherSts);
    mismatchNote = mismatched.ok
      ? "WARNING: mismatched STS streamed fine — CDN may have stopped enforcing sig/sts binding"
      : "confirmed: mismatched STS is blocked (the bug the app fix guards against)";
  }

  console.log(`\nmismatch half: ${mismatchNote}`);
  if (!matched.ok) { console.log("FAIL: matched STS must stream past the 1-MiB wall"); process.exit(1); }
  if (!matched.ciphered) console.log("note: matched response had a direct url — sig path not exercised this run, n+pot were");
  console.log("PASS");
  cipher._close?.();
  process.exit(0);
})();
