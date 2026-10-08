/**
 * The ring palette, reordered so that consecutive neighbourhoods are as far apart on the wheel as it has to offer.
 * Read in the palette's own order the first three groups came out teal, steel and fern - two of them greens - and a
 * reader was being asked to tell a community's biggest regions apart by a shift of about thirty degrees of hue.
 */
const ACCENT_TOKENS = [
  "--ring-teal-a",
  "--ring-ember-a",
  "--ring-steel-a",
  "--ring-gold-a",
  "--ring-violet-a",
  "--ring-fern-a",
];

export const ACCENT_COUNT = ACCENT_TOKENS.length;

/** Substituted where the theme has not painted yet, or under a test environment with no cascade at all. */
const UNTHEMED = "#8b7fa8";

/** WebGL reads no cascade, so the token is resolved against the live theme before three.js ever sees it. */
export function accentHexOf(slot: number): string {
  const token = ACCENT_TOKENS[Math.max(0, slot) % ACCENT_TOKENS.length]!;
  const painted = globalThis
    .getComputedStyle(document.documentElement)
    .getPropertyValue(token)
    .trim();
  return painted === "" ? UNTHEMED : painted;
}
