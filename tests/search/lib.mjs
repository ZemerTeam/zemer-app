// Request layer + faithful ports of the InnerTube list helpers and MusicResponsiveListItemRenderer
// accessors used by the search parsers. Reproduces the app's EXACT search HTTP path:
//
//   InnerTube.search(WEB_REMIX, ...)            -> POST /youtubei/v1/search
//   InnerTube.getSearchSuggestions(WEB_REMIX..) -> POST /youtubei/v1/music/get_search_suggestions
//
// IMPORTANT (verified against InnerTube.kt ytClient): both calls run with setLogin=false, so the app
// sends search requests with the WEB_REMIX client headers + X-Goog-Visitor-Id ONLY — NO cookie, NO
// SAPISIDHASH Authorization. Search is unauthenticated. We match that here (visitorData only).
import crypto from "node:crypto";
import { getCred } from "../cred.mjs";
import { CLIENTS, ORIGIN } from "../clients.mjs";

const C = CLIENTS.find((c) => c.key === "WEB_REMIX");
const dec = (s) => { try { return decodeURIComponent(s); } catch { return s; } };

// The 6 SearchFilter param strings, verbatim from YouTube.kt SearchFilter (NOT %-decoded — the app
// passes them straight into the request body `params`).
export const FILTERS = {
  FILTER_SONG: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D",
  FILTER_VIDEO: "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D",
  FILTER_ALBUM: "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D",
  FILTER_ARTIST: "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D",
  FILTER_FEATURED_PLAYLIST: "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D",
  FILTER_COMMUNITY_PLAYLIST: "EgeKAQQoAEABagoQAxAEEAoQCRAF",
};

function headers(visitorData) {
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
  return h; // deliberately no cookie / Authorization (setLogin=false)
}

function context(visitorData) {
  const client = { clientName: C.clientName, clientVersion: C.clientVersion, hl: "en", gl: "US" };
  if (visitorData) client.visitorData = visitorData;
  return { client };
}

// POST /search. Pass { query, params } for a fresh search, or { continuation } to page (the app
// sends the token as the `continuation` AND `ctoken` query params with a null body query/params).
export async function postSearch({ query = null, params = null, continuation = null, visitorData }) {
  const body = { context: context(visitorData), query, params };
  const url = new URL(`${ORIGIN}/youtubei/v1/search`);
  url.searchParams.set("prettyPrint", "false");
  if (continuation) { url.searchParams.set("continuation", continuation); url.searchParams.set("ctoken", continuation); }
  const res = await fetch(url, { method: "POST", headers: headers(visitorData), body: JSON.stringify(body) });
  return { status: res.status, json: await res.json() };
}

export async function postSuggestions({ input, visitorData }) {
  const body = { context: context(visitorData), input };
  const url = `${ORIGIN}/youtubei/v1/music/get_search_suggestions?prettyPrint=false`;
  const res = await fetch(url, { method: "POST", headers: headers(visitorData), body: JSON.stringify(body) });
  return { status: res.status, json: await res.json() };
}

export async function cred() {
  const c = await getCred();
  return { visitorData: dec(c.visitorData || ""), source: c.source };
}

// ------------------------------------------------------------------------------------------------
// Faithful ports of innertube list helpers (models/Runs.kt, models/Continuation.kt,
// models/MusicShelfRenderer.kt, utils/Utils.kt) and MRLIR accessors. Kept byte-for-byte equivalent
// to the Kotlin so a parser drop in the harness means a parser drop in the app.

const SEP = " • "; // " • " — the run separator

export function splitBySeparator(runs) {
  const res = [];
  let tmp = [];
  for (const run of runs) {
    if (run.text === SEP) { res.push(tmp); tmp = []; }
    else tmp.push(run);
  }
  res.push(tmp);
  return res;
}

export function oddElements(runs) {
  return runs.filter((_, i) => i % 2 === 0);
}

export function clean(lists) {
  const a = lists?.[0]?.[0];
  const navPresent = a?.navigationEndpoint != null;
  const textContains = a?.text == null ? null : /[&,]/.test(a.text); // Boolean? in Kotlin
  // Kotlin: keep if (nav != null) || (text?.contains([&,]) != false). != false is true for null|true.
  if (navPresent || textContains !== false) return lists;
  return lists.slice(1);
}

export function parseTime(s) {
  if (s == null) return null;
  try {
    const parts = s.split(":").map((p) => {
      const n = Number(p);
      if (!Number.isInteger(n)) throw new Error("nan");
      return n;
    });
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  } catch { return null; }
  return null;
}

// models/MusicShelfRenderer.kt getItems()/getContinuation()
export const getItems = (contents) => (contents || []).map((c) => c.musicResponsiveListItemRenderer).filter((x) => x != null);
export const getShelfContinuation = (contents) =>
  (contents || []).find((c) => c.continuationItemRenderer != null)
    ?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token ?? null;
// models/Continuation.kt getContinuation()
export const getContinuation = (list) =>
  (list || [])[0]?.nextContinuationData?.continuation ??
  (list || [])[0]?.nextRadioContinuationData?.continuation ?? null;

// ThumbnailRenderer.getThumbnailUrl() — croppedSquareThumbnailRenderer is a @JsonNames alias of
// musicThumbnailRenderer, so accept either key.
export function thumbnailUrl(tr) {
  const mtr = tr?.musicThumbnailRenderer ?? tr?.croppedSquareThumbnailRenderer;
  return mtr?.thumbnail?.thumbnails?.at(-1)?.url ?? null;
}

// ---- MusicResponsiveListItemRenderer accessors (models/MusicResponsiveListItemRenderer.kt) ----
const pageType = (r) =>
  r?.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;

export const isSong = (r) =>
  r.navigationEndpoint == null ||
  r.navigationEndpoint.watchEndpoint != null ||
  r.navigationEndpoint.watchPlaylistEndpoint != null ||
  r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint != null;
export const isPlaylist = (r) => pageType(r) === "MUSIC_PAGE_TYPE_PLAYLIST";
export const isAlbum = (r) => pageType(r) === "MUSIC_PAGE_TYPE_ALBUM" || pageType(r) === "MUSIC_PAGE_TYPE_AUDIOBOOK";
export const isArtist = (r) => pageType(r) === "MUSIC_PAGE_TYPE_ARTIST" || pageType(r) === "MUSIC_PAGE_TYPE_LIBRARY_ARTIST";

export const videoIdOf = (r) =>
  r.playlistItemData?.videoId ??
  r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.navigationEndpoint?.watchEndpoint?.videoId ??
  r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId ??
  null;

// flex column [i] runs
export const flexRuns = (r, i) =>
  r.flexColumns?.[i]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? null;
