// probe-dates.mjs — the make-or-break experiment for the "recent releases" feature.
//
// The feature needs a real, per-release DATE so kosher releases can be ordered "newest first" across
// artists. The InnerTube models only expose `year` (AlbumItem.year). This probe asks the live API,
// for real kosher artists, WHAT date granularity is actually obtainable — by dumping every
// date-bearing field from the artist page, the album page, and a track's /next + /player responses.
//
// Hard data only: we print what the server actually returns, not what we hope it returns.
//
// Usage:
//   node tests/recent-releases/probe-dates.mjs                 # default kosher artist sample
//   node tests/recent-releases/probe-dates.mjs UCxxxx UCyyyy   # specific artist channelIds
//   RELEASES=3 node tests/recent-releases/probe-dates.mjs      # probe top N releases per artist
import { cred, browse, next, player, artistReleases, albumFirstTrack, findDateFields } from "./lib.mjs";

// Known active kosher artists (channelIds from the live artistsWhitelist) — likely to have recent drops.
const DEFAULT_ARTISTS = [
  ["Joey Newcomb", "UC1QsA-Q6SUKX9gWi1heQDEA"],
  ["Avraham Fried", "UC1-uPfCV7ZcLCSou9v_Vclw"],
  ["Pey Dalid", "UCBMDYwogwSLZRYxRbJq5GUw"],
  ["Eli Herzlich", "UC8xpsBCWwcPpaRr1fmVs1wA"],
];

const N = Number(process.env.RELEASES || 2);

function show(label, fields) {
  if (!fields.length) { console.log(`    ${label}: (no date-like fields)`); return; }
  // de-dup by value, keep shortest path
  const byVal = new Map();
  for (const f of fields) {
    const cur = byVal.get(f.value);
    if (!cur || f.path.length < cur.path.length) byVal.set(f.value, f);
  }
  console.log(`    ${label}:`);
  for (const f of byVal.values()) console.log(`      ${f.value}   <- ${f.path}`);
}

async function probeArtist(name, channelId, cr) {
  console.log(`\n=== ${name}  (${channelId}) ===`);
  const a = await browse({ browseId: channelId }, cr);
  if (a.http !== 200 || !a.json) { console.log(`  artist browse FAILED http=${a.http} blocked=${a.blocked}`); return; }

  const releases = artistReleases(a.json);
  if (!releases.length) { console.log("  no MPRE* releases found on artist page (sections:", Object.keys(a.json?.contents ?? {}), ")"); return; }

  console.log(`  ${releases.length} releases on artist page; probing top ${N}:`);
  for (const rel of releases.slice(0, N)) {
    console.log(`\n  • "${rel.title}"  [${rel.section}]  year=${rel.yearGuess}  ${rel.browseId}`);
    // album page
    const al = await browse({ browseId: rel.browseId }, cr);
    if (al.http === 200 && al.json) show("album page", findDateFields(al.json));
    else console.log(`    album page FAILED http=${al.http}`);

    const track = al.json ? albumFirstTrack(al.json) : null;
    if (!track) { console.log("    (no track videoId found — cannot probe /next or /player)"); continue; }
    console.log(`    first track: "${track.title}"  ${track.videoId}`);

    const nx = await next({ videoId: track.videoId, playlistId: rel.playlistId }, cr);
    if (nx.http === 200 && nx.json) show("/next", findDateFields(nx.json));
    else console.log(`    /next FAILED http=${nx.http} blocked=${nx.blocked}`);

    const pl = await player({ videoId: track.videoId }, cr);
    if (pl.http === 200 && pl.json) show("/player", findDateFields(pl.json));
    else console.log(`    /player FAILED http=${pl.http} blocked=${pl.blocked}`);
  }
}

async function main() {
  const cr = await cred();
  console.log(`cred source: ${cr.source}  visitorData=${cr.visitorData ? "yes" : "NO"}  cookie=${cr.cookie ? "yes" : "NO"}`);
  const args = process.argv.slice(2);
  const artists = args.length ? args.map((id) => [id, id]) : DEFAULT_ARTISTS;
  for (const [name, id] of artists) {
    try { await probeArtist(name, id, cr); }
    catch (e) { console.log(`  ERROR probing ${name}: ${e.message}`); }
  }
  console.log("\ndone.");
}

main();
