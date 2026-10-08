import { describe, expect, it } from "vitest";

import {
  captureErrorMessage,
  creditErrorMessage,
} from "@/features/knowledge/knowledge-errors";
import { ApiError } from "@/lib/api-client";

describe("creditErrorMessage", () => {
  it("names the closed crediting right for a codeless 403", () => {
    // given
    const error = new ApiError(403, "detail");

    // when
    const actual = creditErrorMessage(error);

    // then
    expect(actual).toBe("Crediting contributions is no longer open here.");
  });

  it.each([
    ["CONTRIBUTOR_NOT_FOUND", "That person is not a member of this community."],
    [
      "CONTRIBUTOR_AMBIGUOUS",
      "Several members match that name. Pick one from the list.",
    ],
  ])("maps %s to its own copy", (code, expected) => {
    // given
    const error = new ApiError(409, "detail", code);

    // when
    const actual = creditErrorMessage(error);

    // then
    expect(actual).toBe(expected);
  });

  it("gives no artifact-specific copy for a code crediting never returns", () => {
    // given
    const error = new ApiError(409, "detail", "ARTIFACT_TYPE_IN_USE");

    // when
    const actual = creditErrorMessage(error);

    // then
    expect(actual).toBe("Something went wrong. Please try again.");
  });

  it("falls back to a generic message for unknown errors", () => {
    // when
    const actual = creditErrorMessage(new Error("boom"));

    // then
    expect(actual).toBe("Something went wrong. Please try again.");
  });
});

describe("captureErrorMessage", () => {
  it("keeps a codeless 403 generic", () => {
    // when
    const actual = captureErrorMessage(new ApiError(403, "detail"));

    // then
    expect(actual).toBe("Something went wrong. Please try again.");
  });

  it("still maps a contributor code", () => {
    // when
    const actual = captureErrorMessage(
      new ApiError(409, "detail", "CONTRIBUTOR_NOT_FOUND"),
    );

    // then
    expect(actual).toBe("That person is not a member of this community.");
  });
});
