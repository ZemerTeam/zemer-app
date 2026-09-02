// The SABR drain's session refresh: a mid-drain HTTP failure re-resolves the player and continues
// the same drain over a fresh session (the app's error-refresh), never a "partial" on its own.
import test from "node:test";
import assert from "node:assert/strict";
import { drainClientSabr } from "./sabr-clients.mjs";

// --- minimal UMP / protobuf encoders (mirror the harness decoders) ---
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const fV = (f, val) => Buffer.concat([varint((f << 3) | 0), varint(val)]);
const fB = (f, b) => Buffer.concat([varint((f << 3) | 2), varint(b.length), b]);
const ump = (type, payload) => { assert.ok(payload.length < 128); return Buffer.concat([Buffer.from([type]), Buffer.from([payload.length]), payload]); };
const SEG = 1000, END = 4, INIT = 100, CLEN = INIT + END * SEG;
const mediaHeader = (seq, init = false) => ump(20, Buffer.concat([fV(1, seq + 1), init ? fV(8, 1) : Buffer.alloc(0), fV(9, seq), fV(14, init ? INIT : SEG), fB(15, Buffer.concat([fV(1, Math.max(0, seq - 1) * 10), fV(2, init ? 0 : 10), fV(3, 1)]))]));
const fmtInit = () => ump(42, fV(4, END));
const body = (...parts) => Buffer.concat(parts);

const player = (ok = true) => ({ playabilityStatus: { status: ok ? "OK" : "ERROR" }, streamingData: { serverAbrStreamingUrl: "https://sabr.test/abr", adaptiveFormats: [{ itag: 251, bitrate: 1, contentLength: String(CLEN), lastModified: "7" }] }, playerConfig: { mediaCommonConfig: { mediaUstreamerRequestConfig: { videoPlaybackUstreamerConfig: Buffer.from("u").toString("base64") } } } });
const ctx = { visitorData: "v", cred: {}, webPot: null, cipher: { sts: null, transformNParamInUrl: (u) => u }, potBytes: Buffer.alloc(1), hasCookie: false };
const client = { key: "T", clientName: "T", clientVersion: "1", clientId: 1, ua: "ua", web: 0, auth: 0 };
const video = { videoId: "vid", videoPot: null };

/** Script the network: `abr` is a queue of responders for the SABR POSTs; players are counted. */
function script(abr) {
  const calls = { player: 0, abr: 0 };
  globalThis.fetch = async (url) => {
    if (String(url).includes("/player")) { calls.player++; return new Response(JSON.stringify(player()), { status: 200 }); }
    calls.abr++;
    const next = abr.shift();
    if (!next) return new Response(body(), { status: 200 });
    if (typeof next === "number") return new Response("", { status: next });
    return new Response(next, { status: 200 });
  };
  return calls;
}
const realFetch = globalThis.fetch;
test.afterEach(() => { globalThis.fetch = realFetch; });

test("a 403 mid-drain refreshes the session and the drain finishes whole", async () => {
  const calls = script([
    body(fmtInit(), mediaHeader(0, true), mediaHeader(1), mediaHeader(2)), // session 1: init + 2 segments
    403, 403,                                                              // then the wall (request + in-place retry)
    body(fmtInit(), mediaHeader(0, true), mediaHeader(3), mediaHeader(4)), // session 2 resumes from seg 2
  ]);
  const r = await drainClientSabr(ctx, client, video, { transportRetries: 0 });
  assert.equal(r.kind, "whole", JSON.stringify(r));
  assert.equal(r.refreshes, 1);
  assert.equal(calls.player, 2);
});

test("a 403 on the FIRST request is the client's verdict, no refresh", async () => {
  const calls = script([403]);
  const r = await drainClientSabr(ctx, client, video, { transportRetries: 0 });
  assert.equal(r.kind, "partial");
  assert.match(r.reason, /HTTP 403 \(0\/0\)$/);
  assert.equal(calls.player, 1);
});

test("a wall that survives every refresh is a capped drain, and says so", async () => {
  const seg = body(fmtInit(), mediaHeader(0, true), mediaHeader(1));
  const calls = script([seg, 403, 403, 403, 403, 403, 403]);
  const r = await drainClientSabr(ctx, client, video, { transportRetries: 0 });
  assert.equal(r.kind, "partial");
  assert.match(r.reason, /HTTP 403 \(1\/4\) after 2 session refresh\(es\)/);
  assert.equal(calls.player, 3);
});
