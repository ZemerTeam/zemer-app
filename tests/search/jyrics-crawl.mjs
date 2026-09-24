// Crawl jyrics.com for the artists whose slug exactly matches a whitelisted artist name
// (scratchpad jy_overlap.json from the earlier overlap check). Phase 1: artist pages -> song list.
// Phase 2: every song page -> album, header line, script of the lyrics (hebrew/latin/mixed), line count.
// Output: tests/search/.cache/jyrics.json
import fs from "node:fs";
const OVERLAP = process.env.OVERLAP; const OUT = new URL(process.env.OUT || "./.cache/jyrics.json", import.meta.url);
const overlap = JSON.parse(fs.readFileSync(OVERLAP, "utf8"));
const UA = { "User-Agent": "Mozilla/5.0 (zemer lyrics coverage study)" };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const unesc = (s) => s.replace(/&#8217;|&rsquo;/g, "'").replace(/&#8211;|&ndash;/g, "–").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&#(\d+);/g, (_, n) => String.fromCharCode(n));
async function get(u) { for (let i = 0; i < 3; i++) { try { const r = await fetch(u, { headers: UA, signal: AbortSignal.timeout(20000) }); if (r.status === 200) return await r.text(); if (r.status === 404) return null; console.error(`http ${r.status} ${u}`); } catch (e) { console.error(`err ${String(e).slice(0, 50)} ${u}`); } await sleep(1500); } return null; }
const artists = [];
for (const [slug, wlName] of Object.entries(overlap)) {
  const h = await get(`https://www.jyrics.com/artist/${slug}/`);
  const songs = [...(h || "").matchAll(/href="(https:\/\/www\.jyrics\.com\/lyrics\/[^"]+)">([^<]+)</g)].map((m) => ({ url: m[1], title: unesc(m[2]).trim() }));
  const seen = new Set(); const uniq = songs.filter((s) => !seen.has(s.url) && seen.add(s.url));
  artists.push({ slug, wlName, songs: uniq });
  if (artists.length % 25 === 0) console.error(`phase1: ${artists.length} artists`);
  await sleep(150);
}
console.error(`phase1: ${artists.length} artists, ${artists.reduce((n, a) => n + a.songs.length, 0)} songs`);
fs.writeFileSync(OUT, JSON.stringify(artists));
let done = 0; const queue = artists.flatMap((a) => a.songs);
async function worker() { while (queue.length) { const s = queue.shift(); await song(s); } }
async function song(s) {
  const h = await get(s.url);
  if (h) {
    const art = h.match(/<article[\s\S]*?<\/article>/)?.[0] || h;
    const text = unesc(art.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>/g, "").replace(/<br\s*\/?>/g, "\n").replace(/<\/(p|div|h\d|li)>/g, "\n").replace(/<[^>]+>/g, ""));
    const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
    const i = lines.findIndex((l) => /^LYRIC$/i.test(l)); const j = lines.findIndex((l, k) => k > i && /^Other Songs from/i.test(l));
    const body = lines.slice(i >= 0 ? i + 1 : 0, j > 0 ? j : undefined).filter((l) => !/^(Print|-->|SHARE|Added by|admin)$/.test(l));
    s.header = body[0] || ""; const lyr = body.slice(1);
    const heb = lyr.filter((l) => /[֐-׿]/.test(l)).length, lat = lyr.filter((l) => /[A-Za-z]{3,}/.test(l) && !/[֐-׿]/.test(l)).length;
    s.lines = lyr.length; s.script = lyr.length === 0 ? "empty" : heb && lat ? "mixed" : heb ? "hebrew" : "latin";
    s.album = h.match(/href="https:\/\/www\.jyrics\.com\/album\/[^"]+"[^>]*>([^<]+)</)?.[1]?.trim() || null;
  } else s.script = "fetch-failed";
  if (++done % 100 === 0) { console.error(`phase2: ${done}`); fs.writeFileSync(OUT, JSON.stringify(artists)); }
  await sleep(150);
}
await Promise.all([worker(), worker(), worker(), worker()]);
fs.writeFileSync(OUT, JSON.stringify(artists)); console.error("done");
