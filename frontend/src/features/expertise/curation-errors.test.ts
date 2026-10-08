import { describe, expect, it } from "vitest";

import {
  curationErrorMessage,
  namedAreaErrorMessage,
} from "@/features/expertise/curation-errors";
import { ApiError } from "@/lib/api-client";

describe("curationErrorMessage", () => {
  it("explains a name that has been merged away, which no client-side check can see", () => {
    // given - the rename dialog only knows the canonical tags, so a merged variant passes it
    const error = new ApiError(409, "detail", "RENAME_TARGET_MERGED");

    // when
    const actual = curationErrorMessage(error, "Couldn't rename that tag.");

    // then
    expect(actual).toMatch(/merged/i);
  });

  it("explains a tag that carries other names", () => {
    const actual = curationErrorMessage(
      new ApiError(409, "detail", "TAG_MERGED"),
      "Couldn't delete that tag.",
    );

    expect(actual).toMatch(/split/i);
  });

  it("falls back to the verb's own copy for an error it does not know", () => {
    const actual = curationErrorMessage(
      new Error("boom"),
      "Couldn't rename that tag.",
    );

    expect(actual).toBe("Couldn't rename that tag.");
  });

  it("falls back when the backend sends a code it has no copy for", () => {
    const actual = curationErrorMessage(
      new ApiError(500, "detail", "SOMETHING_ELSE"),
      "Couldn't delete that tag.",
    );

    expect(actual).toBe("Couldn't delete that tag.");
  });
});

describe("namedAreaErrorMessage", () => {
  it.each([
    ["NAMED_AREA_NAME_BLANK", "A named area needs a name."],
    [
      "NAMED_AREA_NAME_TOO_LONG",
      "A named area's name can be at most 80 characters.",
    ],
  ])("explains %s", (code, message) => {
    const actual = namedAreaErrorMessage(
      new ApiError(400, "detail", code),
      "Couldn’t rename that named area.",
    );

    expect(actual).toBe(message);
  });

  it("says a named area deleted elsewhere is gone", () => {
    const actual = namedAreaErrorMessage(
      new ApiError(404, "detail"),
      "Couldn’t rename that named area.",
    );

    expect(actual).toBe("That named area no longer exists.");
  });

  it("keeps the tag codes and the fallback", () => {
    expect(
      namedAreaErrorMessage(
        new ApiError(400, "detail", "TAG_NOT_IN_COMMUNITY"),
        "Couldn’t place that tag.",
      ),
    ).toBe("This community doesn't use that tag.");
    expect(
      namedAreaErrorMessage(
        new ApiError(500, "detail"),
        "Couldn’t place that tag.",
      ),
    ).toBe("Couldn’t place that tag.");
  });
});
