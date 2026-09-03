// OFFLINE resolution of external lyric sources against the zemer-search corpus (whitelist-pure by
// construction). (a) Jyrics songs -> corpus track: artist by whitelisted channel id, title by
// transliteration-tolerant forms. (b) Booklet PDFs -> corpus album: artist by Hebrew/English name
// (artist.name / altName), album title by the same matcher.
import fs from "node:fs";
import { titleForms, norm, openCorpus, importZemerSearch } from "./jyrics-common.mjs";
const db = openCorpus();
const { sameSongScore, hebKey, latKey } = await importZemerSearch("corpus/lyrics.mjs");
const S = process.env.S;
// transliteration-tolerant key: lowercase, strip niqqud/punct, collapse common Ashkenazi/Sephardi and
// spelling variants so "Achas Shoalti"/"Achat Sha'alti", "Kel Elyon"/"Keil Elyon" meet.
const key = (s) => norm(s).replace(/['’`]/g, "").replace(/\bh\b/g, "").replace(/ch|kh/g, "k").replace(/th/g, "t").replace(/ei|ai|ey|ay/g, "e").replace(/oi|oy/g, "o").replace(/ou|oo/g, "u").replace(/tz|ts|z/g, "s").replace(/w/g, "v").replace(/([a-z])\1/g, "$1").replace(/[aeiou]/g, "").replace(/\s+/g, " ").trim();
const keys = (t) => new Set(titleForms(t).map(key).filter((k) => k.length >= 3));
// Score 1.0 = a normalised title form is IDENTICAL (either script) — rock solid.
// Score 0.9 = consonant keys equal AND the key is >=5 chars (short skeletons like "trs" collide).
// Anything else is not a match (goes to the review queue).
function bestTitle(cands, title) { let best = null, bs = 0; for (const c of cands) for (const f of [c.title, c.altTitle]) { if (!f) continue; const s = sameSongScore(title, f); if (s > bs) { bs = s; best = c; } } return { best, score: bs }; }
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); const idByName = new Map(wl.map((a) => [a.name, a.id]));
const tracksByArtist = db.prepare("select videoId,title,altTitle,isVideo from track where artistId=?");
const albumsByArtist = db.prepare("select id,title,type,year from album where artistId=?");
// (a) Jyrics
const jy = JSON.parse(fs.readFileSync(new URL("./.cache/jyrics.json", import.meta.url), "utf8")).concat(fs.existsSync(new URL("./.cache/jyrics-extra.json", import.meta.url)) ? JSON.parse(fs.readFileSync(new URL("./.cache/jyrics-extra.json", import.meta.url), "utf8")) : []);
const extra = JSON.parse(fs.readFileSync(`${S}/jy_resolve2.json`, "utf8"));
const t = { songs: 0, artistInCorpus: 0, resolved: 0, exact: 0, keyed5: 0 }; const out = [];
for (const a of jy) {
  const aid = idByName.get(a.wlName) || extra[a.slug.replace(/-/g, " ")]?.id; if (!aid) { t.songs += a.songs.length; continue; }
  const tracks = tracksByArtist.all(aid); if (!tracks.length) { t.songs += a.songs.length; continue; }
  for (const s of a.songs) { t.songs++; t.artistInCorpus++; const { best, score } = bestTitle(tracks, s.title); if (best && score >= 0.85) { t.resolved++; if (score === 1) t.exact++; else t.keyed5++; out.push({ artist: a.wlName, jy: s.title, videoId: best.videoId, yt: best.title, score, url: s.url }); } }
}
console.log("JYRICS->CORPUS", JSON.stringify(t));
fs.writeFileSync(new URL("./.cache/jyrics-corpus-resolved.json", import.meta.url), JSON.stringify(out, null, 1));
out.filter((r) => r.score < 1).slice(0, 10).forEach((r) => console.log(`  keyed5: ${r.artist} | "${r.jy}" -> "${r.yt}"`));
// (b) booklets
const artists = db.prepare("select id,name,altName from artist").all();
const nameIdx = new Map(); for (const a of artists) for (const n of [a.name, a.altName]) if (n) for (const p of n.split(/\s+-\s+|\s*\/\s*|\s*\|\s*/)) nameIdx.set(norm(p), a.id);
function lev(a, b) { const m = a.length, n = b.length; if (!m || !n) return Math.max(m, n); let prev = Array.from({ length: n + 1 }, (_, j) => j); for (let i = 1; i <= m; i++) { const cur = [i]; for (let j = 1; j <= n; j++) cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)); prev = cur; } return prev[n]; }
const hebNames = []; for (const a of artists) for (const n of [a.name, a.altName]) if (n) for (const p of n.split(/\s+-\s+|\s*\/\s*|\s*\|\s*/)) { const h = norm(p).replace(/[^א-ת ]/g, "").replace(/\s+/g, " ").trim(); if (h.length >= 4) hebNames.push([h, a.id, a.name]); }
function fuzzyHeb(q) { const h = norm(q).replace(/[^א-ת ]/g, "").replace(/\s+/g, " ").trim(); if (h.length < 4) return null; let best = null, bd = 99; for (const [n, id, name] of hebNames) { const d = lev(h, n); if (d < bd) { bd = d; best = { id, name, d }; } } return best && bd <= Math.max(1, Math.floor(h.length / 6)) ? best : null; }
const tree = JSON.parse(fs.readFileSync(`${S}/drive_tree.json`, "utf8"));
const b = { pdfs: 0, artistMatched: 0, albumResolved: 0 }; const bout = []; const unmatchedArtists = new Set(); const fuzzyLog = [];
for (const [art, v] of Object.entries(tree)) {
  let aid = art.split(/\s+-\s+|\s*\/\s*/).map((p) => nameIdx.get(norm(p.replace(/_/g, " ")))).find(Boolean); let fz = null; if (!aid) { fz = fuzzyHeb(art.replace(/_/g, " ")); if (fz) { aid = fz.id; b.artistFuzzy = (b.artistFuzzy || 0) + 1; fuzzyLog.push(`${art} ~ ${fz.name} (d=${fz.d})`); } }
  for (const [fid, fn] of v.files) {
    if (!fn.toLowerCase().endsWith(".pdf")) continue; b.pdfs++;
    if (!aid) { unmatchedArtists.add(art); continue; } b.artistMatched++;
    let title = fn.split("/").pop().replace(/\.pdf$/i, "").replace(/^\s*(19|20)\d\d\s*-\s*/, ""); const an = art.replace(/_/g, " "); if (title.startsWith(an)) title = title.slice(an.length).replace(/^\s*[-–]\s*/, "");
    const albumCands = albumsByArtist.all(aid).map((x) => ({ ...x, altTitle: null }));
    let { best, score } = bestTitle(albumCands, title);
    if (!best || score < 0.85) { // artist-gated cross-script fallback for short Hebrew album titles ("חזק" ~ "Chazak!")
      const forms = title.split(/\s*[\(\)]\s*/).map((x) => x.trim()).filter(Boolean);
      for (const c of albumCands) for (const f of forms) { const heb = /[\u05d0-\u05ea]/.test(f), lat = /[a-z]/i.test(c.title); if (heb && lat) { const hk = hebKey(f), lk = latKey(c.title.replace(/\(.*?\)|\[.*?\]|!/g, "")); if (hk.length >= 4 && hk === lk) { best = c; score = 0.85; } } }
    }
    if (best && score >= 0.85) { b.albumResolved++; bout.push({ artist: art, artistId: aid, fn, albumId: best.id, album: best.title, score }); }
  }
}
console.log("BOOKLETS->CORPUS", JSON.stringify(b), "artist folders unmatched:", unmatchedArtists.size, "of", Object.keys(tree).length);
fs.writeFileSync(new URL("./.cache/booklets-corpus-resolved.json", import.meta.url), JSON.stringify(bout, null, 1));
console.log("  fuzzy artist matches (review):", fuzzyLog.slice(0, 40).join("; "));
console.log("  unmatched folder sample:", [...unmatchedArtists].slice(0, 25).join(", "));

// (c) ground truth: resolved Jyrics songs whose videoId has YouTube lyrics -> token overlap
import { tokens } from "./jyrics-common.mjs";
const gt = JSON.parse(fs.readFileSync(new URL("./.cache/yt-lyrics-fulltext.json", import.meta.url), "utf8"));
const gtById = new Map(gt.map((g) => [g.id, g])); const overlap = (a, b2) => { const A = tokens(a), B = tokens(b2); let i = 0; for (const x of A) if (B.has(x)) i++; return i / Math.min(A.size, B.size); };
for (const r of out.filter((r) => gtById.has(r.videoId))) {
  const h = await (await fetch(r.url, { headers: { "User-Agent": "Mozilla/5.0" } })).text(); const art = h.match(/<article[\s\S]*?<\/article>/)?.[0] || h;
  const text = art.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>/g, "").replace(/<br\s*\/?>/g, "\n").replace(/<[^>]+>/g, " ");
  const ov = overlap(gtById.get(r.videoId).text, text); console.log(`GT ${r.artist} | "${r.jy}" -> ${r.videoId} overlap ${ov.toFixed(2)} ${ov >= 0.5 ? "SAME" : "DIFF"}`);
}
