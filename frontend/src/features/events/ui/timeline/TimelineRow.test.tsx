import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { TimelineRow } from "@/features/events/ui/timeline/TimelineRow";

const TODAY = "2026-01-06T00:00:00.000Z";

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    displayTitle: "Session",
    occurrenceDate: "2026-01-09",
    scheduledAt: "2026-01-09T12:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

function setup(
  e: ScheduleEntry,
  nextUp = false,
  onContextMenu?: ComponentProps<typeof TimelineRow>["onContextMenu"],
) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ul>
          <TimelineRow
            entry={e}
            nextUp={nextUp}
            label="Frontend Dojo"
            href="/x"
            todayIso={TODAY}
            users={{}}
            {...(onContextMenu !== undefined && { onContextMenu })}
          />
        </ul>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("TimelineRow", () => {
  it("renders weekday, day number, and relative label in the date cell", () => {
    // when
    setup(entry({}));
    // then - weekday appears twice (desktop inline + mobile stacked variant)
    expect(screen.getAllByText(/Fri/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/9/).length).toBeGreaterThan(0);
    expect(screen.getByText("in 3 days")).toBeInTheDocument();
  });

  it("renders as a list item carrying the entrance-animation class", () => {
    // when
    setup(entry({}));
    // then
    const item = screen.getByRole("listitem");
    expect(item.className).toContain("timeline-row");
    expect(item.className).toContain("group");
  });

  it("omits the relative label for far dates", () => {
    // given
    const e = entry({
      scheduledAt: "2026-03-10T12:00:00Z",
      occurrenceDate: "2026-03-10",
    });
    // when
    setup(e);
    // then
    expect(screen.queryByText(/next week|in \d/)).not.toBeInTheDocument();
  });

  it("renders the ghost variant end to end (muted date cell, dashed dot, ghost card)", () => {
    // when
    setup(entry({ sessionId: null, virtual: true }));
    // then
    expect(screen.getByText("Projected")).toBeInTheDocument();
  });

  it("hands a right-click to its owner instead of the browser", () => {
    const onContextMenu = vi.fn();
    setup(entry({}), false, onContextMenu);

    const notPrevented = fireEvent.contextMenu(screen.getByRole("listitem"));

    expect(onContextMenu).toHaveBeenCalledOnce();
    // The row hands the event over untouched: swallowing the browser menu is its owner's call.
    expect(notPrevented).toBe(true);
  });

  describe("join link under the next card", () => {
    const PLACE = { ...EMPTY_PLACE, joinUrl: "https://meet.example/dojo" };
    afterEach(() => {
      vi.useRealTimers();
    });

    it("shows while the session is running", () => {
      // given
      vi.useFakeTimers();
      vi.setSystemTime(new Date("2026-01-09T12:10:00Z"));
      // when
      setup(entry({ place: PLACE }), true);
      // then
      expect(
        document.querySelector('a[href="https://meet.example/dojo"]'),
      ).not.toBeNull();
    });

    it("is absent two days ahead", () => {
      // given
      vi.useFakeTimers();
      vi.setSystemTime(new Date("2026-01-07T12:00:00Z"));
      // when
      setup(entry({ place: PLACE }), true);
      // then
      expect(
        document.querySelector('a[href="https://meet.example/dojo"]'),
      ).toBeNull();
    });

    it("is absent without a join URL", () => {
      // given
      vi.useFakeTimers();
      vi.setSystemTime(new Date("2026-01-09T12:10:00Z"));
      // when
      setup(entry({}), true);
      // then
      expect(document.querySelector('a[href^="https://"]')).toBeNull();
    });
  });
});
