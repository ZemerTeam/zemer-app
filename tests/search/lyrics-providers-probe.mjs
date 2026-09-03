// Probe Metrolist's extra title/artist-keyed providers (BetterLyrics, LyricsPlus, KuGou) against the
// sampled whitelisted songs, recording what each returned so a wrong-song match is visible.
import fs from "node:fs";
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const LIMIT = Number(process.env.LIMIT || 40);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const artistOf = (r) => r.artist.split(" - ")[0];
const snip = (s) => (s || "").replace(/\s+/g, " ").slice(0, 70);

async function better(title, artist) {
  const u = new URL("https://lyrics-api.boidu.dev/getLyrics"); u.searchParams.set("s", title); u.searchParams.set("a", artist);
  const r = await fetch(u); if (r.status !== 200) return `HTTP${r.status}`;
  const j = await r.json(); const t = j.ttml || "";
  if (!t) return "NONE";
  const m = t.match(/<ttm:title>([^<]*)|itunes:songTitle="([^"]*)/); const lines = (t.match(/<p /g) || []).length;
  return `HIT lines=${lines} keys=${Object.keys(j).join(",")} text="${snip(t.replace(/<[^>]+>/g, " "))}"`;
}
async function plus(title, artist) {
  const u = new URL("https://lyricsplus.binimum.org/v2/lyrics/get"); u.searchParams.set("title", title); u.searchParams.set("artist", artist);
  const r = await fetch(u); if (r.status !== 200) return `HTTP${r.status}`;
  const j = await r.json(); const lines = j.lyrics || j.lines || [];
  if (!lines.length) return `NONE keys=${Object.keys(j).join(",")}`;
  return `HIT type=${j.type} meta=${JSON.stringify(j.metadata).slice(0, 120)} text="${snip(lines.slice(0, 3).map((l) => l.text).join(" / "))}"`;
}
async function kugou(title, artist) {
  const u = new URL("https://mobileservice.kugou.com/api/v3/search/song");
  for (const [k, v] of Object.entries({ version: 9108, plat: 0, pagesize: 8, showtype: 0, keyword: `${title} - ${artist}` })) u.searchParams.set(k, v);
  const r = await fetch(u); if (r.status !== 200) return `HTTP${r.status}`;
  const j = await r.json(); const info = j?.data?.info || [];
  if (!info.length) return "NONE";
  return `CANDIDATES ${info.slice(0, 3).map((s) => `"${s.songname} — ${s.singername} (${s.duration}s)"`).join("; ")}`;
}
for (const r of rows.slice(0, LIMIT)) {
  const a = artistOf(r);
  console.log(`\n## ${a} — ${r.title} (${r.id})`);
  for (const [name, fn] of [["better", better], ["plus", plus], ["kugou", kugou]]) {
    try { console.log(`  ${name}: ${await fn(r.title, a)}`); } catch (e) { console.log(`  ${name}: ERR ${String(e).slice(0, 80)}`); }
  }
  await sleep(300);
}
