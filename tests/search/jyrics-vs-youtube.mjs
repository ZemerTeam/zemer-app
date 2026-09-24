// Accuracy ground truth: for YouTube-lyrics songs whose artist is on Jyrics, fetch the matched
// Jyrics lyrics and compare token overlap with YouTube's text. High overlap = same song text.
import fs from "node:fs";
import { loadJyrics, titleMatch, tokens } from "./jyrics-common.mjs";
const gt = JSON.parse(fs.readFileSync(new URL("./.cache/yt-lyrics-fulltext.json", import.meta.url), "utf8"));
const jy = loadJyrics(); const byArtist = new Map(jy.map((a) => [a.wlName, a.songs]));
const overlap = (a, b) => { const A = tokens(a), B = tokens(b); let i = 0; for (const x of A) if (B.has(x)) i++; return i / Math.min(A.size, B.size); };
const out = [];
for (const g of gt) {
  const songs = byArtist.get(g.artist); if (!songs) { out.push(`${g.artist} — ${g.title}: artist not on Jyrics`); continue; }
  let best = 0, bs = null; for (const s of songs) { const sc = titleMatch(g.title, s.title); if (sc > best) { best = sc; bs = s; } }
  if (!bs || best < 0.6) { out.push(`${g.artist} — ${g.title}: no Jyrics title match (nearest "${bs?.title}" ${best.toFixed(2)})`); continue; }
  const h = await (await fetch(bs.url, { headers: { "User-Agent": "Mozilla/5.0" } })).text();
  const art = h.match(/<article[\s\S]*?<\/article>/)?.[0] || h;
  const text = art.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>/g, "").replace(/<br\s*\/?>/g, "\n").replace(/<[^>]+>/g, " ");
  const ov = overlap(g.text, text);
  out.push(`${g.artist} — ${g.title} ~ Jyrics "${bs.title}": token overlap ${ov.toFixed(2)} (${ov >= 0.5 ? "SAME SONG" : "DIFFERENT/PARTIAL"})`);
  await new Promise((r) => setTimeout(r, 300));
}
out.forEach((l) => console.log(l));
