import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { ApiError } from "@/lib/api-client";

export const OWN_SIGN_IN_REFUSAL = "Mentoring needs the person’s own sign-in.";
export const MENTORING_IS_OFF = "Mentoring is off in this community.";
export const WORDS_CLOSED = "Words are closed for this pairing.";
export const MEETING_DAY_RANGE =
  "Pick a day between the start of the mentoring and today.";

const REFUSALS = new Map([
  ["MENTORING_OFF", MENTORING_IS_OFF],
  ["COMMUNITY_ARCHIVED", "This community is archived."],
  ["MENTORING_SELF", "A person cannot be paired with themselves."],
  ["MENTORING_NOT_AVAILABLE", "This person is not taking part right now."],
  ["MENTORING_PROPOSAL_OPEN", "A proposal to this person is already open."],
  [
    "MENTORING_DECLINED_RECENTLY",
    "Not this time. A new proposal opens 30 days after the decline.",
  ],
  ["MENTORING_ALREADY_PAIRED", "Already paired with this person here."],
  [
    "MENTORING_MENTOR_FULL",
    "This mentor has no room for another mentee right now.",
  ],
  ["MENTORING_PROPOSAL_CLOSED", "This proposal is no longer open."],
  ["MENTORING_PAIRING_ENDED", "This mentoring has ended."],
  ["MENTORING_WORDS_CLOSED", WORDS_CLOSED],
  ["MENTORING_WORDS_NOT_YET", "Words open once the mentoring ends."],
  ["MENTORING_MEETING_DAY", MEETING_DAY_RANGE],
  ["MENTORING_MEETING_PAST", "Pick today or a later day."],
  ["MENTORING_TOO_LONG", "That is too long. Shorten it and try again."],
  ["MENTORING_JOURNAL_FULL", "This journal is full. Remove something first."],
]);

const NOT_A_WEB_ADDRESS = "Paste a full web address starting with https.";

function codeOf(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined;
}

export function mentoringErrorMessage(error: unknown): string {
  if (isOwnSignInRefusal(error)) return OWN_SIGN_IN_REFUSAL;
  return REFUSALS.get(codeOf(error) ?? "") ?? "Could not save that.";
}

/** A refused address names what was wrong with it rather than the generic failure. */
export function pointerErrorMessage(error: unknown): string {
  return codeOf(error) === "field_invalid"
    ? NOT_A_WEB_ADDRESS
    : mentoringErrorMessage(error);
}

const SETTLED_STATUSES = new Set([403, 404, 409]);

/** A refusal that answers the same however often it is asked: not a member, not found, a rule, or a session that is not the person's own. */
export function isKnownRefusal(error: unknown): boolean {
  return (
    isOwnSignInRefusal(error) ||
    (error instanceof ApiError && SETTLED_STATUSES.has(error.status))
  );
}
