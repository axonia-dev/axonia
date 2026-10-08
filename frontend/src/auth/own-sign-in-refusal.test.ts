import { describe, expect, it } from "vitest";

import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { ApiError } from "@/lib/api-client";

describe("isOwnSignInRefusal", () => {
  it("reads the backend's code, not the status", () => {
    // when
    const actual = isOwnSignInRefusal(
      new ApiError(403, "x", "REQUIRES_OWN_SESSION"),
    );

    // then
    expect(actual).toBe(true);
  });

  it("is false for an uncoded 403", () => {
    // when
    const actual = isOwnSignInRefusal(new ApiError(403, "x"));

    // then
    expect(actual).toBe(false);
  });

  it("is false for anything that is not an API error", () => {
    // when
    const actual = isOwnSignInRefusal(new Error("REQUIRES_OWN_SESSION"));

    // then
    expect(actual).toBe(false);
  });
});
