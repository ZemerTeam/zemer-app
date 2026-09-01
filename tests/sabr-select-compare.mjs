// Do zemer and innertubex SELECT the same audio/video itag from an identical format ladder?
// Fetches one live player response (VISIONOS: full avc+vp9+av01 ladder, no cipher needed) and runs
// each codebase's EXACT selection scoring, printing the itag each would stream.
//   node tests/sabr-select-compare.mjs [videoId]

import { getCred, describeCred } from "./cred.mjs";
const VIDEO_ID = process.argv[2] || "dQw4w9WgXcQ";
const ORIGIN = "https://music.youtube.com";
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const C = { clientName: "VISIONOS", clientVersion: "1.02", clientId: 101, ua: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15", osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1" };

const lbl = (f) => `itag ${String(f.itag).padEnd(4)} ${(f.qualityLabel || (f.width ? f.height + "p" : Math.round(f.bitrate / 1000) + "k")).padEnd(7)} ${(f.mimeType || "").split(";")[0].replace(/audio\/|video\//, "").padEnd(5)} ${String(f.bitrate).padStart(8)}bps${f.audioChannels ? " " + f.audioChannels + "ch" : ""}`;

// ---- zemer selection ----
const isOriginal = (f) => !f.audioTrack || f.audioTrack.isAutoDubbed == null;
function zemerAudio(formats, mode, metered) {
  const sign = mode === "HIGH" ? 1 : mode === "LOW" ? -1 : (metered ? -1 : 1);
  return formats.filter((f) => f.width == null && isOriginal(f))
    .map((f) => ({ f, s: f.bitrate * sign + ((f.mimeType || "").startsWith("audio/webm") ? 10240 : 0) }))
    .sort((a, b) => b.s - a.s)[0]?.f;
}
// zemer ranks by the ACTUAL codec string (codecOf), avc1 < vp9 < av01 (lower = better), then bitrate.
const codecOf = (m) => { const i = (m || "").indexOf('codecs="'); if (i < 0) return ""; return (m.slice(i + 8).split(/[".]/)[0] || "").toLowerCase(); };
const zCodecRank = (m) => { const c = codecOf(m); return c === "avc1" ? 0 : (c === "vp9" || c === "vp09") ? 1 : c === "av01" ? 2 : 3; };
function zemerVideo(formats, cap) {
  const v = formats.filter((f) => f.width != null && (f.height || 0) > 0 && f.height <= cap);
  // best rung <= cap: highest height, then better codec (lower rank), then higher bitrate.
  return v.sort((a, b) => (b.height - a.height) || (zCodecRank(a.mimeType) - zCodecRank(b.mimeType)) || (b.bitrate - a.bitrate))[0];
}

// ---- innertubex selection (FormatSelectors.kt, verbatim) ----
function ixAudioScore(f) {
  const codecRank = (f.mimeType || "").includes("audio/webm") ? 100 : (f.mimeType || "").includes("audio/mp4") ? 50 : 0;
  const channelBonus = f.audioChannels === 2 ? 50000 : f.audioChannels === 1 ? 0 : 25000;
  return codecRank * 1e6 + channelBonus + f.bitrate + Math.min(Math.max(f.audioSampleRate ? +f.audioSampleRate : 0, 0), 48000) / 10;
}
function ixAudio(formats, mode) {
  const v = formats.filter((f) => f.width == null);
  if (mode === "LOW") return v.filter((f) => (f.mimeType || "").includes("audio/mp4")).sort((a, b) => a.bitrate - b.bitrate)[0] || v.slice().sort((a, b) => a.bitrate - b.bitrate)[0];
  if (mode === "AUTO") return v.filter((f) => (f.mimeType || "").includes("audio/webm")).sort((a, b) => ixAudioScore(b) - ixAudioScore(a))[0] || v.slice().sort((a, b) => ixAudioScore(b) - ixAudioScore(a))[0];
  return v.slice().sort((a, b) => ixAudioScore(b) - ixAudioScore(a))[0]; // HIGH
}
const ixVideoCodec = (m) => { m = (m || "").toLowerCase(); return (/vp09|vp9|video\/webm/.test(m) ? 3 : /avc1|video\/mp4/.test(m) ? 2 : /av01/.test(m) ? 1 : 0); };
function ixVideoScore(f) { return (f.height || 0) * 1e12 + ixVideoCodec(f.mimeType) * 1e9 + f.bitrate; }
function ixVideo(formats, cap) {
  const v = formats.filter((f) => f.width != null && (f.height || 0) > 0 && f.height <= cap);
  return v.slice().sort((a, b) => ixVideoScore(b) - ixVideoScore(a))[0];
}

(async () => {
  const cred = await getCred();
  const visitorData = dec(cred.visitorData);
  console.log(describeCred(cred), `\nvideo=${VIDEO_ID}\n`);
  const body = { context: { client: { clientName: C.clientName, clientVersion: C.clientVersion, hl: "en", gl: "US", visitorData, osName: C.osName, osVersion: C.osVersion, deviceMake: C.deviceMake, deviceModel: C.deviceModel } }, videoId: VIDEO_ID, contentCheckOk: true, racyCheckOk: true };
  const res = await fetch(ORIGIN + "/youtubei/v1/player?prettyPrint=false", { method: "POST", headers: { "Content-Type": "application/json", "X-YouTube-Client-Name": String(C.clientId), "X-YouTube-Client-Version": C.clientVersion, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": C.ua, "X-Goog-Visitor-Id": visitorData }, body: JSON.stringify(body) });
  const j = JSON.parse(await res.text());
  const fmts = j?.streamingData?.adaptiveFormats || [];
  console.log(`play=${j?.playabilityStatus?.status}  formats=${fmts.length}\n`);

  console.log("=== AUDIO selection ===");
  for (const mode of ["AUTO (unmetered)", "AUTO (metered)", "HIGH", "LOW"]) {
    const m = mode.split(" ")[0], metered = mode.includes("(metered)");
    const z = zemerAudio(fmts, m, metered), i = ixAudio(fmts, m);
    const same = z?.itag === i?.itag ? "SAME" : "DIFF";
    console.log(`  ${mode.padEnd(18)} zemer=[${lbl(z)}]  ix=[${i ? lbl(i) : "-"}]  -> ${same}`);
  }

  console.log("\n=== VIDEO selection (per height cap) ===");
  for (const cap of [2160, 1080, 720, 480, 360, 240, 144]) {
    const z = zemerVideo(fmts, cap), i = ixVideo(fmts, cap);
    if (!z && !i) continue;
    const same = z?.itag === i?.itag ? "SAME" : "DIFF";
    console.log(`  cap ${String(cap).padStart(4)}p  zemer=[${z ? lbl(z) : "-"}]  ix=[${i ? lbl(i) : "-"}]  -> ${same}`);
  }
})();
