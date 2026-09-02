// End-to-end yield + accuracy of Jyrics -> whitelisted video id. For N sampled Jyrics songs of
// whitelisted artists: FILTER_SONG search "title artist", accept only a result whose artist ids
// include the whitelisted channel id AND whose title matches (exact form, or jaccard >= 0.6).
// Control: the same search with a shuffled/wrong title must NOT be accepted (false-accept rate).
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { toYTItem } from "./parsers.mjs";
import { loadWhitelist, loadJyrics, titleMatch, norm } from "./jyrics-common.mjs";
const N = Number(process.env.N || 150);
const wl = loadWhitelist(); const idByName = new Map(wl.map((a) => [a.name, a.id]));
const all = loadJyrics().flatMap((a) => a.songs.map((s) => ({ ...s, artist: a.wlName, artistId: idByName.get(a.wlName), slug: a.slug })));
const stride = Math.max(1, Math.floor(all.length / N)); const sample = []; for (let i = 0; i < all.length && sample.length < N; i += stride) sample.push(all[i]);
const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sc = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const songsOf = (j) => sc(j).flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).map(toYTItem).filter((x) => x.ok && x.kind === "song").map((x) => x.item);
async function resolve(title, s) {
  const q = `${title} ${s.artist.split(" - ")[0]}`;
  const { json } = await postSearch({ query: q, params: FILTERS.FILTER_SONG, visitorData });
  const songs = songsOf(json);
  const channelHits = songs.filter((x) => x.artists.some((a) => a.id === s.artistId));
  let best = null, bestScore = 0;
  for (const x of channelHits) { const sc = titleMatch(title, x.title); if (sc > bestScore) { bestScore = sc; best = x; } }
  return { songs: songs.length, channelHits: channelHits.length, best, bestScore, accepted: best && bestScore >= 0.6 ? best : null };
}
const t = { sampled: sample.length, resolved: 0, channelHitNoTitle: 0, noChannelHit: 0, noResults: 0, controlFalseAccept: 0, controlTried: 0 }; const rows = [];
for (const s of sample) {
  try {
    const r = await resolve(s.title, s);
    if (r.accepted) t.resolved++; else if (r.channelHits) t.channelHitNoTitle++; else if (r.songs) t.noChannelHit++; else t.noResults++;
    rows.push({ artist: s.artist, jy: s.title, yt: r.accepted?.title || null, id: r.accepted?.id || null, score: +r.bestScore.toFixed(2), nearest: r.best?.title || null });
    // control: wrong title (words reversed + a nonsense token) for the same artist
    const wrong = norm(s.title).split(" ").reverse().join(" ") + " zqx";
    const c = await resolve(wrong, s); t.controlTried++; if (c.accepted) t.controlFalseAccept++;
  } catch (e) { rows.push({ artist: s.artist, jy: s.title, err: String(e).slice(0, 60) }); }
  await sleep(350);
}
console.log(JSON.stringify(t));
console.log("UNRESOLVED (channel hit but title gate failed) examples:");
rows.filter((r) => !r.id && r.nearest).slice(0, 15).forEach((r) => console.log(`  ${r.artist} | jy="${r.jy}" nearest="${r.nearest}" score=${r.score}`));
console.log("RESOLVED examples:");
rows.filter((r) => r.id).slice(0, 15).forEach((r) => console.log(`  ${r.artist} | jy="${r.jy}" -> "${r.yt}" (${r.id}) score=${r.score}`));
import fs from "node:fs"; fs.writeFileSync(new URL("./.cache/jyrics-resolved.json", import.meta.url), JSON.stringify(rows, null, 1));
