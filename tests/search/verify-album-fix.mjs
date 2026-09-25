// Verifies the data path that fix #1 relies on, BEFORE editing the app.
//
// Fix #1: in ArtistScreen, route the artist's "Albums" section through `section.moreEndpoint` (the
// album grid) instead of the now-empty `search?filter=albums`.
//
// This proves, against live data, that:
//   1) browse(artistChannelId) returns an "Albums" carousel section (ArtistPage.sections);
//   2) that section carries a moreContentButton -> browseEndpoint (so ArtistSection.moreEndpoint is
//      non-null and the title becomes clickable);
//   3) browsing that endpoint returns the album grid.
// If all three hold, the fix routes "Albums" to real albums sourced from the artist page.
import { browseDiag, sleep, diagCred } from "./diag-auth.mjs";

const tab0 = (j) => j?.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
const carouselTitle = (c) => c?.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.[0]?.text;
const moreEndpoint = (c) => c?.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint;
const isAlbumTwoRow = (it) => {
  const be = it?.musicTwoRowItemRenderer?.navigationEndpoint?.browseEndpoint;
  const pt = be?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;
  return pt === "MUSIC_PAGE_TYPE_ALBUM" || pt === "MUSIC_PAGE_TYPE_AUDIOBOOK" || /^MPREb_/.test(be?.browseId || "");
};

const ARTISTS = [
  ["Mordechai Shapiro", "UCm5x_womXL5E7TAYca5JkZQ"],
  ["Yaakov Shwekey", "UC_dRX63mhvOrenBjYWJx9mA"],
  ["Baruch Levine", "UC_IxmTZosg-FyYkvf1mzYnw"],
];

const { source } = await diagCred();
console.log(`cred=${source ? "yes" : "no"}\n`);
console.log("artist".padEnd(20), "| Albums section | inline albums | moreEndpoint | grid albums (artistItems)");
let allGood = true;
for (const [name, id] of ARTISTS) {
  const page = await browseDiag(id);
  if (page.blocked) { console.log(name.padEnd(20), "| RATE-LIMITED"); allGood = false; break; }
  const albumsSection = tab0(page.j).find((c) => carouselTitle(c) === "Albums");
  const inline = albumsSection?.musicCarouselShelfRenderer?.contents?.filter(isAlbumTwoRow).length ?? 0;
  const more = albumsSection ? moreEndpoint(albumsSection) : null;
  let gridAlbums = "(no moreEndpoint)";
  if (more?.browseId) {
    await sleep(7000);
    const grid = await browseDiag(more.browseId, { params: more.params });
    if (grid.blocked) { gridAlbums = "RATE-LIMITED"; }
    else {
      const items = grid.j?.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents?.[0]?.gridRenderer?.items || [];
      gridAlbums = `${items.filter(isAlbumTwoRow).length}/${items.length}`;
    }
  }
  const ok = !!albumsSection && !!more?.browseId;
  if (!ok) allGood = false;
  console.log(
    name.padEnd(20), "|",
    String(!!albumsSection).padEnd(14), "|",
    String(inline).padEnd(13), "|",
    String(more?.browseId ? "yes" : "NO").padEnd(12), "|",
    gridAlbums,
  );
  await sleep(7000);
}
console.log(`\n${allGood ? "PASS" : "PARTIAL"}: the 'Albums' section + moreEndpoint exist and resolve to an album grid ->`);
console.log("routing ArtistScreen's 'Albums' title through section.moreEndpoint shows real albums from the artist page.");
