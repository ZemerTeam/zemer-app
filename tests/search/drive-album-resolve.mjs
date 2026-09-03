// Map each booklet PDF in the Drive archive to a YouTube Music ALBUM owned by a whitelisted channel.
// Gate: FILTER_ALBUM search "<album title> <artist>", accept only if the album's artist id is
// whitelisted AND album title matches (exact normalised form or jaccard >= 0.6).
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { toYTItem } from "./parsers.mjs";
import { titleMatch, norm } from "./jyrics-common.mjs";
const tree = JSON.parse(fs.readFileSync(process.env.TREE, "utf8"));
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); const byId = new Map(wl.map((a) => [a.id, a.name]));
const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sc = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const albumsOf = (j) => sc(j).flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).map(toYTItem).filter((x) => x.ok && x.kind === "album").map((x) => x.item);
function albumTitle(artist, fn) {
  let t = fn.split("/").pop().replace(/\.pdf$/i, "").replace(/^\s*(19|20)\d\d\s*-\s*/, "");
  const a = artist.replace(/[_]/g, " "); if (t.startsWith(a)) t = t.slice(a.length).replace(/^\s*[-–]\s*/, "");
  return t.trim();
}
const t = { pdfs: 0, accepted: 0, wlAlbumTitleMiss: 0, noWlAlbum: 0, noResults: 0 }; const rows = [];
for (const [artist, v] of Object.entries(tree)) for (const [fid, fn] of v.files) {
  if (!fn.toLowerCase().endsWith(".pdf")) continue; t.pdfs++;
  const title = albumTitle(artist, fn); const forms = title.split(/\s*[\(\)]\s*/).filter((x) => x.trim());
  try {
    const { json } = await postSearch({ query: `${forms[0]} ${artist.replace(/_/g, " ")}`, params: FILTERS.FILTER_ALBUM, visitorData });
    const albums = albumsOf(json); const wlAlbums = albums.filter((a) => a.artists.some((x) => byId.has(x.id)));
    let best = null, bs = 0; for (const a of wlAlbums) { const s = Math.max(...forms.map((f) => titleMatch(f, a.title))); if (s > bs) { bs = s; best = a; } }
    if (best && bs >= 0.6) { t.accepted++; rows.push({ artist, fn, album: best.title, albumId: best.id, channel: byId.get(best.artists.find((x) => byId.has(x.id)).id), score: +bs.toFixed(2) }); }
    else if (wlAlbums.length) { t.wlAlbumTitleMiss++; rows.push({ artist, fn, nearest: wlAlbums[0].title, score: +bs.toFixed(2) }); }
    else if (albums.length) t.noWlAlbum++; else t.noResults++;
  } catch (e) { rows.push({ artist, fn, err: String(e).slice(0, 50) }); }
  if (t.pdfs % 100 === 0) console.error(JSON.stringify(t));
  await sleep(350);
}
console.log(JSON.stringify(t)); fs.writeFileSync(new URL("./.cache/drive-albums.json", import.meta.url), JSON.stringify(rows, null, 1));
console.log("ACCEPTED sample:"); rows.filter((r) => r.albumId).slice(0, 20).forEach((r) => console.log(`  ${r.artist} | ${r.fn} -> "${r.album}" [${r.channel}] ${r.score}`));
console.log("TITLE-MISS sample:"); rows.filter((r) => r.nearest).slice(0, 12).forEach((r) => console.log(`  ${r.artist} | ${r.fn} ~ "${r.nearest}" ${r.score}`));
