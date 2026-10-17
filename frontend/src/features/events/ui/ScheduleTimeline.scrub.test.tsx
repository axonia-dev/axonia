// When a scrub may start and when its scroll lands, in its own file: the ribbon tests fill theirs.
import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

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

function memoryOn(sessionId: number, scheduledAt: string): Memory {
  return {
    entry: aScheduleEntry({
      sessionId,
      title: `Session ${sessionId}`,
      scheduledAt,
      occurrenceDate: scheduledAt.slice(0, 10),
      seriesTimeZone: "UTC",
      status: "HELD",
    }),
    traces: NO_TRACES,
  };
}

const latest = memoryOn(3, "2026-10-02T18:00:00Z");
const oldest = memoryOn(1, "2026-01-09T18:00:00Z");

const pagesOf = (...pages: Memory[][]) => ({
  pages: pages.map((items) => ({ items, next: null })),
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("ScheduleTimeline scrub timing", () => {
  it("holds the ribbon back until the first memories page has settled", () => {
    // given the schedule is in but the memories are still on their way
    const { rerenderWith } = renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [], isLoading: true },
      ribbon: {
        firstSessionAt: latest.entry.scheduledAt,
        ticks: [{ at: latest.entry.scheduledAt ?? "", kind: "MEMORY" }],
      },
    });
    // then no scrub can start against a list that has no page to load from yet
    expect(screen.queryByRole("slider")).not.toBeInTheDocument();
    // when
    rerenderWith({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
      ribbon: {
        firstSessionAt: latest.entry.scheduledAt,
        ticks: [{ at: latest.entry.scheduledAt ?? "", kind: "MEMORY" }],
      },
    });
    // then
    expect(screen.getByRole("slider")).toBeInTheDocument();
  });

  it("scrolls to an older target only after the prepend is anchored", async () => {
    // given every box sits at a shared offset, read lazily so a prepend can move them all
    const box = { top: 0 };
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
      () => ({ top: box.top, height: 0 }) as DOMRect,
    );
    const given = {
      fromIso: FROM,
      schedule: { data: [] },
      ribbon: {
        firstSessionAt: oldest.entry.scheduledAt,
        ticks: [
          { at: oldest.entry.scheduledAt ?? "", kind: "MEMORY" as const },
        ],
      },
    };
    const fetchNextPage = vi.fn().mockResolvedValue({
      hasNextPage: false,
      data: pagesOf([latest], [oldest]),
    });
    const { rerenderWith } = renderTimeline({
      ...given,
      memories: { pages: [[latest]], hasNextPage: true, fetchNextPage },
    });
    const main = screen.getByRole("main");
    const moves: string[] = [];
    main.scrollTo = () => moves.push("scroll");
    Object.defineProperty(main, "scrollTop", {
      get: () => 0,
      set: () => moves.push("anchor"),
    });
    await new Promise((resolve) => requestAnimationFrame(resolve));
    // when the reader jumps to the first session and its page lands above the row being read
    fireEvent.keyDown(screen.getByRole("slider"), { key: "Home" });
    await act(async () => {
      await Promise.resolve();
    });
    box.top = 400;
    act(() => {
      rerenderWith({ ...given, memories: { pages: [[latest], [oldest]] } });
    });
    // then the smooth scroll starts last, so the anchoring cannot cancel it
    expect(moves).toEqual(["anchor", "scroll"]);
  });
});
