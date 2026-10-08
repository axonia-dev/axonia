import { z } from "zod";

const EMPTY_PLACE_VALUE = {
  location: null,
  joinUrl: null,
  joiningDetails: null,
};

/** Where a session meets. The three parts are one value: an occurrence either inherits all of its
 *  series' place or defines all of its own, so a passcode can never outlive the link it belonged to.
 *
 *  Absence is answered at the object as well as at each field. Per-field defaults alone cover only
 *  a place that is present but partial: a `"place": null`, or the key missing altogether, threw a
 *  `ZodError` that failed the whole parse - every session, every series and every schedule entry at
 *  once rather than one blank line. `nullish` rather than `default`, because `default` answers for
 *  `undefined` only and an explicit `null` is the likelier of the two shapes a serializer emits. */
export const meetingPlaceSchema = z
  .object({
    location: z.string().nullable().default(null),
    joinUrl: z.string().nullable().default(null),
    joiningDetails: z.string().nullable().default(null),
  })
  .nullish()
  .transform((place) => place ?? EMPTY_PLACE_VALUE);

export type MeetingPlace = z.infer<typeof meetingPlaceSchema>;

export const EMPTY_PLACE: MeetingPlace = EMPTY_PLACE_VALUE;

/** What a form holds while it is being typed in. Strings rather than nullable strings, because a
 *  controlled input cannot hold null. */
export interface MeetingPlaceDraft {
  location: string;
  joinUrl: string;
  joiningDetails: string;
}

export function placeIsEmpty(place: MeetingPlace): boolean {
  return (
    place.location === null &&
    place.joinUrl === null &&
    place.joiningDetails === null
  );
}

/** Trimmed on the way out: the server refuses a join link that does not start with http, so a
 *  pasted link carrying a stray space would be rejected. Blank becomes null, which is what returns
 *  an occurrence to inheritance rather than storing an empty place of its own. */
export function placeFromDraft(draft: MeetingPlaceDraft): MeetingPlace {
  return {
    location: blankToNull(draft.location),
    joinUrl: blankToNull(draft.joinUrl),
    joiningDetails: blankToNull(draft.joiningDetails),
  };
}

export function draftFromPlace(place: MeetingPlace): MeetingPlaceDraft {
  return {
    location: place.location ?? "",
    joinUrl: place.joinUrl ?? "",
    joiningDetails: place.joiningDetails ?? "",
  };
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}
