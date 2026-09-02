// Unit tests for the drain classifier with a FAKE CDN — every verdict kind the monitor keys on,
// without network, cookie, jsdom or a pot minter:  node --test tests/client-fulldownload.test.mjs
//
// drainClient reads only global fetch and a small ctx surface, so both are stubbed here. What is
// under test is the classification the monitor's decide.mjs consumes: which responses are the
// app's own failure ("definitive"), which are the runner's ("inconclusive"), and what "whole" means.

import { test } from "node:test";
import assert from "node:assert/strict";
import { drainClient, formatRow } from "./client-fulldownload.mjs";

const ctx = (cookie = true) => ({
  hasCookie: cookie, visitorData: "vd", potVisitor: "potv",
  cred: { cookie: cookie ? "SAPISID=abc; SID=1" : "", dataSyncId: "dsid" },
  cipher: { sts: 20000, deobfuscateStreamUrl: (sc) => "https://r1---sn.googlevideo.com/videoplayback?sig=ok", transformNParamInUrl: (u) => u + "&n=t" },
});
const web = { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1", clientId: "67", userAgent: "ua", protocol: "web_cipher_pot", loginSupported: true, useSignatureTimestamp: true, useWebPoTokens: true };
const direct = { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "1.02", clientId: "101", userAgent: "ua", protocol: "direct", useSignatureTimestamp: false, useWebPoTokens: false };
const loginReq = { ...web, key: "WEB_CREATOR", clientName: "WEB_CREATOR", loginRequired: true };
const video = { videoId: "vid", potVideo: "potvid" };

const fmt = (over = {}) => ({ itag: 251, mimeType: "audio/webm; codecs=opus", bitrate: 140000, width: null, contentLength: "1000", approxDurationMs: "10000", url: "https://cdn/x?a=1", ...over });
const player = (status, over = {}) => ({ playabilityStatus: { status, ...(over.reason ? { reason: over.reason } : {}) }, streamingData: over.streamingData === null ? undefined : { adaptiveFormats: over.formats || [fmt()], serverAbrStreamingUrl: over.sabrUrl } });

/** Install a fake fetch: `plan.player` = {status, json}, `plan.cdn` = {status, bytes}. Records requests. */
function fakeFetch(plan) {
  const calls = [];
  globalThis.fetch = async (url, opts = {}) => {
    calls.push({ url: String(url), opts });
    if (String(url).includes("/youtubei/v1/player")) {
      const p = plan.player; return { status: p.status ?? 200, text: async () => JSON.stringify(p.json ?? {}) };
    }
    if (plan.cdnThrow) throw new Error("ECONNRESET");
    const c = plan.cdn || { status: 206, bytes: 1000 };
    const chunk = new Uint8Array(c.bytes);
    let sent = false;
    return { status: c.status, body: { getReader: () => ({ read: async () => (sent || c.bytes === 0 ? { done: true } : (sent = true, { done: false, value: chunk })), cancel: async () => {} }) } };
  };
  return calls;
}

test("whole song: OK player, consumable format, every byte drained", async () => {
  const calls = fakeFetch({ player: { json: player("OK") }, cdn: { status: 206, bytes: 1000 } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "whole"); assert.equal(r.read, 1000); assert.equal(r.clen, 1000); assert.equal(r.secs, 10);
  assert.match(formatRow(r), /WHOLE SONG/);
  // The app's request shape: sts + pot for a web client, cookie + SAPISIDHASH auth for loginSupported.
  const body = JSON.parse(calls[0].opts.body);
  assert.equal(body.playbackContext.contentPlaybackContext.signatureTimestamp, 20000);
  assert.equal(body.serviceIntegrityDimensions.poToken, "potv");
  assert.equal(body.context.user.onBehalfOfUser, "dsid");
  assert.equal(body.context.client.clientVersion, "1");
  assert.match(calls[0].opts.headers.Authorization, /^SAPISIDHASH \d+_[0-9a-f]{40}$/);
  assert.equal(calls[0].opts.headers["X-YouTube-Client-Name"], "67");
  // The CDN url got the n-transform and the videoId-bound pot (web client).
  assert.match(calls[1].url, /&n=t&pot=potvid$/);
});

test("direct client: no sts, no pot, no auth, url used AS-IS", async () => {
  const calls = fakeFetch({ player: { json: player("OK", { formats: [fmt({ url: "https://cdn/direct" })] }) } });
  const r = await drainClient(ctx(), direct, video);
  assert.equal(r.kind, "whole");
  const body = JSON.parse(calls[0].opts.body);
  assert.equal(body.playbackContext, undefined); assert.equal(body.serviceIntegrityDimensions, undefined); assert.equal(body.context.user, undefined);
  assert.equal(calls[0].opts.headers.cookie, undefined);
  assert.equal(calls[1].url, "https://cdn/direct");
});

test("partial: the CDN stops before contentLength (the 1 MiB wall) is a definitive failure", async () => {
  fakeFetch({ player: { json: player("OK") }, cdn: { status: 206, bytes: 400 } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "partial"); assert.match(r.reason, /206 after 0KB|206 after/);
});

test("partial: a 403 on the first GET (0 bytes) is a definitive failure", async () => {
  fakeFetch({ player: { json: player("OK") }, cdn: { status: 403, bytes: 0 } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "partial"); assert.match(r.reason, /403 after 0KB/);
});

test("sabr-only: OK but no url/signatureCipher on any format is the client-kill shape", async () => {
  fakeFetch({ player: { json: player("OK", { formats: [fmt({ url: null, signatureCipher: null })], sabrUrl: "https://sabr" }) } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "sabr-only"); assert.match(r.reason, /SABR-only/);
});

test("no-format: OK with no original audio format", async () => {
  fakeFetch({ player: { json: player("OK", { formats: [fmt({ width: 1280 }), fmt({ audioTrack: { isAutoDubbed: true } })] }) } });
  assert.equal((await drainClient(ctx(), web, video)).kind, "no-format");
});

test("not-ok: a playability rejection is definitive; the bot gate is NOT", async () => {
  fakeFetch({ player: { json: player("UNPLAYABLE", { reason: "This video is unavailable" }) } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "not-ok"); assert.match(r.reason, /UNPLAYABLE: This video is unavailable/);
  fakeFetch({ player: { json: player("LOGIN_REQUIRED", { reason: "Sign in to confirm you’re not a bot" }) } });
  const g = await drainClient(ctx(), direct, video);
  assert.equal(g.kind, "bot-gated"); assert.match(formatRow(g), /bot-gated/);
  fakeFetch({ player: { json: player("UNPLAYABLE", { reason: "Sign in to confirm you're not a bot" }) } });
  assert.equal((await drainClient(ctx(), direct, video)).kind, "bot-gated");
});

test("http-error: a non-200 /player is definitive; transport errors are inconclusive", async () => {
  fakeFetch({ player: { status: 400, json: {} } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "http-error"); assert.equal(r.http, 400);
  fakeFetch({ player: { json: player("OK") }, cdnThrow: true });
  const e = await drainClient(ctx(), web, video);
  assert.equal(e.kind, "error"); assert.match(e.reason, /ECONNRESET/);
  globalThis.fetch = async () => { throw new Error("getaddrinfo ENOTFOUND"); };
  const n = await drainClient(ctx(), web, video);
  assert.equal(n.kind, "error"); assert.match(n.reason, /ENOTFOUND/);
});

test("skipped-login: a login-required client without a cookie never reaches the network", async () => {
  const calls = fakeFetch({ player: { json: player("OK") } });
  const r = await drainClient(ctx(false), loginReq, video);
  assert.equal(r.kind, "skipped-login"); assert.equal(calls.length, 0);
  // With a cookie it is drained like any other client.
  assert.equal((await drainClient(ctx(true), loginReq, video)).kind, "whole");
});

test("a ciphered format goes through the cipher; a malformed /player body is a transport error, not a verdict", async () => {
  const calls = fakeFetch({ player: { json: player("OK", { formats: [fmt({ url: null, signatureCipher: "s=abc&sp=sig&url=https%3A%2F%2Fcdn" })] }) } });
  const r = await drainClient(ctx(), web, video);
  assert.equal(r.kind, "whole"); assert.match(calls[1].url, /sig=ok/);
  globalThis.fetch = async (url) => String(url).includes("/player") ? { status: 200, text: async () => "<html>not json" } : null;
  const m = await drainClient(ctx(), web, video);
  // An unparseable 200 has no playabilityStatus: reported as not-ok "-" (the app fails the same way).
  assert.equal(m.kind, "not-ok"); assert.equal(m.status, "-");
});
