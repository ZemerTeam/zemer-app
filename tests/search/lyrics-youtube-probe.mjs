// How many sampled whitelisted songs have YouTube Music's own lyrics tab (keyed by videoId)?
import fs from "node:fs";
import { cred } from "./lib.mjs";
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const LIMIT = Number(process.env.LIMIT || 60);
const { visitorData } = await cred();
const ctx = { client: { clientName: "WEB_REMIX", clientVersion: "1.20250310.01.00", hl: "en", gl: "US", visitorData } };
async function post(ep, body) {
  const r = await fetch(`https://music.youtube.com/youtubei/v1/${ep}?prettyPrint=false`, {
    method: "POST", headers: { "Content-Type": "application/json", "X-Goog-Visitor-Id": visitorData, Origin: "https://music.youtube.com", "User-Agent": "Mozilla/5.0" },
    body: JSON.stringify({ context: ctx, ...body }),
  });
  return r.status === 200 ? r.json() : null;
}
let has = 0, none = 0, err = 0; const hits = [];
for (const s of rows.slice(0, LIMIT)) {
  try {
    const n = await post("next", { videoId: s.id });
    const tabs = n?.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs || [];
    const ep = tabs.map((t) => t.tabRenderer?.endpoint?.browseEndpoint).find((e) => e?.browseId?.startsWith("MPLYt")) ?? tabs[1]?.tabRenderer?.endpoint?.browseEndpoint;
    const unselectable = tabs[1]?.tabRenderer?.unselectable === true;
    if (!ep || unselectable) { none++; continue; }
    const b = await post("browse", { browseId: ep.browseId, params: ep.params });
    const text = b?.contents?.sectionListRenderer?.contents?.[0]?.musicDescriptionShelfRenderer?.description?.runs?.[0]?.text;
    if (text) { has++; hits.push(`${s.artist} — ${s.title} (${s.id}): "${text.replace(/\s+/g, " ").slice(0, 60)}"`); } else none++;
  } catch (e) { err++; }
  await new Promise((r) => setTimeout(r, 250));
}
console.log(`youtube lyrics: has=${has} none=${none} err=${err} of ${Math.min(LIMIT, rows.length)}`);
hits.forEach((h) => console.log("  " + h));
