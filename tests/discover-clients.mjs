// Discovery sweep: probe InnerTube clients the app does NOT use, to find any that stream a full
// song without attestation we can't provide. Classifies each:
//   EXEMPT WIN  - direct url, no spc gate, whole song (like VISIONOS/ANDROID_VR) -> usable now
//   WEB-POT     - spc gate satisfied by our web videoId poToken (like WEB_REMIX)  -> usable now
//   GATED       - spc but no pot we can mint satisfies it (like IOS)              -> not usable
//   AUTH/DEAD   - LOGIN_REQUIRED / UNPLAYABLE / 400 (maybe stale version)
//
//   PLAYER_HASH=4f38b487 node tests/discover-clients.mjs [videoId]

import crypto from "node:crypto";
import { ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";
import { createCipher } from "./cipher.mjs";
import { createMinter } from "./potoken.mjs";

const VID = process.argv[2] || "JTF9fLJvniI";
const MIB = 1048576;
const D = "20260213";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const A = { osName: "Android", osVersion: "14", androidSdkVersion: 34, deviceMake: "Google", deviceModel: "Pixel 8" };
const I = { osName: "iOS", osVersion: "18.2.22C152", deviceMake: "Apple", deviceModel: "iPhone16,2" };

// [clientName, clientId, clientVersion, deviceFields, userAgent?]  — clients NOT already used by app
const CANDIDATES = [
  ["TVHTML5_SIMPLY", "75", "1.0", {}],
  ["TVHTML5_UNPLUGGED", "65", `7.${D}.00.00`, {}],
  ["TVHTML5_FOR_KIDS", "76", `7.${D}.00.00`, {}],
  ["TVHTML5_AUDIO", "97", `2.0`, {}],
  ["TVHTML5_CAST", "76", `1.0`, {}],
  ["TVHTML5_SIMPLY_EMBEDDED_PLAYER", "85", "2.0", {}],
  ["TVLITE", "87", "2.0", {}],
  ["TVANDROID", "92", "1.0", { ...A }],
  ["ANDROID_UNPLUGGED", "29", "8.49.0", { ...A }],
  ["IOS_UNPLUGGED", "33", "8.49", { ...I }],
  ["WEB_UNPLUGGED", "29", `1.${D}.00.00`, {}],
  ["WEB_EMBEDDED_PLAYER", "56", `1.${D}.01.00`, {}],
  ["ANDROID_EMBEDDED_PLAYER", "55", "21.03.38", { ...A }],
  ["ANDROID_MUSIC", "21", "7.27.52", { ...A }],
  ["IOS_MUSIC", "26", "7.27", { ...I }],
  ["ANDROID_CREATOR_no", "14", "25.03.101", { ...A }],
  ["IOS_CREATOR", "15", "25.03", { ...I }],
  ["MEDIA_CONNECT_FRONTEND", "95", "0.1", {}],
  ["GOOGLE_ASSISTANT", "84", "0.1.0", {}],
  ["ANDROID_TESTSUITE", "30", "1.9", { ...A }],
  ["WEB_KIDS", "76", `2.${D}.00.00`, {}],
  ["ANDROID_KIDS", "17", "21.03.38", { ...A }],
  ["IOS_KIDS", "16", "21.03", { ...I }],
  ["XBOXONEGUIDE", "13", "1.0", {}],
  ["ANDROID_LITE", "3", "21.03.38", { ...A }],
];

// Music clients live on music.youtube.com; everything else on www.youtube.com. SAPISIDHASH origin
// must match the endpoint origin.
function sapisidHash(cookie, origin) { const m = cookie?.match(/(?:^|; )SAPISID=([^;]+)/); if (!m) return null; const t = Math.floor(Date.now() / 1000); return `SAPISIDHASH ${t}_${crypto.createHash("sha1").update(`${t} ${m[1]} ${origin}`).digest("hex")}`; }
async function player(name, id, version, dev, vd, { auth, cookie } = {}) {
  const cn = name.replace(/_no$/, "");
  const origin = /MUSIC|REMIX/.test(cn) ? "https://music.youtube.com" : "https://www.youtube.com";
  const client = { clientName: cn, clientVersion: version, hl: "en", gl: "US", visitorData: vd, ...dev };
  const headers = { "Content-Type": "application/json", "X-YouTube-Client-Name": id, "X-YouTube-Client-Version": version, "X-Origin": origin, Referer: origin + "/", "User-Agent": "Mozilla/5.0", "X-Goog-Visitor-Id": vd };
  if (auth && cookie) { headers.cookie = cookie; const a = sapisidHash(cookie, origin); if (a) headers.Authorization = a; }
  const r = await fetch(`${origin}/youtubei/v1/player?prettyPrint=false`, { method: "POST", headers, body: JSON.stringify({ context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true }) });
  let j = {}; try { j = JSON.parse(await r.text()); } catch {}
  return { http: r.status, j };
}
const audio = (j) => (j.streamingData?.adaptiveFormats || []).filter((f) => f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
const withPot = (u, p) => { const b = u.replace(/([?&])pot=[^&]*/, "$1").replace(/[?&]$/, ""); return p ? `${b}${b.includes("?") ? "&" : "?"}pot=${encodeURIComponent(p)}` : b; };
async function range(u, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": "Mozilla/5.0", Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }
async function drain(u, cap) { try { const r = await fetch(u, { headers: { "User-Agent": "Mozilla/5.0", Range: "bytes=0-" } }); if (r.status !== 200 && r.status !== 206) return 0; let n = 0; const rd = r.body.getReader(); for (;;) { const { done, value } = await rd.read(); if (done) break; n += value.length; if (cap && n >= cap) { await rd.cancel(); break; } } return n; } catch { return 0; } }

(async () => {
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  const cipher = await createCipher({ verbose: false });
  const minter = await createMinter(vd);
  const potVideo = await minter.mint(VID);
  console.log(`video=${VID}  cipher=${cipher.hash}/${cipher.sts}\n`);
  console.log("client".padEnd(32), "http".padEnd(5), "status".padEnd(15), "fmt".padEnd(9), "spc".padEnd(4), "verdict");
  const wins = [];
  for (const [name, id, ver, dev] of CANDIDATES) {
    try {
      let { http, j } = await player(name, id, ver, dev, vd);
      let ps = j?.playabilityStatus?.status || "-";
      let tag = "";
      if ((ps === "LOGIN_REQUIRED" || /sign in/i.test(j?.playabilityStatus?.reason || "")) && cred.cookie) {
        ({ http, j } = await player(name, id, ver, dev, vd, { auth: true, cookie: cred.cookie }));
        ps = j?.playabilityStatus?.status || "-";
        tag = " [auth]";
      }
      const f = audio(j);
      if (!f) {
        // anon LOGIN_REQUIRED then auth 400 == the mobile attestation wall (same as ANDROID_CREATOR):
        // version is fine (anon reached playability), the 400 is the cookie/auth rejection.
        const why = tag && http === 400 ? "auth->400 (DroidGuard/attestation wall)"
          : http === 400 ? "400 (stale ver/unknown client)"
          : (j?.playabilityStatus?.reason || "no audio fmt");
        console.log(name.padEnd(32), String(http).padEnd(5), (ps + tag).padEnd(16), "-".padEnd(9), "-".padEnd(4), why);
        continue;
      }
      const kind = f.url ? "direct" : f.signatureCipher ? "ciphered" : "?";
      let url = f.url;
      if (!url && f.signatureCipher) { try { url = cipher.transformNParamInUrl(cipher.deobfuscateStreamUrl(f.signatureCipher)); } catch {} }
      const spc = (new URL(url).searchParams.get("sparams") || "").includes("spc") ? "YES" : "no";
      const clen = f.contentLength ? Number(f.contentLength) : null;
      const noPot = await range(withPot(url, null), MIB, MIB + 262143);
      const vidPot = await range(withPot(url, potVideo), MIB, MIB + 262143);
      let verdict;
      if (noPot === 206 || noPot === 200) { const got = await drain(withPot(url, null), clen ? clen + 1 : 50 * MIB); verdict = clen && got >= clen ? "EXEMPT WIN (whole song, no pot)" : `partial ${(got / 1024).toFixed(0)}KB`; if (verdict.startsWith("EXEMPT")) wins.push(name); }
      else if (vidPot === 206 || vidPot === 200) { const got = await drain(withPot(url, potVideo), clen ? clen + 1 : 50 * MIB); verdict = clen && got >= clen ? "WEB-POT (whole song w/ videoId pot)" : `pot-partial ${(got / 1024).toFixed(0)}KB`; if (verdict.startsWith("WEB-POT")) wins.push(name); }
      else verdict = `GATED (1MiB->403; pot no help)`;
      console.log(name.padEnd(32), String(http).padEnd(5), (ps + tag).padEnd(16), kind.padEnd(9), spc.padEnd(4), verdict);
    } catch (e) { console.log(name.padEnd(32), "ERR", String(e.message).slice(0, 50)); }
  }
  cipher._close?.();
  console.log(`\nNEW USABLE CLIENTS: ${wins.length ? wins.join(", ") : "none"}`);
  process.exit(0);
})();
