// Credential loader. Reads the local innertube_cookie.txt (the dumped
// "***INNERTUBE COOKIE*** =..." format) so the test scripts use the same
// logged-in session the app uses, without hitting a remote credential worker.
//
// Resolution order for each field:
//   1. env override  (YT_COOKIE / YT_VISITOR_DATA / YT_DATASYNC_ID)
//   2. file          (COOKIE_FILE env, else ../innertube_cookie.txt)
//   3. CRED_URL       remote worker, only if nothing local is found
//
// Returns { cookie, visitorData, dataSyncId, source }.

import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_FILE = path.join(HERE, "..", "innertube_cookie.txt");

// Map the dumped "***LABEL*** =value" lines to our field names.
const LABELS = {
  "INNERTUBE COOKIE": "cookie",
  "VISITOR DATA": "visitorData",
  "DATASYNC ID": "dataSyncId",
  "ACCOUNT NAME": "accountName",
  "ACCOUNT EMAIL": "accountEmail",
  "ACCOUNT CHANNEL HANDLE": "accountHandle",
};

export function parseCookieFile(text) {
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\*\*\*\s*(.+?)\s*\*\*\*\s*=(.*)$/);
    if (!m) continue;
    const field = LABELS[m[1].trim().toUpperCase()];
    if (field) out[field] = m[2].trim();
  }
  return out;
}

export async function getCred() {
  const file = process.env.COOKIE_FILE || DEFAULT_FILE;
  let fromFile = {};
  let source = "env";
  if (fs.existsSync(file)) {
    try {
      fromFile = parseCookieFile(fs.readFileSync(file, "utf8"));
      source = file;
    } catch (e) {
      console.warn(`cred: failed to read ${file}: ${e.message}`);
    }
  }

  let cookie = process.env.YT_COOKIE || fromFile.cookie || "";
  let visitorData = process.env.YT_VISITOR_DATA || fromFile.visitorData || "";
  let dataSyncId = process.env.YT_DATASYNC_ID || fromFile.dataSyncId || "";

  // Fall back to the remote credential worker only if we have nothing local.
  if (!cookie && !visitorData && process.env.CRED_URL) {
    try {
      const j = await (await fetch(process.env.CRED_URL)).json();
      cookie = j.cookie || "";
      visitorData = j.visitorData || "";
      dataSyncId = j.dataSyncId || "";
      source = process.env.CRED_URL;
    } catch (e) {
      console.warn(`cred: CRED_URL fetch failed: ${e.message}`);
    }
  }

  return { cookie, visitorData, dataSyncId, source };
}

// Print a one-line summary safe for logs (no secrets).
export function describeCred(c) {
  const has = (s) => (s ? "yes" : "NO");
  return (
    `cookie=${has(c.cookie)} SAPISID=${/SAPISID=/.test(c.cookie) ? "yes" : "NO"} ` +
    `visitorData=${(c.visitorData || "").slice(0, 14)}${c.visitorData ? "…" : ""} ` +
    `dataSyncId=${c.dataSyncId ? c.dataSyncId.slice(0, 8) + "…" : "(none)"} ` +
    `[${c.source}]`
  );
}
