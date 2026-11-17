import { describe, expect, it } from "vitest";

import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";

describe("pairingDates", () => {
  it.each([
    ["an ongoing pairing", null, "since 16 Nov 2026"],
    [
      "a pairing ended later",
      "2027-02-09T12:00:00Z",
      "16 Nov 2026 to 9 Feb 2027",
    ],
    ["a pairing ended the same day", "2026-11-16T15:00:00Z", "16 Nov 2026"],
  ])("dates %s", (_name, endedAt, expected) => {
    // when
    const actual = pairingDates("2026-11-16T10:00:00Z", endedAt);

    // then
    expect(actual).toBe(expected);
  });
});
