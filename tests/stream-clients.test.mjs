// Unit tests for the stream-client loader (no network, no cookie):
//   node --test tests/stream-clients.test.mjs
//
// Includes the cross-language parity sweep: the golden fixtures in
// cipher/library/src/test/resources/stream-clients-parity/ are the SAME files the Kotlin
// StreamClientsParityFixturesTest pins — file-level accept/reject verdicts must agree between
// the app parser and this loader, or "the harness reads what a device reads" silently breaks.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { loadStreamClients, parseStreamClients, fileVerdictRejects } from "./stream-clients.mjs";

const FIXTURES = join(
  dirname(fileURLToPath(import.meta.url)),
  "..", "cipher", "library", "src", "test", "resources", "stream-clients-parity",
);

test("parity: accept fixtures parse, reject fixtures reject", () => {
  const names = readdirSync(FIXTURES);
  assert.ok(names.some((n) => n.startsWith("accept-")));
  assert.ok(names.some((n) => n.startsWith("reject-")));
  for (const name of names) {
    const text = readFileSync(join(FIXTURES, name), "utf8");
    const rejects = fileVerdictRejects(text);
    if (name.startsWith("accept-")) assert.equal(rejects, false, `${name} must be accepted`);
    if (name.startsWith("reject-")) assert.equal(rejects, true, `${name} must be rejected`);
  }
});

test("bundled asset loads with the expected chain shape", () => {
  const { clients, families } = loadStreamClients();
  assert.ok(clients.length >= 2, "chain needs a main + at least one fallback");
  const main = clients[0];
  assert.equal(main.clientName, "WEB_REMIX");
  assert.equal(main.protocol, "web_cipher_pot");
  assert.equal(main.skipHeadValidation, true);
  // Every client's family renders a toggle; the bundled file carries meta for each.
  for (const c of clients) assert.ok(families[c.family], `family ${c.family} missing meta`);
});

test("derived booleans track the protocol (the app's single-source rule)", () => {
  const { clients } = loadStreamClients();
  for (const c of clients) {
    const isWeb = c.protocol === "web_cipher_pot";
    assert.equal(c.useSignatureTimestamp, isWeb, c.key);
    assert.equal(c.useWebPoTokens, isWeb, c.key);
  }
});

test("benched and unknown-protocol entries skip; malformed entries throw", () => {
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "web_cipher_pot", family: "MAIN",
  };
  const file = (extra) => JSON.stringify({ schemaVersion: 1, clients: [base, extra] });

  const benched = parseStreamClients(file({ ...base, key: "B", family: "B", enabled: false }));
  assert.deepEqual(benched.clients.map((c) => c.key), ["MAIN"]);
  assert.deepEqual(benched.skipped, ["B"]);

  const future = parseStreamClients(file({ ...base, key: "F", family: "F", protocol: "sabr" }));
  assert.deepEqual(future.skipped, ["F"]);

  // A malformed entry throws loud in the harness (the app would skip it and keep playing).
  assert.throws(() => parseStreamClients(file({ ...base, key: "X", clientId: "not-digits" })));
});

test("duplicate keys and an unusable main reject the file", () => {
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "direct", family: "MAIN",
  };
  assert.equal(fileVerdictRejects(JSON.stringify({ schemaVersion: 1, clients: [base, base] })), true);
  assert.equal(
    fileVerdictRejects(JSON.stringify({ schemaVersion: 1, clients: [{ ...base, enabled: false }] })),
    true,
  );
  assert.equal(
    fileVerdictRejects(JSON.stringify({ schemaVersion: 1, clients: [{ ...base, protocol: "sabr" }] })),
    true,
  );
});

test("header-injection shapes are refused", () => {
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "direct", family: "MAIN",
  };
  const evil = { ...base, key: "EVIL", family: "EVIL", userAgent: "Mozilla\r\nX-Evil: 1" };
  assert.throws(() => parseStreamClients(JSON.stringify({ schemaVersion: 1, clients: [base, evil] })));
});
