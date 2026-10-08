import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { ApiError } from "@/lib/api-client";
import { MAX_TAG_LENGTH } from "@/lib/normalize-tag";

export const OWN_SIGN_IN_REFUSAL =
  "Areas to learn need the person’s own sign-in.";

const REFUSALS = new Map([
  ["LEARNING_INTENT_LIMIT", "At most 10 areas per community."],
  ["COMMUNITY_ARCHIVED", "This community is archived."],
  ["TAG_BLANK", "An area needs a name."],
  ["TAG_TOO_LONG", `An area can be at most ${MAX_TAG_LENGTH} characters.`],
]);

export function learningErrorMessage(error: unknown): string {
  if (isOwnSignInRefusal(error)) return OWN_SIGN_IN_REFUSAL;
  const refusal =
    error instanceof ApiError ? REFUSALS.get(error.code ?? "") : undefined;
  return refusal ?? "Could not add that area.";
}

export function learningRemovalErrorMessage(error: unknown): string {
  return isOwnSignInRefusal(error)
    ? OWN_SIGN_IN_REFUSAL
    : "Could not remove that area.";
}
