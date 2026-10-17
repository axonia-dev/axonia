// The ribbon over the timeline, in its own file: ScheduleTimeline.test.tsx is near the 300-line ceiling.
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useRibbon } from "@/features/events/api/use-ribbon";
import { useSchedule } from "@/features/events/api/use-schedule";
import type { Memory } from "@/features/events/domain/memory";
import { NO_TRACES } from "@/features/events/domain/memory";
import { addDaysIso } from "@/features/events/domain/schedule-window";
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
const middle = memoryOn(2, "2026-06-05T18:00:00Z");
const oldest = memoryOn(1, "2026-01-09T18:00:00Z");

const pagesOf = (...pages: Memory[][]) => ({
  pages: pages.map((items) => ({ items, next: null })),
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("ScheduleTimeline ribbon", () => {
  it("loads older pages until it reaches a scrub target, then stops", async () => {
    // given only the latest memory loaded, and the ribbon knowing the oldest
    const fetchNextPage = vi
      .fn()
      .mockResolvedValueOnce({
        hasNextPage: true,
        data: pagesOf([latest], [middle]),
      })
      .mockResolvedValueOnce({
        hasNextPage: false,
        data: pagesOf([latest], [middle], [oldest]),
      });
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]], hasNextPage: true, fetchNextPage },
      ribbon: {
        firstSessionAt: oldest.entry.scheduledAt,
        ticks: [latest, middle, oldest]
          .map((m) => ({
            at: m.entry.scheduledAt ?? "",
            kind: "MEMORY" as const,
          }))
          .reverse(),
      },
    });
    // when the reader jumps to the first session
    fireEvent.keyDown(screen.getByRole("slider"), { key: "Home" });
    // then
    await waitFor(() => {
      expect(fetchNextPage).toHaveBeenCalledTimes(2);
    });
    await new Promise((resolve) => requestAnimationFrame(resolve));
    expect(fetchNextPage).toHaveBeenCalledTimes(2);
  });

  it("does not load anything for a target already in the list", () => {
    // given
    const fetchNextPage = vi.fn();
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]], hasNextPage: true, fetchNextPage },
      ribbon: {
        firstSessionAt: latest.entry.scheduledAt,
        ticks: [{ at: latest.entry.scheduledAt ?? "", kind: "MEMORY" }],
      },
    });
    // when
    fireEvent.keyDown(screen.getByRole("slider"), { key: "Home" });
    // then
    expect(fetchNextPage).not.toHaveBeenCalled();
  });

  it("reads the session under the lens", () => {
    // when
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
    });
    // then: no row has been read yet, so the lens rests on today
    expect(screen.getByRole("slider")).toHaveAttribute(
      "aria-valuetext",
      "Today",
    );
  });

  it("asks for the ticks of the years around the window it opens on", () => {
    // when
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
      ribbon: { firstSessionAt: oldest.entry.scheduledAt, ticks: [] },
    });
    // then
    expect(useRibbon).toHaveBeenLastCalledWith("x", [2026, 2027]);
  });

  it("opens the schedule a step ahead of today", () => {
    // when
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
    });
    // then
    expect(useSchedule).toHaveBeenLastCalledWith(
      "x",
      FROM,
      addDaysIso(FROM, 90),
    );
  });

  it("loads ahead before scrolling to a session past the loaded rows", () => {
    // given
    const given = {
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
    };
    const { rerenderWith } = renderTimeline(given);
    const main = screen.getByRole("main");
    main.scrollTo = vi.fn();
    // when the reader jumps to the end of the horizon, and the next rows are still on their way
    vi.mocked(useSchedule).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      isPlaceholderData: true,
    } as never);
    fireEvent.keyDown(screen.getByRole("slider"), { key: "End" });
    // then the whole horizon is asked for, and nothing scrolls before it lands
    expect(useSchedule).toHaveBeenLastCalledWith(
      "x",
      FROM,
      addDaysIso(FROM, 1095),
    );
    expect(main.scrollTo).not.toHaveBeenCalled();
    // when
    rerenderWith(given);
    // then
    expect(main.scrollTo).toHaveBeenCalledOnce();
  });

  it("never steps to a tick past the horizon, which no load could reach", () => {
    // given the only tick ahead lies past the three years the list ever loads
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]] },
      ribbon: {
        firstSessionAt: latest.entry.scheduledAt,
        ticks: [{ at: addDaysIso(FROM, 1100), kind: "PROJECTED" }],
      },
    });
    // when
    fireEvent.keyDown(screen.getByRole("slider"), { key: "ArrowRight" });
    // then
    expect(useSchedule).toHaveBeenLastCalledWith(
      "x",
      FROM,
      addDaysIso(FROM, 90),
    );
  });

  it("joins the page in flight and drops a superseded scrub", async () => {
    // given a page that has not answered yet
    let answer: ((value: unknown) => void) | undefined;
    const fetchNextPage = vi.fn(
      () => new Promise((resolve) => (answer = resolve)),
    );
    renderTimeline({
      fromIso: FROM,
      schedule: { data: [] },
      memories: { pages: [[latest]], hasNextPage: true, fetchNextPage },
      ribbon: {
        firstSessionAt: oldest.entry.scheduledAt,
        ticks: [{ at: oldest.entry.scheduledAt ?? "", kind: "MEMORY" }],
      },
    });
    const main = screen.getByRole("main");
    main.scrollTo = vi.fn();
    const slider = screen.getByRole("slider");
    // when two scrubs reach for history, then a third jumps to today
    fireEvent.keyDown(slider, { key: "Home" });
    fireEvent.keyDown(slider, { key: "Home" });
    fireEvent.keyDown(slider, { key: "t" });
    answer?.({ hasNextPage: false, data: pagesOf([latest], [oldest]) });
    await new Promise((resolve) => requestAnimationFrame(resolve));
    // then every request joins the one in flight, and only today's scrub scrolls
    expect(fetchNextPage.mock.calls).toEqual([
      [{ cancelRefetch: false }],
      [{ cancelRefetch: false }],
    ]);
    expect(main.scrollTo).toHaveBeenCalledOnce();
  });
});
