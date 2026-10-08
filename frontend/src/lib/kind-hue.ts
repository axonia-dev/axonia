// A curated, warm-leaning palette. Kinds are tenant-managed, so colour is
// assigned deterministically by hashing a stable key (the kind id).
/** The product's community palette. The accent picker offers exactly these, and an unset accent
 *  falls back to one of them, so the two must not be allowed to diverge. */
export const COMMUNITY_PALETTE = [
  "#de3b78",
  "#6e3ceb",
  "#d9871f",
  "#1aa6a0",
  "#f2603c",
  "#1f9d63",
  "#3b82de",
  "#9b3cc4",
] as const;

export function kindHue(key: string): string {
  let sum = 0;
  for (let i = 0; i < key.length; i += 1) {
    sum += key.charCodeAt(i);
  }
  return (
    COMMUNITY_PALETTE[sum % COMMUNITY_PALETTE.length] ?? COMMUNITY_PALETTE[0]
  );
}
