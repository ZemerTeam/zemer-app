// The pre-push gate for stream_clients.json — the client analog of validate-player-config.mjs.
//
//   node tests/validate-stream-clients.mjs [path/to/stream_clients.json] [videoId]
//
// 1. Schema-validates the candidate file with the same rules a device applies (loud on any
//    violation — a file that fails here would be rejected fleet-wide, kept-last-good).
// 2. Full-drains EVERY client in the candidate chain against the live CDN via
//    client-fulldownload.mjs (whole-song delivery is the ground truth — a 206 on the first MiB
//    proves nothing past the free window).
//
// Push a table to zemer-cipher master ONLY after this passes. Login-required clients need
// innertube_cookie.txt at the repo root (they are skipped-with-warning without it).

import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { existsSync } from "node:fs";
import { loadStreamClients, STREAM_CLIENTS_PATH } from "./stream-clients.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const candidatePath = args[0] ? resolve(args[0]) : STREAM_CLIENTS_PATH;
const videoId = args[1] || process.env.VIDEO_ID || "";

console.log(`Candidate: ${candidatePath}`);

// 1. Schema gate — throws with the exact reason on any violation.
const { clients, families, skipped } = loadStreamClients(candidatePath);
console.log(`Schema OK: ${clients.length} usable clients` +
  (skipped.length ? ` (skipped: ${skipped.join(", ")})` : ""));
console.log("\n  #  key                  clientName        proto            login  family");
clients.forEach((c, i) => {
  const login = c.loginRequired ? "req" : c.loginSupported ? "opt" : "-";
  console.log(
    `  ${String(i).padEnd(2)} ${c.key.padEnd(20)} ${c.clientName.padEnd(17)} ` +
    `${c.protocol.padEnd(16)} ${login.padEnd(6)} ${c.family}` +
    (i === 0 ? "   <- MAIN" : ""),
  );
});
for (const c of clients) {
  if (!families[c.family]) console.log(`  note: family ${c.family} has no families[] display row`);
}

// 2. Live whole-song drain per client, against the CANDIDATE file.
const cookiePath = join(here, "..", "innertube_cookie.txt");
if (!existsSync(cookiePath) && clients.some((c) => c.loginRequired)) {
  console.log("\nWARNING: no innertube_cookie.txt — login-required clients will fail their drain.");
}
console.log("\nRunning whole-song drains (client-fulldownload.mjs)...\n");
const result = spawnSync(
  process.execPath,
  [join(here, "client-fulldownload.mjs"), ...(videoId ? [videoId] : [])],
  {
    stdio: "inherit",
    env: {
      ...process.env,
      STREAM_CLIENTS_JSON: candidatePath,
      CLIENTS: clients.map((c) => c.key).join(","),
    },
  },
);
process.exit(result.status ?? 1);
