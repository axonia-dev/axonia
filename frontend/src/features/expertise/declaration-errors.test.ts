import { describe, expect, it } from "vitest";

import {
  declarationErrorMessage,
  declarationRemovalErrorMessage,
} from "@/features/expertise/declaration-errors";
import { ApiError } from "@/lib/api-client";

function apiError(code: string): ApiError {
  return new ApiError(409, "refused", code);
}

describe("declarationErrorMessage", () => {
  it("names the per-community limit", () => {
    // when
    const actual = declarationErrorMessage(apiError("DECLARATION_LIMIT"));

    // then
    expect(actual).toBe("At most 20 areas per community.");
  });

  it("names the archived community that refused the declaration", () => {
    // when
    const actual = declarationErrorMessage(apiError("COMMUNITY_ARCHIVED"));

    // then
    expect(actual).toBe("This community is archived.");
  });

  it("reuses the tag naming messages", () => {
    // when
    const actual = declarationErrorMessage(apiError("TAG_BLANK"));

    // then
    expect(actual).toBe("A tag needs a name.");
  });

  it("names the own sign-in a declaration needs", () => {
    // when
    const actual = declarationErrorMessage(
      new ApiError(403, "refused", "REQUIRES_OWN_SESSION"),
    );

    // then
    expect(actual).toBe("Declarations need the person’s own sign-in.");
  });

  it("falls back for anything else", () => {
    // when
    const actual = declarationErrorMessage(new Error("offline"));

    // then
    expect(actual).toBe("Could not add that area.");
  });
});

describe("declarationRemovalErrorMessage", () => {
  it("names the own sign-in a removal needs", () => {
    // when
    const actual = declarationRemovalErrorMessage(
      new ApiError(403, "refused", "REQUIRES_OWN_SESSION"),
    );

    // then
    expect(actual).toBe("Declarations need the person’s own sign-in.");
  });

  it("falls back for anything else", () => {
    // when
    const actual = declarationRemovalErrorMessage(new Error("offline"));

    // then
    expect(actual).toBe("Could not remove that area.");
  });
});
