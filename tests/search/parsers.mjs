// Faithful JS port of the app's search parser, with drop instrumentation. Returns either
//   { ok: true,  kind, item }                      // item the app would surface
//   { ok: false, kind, reason }                    // the app's `?: return null` that fired
// so the runner can report exactly WHICH field YouTube stopped sending for each lost item.
//
// Port (line-for-line equivalent to the Kotlin):
//   toYTItem                              <- pages/SearchPage.kt          (used by YouTube.search)
//
// (The suggestion/summary/continuation ports were removed with their app functions - YouTube.search
// is the app's ONLY remaining InnerTube search entry point; Zemer serves everything else.)
import {
  splitBySeparator, oddElements, clean, parseTime, thumbnailUrl,
  isSong, isPlaylist, isAlbum, isArtist, videoIdOf, flexRuns,
} from "./lib.mjs";

const drop = (kind, reason) => ({ ok: false, kind, reason });

const ok = (kind, item) => ({ ok: true, kind, item });

const text0 = (runs) => runs?.[0]?.text ?? null;
const title0 = (r) => flexRuns(r, 0)?.[0]?.text ?? null;
const browseId = (r) => r.navigationEndpoint?.browseEndpoint?.browseId ?? null;
const explicitOf = (r) =>
  (r.badges || []).some((b) => b.musicInlineBadgeRenderer?.icon?.iconType === "MUSIC_EXPLICIT_BADGE");
const menuItems = (r) => r.menu?.menuRenderer?.items ?? [];
const menuWatchPlaylist = (r, iconType) =>
  menuItems(r).find((it) => it.menuNavigationItemRenderer?.icon?.iconType === iconType)
    ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?? null;
const overlayPlayNav = (r) =>
  r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint ?? null;
const artistsFrom = (group) =>
  (group ? oddElements(group) : null)?.map((run) => ({ name: run.text, id: run.navigationEndpoint?.browseEndpoint?.browseId ?? null })) ?? null;

// ---- SearchPage.toYTItem -----------------------------------------------------------------------
export function toYTItem(r) {
  const secondaryLine = (() => { const runs = flexRuns(r, 1); return runs ? splitBySeparator(runs) : null; })();
  if (secondaryLine == null) return drop("?", "flexColumns[1] runs missing (secondaryLine null)");

  if (isSong(r)) {
    const id =
      r.playlistItemData?.videoId ??
      r.navigationEndpoint?.watchEndpoint?.videoId ??
      overlayPlayNav(r)?.watchEndpoint?.videoId ??
      flexRuns(r, 0)?.[0]?.navigationEndpoint?.watchEndpoint?.videoId ?? null;
    if (id == null) return drop("song", "id null (no videoId in playlistItemData/nav/overlay/flex)");
    const title = title0(r); if (title == null) return drop("song", "title null");
    const artists = artistsFrom(secondaryLine[0]); if (artists == null) return drop("song", "artists null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("song", "thumbnail null");
    return ok("song", { type: "song", id, title, artists, thumbnail, explicit: explicitOf(r), duration: parseTime(text0(secondaryLine.at(-1))) });
  }
  if (isArtist(r)) {
    const id = browseId(r); if (id == null) return drop("artist", "browseId null");
    const title = title0(r); if (title == null) return drop("artist", "title null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("artist", "thumbnail null");
    if (menuWatchPlaylist(r, "MUSIC_SHUFFLE") == null) return drop("artist", "shuffleEndpoint null");
    if (menuWatchPlaylist(r, "MIX") == null) return drop("artist", "radioEndpoint null");
    return ok("artist", { type: "artist", id, title, thumbnail, explicit: false });
  }
  if (isAlbum(r)) {
    const id = browseId(r); if (id == null) return drop("album", "browseId null");
    const playlistId = (overlayPlayNav(r)?.watchEndpoint ?? overlayPlayNav(r)?.watchPlaylistEndpoint)?.playlistId ?? null;
    if (playlistId == null) return drop("album", "playlistId null (overlay anyWatchEndpoint)");
    const title = title0(r); if (title == null) return drop("album", "title null");
    const artists = artistsFrom(secondaryLine[1]); if (artists == null) return drop("album", "artists null (secondaryLine[1])");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("album", "thumbnail null");
    return ok("album", { type: "album", id, title, artists, thumbnail, explicit: explicitOf(r), year: Number(text0(secondaryLine[2])) || null });
  }
  if (isPlaylist(r)) {
    const id = browseId(r)?.replace(/^VL/, "") ?? null; if (id == null) return drop("playlist", "browseId null");
    const title = title0(r); if (title == null) return drop("playlist", "title null");
    const authorRun = secondaryLine[0]?.[0]; if (authorRun == null) return drop("playlist", "author null (secondaryLine[0][0])");
    const songCountText = flexRuns(r, 1)?.at(-1)?.text ?? null; if (songCountText == null) return drop("playlist", "songCountText null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("playlist", "thumbnail null");
    if (overlayPlayNav(r)?.watchPlaylistEndpoint == null) return drop("playlist", "playEndpoint null");
    if (menuWatchPlaylist(r, "MUSIC_SHUFFLE") == null) return drop("playlist", "shuffleEndpoint null");
    if (menuWatchPlaylist(r, "MIX") == null) return drop("playlist", "radioEndpoint null");
    return ok("playlist", { type: "playlist", id, title, author: { name: authorRun.text, id: authorRun.navigationEndpoint?.browseEndpoint?.browseId ?? null }, thumbnail, explicit: false });
  }
  return drop("unclassified", "no is* branch matched");
}
