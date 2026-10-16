import { describe, expect, it } from "vitest";

import {
  agoLabel,
  isAwaitingAnswer,
  memoryFade,
  memoryPageSchema,
} from "@/features/events/domain/memory";
import { aScheduleEntry } from "@/test/session-fixture";

const TODAY = "2026-10-16T00:00:00.000Z";

describe("memory", () => {
  it("parses a page and its cursor", () => {
    const page = memoryPageSchema.parse({
      items: [
        {
          entry: aScheduleEntry({ sessionId: 4, status: "HELD" }),
          traces: { loves: 9, recorded: true, artifacts: 2 },
        },
      ],
      next: { at: "2026-09-01T18:00:00Z", id: 4 },
    });
    expect(page.items[0]?.traces.loves).toBe(9);
  });

  it.each([
    ["2026-10-16", "earlier today"],
    ["2026-10-15", "yesterday"],
    ["2026-10-12", "4 days ago"],
    ["2026-10-09", "1 week ago"],
    ["2026-10-02", "2 weeks ago"],
    ["2026-09-01", "1 month ago"],
    ["2026-08-16", "2 months ago"],
    ["2025-10-16", "12 months ago"],
    ["2024-09-01", "2 years ago"],
  ])("ago label for %s", (day, label) => {
    expect(agoLabel(`${day}T00:00:00.000Z`, TODAY)).toBe(label);
  });

  it("counts a month only once its day of month is reached", () => {
    expect(agoLabel("2026-08-20T00:00:00.000Z", TODAY)).toBe("1 month ago");
  });

  it("fades with age down to a floor", () => {
    expect(memoryFade(TODAY, TODAY)).toBe(1);
    expect(memoryFade("2026-01-01T00:00:00.000Z", TODAY)).toBeCloseTo(0.65, 5);
    expect(memoryFade("2026-10-17T00:00:00.000Z", TODAY)).toBe(1);
  });

  it("an unanswered past session awaits an answer", () => {
    expect(isAwaitingAnswer(aScheduleEntry({ status: "SCHEDULED" }))).toBe(
      true,
    );
    expect(isAwaitingAnswer(aScheduleEntry({ status: "HELD" }))).toBe(false);
  });
});
