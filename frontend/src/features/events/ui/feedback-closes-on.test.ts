import { describe, expect, it } from "vitest";

import { closesOn } from "@/features/events/ui/feedback-closes-on";

describe("closesOn", () => {
  it("names the day and a three letter month", () => {
    // when / then
    expect(closesOn("2026-11-13T12:00:00Z")).toBe("13 Nov");
    expect(closesOn("2026-09-04T12:00:00Z")).toBe("4 Sep");
  });
});
