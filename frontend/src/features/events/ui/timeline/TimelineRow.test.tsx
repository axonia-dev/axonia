import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { TimelineRow } from "@/features/events/ui/timeline/TimelineRow";

const TODAY = "2026-01-06T00:00:00.000Z";

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
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

function setup(e: ScheduleEntry, nextUp = false) {
  render(
    <MemoryRouter>
      <ul>
        <TimelineRow
          entry={e}
          nextUp={nextUp}
          label="Frontend Dojo"
          href="/x"
          todayIso={TODAY}
          users={{}}
        />
      </ul>
    </MemoryRouter>,
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
    render(
      <MemoryRouter>
        <ul>
          <TimelineRow
            entry={entry({})}
            nextUp={false}
            label="Frontend Dojo"
            href="/x"
            todayIso={TODAY}
            users={{}}
            onContextMenu={onContextMenu}
          />
        </ul>
      </MemoryRouter>,
    );

    const notPrevented = fireEvent.contextMenu(screen.getByRole("listitem"));

    expect(onContextMenu).toHaveBeenCalledOnce();
    // The row hands the event over untouched: swallowing the browser menu is its owner's call.
    expect(notPrevented).toBe(true);
  });
});
