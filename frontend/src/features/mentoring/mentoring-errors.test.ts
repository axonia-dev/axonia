import { describe, expect, it } from "vitest";

import {
  isKnownRefusal,
  mentoringErrorMessage,
  pointerErrorMessage,
} from "@/features/mentoring/mentoring-errors";
import { ApiError } from "@/lib/api-client";

describe("mentoringErrorMessage", () => {
  it.each([
    ["MENTORING_OFF", "Mentoring is off in this community."],
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
    ["MENTORING_WORDS_CLOSED", "Words are closed for this pairing."],
    ["MENTORING_WORDS_NOT_YET", "Words open once the mentoring ends."],
    [
      "MENTORING_MEETING_DAY",
      "Pick a day between the start of the mentoring and today.",
    ],
    ["MENTORING_MEETING_PAST", "Pick today or a later day."],
    ["MENTORING_MEETING_TOO_FAR", "Pick a day within a year."],
    ["MENTORING_MEETING_TOPICS", "A meeting names at most five topics."],
    ["MENTORING_MEETING_TAKEN", "A meeting is already planned for that day."],
    [
      "MENTORING_MEETING_LIMIT",
      "Twelve meetings are planned already. Remove one first.",
    ],
    ["MENTORING_MEETING_PASSED", "This meeting has passed."],
    ["MENTORING_TOO_LONG", "That is too long. Shorten it and try again."],
    ["MENTORING_JOURNAL_FULL", "This journal is full. Remove something first."],
    ["REQUIRES_OWN_SESSION", "Mentoring needs the person’s own sign-in."],
  ])("names the refusal %s", (code, message) => {
    // when
    const actual = mentoringErrorMessage(new ApiError(409, "refused", code));

    // then
    expect(actual).toBe(message);
  });

  it.each([
    ["an unknown code", new ApiError(500, "boom", "SOMETHING_ELSE")],
    [
      "the retired already asked code",
      new ApiError(409, "asked", "MENTORING_WORDS_ALREADY_ASKED"),
    ],
    ["a refusal without a code", new ApiError(500, "boom")],
    ["a network failure", new Error("offline")],
  ])("falls back for %s", (_name, error) => {
    // when
    const actual = mentoringErrorMessage(error);

    // then
    expect(actual).toBe("Could not save that.");
  });
});

describe("pointerErrorMessage", () => {
  it("names a refused address", () => {
    // when
    const actual = pointerErrorMessage(
      new ApiError(400, "Not a web address", "field_invalid"),
    );

    // then
    expect(actual).toBe("Paste a full web address starting with https.");
  });

  it("names any other refusal as the journal does", () => {
    // when
    const actual = pointerErrorMessage(
      new ApiError(400, "too long", "MENTORING_TOO_LONG"),
    );

    // then
    expect(actual).toBe("That is too long. Shorten it and try again.");
  });
});

describe("isKnownRefusal", () => {
  it.each([
    ["a non-member", new ApiError(403, "forbidden"), true],
    ["a missing journal", new ApiError(404, "missing"), true],
    ["switched off", new ApiError(409, "off", "MENTORING_OFF"), true],
    [
      "a delegated session",
      new ApiError(401, "own", "REQUIRES_OWN_SESSION"),
      true,
    ],
    ["a server failure", new ApiError(500, "boom"), false],
    ["a network failure", new Error("offline"), false],
  ])("is %s: %s", (_name, error, expected) => {
    // when
    const actual = isKnownRefusal(error);

    // then
    expect(actual).toBe(expected);
  });
});
