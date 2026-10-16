// The countdown's clock, in its own file: it replaces MemoryCard with a render counter.
import { act, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { NO_TRACES } from "@/features/events/domain/memory";
import { renderTimeline } from "@/test/schedule-timeline-harness";
import { aScheduleEntry } from "@/test/session-fixture";

const cardRenders = vi.hoisted(() => ({ count: 0 }));

vi.mock("@/features/events/ui/timeline/MemoryCard", () => ({
  MemoryCard: () => {
    cardRenders.count += 1;
    return <span>memory</span>;
  },
}));
vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/events/api/use-memories", () => ({ useMemories: vi.fn() }));
vi.mock("@/features/events/api/use-ribbon", () => ({ useRibbon: vi.fn() }));
vi.mock("@/features/events/api/invalidate-memories", () => ({
  invalidateMemories: vi.fn(),
  invalidateRibbons: vi.fn(),
}));

const FROM = "2026-10-16T00:00:00.000Z";

const next = aScheduleEntry({
  sessionId: 40,
  title: "Kotlin coroutines",
  scheduledAt: "2026-10-18T18:00:00Z",
  occurrenceDate: "2026-10-18",
});
const past = aScheduleEntry({
  sessionId: 41,
  title: "Refactoring live",
  scheduledAt: "2026-10-02T18:00:00Z",
  occurrenceDate: "2026-10-02",
  status: "HELD",
});

afterEach(() => {
  vi.useRealTimers();
});

describe("ScheduleTimeline clock", () => {
  it("ticks the countdown each second without re-rendering the memories", () => {
    // given
    renderTimeline({
      fromIso: FROM,
      now: "2026-10-16T12:00:00Z",
      fakeTimeouts: true,
      schedule: { data: [next] },
      memories: { pages: [[{ entry: past, traces: NO_TRACES }]] },
    });
    expect(screen.getByText("memory")).toBeInTheDocument();
    const before = cardRenders.count;
    const seconds = () => screen.getByText("s").parentElement?.textContent;
    const first = seconds();
    // when
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    // then
    expect(seconds()).not.toBe(first);
    expect(cardRenders.count).toBe(before);
  });
});
