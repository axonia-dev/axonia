import { describe, expect, it } from "vitest";

import {
  markSegments,
  type TextSegment,
} from "@/features/admin/domain/mark-match";

describe("markSegments", () => {
  it("returns the whole text unmarked when nothing matches", () => {
    const expected: TextSegment[] = [
      { text: "Manage members", matched: false },
    ];
    expect(markSegments("Manage members", "sso")).toEqual(expected);
  });

  it("splits around a single match", () => {
    expect(markSegments("Manage SSO connections", "SSO")).toEqual([
      { text: "Manage ", matched: false },
      { text: "SSO", matched: true },
      { text: " connections", matched: false },
    ]);
  });

  it("marks every occurrence, not only the first", () => {
    expect(markSegments("role, role, role", "role")).toEqual([
      { text: "role", matched: true },
      { text: ", ", matched: false },
      { text: "role", matched: true },
      { text: ", ", matched: false },
      { text: "role", matched: true },
    ]);
  });

  it("matches case insensitively, keeping the text's own casing", () => {
    expect(markSegments("Manage SSO connections", "sso")).toEqual([
      { text: "Manage ", matched: false },
      { text: "SSO", matched: true },
      { text: " connections", matched: false },
    ]);
  });

  it("treats regex metacharacters as literal text", () => {
    expect(markSegments("Manage (SSO) connections", "(")).toEqual([
      { text: "Manage ", matched: false },
      { text: "(", matched: true },
      { text: "SSO) connections", matched: false },
    ]);
    expect(markSegments("Manage members", "*")).toEqual([
      { text: "Manage members", matched: false },
    ]);
  });

  it("returns one unmarked segment for a blank needle", () => {
    expect(markSegments("Manage members", "")).toEqual([
      { text: "Manage members", matched: false },
    ]);
    expect(markSegments("Manage members", "   ")).toEqual([
      { text: "Manage members", matched: false },
    ]);
  });

  it("returns one empty unmarked segment for empty text under a real needle", () => {
    // A role with no description reaches here, and the loop pushes nothing: the fallback is
    // what keeps the caller from mapping over an empty list and rendering nothing at all.
    expect(markSegments("", "sso")).toEqual([{ text: "", matched: false }]);
  });

  it("marks the whole text when the needle is the whole text", () => {
    expect(markSegments("Manage members", "manage members")).toEqual([
      { text: "Manage members", matched: true },
    ]);
  });
});
