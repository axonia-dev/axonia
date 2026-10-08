import { describe, expect, it } from "vitest";

import { shortDayLabel } from "@/features/events/ui/schedule-dates";

/** The suite runs on a clock west of UTC, so a date read on the local frame renders the day
 *  before. An occurrence date is a calendar day and has to be read as one. */
describe("shortDayLabel", () => {
  it("reads a calendar day in UTC, not on the viewer's clock", () => {
    expect(shortDayLabel("2026-07-02")).toBe("Thu, Jul 2");
  });

  it("reads an instant on the same frame, so a day never shifts between views", () => {
    expect(shortDayLabel("2026-07-02T18:00:00Z")).toBe("Thu, Jul 2");
  });
});
