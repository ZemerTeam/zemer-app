// Identity-drift scan: compare each table client's identity (clientVersion, userAgent, os/device
// fields) with yt-dlp master's INNERTUBE_CLIENTS — the table mirrors yt-dlp-master-exact by
// policy. Input for the client monitor's AUTO-BUMP: a drifted entry is copied into a candidate
// table with yt-dlp's values (cipher tools/clients/apply-bump.mjs), the candidate is drained on
// every validation video, and only an all-whole-song result is deployed. Drift by itself is never
// a kill signal — a months-old version keeps draining until YouTube retires it.
//
//   node tests/scan-client-versions.mjs            # bundled table
//   STREAM_CLIENTS_JSON=cand.json node tests/scan-client-versions.mjs
//
// stdout: JSON { source, drift:[{key, clientName, ours, ytdlp}], matched:[...], unmapped:[...] }
// Exit 0 even on drift (the workflow decides what to do); exit 2 if yt-dlp could not be fetched.

import { loadStreamClients } from "./stream-clients.mjs";

const SOURCE = process.env.YTDLP_BASE_URL ||
  "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/extractor/youtube/_base.py";

// Which yt-dlp key an entry mirrors is TABLE data (`mirrors` on the entry, read by the loader):
// a clientName can back several yt-dlp keys (TVHTML5: tv / tv_downgraded) and an entry can be a
// deliberately pinned OLD config (VISIONOS_0_1, the second chance) that must never be bumped —
// only an entry that says what it mirrors is compared.

/** The client-identity fields the table mirrors from yt-dlp (all optional but clientVersion). */
export const IDENTITY_FIELDS = ["clientVersion", "userAgent", "osName", "osVersion", "deviceMake", "deviceModel", "androidSdkVersion"];

/**
 * Parse each `'<key>': { 'INNERTUBE_CONTEXT': { 'client': { ... } } }` block into
 * { clientName, clientVersion, userAgent?, osName?, osVersion?, deviceMake?, deviceModel?,
 *   androidSdkVersion? } (pure, tested). Numbers (androidSdkVersion) become strings, the table's
 * shape. Only single-quoted scalar fields are read — a client dict yt-dlp writes differently is
 * reported as missing, never guessed.
 */
export function parseYtdlpClients(pySource) {
  const start = pySource.indexOf("INNERTUBE_CLIENTS");
  const src = start >= 0 ? pySource.slice(start) : pySource;
  const out = {};
  const re = /^ {4}'([a-z_0-9]+)': \{/gm;
  let m;
  while ((m = re.exec(src))) {
    const block = src.slice(m.index, m.index + 2500);
    // The client dict: from `'client': {` to its closing brace at the same indent.
    const c = block.indexOf("'client': {");
    if (c < 0) continue;
    const end = block.indexOf("\n            }", c);
    const clientDict = block.slice(c, end > 0 ? end : c + 1200);
    const field = (name) => {
      const hit = clientDict.match(new RegExp(`'${name}': (?:'([^']*)'|(\\d+))`));
      return hit ? (hit[1] ?? hit[2]) : undefined;
    };
    const clientName = field("clientName"), clientVersion = field("clientVersion");
    if (!clientName || !clientVersion) continue;
    const entry = { clientName };
    for (const f of IDENTITY_FIELDS) { const v = field(f); if (v !== undefined) entry[f] = String(v); }
    out[m[1]] = entry;
  }
  return out;
}

/**
 * Diff a table against the parsed yt-dlp map (pure, tested). For each entry that `mirrors` a
 * yt-dlp key, `changes` lists every identity field where yt-dlp HAS a value that differs from ours
 * (a field yt-dlp does not set — web clients carry no userAgent there — is left as ours, never
 * removed); `fields` is the full target value set an auto-bump applies. Entries without
 * `mirrors` are `pinned` (never compared); a `mirrors` key yt-dlp no longer has is `unmapped`.
 */
export function versionDrift(clients, ytdlp) {
  const drift = [], matched = [], unmapped = [], pinned = [];
  for (const c of clients) {
    const key = c.mirrors;
    if (!key) { pinned.push(c.key); continue; }
    const ref = ytdlp[key];
    if (!ref) { unmapped.push(c.key); continue; }
    if (ref.clientName !== c.clientName) { unmapped.push(c.key); continue; }
    const changes = {};
    for (const f of IDENTITY_FIELDS) {
      if (ref[f] === undefined) continue;
      if ((c[f] ?? null) !== ref[f]) changes[f] = { from: c[f] ?? null, to: ref[f] };
    }
    const row = { key: c.key, clientName: c.clientName, ytdlpKey: key, ours: c.clientVersion, ytdlp: ref.clientVersion, changes,
      fields: Object.fromEntries(Object.entries(changes).map(([f, ch]) => [f, ch.to])) };
    (Object.keys(changes).length ? drift : matched).push(row);
  }
  return { drift, matched, unmapped, pinned };
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
  for (const d of report.drift) console.error(`drift: ${d.key} <- yt-dlp ${d.ytdlpKey}: ${Object.entries(d.changes).map(([f, c]) => `${f} ${JSON.stringify(c.from)} -> ${JSON.stringify(c.to)}`).join(", ")}`);
  process.stdout.write(JSON.stringify(report, null, 2) + "\n");
}
