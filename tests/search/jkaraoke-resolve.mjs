// jkaraoke.com feed (line-timed lyrics) -> corpus tracks, offline. Gates: artist -> whitelisted corpus
// artist (name/altName/Hebrew name exact), title (sameSongScore >= 0.9 on title/title_hebrew), duration
// (|karaoke - corpus| <= DUR_TOL s), and text agreement with an existing Jyrics row when present.
import fs from "node:fs";
import { createRequire } from "node:module";
const require = createRequire("/home/asternheim/zemer-search/package.json"); const Database = require("better-sqlite3");
const db = new Database("/home/asternheim/zemer-search/data/corpus.db", { readonly: true });
import { sameSongScore, normTitle, lyricsOverlap } from "/home/asternheim/zemer-search/corpus/lyrics.mjs";
const songs = JSON.parse(fs.readFileSync(`${process.env.S}/jk_all_songs.json`, "utf8")); const DUR_TOL = Number(process.env.DUR_TOL || 4);
const artists = db.prepare("SELECT id,name,altName FROM artist").all(); const nameIdx = new Map();
for (const a of artists) for (const n of [a.name, a.altName]) if (n) for (const p of n.split(/\s+-\s+|\s*\/\s*|\s*\|\s*/)) nameIdx.set(normTitle(p), a.id);
const tracksOf = db.prepare("SELECT videoId,title,altTitle,durationSec,isVideo FROM track WHERE artistId=?"), lyr = db.prepare("SELECT plain,source FROM lyrics WHERE videoId=?");
const t = { songs: songs.length, artistMatched: 0, titleMatched: 0, durationOk: 0, textAgree: 0, textConflict: 0, noExisting: 0 }; const out = [], conflicts = [], durFail = [];
const jkArtists = new Map();
for (const s of songs) {
  const names = [s.artist?.name, ...(s.collaborating_artists || []).map((c) => c.name)].filter(Boolean);
  let aid = null; for (const n of names) { aid = nameIdx.get(normTitle(n)); if (aid) break; }
  jkArtists.set(s.artist?.name, aid || jkArtists.get(s.artist?.name) || null);
  if (!aid) continue; t.artistMatched++;
  const cands = tracksOf.all(aid); let best = null, bs = 0;
  for (const c of cands) for (const f of [c.title, c.altTitle]) if (f) for (const q of [s.title, s.title_hebrew]) if (q) { const sc = sameSongScore(q, f); if (sc > bs) { bs = sc; best = c; } }
  if (!best || bs < 0.9) continue; t.titleMatched++;
  // duration gate: pick the artist's version whose duration is closest among title matches
  const same = cands.filter((c) => [c.title, c.altTitle].some((f) => f && [s.title, s.title_hebrew].some((q) => q && sameSongScore(q, f) >= 0.9)));
  const closest = same.map((c) => ({ c, d: Math.abs((c.durationSec || 0) - s.duration) })).sort((a, b) => a.d - b.d)[0];
  if (!closest || closest.d > DUR_TOL) { durFail.push({ jk: s.title, artist: s.artist.name, jkDur: s.duration, corpus: same.map((c) => `${c.title}:${c.durationSec}`).slice(0, 3) }); continue; }
  t.durationOk++;
  const text = s.lyrics.map((l) => l.text).join("\n"); const ex = lyr.get(closest.c.videoId);
  let verdict = "unverified"; if (ex) { const ov = lyricsOverlap(ex.plain, text); if (ov >= 0.5) { t.textAgree++; verdict = "agree"; } else if (ov < 0.3) { t.textConflict++; verdict = "conflict"; conflicts.push({ jk: s.title, artist: s.artist.name, ov: +ov.toFixed(2), jkHead: text.split("\n").slice(0, 2).join(" / ").slice(0, 80), exHead: ex.plain.split("\n").filter(Boolean).slice(0, 2).join(" / ").slice(0, 80) }); } else verdict = "partial"; } else t.noExisting++;
  out.push({ jkId: s.id, jk: s.title, artist: s.artist.name, videoId: closest.c.videoId, yt: closest.c.title, durDelta: closest.d, lines: s.lyrics.length, verdict });
}
console.log(JSON.stringify(t));
console.log(`jkaraoke artists=${jkArtists.size} resolved-to-whitelist=${[...jkArtists.values()].filter(Boolean).length}`);
console.log("duration-gate failures (sample):"); durFail.slice(0, 8).forEach((d) => console.log("  " + JSON.stringify(d)));
console.log("text conflicts:"); conflicts.slice(0, 10).forEach((c) => console.log("  " + JSON.stringify(c)));
console.log("resolved sample:"); out.slice(0, 8).forEach((r) => console.log("  " + JSON.stringify(r)));
fs.writeFileSync(new URL("./.cache/jkaraoke-resolved.json", import.meta.url), JSON.stringify(out, null, 1));
console.log("unresolved jkaraoke artists (sample):", [...jkArtists].filter(([, v]) => !v).map(([k]) => k).slice(0, 40).join(", "));
