import { describe, expect, it } from "vitest";

import { nextUpEntry } from "@/features/events/domain/schedule";
import { aScheduleEntry } from "@/test/session-fixture";

const entry = aScheduleEntry;

describe("nextUpEntry", () => {
  const now = new Date("2026-08-17T12:00:00Z");

  it("picks the first entry still to come", () => {
    const past = entry({ scheduledAt: "2026-08-13T12:00:00Z" });
    const soon = entry({ scheduledAt: "2026-08-20T12:00:00Z" });
    const later = entry({ scheduledAt: "2026-08-27T12:00:00Z" });
    expect(nextUpEntry([later, past, soon], now)).toBe(soon);
  });

  it("skips a projected entry and a held one", () => {
    const ghost = entry({ scheduledAt: "2026-08-18T12:00:00Z", virtual: true });
    const held = entry({ scheduledAt: "2026-08-19T12:00:00Z", status: "HELD" });
    const real = entry({ scheduledAt: "2026-08-20T12:00:00Z" });
    expect(nextUpEntry([ghost, held, real], now)).toBe(real);
  });

  it("finds nothing when every entry has passed", () => {
    expect(
      nextUpEntry([entry({ scheduledAt: "2026-08-13T12:00:00Z" })], now),
    ).toBeNull();
  });
});
