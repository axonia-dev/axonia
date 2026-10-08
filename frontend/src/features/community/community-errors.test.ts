import { describe, expect, it } from "vitest";

import { communityErrorMessage } from "@/features/community/community-errors";
import { ApiError } from "@/lib/api-client";

describe("communityErrorMessage", () => {
  it("maps a known ProblemDetail code to a human sentence", () => {
    // given
    const error = new ApiError(409, "x", "LAST_ORGANIZER_CANNOT_LEAVE");

    // when
    const actual = communityErrorMessage(error);

    // then
    expect(actual).toMatch(/last organizer/i);
  });

  it("falls back to a friendly generic message for unknown errors", () => {
    // when
    const actual = communityErrorMessage(new Error("boom"));

    // then
    expect(actual).toMatch(/something went wrong/i);
  });
});
