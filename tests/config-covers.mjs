// Does a player_configs.json file cover a hash, by the rules a device applies?
//
//   node tests/config-covers.mjs <hash> <path-to-player_configs.json>
//
// Prints "covered" or "uncovered" (exit 0). Exits 1 with the validation error when the
// file itself is invalid — devices would reject it wholesale, so a textually-present hash
// in an invalid file is NOT covered.

import { readFileSync } from "node:fs";
import { parsePlayerConfigs } from "./player-configs.mjs";

const [hash, file] = process.argv.slice(2);
if (!hash || !file) {
  console.error("usage: node tests/config-covers.mjs <hash> <player_configs.json>");
  process.exit(2);
}

const players = parsePlayerConfigs(readFileSync(file, "utf8"), file);
const keys = new Set();
for (const [primary, entry] of Object.entries(players)) {
  keys.add(primary);
  for (const alias of entry.aliases ?? []) keys.add(alias);
}
console.log(keys.has(hash) ? "covered" : "uncovered");
