// Scan EVERY live YouTube player surface MANY times to catch low-rate A/B "canary" players
// EARLY — before they ramp to dominant and break playback for users. A player served 1/6 of the
// time is missed ~83% of the time by a single sample; sampling N times per run, across every
// surface and a rotation of user agents and locales, turns that into a near-certain catch on the
// first hour the canary appears anywhere.
//
//   node tests/scan-live-players.mjs <player_configs.json> [samples]
//   SAMPLES=30 node tests/scan-live-players.mjs /tmp/player_configs.json
//
// stdout: machine-readable JSON { samples, requested, distinct:[{hash,count}],
//         unknown:[{hash,count,md5,sts}], surfaces:{name:{requests,ok,hashes,errors}}, blind:[name] }
//         for the workflow to act on.
// stderr: a human summary, one line per surface.
// Exit 0 normally; 1 if the config file is invalid (devices reject it wholesale); 3 if any surface
// yielded NO player at all (the scan is blind there — it must never pass as "all covered").

import crypto from "node:crypto";
import https from "node:https";
import { readFileSync } from "node:fs";
import { parsePlayerConfigs } from "./player-configs.mjs";

// Every user agent YouTube may bucket a player experiment by. `app` is EXACTLY the header the app's
// PlayerJsFetcher sends to iframe_api, so the scan always includes the request devices make.
export const UAS = {
  app: "Mozilla/5.0",
  chrome: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  firefox: "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
  safari: "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
  edge: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
  android: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
  ios: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
  tv: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
};
const DESKTOP = ["chrome", "firefox", "safari", "edge"];
const LOCALES = [["en", "US"], ["iw", "IL"], ["en", "GB"], ["de", "DE"], ["fr", "FR"], ["es", "MX"], ["pt", "BR"], ["ja", "JP"], ["ru", "RU"], ["ar", "SA"]];

// A stable, always-available public video for the watch/embed surfaces. The player served is
// bucketed by session/experiment, not by which video — so any reliable id surfaces the same canary.
const PROBE_VID = "dQw4w9WgXcQ";

// Every page YouTube serves a player from. iframe_api is what the app reads, so it gets the full
// `requested` count (the app's exact UA among the rotation); every other surface is an early
// warning — a canary often A/B-shows on one of them before it reaches iframe_api, and the mobile
// web (player-plasma) and TV (tv-player-ias) players are separate families.
export const SURFACES = [
  { name: "iframe_api", primary: true, uas: ["app", ...DESKTOP, "android", "ios"], url: () => "https://www.youtube.com/iframe_api" },
  { name: "music", uas: [...DESKTOP, "android"], url: (hl, gl) => `https://music.youtube.com/?hl=${hl}&gl=${gl}` },
  { name: "watch", uas: DESKTOP, url: (hl, gl) => `https://www.youtube.com/watch?v=${PROBE_VID}&hl=${hl}&gl=${gl}` },
  { name: "home", uas: DESKTOP, url: (hl, gl) => `https://www.youtube.com/?hl=${hl}&gl=${gl}` },
  { name: "embed", uas: DESKTOP, url: (hl) => `https://www.youtube.com/embed/${PROBE_VID}?hl=${hl}` },
  { name: "embed-nocookie", uas: DESKTOP, url: (hl) => `https://www.youtube-nocookie.com/embed/${PROBE_VID}?hl=${hl}` },
  { name: "mobile", uas: ["android", "ios"], url: (hl, gl) => `https://m.youtube.com/watch?v=${PROBE_VID}&hl=${hl}&gl=${gl}` },
  { name: "tv", uas: ["tv"], url: () => "https://www.youtube.com/tv" },
];

// iframe_api JSON-escapes slashes (\/s\/player\/HASH\/); the HTML pages do not.
const PLAYER_RE = /\\?\/s\\?\/player\\?\/([a-z0-9]{8})\\?\//g;

/** Every distinct player hash a page references (a page can carry more than one). Pure. */
export function hashesFrom(body) {
  return [...new Set([...body.matchAll(PLAYER_RE)].map((m) => m[1]))];
}

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

/**
 * Requests for one surface per run. A canary can be bucketed by user agent, so rotation must not
 * thin any agent out: the primary surface (the app's source) gets the full `requested` count PER
 * agent, every other surface at least 3 per agent and at least max(6, requested/3) in all. Pure.
 */
export function samplesFor(surface, requested) {
  if (surface.primary) return requested * surface.uas.length;
  return Math.max(6, Math.ceil(requested / 3), 3 * surface.uas.length);
}

/** The surfaces that returned no player at all: the scan cannot see there. Pure. */
export function blindSurfaces(surfaces) {
  return Object.entries(surfaces).filter(([, s]) => s.ok === 0).map(([name]) => name);
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

// node:https, not fetch: fetch caps response headers at 16 KB and YouTube's pages crossed that on
// 2026-10-30 (a ~7 KB connection-allowlist-report-only header), which blinded every sample. A failure
// is returned as { error }, never swallowed into an empty "nothing seen". Cookies set along a
// redirect chain are sent on the next hop (a consent/region redirect loops without them).
function fetchText(url, ua, hops = 5, cookies = {}) {
  return new Promise((resolve) => {
    const headers = { "User-Agent": ua };
    const jar = Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join("; ");
    if (jar) headers.Cookie = jar;
    const req = https.get(url, { headers, maxHeaderSize: 1 << 20, timeout: 20000 }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        const next = new URL(res.headers.location, url);
        if (hops === 0) {
          resolve({ body: "", error: `HTTP ${res.statusCode} -> ${next.host}${next.pathname}` });
          return;
        }
        for (const c of res.headers["set-cookie"] ?? []) {
          const [pair] = c.split(";");
          const eq = pair.indexOf("=");
          if (eq > 0) cookies[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
        }
        resolve(fetchText(next.href, ua, hops - 1, cookies));
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        resolve({ body: "", error: `HTTP ${res.statusCode}` });
        return;
      }
      res.setEncoding("utf8");
      let body = "";
      res.on("data", (c) => (body += c));
      res.on("end", () => resolve({ body }));
      res.on("error", (e) => resolve({ body: "", error: e.code || e.message }));
    });
    req.on("timeout", () => req.destroy(new Error("timeout")));
    req.on("error", (e) => resolve({ body: "", error: e.code || e.message }));
  });
}

// md5-of-first-10000-bytes alias + sts for an unknown hash — the same alias identity the app
// (FunctionNameExtractor.extractPlayerHash) computes.
async function playerIdentity(hash) {
  for (const locale of ["en_GB", "en_US"]) {
    const { body: js } = await fetchText(`https://www.youtube.com/s/player/${hash}/player_ias.vflset/${locale}/base.js`, UAS.chrome);
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

  const samples = []; // one entry per hash per page
  const surfaces = {};
  let page = 0;
  for (const surface of SURFACES) {
    const stats = (surfaces[surface.name] = { requests: 0, ok: 0, hashes: {}, errors: {} });
    const n = samplesFor(surface, requested);
    for (let i = 0; i < n; i++, page++) {
      const ua = UAS[surface.uas[i % surface.uas.length]];
      const [hl, gl] = LOCALES[page % LOCALES.length];
      const { body, error } = await fetchText(surface.url(hl, gl), ua);
      const found = hashesFrom(body);
      stats.requests++;
      if (found.length) stats.ok++;
      else {
        const why = error || "no player reference";
        stats.errors[why] = (stats.errors[why] || 0) + 1;
      }
      for (const h of found) {
        samples.push(h);
        stats.hashes[h] = (stats.hashes[h] || 0) + 1;
      }
    }
  }

  const distinct = aggregate(samples);
  const unknown = [];
  for (const d of distinct) {
    if (covered.has(d.hash)) continue;
    const { md5, sts } = await playerIdentity(d.hash);
    // An md5-only match is NOT covered: devices look the player up by its URL hash
    // (PlayerJsFetcher -> getHardcodedConfig) and never consult the md5. Report it as unknown
    // so it gets its own alias entry (the 2026-10-06 f2999a12 outage).
    unknown.push({ hash: d.hash, count: d.count, md5, sts });
  }

  const blind = blindSurfaces(surfaces);
  console.log(JSON.stringify({ samples: samples.length, requested, distinct, unknown, surfaces, blind }, null, 2));
  const lines = Object.entries(surfaces).map(([name, s]) =>
    `  ${name}: ${s.ok}/${s.requests} ` +
      Object.entries(s.hashes).map(([h, c]) => `${h}=${c}${covered.has(h) ? "" : "(NEW)"}`).join(" ") +
      (Object.keys(s.errors).length ? ` errors: ${Object.entries(s.errors).map(([e, c]) => `${e} x${c}`).join(", ")}` : ""),
  );
  process.stderr.write(
    `scanned ${samples.length} samples -> ${distinct.length} distinct: ` +
      distinct.map((d) => `${d.hash}=${d.count}${covered.has(d.hash) ? "" : "(NEW)"}`).join(" ") +
      `\n${lines.join("\n")}\n` +
      (unknown.length
        ? `UNKNOWN: ${unknown.map((u) => `${u.hash} (seen ${u.count}x, md5 ${u.md5}, sts ${u.sts})`).join("; ")}\n`
        : "all covered\n") +
      (blind.length ? `BLIND: no player from ${blind.join(", ")}\n` : ""),
  );
  if (blind.length) process.exit(3);
}

// Only run main when invoked directly (not when imported by the unit test).
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
