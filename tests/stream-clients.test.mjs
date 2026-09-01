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
import { loadStreamClients, loadStreamClientsIncludingBenched, parseStreamClients, fileVerdictRejects, sabrRoster } from "./stream-clients.mjs";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";

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

test("the SABR roster is the bundled table's sabr-capable entries, in table order", () => {
  const { clients } = loadStreamClients();
  assert.deepEqual(sabrRoster(clients).map((c) => c.key), ["WEB_REMIX", "VISIONOS", "TVHTML5_SIMPLY"]);
  // WEB_REMIX announces Windows 10.0 in the SABR streamerContext; its /player context stays OS-less.
  assert.deepEqual(clients[0].sabr, { osName: "Windows", osVersion: "10.0" });
  assert.equal(clients[0].osName, undefined);
});

test("sabr: absent/null = not SABR-usable, object = usable, anything else is malformed", () => {
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "web_cipher_pot", family: "MAIN",
  };
  const one = (extra) => parseStreamClients(JSON.stringify({ schemaVersion: 1, clients: [{ ...base, ...extra }] })).clients[0];
  assert.equal(one({}).sabr, undefined);
  assert.equal(one({ sabr: null }).sabr, undefined);
  assert.deepEqual(one({ sabr: {} }).sabr, {});
  assert.deepEqual(one({ sabr: { osName: "Windows", deviceMake: null } }).sabr, { osName: "Windows" });
  for (const bad of [true, "yes", [], { osName: 1 }, { osVersion: "bad version!" }]) {
    assert.throws(() => one({ sabr: bad }), `sabr=${JSON.stringify(bad)} must be malformed`);
  }
});

test("loadStreamClientsIncludingBenched surfaces benched entries parsed with the live rules", () => {
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "web_cipher_pot", family: "MAIN",
  };
  const dir = mkdtempSync(join(tmpdir(), "sc-"));
  const file = join(dir, "t.json");
  writeFileSync(file, JSON.stringify({ schemaVersion: 1, clients: [
    base, { ...base, key: "B", family: "B", enabled: false }, { ...base, key: "C", family: "C" },
  ] }));
  const t = loadStreamClientsIncludingBenched(file);
  assert.deepEqual(t.clients.map((c) => c.key), ["MAIN", "C"]);
  assert.deepEqual(t.benched.map((c) => c.key), ["B"]);
  assert.equal(t.benched[0].benched, true);
  assert.equal(t.benched[0].useWebPoTokens, true);
  // The bundled table today has no benched entry.
  assert.deepEqual(loadStreamClientsIncludingBenched().benched, []);
});

test("mirrors: the yt-dlp key an entry follows is optional harness metadata; pinned entries have none", () => {
  const { clients } = loadStreamClients();
  const byKey = Object.fromEntries(clients.map((c) => [c.key, c]));
  assert.equal(byKey.WEB_REMIX.mirrors, "web_music");
  assert.equal(byKey.VISIONOS.mirrors, "visionos");
  assert.equal(byKey.VISIONOS_0_1.mirrors, undefined, "the second-chance old config is pinned");
  const base = {
    key: "MAIN", clientName: "MAIN", clientVersion: "1.0", clientId: "1",
    userAgent: "Mozilla/5.0 (test)", protocol: "web_cipher_pot", family: "MAIN",
  };
  const one = (extra) => parseStreamClients(JSON.stringify({ schemaVersion: 1, clients: [{ ...base, ...extra }] })).clients[0];
  assert.equal(one({ mirrors: null }).mirrors, undefined);
  assert.throws(() => one({ mirrors: "Bad Key" }));
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
