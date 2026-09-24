// Stricter description-lyrics detector across channel-owned videos of N whitelisted artists.
// A "lyrics block" = >=6 consecutive lines, each <70 chars, no digits/URLs/@/colons, at least 4
// distinct lines, and the block must not be preceded within 3 lines by a role label (credits list).
import fs from "node:fs";
import { postSearch, cred, FILTERS } from "./lib.mjs";
import { CLIENTS } from "../clients.mjs";
const WEB = CLIENTS.find((c) => c.key === "WEB");
const wl = JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")).filter((a) => a.id && a.name);
const N = Number(process.env.N || 120), K = Number(process.env.K || 3);
const stride = Math.max(1, Math.floor(wl.length / N)); const artists = []; for (let i = 0; i < wl.length && artists.length < N; i += stride) artists.push(wl[i]);
const { visitorData } = await cred(); const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sectionContents = (j) => j?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
const mrlirs = (c) => c.flatMap((s) => [...(s.musicShelfRenderer?.contents || []), ...(s.itemSectionRenderer?.contents || [])].map((x) => x.musicResponsiveListItemRenderer).filter(Boolean));
const vidOf = (r) => r.playlistItemData?.videoId ?? r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId ?? null;
const chanIds = (r) => JSON.stringify(r.flexColumns?.[1] || {}).match(/"browseId":"(UC[^"]+)"/g)?.map((m) => m.slice(12, -1)) || [];
const ROLE = /:|https?:|www\.|@|#|℗|©|\d{3}|instagram|facebook|spotify|apple|itunes|youtube|download|subscribe|produced|arranged|mix|master|vocals|guitar|drums|bass|keys|piano|photo|video|director|תופים|גיטר|בס|קלידים|פסנתר|כינור|חליל|סקסופון|הקשה|צילום|בימוי|עריכה|הפקה|עיבוד|מיקס|מאסטר|הזמנ|ניהול|שיווק|יח"צ|עיצוב|איפור|שיער|הפצה|לחנים|בהופעה/i;
function lyricsBlock(desc) {
  const lines = (desc || "").split("\n").map((l) => l.trim());
  let best = 0, run = [], bestStart = -1;
  const flush = (end) => { const distinct = new Set(run).size; if (run.length >= 6 && distinct >= 4 && run.length > best) { best = run.length; bestStart = end - run.length; } run = []; };
  lines.forEach((l, i) => { if (l && l.length < 70 && !ROLE.test(l) && /[֐-׿]{2,}|[A-Za-z']{2,}/.test(l) && !/^[A-Z][a-z]+ [A-Z][a-z]+$/.test(l)) run.push(l); else flush(i); });
  flush(lines.length);
  if (best === 0) return null;
  return { lines: best, sample: lines.slice(bestStart, bestStart + 3).join(" / ") };
}
const tally = { artists: artists.length, videosChecked: 0, withLyricsBlock: 0, manualCaptions: 0, asr: 0 }; const hits = [];
for (const a of artists) {
  try {
    const { json } = await postSearch({ query: a.name.split(" - ")[0], params: FILTERS.FILTER_VIDEO, visitorData });
    const vids = mrlirs(sectionContents(json)).filter((r) => chanIds(r).includes(a.id) && vidOf(r)).map(vidOf).slice(0, K);
    for (const id of vids) {
      const pr = await fetch("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", { method: "POST", headers: { "Content-Type": "application/json", "X-Goog-Visitor-Id": visitorData, "User-Agent": "Mozilla/5.0" }, body: JSON.stringify({ context: { client: { clientName: WEB.clientName, clientVersion: WEB.clientVersion, hl: "en", gl: "US", visitorData } }, videoId: id }) });
      const p = pr.status === 200 ? await pr.json() : null; if (!p?.videoDetails) continue;
      tally.videosChecked++;
      const b = lyricsBlock(p.videoDetails.shortDescription);
      if (b) { tally.withLyricsBlock++; hits.push(`${a.name} | ${p.videoDetails.title} (${id}) | ${b.lines} lines | ${b.sample}`); }
      const tracks = p.captions?.playerCaptionsTracklistRenderer?.captionTracks || [];
      if (tracks.some((t) => t.kind !== "asr")) tally.manualCaptions++; else if (tracks.length) tally.asr++;
      await sleep(250);
    }
  } catch (e) { console.error(a.name, String(e).slice(0, 80)); }
  await sleep(200);
}
console.log(JSON.stringify(tally)); hits.forEach((h) => console.log("  " + h));
