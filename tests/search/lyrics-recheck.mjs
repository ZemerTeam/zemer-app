// Re-probe the songs in .cache/lyrics-coverage.json serially (SimpMusic rate-limits bursts to 429),
// and cross-check LRCLIB by title+artist. Writes .cache/lyrics-recheck.json.
import fs from "node:fs";
const FILE = new URL("./.cache/lyrics-coverage.json", import.meta.url);
const LIMIT = Number(process.env.LIMIT || 200), DELAY = Number(process.env.DELAY || 1200);
const rows = JSON.parse(fs.readFileSync(FILE, "utf8")).slice(0, LIMIT);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const cls = (d, keys) => { for (const [k, c] of keys) if (d.some((t) => (t[k] || "").trim())) return c; return "NONE"; };

async function simp(id) {
  for (let attempt = 0; attempt < 6; attempt++) {
    const r = await fetch(`https://api-lyrics.simpmusic.org/v1/${id}`, { headers: { Accept: "application/json" } });
    if (r.status === 429) { await sleep(5000 * (attempt + 1)); continue; }
    if (r.status === 404) return "NONE";
    if (r.status !== 200) return `HTTP${r.status}`;
    const j = await r.json();
    return cls(j?.data || [], [["richSyncLyrics", "WORD"], ["syncedLyrics", "LINE"], ["plainLyric", "PLAIN"]]);
  }
  return "RATELIMIT";
}
async function lrclib(title, artist) {
  const u = new URL("https://lrclib.net/api/search");
  u.searchParams.set("track_name", title); u.searchParams.set("artist_name", artist);
  const r = await fetch(u, { headers: { "User-Agent": "zemer-coverage-probe/1.0" } });
  if (r.status !== 200) return `HTTP${r.status}`;
  const d = await r.json();
  return cls(d, [["syncedLyrics", "LINE"], ["plainLyrics", "PLAIN"]]);
}
const out = [];
for (const r of rows) {
  const s = await simp(r.id);
  const l = await lrclib(r.title, r.artist.split(" - ")[0]);
  out.push({ ...r, simp: s, lrclib: l });
  if (out.length % 10 === 0) console.error(`progress ${out.length}/${rows.length}`);
  await sleep(DELAY);
}
const t = (k) => out.reduce((m, r) => (m[r[k]] = (m[r[k]] || 0) + 1, m), {});
console.log(`songs=${out.length}`);
console.log("simpmusic:", t("simp"));
console.log("lrclib:   ", t("lrclib"));
const hits = out.filter((r) => r.simp !== "NONE" || (r.lrclib !== "NONE" && !r.lrclib.startsWith("HTTP")));
console.log("\nany hit:");
for (const r of hits) console.log(`  simp=${r.simp} lrclib=${r.lrclib}  ${r.artist} — ${r.title} (${r.id})`);
fs.writeFileSync(new URL("./.cache/lyrics-recheck.json", import.meta.url), JSON.stringify(out, null, 1));
