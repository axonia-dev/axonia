import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useScheduleActivity } from "@/features/events/api/use-schedule-activity";
import { useSeriesList } from "@/features/events/api/use-series";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { MonthCalendar } from "@/features/events/ui/MonthCalendar";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-schedule-activity", () => ({
  useScheduleActivity: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
// No network, no QueryClient needed, same way SlotRow.test.tsx mocks it.
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

// Loaded activity result (isSuccess mirrors the resolved query).
function activityStub(data: string[] = []) {
  return { data, isSuccess: true } as never;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
  vi.mocked(useScheduleActivity).mockReturnValue(activityStub());
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});
afterEach(() => vi.useRealTimers());

// Only fields resolveLabel() reads: id + title.
const SERIES_LIST = [{ id: 1, title: "Backend Guild Series" }] as never[];

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-06-05",
    scheduledAt: "2026-06-05T18:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 60,
    status: "HELD",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

// June 5, distinct from the mocked today (June 14) so the day-detail list starts empty and the
// chip renders exactly once.
const heldEntry = entry({});

// A second grid chip, under its own name, so a pick can be told from the one before it.
const reviewEntry = entry({
  sessionId: 2,
  title: "Architecture Review",
  occurrenceDate: "2026-06-11",
  scheduledAt: "2026-06-11T18:00:00Z",
});

// On the mocked today, the day the day-detail list opens on: without it that list is empty and no
// chip on the phone's own surface exists to be picked.
const todayEntry = entry({
  sessionId: 3,
  title: "Platform Office Hours",
  occurrenceDate: "2026-06-14",
  scheduledAt: "2026-06-14T18:00:00Z",
});

function mockSchedule(entries: ScheduleEntry[]) {
  vi.mocked(useSchedule).mockReturnValue({
    data: entries,
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: SERIES_LIST },
  } as never);
}

function renderCalendar() {
  render(
    <MemoryRouter>
      <MonthCalendar
        slug="backend-guild"
        month={{ year: 2026, monthIndex0: 5 }}
        onMonthChange={() => {}}
      />
    </MemoryRouter>,
  );
}

/** Opens the panel the way every case here does: by clicking a chip in the grid. */
function clickChip(name: RegExp) {
  return user.click(screen.getByRole("button", { name }));
}

function setupFull() {
  mockSchedule([heldEntry]);
  renderCalendar();
}

// Both panels are always in the DOM (jsdom loads no CSS, so `hidden` and `md:hidden` remove
// neither one); the desktop panel is whichever match sits outside `day-detail`.
function desktopPanel() {
  const detail = screen.getByTestId("day-detail");
  return screen
    .getAllByRole("complementary", { name: /session details/i })
    .find((el) => !detail.contains(el));
}

describe("MonthCalendar session panel", () => {
  it("opens the panel for the chip that was clicked", async () => {
    setupFull();
    await clickChip(/backend guild series/i);
    expect(desktopPanel()).toBeInTheDocument();
  });

  it("closes the panel on Escape", async () => {
    setupFull();
    await clickChip(/backend guild series/i);
    await user.keyboard("{Escape}");
    expect(
      screen.queryByRole("complementary", { name: /session details/i }),
    ).not.toBeInTheDocument();
  });

  it("closes the panel when the month changes", async () => {
    mockSchedule([heldEntry]);
    const { rerender } = render(
      <MemoryRouter>
        <MonthCalendar
          slug="backend-guild"
          month={{ year: 2026, monthIndex0: 5 }}
          onMonthChange={() => {}}
        />
      </MemoryRouter>,
    );
    await clickChip(/backend guild series/i);
    rerender(
      <MemoryRouter>
        <MonthCalendar
          slug="backend-guild"
          month={{ year: 2026, monthIndex0: 6 }}
          onMonthChange={() => {}}
        />
      </MemoryRouter>,
    );
    expect(
      screen.queryByRole("complementary", { name: /session details/i }),
    ).not.toBeInTheDocument();
  });

  /** The panel is a sibling of the whole grid, so a chip in the first week is up to forty tab
   *  stops from its own link to the session. Opening therefore moves the reader into the panel:
   *  without it the branch took away a one-keystroke route and left a pointer-only one. */
  it("moves the reader into the panel it opens", async () => {
    setupFull();
    await clickChip(/backend guild series/i);
    expect(desktopPanel()).toHaveFocus();
  });

  it("hands the reader back to the chip when Escape closes the panel", async () => {
    setupFull();
    const chip = screen.getByRole("button", {
      name: /backend guild series/i,
    });
    await user.click(chip);
    await user.keyboard("{Escape}");
    expect(chip).toHaveFocus();
  });

  it("says on the chip which panel it opens and that the panel is showing", async () => {
    setupFull();
    const chip = screen.getByRole("button", {
      name: /backend guild series/i,
    });
    expect(chip).toHaveAttribute("aria-expanded", "false");

    await user.click(chip);

    expect(chip).toHaveAttribute("aria-expanded", "true");
    expect(chip.getAttribute("aria-controls")).toBe(desktopPanel()?.id);
  });

  /** A reschedule, a skip or a cancel invalidates the schedule, and the refreshed answer carries a
   *  new object for the entry that moved. Held on to, the selection would leave the panel stating
   *  the day and hour the entry had before the move, beside a chip visibly sitting elsewhere. */
  it("closes the panel once the selected entry is no longer the one the schedule holds", async () => {
    mockSchedule([heldEntry]);
    const view = render(
      <MemoryRouter>
        <MonthCalendar
          slug="backend-guild"
          month={{ year: 2026, monthIndex0: 5 }}
          onMonthChange={() => {}}
        />
      </MemoryRouter>,
    );
    await clickChip(/backend guild series/i);
    expect(desktopPanel()).toBeInTheDocument();

    mockSchedule([
      {
        ...heldEntry,
        occurrenceDate: "2026-06-09",
        scheduledAt: "2026-06-09T18:00:00Z",
      },
    ]);
    view.rerender(
      <MemoryRouter>
        <MonthCalendar
          slug="backend-guild"
          month={{ year: 2026, monthIndex0: 5 }}
          onMonthChange={() => {}}
        />
      </MemoryRouter>,
    );

    expect(
      screen.queryAllByRole("complementary", { name: /session details/i }),
    ).toHaveLength(0);
  });

  it("gives the phone its panel inside the day detail", async () => {
    setupFull();
    await user.click(
      screen.getAllByRole("button", { name: /backend guild series/i })[0]!,
    );
    const detail = screen.getByTestId("day-detail");
    expect(within(detail).getByTestId("session-panel")).toBeInTheDocument();
  });

  /**
   * A second pick replaces the panel's session without unmounting it, so nothing about mounting
   * says a new one was opened. Keyed on the surface alone the reader is left out on the grid from
   * the second pick on, and the keyboard route the chip's button promises works once a visit.
   */
  it("moves the reader into the panel again when a second chip is picked", async () => {
    mockSchedule([heldEntry, reviewEntry]);
    renderCalendar();

    await clickChip(/backend guild series/i);
    await clickChip(/architecture review/i);

    expect(desktopPanel()).toHaveFocus();
  });

  it("hands the reader back to the chip the showing panel was opened from", async () => {
    mockSchedule([heldEntry, reviewEntry]);
    renderCalendar();
    const first = screen.getByRole("button", {
      name: /backend guild series/i,
    });
    const second = screen.getByRole("button", { name: /architecture review/i });

    await user.click(first);
    await user.click(second);
    await user.keyboard("{Escape}");

    expect(second).toHaveFocus();
    expect(first).not.toHaveFocus();
  });

  /**
   * Both panels sit in the document at every width: the breakpoint that hides one is a class name
   * and jsdom loads no CSS. The surface the chip was on is the only thing telling them apart, so a
   * pick in the day-detail list has to take that list's panel and leave the grid's where it is.
   */
  it("moves the reader into the phone's panel for a chip picked in the day detail", async () => {
    mockSchedule([heldEntry, todayEntry]);
    renderCalendar();
    const detail = screen.getByTestId("day-detail");

    await user.click(
      within(detail).getByRole("button", { name: /platform office hours/i }),
    );

    expect(within(detail).getByTestId("session-panel")).toHaveFocus();
    expect(desktopPanel()).not.toHaveFocus();
  });
});
