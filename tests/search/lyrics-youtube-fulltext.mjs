// Fetch the full YouTube Music lyrics text for the ids listed in yt-lyrics output (ground truth set).
import fs from "node:fs";
import { cred } from "./lib.mjs";
const ids = [...fs.readFileSync(process.env.IN, "utf8").matchAll(/\(([A-Za-z0-9_-]{11})\): "/g)].map((m) => m[1]);
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const { visitorData } = await cred();
const ctx = { client: { clientName: "WEB_REMIX", clientVersion: "1.20250310.01.00", hl: "en", gl: "US", visitorData } };
async function post(ep, body) { const r = await fetch(`https://music.youtube.com/youtubei/v1/${ep}?prettyPrint=false`, { method: "POST", headers: { "Content-Type": "application/json", "X-Goog-Visitor-Id": visitorData, Origin: "https://music.youtube.com", "User-Agent": "Mozilla/5.0" }, body: JSON.stringify({ context: ctx, ...body }) }); return r.status === 200 ? r.json() : null; }
const out = [];
for (const id of ids) {
  const n = await post("next", { videoId: id });
  const tabs = n?.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs || [];
  const ep = tabs.map((t) => t.tabRenderer?.endpoint?.browseEndpoint).find((e) => e?.browseId?.startsWith("MPLYt"));
  const b = ep && await post("browse", { browseId: ep.browseId, params: ep.params });
  const sec = b?.contents?.sectionListRenderer?.contents?.[0]?.musicDescriptionShelfRenderer;
  const text = sec?.description?.runs?.[0]?.text; const source = sec?.footer?.runs?.[0]?.text;
  const row = rows.find((r) => r.id === id);
  if (text) out.push({ id, artist: row?.artist, title: row?.title, source, text });
  await new Promise((r) => setTimeout(r, 300));
}
fs.writeFileSync(new URL("./.cache/yt-lyrics-fulltext.json", import.meta.url), JSON.stringify(out, null, 1));
console.log(`saved ${out.length} lyrics; sources:`, out.reduce((m, r) => (m[r.source] = (m[r.source] || 0) + 1, m), {}));
