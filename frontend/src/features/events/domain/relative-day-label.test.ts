import { describe, expect, it } from "vitest";

import { relativeDayLabel } from "@/features/events/domain/relative-day-label";

const now = new Date("2026-07-22T10:00:00-07:00");

describe("relativeDayLabel", () => {
  it("labels the same local day as today", () => {
    expect(
      relativeDayLabel(now, new Date("2026-07-22T23:30:00-07:00")),
    ).toEqual({ label: "today", soon: true });
  });

  it("labels the next local day as tomorrow even when under 24h away", () => {
    expect(
      relativeDayLabel(now, new Date("2026-07-23T08:00:00-07:00")),
    ).toEqual({ label: "tomorrow", soon: true });
  });

  it("counts days up to 13", () => {
    expect(
      relativeDayLabel(now, new Date("2026-07-24T10:00:00-07:00")),
    ).toEqual({ label: "in 2 days", soon: false });
    expect(
      relativeDayLabel(now, new Date("2026-08-04T10:00:00-07:00")),
    ).toEqual({ label: "in 13 days", soon: false });
  });

  it("switches to weeks from 14 days, rounding down", () => {
    expect(
      relativeDayLabel(now, new Date("2026-08-05T10:00:00-07:00")),
    ).toEqual({ label: "in 2 weeks", soon: false });
    expect(
      relativeDayLabel(now, new Date("2026-08-12T10:00:00-07:00")),
    ).toEqual({ label: "in 3 weeks", soon: false });
  });
});
