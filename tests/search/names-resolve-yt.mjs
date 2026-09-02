// Accuracy-gated name -> whitelisted channel resolver: YouTube Music artist search for each name;
// accept only if a top-2 result's channel id is whitelisted AND its displayed name matches the query
// (normalised equality, or the query's Hebrew/English part equals one part of the result name).
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
const names = fs.readFileSync(process.env.NAMES, "utf8").split("\n").map((s) => s.trim()).filter(Boolean);
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); const byId = new Map(wl.map((a) => [a.id, a.name]));
const norm = (s) => (s || "").normalize("NFKD").replace(/[֑-ׇ]/g, "").toLowerCase().replace(/[^a-z0-9א-ת]/g, "");
const parts = (n) => n.split(/\s+-\s+|\s*\/\s*|\s*\|\s*|\s*&\s*/).map(norm).filter((p) => p.length >= 3);
const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sc = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const out = {}; let probed = 0, channelOnly = 0;
for (const name of names) {
  try {
    const { json } = await postSearch({ query: name.replace(/\s+-\s+.*$/, ""), params: FILTERS.FILTER_ARTIST, visitorData }); probed++;
    const items = sc(json).flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])]).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).slice(0, 2)
      .map((r) => ({ id: r.navigationEndpoint?.browseEndpoint?.browseId, title: r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.text || "" }));
    const wlHit = items.find((t) => byId.has(t.id));
    if (!wlHit) continue;
    const q = parts(name), w = parts(byId.get(wlHit.id)).concat(parts(wlHit.title));
    if (q.some((p) => w.includes(p))) out[name] = { wlName: byId.get(wlHit.id), id: wlHit.id, ytTitle: wlHit.title }; else channelOnly++;
  } catch {}
  await sleep(300);
}
console.error(`probed=${probed} accepted=${Object.keys(out).length} rejected-name-mismatch=${channelOnly}`);
console.log(JSON.stringify(out, null, 1));
