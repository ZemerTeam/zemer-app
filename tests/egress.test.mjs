// node --test tests/egress.test.mjs — the retry wrapper: network-class errors retry with backoff,
// everything else (HTTP statuses, application errors) passes through untouched.
import { test } from "node:test";
import assert from "node:assert/strict";
process.env.EGRESS_NO_RETRY = "1";
const { withRetries, isNetworkError } = await import("./egress.mjs");

const netErr = (code) => { const e = new TypeError("fetch failed"); e.cause = Object.assign(new Error("boom"), { code }); return e; };

test("retries connect timeouts / resets and returns the first success", async () => {
  let calls = 0; const sleeps = [];
  const f = withRetries(async () => { calls++; if (calls < 3) throw netErr(calls === 1 ? "UND_ERR_CONNECT_TIMEOUT" : "ECONNRESET"); return { status: 200 }; },
    { attempts: 4, backoffMs: 10, sleep: async (ms) => { sleeps.push(ms); }, log: () => {} });
  assert.equal((await f("https://x")).status, 200); assert.equal(calls, 3); assert.deepEqual(sleeps, [10, 20]);
});

test("gives up after the attempts and rethrows the last network error", async () => {
  let calls = 0;
  const f = withRetries(async () => { calls++; throw netErr("EAI_AGAIN"); }, { attempts: 3, backoffMs: 1, sleep: async () => {}, log: () => {} });
  await assert.rejects(f("https://x"), /fetch failed/); assert.equal(calls, 3);
});

test("non-network errors and HTTP statuses are never retried", async () => {
  let calls = 0;
  const f = withRetries(async () => { calls++; throw new Error("bad base-64"); }, { attempts: 4, sleep: async () => {}, log: () => {} });
  await assert.rejects(f("https://x"), /bad base-64/); assert.equal(calls, 1);
  let calls2 = 0;
  const g = withRetries(async () => { calls2++; return { status: 403 }; }, { attempts: 4, sleep: async () => {}, log: () => {} });
  assert.equal((await g("https://x")).status, 403); assert.equal(calls2, 1);
  assert.equal(isNetworkError(new Error("socket hang up")), true); assert.equal(isNetworkError(new Error("nope")), false);
  assert.equal(isNetworkError(netErr("UND_ERR_HEADERS_TIMEOUT")), true); assert.equal(isNetworkError(netErr("UND_ERR_BODY_TIMEOUT")), true);
});
