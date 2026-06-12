// Faithful JS ports of the app's search parsers, with drop instrumentation. Each returns either
//   { ok: true,  kind, item }                      // item the app would surface
//   { ok: false, kind, reason }                    // the app's `?: return null` that fired
// so the runner can report exactly WHICH field YouTube stopped sending for each lost item.
//
// Ports (line-for-line equivalent to the Kotlin):
//   toYTItem                              <- pages/SearchPage.kt          (used by YouTube.search)
//   fromMRLIR_summary                     <- pages/SearchSummaryPage.kt   (used by YouTube.searchSummary)
//   fromCardShelf                         <- pages/SearchSummaryPage.kt   (top result)
//   fromMRLIR_suggestion                  <- pages/SearchSuggestionPage.kt(used by YouTube.searchSuggestions)
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

// ---- SearchSummaryPage.fromMusicResponsiveListItemRenderer -------------------------------------
export function fromMRLIR_summary(r) {
  const secRuns = flexRuns(r, 1); if (secRuns == null) return drop("?", "flexColumns[1] runs missing (secondaryLine null)");
  const secondaryLine = splitBySeparator(secRuns);
  const thirdRuns = flexRuns(r, 2);
  const thirdLine = thirdRuns ? splitBySeparator(thirdRuns) : [];
  const listRun = clean([...secondaryLine, ...thirdLine]);

  if (isSong(r)) {
    const id =
      r.playlistItemData?.videoId ??
      r.navigationEndpoint?.watchEndpoint?.videoId ??
      overlayPlayNav(r)?.watchEndpoint?.videoId ??
      flexRuns(r, 0)?.[0]?.navigationEndpoint?.watchEndpoint?.videoId ?? null;
    if (id == null) return drop("song", "id null");
    const title = title0(r); if (title == null) return drop("song", "title null");
    const artists = artistsFrom(listRun[0]); if (artists == null) return drop("song", "artists null (listRun[0])");
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
    const playlistId = overlayPlayNav(r)?.watchPlaylistEndpoint?.playlistId ?? null;
    if (playlistId == null) return drop("album", "playlistId null (overlay watchPlaylistEndpoint)");
    const title = title0(r); if (title == null) return drop("album", "title null");
    const artists = artistsFrom(secondaryLine[1]); if (artists == null) return drop("album", "artists null (secondaryLine[1])");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("album", "thumbnail null");
    return ok("album", { type: "album", id, title, artists, thumbnail, explicit: explicitOf(r), year: Number(text0(secondaryLine[2])) || null });
  }
  if (isPlaylist(r)) {
    const id = browseId(r)?.replace(/^VL/, "") ?? null; if (id == null) return drop("playlist", "browseId null");
    const title = title0(r); if (title == null) return drop("playlist", "title null");
    const authorRun = secondaryLine[1]?.[0]; if (authorRun == null) return drop("playlist", "author null (secondaryLine[1][0])");
    const songCountText = flexRuns(r, 1)?.at(-1)?.text ?? null; if (songCountText == null) return drop("playlist", "songCountText null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("playlist", "thumbnail null");
    if (overlayPlayNav(r)?.watchPlaylistEndpoint == null) return drop("playlist", "playEndpoint null");
    if (menuWatchPlaylist(r, "MUSIC_SHUFFLE") == null) return drop("playlist", "shuffleEndpoint null");
    if (menuWatchPlaylist(r, "MIX") == null) return drop("playlist", "radioEndpoint null");
    return ok("playlist", { type: "playlist", id, title, author: { name: authorRun.text, id: authorRun.navigationEndpoint?.browseEndpoint?.browseId ?? null }, thumbnail, explicit: false });
  }
  return drop("unclassified", "no is* branch matched");
}

// ---- SearchSummaryPage.fromMusicCardShelfRenderer (top result) ---------------------------------
export function fromCardShelf(renderer) {
  const subtitle = renderer.subtitle?.runs ? splitBySeparator(renderer.subtitle.runs) : null;
  const onTap = renderer.onTap || {};
  const be = onTap.browseEndpoint;
  const beType = be?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;
  const cardThumb = () => thumbnailUrl(renderer.thumbnail);
  const cardExplicit = () => (renderer.subtitleBadges || []).some((b) => b.musicInlineBadgeRenderer?.icon?.iconType === "MUSIC_EXPLICIT_BADGE");
  const cardButtons = renderer.buttons || [];
  const findBtn = (iconType) => cardButtons.find((b) => b.buttonRenderer?.icon?.iconType === iconType)?.buttonRenderer?.command;

  if (onTap.watchEndpoint != null) {
    const id = onTap.watchEndpoint.videoId ?? null; if (id == null) return drop("song", "id null");
    const title = text0(renderer.title?.runs); if (title == null) return drop("song", "title null");
    const artists = artistsFrom(subtitle?.[1]); if (artists == null) return drop("song", "artists null (subtitle[1])");
    const thumbnail = cardThumb(); if (thumbnail == null) return drop("song", "thumbnail null");
    return ok("song", { type: "song", id, title, artists, thumbnail, explicit: cardExplicit() });
  }
  if (beType === "MUSIC_PAGE_TYPE_ARTIST") {
    const id = be.browseId;
    const title = text0(renderer.title?.runs); if (title == null) return drop("artist", "title null");
    const thumbnail = cardThumb(); if (thumbnail == null) return drop("artist", "thumbnail null");
    if (findBtn("MUSIC_SHUFFLE")?.watchPlaylistEndpoint == null) return drop("artist", "shuffleEndpoint null");
    if (findBtn("MIX")?.watchPlaylistEndpoint == null) return drop("artist", "radioEndpoint null");
    return ok("artist", { type: "artist", id, title, thumbnail, explicit: false });
  }
  if (beType === "MUSIC_PAGE_TYPE_ALBUM" || beType === "MUSIC_PAGE_TYPE_AUDIOBOOK") {
    const playNav = cardButtons[0]?.buttonRenderer?.command;
    const playlistId = (playNav?.watchEndpoint ?? playNav?.watchPlaylistEndpoint)?.playlistId ?? null;
    if (playlistId == null) return drop("album", "playlistId null (buttons[0] anyWatchEndpoint)");
    const title = text0(renderer.title?.runs); if (title == null) return drop("album", "title null");
    const artists = artistsFrom(subtitle?.[1]); if (artists == null) return drop("album", "artists null (subtitle[1])");
    const thumbnail = cardThumb(); if (thumbnail == null) return drop("album", "thumbnail null");
    return ok("album", { type: "album", id: be.browseId, title, artists, thumbnail, explicit: cardExplicit() });
  }
  if (beType === "MUSIC_PAGE_TYPE_PLAYLIST") {
    const id = be.browseId.replace(/^VL/, "");
    const title = renderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.map((x) => x.text).join("") ?? null;
    if (title == null) return drop("playlist", "title null (header)");
    const authorName = renderer.subtitle?.runs?.map((x) => x.text).join(", ") ?? null;
    if (authorName == null) return drop("playlist", "author null (subtitle)");
    const thumbnail = cardThumb(); if (thumbnail == null) return drop("playlist", "thumbnail null");
    if (findBtn("PLAY_ARROW")?.watchPlaylistEndpoint == null) return drop("playlist", "playEndpoint null");
    if (findBtn("MUSIC_SHUFFLE")?.watchPlaylistEndpoint == null) return drop("playlist", "shuffleEndpoint null");
    return ok("playlist", { type: "playlist", id, title, author: { name: authorName, id: null }, thumbnail, explicit: false });
  }
  return drop("unclassified", "onTap is neither watch nor a known browse endpoint");
}

// ---- SearchSuggestionPage.fromMusicResponsiveListItemRenderer ----------------------------------
export function fromMRLIR_suggestion(r) {
  if (isSong(r)) {
    const id = videoIdOf(r); if (id == null) return drop("song", "videoId null");
    const title = title0(r); if (title == null) return drop("song", "title null");
    const artists = artistsFrom(splitBySeparator(flexRuns(r, 1) ?? [])[1]); if (artists == null) return drop("song", "artists null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("song", "thumbnail null");
    return ok("song", { type: "song", id, title, artists, thumbnail, explicit: explicitOf(r) });
  }
  if (isArtist(r)) {
    const id = browseId(r); if (id == null) return drop("artist", "browseId null");
    const title = title0(r); if (title == null) return drop("artist", "title null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("artist", "thumbnail null");
    return ok("artist", { type: "artist", id, title, thumbnail, explicit: false }); // shuffle/radio nullable here
  }
  if (isAlbum(r)) {
    const secRuns = flexRuns(r, 1); if (secRuns == null) return drop("album", "secondaryLine null");
    const secondaryLine = splitBySeparator(secRuns);
    const id = browseId(r); if (id == null) return drop("album", "browseId null");
    const playlistId = menuWatchPlaylist(r, "MUSIC_SHUFFLE")?.playlistId ?? null;
    if (playlistId == null) return drop("album", "playlistId null (menu MUSIC_SHUFFLE)");
    const title = title0(r); if (title == null) return drop("album", "title null");
    const artists = artistsFrom(secondaryLine[1]); if (artists == null) return drop("album", "artists null");
    const thumbnail = thumbnailUrl(r.thumbnail); if (thumbnail == null) return drop("album", "thumbnail null");
    return ok("album", { type: "album", id, title, artists, thumbnail, explicit: explicitOf(r) });
  }
  return drop("unclassified", "no is* branch matched");
}
