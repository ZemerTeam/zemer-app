// Loader for the SINGLE source of truth for stream clients:
//   cipher/library/src/main/assets/stream_clients.json   (zemer-cipher submodule)
//
// That one file is (1) bundled in the APK as the offline default, (2) fetched raw from GitHub by
// running apps (StreamClientStore) so client kills/rotations are fixed with no APK update, and
// (3) read here by the harness — so app, devices, and tests can never drift apart.
//
// Validation mirrors StreamClientParser.kt. FILE-level verdicts are identical (pinned by the
// shared fixtures in cipher/library/src/test/resources/stream-clients-parity/). Entry-level
// handling matches the app for the two EXPECTED skips — a benched entry (`enabled: false`) and an
// unknown protocol slug (forward compat) — but THROWS on a malformed entry, where the app would
// skip and keep playing: in tests, loud is right.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

// STREAM_CLIENTS_JSON overrides the source file — used by validate-stream-clients.mjs to run
// the whole harness against a CANDIDATE table before it is pushed.
export const STREAM_CLIENTS_PATH = process.env.STREAM_CLIENTS_JSON || join(
  dirname(fileURLToPath(import.meta.url)),
  "..", "cipher", "library", "src", "main", "assets", "stream_clients.json",
);

const SUPPORTED_SCHEMA_VERSION = 1;
// Mirrors StreamClientParser.MAX_CLIENTS: the one bound whose cost is live network round-trips
// (one /player request per client on failure).
const MAX_CLIENTS = 32;
const KEY_RE = /^[A-Z0-9_]{1,32}$/;
const CLIENT_ID_RE = /^[0-9]{1,4}$/;
const VERSIONISH_RE = /^[A-Za-z0-9._-]{1,32}$/;
const GROUP_RE = /^[a-z0-9_-]{1,16}$/;
const PROTOCOLS = new Set(["web_cipher_pot", "direct"]);

const headerSafe = (v, maxLen) =>
  typeof v === "string" && v.length > 0 && v.length <= maxLen && /^[\x20-\x7E]+$/.test(v);

class FileReject extends Error {}

function req(cond, reason) {
  if (!cond) throw new FileReject(reason);
}

// Returns the parsed client, "disabled", "unknown-protocol", or throws on a malformed entry.
function parseEntry(obj, label) {
  if (obj === null || typeof obj !== "object" || Array.isArray(obj)) {
    throw new Error(`${label}: entry is not an object`);
  }
  // Explicit JSON null == absent, matching StreamClientParser's JsonNull handling.
  if ("enabled" in obj && obj.enabled !== null && obj.enabled !== undefined) {
    if (typeof obj.enabled !== "boolean") throw new Error(`${label}: enabled must be a boolean`);
    if (obj.enabled === false) return "disabled";
  }
  const bad = (what) => { throw new Error(`${label}: invalid ${what}`); };

  const str = (field, re) => {
    const v = obj[field];
    if (typeof v !== "string" || !re.test(v)) bad(field);
    return v;
  };
  const key = str("key", KEY_RE);
  const clientName = str("clientName", KEY_RE);
  const clientVersion = str("clientVersion", VERSIONISH_RE);
  const clientId = str("clientId", CLIENT_ID_RE);
  const userAgent = obj.userAgent;
  if (!headerSafe(userAgent, 300)) bad("userAgent");
  const protocol = obj.protocol;
  if (typeof protocol !== "string") bad("protocol");
  if (!PROTOCOLS.has(protocol)) return "unknown-protocol";
  const family = str("family", KEY_RE);

  const optional = (field, valid) => {
    if (!(field in obj) || obj[field] === null || obj[field] === undefined) return undefined;
    const v = obj[field];
    if (typeof v !== "string" || !valid(v)) bad(field);
    return v;
  };
  const osName = optional("osName", (v) => headerSafe(v, 64));
  const osVersion = optional("osVersion", (v) => VERSIONISH_RE.test(v));
  const deviceMake = optional("deviceMake", (v) => headerSafe(v, 64));
  const deviceModel = optional("deviceModel", (v) => headerSafe(v, 64));
  const androidSdkVersion = optional("androidSdkVersion", (v) => VERSIONISH_RE.test(v));

  const bool = (field) => {
    if (!(field in obj) || obj[field] === null || obj[field] === undefined) return false;
    if (typeof obj[field] !== "boolean") bad(field);
    return obj[field];
  };

  // `sabr`: absent/null = not SABR-usable; an object (possibly empty) = SABR-usable, with the same
  // optional os/device identity overrides (same shapes) for the SABR streamerContext. Anything
  // else is a malformed entry (the app skips it; here: loud).
  let sabr;
  if ("sabr" in obj && obj.sabr !== null && obj.sabr !== undefined) {
    const o = obj.sabr;
    if (o === null || typeof o !== "object" || Array.isArray(o)) bad("sabr");
    const sub = (field, valid) => {
      if (!(field in o) || o[field] === null || o[field] === undefined) return undefined;
      const v = o[field];
      if (typeof v !== "string" || !valid(v)) bad(`sabr.${field}`);
      return v;
    };
    const s = {
      osName: sub("osName", (v) => headerSafe(v, 64)),
      osVersion: sub("osVersion", (v) => VERSIONISH_RE.test(v)),
      deviceMake: sub("deviceMake", (v) => headerSafe(v, 64)),
      deviceModel: sub("deviceModel", (v) => headerSafe(v, 64)),
      androidSdkVersion: sub("androidSdkVersion", (v) => VERSIONISH_RE.test(v)),
    };
    sabr = Object.fromEntries(Object.entries(s).filter(([, v]) => v !== undefined));
  }

  const isWeb = protocol === "web_cipher_pot";
  return {
    key, clientName, clientVersion, clientId, userAgent, protocol, family,
    ...(osName && { osName }),
    ...(osVersion && { osVersion }),
    ...(deviceMake && { deviceMake }),
    ...(deviceModel && { deviceModel }),
    ...(androidSdkVersion && { androidSdkVersion }),
    loginSupported: bool("loginSupported"),
    loginRequired: bool("loginRequired"),
    isEmbedded: bool("isEmbedded"),
    skipHeadValidation: bool("skipHeadValidation"),
    ...(sabr && { sabr }),
    // Derived exactly like YouTubeClient (the protocol is the single source of truth).
    useSignatureTimestamp: isWeb,
    useWebPoTokens: isWeb,
  };
}

/**
 * Parse a stream_clients.json TEXT. Throws FileReject with the same file-level verdicts as
 * StreamClientParser.kt; throws a plain Error on a malformed non-main entry (harness-loud).
 * Returns { clients, families, skipped } — clients ordered, entry 0 = main.
 */
export function parseStreamClients(text) {
  let root;
  try {
    root = JSON.parse(text);
  } catch (e) {
    throw new FileReject(`malformed JSON: ${e.message}`);
  }
  req(root !== null && typeof root === "object" && !Array.isArray(root), "root is not a JSON object");
  req(
    typeof root.schemaVersion === "number" && Number.isInteger(root.schemaVersion),
    "schemaVersion missing or not an int",
  );
  req(root.schemaVersion > 0, "schemaVersion must be positive");
  req(
    root.schemaVersion <= SUPPORTED_SCHEMA_VERSION,
    `unsupported schemaVersion ${root.schemaVersion} (supported: ${SUPPORTED_SCHEMA_VERSION})`,
  );
  req(Array.isArray(root.clients), "clients missing or not an array");

  const clients = [];
  const skipped = [];
  const seen = new Set();
  for (const [i, entry] of root.clients.entries()) {
    let parsed;
    try {
      parsed = parseEntry(entry, `clients[${i}]`);
    } catch (e) {
      // The MAIN row (entry 0) being malformed is a file-level reject in the app too.
      if (i === 0) throw new FileReject("main client entry (clients[0]) is invalid or disabled");
      throw e;
    }
    if (parsed === "disabled" || parsed === "unknown-protocol") {
      if (i === 0) throw new FileReject("main client entry (clients[0]) is invalid or disabled");
      skipped.push(entry?.key ?? `clients[${i}]`);
      continue;
    }
    req(!seen.has(parsed.key), `duplicate client key '${parsed.key}'`);
    seen.add(parsed.key);
    clients.push(parsed);
  }
  req(clients.length > 0, "no usable client entries (never-zero-clients invariant)");
  req(clients.length <= MAX_CLIENTS, `too many client entries (${clients.length} > ${MAX_CLIENTS})`);

  // Families are display metadata: bad rows skipped, duplicate ids keep the first (app parity).
  const families = {};
  if (Array.isArray(root.families)) {
    for (const row of root.families) {
      if (row === null || typeof row !== "object" || Array.isArray(row)) continue;
      const { id, title, group } = row;
      if (typeof id !== "string" || !KEY_RE.test(id)) continue;
      if (!headerSafe(title, 48)) continue;
      if (typeof group !== "string" || !GROUP_RE.test(group)) continue;
      if (!(id in families)) families[id] = { id, title, group };
    }
  }

  return { clients, families, skipped };
}

/**
 * The SABR roster: the sabr-capable entries in table order — what the app's SabrRoster runs.
 * Scripts probing SABR against "the app's clients" must take this, not a hand-kept list.
 */
export function sabrRoster(clients) {
  return clients.filter((c) => c.sabr);
}

/** True iff parse() rejects the whole file (the parity-fixture verdict). */
export function fileVerdictRejects(text) {
  try {
    parseStreamClients(text);
    return false;
  } catch (e) {
    if (e instanceof FileReject) return true;
    throw e; // malformed non-main entry: not a file-level verdict — surface it
  }
}

/**
 * THE web-transform predicate, mirroring YTPlayerUtils.clientNeedsNTransform (protocol, never a
 * client-name list — the name list was retired with the remote table). Scripts must call this
 * instead of re-deriving it, or the harness can prove a drain the app path never runs.
 */
export function needsWebTransforms(client) {
  if (client.protocol) return client.protocol === "web_cipher_pot";
  // Defs that predate the table and carry no protocol — the hand-defined browse-only WEB and the
  // retired clients in clients-retired.mjs, which several probe scripts still exercise. Fall back
  // to the pre-table signal so those probes keep behaving exactly as they did.
  return Boolean(client.useWebPoTokens) ||
    ["WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5"].includes(client.clientName);
}

export function loadStreamClients(path = STREAM_CLIENTS_PATH) {
  let text;
  try {
    text = readFileSync(path, "utf8");
  } catch (e) {
    throw new Error(
      `stream_clients.json not found at ${path} — is the cipher submodule checked out? ` +
      `(git submodule update --init --recursive)`,
    );
  }
  return parseStreamClients(text);
}
