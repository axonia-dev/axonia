import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { curationErrorMessage } from "@/features/expertise/curation-errors";
import { ApiError } from "@/lib/api-client";

const OWN_SIGN_IN = "Declarations need the person’s own sign-in.";

const REFUSALS = new Map([
  ["DECLARATION_LIMIT", "At most 20 areas per community."],
  ["COMMUNITY_ARCHIVED", "This community is archived."],
]);

export function declarationErrorMessage(error: unknown): string {
  if (isOwnSignInRefusal(error)) return OWN_SIGN_IN;
  const refusal =
    error instanceof ApiError ? REFUSALS.get(error.code ?? "") : undefined;
  if (refusal !== undefined) return refusal;
  return curationErrorMessage(error, "Could not add that area.");
}

export function declarationRemovalErrorMessage(error: unknown): string {
  return isOwnSignInRefusal(error)
    ? OWN_SIGN_IN
    : "Could not remove that area.";
}
