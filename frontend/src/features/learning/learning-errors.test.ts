import { describe, expect, it } from "vitest";

import {
  learningErrorMessage,
  learningRemovalErrorMessage,
} from "@/features/learning/learning-errors";
import { ApiError } from "@/lib/api-client";

function apiError(code: string): ApiError {
  return new ApiError(409, "refused", code);
}

describe("learningErrorMessage", () => {
  it.each([
    ["LEARNING_INTENT_LIMIT", "At most 10 areas per community."],
    ["COMMUNITY_ARCHIVED", "This community is archived."],
    ["TAG_BLANK", "An area needs a name."],
    ["TAG_TOO_LONG", "An area can be at most 50 characters."],
  ])("names the refusal %s", (code, message) => {
    // when
    const actual = learningErrorMessage(apiError(code));

    // then
    expect(actual).toBe(message);
  });

  it("names the own sign-in an add needs", () => {
    // when
    const actual = learningErrorMessage(
      new ApiError(403, "refused", "REQUIRES_OWN_SESSION"),
    );

    // then
    expect(actual).toBe("Areas to learn need the person’s own sign-in.");
  });

  it("falls back for a refusal it does not know", () => {
    // when
    const actual = learningErrorMessage(apiError("SOMETHING_ELSE"));

    // then
    expect(actual).toBe("Could not add that area.");
  });

  it("falls back for a refusal that carries no code", () => {
    // when
    const actual = learningErrorMessage(new ApiError(500, "boom"));

    // then
    expect(actual).toBe("Could not add that area.");
  });

  it("falls back for anything else", () => {
    // when
    const actual = learningErrorMessage(new Error("offline"));

    // then
    expect(actual).toBe("Could not add that area.");
  });
});

describe("learningRemovalErrorMessage", () => {
  it("names the own sign-in a removal needs", () => {
    // when
    const actual = learningRemovalErrorMessage(
      new ApiError(403, "refused", "REQUIRES_OWN_SESSION"),
    );

    // then
    expect(actual).toBe("Areas to learn need the person’s own sign-in.");
  });

  it("falls back for anything else", () => {
    // when
    const actual = learningRemovalErrorMessage(new Error("offline"));

    // then
    expect(actual).toBe("Could not remove that area.");
  });
});
