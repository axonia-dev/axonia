import { MAX_NAMED_AREA_NAME_LENGTH } from "@/features/expertise/domain/named-area";
import { ApiError } from "@/lib/api-client";
import { MAX_TAG_LENGTH } from "@/lib/normalize-tag";

/**
 * What the backend refuses a curation verb for, said back to the curator.
 *
 * Rename and delete have no undo, so a bare "couldn't do that" is not enough: the curator has to
 * be told what to change. RENAME_TARGET_MERGED is why this exists at all. The rename dialog only
 * knows the community's canonical tags, so typing the name of a merged variant passes every check
 * it can make, and only the backend knows better.
 */
const MESSAGES: Record<string, string> = {
  TAG_MERGED: "That tag is merged with others. Split them apart first.",
  RENAME_TARGET_MERGED:
    "That name is merged into another tag. Split it out first, or pick a different name.",
  SELF_RENAME: "That is already the tag's name.",
  TAG_BLANK: "A tag needs a name.",
  TAG_TOO_LONG: `A tag can be at most ${MAX_TAG_LENGTH} characters.`,
  TAG_NOT_IN_COMMUNITY: "This community doesn't use that tag.",
  NAMED_AREA_NAME_BLANK: "A named area needs a name.",
  NAMED_AREA_NAME_TOO_LONG: `A named area's name can be at most ${MAX_NAMED_AREA_NAME_LENGTH} characters.`,
};

export function curationErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.code) {
    const message = MESSAGES[error.code];
    if (message) {
      return message;
    }
  }
  return fallback;
}

/** A 404 carries no code: the only thing a named-area verb can miss is the named area, deleted elsewhere. */
export function namedAreaErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof ApiError && error.status === 404) {
    return "That named area no longer exists.";
  }
  return curationErrorMessage(error, fallback);
}
