import { describe, expect, it } from "vitest";

import { identifierSegments } from "@/lib/identifier-segments";

describe("identifierSegments", () => {
  it("cuts a URL after each run of separators", () => {
    // when
    const actual = identifierSegments(
      "https://idp.example/realms/example-corp?x=1",
    );

    // then
    expect(actual).toEqual([
      "https://",
      "idp.",
      "example/",
      "realms/",
      "example-",
      "corp?",
      "x=",
      "1",
    ]);
  });

  it("cuts after an underscore", () => {
    // when
    const actual = identifierSegments("axk_3f9a2b7c1d");

    // then
    expect(actual).toEqual(["axk_", "3f9a2b7c1d"]);
  });

  it("leaves a value with no separator whole", () => {
    // when
    const actual = identifierSegments("3f9a2b7c1d");

    // then
    expect(actual).toEqual(["3f9a2b7c1d"]);
  });

  it("adds no empty segment after a trailing separator", () => {
    // when
    const actual = identifierSegments("example.com/");

    // then
    expect(actual).toEqual(["example.", "com/"]);
  });
});
