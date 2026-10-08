import { z } from "zod";

/** Backend bounds (`@Min`/`@Max`, mirrored from the CHECK constraints): a lead
 *  time can be same-day (0) up to a month out, and a grace period can be none
 *  up to two weeks (336 hours). */
export const SESSION_UPCOMING_LEAD_DAYS_MAX = 30;
export const WRAP_UP_GRACE_HOURS_MAX = 336;

export const communicationSettingsSchema = z.object({
  notifySessionUpcoming: z.boolean(),
  notifySessionWrapUp: z.boolean(),
  sessionUpcomingLeadDays: z
    .number()
    .int()
    .min(0)
    .max(SESSION_UPCOMING_LEAD_DAYS_MAX),
  wrapUpGraceHours: z.number().int().min(0).max(WRAP_UP_GRACE_HOURS_MAX),
});

export type CommunicationSettings = z.infer<typeof communicationSettingsSchema>;

/** A settings form's raw, editable field values: the two switches plus the
 *  two number inputs as the strings a text field actually holds. */
export interface CommunicationSettingsInput {
  notifySessionUpcoming: boolean;
  notifySessionWrapUp: boolean;
  sessionUpcomingLeadDays: string;
  wrapUpGraceHours: string;
}

/** A plain, non-negative whole number written in decimal digits only - no sign,
 *  point, exponent, or hex/octal/binary prefix - so `Number()`'s tolerance for
 *  `"0x10"` (16), `"1e1"` (10), or `" "` (0) can never let a saved value differ
 *  from what was actually typed. Combined with the upper bound in one predicate
 *  so the parser and the field-error message can never drift apart. */
function isBoundedWholeNumber(raw: string, max: number): boolean {
  const trimmed = raw.trim();
  return /^\d+$/.test(trimmed) && Number(trimmed) <= max;
}

/** The in-range integer a raw field value denotes, or null for anything the
 *  backend's `@Min`/`@Max` would reject. */
function parsedInt(raw: string, max: number): number | null {
  return isBoundedWholeNumber(raw, max) ? Number(raw.trim()) : null;
}

/** The message to show under a number field whose value is out of range,
 *  fractional, or non-numeric - or undefined while it's valid, or blank.
 *  Backs `TextInput`'s `errorMessage`, which also drives its `aria-invalid`, so
 *  an out-of-range value is never silent: the field itself says why Save is
 *  disabled, not just the button. Blank is deliberately exempt even though
 *  `parsedInt` still rejects it (Save stays disabled): a field cleared to be
 *  retyped would otherwise flash an error for every keystroke of the pause. */
export function communicationFieldError(
  raw: string,
  max: number,
): string | undefined {
  if (raw.trim() === "") return undefined;
  return isBoundedWholeNumber(raw, max)
    ? undefined
    : `Enter a whole number from 0 to ${max}.`;
}

/** Validates a form's raw field values against the exact bounds the backend's
 *  `@Min`/`@Max` enforce, returning the parsed settings or null when either
 *  number is out of range - the caller disables Save on null rather than
 *  sending a request the server would 400 on. */
export function parseCommunicationSettingsInput(
  input: Readonly<CommunicationSettingsInput>,
): CommunicationSettings | null {
  const sessionUpcomingLeadDays = parsedInt(
    input.sessionUpcomingLeadDays,
    SESSION_UPCOMING_LEAD_DAYS_MAX,
  );
  const wrapUpGraceHours = parsedInt(
    input.wrapUpGraceHours,
    WRAP_UP_GRACE_HOURS_MAX,
  );
  if (sessionUpcomingLeadDays === null || wrapUpGraceHours === null) {
    return null;
  }
  return {
    notifySessionUpcoming: input.notifySessionUpcoming,
    notifySessionWrapUp: input.notifySessionWrapUp,
    sessionUpcomingLeadDays,
    wrapUpGraceHours,
  };
}
