import fs from "node:fs";
export const norm = (s) => (s || "").normalize("NFKD").replace(/[֑-ׇ]/g, "").toLowerCase()
  .replace(/[’'`"״׳]/g, "").replace(/\(.*?\)|\[.*?\]/g, " ").replace(/[^a-z0-9א-ת ]+/g, " ").replace(/\s+/g, " ").trim();
export const tokens = (s) => new Set(norm(s).split(" ").filter((t) => t.length > 1));
export function jaccard(a, b) { const A = tokens(a), B = tokens(b); if (!A.size || !B.size) return 0; let i = 0; for (const t of A) if (B.has(t)) i++; return i / (A.size + B.size - i); }
// Jyrics titles are usually "English - Hebrew" or "English (Hebrew)"; split into candidate forms.
export const titleForms = (t) => [...new Set(t.split(/\s+[-–]\s+|\s*\/\s*|\(|\)/).map((x) => norm(x)).filter((x) => x.length >= 2).concat([norm(t)]))];
export function titleMatch(a, b) { const fa = titleForms(a), fb = titleForms(b); let best = 0; for (const x of fa) for (const y of fb) { if (x === y) return 1; best = Math.max(best, jaccard(x, y)); } return best; }
export function loadWhitelist() { return JSON.parse(fs.readFileSync(new URL("./.cache/whitelist.json", import.meta.url), "utf8")); }
export function loadJyrics() { return JSON.parse(fs.readFileSync(new URL("./.cache/jyrics.json", import.meta.url), "utf8")); }
