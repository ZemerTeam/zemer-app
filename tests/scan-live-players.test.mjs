// Unit tests for the pure core of scan-live-players.mjs (no network).
//   node --test tests/scan-live-players.test.mjs

import { test } from "node:test";
import assert from "node:assert/strict";
import { aggregate, blindSurfaces, coveredKeys, hashesFrom, samplesFor, SURFACES, UAS } from "./scan-live-players.mjs";

test("rotation never thins out a user agent", () => {
  for (const s of SURFACES) {
    const n = samplesFor(s, 30);
    const perAgent = Math.floor(n / s.uas.length);
    if (s.primary) assert.equal(perAgent, 30, "the app's source gets the full count for every agent");
    else assert.ok(perAgent >= 3 && n >= 10, `${s.name}: ${n} requests, ${perAgent} per agent`);
  }
});

test("hashesFrom reads escaped and plain player paths, every distinct hash on the page", () => {
  const iframe = 'var scriptUrl = \'https:\\/\\/www.youtube.com\\/s\\/player\\/11a45ffb\\/www-widgetapi.vflset\\/www-widgetapi.js\';';
  assert.deepEqual(hashesFrom(iframe), ["11a45ffb"]);
  const html = '<link href="/s/player/4efb8b2c/mweb.css"><script src="/s/player/4efb8b2c/player-plasma.js"></script>' +
    '<script src="/s/player/c65d4dfb/player_ias.vflset/en_US/base.js"></script>';
  assert.deepEqual(hashesFrom(html), ["4efb8b2c", "c65d4dfb"]);
  assert.deepEqual(hashesFrom(""), []);
  assert.deepEqual(hashesFrom("/s/player/TOOSHORT/x"), []);
});

test("blindSurfaces names every surface that returned no player", () => {
  assert.deepEqual(blindSurfaces({ iframe_api: { ok: 30 }, tv: { ok: 0 }, music: { ok: 0 } }), ["tv", "music"]);
  assert.deepEqual(blindSurfaces({ iframe_api: { ok: 1 } }), []);
});

test("the scan always includes the app's own iframe_api request", () => {
  const primary = SURFACES.filter((s) => s.primary);
  assert.equal(primary.length, 1);
  assert.equal(primary[0].url(), "https://www.youtube.com/iframe_api");
  assert.ok(primary[0].uas.includes("app"));
  assert.equal(UAS.app, "Mozilla/5.0"); // PlayerJsFetcher's exact User-Agent
});

test("aggregate counts and sorts most-seen first, dropping nulls", () => {
  const out = aggregate(["aaaaaaaa", "bbbbbbbb", "aaaaaaaa", null, "aaaaaaaa", "bbbbbbbb"]);
  assert.deepEqual(out, [
    { hash: "aaaaaaaa", count: 3 },
    { hash: "bbbbbbbb", count: 2 },
  ]);
});

test("aggregate breaks count ties by hash for stable output", () => {
  const out = aggregate(["ffffffff", "11111111"]);
  assert.deepEqual(out, [
    { hash: "11111111", count: 1 },
    { hash: "ffffffff", count: 1 },
  ]);
});

test("aggregate of empty / all-null is empty", () => {
  assert.deepEqual(aggregate([]), []);
  assert.deepEqual(aggregate([null, null]), []);
});

test("coveredKeys includes both primaries and aliases", () => {
  const cfg = JSON.stringify({
    schemaVersion: 1,
    players: {
      "12345678": { sig: "Tl(48,5831,INPUT)", nClass: "W_", sts: 20602, aliases: ["abcdef01"] },
    },
  });
  const keys = coveredKeys(cfg, "test.json");
  assert.ok(keys.has("12345678"), "primary covered");
  assert.ok(keys.has("abcdef01"), "alias covered");
  assert.ok(!keys.has("deadbeef"), "unknown not covered");
});

test("coveredKeys throws on an invalid config (device would reject it wholesale)", () => {
  assert.throws(() => coveredKeys(JSON.stringify({ players: {} }), "bad.json"));
});
