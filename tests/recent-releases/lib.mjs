// Request layer for the "recent releases" feature probes. Faithful to the app's InnerTube path:
//
//   YouTube.artist(browseId)  -> InnerTube.browse(WEB_REMIX, browseId)  (setLogin=false, visitorData only)
//   YouTube.album(browseId)   -> InnerTube.browse(WEB_REMIX, browseId)  (setLogin=false)
//   InnerTube.next(videoId)   -> POST /next   (setLogin=true  -> cookie + SAPISIDHASH)
//   InnerTube.player(videoId) -> POST /player (setLogin=true  -> cookie + SAPISIDHASH)
//
// Verified against InnerTube.kt: browse defaults to setLogin=false (WEB_REMIX context + X-Goog-Visitor-Id
// only, NO cookie), while next/player run with setLogin=true. The auth (SAPISIDHASH) and context builders
// are ported verbatim from web-remix-stream.mjs so this measures the SAME requests the app sends.
import crypto from "node:crypto";
import { getCred } from "../cred.mjs";
import { CLIENTS, ORIGIN } from "../clients.mjs";

const C = CLIENTS.find((c) => c.key === "WEB_REMIX");

// --- auth + headers + context (verbatim from web-remix-stream.mjs) ---
function sapisidHash(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  const hash = crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex");
  return `SAPISIDHASH ${ts}_${hash}`;
}

function headers(visitorData, { cookie, auth } = {}) {
  const h = {
    "Content-Type": "application/json",
    "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": C.clientId,
    "X-YouTube-Client-Version": C.clientVersion,
    "X-Origin": ORIGIN,
    Referer: ORIGIN + "/",
    "User-Agent": C.userAgent,
  };
  if (visitorData) h["X-Goog-Visitor-Id"] = visitorData;
  if (auth && cookie && C.loginSupported) {
    h.cookie = cookie;
    const a = sapisidHash(cookie);
    if (a) h.Authorization = a;
  }
  return h;
}

function context(visitorData) {
  const client = { clientName: C.clientName, clientVersion: C.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  return { client };
}

async function post(path, body, { visitorData, cred, auth = false }) {
  const url = `${ORIGIN}/youtubei/v1/${path}?prettyPrint=false`;
  const res = await fetch(url, {
    method: "POST",
    headers: headers(visitorData, { cookie: cred?.cookie, auth }),
    body: JSON.stringify(body),
  });
  const txt = await res.text();
  if (/^\s*</.test(txt)) return { http: res.status, blocked: true, json: null };
  let json = null;
  try { json = JSON.parse(txt); } catch { /* leave null */ }
  return { http: res.status, blocked: false, json };
}

export async function cred() {
  return getCred();
}

// browse: setLogin=false (artist/album pages) — visitorData only, no cookie.
export function browse({ browseId = null, params = null, continuation = null }, cr) {
  const body = { context: context(cr.visitorData), browseId, params };
  const path = continuation
    ? `browse?ctoken=${continuation}&continuation=${continuation}&type=next`
    : "browse";
  return post(path, body, { visitorData: cr.visitorData, cred: cr, auth: false });
}

// next: the watch panel. The app uses setLogin=true, but the feed job stays session-free (visitorData
// only) by default — pass { auth: true } to mirror the app exactly when probing.
export function next({ videoId, playlistId = null }, cr, { auth = false } = {}) {
  const body = { context: context(cr.visitorData), videoId };
  if (playlistId) body.playlistId = playlistId;
  return post("next", body, { visitorData: cr.visitorData, cred: cr, auth });
}

// player: carries the microformat date. Proven to return uploadDate with visitorData ONLY, so the
// feed job runs anonymous (auth=false) regardless of whether a cookie file is present.
export function player({ videoId }, cr, { auth = false } = {}) {
  const body = { context: context(cr.visitorData), videoId, contentCheckOk: true, racyCheckOk: true };
  return post("player", body, { visitorData: cr.visitorData, cred: cr, auth });
}

// --- tiny faithful parsers (only what the probe needs) ---

const runText = (runs) => runs?.map((r) => r.text).join("") ?? null;

// Walk an artist BrowseResponse and pull AlbumItems out of every shelf/carousel, in document order
// (YouTube returns newest-first within Albums/Singles). Mirrors ArtistPage.fromMusicTwoRowItemRenderer
// / fromMusicShelfRenderer for the album case.
export function artistReleases(json) {
  const tabs = json?.contents?.singleColumnBrowseResultsRenderer?.tabs;
  const sections = tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
  const out = [];
  for (const sec of sections) {
    const carousel = sec.musicCarouselShelfRenderer;
    const shelf = sec.musicShelfRenderer;
    const title =
      runText(carousel?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs) ??
      runText(shelf?.title?.runs) ?? "";
    const moreEndpoint =
      carousel?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer
        ?.navigationEndpoint?.browseEndpoint ??
      shelf?.title?.runs?.[0]?.navigationEndpoint?.browseEndpoint ?? null;
    const items = carousel?.contents ?? shelf?.contents ?? [];
    for (const it of items) {
      const r = it.musicTwoRowItemRenderer;
      if (!r) continue;
      const browseId = r.navigationEndpoint?.browseEndpoint?.browseId;
      if (!browseId || !browseId.startsWith("MPRE")) continue; // album/single browseId
      out.push({
        section: title,
        title: runText(r.title?.runs),
        browseId,
        playlistId:
          r.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint?.watchEndpoint?.playlistId ??
          r.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint?.watchPlaylistEndpoint?.playlistId ?? null,
        thumbnail: r.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.at(-1)?.url ?? null,
        yearGuess: r.subtitle?.runs?.at(-1)?.text ?? null,
        moreEndpoint,
      });
    }
  }
  return out;
}

// Parse the discography grid returned by the artist "Albums"/"Singles" more endpoint
// (InnerTube.artistItems -> gridRenderer). Items are in the order YouTube returns them.
export function artistItemsGrid(json) {
  const grid =
    json?.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
      ?.sectionListRenderer?.contents?.find((c) => c.gridRenderer)?.gridRenderer ??
    json?.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
      ?.sectionListRenderer?.contents?.find((c) => c.musicPlaylistShelfRenderer)?.musicPlaylistShelfRenderer;
  const items = grid?.items ?? grid?.contents ?? [];
  const out = [];
  for (const it of items) {
    const r = it.musicTwoRowItemRenderer;
    const browseId = r?.navigationEndpoint?.browseEndpoint?.browseId;
    if (!browseId || !browseId.startsWith("MPRE")) continue;
    const ov = r.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
      ?.playNavigationEndpoint;
    out.push({
      title: runText(r.title?.runs),
      browseId,
      playlistId: ov?.watchPlaylistEndpoint?.playlistId ?? ov?.watchEndpoint?.playlistId ?? null,
      thumbnail: r.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.at(-1)?.url ?? null,
      yearGuess: r.subtitle?.runs?.at(-1)?.text ?? null,
    });
  }
  return out;
}

// The track-listing shelf (musicShelfRenderer) of an album BrowseResponse.
function albumTrackShelf(json) {
  const tabs =
    json?.contents?.singleColumnBrowseResultsRenderer?.tabs ??
    json?.contents?.twoColumnBrowseResultsRenderer?.tabs;
  return (
    tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents?.find((c) => c.musicShelfRenderer)
      ?.musicShelfRenderer ??
    json?.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents
      ?.find((c) => c.musicShelfRenderer)?.musicShelfRenderer
  );
}

// Every track of an album BrowseResponse, in order ({ videoId, title }). The length is the track
// count, which the feed stores so the app can auto-play a 1-track single instead of opening it.
export function albumTracks(json) {
  const out = [];
  for (const it of albumTrackShelf(json)?.contents ?? []) {
    const r = it.musicResponsiveListItemRenderer;
    const vid =
      r?.playlistItemData?.videoId ??
      r?.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]
        ?.navigationEndpoint?.watchEndpoint?.videoId ??
      r?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
        ?.playNavigationEndpoint?.watchEndpoint?.videoId;
    if (vid) out.push({ videoId: vid, title: runText(r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs) });
  }
  return out;
}

// First track videoId from an album BrowseResponse (to probe per-track date sources).
export function albumFirstTrack(json) {
  return albumTracks(json)[0] ?? null;
}

// Largest thumbnail URL anywhere in a response (album art lives under various renderers; we just take
// the widest one). Used as a fallback when a grid item carries no thumbnail.
export function biggestThumbnail(obj) {
  let best = null, bestW = -1;
  const walk = (o) => {
    if (o == null || typeof o !== "object") return;
    if (Array.isArray(o)) { o.forEach(walk); return; }
    if (Array.isArray(o.thumbnails)) {
      for (const t of o.thumbnails) {
        if (t?.url && (t.width ?? 0) > bestW) { best = t.url; bestW = t.width ?? 0; }
      }
    }
    for (const v of Object.values(o)) walk(v);
  };
  walk(obj);
  return best;
}

// Deep-scan: collect every primitive value whose key looks date-related, with its path. This is the
// "hard data" part — we don't assume which field carries the date, we surface whatever the live
// response actually contains.
export function findDateFields(obj, path = "", out = []) {
  if (obj == null) return out;
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => findDateFields(v, `${path}[${i}]`, out));
    return out;
  }
  if (typeof obj === "object") {
    for (const [k, v] of Object.entries(obj)) {
      const p = path ? `${path}.${k}` : k;
      if (/(date|publish|upload|released|timestamp)/i.test(k) && (typeof v === "string" || typeof v === "number")) {
        out.push({ path: p, value: v });
      } else if (k === "simpleText" && /date|ago|20\d\d/i.test(String(v)) && path.toLowerCase().includes("date")) {
        out.push({ path: p, value: v });
      }
      findDateFields(v, p, out);
    }
  }
  return out;
}
