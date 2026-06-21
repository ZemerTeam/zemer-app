// probe-order.mjs — answers the two feasibility questions:
//
//   (a) Is the artist DISCOGRAPHY GRID (the "Albums"/"Singles" more endpoint, = InnerTube.artistItems)
//       returned newest-first? If yes, the job only needs the top 1-2 per artist (cheap). If no, we'd
//       have to date-enrich more items.
//   (b) Does /player return microformat.uploadDate WITHOUT the cookie (visitorData only)? If yes, the
//       VPS can run with no logged-in session at all.
//
// It date-enriches the top N grid items per section and prints them in returned order so we can SEE
// whether uploadDate is descending. Hard data, not assumption.
import { cred, browse, player, artistReleases, artistItemsGrid, albumFirstTrack, findDateFields } from "./lib.mjs";

const N = Number(process.env.TOPN || 6);
const ARTIST = process.argv[2] || "UC1QsA-Q6SUKX9gWi1heQDEA"; // Joey Newcomb (frequent singles)

// release date = uploadDate of the album's first track (album browse -> first track -> player microformat)
async function releaseDate(browseId, cr, { auth = true } = {}) {
  const al = await browse({ browseId }, cr);
  const track = al.json ? albumFirstTrack(al.json) : null;
  if (!track) return { date: null, why: "no track" };
  const pl = await playerWith(track.videoId, cr, auth);
  if (pl.http !== 200 || !pl.json) return { date: null, why: `player http=${pl.http}`, videoId: track.videoId };
  const dates = findDateFields(pl.json).filter((f) => /uploadDate|publishDate/.test(f.path));
  return { date: dates[0]?.value ?? null, videoId: track.videoId };
}

// player with explicit auth flag (lib.player is always auth=true; we need a no-cookie variant for (b))
import { CLIENTS, ORIGIN } from "../clients.mjs";
import crypto from "node:crypto";
const C = CLIENTS.find((c) => c.key === "WEB_REMIX");
function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function playerWith(videoId, cr, auth) {
  const client = { clientName: C.clientName, clientVersion: C.clientVersion, hl: "en", gl: "US", visitorData: cr.visitorData };
  const h = {
    "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": C.clientId, "X-YouTube-Client-Version": C.clientVersion,
    "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": C.userAgent, "X-Goog-Visitor-Id": cr.visitorData,
  };
  if (auth && cr.cookie) { h.cookie = cr.cookie; const a = sapisidHash(cr.cookie); if (a) h.Authorization = a; }
  const res = await fetch(`${ORIGIN}/youtubei/v1/player?prettyPrint=false`, {
    method: "POST", headers: h, body: JSON.stringify({ context: { client }, videoId, contentCheckOk: true, racyCheckOk: true }),
  });
  const txt = await res.text();
  let json = null; try { json = JSON.parse(txt); } catch {}
  return { http: res.status, json };
}

async function checkOrder(label, browseId, params, cr) {
  console.log(`\n--- ${label} grid (${browseId}${params ? " +params" : ""}) ---`);
  const g = await browse({ browseId, params }, cr);
  if (g.http !== 200 || !g.json) { console.log(`  grid browse FAILED http=${g.http}`); return; }
  const items = artistItemsGrid(g.json);
  if (!items.length) { console.log("  no grid items parsed; keys:", Object.keys(g.json?.contents ?? {})); return; }
  console.log(`  ${items.length} items; date-enriching top ${N} (in returned order):`);
  const rows = [];
  for (const it of items.slice(0, N)) {
    const { date } = await releaseDate(it.browseId, cr);
    rows.push({ title: it.title, year: it.yearGuess, date });
    console.log(`    ${String(date).padEnd(28)}  year=${String(it.year).padEnd(6)}  ${it.title}`);
  }
  const dated = rows.map((r) => r.date).filter(Boolean);
  const descending = dated.every((d, i) => i === 0 || d <= dated[i - 1]);
  console.log(`  -> newest-first? ${descending ? "YES (uploadDate descending)" : "NO (not sorted by uploadDate)"}`);
}

async function main() {
  const cr = await cred();
  console.log(`cred: visitorData=${cr.visitorData ? "yes" : "NO"} cookie=${cr.cookie ? "yes" : "NO"}`);

  // (b) cookie requirement test
  console.log("\n=== (b) does /player return uploadDate WITHOUT cookie? ===");
  const a = await browse({ browseId: ARTIST }, cr);
  const first = artistReleases(a.json)[0];
  if (first) {
    const al = await browse({ browseId: first.browseId }, cr);
    const track = albumFirstTrack(al.json);
    if (track) {
      const withCookie = await playerWith(track.videoId, cr, true);
      const noCookie = await playerWith(track.videoId, cr, false);
      const d = (pl) => findDateFields(pl.json || {}).find((f) => /uploadDate/.test(f.path))?.value ?? null;
      console.log(`  videoId ${track.videoId}`);
      console.log(`  with cookie : http=${withCookie.http}  uploadDate=${d(withCookie)}`);
      console.log(`  NO cookie   : http=${noCookie.http}  uploadDate=${d(noCookie)}`);
    }
  }

  // (a) grid ordering test — find Albums + Singles more endpoints from the artist page
  console.log("\n=== (a) is the discography grid newest-first? ===");
  const releases = artistReleases(a.json);
  const sections = new Map();
  for (const r of releases) if (r.moreEndpoint && !sections.has(r.section)) sections.set(r.section, r.moreEndpoint);
  if (!sections.size) console.log("  (no 'more' endpoints on artist page — sections may be inline only)");
  for (const [label, ep] of sections) await checkOrder(label, ep.browseId, ep.params, cr);

  console.log("\ndone.");
}

main();
