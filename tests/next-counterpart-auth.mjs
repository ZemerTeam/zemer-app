// §9 verification 1 for the unified-video feature: does an AUTHENTICATED next() actually deliver the
// song↔video counterpart as a playlistPanelVideoWrapperRenderer? Step 2 proved anonymous requests
// never do; the app's next() runs setLogin=true with a cookie (personal or pooled), so this reproduces
// the app's EXACT authenticated /next request (WEB_REMIX context, cookie + SAPISIDHASH, visitorData,
// onBehalfOfUser=dataSyncId) and reports wrapper/counterpart presence.
//
//   node tests/next-counterpart-auth.mjs [videoId ...]   # defaults to a couple of ATV song ids
//
// Needs innertube_cookie.txt at the repo root. Prints, per id: HTTP, wrapper count, and each
// counterpart (videoId + musicVideoType) — the data the app caches as VideoAvailability.counterpart.
// Capture a positive response body (COUNTERPART_DUMP=1) as the innertube NextCounterpartTest fixture.

import crypto from "node:crypto";
import fs from "node:fs";
import { CLIENTS, ORIGIN } from "./clients.mjs";
import { getCred, describeCred } from "./cred.mjs";

const NEXT_URL = ORIGIN + "/youtubei/v1/next?prettyPrint=false";
const WEB_REMIX = CLIENTS.find((c) => c.key === "WEB_REMIX");
const IDS = process.argv.slice(2);
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  const hash = crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex");
  return `SAPISIDHASH ${ts}_${hash}`;
}

function findWrappers(obj, acc = []) {
  if (Array.isArray(obj)) { for (const v of obj) findWrappers(v, acc); return acc; }
  if (obj && typeof obj === "object") {
    if (obj.playlistPanelVideoWrapperRenderer) acc.push(obj.playlistPanelVideoWrapperRenderer);
    for (const k of Object.keys(obj)) findWrappers(obj[k], acc);
  }
  return acc;
}
const mvType = (r) => r?.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType;
const vid = (r) => r?.videoId || r?.navigationEndpoint?.watchEndpoint?.videoId;

async function probe(id, cred) {
  const visitorData = dec(cred.visitorData);
  const client = { clientName: WEB_REMIX.clientName, clientVersion: WEB_REMIX.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  const body = { context: { client }, videoId: id, isAudioOnly: true, enablePersistentPlaylistPanel: true };
  if (cred.dataSyncId) body.context.user = { onBehalfOfUser: cred.dataSyncId };

  const headers = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": WEB_REMIX.clientId, "X-YouTube-Client-Version": WEB_REMIX.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": WEB_REMIX.userAgent,
  };
  if (visitorData) headers["X-Goog-Visitor-Id"] = visitorData;
  if (cred.cookie) {
    headers.cookie = cred.cookie;
    const a = sapisidHash(cred.cookie);
    if (a) headers.Authorization = a;
  }

  const res = await fetch(NEXT_URL, { method: "POST", headers, body: JSON.stringify(body) });
  const text = await res.text();
  let j = {}; try { j = JSON.parse(text); } catch {}
  const wrappers = findWrappers(j);
  console.log(`\n== videoId=${id} HTTP ${res.status}: wrappers=${wrappers.length}`);
  for (const w of wrappers) {
    const p = w.primaryRenderer?.playlistPanelVideoRenderer;
    const cps = (w.counterpart || []).map((x) => x.counterpartRenderer?.playlistPanelVideoRenderer);
    console.log(`   primary ${vid(p)} [${mvType(p)}] -> counterparts: ${cps.map((c) => `${vid(c)} [${mvType(c)}]`).join(", ") || "(none)"}`);
  }
  if (wrappers.length && process.env.COUNTERPART_DUMP) {
    const out = `tests/counterpart-${id}.json`;
    fs.writeFileSync(out, text);
    console.log(`   full body dumped to ${out} (candidate innertube test fixture)`);
  }
  return wrappers.length;
}

(async () => {
  const cred = await getCred();
  console.log(describeCred(cred));
  if (!/SAPISID=/.test(cred.cookie || "")) {
    console.log("WARNING: no SAPISID in cookie — this will run UNAUTHENTICATED and (per step 2) find no wrappers.");
  }
  const ids = IDS.length ? IDS : ["JTF9fLJvniI"];
  let total = 0;
  for (const id of ids) { try { total += await probe(id, cred); } catch (e) { console.log(`  ${id} FAILED: ${e.message}`); } }
  console.log(`\nTOTAL wrappers across ${ids.length} id(s): ${total}`);
  console.log(total ? "RESULT: counterparts ARE delivered when authenticated → COUNTERPART rendition viable."
    : "RESULT: no counterparts even authenticated → ship on SELF+LOCAL only (shrink DESIGN §1).");
  process.exit(0);
})();
