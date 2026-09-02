// The unattended client health scan behind zemer-cipher's client-monitor workflow: drains EVERY
// known client on EVERY validation video and emits one machine-readable verdict per client per
// video — the same drain a human runs with client-fulldownload.mjs, never a second definition of
// "delivered". The roster is DYNAMIC, never a hand-kept list:
//   live     the table's enabled entries (STREAM_CLIENTS_JSON for a candidate file) — a failure
//            here is a kill signal (the monitor benches on the second consecutive sighting)
//   benched  the table's `enabled: false` entries — a whole song here is a revival signal (the
//            monitor un-benches on the second consecutive sighting)
//   retired  tests/clients-retired.mjs, the clients the app REMOVED as dead — a whole song here
//            is a "YouTube revived it, consider re-adding" alert, never an automatic change
//
//   VALIDATION_VIDEO_IDS="id1,id2" node tests/scan-stream-clients.mjs [videoId ...]
//   ONLY_KEYS=WEB_REMIX,VISIONOS   restrict the roster (candidate-table verification of one entry)
//   YT_COOKIE / YT_VISITOR_DATA / YT_DATASYNC_ID (env) or innertube_cookie.txt supply the session.
//
// stdout: JSON { table, videos, cookie, conclusive, clients:[{key, family, main, loginRequired,
//               results:[{video, kind, http, status, itag, clen, read, reason}]}] }
// stderr: the human table, one block per video.
// Exit 0 normally; exit 1 only when the table itself is invalid (devices would reject it).
//
// `conclusive` is the runner sanity canary: false unless SOME client (any role, any video) drained
// a whole song — then the runner, cookie, cipher and pot minter are known-good and every failure
// is the client's own. A bot-gated datacenter IP, a dead cookie or a broken cipher makes EVERY
// client fail, and that must read as "scan broken", never as "every client is dead". (It is
// deliberately not "the MAIN drained": a dead main — e.g. YouTube retiring its version, /player
// 404 — must surface as DEAD so its issue opens and its yt-dlp bump can revive it, not hide
// every other verdict behind an inconclusive scan.)

import "./egress.mjs"; // FIRST: routes every fetch through SCAN_PROXY when set
import { STREAM_CLIENTS_PATH, loadStreamClientsIncludingBenched } from "./stream-clients.mjs";
import { RETIRED } from "./clients-retired.mjs";
import { createDrainContext, drainClient, formatRow } from "./client-fulldownload.mjs";

const videos = [
  ...process.argv.slice(2),
  ...(process.env.VALIDATION_VIDEO_IDS || "").split(/[\s,]+/),
].map((v) => v.trim()).filter(Boolean);
if (videos.length === 0) videos.push("JTF9fLJvniI");

/** The dynamic roster: live table entries (entry 0 = main), benched entries, then retired defs. */
export function buildRoster(table, retired, onlyKeys = []) {
  const roster = [
    ...table.clients.map((c, i) => ({ def: c, role: "live", main: i === 0 })),
    ...table.benched.map((c) => ({ def: c, role: "benched", main: false })),
    // A retired def whose key the table still carries is the table's to judge, not a resurrection.
    ...retired.filter((c) => ![...table.clients, ...table.benched].some((t) => t.key === c.key))
      .map((c) => ({ def: c, role: "retired", main: false })),
  ];
  return onlyKeys.length ? roster.filter((r) => onlyKeys.includes(r.def.key)) : roster;
}

const isMain = process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href;
if (!isMain) {
  // Imported for its exports (tests): do not scan.
} else {
const table = loadStreamClientsIncludingBenched();
const only = (process.env.ONLY_KEYS || "").split(/[\s,]+/).filter(Boolean);
const roster = buildRoster(table, RETIRED);
const scanned = buildRoster(table, RETIRED, only);
const ctx = await createDrainContext();
const clients = scanned.map(({ def, role, main }) => ({
  key: def.key, family: def.family ?? def.clientName, role, main, loginRequired: Boolean(def.loginRequired), results: [],
}));

for (const videoId of [...new Set(videos)]) {
  const video = await ctx.forVideo(videoId);
  console.error(`video=${videoId}`);
  for (const [i, entry] of clients.entries()) {
    const r = await drainClient(ctx, scanned[i].def, video);
    console.error(`${formatRow(r)}${entry.role === "live" ? "" : "   [" + entry.role + "]"}`);
    entry.results.push({ video: videoId, kind: r.kind, http: r.http, status: r.status, itag: r.itag, clen: r.clen, read: r.read, reason: r.reason });
  }
  console.error("");
}
ctx.close();

const conclusive = clients.some((c) => c.results.some((r) => r.kind === "whole"));
const mainHealthy = Boolean(clients.find((c) => c.main)?.results.some((r) => r.kind === "whole"));
process.stdout.write(JSON.stringify({
  table: STREAM_CLIENTS_PATH, videos: [...new Set(videos)], cookie: ctx.hasCookie, conclusive, mainHealthy, clients,
  roles: { live: table.clients.length, benched: table.benched.length, retired: roster.filter((c) => c.role === "retired").length },
  only,
}, null, 2) + "\n");
process.exit(0);
}
