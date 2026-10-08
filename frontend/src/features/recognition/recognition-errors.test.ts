import { describe, expect, it } from "vitest";

import { recognitionErrorMessage } from "@/features/recognition/recognition-errors";
import { ApiError } from "@/lib/api-client";

describe("recognitionErrorMessage", () => {
  it("maps NO_SELF_LOVE", () => {
    // when
    const actual = recognitionErrorMessage(
      new ApiError(409, "x", "NO_SELF_LOVE"),
    );

    // then
    expect(actual).toMatch(/cannot be loved by one of its own contributors/i);
  });

  it("maps ALREADY_LOVED", () => {
    // when
    const actual = recognitionErrorMessage(
      new ApiError(409, "x", "ALREADY_LOVED"),
    );

    // then
    expect(actual).toMatch(/already loved/i);
  });

  it("maps REACTION_LIMIT_REACHED", () => {
    // when
    const actual = recognitionErrorMessage(
      new ApiError(409, "x", "REACTION_LIMIT_REACHED"),
    );

    // then
    expect(actual).toMatch(/limit of custom reactions/i);
  });

  it("falls back for an unknown code", () => {
    // when
    const actual = recognitionErrorMessage(new ApiError(500, "x", "WAT"));

    // then
    expect(actual).toMatch(/something went wrong/i);
  });

  it("falls back for a non-ApiError", () => {
    // when
    const actual = recognitionErrorMessage(new Error("boom"));

    // then
    expect(actual).toMatch(/something went wrong/i);
  });
});
