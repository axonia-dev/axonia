import { describe, expect, it } from "vitest";

import { upcomingGlow } from "@/features/events/domain/upcoming-glow";

const NOW = new Date("2026-10-16T00:00:00Z");

describe("upcomingGlow", () => {
  it("is full now, half at 10.5 days and none from 21 days", () => {
    expect(upcomingGlow("2026-10-16T00:00:00Z", NOW)).toBe(1);
    expect(upcomingGlow("2026-10-26T12:00:00Z", NOW)).toBe(0.5);
    expect(upcomingGlow("2026-11-06T00:00:00Z", NOW)).toBe(0);
    expect(upcomingGlow("2027-01-01T00:00:00Z", NOW)).toBe(0);
  });

  it("is full for an instant already past", () => {
    expect(upcomingGlow("2026-10-01T00:00:00Z", NOW)).toBe(1);
  });
});
