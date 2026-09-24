// For sampled whitelisted songs, find the channel's own VIDEO upload via FILTER_VIDEO search (same
// channel id), then check its description for a lyrics block and its caption tracks (manual vs asr).
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { CLIENTS } from "../clients.mjs";
const WEB = CLIENTS.find((c) => c.key === "WEB");
const rows = JSON.parse(fs.readFileSync(new URL("./.cache/lyrics-coverage.json", import.meta.url), "utf8"));
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8"));
const idByName = new Map(wl.map((a) => [a.name, a.id]));
const LIMIT = Number(process.env.LIMIT || 60);
const { visitorData } = await cred();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sectionContents = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const mrlirs = (c) => c.flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])].map((x) => x.musicResponsiveListItemRenderer).filter(Boolean));
const vidOf = (r) => r.playlistItemData?.videoId ?? r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId ?? null;
const chanIds = (r) => JSON.stringify(r.flexColumns?.[1] || {}).match(/"browseId":"(UC[^"]+)"/g)?.map((m) => m.slice(12, -1)) || [];
const lyricLine = /[֐-׿]{2,}|[A-Za-z']{2,}/;
function descRun(d) {
  let run = 0, best = 0;
  for (const l0 of (d || "").split("\n")) { const l = l0.trim(); if (l && l.length < 80 && !/https?:|@|#|℗|©|instagram|spotify|apple|itunes|download|subscribe|produced|arranged|mix|master|lyrics by|music by|composed|כתיבה|לחן|עיבוד|הפקה|מיקס|צילום|בימוי|הזמנות|להזמנת|מילים:|לחן:/i.test(l) && lyricLine.test(l)) { run++; best = Math.max(best, run); } else run = 0; }
  return best;
}
const tally = { withVideo: 0, descLyrics: 0, manualCaptions: 0, asrOnly: 0 };
const notes = [];
for (const s of rows.slice(0, LIMIT)) {
  const wanted = idByName.get(s.artist);
  try {
    const { json } = await postSearch({ query: `${s.title} ${s.artist.split(" - ")[0]}`, params: FILTERS.FILTER_VIDEO, visitorData });
    const vid = mrlirs(sectionContents(json)).find((r) => wanted && chanIds(r).includes(wanted) && vidOf(r));
    if (!vid) continue;
    tally.withVideo++;
    const id = vidOf(vid);
    const pr = await fetch("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", { method: "POST", headers: { "Content-Type": "application/json", "X-Goog-Visitor-Id": visitorData, "User-Agent": "Mozilla/5.0" },
      body: JSON.stringify({ context: { client: { clientName: WEB.clientName, clientVersion: WEB.clientVersion, hl: "en", gl: "US", visitorData } }, videoId: id }) });
    const p = pr.status === 200 ? await pr.json() : null;
    const run = descRun(p?.videoDetails?.shortDescription);
    const tracks = p?.captions?.playerCaptionsTracklistRenderer?.captionTracks || [];
    const manual = tracks.filter((t) => t.kind !== "asr");
    const line = [];
    if (run >= 6) { tally.descLyrics++; line.push(`desc ${run} lines`); }
    if (manual.length) { tally.manualCaptions++; line.push(`captions[${manual.map((t) => t.languageCode).join(",")}]`); } else if (tracks.length) { tally.asrOnly++; line.push("asr-only"); }
    if (line.length) notes.push(`${s.artist} — ${s.title} → video ${id}: ${line.join(" | ")}`);
  } catch (e) { notes.push(`${s.title}: ERR ${String(e).slice(0, 60)}`); }
  await sleep(300);
}
console.log(`n=${Math.min(LIMIT, rows.length)}`, tally);
notes.forEach((x) => console.log("  " + x));
