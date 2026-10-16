// Memories above Today, in their own file: ScheduleTimeline.test.tsx is near the 300-line ceiling.
import { act, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { invalidateMemories } from "@/features/events/api/invalidate-memories";
import type { Memory } from "@/features/events/domain/memory";
import { NO_TRACES } from "@/features/events/domain/memory";
import { renderTimeline } from "@/test/schedule-timeline-harness";
import { aScheduleEntry } from "@/test/session-fixture";

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
const HOUR = 3_600_000;

function held(sessionId: number, title: string, scheduledAt: string) {
  return aScheduleEntry({
    sessionId,
    title,
    scheduledAt,
    occurrenceDate: scheduledAt.slice(0, 10),
    seriesTimeZone: "UTC",
    status: "HELD",
  });
}

const older: Memory = {
  entry: held(1, "Kotlin coroutines", "2026-09-04T18:00:00Z"),
  traces: { loves: 9, recorded: true, artifacts: 2 },
};
const newer: Memory = {
  entry: held(2, "Refactoring live", "2026-10-02T18:00:00Z"),
  traces: NO_TRACES,
};

const today = aScheduleEntry({
  sessionId: 30,
  title: "Morning sync",
  scheduledAt: "2026-10-16T09:00:00Z",
  occurrenceDate: "2026-10-16",
  seriesTimeZone: "UTC",
  durationMinutes: 60,
});

const linkNamed = (title: string) =>
  screen.getAllByRole("link", { name: (name) => name.includes(title) });

const precedes = (a: Element, b: Element) =>
  Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

afterEach(() => {
  vi.useRealTimers();
  vi.mocked(invalidateMemories).mockClear();
});

describe("ScheduleTimeline memories", () => {
  it("lists memories above Today, oldest first, with their traces", () => {
    // when
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[newer, older]] },
    });
    // then
    const [first] = linkNamed("Kotlin coroutines");
    const [second] = linkNamed("Refactoring live");
    expect(precedes(first!, second!)).toBe(true);
    expect(precedes(second!, screen.getByText("Today"))).toBe(true);
    expect(first).toHaveAccessibleName(/9 loves, recording, 2 artifacts/);
  });

  it("moves a session that ends while open above Today, once, and refreshes memories", () => {
    // given an hour before the session starts
    renderTimeline({
      fromIso: FROM,
      now: "2026-10-16T08:00:00Z",
      fakeTimeouts: true,
      schedule: { data: [today] },
    });
    const before = linkNamed("Morning sync")[0]!;
    expect(precedes(screen.getByText("Today"), before)).toBe(true);
    // when its hour has run
    act(() => {
      vi.advanceTimersByTime(2 * HOUR);
    });
    // then
    const [after] = linkNamed("Morning sync");
    expect(precedes(after!, screen.getByText("Today"))).toBe(true);
    expect(after!.closest("li")).toHaveClass("memory-arrive");
    expect(invalidateMemories).toHaveBeenCalledOnce();
  });

  it("shows an ended session once when the server serves it too", () => {
    // given it ended before the page opened
    const { rerenderWith } = renderTimeline({
      fromIso: FROM,
      now: "2026-10-16T12:00:00Z",
      schedule: { data: [today] },
    });
    // when the memories page catches up
    rerenderWith({
      fromIso: FROM,
      schedule: { data: [today] },
      memories: {
        pages: [
          [
            {
              entry: { ...today, status: "HELD" },
              traces: { ...NO_TRACES, loves: 3 },
            },
          ],
        ],
      },
    });
    // then
    expect(linkNamed("Morning sync")).toHaveLength(1);
    expect(linkNamed("Morning sync")[0]).toHaveAccessibleName(/3 loves/);
  });

  it("keeps a running session the server already serves as one live card below Today", () => {
    // given ten minutes into the session
    renderTimeline({
      fromIso: FROM,
      now: "2026-10-16T09:10:00Z",
      schedule: { data: [today] },
      memories: { pages: [[{ entry: today, traces: NO_TRACES }]] },
    });
    // then
    const cards = linkNamed("Morning sync");
    expect(cards).toHaveLength(1);
    expect(precedes(screen.getByText("Today"), cards[0]!)).toBe(true);
    expect(screen.getByText("Running now")).toBeInTheDocument();
  });

  it("keeps an untimed session today below Today though the server serves it", () => {
    // given
    const untimed = { ...today, hasTime: false, scheduledAt: FROM };
    renderTimeline({
      fromIso: FROM,
      now: "2026-10-16T15:00:00Z",
      schedule: { data: [untimed] },
      memories: { pages: [[{ entry: untimed, traces: NO_TRACES }]] },
    });
    // then
    const cards = linkNamed("Morning sync");
    expect(cards).toHaveLength(1);
    expect(precedes(screen.getByText("Today"), cards[0]!)).toBe(true);
  });

  it("shows no ribbon and the empty message with nothing at all", () => {
    // when
    renderTimeline({ fromIso: FROM, schedule: { data: [] } });
    // then
    expect(screen.queryByRole("slider")).not.toBeInTheDocument();
    expect(
      screen.getByText("No scheduled sessions in this window."),
    ).toBeInTheDocument();
  });
});
