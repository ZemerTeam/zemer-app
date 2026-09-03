// Does SimpMusic (the app's first lyrics provider) have anything for the whitelisted catalog?
// For N whitelisted artists, run the app's FILTER_SONG search for the name, keep up to K songs
// credited to that exact channel id, and ask api-lyrics.simpmusic.org for each videoId.
// Classifies each song as NONE / PLAIN / LINE / WORD (best body available).
//
//   N=120 K=3 CONC=6 node tests/search/lyrics-coverage.mjs
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { toYTItem } from "./parsers.mjs";

const FILE = new URL("./.cache/whitelist.json", import.meta.url);
const N = Number(process.env.N || 120), K = Number(process.env.K || 3), CONC = Number(process.env.CONC || 6);

const raw = JSON.parse(fs.readFileSync(FILE, "utf8")).filter((x) => x && x.id && x.name);
const stride = Math.max(1, Math.floor(raw.length / N));
const artists = []; for (let i = 0; i < raw.length && artists.length < N; i += stride) artists.push(raw[i]);

const sectionContents = (j) =>
  j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const mrlirs = (contents) => contents.flatMap((s) =>
  [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]
    .map((c) => c.musicResponsiveListItemRenderer).filter(Boolean));

async function simp(videoId) {
  try {
    const r = await fetch(`https://api-lyrics.simpmusic.org/v1/${videoId}`, { headers: { Accept: "application/json" } });
    if (r.status !== 200) return { cls: "NONE", http: r.status };
    const j = await r.json();
    const d = j?.data || [];
    if (j?.type !== "success" || !d.length) return { cls: "NONE" };
    if (d.some((t) => (t.richSyncLyrics || "").trim())) return { cls: "WORD" };
    if (d.some((t) => (t.syncedLyrics || "").trim())) return { cls: "LINE" };
    if (d.some((t) => (t.plainLyric || "").trim())) return { cls: "PLAIN" };
    return { cls: "NONE" };
  } catch (e) { return { cls: "ERR", err: String(e) }; }
}

const { visitorData } = await cred();
const rows = []; let noSongs = 0, searchErr = 0;
let i = 0;
async function worker() {
  while (i < artists.length) {
    const a = artists[i++];
    let json;
    try { ({ json } = await postSearch({ query: a.name, params: FILTERS.FILTER_SONG, visitorData })); }
    catch { searchErr++; continue; }
    const songs = mrlirs(sectionContents(json)).map(toYTItem).filter((x) => x.ok && x.kind === "song")
      .map((x) => x.item).filter((s) => s.artists.some((ar) => ar.id === a.id)).slice(0, K);
    if (!songs.length) { noSongs++; continue; }
    for (const s of songs) rows.push({ artist: a.name, title: s.title, id: s.id, ...(await simp(s.id)) });
  }
}
await Promise.all(Array.from({ length: CONC }, worker));

const tally = {}; for (const r of rows) tally[r.cls] = (tally[r.cls] || 0) + 1;
const byArtist = new Map();
for (const r of rows) { const m = byArtist.get(r.artist) || {}; m[r.cls] = (m[r.cls] || 0) + 1; byArtist.set(r.artist, m); }
const artistsWithAny = [...byArtist.values()].filter((m) => m.WORD || m.LINE || m.PLAIN).length;
const artistsWithWord = [...byArtist.values()].filter((m) => m.WORD).length;
console.log(`artists sampled=${artists.length} searched-ok=${artists.length - searchErr} with-own-songs=${byArtist.size} no-own-songs=${noSongs}`);
console.log(`songs probed=${rows.length}`, tally);
console.log(`artists with any lyrics=${artistsWithAny}/${byArtist.size}  with word-sync=${artistsWithWord}/${byArtist.size}`);
console.log("\nWORD-sync hits:");
for (const r of rows.filter((r) => r.cls === "WORD")) console.log(`  ${r.artist} — ${r.title} (${r.id})`);
console.log("\nLINE-sync hits:");
for (const r of rows.filter((r) => r.cls === "LINE")) console.log(`  ${r.artist} — ${r.title} (${r.id})`);
fs.writeFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), JSON.stringify(rows, null, 1));
