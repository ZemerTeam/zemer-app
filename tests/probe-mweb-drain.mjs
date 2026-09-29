// Clean, low-volume, controlled drain test — the arbiter for "does MWEB stream a whole song?".
//
// Confounds removed from earlier probes:
//   - SHORT songs only (clen < 6 MiB; long-form mixes excluded).
//   - WEB_REMIX itag 251 as a CONTROL on the same pinned player + same video: it is known to
//     deliver the whole song, so if it drains here, the pinned player is sound and any MWEB
//     failure is real (client/format), not a pin artifact.
//   - ExoPlayer-faithful drive: sequential bounded CHUNK-sized ranges on FRESH connections
//     (Connection: close), walking 0 -> clen, with a pause between requests so we measure the
//     gate, not rate-limiting. Reports the first byte offset that 403s (the real free window).
//
//   PLAYER_HASH=69e2a55d node tests/probe-mweb-drain.mjs [searchQuery]

import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";
import crypto from "node:crypto";

const ORIGIN = "https://music.youtube.com";
const MWEB_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const MWEB = { clientName: "MWEB", clientVersion: "2.20260213.00.00", clientId: "2" };
const WEB_REMIX = { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: "67" };
const CHUNK = 262144;

const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function call(endpoint, client, body, cred) {
  const h = { "Content-Type": "application/json", "User-Agent": MWEB_UA, "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": client.clientId, "X-YouTube-Client-Version": client.clientVersion, "X-Origin": ORIGIN,
    Referer: ORIGIN + "/", "X-Goog-Visitor-Id": cred.visitorData, cookie: cred.cookie };
  const a = sapisidHash(cred.cookie); if (a) h.Authorization = a;
  const res = await fetch(`${ORIGIN}/youtubei/v1/${endpoint}?prettyPrint=false`, { method: "POST", headers: h,
    body: JSON.stringify({ context: { client: { clientName: client.clientName, clientVersion: client.clientVersion, gl: "US", hl: "en", visitorData: cred.visitorData } }, ...body }) });
  let j = {}; try { j = JSON.parse(await res.text()); } catch {}
  return j;
}
async function searchSongs(query, cred, max = 12) {
  const { } = {};
  const j = await call("search", WEB_REMIX, { query, params: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" }, cred);
  const text = JSON.stringify(j);
  return [...new Set([...text.matchAll(/"videoId":"([A-Za-z0-9_-]{11})"/g)].map((m) => m[1]))].slice(0, max);
}
async function playerFmts(client, videoId, sts, cred, reqPot) {
  const body = { videoId, playbackContext: { contentPlaybackContext: { signatureTimestamp: Number(sts) } } };
  if (reqPot) body.serviceIntegrityDimensions = { poToken: reqPot };
  const j = await call("player", client, body, cred);
  if (j?.playabilityStatus?.status !== "OK") return null;
  return (j?.streamingData?.adaptiveFormats || []).filter((f) => (f.mimeType || "").startsWith("audio/"));
}
function deobf(cipher, f) {
  let u = f.url || cipher.deobfuscateStreamUrl(f.signatureCipher);
  return cipher.transformNParamInUrl(u);
}
async function chunkGet(url, start, end, ua) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": ua, Range: `bytes=${start}-${end}`, Connection: "close" } });
    r.body?.cancel?.();
    return r.status;
  } catch { return "ERR"; }
}
// Walk bounded CHUNK ranges across the whole file on fresh connections; stop at first non-206/200.
// Returns { ok, firstFailAt, lastOk, chunks }.
async function sequentialDrain(url, clen, ua) {
  let offset = 0, lastOk = -1, chunks = 0;
  while (offset < clen) {
    const end = Math.min(offset + CHUNK - 1, clen - 1);
    const st = await chunkGet(url, offset, end, ua);
    chunks++;
    if (st !== 206 && st !== 200) return { ok: false, firstFailAt: offset, lastOk, chunks, failStatus: st };
    lastOk = end;
    offset = end + 1;
    await sleep(150); // measure the gate, not rate-limiting
  }
  return { ok: true, firstFailAt: null, lastOk, chunks };
}

async function main() {
  const cred0 = await getCred();
  console.log(describeCred(cred0));
  const cred = { visitorData: dec(cred0.visitorData), cookie: cred0.cookie };
  const cipher = await createCipher({ verbose: true });
  console.log(`cipher: ${cipher.hash} sts=${cipher.sts} (pinned=${process.env.PLAYER_HASH || "live"})`);
  const minter = await createMinter(cred.visitorData);
  const sessionPot = await minter.mint(cred.visitorData);

  const query = process.argv[2] || "pop songs";
  const candidates = await searchSongs(query, cred);

  // Pick the first 3 SHORT tracks (MWEB itag140 clen < 6 MiB) to keep volume + time low.
  const chosen = [];
  for (const v of candidates) {
    if (chosen.length >= 3) break;
    const fmts = await playerFmts(MWEB, v, cipher.sts, cred, sessionPot);
    if (!fmts) continue;
    const f140 = fmts.find((f) => f.itag === 140);
    const f251 = fmts.find((f) => f.itag === 251);
    if (!f140 || !f251 || (!f140.url && !f140.signatureCipher)) continue;
    let url140; try { url140 = deobf(cipher, f140); } catch { continue; }
    const clen = Number(new URL(url140).searchParams.get("clen")) || 0;
    if (clen === 0 || clen > 6 * 1024 * 1024) continue;
    chosen.push({ v, f140, f251 });
  }
  console.log(`chosen short tracks: ${chosen.map((c) => c.v).join(" ")}\n`);

  for (const { v, f140, f251 } of chosen) {
    console.log(`=== ${v} ===`);
    const videoPot = await minter.mint(v);

    // MWEB itag 140 (m4a) + 251 (webm), no pot
    for (const [label, f, ua] of [
      ["MWEB-140(m4a) none", f140, MWEB_UA],
      ["MWEB-251(webm) none", f251, MWEB_UA],
    ]) {
      let url; try { url = deobf(cipher, f); } catch { console.log(`  ${label}: decipher-fail`); continue; }
      const clen = Number(new URL(url).searchParams.get("clen")) || 0;
      const r = await sequentialDrain(url, clen, ua);
      console.log(`  ${label.padEnd(22)} clen=${(clen/1024/1024).toFixed(2)}MiB  ${r.ok ? `WHOLE SONG ✓ (${r.chunks} chunks)` : `FAIL at byte ${r.firstFailAt} (${(r.firstFailAt/1024/1024).toFixed(2)}MiB) status=${r.failStatus}, lastOk=${(r.lastOk/1024/1024).toFixed(2)}MiB`}`);
      await sleep(400);
    }
    // MWEB itag 140 WITH videoId pot
    {
      let url; try { url = deobf(cipher, f140); url = `${url}${url.includes("?")?"&":"?"}pot=${encodeURIComponent(videoPot)}`; } catch { url = null; }
      if (url) {
        const clen = Number(new URL(url).searchParams.get("clen")) || 0;
        const r = await sequentialDrain(url, clen, MWEB_UA);
        console.log(`  ${"MWEB-140(m4a) vidPot".padEnd(22)} clen=${(clen/1024/1024).toFixed(2)}MiB  ${r.ok ? `WHOLE SONG ✓ (${r.chunks} chunks)` : `FAIL at byte ${r.firstFailAt} (${(r.firstFailAt/1024/1024).toFixed(2)}MiB) status=${r.failStatus}`}`);
        await sleep(400);
      }
    }
    // CONTROL: WEB_REMIX itag 251 with videoId pot on the SAME pinned player
    {
      const wf = await playerFmts(WEB_REMIX, v, cipher.sts, cred, sessionPot);
      const w251 = wf?.find((f) => f.itag === 251);
      if (w251 && (w251.url || w251.signatureCipher)) {
        let url; try { url = deobf(cipher, w251); url = `${url}${url.includes("?")?"&":"?"}pot=${encodeURIComponent(await minter.mint(v))}`; } catch { url = null; }
        if (url) {
          const clen = Number(new URL(url).searchParams.get("clen")) || 0;
          const r = await sequentialDrain(url, clen, WEB_UA);
          console.log(`  ${"WEB_REMIX-251 vidPot".padEnd(22)} clen=${(clen/1024/1024).toFixed(2)}MiB  ${r.ok ? `WHOLE SONG ✓ (${r.chunks} chunks)` : `FAIL at byte ${r.firstFailAt} (${(r.firstFailAt/1024/1024).toFixed(2)}MiB) status=${r.failStatus}`}  [CONTROL]`);
        }
      } else {
        console.log(`  WEB_REMIX-251: sabr-only/unavailable [CONTROL]`);
      }
    }
    console.log();
    await sleep(800);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
