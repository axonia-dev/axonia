/**
 * A plain code-unit compare, not `localeCompare`: collation is locale- and ICU-build-dependent and can treat two
 * distinct tags as equal, at which point sort stability hands ordering back to the caller's array - exactly the
 * dependency every deterministic sort in this feature exists to remove.
 */
export function compareTags(a: string, b: string): number {
  if (a < b) return -1;
  if (a > b) return 1;
  return 0;
}
