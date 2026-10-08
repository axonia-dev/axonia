// Generates the emoji catalog the picker lazy-imports.
//
// Committed output rather than a build step, and JSON rather than a .ts module: a generated TypeScript
// file of this size fights eslint's max-lines rule and knip's unused-export scan, and neither argument
// is worth having.
//
// Regenerate with: pnpm run emoji:generate
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

const require = createRequire(import.meta.url);
const source = require.resolve("unicode-emoji-json/data-by-emoji.json");
const raw = JSON.parse(readFileSync(source, "utf8"));

// Fail loudly rather than emitting a catalog with empty names if the upstream shape moves.
const firstKey = Object.keys(raw)[0];
const first = raw[firstKey];
if (
  !first ||
  typeof first.name !== "string" ||
  typeof first.group !== "string"
) {
  throw new Error(
    `unicode-emoji-json shape changed: expected { name, group } on "${firstKey}", got ${JSON.stringify(first)}`,
  );
}

// Component glyphs (skin tones, hair) are modifiers, not reactions anybody picks on their own.
const EXCLUDED_GROUPS = new Set(["Component"]);

const byGroup = new Map();
for (const [character, meta] of Object.entries(raw)) {
  if (EXCLUDED_GROUPS.has(meta.group)) continue;
  if (!byGroup.has(meta.group)) byGroup.set(meta.group, []);
  byGroup.get(meta.group).push({ c: character, n: meta.name });
}

const catalog = [...byGroup.entries()].map(([group, emoji]) => ({
  group,
  emoji,
}));

const out = path.join(
  import.meta.dirname,
  "..",
  "src",
  "features",
  "recognition",
  "domain",
  "emoji-catalog.json",
);
writeFileSync(out, `${JSON.stringify(catalog)}\n`);
console.log(
  `Wrote ${catalog.reduce((n, g) => n + g.emoji.length, 0)} emoji across ${catalog.length} groups`,
);
