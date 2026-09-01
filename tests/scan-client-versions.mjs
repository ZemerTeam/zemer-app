// Version-drift scan: compare each table client's clientVersion with yt-dlp master's
// INNERTUBE_CLIENTS (the table's versions are "yt-dlp-master-exact" by policy). ALERT-ONLY input
// for the client monitor: drift is a to-do, not a kill signal — a months-old version keeps
// draining whole songs until YouTube retires it, and only a failed drain may bench a client.
//
//   node tests/scan-client-versions.mjs            # bundled table
//   STREAM_CLIENTS_JSON=cand.json node tests/scan-client-versions.mjs
//
// stdout: JSON { source, drift:[{key, clientName, ours, ytdlp}], matched:[...], unmapped:[...] }
// Exit 0 even on drift (the workflow decides what to do); exit 2 if yt-dlp could not be fetched.

import { loadStreamClients } from "./stream-clients.mjs";

const SOURCE = process.env.YTDLP_BASE_URL ||
  "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/extractor/youtube/_base.py";

// Our clientName -> the yt-dlp INNERTUBE_CLIENTS key whose config the entry mirrors. A clientName
// can back several yt-dlp keys (TVHTML5: tv / tv_downgraded), so the mapping is explicit.
const YTDLP_KEY = {
  WEB_REMIX: "web_music", WEB_CREATOR: "web_creator", TVHTML5_SIMPLY: "tv_simply", VISIONOS: "visionos",
  WEB: "web", MWEB: "mweb", ANDROID_VR: "android_vr", IOS: "ios", ANDROID: "android", TVHTML5: "tv",
};

/** Parse `'<key>': { ... 'clientName': 'X', ... 'clientVersion': 'Y' ... }` blocks (pure, tested). */
export function parseYtdlpClients(pySource) {
  const start = pySource.indexOf("INNERTUBE_CLIENTS");
  const src = start >= 0 ? pySource.slice(start) : pySource;
  const out = {};
  const re = /^ {4}'([a-z_0-9]+)': \{/gm;
  let m;
  while ((m = re.exec(src))) {
    const block = src.slice(m.index, m.index + 2000);
    const name = block.match(/'clientName': '([A-Z_0-9]+)'/);
    const version = block.match(/'clientVersion': '([^']+)'/);
    if (name && version) out[m[1]] = { clientName: name[1], clientVersion: version[1] };
  }
  return out;
}

/** Diff a table against the parsed yt-dlp map (pure, tested). */
export function versionDrift(clients, ytdlp) {
  const drift = [], matched = [], unmapped = [];
  for (const c of clients) {
    const key = YTDLP_KEY[c.clientName];
    const ref = key && ytdlp[key];
    if (!ref) { unmapped.push(c.key); continue; }
    (ref.clientVersion === c.clientVersion ? matched : drift)
      .push({ key: c.key, clientName: c.clientName, ours: c.clientVersion, ytdlp: ref.clientVersion, ytdlpKey: key });
  }
  return { drift, matched, unmapped };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const res = await fetch(SOURCE, { headers: { "User-Agent": "zemer-client-monitor" } }).catch(() => null);
  if (!res || !res.ok) {
    console.error(`could not fetch ${SOURCE}: ${res ? res.status : "network error"}`);
    process.exit(2);
  }
  const ytdlp = parseYtdlpClients(await res.text());
  const { clients } = loadStreamClients();
  const report = { source: SOURCE, ...versionDrift(clients, ytdlp) };
  for (const d of report.drift) console.error(`drift: ${d.key} ours=${d.ours} yt-dlp(${d.ytdlpKey})=${d.ytdlp}`);
  process.stdout.write(JSON.stringify(report, null, 2) + "\n");
}
