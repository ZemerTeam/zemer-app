// whitelist.mjs — read the kosher artist list straight from Firestore over plain HTTPS.
//
// The `artistsWhitelist` collection is world-readable (the app reads it for anonymous users), so the
// CLIENT API key from google-services.json is enough — NO Firebase Admin SDK / service account. We
// read the key from the gitignored google-services.json at the repo root (same source the app uses)
// or FIREBASE_API_KEY, so no key is ever hardcoded in committed code.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROJECT = "zemer-app";
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

export function apiKey() {
  if (process.env.FIREBASE_API_KEY) return process.env.FIREBASE_API_KEY;
  const gsj = path.join(HERE, "..", "..", "google-services.json");
  const j = JSON.parse(fs.readFileSync(gsj, "utf8"));
  const k = j.client?.[0]?.api_key?.[0]?.current_key;
  if (!k) throw new Error("no api_key in google-services.json");
  return k;
}

// Firestore REST encodes each field as { stringValue|booleanValue|timestampValue|... }. Flatten it.
function flatten(fields = {}) {
  const out = {};
  for (const [k, v] of Object.entries(fields)) {
    out[k] =
      v.stringValue ?? v.booleanValue ?? v.timestampValue ?? v.integerValue ?? v.doubleValue ?? null;
  }
  return out;
}

// Returns [{ id, name, isFemale, isKidZone, ... raw flags }]. id = the channelId (doc name).
export async function fetchWhitelist({ key = apiKey(), pageSize = 300 } = {}) {
  const all = [];
  let pageToken = null;
  do {
    const url = new URL(`${BASE}/artistsWhitelist`);
    url.searchParams.set("pageSize", String(pageSize));
    url.searchParams.set("key", key);
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const res = await fetch(url);
    if (!res.ok) throw new Error(`whitelist fetch http=${res.status}: ${await res.text()}`);
    const j = await res.json();
    for (const doc of j.documents ?? []) {
      const id = doc.name.split("/").pop();
      all.push({ id, ...flatten(doc.fields) });
    }
    pageToken = j.nextPageToken ?? null;
  } while (pageToken);
  return all;
}

// Read the version stamp clients gate on (databasenumber/latest -> "update").
export async function whitelistVersion({ key = apiKey() } = {}) {
  const url = `${BASE}/databasenumber/latest?key=${key}`;
  const res = await fetch(url);
  if (!res.ok) return null;
  const j = await res.json();
  return j.fields?.update?.stringValue ?? null;
}
