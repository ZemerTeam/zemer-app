// Deeper RE (no SABR): can IOS/IPADOS/ANDROID_CREATOR stream a full song?
//   A. Avoid the spc gate on IOS by varying clientVersion / userAgent / device fields.
//   B. Satisfy spc with other pot bindings (videoId / visitorData / url `id` param).
//   D. ANDROID_CREATOR: dump the real 400 body; test +pot; test plain ANDROID auth.

import crypto from "node:crypto";
import { CLIENTS, ORIGIN, PLAYER_URL, USER_AGENT_WEB } from "./clients.mjs";
import { getCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";

const VID = process.argv[2] || "JTF9fLJvniI";
const MIB = 1048576;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

function sapisid(cookie) { const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const t = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${t}_${crypto.createHash("sha1").update(`${t} ${m[1]} ${ORIGIN}`).digest("hex")}`; }
async function player(client, ua, clientId, { cookie, auth, poToken } = {}) {
  const body = { context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true };
  if (poToken) body.serviceIntegrityDimensions = { poToken };
  const h = { "Content-Type": "application/json", "X-YouTube-Client-Name": clientId, "X-YouTube-Client-Version": client.clientVersion, "X-Origin": ORIGIN, "User-Agent": ua, "X-Goog-Visitor-Id": client.visitorData };
  if (auth && cookie) { h.cookie = cookie; const a = sapisid(cookie); if (a) h.Authorization = a; }
  const r = await fetch(PLAYER_URL, { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await r.text()); } catch {}
  return { http: r.status, j };
}
const audio = (j) => (j.streamingData?.adaptiveFormats || []).filter((f) => f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
const withPot = (u, p) => { const b = u.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, ""); return p ? `${b}${b.includes("?") ? "&" : "?"}pot=${encodeURIComponent(p)}` : b; };
async function r1(u, ua, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": ua, Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }

(async () => {
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  const minter = await createMinter(vd);
  const potVideo = await minter.mint(VID);
  const potVisitor = await minter.mint(vd);
  const iosUA = (v) => `com.google.ios.youtube/${v} (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)`;

  console.log("══ A. IOS spc-trigger: vary clientVersion / UA / device ══");
  const variants = [
    ["v21.03.1 native", { clientName: "IOS", clientVersion: "21.03.1", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2", visitorData: vd }, iosUA("21.03.1"), "5"],
    ["v19.29.1 native", { clientName: "IOS", clientVersion: "19.29.1", osName: "iOS", osVersion: "17.5.1.21F90", deviceMake: "Apple", deviceModel: "iPhone16,2", visitorData: vd }, iosUA("19.29.1"), "5"],
    ["v17.40.5 native", { clientName: "IOS", clientVersion: "17.40.5", osName: "iOS", osVersion: "16.6.21G80", deviceMake: "Apple", deviceModel: "iPhone14,5", visitorData: vd }, iosUA("17.40.5"), "5"],
    ["v16.20 native", { clientName: "IOS", clientVersion: "16.20", osName: "iOS", osVersion: "15.6", deviceMake: "Apple", deviceModel: "iPhone14,5", visitorData: vd }, iosUA("16.20"), "5"],
    ["v21.03.1 no-device", { clientName: "IOS", clientVersion: "21.03.1", visitorData: vd }, iosUA("21.03.1"), "5"],
    ["v21.03.1 webUA", { clientName: "IOS", clientVersion: "21.03.1", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2", visitorData: vd }, USER_AGENT_WEB, "5"],
  ];
  for (const [label, cl, ua, id] of variants) {
    const { http, j } = await player(cl, ua, id);
    const f = audio(j);
    if (!f?.url) { console.log(`  ${label.padEnd(20)} http=${http} ${(j.playabilityStatus?.status || "-")} no-url`); continue; }
    const spc = (new URL(f.url).searchParams.get("sparams") || "").includes("spc") ? "spc" : "NO-spc";
    const at1 = await r1(f.url, ua, MIB, MIB + 262143);
    const win = spc === "NO-spc" && (at1 === 206 || at1 === 200) ? "  <<< UNGATED!" : "";
    console.log(`  ${label.padEnd(20)} ${(j.playabilityStatus?.status || "-").padEnd(5)} ${spc.padEnd(7)} @1MiB=${at1}${win}`);
  }

  console.log("\n══ B. satisfy IOS spc with other pot bindings ══");
  const iosCl = { clientName: "IOS", clientVersion: "21.03.1", osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2", visitorData: vd };
  const ij = (await player(iosCl, iosUA("21.03.1"), "5")).j;
  const iosF = audio(ij);
  const idParam = new URL(iosF.url).searchParams.get("id");
  const potId = await minter.mint(idParam);
  for (const [n, p] of [["videoId", potVideo], ["visitorData", potVisitor], ["url-id-param", potId]]) {
    console.log(`  url pot=${n.padEnd(13)} @1MiB=${await r1(withPot(iosF.url, p), iosUA("21.03.1"), MIB, MIB + 262143)}`);
  }

  console.log("\n══ D. ANDROID_CREATOR 400 diagnosis ══");
  const ac = CLIENTS.find((x) => x.key === "ANDROID_CREATOR");
  const acCl = () => { const cl = { clientName: ac.clientName, clientVersion: ac.clientVersion, hl: "en", gl: "US", visitorData: vd }; for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (ac[k]) cl[k] = ac[k]; return cl; };
  const acAuth = await player(acCl(), ac.userAgent, ac.clientId, { cookie: cred.cookie, auth: true });
  console.log(`  ANDROID_CREATOR auth     -> http=${acAuth.http} body=${JSON.stringify(acAuth.j?.error?.message || acAuth.j?.error?.status || acAuth.j?.playabilityStatus?.status || acAuth.j).slice(0, 110)}`);
  const acPot = await player(acCl(), ac.userAgent, ac.clientId, { cookie: cred.cookie, auth: true, poToken: potVisitor });
  console.log(`  ANDROID_CREATOR auth+pot -> http=${acPot.http} ${acPot.j?.playabilityStatus?.status || ""}`);
  const acAnon = await player(acCl(), ac.userAgent, ac.clientId, { poToken: potVisitor });
  console.log(`  ANDROID_CREATOR anon+pot -> http=${acAnon.http} ${acAnon.j?.playabilityStatus?.status || ""} ${acAnon.j?.playabilityStatus?.reason || ""}`);
  const an = CLIENTS.find((x) => x.key === "ANDROID");
  if (an) { const r = await player({ clientName: an.clientName, clientVersion: an.clientVersion, hl: "en", gl: "US", visitorData: vd }, an.userAgent, an.clientId, { cookie: cred.cookie, auth: true }); console.log(`  plain ANDROID auth       -> http=${r.http} ${r.j?.playabilityStatus?.status || ""} (is the 400 mobile-auth-wide or CREATOR-specific?)`); }
  process.exit(0);
})();
