// Download REAL media samples through the app's exact WEB_REMIX resolve path
// (same /player request, cipher, n-transform, videoId-bound pot) for hard-data
// validation of the metadata writers: saves the AAC/m4a (itag 140) and the
// Opus/webm (itag 251) rendition of one video to files.
//
//   node tests/download-media-samples.mjs <outDir> [videoId]
import fs from "node:fs";
import path from "node:path";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const OUT_DIR = process.argv[2] || process.env.OUT_DIR;
const VIDEO_ID = process.argv[3] || process.env.VIDEO_ID || "JTF9fLJvniI";
if (!OUT_DIR) { console.error("usage: node tests/download-media-samples.mjs <outDir> [videoId]"); process.exit(2); }
fs.mkdirSync(OUT_DIR, { recursive: true });

const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return import("node:crypto").then((c) =>
    `SAPISIDHASH ${ts}_${c.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`);
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred));
  const c = CLIENTS.find((x) => x.key === "WEB_REMIX");
  const cipher = await createCipher({});
  const minter = await createMinter(visitorData);
  const potVideo = await minter.mint(VIDEO_ID);
  const potVisitor = await minter.mint(visitorData);

  const body = {
    context: { client: { clientName: c.clientName, clientVersion: c.clientVersion, ...(c.extraClient || {}) } },
    videoId: VIDEO_ID,
    playbackContext: { contentPlaybackContext: { signatureTimestamp: cipher.sts } },
    serviceIntegrityDimensions: { poToken: potVisitor },
  };
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": c.userAgent,
    "X-Goog-Visitor-Id": visitorData,
  };
  if (cred.cookie) { h.cookie = cred.cookie; const a = await sapisidHash(cred.cookie); if (a) h.Authorization = a; }
  const res = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  const j = JSON.parse(await res.text());
  console.log(`player http=${res.status} playability=${j?.playabilityStatus?.status}`);

  const formats = j?.streamingData?.adaptiveFormats || [];
  const targets = [
    { itag: 140, file: `sample-${VIDEO_ID}-140.m4a` },
    { itag: 251, file: `sample-${VIDEO_ID}-251.webm` },
  ];
  for (const t of targets) {
    const fmt = formats.find((f) => f.itag === t.itag);
    if (!fmt) { console.log(`itag ${t.itag}: NOT PRESENT`); continue; }
    let url = fmt.url || cipher.deobfuscateStreamUrl(fmt.signatureCipher);
    url = cipher.transformNParamInUrl(url);
    url += `${url.includes("?") ? "&" : "?"}pot=${encodeURIComponent(potVideo)}`;
    const clen = Number(fmt.contentLength || 0);
    const r = await fetch(url, { headers: { "User-Agent": c.userAgent, Range: "bytes=0-" } });
    if (r.status !== 200 && r.status !== 206) { console.log(`itag ${t.itag}: HTTP ${r.status}`); continue; }
    const outPath = path.join(OUT_DIR, t.file);
    const ws = fs.createWriteStream(outPath);
    const rd = r.body.getReader();
    let read = 0;
    for (;;) { const { done, value } = await rd.read(); if (done) break; ws.write(value); read += value.length; }
    await new Promise((res2) => ws.end(res2));
    const whole = clen && read >= clen;
    console.log(`itag ${t.itag}: ${read} bytes (clen=${clen}) ${whole ? "WHOLE" : "PARTIAL"} -> ${outPath}`);
  }
  cipher._close?.();
  process.exit(0);
})();
