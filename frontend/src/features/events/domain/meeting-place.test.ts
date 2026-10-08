import { describe, expect, it } from "vitest";

import {
  draftFromPlace,
  EMPTY_PLACE,
  meetingPlaceSchema,
  placeFromDraft,
  placeIsEmpty,
} from "@/features/events/domain/meeting-place";

describe("meetingPlaceSchema", () => {
  /**
   * The cliff the per-field defaults left standing. A place absent altogether - `null` from a
   * serializer that omits empties, or the key simply missing - used to throw, and a throw here is
   * not one blank line: it fails the whole session, series or schedule parse it sits inside.
   */
  it.each([
    ["null", null],
    ["undefined", undefined],
  ])("reads a place given as %s as no place at all", (_shape, value) => {
    expect(meetingPlaceSchema.parse(value)).toEqual(EMPTY_PLACE);
  });

  it("still fills in only the parts a partial place leaves out", () => {
    expect(
      meetingPlaceSchema.parse({ joinUrl: "https://meet.example/dojo" }),
    ).toEqual({
      location: null,
      joinUrl: "https://meet.example/dojo",
      joiningDetails: null,
    });
  });

  /** The insurance must not cost the real case: a populated place still parses whole. */
  it("carries a fully populated place through untouched", () => {
    const place = {
      location: "Room 4B",
      joinUrl: "https://meet.example/dojo",
      joiningDetails: "ID 391 2244",
    };

    expect(meetingPlaceSchema.parse(place)).toEqual(place);
  });

  it("still refuses a part that is present but of the wrong type", () => {
    expect(() => meetingPlaceSchema.parse({ joinUrl: 42 })).toThrow();
  });
});

describe("placeFromDraft", () => {
  it("maps a blank field to null so a cleared field returns the occurrence to inheritance", () => {
    expect(
      placeFromDraft({ location: "  ", joinUrl: "", joiningDetails: "\t" }),
    ).toEqual(EMPTY_PLACE);
  });

  it("trims what it keeps, so a pasted link carrying a stray space is not refused", () => {
    expect(
      placeFromDraft({
        location: " Room 4B ",
        joinUrl: " https://teams.microsoft.com/l/abc ",
        joiningDetails: "",
      }),
    ).toEqual({
      location: "Room 4B",
      joinUrl: "https://teams.microsoft.com/l/abc",
      joiningDetails: null,
    });
  });
});

describe("placeIsEmpty", () => {
  it("is true only when no part is set", () => {
    expect(placeIsEmpty(EMPTY_PLACE)).toBe(true);
    expect(
      placeIsEmpty({
        location: "Room 4B",
        joinUrl: null,
        joiningDetails: null,
      }),
    ).toBe(false);
  });
});

describe("draftFromPlace", () => {
  it("round-trips through a draft without inventing a value", () => {
    const place = {
      location: "Room 4B",
      joinUrl: null,
      joiningDetails: "ID 391 2244",
    };

    expect(placeFromDraft(draftFromPlace(place))).toEqual(place);
  });
});
