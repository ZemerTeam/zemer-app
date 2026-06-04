// Reverse-engineer the live player JS: locate the signature-decipher and n-transform
// functions and their call sites, so the cipher's FunctionNameExtractor regexes can be
// updated. Run against the variant the cipher fetches (player_ias).
//
//   PLAYER_JS=/tmp/player_ias.js node tests/analyze-player.mjs
import fs from "node:fs";

const js = fs.readFileSync(process.env.PLAYER_JS || "/tmp/player_ias.js", "utf8");
console.log(`player.js: ${js.length} bytes\n`);

function show(re, label, before = 80, after = 200, max = 4) {
  const g = new RegExp(re.source, re.flags.includes("g") ? re.flags : re.flags + "g");
  let m, n = 0;
  console.log(`### ${label}`);
  while ((m = g.exec(js)) && n < max) {
    n++;
    console.log(`  @${m.index}: ...${js.slice(Math.max(0, m.index - before), m.index + after).replace(/\s+/g, " ")}...`);
  }
  if (n === 0) console.log("  (no match)");
  console.log();
}

// ---- signature decipher ----
// classic call site: X.set("signature"|sp, FUNC(decodeURIComponent(...)))  OR  m=FUNC(decodeURIComponent(...))
show(/[a-zA-Z0-9_$]+\(decodeURIComponent\(/, "X(decodeURIComponent( — sig call candidates", 50, 70, 8);
// the decipher fn itself: NAME=function(a){a=a.split("");...;return a.join("")}
show(/[a-zA-Z0-9_$]{1,4}=function\([a-z]\)\{[a-z]=[a-z]\.split\(""\)/, "split/join decipher fn defs", 5, 180, 4);
show(/\.set\((?:"signature"|"sig"|[a-z]\.sp\b|[a-z]\.sp\|\|)/, ".set(signature|sig|sp)", 90, 90, 4);

// ---- n-transform ----
show(/\.get\("n"\)/, 'get("n") sites', 90, 160, 6);
// modern n-name forms: ...=NFUNC[idx](X)  guarded by get("n")  /  fromCharCode(110)
show(/b=String\.fromCharCode\(110\)/, "String.fromCharCode(110) (='n')", 30, 160, 3);
// n function def shape: NAME=function(a){var b=a.split(...)
show(/[a-zA-Z0-9_$]{1,4}=function\([a-z]\)\{var [a-z]=[a-z]\.split\(/, "n-style function defs (var b=a.split)", 5, 120, 4);

// ---- try a couple of yt-dlp-style name regexes ----
const sigYt = [
  /\b[a-zA-Z0-9$]{2,}&&\([a-zA-Z0-9$]=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(/,
  /([a-zA-Z0-9$]{2,})\(decodeURIComponent\([a-zA-Z0-9$.]+\)\)/,
  /\.set\([^,]+,encodeURIComponent\(([a-zA-Z0-9$]+)\(/,
];
console.log("### yt-dlp-style SIG name probes");
sigYt.forEach((re, i) => { const m = js.match(re); console.log(`  [${i}] ${m ? "name=" + m[1] : "no match"}`); });
console.log();

const nYt = [
  /\.get\("n"\)\)&&\([a-z]=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-z]\)/,
  /[a-zA-Z0-9$]+=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-z]\)(?:,[a-zA-Z0-9$]+\.set\("n"?,)/,
  /([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-z]\),[a-zA-Z0-9$]+\.set\("n"/,
];
console.log("### yt-dlp-style N name probes");
nYt.forEach((re, i) => { const m = js.match(re); console.log(`  [${i}] ${m ? "nfunc=" + m[1] + " idx=" + (m[2] ?? "-") : "no match"}`); });
