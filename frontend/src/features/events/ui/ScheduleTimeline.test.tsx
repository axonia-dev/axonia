import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { ScheduleTimeline } from "@/features/events/ui/ScheduleTimeline";
import { useUsers } from "@/features/identity/api/use-users";
import {
  cancelledEntry,
  ghostSeriesEntry,
  heldSeriesEntry,
  oneOffEntry,
  SERIES_LIST,
} from "@/test/timeline-fixtures";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/events/ui/ScheduleContextMenu", () => ({
  ScheduleContextMenu: ({
    target,
  }: Readonly<{ target: { label?: string } }>) => (
    <div data-testid="entry-menu">{target.label}</div>
  ),
}));

interface RenderGiven {
  schedule?: { data?: ScheduleEntry[]; isLoading?: boolean; isError?: boolean };
  series?: { content: Series[] };
  users?: Record<string, { id: string; displayName: string }>;
  slug?: string;
  fromIso?: string;
  toIso?: string;
  canManage?: boolean;
}

/** A settled schedule query unless the case asks for one still loading or failed. */
function scheduleQueryOf(given: RenderGiven) {
  return {
    data: given.schedule?.data,
    isLoading: given.schedule?.isLoading ?? false,
    isError: given.schedule?.isError ?? false,
  };
}

/** Wires the three queries and renders ScheduleTimeline with sensible defaults. */
function renderTimeline(given: RenderGiven = {}) {
  vi.mocked(useSchedule).mockReturnValue(scheduleQueryOf(given) as never);
  vi.mocked(useSeriesList).mockReturnValue({ data: given.series } as never);
  vi.mocked(useUsers).mockReturnValue({ data: given.users } as never);
  const onShowMore = vi.fn();
  render(
    <MemoryRouter>
      <ScheduleTimeline
        slug={given.slug ?? "x"}
        fromIso={given.fromIso ?? "2026-06-01T00:00:00.000Z"}
        toIso={given.toIso ?? "2026-09-01T00:00:00.000Z"}
        onShowMore={onShowMore}
        canManage={given.canManage ?? false}
      />
    </MemoryRouter>,
  );
  return { onShowMore };
}

function setup() {
  // given
  return renderTimeline({
    schedule: {
      data: [heldSeriesEntry, ghostSeriesEntry, oneOffEntry, cancelledEntry],
    },
    series: { content: SERIES_LIST },
    users: { u1: { id: "u1", displayName: "Maya Kim" } },
    slug: "backend-guild",
    fromIso: "2026-06-01T00:00:00.000Z",
    toIso: "2026-08-30T00:00:00.000Z",
  });
}

describe("ScheduleTimeline", () => {
  it("labels series entries with the series title", () => {
    setup();
    // then
    expect(screen.getAllByText("Backend Guild Series")).toHaveLength(2);
  });

  it("labels one-off entries with their own title", () => {
    setup();
    // then
    expect(screen.getByText("Special Session")).toBeInTheDocument();
  });

  it("renders only SCHEDULED and HELD entries as list items", () => {
    setup();
    // then — held + ghost + one-off; cancelled dropped, month/gap rows are presentational
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
  });

  it("shows the Projected chip and ghost line for virtual entries", () => {
    setup();
    // then
    expect(screen.getByText("Projected")).toBeInTheDocument();
    expect(
      screen.getByText("From the series cadence, not opened yet"),
    ).toBeInTheDocument();
  });

  it("shows the Held chip for held entries", () => {
    setup();
    // then
    expect(screen.getByText("Held")).toBeInTheDocument();
  });

  it("shows the today origin", () => {
    setup();
    // then
    expect(screen.getByText("Today")).toBeInTheDocument();
  });

  it("dates a rescheduled occurrence by the day it now meets", () => {
    // given an occurrence moved off the Friday its series anchors it to
    renderTimeline({
      schedule: {
        data: [
          {
            ...heldSeriesEntry,
            status: "SCHEDULED",
            occurrenceDate: "2026-06-12",
            scheduledAt: "2026-06-17T18:00:00Z",
          },
        ],
      },
      series: { content: SERIES_LIST },
    });
    // then
    expect(
      screen.getByRole("link", { name: /Wednesday, June 17/ }),
    ).toBeInTheDocument();
  });

  it("dates an untimed occurrence on its series' clock, not on UTC", () => {
    // given midnight in Paris, which is the evening before in UTC
    renderTimeline({
      schedule: {
        data: [
          {
            ...ghostSeriesEntry,
            hasTime: false,
            occurrenceDate: "2026-06-12",
            scheduledAt: "2026-06-11T22:00:00Z",
          },
        ],
      },
      series: { content: SERIES_LIST },
    });
    // then
    expect(
      screen.getByRole("link", { name: /Friday, June 12/ }),
    ).toBeInTheDocument();
  });

  it("marks the first real upcoming session as next up with a sneak peek", () => {
    setup();
    // then — one-off is the first non-held, non-virtual entry
    expect(
      screen.getByText("Event sourcing 101 · Maya Kim"),
    ).toBeInTheDocument();
  });

  it("shows presenter avatars resolved through useUsers", () => {
    setup();
    // then, the timeline card's presenter avatar stack renders one avatar per presenter id
    expect(vi.mocked(useUsers)).toHaveBeenCalledWith(["u1"]);
  });

  it("shows an open-spot chip for sessions with open slots", () => {
    setup();
    // then
    expect(screen.getByText("1 spot open")).toBeInTheDocument();
  });

  it("calls onShowMore when the show-more button is clicked", async () => {
    const { onShowMore } = setup();
    const user = userEvent.setup({ delay: null });
    // when
    await user.click(screen.getByRole("button", { name: /show more/i }));
    // then
    expect(onShowMore).toHaveBeenCalledOnce();
  });

  it("a real (held) row links to its session page", () => {
    setup();
    // then
    const link = screen.getByText("Held").closest("a");
    expect(link?.getAttribute("href")).toBe(
      "/communities/backend-guild/sessions/1",
    );
  });

  it("a ghost row links to the occurrence preview", () => {
    setup();
    // then
    const link = screen.getByText("Projected").closest("a");
    expect(link?.getAttribute("href")).toBe(
      "/communities/backend-guild/series/1/occurrences/2026-06-12",
    );
  });

  it("renders month markers and quiet-stretch labels for spread-out schedules", () => {
    // given — Jun 20 to Jul 4 is 14 days (2 weeks) and crosses into July
    const julyEntry: ScheduleEntry = {
      ...oneOffEntry,
      sessionId: 100,
      title: "July Special",
      occurrenceDate: "2026-07-04",
      scheduledAt: "2026-07-04T14:00:00Z",
      sections: [],
    };
    // when
    renderTimeline({
      schedule: { data: [oneOffEntry, julyEntry] },
      series: { content: SERIES_LIST },
      users: {},
    });
    // then — uppercase is applied by CSS, the DOM text stays "July"
    expect(screen.getByText("July")).toBeInTheDocument();
    expect(screen.getByText("2 quiet weeks")).toBeInTheDocument();
  });

  it("shows loading state", () => {
    // when
    renderTimeline({ schedule: { isLoading: true } });
    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows error state", () => {
    // when
    renderTimeline({ schedule: { isError: true } });
    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows the empty state with today origin and show-more still present", () => {
    // when
    renderTimeline({ schedule: { data: [] } });
    // then
    expect(
      screen.getByText("No scheduled sessions in this window."),
    ).toBeInTheDocument();
    expect(screen.getByText("Today")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /show more/i }),
    ).toBeInTheDocument();
  });

  /** The commands apply equally to what the timeline is already showing, so an organizer who
   *  prefers this view should not have to switch to the calendar to reach them. */
  it("opens the entry menu on a right-click, naming the row it was opened on", () => {
    renderTimeline({
      schedule: { data: [heldSeriesEntry] },
      series: { content: SERIES_LIST },
      users: {},
      canManage: true,
    });

    // fireEvent returns !defaultPrevented: false means the browser's own menu was swallowed.
    const notPrevented = fireEvent.contextMenu(
      screen.getAllByRole("listitem")[0] as Element,
    );

    expect(notPrevented).toBe(false);
    expect(screen.getByTestId("entry-menu")).toHaveTextContent(
      "Backend Guild Series",
    );
  });

  /** Suppressing the browser's own menu to show an empty box would be worse than leaving it
   *  alone, and there is no day command a non-organizer could reach here. */
  it("leaves a right-click alone for someone with nothing to command", () => {
    renderTimeline({
      schedule: { data: [heldSeriesEntry] },
      series: { content: SERIES_LIST },
      users: {},
    });

    const notPrevented = fireEvent.contextMenu(
      screen.getAllByRole("listitem")[0] as Element,
    );

    // Nothing was prevented, so the browser still opens the menu it always would have.
    expect(notPrevented).toBe(true);
    expect(screen.queryByTestId("entry-menu")).toBeNull();
  });
});
