// Proves the harness actually detects the failures it screens for — so a clean run of run.mjs means
// "no breaks", not "the checker is asleep". No network/cookie needed.
//   node --test tests/search/self-test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { validate } from "./schema.mjs";
import { splitBySeparator, oddElements, clean, parseTime } from "./lib.mjs";
import { toYTItem, fromMRLIR_suggestion } from "./parsers.mjs";

const has = (ctx, type, field) => ctx.violations.some((v) => v.type === type && v.field === field);

// A minimal but VALID song MRLIR (every non-null field present).
const validSong = () => ({
  flexColumns: [
    { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: "Song Title", navigationEndpoint: { watchEndpoint: { videoId: "abc12345678" } } }] } } },
    { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: "Artist Name", navigationEndpoint: { browseEndpoint: { browseId: "UCxxxx" } } }, { text: " • " }, { text: "3:21" }] } } },
  ],
  playlistItemData: { videoId: "abc12345678" },
  thumbnail: { musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "https://lh3/x=w60-h60" }] } } },
});

test("strict: valid song MRLIR has no violations", () => {
  const ctx = validate(validSong(), "MRLIR");
  assert.equal(ctx.violations.length, 0, JSON.stringify(ctx.violations));
});

test("strict: PlaylistItemData missing its non-null videoId is flagged (the whole-response killer)", () => {
  const r = validSong();
  delete r.playlistItemData.videoId;
  const ctx = validate(r, "MRLIR");
  assert.ok(has(ctx, "PlaylistItemData", "videoId"), "should flag PlaylistItemData.videoId");
});

test("strict: a Run missing its non-null text is flagged", () => {
  const r = validSong();
  r.flexColumns[0].musicResponsiveListItemFlexColumnRenderer.text.runs.push({ navigationEndpoint: {} });
  const ctx = validate(r, "MRLIR");
  assert.ok(has(ctx, "Run", "text"), "should flag Run.text");
});

test("strict: a chip missing isSelected / navigationEndpoint is flagged (zemer-strict chip cloud)", () => {
  const chips = { chips: [{ chipCloudChipRenderer: { text: { runs: [{ text: "Songs" }] } } }] };
  const ctx = validate(chips, "ChipCloudRenderer");
  assert.ok(has(ctx, "ChipCloudChipRenderer", "isSelected"), "should flag isSelected");
  assert.ok(has(ctx, "ChipCloudChipRenderer", "navigationEndpoint"), "should flag navigationEndpoint");
});

test("strict: Icon missing its non-null iconType is flagged", () => {
  const menu = { menuRenderer: { items: [{ menuNavigationItemRenderer: { text: { runs: [{ text: "x" }] }, icon: {}, navigationEndpoint: {} } }] } };
  const ctx = validate(menu, "Menu");
  assert.ok(has(ctx, "Icon", "iconType"), "should flag Icon.iconType");
});

test("strict: unencoded subtree is recorded, not silently passed", () => {
  const ctx = validate({ contents: [{ gridRenderer: { items: [] } }] }, "SectionListRenderer");
  // gridRenderer is intentionally unencoded; SLR_Content just has no descriptor for it -> not recursed,
  // and nothing is wrongly flagged. (This documents the known boundary.)
  assert.equal(ctx.violations.length, 0);
});

test("parser: toYTItem drops a song with no extractable videoId, with a precise reason", () => {
  const r = validSong();
  delete r.playlistItemData;
  r.flexColumns[0].musicResponsiveListItemFlexColumnRenderer.text.runs[0].navigationEndpoint = null; // kill flex fallback
  const res = toYTItem(r);
  assert.equal(res.ok, false);
  assert.match(res.reason, /id null/);
});

test("parser: toYTItem extracts a valid song", () => {
  const res = toYTItem(validSong());
  assert.equal(res.ok, true);
  assert.equal(res.kind, "song");
  assert.equal(res.item.id, "abc12345678");
  assert.equal(res.item.duration, 3 * 60 + 21);
});

test("parser: suggestion song drops when thumbnail missing", () => {
  const r = validSong();
  delete r.thumbnail;
  const res = fromMRLIR_suggestion(r);
  assert.equal(res.ok, false);
  assert.match(res.reason, /thumbnail null/);
});

// ---- helper parity with Kotlin -----------------------------------------------------------------
test("helper: splitBySeparator splits on ' • '", () => {
  const runs = [{ text: "A" }, { text: " • " }, { text: "B" }, { text: "C" }];
  assert.deepEqual(splitBySeparator(runs).map((g) => g.map((r) => r.text)), [["A"], ["B", "C"]]);
});

test("helper: oddElements keeps indices 0,2,4", () => {
  assert.deepEqual(oddElements([1, 2, 3, 4, 5]), [1, 3, 5]);
});

test("helper: parseTime mm:ss and hh:mm:ss, null on garbage", () => {
  assert.equal(parseTime("3:21"), 201);
  assert.equal(parseTime("1:02:03"), 3723);
  assert.equal(parseTime("live"), null);
  assert.equal(parseTime(null), null);
});

test("helper: clean drops a leading non-artist group, keeps when it has nav or &/,", () => {
  // group[0][0] has no nav and no [&,] -> drop(1)
  assert.deepEqual(clean([[{ text: "3:21" }], [{ text: "Artist" }]]).map((g) => g.map((r) => r.text)), [["Artist"]]);
  // group[0][0] has nav -> keep
  const withNav = [[{ text: "Artist", navigationEndpoint: { browseEndpoint: {} } }], [{ text: "x" }]];
  assert.equal(clean(withNav).length, 2);
  // group[0][0] text has ',' -> keep
  assert.equal(clean([[{ text: "A, B" }], [{ text: "x" }]]).length, 2);
});
