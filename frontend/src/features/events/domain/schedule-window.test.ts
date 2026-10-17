import { describe, expect, it } from "vitest";

import {
  addDaysIso,
  HORIZON_DAYS,
  horizonDaysFor,
  startOfTodayUtc,
} from "@/features/events/domain/schedule-window";

const now = new Date("2026-06-14T15:30:00Z");

describe("startOfTodayUtc", () => {
  it("returns midnight UTC for the given date", () => {
    expect(startOfTodayUtc(now)).toBe("2026-06-14T00:00:00.000Z");
  });

  it("uses the UTC date, not the local date", () => {
    // 23:30 UTC on Jun 13 — local time might be Jun 14 in some zones, but UTC is Jun 13
    const lateNight = new Date("2026-06-13T23:30:00Z");
    expect(startOfTodayUtc(lateNight)).toBe("2026-06-13T00:00:00.000Z");
  });
});

describe("addDaysIso", () => {
  it("shifts an ISO instant forward by the given number of days", () => {
    const from = "2026-06-14T00:00:00.000Z";
    expect(addDaysIso(from, 90)).toBe("2026-09-12T00:00:00.000Z");
  });

  it("handles month boundaries", () => {
    const from = "2026-01-31T00:00:00.000Z";
    expect(addDaysIso(from, 1)).toBe("2026-02-01T00:00:00.000Z");
  });
});

describe("horizonDaysFor", () => {
  const from = "2026-10-16T00:00:00.000Z";
  const at = (iso: string) => new Date(iso).getTime();

  it("loads one step for a target inside the first", () => {
    expect(horizonDaysFor(from, at("2026-10-20T10:00:00Z"))).toBe(90);
    expect(horizonDaysFor(from, at("2026-10-01T10:00:00Z"))).toBe(90);
  });

  it("rounds up to whole steps that hold the target", () => {
    expect(horizonDaysFor(from, at("2027-01-14T10:00:00Z"))).toBe(180);
    expect(horizonDaysFor(from, at("2027-06-01T10:00:00Z"))).toBe(270);
  });

  it("never reaches past the horizon", () => {
    expect(horizonDaysFor(from, at("2035-01-01T00:00:00Z"))).toBe(HORIZON_DAYS);
  });
});
