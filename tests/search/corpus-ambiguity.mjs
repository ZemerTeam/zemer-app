// How often is an exact-key Jyrics match ambiguous (>=2 distinct titles under the same artist share
// the consonant key)? Ambiguous = cannot be trusted without review.
import fs from "node:fs";
import { titleForms, norm, openCorpus } from "./jyrics-common.mjs";
const db = openCorpus();
const key = (s) => norm(s).replace(/['’`]/g, "").replace(/\bh\b/g, "").replace(/ch|kh/g, "k").replace(/th/g, "t").replace(/ei|ai|ey|ay/g, "e").replace(/oi|oy/g, "o").replace(/ou|oo/g, "u").replace(/tz|ts|z/g, "s").replace(/w/g, "v").replace(/([a-z])\1/g, "$1").replace(/[aeiou]/g, "").replace(/\s+/g, " ").trim();
const res = JSON.parse(fs.readFileSync(new URL("./.cache/jyrics-corpus-resolved.json", import.meta.url), "utf8")).filter((r) => r.score === 1);
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); const idByName = new Map(wl.map((a) => [a.name, a.id]));
const q = db.prepare("select videoId,title,altTitle from track where artistId=?"); let amb = 0, variants = 0, distinct = 0; const ex = []; const strip = (f) => norm(f.replace(/\(.*?\)|\[.*?\]|official.*|music video.*|live.*|remix.*|feat.*|ft\..*|acapella.*|vocal.*|ווקאלי.*|8th day|יום השמיני|-/gi, "")).trim();
for (const r of res) {
  const aid = idByName.get(r.artist); if (!aid) continue; const K = new Set(titleForms(r.jy).map(key));
  const hits = new Map(); for (const t of q.all(aid)) for (const f of [t.title, t.altTitle]) if (f && titleForms(f).map(key).some((k) => K.has(k))) hits.set(t.videoId, f);
  if (hits.size >= 2) { amb++; const base = new Set([...hits.values()].map(strip).filter(Boolean)); if (base.size <= 1) variants++; else { distinct++; if (ex.length < 12) ex.push(`${r.artist} | "${r.jy}" ~ ${[...hits.values()].join(" / ")}`); } }
}
console.log(`exact matches=${res.length} multi-track=${amb} of which same-song variants=${variants} genuinely distinct titles=${distinct}`); ex.forEach((e) => console.log("  " + e));
