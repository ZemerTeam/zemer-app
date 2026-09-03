import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { titleForms, norm, openCorpus } from "./jyrics-common.mjs";
const db = openCorpus();
const key = (s) => norm(s).replace(/['’`]/g, "").replace(/\bh\b/g, "").replace(/ch|kh/g, "k").replace(/th/g, "t").replace(/ei|ai|ey|ay/g, "e").replace(/oi|oy/g, "o").replace(/ou|oo/g, "u").replace(/tz|ts|z/g, "s").replace(/w/g, "v").replace(/([a-z])\1/g, "$1").replace(/[aeiou]/g, "").replace(/\s+/g, " ").trim();
const S = process.env.S; const tree = JSON.parse(fs.readFileSync(`${S}/drive_tree.json`, "utf8"));
const done = new Set(JSON.parse(fs.readFileSync(new URL("./.cache/booklets-corpus-resolved.json", import.meta.url), "utf8")).map((r) => r.artist));
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); const byId = new Map(wl.map((a) => [a.id, a.name]));
const albums = db.prepare("select id,title from album where artistId=?"); const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sc = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const out = {}; let probed = 0, wlHit = 0;
for (const [art, v] of Object.entries(tree)) {
  if (done.has(art)) continue; const titles = v.files.filter(([, f]) => f.toLowerCase().endsWith(".pdf")).map(([, f]) => f.split("/").pop().replace(/\.pdf$/i, "").replace(/^\s*(19|20)\d\d\s*-\s*/, ""));
  try {
    const { json } = await postSearch({ query: art.split(/\s+-\s+/)[0].replace(/_/g, " "), params: FILTERS.FILTER_ARTIST, visitorData }); probed++;
    const ids = sc(json).flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]).map((c) => c.musicResponsiveListItemRenderer?.navigationEndpoint?.browseEndpoint?.browseId).filter((id) => id && byId.has(id)).slice(0, 3);
    if (ids.length) wlHit++;
    for (const id of ids) { const albKeys = new Set(albums.all(id).flatMap((a) => titleForms(a.title).map(key)).filter((k) => k.length >= 4)); const evidence = titles.filter((t) => titleForms(t).map(key).some((k) => albKeys.has(k))); if (evidence.length) { out[art] = { id, wlName: byId.get(id), evidence: evidence.slice(0, 3) }; break; } }
  } catch {}
  await sleep(300);
}
console.log(`folders probed=${probed} whitelisted-channel-hit=${wlHit} accepted-with-album-evidence=${Object.keys(out).length}`);
Object.entries(out).slice(0, 30).forEach(([k, v]) => console.log(`  ${k} -> ${v.wlName} | ${v.evidence[0]}`));
fs.writeFileSync(`${S}/drive_resolve_evidence.json`, JSON.stringify(out, null, 1));
