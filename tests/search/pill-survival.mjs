// Why do most search "pills" (filters) show nothing in the app, even when YouTube returns results?
// The app runs every search result through zemer's whitelist filter (WhitelistFilter.kt), which keeps
// an item only if one of its artist IDs is in the whitelist. This probe measures, per pill, how many
// of YouTube's results would SURVIVE that filter — replicating the filter's id match against the real
// whitelist. If YouTube returns items but ~none survive, the whitelist (id mismatch / sync) is why the
// pill looks empty; if items survive but the app still shows nothing, the whitelist isn't synced.
//
//   node tests/search/pill-survival.mjs            # default sample of whitelisted artist names
//   N=20 node tests/search/pill-survival.mjs
import fs from "node:fs";
import { postSearch, cred, FILTERS, getItems } from "./lib.mjs";
import { toYTItem } from "./parsers.mjs";

const FILE = new URL("./.cache/whitelist.json", import.meta.url);
const N = Number(process.env.N || 12);
const CONC = Number(process.env.CONC || 4);

const wl = JSON.parse(fs.readFileSync(FILE, "utf8")).filter((x) => x && x.id && x.name);
const allowed = new Set(wl.map((a) => a.id));                 // the whitelist id set
const sample = (() => {
  const stride = Math.max(1, Math.floor(wl.length / N));
  const out = [];
  for (let i = 0; i < wl.length && out.length < N; i += stride) out.push(wl[i]);
  return out;
})();

const sectionContents = (j) =>
  j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? null;

// The ids the whitelist filter checks for each item kind (WhitelistFilter.kt).
function relevantIds(item) {
  if (!item) return [];
  if (item.type === "song" || item.type === "album") return (item.artists || []).map((a) => a.id).filter(Boolean);
  if (item.type === "artist") return [item.id].filter(Boolean);
  if (item.type === "playlist") return [item.author?.id].filter(Boolean);
  return [];
}
const survives = (item) => relevantIds(item).some((id) => allowed.has(id));

const cov = {}; // pill -> { items, survive, artistsWithItems, artistsWithSurvivor }
function bump(pill, items, survivors) {
  const c = (cov[pill] ||= { items: 0, survive: 0, artistsWithItems: 0, artistsWithSurvivor: 0 });
  c.items += items; c.survive += survivors;
  if (items > 0) c.artistsWithItems++;
  if (survivors > 0) c.artistsWithSurvivor++;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// One request, waiting out the anti-bot block (poll up to ~20 min) and spacing to avoid re-tripping it.
async function search(args) {
  for (let i = 0; i < 14; i++) {
    const r = await postSearch(args);
    if (!r.blocked) { await sleep(2500); return r; }
    process.stderr.write(`\r  rate-limited; waiting 90s (try ${i + 1})   `);
    await sleep(90000);
  }
  return { json: null, blocked: true };
}

async function probe(name, vd) {
  // each filter pill
  for (const [pill, params] of Object.entries(FILTERS)) {
    const { json } = await search({ query: name, params, visitorData: vd });
    const contents = sectionContents(json) || [];
    const items = [];
    for (const s of contents) {
      if (s.musicShelfRenderer) items.push(...getItems(s.musicShelfRenderer.contents).map(toYTItem).filter((r) => r.ok).map((r) => r.item));
      else if (s.itemSectionRenderer) items.push(...(s.itemSectionRenderer.contents || []).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).map(toYTItem).filter((r) => r.ok).map((r) => r.item));
    }
    bump(pill.replace("FILTER_", ""), items.length, items.filter(survives).length);
  }
}

async function pool(items, conc, fn) {
  let i = 0;
  await Promise.all(Array.from({ length: conc }, async () => { while (i < items.length) await fn(items[i++]); }));
}

const { visitorData } = await cred();
console.log(`whitelist ids: ${allowed.size} | sample: ${sample.length} whitelisted artists\n`);
// Sequential + spaced + block-aware (search() waits out the anti-bot) so we never burst the IP.
for (let i = 0; i < sample.length; i++) {
  await probe(sample[i].name, visitorData);
  process.stderr.write(`\r  probed ${i + 1}/${sample.length}                      `);
}
process.stderr.write("\n");

console.log("pill".padEnd(22), "YT items", "survive whitelist", "artists w/ items", "artists w/ a survivor");
for (const pill of ["SONG", "VIDEO", "ALBUM", "ARTIST", "FEATURED_PLAYLIST", "COMMUNITY_PLAYLIST"]) {
  const c = cov[pill]; if (!c) continue;
  const sr = c.items ? Math.round((100 * c.survive) / c.items) : 0;
  console.log(
    pill.padEnd(22),
    String(c.items).padStart(8),
    `${String(c.survive).padStart(6)} (${sr}%)`.padStart(17),
    `${c.artistsWithItems}/${sample.length}`.padStart(16),
    `${c.artistsWithSurvivor}/${sample.length}`.padStart(21),
  );
}
console.log("\nReading: 'artists w/ a survivor' is how many of these whitelisted-artist searches would");
console.log("show >=1 result in the app for that pill. Low here = the whitelist filter empties the pill.");
