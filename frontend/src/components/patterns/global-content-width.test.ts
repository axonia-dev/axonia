import { describe, expect, it } from "vitest";

import { globalContentMaxWidthClass } from "@/components/patterns/global-content-width";

describe("globalContentMaxWidthClass", () => {
  it.each([
    ["/me/mentoring/11", "max-w-5xl"],
    ["/me/mentoring", "max-w-3xl"],
    ["/me/impact", "max-w-3xl"],
    ["/communities", "max-w-3xl"],
  ])("gives %s the width %s", (pathname, expected) => {
    // when
    const actual = globalContentMaxWidthClass(pathname);

    // then
    expect(actual).toBe(expected);
  });
});
