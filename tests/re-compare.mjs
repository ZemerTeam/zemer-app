// RE: ANDROID_VR streams a full song; IOS 403s past 1 MiB. Both are direct-url clients with no
// pot. Diff their URLs to find what ungates ANDROID_VR, then try grafting it onto the IOS URL.

import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";

const VID = process.argv[2] || "JTF9fLJvniI";
const MIB = 1048576;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };

async function player(c, vd) {
  const client = { clientName: c.clientName, clientVersion: c.clientVersion, hl: "en", gl: "US", visitorData: vd };
  for (const k of ["osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"]) if (c[k]) client[k] = c[k];
  const r = await fetch(PLAYER_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-YouTube-Client-Name": c.clientId, "X-YouTube-Client-Version": c.clientVersion, "X-Origin": ORIGIN, "User-Agent": c.userAgent, "X-Goog-Visitor-Id": vd },
    body: JSON.stringify({ context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true }),
  });
  return JSON.parse(await r.text());
}
const audio = (j) => (j.streamingData?.adaptiveFormats || []).filter((f) => f.width == null).sort((a, b) => b.bitrate - a.bitrate)[0];
const params = (u) => Object.fromEntries(new URL(u).searchParams);
async function range(u, ua, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": ua, Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }

(async () => {
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  const vrC = CLIENTS.find((x) => x.key === "ANDROID_VR_NO_AUTH");
  const iosC = CLIENTS.find((x) => x.key === "IOS");
  const vr = audio(await player(vrC, vd));
  const ios = audio(await player(iosC, vd));
  const vp = params(vr.url), ip = params(ios.url);

  console.log(`ANDROID_VR itag=${vr.itag} clen=${vr.contentLength}  |  IOS itag=${ios.itag} clen=${ios.contentLength}`);
  console.log("confirm streaming past 1 MiB:");
  console.log(`  ANDROID_VR @1MiB -> ${await range(vr.url, vrC.userAgent, MIB, MIB + 262143)}   IOS @1MiB -> ${await range(ios.url, iosC.userAgent, MIB, MIB + 262143)}`);
  console.log();

  const keys = [...new Set([...Object.keys(vp), ...Object.keys(ip)])].sort();
  console.log("param".padEnd(14), "ANDROID_VR".padEnd(40), "IOS");
  for (const k of keys) {
    const a = vp[k] ?? "—", b = ip[k] ?? "—";
    const mark = a === b ? "" : (a === "—" ? "  <IOS only" : b === "—" ? "  <VR only" : "  <DIFFERS");
    if (mark) console.log(k.padEnd(14), String(a).slice(0, 38).padEnd(40), String(b).slice(0, 38) + mark);
  }
  console.log("\nsparams VR :", vp.sparams);
  console.log("sparams IOS:", ip.sparams);

  // Graft experiments: take IOS url, change unsigned `c`/source toward VR, test @1MiB.
  console.log("\n── graft tests on IOS url (change UNSIGNED params) ──");
  const iosU = ios.url;
  const tryUrl = async (label, u, ua = iosC.userAgent) => console.log(`  ${label.padEnd(34)} @1MiB -> ${await range(u, ua, MIB, MIB + 262143)}`);
  await tryUrl("IOS as-is", iosU);
  await tryUrl("IOS c=ANDROID_VR", iosU.replace(/([?&])c=[^&]*/, `$1c=ANDROID_VR`));
  await tryUrl("IOS + ANDROID_VR User-Agent", iosU, vrC.userAgent);
  if (vp.cpn || ip.cpn) await tryUrl("IOS (has cpn)", iosU);
  process.exit(0);
})();
