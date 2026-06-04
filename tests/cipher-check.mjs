// Hard-data check: run zemer-cipher's FunctionNameExtractor regexes (ported verbatim
// from FunctionNameExtractor.kt) against the live player JS to see whether the cipher
// can actually extract the signature + n-transform functions for the CURRENT player.
//
//   node tests/cipher-check.mjs            (expects /tmp/player_base.js)
import fs from "node:fs";

const PATH = process.env.PLAYER_JS || "/tmp/player_base.js";
const js = fs.readFileSync(PATH, "utf8");
console.log(`player.js: ${js.length} bytes (${PATH})\n`);

// --- Q-array obfuscation (the thing the cipher was updated for, on player 74edf1a3) ---
const Q_ARRAY = /var\s+Q\s*=\s*"[^"]+"\s*\.\s*split\s*\(\s*"\}"\s*\)/;
const qMatch = js.match(Q_ARRAY);
console.log(`Q-array obfuscation: ${qMatch ? "YES" : "no"}`);
console.log(`"enhanced_except_" present: ${/enhanced_except_/.test(js)}`);
console.log(`'.get("n")' present: ${/\.get\("n"\)/.test(js)}\n`);

// --- SIG function patterns (verbatim from FunctionNameExtractor.SIG_FUNCTION_PATTERNS) ---
const SIG = [
  /&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)/,
  /\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\(/,
  /\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\(/,
  /\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)/,
  /\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\(/,
  /\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\(/,
];
console.log("--- SIG function patterns ---");
let sigName = null;
SIG.forEach((re, i) => {
  const m = js.match(re);
  if (m && !sigName) sigName = m[1];
  console.log(`  [${i}] ${m ? `MATCH name=${m[1]} arg=${m[2] ?? "-"}` : "no match"}`);
});
console.log(`SIG extracted: ${sigName ? `YES (${sigName})` : "NO"}\n`);

// --- N function patterns (verbatim from N_FUNCTION_PATTERNS) ---
const N = [
  /\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)/,
  /\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)/,
  /\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)/,
  /([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_/,
];
console.log("--- N function patterns ---");
let nName = null;
N.forEach((re, i) => {
  const m = js.match(re);
  if (m && !nName) nName = m[1];
  console.log(`  [${i}] ${m ? `MATCH groups=${JSON.stringify(m.slice(1))}` : "no match"}`);
});
console.log(`N extracted: ${nName ? `YES (${nName})` : "NO"}\n`);

// --- signatureTimestamp ---
const STS = [/signatureTimestamp['":\s]+(\d+)/, /sts['":\s]+(\d+)/, /"signatureTimestamp"\s*:\s*(\d+)/];
let sts = null;
for (const re of STS) { const m = js.match(re); if (m) { sts = m[1]; break; } }
console.log(`signatureTimestamp: ${sts ?? "NOT FOUND"}`);

// --- hardcoded fallback availability ---
const playerVer = "9c249f6f";
const KNOWN = ["74edf1a3"];
console.log(`\nhardcoded config for live player ${playerVer}: ${KNOWN.includes(playerVer) ? "yes" : "NO (only " + KNOWN.join(",") + ")"}`);

console.log("\n=== VERDICT ===");
const ok = sigName && nName;
if (ok) console.log(`Cipher CAN extract on ${playerVer}: sig=${sigName}, n=${nName}, sts=${sts}`);
else console.log(`Cipher CANNOT fully extract on ${playerVer}: sig=${sigName ? "ok" : "FAIL"}, n=${nName ? "ok" : "FAIL"} — and no hardcoded fallback → web clients will fail deciphering, falling back to direct-url clients.`);
