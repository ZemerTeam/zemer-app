// Exercises the app's ONE remaining InnerTube search function - YouTube.search(query, filter) - against
// live YouTube Music, exactly as the app does (RecognitionResolver, Android Auto voice search and the
// add-to-playlist online search all call it), and reports any error: a strict-deserialization break
// (a non-null field YouTube stopped sending -> whole response fails to parse -> "No results"), a
// parser drop (item silently lost, with the exact field that caused it), or an unexpectedly empty
// result. (searchSuggestions/searchSummary/searchContinuation were removed from the app - Zemer is
// the only search engine for the UI - so their probes are gone from this harness too.)
//
//   node tests/search/run.mjs                       # default query set, all filters
//   node tests/search/run.mjs "mordechai shapiro"   # one custom query
//   node tests/search/run.mjs q1 q2 q3 ...           # several custom queries
//   SAVE=1 node tests/search/run.mjs                # also dump raw JSON responses to tests/search/out/
//
// Exit code: 0 = no whole-response killers found; 1 = a strict break was found.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { postSearch, cred, FILTERS, getItems, getShelfContinuation, getContinuation } from "./lib.mjs";
import { validate } from "./schema.mjs";
import { toYTItem } from "./parsers.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const QUERIES = process.argv.slice(2);
const DEFAULT_QUERIES = ["mordechai shapiro", "simcha", "yaakov shwekey", "baruch levine", "uncle moishy"];
const SAVE = !!process.env.SAVE;

const C = (s) => s; // (color hook left plain so logs stay grep-friendly)
const pct = (n, d) => (d === 0 ? "—" : `${Math.round((100 * n) / d)}%`);

// Critical findings (whole-response killers) accumulate here; they drive the exit code.
const critical = [];

// Run a parser but never let one malformed item abort the whole sweep: a throw becomes a reported
// drop (and a critical finding — the app would crash the same way if the parser is reached).
function safe(label, fn) {
  try { return fn(); }
  catch (e) { critical.push(`${label} parser threw: ${e.message}`); return { ok: false, kind: "?", reason: `threw: ${e.message}` }; }
}

// True if a result section is just YouTube's "No results for X." placeholder (a messageRenderer),
// i.e. the emptiness is YouTube's answer, not an app parse failure.
function isNoResults(contents) {
  const sections = contents || [];
  if (sections.length === 0) return false;
  return sections.every((s) => {
    const inner = s.itemSectionRenderer?.contents || s.musicShelfRenderer?.contents || [];
    return inner.length > 0 && inner.every((it) => it.messageRenderer != null || it.musicResponsiveListItemRenderer == null);
  });
}

function reportStrict(label, json, rootType) {
  const ctx = validate(json, rootType);
  // One missing non-null field repeats across every item; dedupe to the distinct (type.field:why).
  const byKey = new Map();
  for (const v of ctx.violations) {
    const k = `${v.type}.${v.field}:${v.why}`;
    const e = byKey.get(k) || { ...v, count: 0, examples: [] };
    e.count++; if (e.examples.length < 2) e.examples.push(v.path);
    byKey.set(k, e);
  }
  if (byKey.size === 0) {
    console.log(`    strict-parse: OK (${ctx.visited} objects validated, no missing non-null fields)`);
  } else {
    console.log(`    strict-parse: ${C("BREAK")} — ${ctx.violations.length} missing non-null field(s) -> WHOLE RESPONSE FAILS TO PARSE in the app:`);
    for (const e of byKey.values()) {
      console.log(`      * ${e.type}.${e.field} (${e.why}) x${e.count}  e.g. ${e.examples[0]}`);
      critical.push(`[${label}] strict break: ${e.type}.${e.field} (${e.why})`);
    }
  }
  if (ctx.unencoded.size) {
    console.log(`    strict-parse: note — un-encoded subtree(s) present, not strict-checked: ${[...ctx.unencoded].join(", ")}`);
  }
  return ctx;
}

function tallyDrops(results) {
  const drops = results.filter((r) => !r.ok);
  const byReason = new Map();
  for (const d of drops) {
    const k = `${d.kind}: ${d.reason}`;
    byReason.set(k, (byReason.get(k) || 0) + 1);
  }
  return { drops, byReason };
}

function printItemsAndDrops(indent, results) {
  const okItems = results.filter((r) => r.ok).map((r) => r.item);
  const { drops, byReason } = tallyDrops(results);
  const kinds = {};
  for (const r of results.filter((r) => r.ok)) kinds[r.kind] = (kinds[r.kind] || 0) + 1;
  console.log(`${indent}parsed: ${okItems.length}/${results.length} (${pct(okItems.length, results.length)}) ${JSON.stringify(kinds)}`);
  if (drops.length) {
    console.log(`${indent}dropped: ${drops.length} (each = an item the app would NOT show)`);
    for (const [reason, n] of [...byReason.entries()].sort((a, b) => b[1] - a[1])) {
      console.log(`${indent}  - ${reason}  x${n}`);
    }
  }
  return okItems;
}

function save(name, json) {
  if (!SAVE) return;
  const dir = path.join(HERE, "out");
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, name), JSON.stringify(json, null, 2));
}

const sectionContents = (j) =>
  j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? null;

// ---- YouTube.search(filter) --------------------------------------------------------------------
async function runFiltered(query, filterName, visitorData) {
  const params = FILTERS[filterName];
  console.log(`\n  [search filter=${filterName}] "${query}"`);
  const { status, json } = await postSearch({ query, params, visitorData });
  save(`filter_${filterName}_${query}.json`.replace(/\s+/g, "_"), json);
  console.log(`    HTTP ${status}`);
  reportStrict(`${filterName} "${query}"`, json, "SearchResponse");

  const contents = sectionContents(json);
  if (contents == null) { console.log(`    ${C("NO sectionListRenderer")}`); critical.push(`[${filterName} "${query}"] no sectionListRenderer`); return; }

  const results = [];
  let continuation = null;
  for (const section of contents) {
    if (section.musicShelfRenderer) {
      results.push(...getItems(section.musicShelfRenderer.contents).map((r) => safe(`${filterName} "${query}"`, () => toYTItem(r))));
      if (continuation == null) {
        continuation = getContinuation(section.musicShelfRenderer.continuations) ?? getShelfContinuation(section.musicShelfRenderer.contents);
      }
    } else if (section.itemSectionRenderer) {
      results.push(...(section.itemSectionRenderer.contents || []).map((c) => c.musicResponsiveListItemRenderer).filter(Boolean).map((r) => safe(`${filterName} "${query}"`, () => toYTItem(r))));
    }
  }
  const items = printItemsAndDrops("    ", results);
  if (items.length === 0) {
    if (isNoResults(contents)) console.log(`    EMPTY (YouTube returned "No results") — not an app error`);
    else { console.log(`    ${C("EMPTY")} — filter ${filterName} surfaced 0 items but YouTube did NOT say "No results" (suspect a parse drop)`); critical.push(`[${filterName} "${query}"] 0 items despite results present`); }
  }
  console.log(`    continuation: ${continuation ? "present" : "none"} (the app never pages YouTube search)`);
}

async function main() {
  const { visitorData, source } = await cred();
  const queries = QUERIES.length ? QUERIES : DEFAULT_QUERIES;
  console.log(`cred=${source ? "yes" : "no"}  visitorData=${visitorData ? "yes" : "no"}  queries=${JSON.stringify(queries)}`);
  console.log("NOTE: search is unauthenticated in the app (setLogin=false) — this harness sends visitorData only, no cookie/auth.");
  console.log("NOTE: zemer's artist-whitelist filter (WhitelistFilter.kt) runs AFTER these functions in the ViewModel and");
  console.log("      drops everything whose artist isn't whitelisted. It needs the app DB, so it is OUT OF SCOPE here.");

  for (const q of queries) {
    console.log(`\n${"=".repeat(78)}\nQUERY: "${q}"\n${"=".repeat(78)}`);
    for (const f of Object.keys(FILTERS)) await runFiltered(q, f, visitorData);
  }

  console.log(`\n${"=".repeat(78)}\nVERDICT\n${"=".repeat(78)}`);
  if (critical.length === 0) {
    console.log("OK — no whole-response killers found across any filter/query.");
    console.log("YouTube.search(filter) parsed live data without a strict-deserialization break or an");
    console.log("empty result. If search misbehaves in the app, the cause is downstream of this");
    console.log("function (whitelist filter / UI), not the InnerTube search layer.");
    process.exit(0);
  } else {
    console.log(`FOUND ${critical.length} whole-response killer(s) — these break search in the app:`);
    for (const c of [...new Set(critical)]) console.log(`  - ${c}`);
    process.exit(1);
  }
}
main().catch((e) => { console.error(e); process.exit(1); });
