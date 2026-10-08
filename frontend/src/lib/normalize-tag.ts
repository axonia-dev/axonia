/** The hard ceiling: a tag lands in the source contexts' varchar(50) columns. */
export const MAX_TAG_LENGTH = 50;

/**
 * Mirrors the backend's TagNormalizer, so a tag the client shows or matches is the name that
 * will actually be stored: lowercase, drop accents, collapse every run of non-alphanumerics
 * into a single dash, then trim the dashes off both ends.
 */
export function normalizeTag(input: string): string {
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[^\p{ASCII}]/gu, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
