// build-feed.mjs — the "latest kosher releases" feed builder (the VPS job, validated in tests/ first).
//
// For every whitelisted artist: read the recency-sorted discography grids (Albums + Singles), look at
// the TOP N entries, and for any release within the WINDOW that we haven't already stored, fetch its
// precise date (uploadDate from /player microformat) and add it. Output a small JSON sorted newest-
// first. Incremental: a release already in the prior feed is skipped (and, because the grid is
// recency-sorted, hitting a known/old entry stops scanning that grid). Entries older than the window
// are purged.
//
// Date source (proven by probe-dates/probe-order): /player microformat.uploadDate, full ISO-8601,
// returned with visitorData ONLY (no cookie). So this runs session-free.
//
// Usage:
//   node tests/recent-releases/build-feed.mjs                       # full run (slow on phone; run on VPS)
//   LIMIT=20 WINDOW=400 node tests/recent-releases/build-feed.mjs   # small validation sample, wide window
//   WINDOW=7 TOP=2 CONCURRENCY=6 OUT=feed.json node ...build-feed.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cred, browse, player, artistReleases, artistItemsGrid, albumTracks, findDateFields, biggestThumbnail } from "./lib.mjs";
import { fetchWhitelist, whitelistVersion } from "./whitelist.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WINDOW_DAYS = Number(process.env.WINDOW || 7);
const TOP = Number(process.env.TOP || 2);
const CONCURRENCY = Number(process.env.CONCURRENCY || 6);
const LIMIT = process.env.LIMIT ? Number(process.env.LIMIT) : null;
const OUT = process.env.OUT
  ? (path.isAbsolute(process.env.OUT) ? process.env.OUT : path.join(HERE, process.env.OUT))
  : path.join(HERE, "feed.json");
const cutoff = Date.now() - WINDOW_DAYS * 86400_000;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.error(...a); // logs to stderr so stdout can stay clean if piped

// release date = uploadDate of the album's first track (album browse -> first track -> player).
async function releaseDate(browseId, cr) {
  const al = await browse({ browseId }, cr);
  if (al.blocked) return { blocked: true };
  const tracks = al.json ? albumTracks(al.json) : [];
  const track = tracks[0] ?? null;
  if (!track) return { date: null };
  const albumThumb = al.json ? biggestThumbnail(al.json) : null;
  const pl = await player({ videoId: track.videoId }, cr);
  if (pl.blocked) return { blocked: true };
  const f = findDateFields(pl.json || {}).find((x) => /uploadDate|publishDate/.test(x.path));
  return { date: f?.value ?? null, sampleVideoId: track.videoId, albumThumb, trackCount: tracks.length };
}

// Candidate releases for one artist: top TOP of each discography grid (recency-sorted), de-duped.
async function artistCandidates(artist, cr) {
  const a = await browse({ browseId: artist.id }, cr);
  if (a.blocked) return { blocked: true };
  if (a.http !== 200 || !a.json) return { items: [] };

  const onPage = artistReleases(a.json);
  // collect distinct Albums/Singles "more" endpoints
  const more = new Map();
  for (const r of onPage) if (r.moreEndpoint) more.set(r.section, r.moreEndpoint);

  const seen = new Set();
  const items = [];
  const take = (list) => {
    let n = 0;
    for (const it of list) {
      if (n >= TOP) break;
      if (seen.has(it.browseId)) continue;
      seen.add(it.browseId);
      items.push(it);
      n++;
    }
  };

  if (more.size) {
    for (const ep of more.values()) {
      const g = await browse({ browseId: ep.browseId, params: ep.params }, cr);
      if (g.blocked) return { blocked: true };
      if (g.json) take(artistItemsGrid(g.json));
    }
  } else {
    // no "see all" grid — releases are inline on the artist page; use those (already newest-first-ish)
    take(onPage.map((r) => ({ browseId: r.browseId, title: r.title, playlistId: r.playlistId, thumbnail: null, yearGuess: r.yearGuess })));
  }
  return { items, artistName: artist.name };
}

async function main() {
  const t0 = Date.now();
  const cr = await cred();
  const version = await whitelistVersion();
  let artists = (await fetchWhitelist()).filter((a) => a.id.startsWith("UC"));
  if (LIMIT) artists = artists.slice(0, LIMIT);
  log(`whitelist v${version}: ${artists.length} channels  window=${WINDOW_DAYS}d top=${TOP} concurrency=${CONCURRENCY}`);

  // incremental: load prior feed, keep only entries still in window as the baseline
  let prior = [];
  try { prior = JSON.parse(fs.readFileSync(OUT, "utf8")).releases ?? []; } catch {}
  const known = new Map(); // browseId -> entry
  for (const e of prior) if (new Date(e.uploadDate).getTime() >= cutoff) known.set(e.browseId, e);

  const results = new Map(known); // start from carried-forward entries
  let done = 0, added = 0, blocked = 0, errors = 0;

  // simple concurrency pool over artists
  let idx = 0;
  async function worker() {
    while (idx < artists.length) {
      const artist = artists[idx++];
      try {
        let res = await artistCandidates(artist, cr);
        if (res.blocked) { blocked++; await sleep(3000); res = await artistCandidates(artist, cr); }
        for (const it of res.items ?? []) {
          if (results.has(it.browseId)) continue; // already known/added
          const { date, sampleVideoId, albumThumb, trackCount, blocked: b } = await releaseDate(it.browseId, cr);
          if (b) { blocked++; await sleep(3000); continue; }
          if (!date) continue;
          if (new Date(date).getTime() < cutoff) continue; // older than window
          results.set(it.browseId, {
            artistId: artist.id, artistName: artist.name, title: it.title,
            browseId: it.browseId, playlistId: it.playlistId,
            thumbnail: it.thumbnail || albumThumb || `https://i.ytimg.com/vi/${sampleVideoId}/hqdefault.jpg`,
            year: Number.parseInt(it.yearGuess, 10) || null, uploadDate: date, trackCount, sampleVideoId,
          });
          added++;
        }
      } catch (e) { errors++; log(`  ! ${artist.name}: ${e.message}`); }
      if (++done % 25 === 0) log(`  ${done}/${artists.length}  (+${added} releases, ${blocked} blocks, ${errors} errs)`);
      await sleep(40 + Math.random() * 60); // gentle throttle / jitter
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));

  const releases = [...results.values()]
    .filter((e) => new Date(e.uploadDate).getTime() >= cutoff)
    .sort((a, b) => new Date(b.uploadDate) - new Date(a.uploadDate));

  const feed = { generatedAt: new Date().toISOString(), whitelistVersion: version, windowDays: WINDOW_DAYS, count: releases.length, releases };
  fs.writeFileSync(OUT, JSON.stringify(feed, null, 2));
  const secs = ((Date.now() - t0) / 1000).toFixed(1);
  log(`\ndone in ${secs}s: ${releases.length} releases in window  (added ${added}, ${blocked} blocks, ${errors} errs)`);
  log(`wrote ${OUT}`);
  for (const r of releases.slice(0, 15)) log(`   ${r.uploadDate}  ${r.artistName} — ${r.title}`);
}

main();
