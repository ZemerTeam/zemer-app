// Whitelist-driven coverage probe. Runs the app's ONE remaining InnerTube search function -
// YouTube.search(query, filter) - over a large sample of REAL whitelisted artist names and
// aggregates, across all of them: strict-deserialization breaks (the whole-response killer), parser
// drops, and per-filter coverage (results vs YouTube "No results" vs the dangerous "sections present
// but we parsed 0" bucket).
//
// Names come from a JSON file — an array of strings or of objects with a `name` field. NO secrets
// live here; fetch the whitelist into that file separately.
//
//   node tests/search/coverage.mjs [names.json]   # default tests/search/.cache/whitelist.json, N=120 even-stride sample
//   N=300 node tests/search/coverage.mjs          # bigger sample
//   N=ALL CONC=6 node tests/search/coverage.mjs   # every name (slow)
//   FILTERS=FILTER_ALBUM node tests/search/coverage.mjs   # restrict to one/some filters (comma-sep)
import fs from "node:fs";
import { postSearch, cred, FILTERS as ALL_FILTERS, getItems } from "./lib.mjs";
import { validate } from "./schema.mjs";
import { toYTItem } from "./parsers.mjs";

const FILE = process.argv[2] || new URL("./.cache/whitelist.json", import.meta.url);
const N = process.env.N || "120";
const CONC = Number(process.env.CONC || 5);
const FILTER_KEYS = (process.env.FILTERS ? process.env.FILTERS.split(",") : Object.keys(ALL_FILTERS)).filter((k) => ALL_FILTERS[k]);

function loadNames() {
  const raw = JSON.parse(fs.readFileSync(FILE, "utf8"));
  const names = raw.map((x) => (typeof x === "string" ? x : x?.name)).filter((s) => s && s.trim());
  if (N === "ALL") return names;
  const want = Math.min(Number(N), names.length);
  const stride = Math.max(1, Math.floor(names.length / want));
  const out = [];
  for (let i = 0; i < names.length && out.length < want; i += stride) out.push(names[i]);
  return out;
}

const sectionContents = (j) =>
  j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? null;
const isNoResults = (contents) => {
  const s = contents || [];
  if (!s.length) return false;
  return s.every((sec) => {
    const inner = sec.itemSectionRenderer?.contents || sec.musicShelfRenderer?.contents || [];
    return inner.length > 0 && inner.every((it) => it.messageRenderer != null || it.musicResponsiveListItemRenderer == null);
  });
};

// Aggregators
const strict = new Map();   // "Type.field:why" -> { type, field, why, count, artists:Set, example }
const unencoded = new Set();
const drops = new Map();    // "fn reason" -> count
const parseThrows = [];     // { artist, fn, msg }
const http = new Map();     // status -> count
const cover = {};           // fnKey -> { items, noResults, suspicious, empty, wouldBreak }
const bucket = (fn, b) => { (cover[fn] ||= { items: 0, noResults: 0, suspicious: 0, empty: 0, wouldBreak: 0 })[b]++; };
const addDrop = (fn, reason) => drops.set(`${fn} | ${reason}`, (drops.get(`${fn} | ${reason}`) || 0) + 1);

function runStrict(json, rootType, artist) {
  const ctx = validate(json, rootType);
  for (const v of ctx.violations) {
    const k = `${v.type}.${v.field}:${v.why}`;
    const e = strict.get(k) || { type: v.type, field: v.field, why: v.why, count: 0, artists: new Set(), example: v.path };
    e.count++; e.artists.add(artist); strict.set(k, e);
  }
  for (const u of ctx.unencoded) unencoded.add(u);
  return ctx.violations.length > 0;
}

// Parse a list of results-objects ({ok,reason}) into counts, recording drops.
function tally(fn, results, artist) {
  let ok = 0;
  for (const r of results) {
    if (r == null) continue;
    if (r.ok) ok++;
    else { addDrop(fn, `${r.kind}: ${r.reason}`); if (/^threw:/.test(r.reason)) parseThrows.push({ artist, fn, msg: r.reason }); }
  }
  return ok;
}
const safe = (fn, artist, parseFn) => { try { return parseFn(); } catch (e) { parseThrows.push({ artist, fn, msg: e.message }); return { ok: false, kind: "?", reason: `threw: ${e.message}` }; } };

function classify(fn, status, json, contents, parsedCount, wouldBreak) {
  if (status !== 200) { bucket(fn, "empty"); http.set(status, (http.get(status) || 0) + 1); return; }
  if (wouldBreak) { bucket(fn, "wouldBreak"); return; }
  if (parsedCount > 0) bucket(fn, "items");
  else if (isNoResults(contents)) bucket(fn, "noResults");
  else if ((contents || []).some((s) => s.musicShelfRenderer || s.itemSectionRenderer || s.musicCardShelfRenderer)) bucket(fn, "suspicious");
  else bucket(fn, "empty");
}

async function probe(artist, vd) {
  // ---- search(filter) x N ----
  for (const fk of FILTER_KEYS) {
    try {
      const { status, json } = await postSearch({ query: artist, params: ALL_FILTERS[fk], visitorData: vd });
      const wb = runStrict(json, "SearchResponse", artist);
      const contents = sectionContents(json);
      const results = [];
      for (const sec of contents || []) {
        if (sec.musicShelfRenderer) {
          results.push(...getItems(sec.musicShelfRenderer.contents).map((r) => safe(fk, artist, () => toYTItem(r))));
        } else if (sec.itemSectionRenderer) {
          results.push(...(sec.itemSectionRenderer.contents || []).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).map((r) => safe(fk, artist, () => toYTItem(r))));
        }
      }
      classify(fk, status, json, contents, tally(fk, results, artist), wb);
    } catch (e) { parseThrows.push({ artist, fn: fk, msg: e.message }); }
  }
}

async function pool(items, conc, fn) {
  let i = 0, done = 0;
  const tick = () => { done++; if (done % 10 === 0 || done === items.length) process.stderr.write(`\r  probed ${done}/${items.length} artists`); };
  const workers = Array.from({ length: conc }, async () => {
    while (i < items.length) { const idx = i++; await fn(items[idx]); tick(); }
  });
  await Promise.all(workers);
  process.stderr.write("\n");
}

async function main() {
  const { visitorData, source } = await cred();
  const names = loadNames();
  console.log(`names file: ${FILE}  | sample: ${names.length}  | filters: ${FILTER_KEYS.join(",")}  | conc: ${CONC}  | cred: ${source ? "yes" : "no"}`);
  console.log(`requests per artist: ${FILTER_KEYS.length} (one YouTube.search per filter - the app's only InnerTube search entry point)\n`);

  await pool(names, CONC, (n) => probe(n, visitorData));

  console.log(`\n${"=".repeat(86)}\nCOVERAGE  (per function: items / noResults / suspicious / empty / wouldBreak)\n${"=".repeat(86)}`);
  const order = [...FILTER_KEYS];
  for (const fn of order) {
    const c = cover[fn]; if (!c) continue;
    const tot = c.items + c.noResults + c.suspicious + c.empty + c.wouldBreak;
    console.log(`  ${fn.padEnd(26)} items=${String(c.items).padStart(4)}  noResults=${String(c.noResults).padStart(4)}  suspicious=${String(c.suspicious).padStart(3)}  empty=${String(c.empty).padStart(4)}  wouldBreak=${String(c.wouldBreak).padStart(3)}  (n=${tot})`);
  }

  console.log(`\n${"=".repeat(86)}\nERRORS\n${"=".repeat(86)}`);
  if (strict.size === 0) console.log("  strict-parse breaks: NONE (no whitelisted artist's response omitted a non-null field)");
  else {
    console.log(`  strict-parse breaks: ${strict.size} distinct field(s) — EACH fails the WHOLE response for the artists hitting it:`);
    for (const e of [...strict.values()].sort((a, b) => b.artists.size - a.artists.size))
      console.log(`    * ${e.type}.${e.field} (${e.why}) — ${e.artists.size} artist(s), ${e.count} hit(s)  e.g. "${[...e.artists][0]}"  @ ${e.example}`);
  }
  console.log(`  parser throws: ${parseThrows.length}${parseThrows.length ? "  e.g. " + parseThrows.slice(0, 3).map((x) => `${x.artist}/${x.fn}: ${x.msg}`).join(" ; ") : ""}`);
  if (http.size) console.log(`  non-200 HTTP: ${[...http.entries()].map(([s, n]) => `${s}×${n}`).join(", ")}`);
  if (unencoded.size) console.log(`  note: un-encoded subtrees seen (not strict-checked): ${[...unencoded].join(", ")}`);

  const suspicious = order.filter((fn) => cover[fn]?.suspicious > 0);
  if (suspicious.length) console.log(`\n  SUSPICIOUS (YouTube returned result sections but we parsed 0 — possible parse bug): ${suspicious.map((fn) => `${fn}×${cover[fn].suspicious}`).join(", ")}`);

  console.log(`\n${"=".repeat(86)}\nTOP PARSER DROPS (aggregate)\n${"=".repeat(86)}`);
  for (const [k, n] of [...drops.entries()].sort((a, b) => b[1] - a[1]).slice(0, 15)) console.log(`  ${String(n).padStart(5)}  ${k}`);

  const killers = strict.size + parseThrows.length;
  console.log(`\nVERDICT: ${killers === 0 ? "no whole-response killers across the sample — search layer is healthy for whitelisted content." : `${killers} whole-response killer condition(s) found — see ERRORS above.`}`);
  process.exit(killers === 0 ? 0 : 1);
}
main().catch((e) => { console.error(e); process.exit(1); });
