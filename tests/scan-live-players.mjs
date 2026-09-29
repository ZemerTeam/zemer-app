// Scan the live YouTube player surfaces MANY times to catch low-rate A/B "canary" players
// EARLY — before they ramp to dominant and break playback for users. The old monitor took a
// single sample per run, so a player served 1/6 of the time was missed ~83% of the time and
// only surfaced once it had already rotated in. Sampling N times per run turns that into a
// near-certain catch on the first hour the canary appears at all.
//
//   node tests/scan-live-players.mjs <player_configs.json> [samples]
//   SAMPLES=30 node tests/scan-live-players.mjs /tmp/player_configs.json
//
// stdout: machine-readable JSON { samples, requested, distinct:[{hash,count}],
//         unknown:[{hash,count,md5,sts}] } for the workflow to act on.
// stderr: a human one-line summary.
// Exit 0 normally; exit 1 if the config file is invalid (devices reject it wholesale).

import crypto from "node:crypto";
import { readFileSync } from "node:fs";
import { parsePlayerConfigs } from "./player-configs.mjs";

const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
// iframe_api JSON-escapes slashes (\/s\/player\/HASH\/); music.youtube.com does not.
const IFRAME_RE = /\\?\/s\\?\/player\\?\/([a-z0-9]{8})\\?\//;
const PLAIN_RE = /\/s\/player\/([a-z0-9]{8})\//;

/** Collapse a list of sampled hashes into distinct {hash,count}, most-seen first. Pure. */
export function aggregate(samples) {
  const counts = new Map();
  for (const h of samples) {
    if (!h) continue;
    counts.set(h, (counts.get(h) || 0) + 1);
  }
  return [...counts.entries()]
    .map(([hash, count]) => ({ hash, count }))
    .sort((a, b) => b.count - a.count || a.hash.localeCompare(b.hash));
}

/** The set of hashes a device would accept from this config (primaries + aliases). Pure. */
export function coveredKeys(configsText, file) {
  const players = parsePlayerConfigs(configsText, file);
  const keys = new Set();
  for (const [primary, entry] of Object.entries(players)) {
    keys.add(primary);
    for (const alias of entry.aliases ?? []) keys.add(alias);
  }
  return keys;
}

async function fetchText(url) {
  try {
    const r = await fetch(url, { headers: { "User-Agent": UA } });
    return r.ok ? await r.text() : "";
  } catch {
    return "";
  }
}
const hashFrom = (body, re) => (body.match(re) || [])[1] || null;

// A stable, always-available public video for the watch/embed surfaces. The player served is
// bucketed by session/experiment, not by which video — so any reliable id surfaces the same canary.
const PROBE_VID = "dQw4w9WgXcQ";
const sampleIframe = async () => hashFrom(await fetchText("https://www.youtube.com/iframe_api"), IFRAME_RE);
const sampleMusic = async () => hashFrom(await fetchText("https://music.youtube.com/"), PLAIN_RE);
const sampleWatch = async () => hashFrom(await fetchText(`https://www.youtube.com/watch?v=${PROBE_VID}`), PLAIN_RE);
const sampleEmbed = async () => hashFrom(await fetchText(`https://www.youtube.com/embed/${PROBE_VID}`), PLAIN_RE);

// md5-of-first-10000-bytes alias + sts for an unknown hash — the same alias identity the app
// (FunctionNameExtractor.extractPlayerHash) computes.
async function playerIdentity(hash) {
  for (const locale of ["en_GB", "en_US"]) {
    const js = await fetchText(`https://www.youtube.com/s/player/${hash}/player_ias.vflset/${locale}/base.js`);
    if (js) {
      const md5 = crypto.createHash("md5").update(Buffer.from(js.slice(0, 10000), "utf8")).digest("hex").slice(0, 8);
      const sts = (js.match(/signatureTimestamp[':\s"]+(\d{4,6})/) || [])[1] || null;
      return { md5, sts };
    }
  }
  return { md5: null, sts: null };
}

async function main() {
  const [file, samplesArg] = process.argv.slice(2);
  if (!file) {
    console.error("usage: node tests/scan-live-players.mjs <player_configs.json> [samples]");
    process.exit(2);
  }
  const requested = Number(samplesArg || process.env.SAMPLES || 30);
  const covered = coveredKeys(readFileSync(file, "utf8"), file); // throws on invalid file -> exit 1

  // iframe_api is what the app's PlayerJsFetcher uses — sample it heavily (the `requested` count).
  // music / watch / embed are sampled lightly: a canary often A/B-shows on one of these BEFORE it
  // reaches iframe_api (early warning), and a hash unique to one surface is still caught.
  const aux = Math.max(2, Math.floor(requested / 6));
  const samples = [];
  for (let i = 0; i < requested; i++) samples.push(await sampleIframe());
  for (let i = 0; i < aux; i++) samples.push(await sampleMusic());
  for (let i = 0; i < aux; i++) samples.push(await sampleWatch());
  for (let i = 0; i < aux; i++) samples.push(await sampleEmbed());

  const distinct = aggregate(samples);
  const unknown = [];
  for (const d of distinct) {
    if (covered.has(d.hash)) continue;
    const { md5, sts } = await playerIdentity(d.hash);
    if (md5 && covered.has(md5)) continue; // covered via its md5 alias
    unknown.push({ hash: d.hash, count: d.count, md5, sts });
  }

  const seen = samples.filter(Boolean).length;
  console.log(JSON.stringify({ samples: seen, requested, distinct, unknown }, null, 2));
  process.stderr.write(
    `scanned ${seen} samples -> ${distinct.length} distinct: ` +
      distinct.map((d) => `${d.hash}=${d.count}${covered.has(d.hash) ? "" : "(NEW)"}`).join(" ") +
      (unknown.length
        ? `\nUNKNOWN: ${unknown.map((u) => `${u.hash} (seen ${u.count}x, md5 ${u.md5}, sts ${u.sts})`).join("; ")}\n`
        : "\nall covered\n"),
  );
}

// Only run main when invoked directly (not when imported by the unit test).
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
