// RE: the 1 MiB gate is the `spc` (signed playback context) param. ANDROID_VR has none and streams
// freely; IOS has spc and is pot-gated. Survey direct-url clients for spc presence + full download
// to find a non-gated client usable in place of IOS/IPADOS.

import { CLIENTS, ORIGIN, PLAYER_URL } from "./clients.mjs";
import { getCred } from "./cred.mjs";

const VID = process.argv[2] || "JTF9fLJvniI";
const MIB = 1048576;
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const SURVEY = ["IOS", "IPADOS", "VISIONOS", "ANDROID_VR_NO_AUTH", "ANDROID_VR_1_43_32"];

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
async function range(u, ua, s, e) { try { const r = await fetch(u, { headers: { "User-Agent": ua, Range: `bytes=${s}-${e}` } }); r.body?.cancel?.(); return r.status; } catch { return "ERR"; } }
async function drain(u, ua, cap) { try { const r = await fetch(u, { headers: { "User-Agent": ua, Range: "bytes=0-" } }); if (r.status !== 200 && r.status !== 206) return { status: r.status, read: 0 }; let n = 0; const rd = r.body.getReader(); for (;;) { const { done, value } = await rd.read(); if (done) break; n += value.length; if (cap && n >= cap) { await rd.cancel(); break; } } return { status: r.status, read: n }; } catch { return { status: "ERR", read: 0 }; } }

(async () => {
  const cred = await getCred();
  const vd = dec(cred.visitorData);
  console.log(`video=${VID}\n`);
  console.log("client".padEnd(20), "play".padEnd(6), "spc?".padEnd(6), "@1MiB".padEnd(7), "full-drain");
  for (const key of SURVEY) {
    const c = CLIENTS.find((x) => x.key === key);
    if (!c) { console.log(key.padEnd(20), "(not in clients.mjs)"); continue; }
    try {
      const j = await player(c, vd);
      const ps = j.playabilityStatus?.status || "-";
      const f = audio(j);
      if (!f?.url) { console.log(key.padEnd(20), ps.padEnd(6), "-".padEnd(6), "-".padEnd(7), "no direct url"); continue; }
      const sp = new URL(f.url).searchParams.get("sparams") || "";
      const hasSpc = sp.includes("spc") ? "YES" : "no";
      const at1 = await range(f.url, c.userAgent, MIB, MIB + 262143);
      const clen = f.contentLength ? Number(f.contentLength) : null;
      const d = await drain(f.url, c.userAgent, clen ? clen + 1 : 50 * MIB);
      const whole = clen && d.read >= clen;
      console.log(key.padEnd(20), ps.padEnd(6), hasSpc.padEnd(6), String(at1).padEnd(7),
        `${(d.read / 1024).toFixed(0)}KB${clen ? `/${(clen / 1024).toFixed(0)}KB` : ""} ${whole ? "WHOLE SONG" : "stopped @ " + (d.status)}`);
    } catch (e) { console.log(key.padEnd(20), "ERR", String(e.message).slice(0, 40)); }
  }
  process.exit(0);
})();
