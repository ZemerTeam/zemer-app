// RE: what makes an IOS stream URL serve past the 1 MiB free window?
// Hypotheses tested against the live CDN:
//   H1 — pot must be in the /player request (serviceIntegrityDimensions), so YouTube returns an
//        ungated URL (not appended to the URL like web clients).
//   H2 — pot must be appended to the URL.
//   H3 — both.
//   H4 — the IOS URL already carries everything; the gate is something else (sparams/cpn/redirect).
// For each request-pot variant we resolve the URL and probe @0 / @1MiB / @2MiB, with and without
// the pot also on the URL, and print the URL's sparams + whether `pot` is required/signed.

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";

const VID = process.argv[2] || "JTF9fLJvniI";
const MIB = 1048576;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const c = CLIENTS.find((x) => x.key === "IOS");

async function iosPlayer(visitorData, poToken) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData };
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel"]) if (c[k]) client[k] = c[k];
  const body = { context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const r = await fetch(PLAYER_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, "User-Agent": c.userAgent, "X-Goog-Visitor-Id": visitorData },
    body: JSON.stringify(body),
  });
  return JSON.parse(await r.text());
}
const audio = (j) => (j.streamingData?.adaptiveFormats || []).filter((f) => f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
const withPot = (u, p) => { const b = u.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, ""); return p ? `${b}${b.includes("?") ? "&" : "?"}pot=${encodeURIComponent(p)}` : b; };
async function range(u, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": c.userAgent, Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }

(async () => {
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  const minter = await createMinter(vd);
  const potVideo = await minter.mint(VID);
  const potVisitor = await minter.mint(vd);

  // Inspect the baseline IOS URL anatomy.
  const j0 = await iosPlayer(vd, null);
  const f0 = audio(j0);
  const q = new URL(f0.url).searchParams;
  console.log(`IOS baseline url: itag=${f0.itag} clen=${f0.contentLength}`);
  console.log(`  has pot=${q.has("pot")} n=${q.has("n")} ratebypass=${q.get("ratebypass")} gir=${q.get("gir")} lmt=${q.get("lmt")}`);
  console.log(`  sparams=${q.get("sparams")}`);
  console.log(`  (pot in sparams? ${(q.get("sparams") || "").includes("pot")})  expire=${q.get("expire")}`);
  console.log();

  const reqPots = [["none", null], ["web-videoId", potVideo], ["web-visitorData", potVisitor]];
  for (const [rn, rp] of reqPots) {
    const j = await iosPlayer(vd, rp);
    const f = audio(j);
    if (!f?.url) { console.log(`req=${rn}: no url (playability=${j.playabilityStatus?.status})`); continue; }
    const base = f.url;
    const bq = new URL(base).searchParams;
    const nopot = await range(withPot(base, null), MIB, MIB + 262143);
    const vid = await range(withPot(base, potVideo), MIB, MIB + 262143);
    const vis = await range(withPot(base, potVisitor), MIB, MIB + 262143);
    const first = await range(withPot(base, null), 0, 262143);
    console.log(`req=${rn.padEnd(15)} urlHasPot=${bq.has("pot")}  @0=${first}  @1MiB[urlpot none/video/visitor]=${nopot}/${vid}/${vis}`);
  }
  process.exit(0);
})();
