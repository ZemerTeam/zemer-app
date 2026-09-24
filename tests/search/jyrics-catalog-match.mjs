// Reverse coverage: for each sampled catalog song (whitelisted channel), does Jyrics list a song
// under that artist whose title matches? Reports the share of the catalog Jyrics can cover.
import fs from "node:fs";
import { loadJyrics, titleMatch } from "./jyrics-common.mjs";
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const jy = loadJyrics(); const byArtist = new Map(jy.map((a) => [a.wlName, a.songs]));
const t = { catalogSongs: rows.length, artistOnJyrics: 0, titleMatched: 0 }; const ex = [];
for (const r of rows) {
  const songs = byArtist.get(r.artist); if (!songs) continue; t.artistOnJyrics++;
  let best = 0, bt = null; for (const s of songs) { const sc = titleMatch(r.title, s.title); if (sc > best) { best = sc; bt = s.title; } }
  if (best >= 0.6) { t.titleMatched++; if (ex.length < 12) ex.push(`${r.artist} | "${r.title}" ~ "${bt}" ${best.toFixed(2)}`); }
}
console.log(JSON.stringify(t)); ex.forEach((e) => console.log("  " + e));
