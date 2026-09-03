// Genius public search over the sampled whitelisted songs. Accuracy gate: accept only when the
// primary artist name normalises to the whitelisted channel name (either half of "English - Hebrew").
import fs from "node:fs";
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const LIMIT = Number(process.env.LIMIT || 370); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const norm = (s) => (s || "").normalize("NFKD").replace(/[֑-ׇ]/g, "").toLowerCase().replace(/[^a-z0-9א-ת]/g, "");
const tally = { probed: 0, returnedSongs: 0, artistMatch: 0, artistMismatchOnly: 0, http: {} }; const matches = [], mismatches = [];
for (const r of rows.slice(0, LIMIT)) {
  const halves = r.artist.split(/\s+-\s+/).map((x) => x.trim()).filter(Boolean);
  const u = new URL("https://genius.com/api/search/multi"); u.searchParams.set("q", `${r.title} ${halves[0]}`);
  let res; try { res = await fetch(u, { headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36", "Accept": "application/json, text/plain, */*", "Accept-Language": "en-US,en;q=0.9", "Referer": "https://genius.com/" } }); } catch { continue; }
  tally.probed++; if (res.status !== 200) { tally.http[res.status] = (tally.http[res.status] || 0) + 1; await sleep(8000); continue; }
  const j = await res.json(); const songs = (j?.response?.sections || []).filter((s) => s.type === "song").flatMap((s) => s.hits.map((h) => h.result));
  if (songs.length) tally.returnedSongs++;
  const wl = halves.map(norm);
  const ok = songs.find((s) => { const parts = s.primary_artist.name.split(/\s+-\s+|\s*&\s*|,\s*/).map(norm); return parts.some((p) => wl.includes(p)) && (norm(s.title).includes(norm(r.title).slice(0, 8)) || norm(r.title).includes(norm(s.title).slice(0, 8))); });
  if (ok) { tally.artistMatch++; matches.push(`${r.artist} — ${r.title} (${r.id}) => "${ok.title}" by ${ok.primary_artist.name} ${ok.url}`); }
  else if (songs.length) { tally.artistMismatchOnly++; if (mismatches.length < 12) mismatches.push(`${r.artist} — ${r.title} => top: "${songs[0].title}" by ${songs[0].primary_artist.name}`); }
  await sleep(3000);
}
console.log(JSON.stringify(tally)); console.log("MATCHES:"); matches.forEach((m) => console.log("  " + m)); console.log("MISMATCH EXAMPLES:"); mismatches.forEach((m) => console.log("  " + m));
