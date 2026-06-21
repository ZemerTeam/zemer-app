// self-test.mjs — no-network unit tests for the feed builder's pure parsers/helpers (lib.mjs).
//
// These are the bits that break when YouTube changes a renderer shape; the live probes
// (probe-dates / probe-order / build-feed) cover the end-to-end path against the real CDN. Run:
//   node --test tests/recent-releases/self-test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  biggestThumbnail, findDateFields, artistReleases, artistItemsGrid, albumFirstTrack, albumTracks,
} from "./lib.mjs";

test("biggestThumbnail picks the widest url anywhere in the tree", () => {
  const obj = { a: { thumbnails: [{ url: "small", width: 100 }, { url: "big", width: 544 }] }, b: { thumbnails: [{ url: "mid", width: 300 }] } };
  assert.equal(biggestThumbnail(obj), "big");
  assert.equal(biggestThumbnail({}), null);
});

test("findDateFields surfaces the player microformat uploadDate", () => {
  const player = { microformat: { microformatDataRenderer: { uploadDate: "2026-06-17T04:08:04-07:00" } } };
  const hit = findDateFields(player).find((f) => /uploadDate/.test(f.path));
  assert.equal(hit?.value, "2026-06-17T04:08:04-07:00");
  assert.equal(findDateFields({}).length, 0);
});

const gridItem = (browseId, year, playlistId) => ({
  musicTwoRowItemRenderer: {
    title: { runs: [{ text: `Title ${browseId}` }] },
    navigationEndpoint: { browseEndpoint: { browseId } },
    subtitle: { runs: [{ text: year }] },
    thumbnailRenderer: { musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "thumb", width: 544 }] } } },
    thumbnailOverlay: {
      musicItemThumbnailOverlayRenderer: {
        content: { musicPlayButtonRenderer: { playNavigationEndpoint: { watchPlaylistEndpoint: { playlistId } } } },
      },
    },
  },
});

const browseWithGrid = (items) => ({
  contents: { singleColumnBrowseResultsRenderer: { tabs: [{ tabRenderer: { content: { sectionListRenderer: { contents: [{ gridRenderer: { items } }] } } } }] } },
});

test("artistItemsGrid returns only MPRE* albums, with fields", () => {
  const json = browseWithGrid([gridItem("MPREb_one", "2026", "OLAK1"), gridItem("VLsomething", "2026", "OLAK2")]);
  const out = artistItemsGrid(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].browseId, "MPREb_one");
  assert.equal(out[0].playlistId, "OLAK1");
  assert.equal(out[0].thumbnail, "thumb");
  assert.equal(out[0].yearGuess, "2026");
});

test("artistReleases reads albums out of carousels with their section + more endpoint", () => {
  const json = {
    contents: {
      singleColumnBrowseResultsRenderer: {
        tabs: [{
          tabRenderer: {
            content: {
              sectionListRenderer: {
                contents: [{
                  musicCarouselShelfRenderer: {
                    header: {
                      musicCarouselShelfBasicHeaderRenderer: {
                        title: { runs: [{ text: "Albums" }] },
                        moreContentButton: { buttonRenderer: { navigationEndpoint: { browseEndpoint: { browseId: "MPADmore", params: "p" } } } },
                      },
                    },
                    contents: [gridItem("MPREb_two", "2025", "OLAK3")],
                  },
                }],
              },
            },
          },
        }],
      },
    },
  };
  const out = artistReleases(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].section, "Albums");
  assert.equal(out[0].browseId, "MPREb_two");
  assert.equal(out[0].moreEndpoint?.browseId, "MPADmore");
});

const albumWithTracks = (...videoIds) => ({
  contents: { singleColumnBrowseResultsRenderer: { tabs: [{ tabRenderer: { content: { sectionListRenderer: { contents: [{
    musicShelfRenderer: { contents: videoIds.map((videoId) => ({ musicResponsiveListItemRenderer: { playlistItemData: { videoId } } })) },
  }] } } } }] } },
});

test("albumFirstTrack returns the first track's videoId", () => {
  assert.equal(albumFirstTrack(albumWithTracks("VID123"))?.videoId, "VID123");
  assert.equal(albumFirstTrack({}), null);
});

test("albumTracks lists every track in order (the count distinguishes a single from an album)", () => {
  const tracks = albumTracks(albumWithTracks("VID1", "VID2", "VID3"));
  assert.equal(tracks.length, 3);
  assert.deepEqual(tracks.map((t) => t.videoId), ["VID1", "VID2", "VID3"]);
  assert.equal(albumTracks(albumWithTracks("ONLY")).length, 1); // a single
  assert.equal(albumTracks({}).length, 0);
});
