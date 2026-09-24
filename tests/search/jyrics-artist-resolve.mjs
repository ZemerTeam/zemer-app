// For jyrics artist slugs that did NOT match a whitelist name textually, ask YouTube Music's artist
// search for the slug and accept the artist only if the top result's channel id is whitelisted.
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
const slugs = fs.readFileSync(process.env.SLUGS, "utf8").split("\n").map((s) => s.trim()).filter(Boolean);
const overlap = JSON.parse(fs.readFileSync(process.env.OVERLAP, "utf8"));
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8"));
const byId = new Map(wl.map((a) => [a.id, a.name]));
const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sectionContents = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const found = {}; let probed = 0;
for (const slug of slugs.filter((s) => !overlap[s])) {
  const name = slug.replace(/-/g, " ");
  try {
    const { json } = await postSearch({ query: name, params: FILTERS.FILTER_ARTIST, visitorData }); probed++;
    const items = sectionContents(json).flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean);
    const top = items.slice(0, 2).map((r) => ({ id: r.navigationEndpoint?.browseEndpoint?.browseId, title: r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.text }));
    const hit = top.find((t) => byId.has(t.id));
    if (hit) found[slug] = { wlName: byId.get(hit.id), id: hit.id, ytTitle: hit.title };
  } catch (e) {}
  await sleep(300);
}
console.error(`probed=${probed} resolved=${Object.keys(found).length}`);
console.log(JSON.stringify(found, null, 1));
