import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
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

// Fix only Date so todayIso() and the today marker stay predictable.
beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
  vi.mocked(useScheduleActivity).mockReturnValue(activityStub());
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});
afterEach(() => vi.useRealTimers());

// Reports the router's current location, so a chip's onOpen (double click) is observable.
function LocationDisplay() {
  return <div data-testid="location">{useLocation().pathname}</div>;
}

interface CalendarProps {
  highlightedDate?: string;
  month?: { year: number; monthIndex0: number };
  onMonthChange?: (m: { year: number; monthIndex0: number }) => void;
}

function renderCalendar(props?: CalendarProps) {
  const { month = { year: 2026, monthIndex0: 5 }, ...rest } = props ?? {};
  render(
    <MemoryRouter>
      <MonthCalendar
        slug="backend-guild"
        month={month}
        onMonthChange={rest.onMonthChange ?? (() => {})}
        {...(rest.highlightedDate === undefined
          ? {}
          : { highlightedDate: rest.highlightedDate })}
      />
      <LocationDisplay />
    </MemoryRouter>,
  );
}

// Only fields resolveLabel() reads: id + title.
const SERIES_LIST = [{ id: 1, title: "Backend Guild Series" }] as never[];

// Base fixture: June 5, held; each entry below overrides only what differs.
function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
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

const heldEntry = entry({});
// June 12, ghost (virtual / projected) backed by series
const ghostEntry = entry({
  sessionId: null,
  occurrenceDate: "2026-06-12",
  scheduledAt: "2026-06-12T18:00:00Z",
  status: "SCHEDULED",
  virtual: true,
});
// June 20, one-off with its own title
const oneOffEntry = entry({
  sessionId: 99,
  seriesId: null,
  title: "Special Session",
  occurrenceDate: "2026-06-20",
  scheduledAt: "2026-06-20T14:00:00Z",
  durationMinutes: 45,
  status: "SCHEDULED",
});
// June 26, skipped ghost
const skippedEntry = entry({
  sessionId: null,
  occurrenceDate: "2026-06-26",
  scheduledAt: "2026-06-26T18:00:00Z",
  status: "SKIPPED",
  virtual: true,
});
// June 19, cancelled real session
const cancelledEntry = entry({
  sessionId: 2,
  occurrenceDate: "2026-06-19",
  scheduledAt: "2026-06-19T18:00:00Z",
  status: "CANCELLED",
});

/** Renders the calendar over exactly the entries a test cares about. */
function setupWith(data: ScheduleEntry[], props?: CalendarProps) {
  vi.mocked(useSchedule).mockReturnValue({
    data,
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: SERIES_LIST },
  } as never);
  renderCalendar(props);
}

function setupFull(props?: CalendarProps) {
  setupWith(
    [heldEntry, ghostEntry, oneOffEntry, skippedEntry, cancelledEntry],
    props,
  );
}

function setupStatus(isLoading: boolean, isError: boolean) {
  vi.mocked(useSchedule).mockReturnValue({
    data: undefined,
    isLoading,
    isError,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({ data: undefined } as never);
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

describe("MonthCalendar", () => {
  it("renders the month-calendar root with the current month label", () => {
    setupFull();
    expect(screen.getByTestId("month-calendar")).toBeInTheDocument();
    expect(screen.getByText(/june 2026/i)).toBeInTheDocument();
  });

  it("renders seven weekday headers and 35 day cells for June 2026", () => {
    setupFull();
    for (const day of ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]) {
      expect(screen.getByText(day)).toBeInTheDocument();
    }
    // June 2026 starts on Monday → 5 complete weeks = 35 cells
    expect(document.querySelectorAll("[data-date]")).toHaveLength(35);
  });

  it("shows a HELD series entry on its date", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-05']");
    expect(cell).not.toBeNull();
    expect(cell?.textContent).toContain("Backend Guild Series");
  });

  it("shows a ghost (virtual) series entry on its date", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-12']");
    expect(cell).not.toBeNull();
    expect(cell?.textContent).toContain("Backend Guild Series");
  });

  it("shows a one-off entry with its own title on its date", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-20']");
    expect(cell).not.toBeNull();
    expect(cell?.textContent).toContain("Special Session");
  });

  it("shows a SKIPPED entry on its date", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-26']");
    expect(cell).not.toBeNull();
    expect(cell?.textContent).toContain("Backend Guild Series");
  });

  it("shows a CANCELLED entry on its date", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-19']");
    expect(cell).not.toBeNull();
    expect(cell?.textContent).toContain("Backend Guild Series");
  });

  it("shows the month it is given (May)", () => {
    setupFull({ month: { year: 2026, monthIndex0: 4 } });
    expect(screen.getByText(/may 2026/i)).toBeInTheDocument();
  });

  it("shows the month it is given (July)", () => {
    setupFull({ month: { year: 2026, monthIndex0: 6 } });
    expect(screen.getByText(/july 2026/i)).toBeInTheDocument();
  });

  it("asks its owner to change month rather than deciding itself", async () => {
    const onMonthChange = vi.fn();
    setupWith([], { onMonthChange });
    await user.click(screen.getByRole("button", { name: "Next month" }));
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2026, monthIndex0: 6 });
  });

  it("a ghost chip opens its occurrence preview, a real chip its session page", async () => {
    setupFull();
    const ghostChip = document
      .querySelector("[data-date='2026-06-12']")
      ?.querySelector("button[title]") as HTMLElement;
    await user.dblClick(ghostChip);
    expect(screen.getByTestId("location")).toHaveTextContent(
      "/communities/backend-guild/series/1/occurrences/2026-06-12",
    );
    const realChip = document
      .querySelector("[data-date='2026-06-20']")
      ?.querySelector("button[title]") as HTMLElement;
    await user.dblClick(realChip);
    expect(screen.getByTestId("location")).toHaveTextContent(
      "/communities/backend-guild/sessions/99",
    );
  });

  /** The June 20 one-off is the first entry still to come from the mocked today, June 14. */
  it("marks the next occurrence still to come in the month that holds today", () => {
    setupFull();
    const cell = document.querySelector("[data-date='2026-06-20']");
    expect(cell?.querySelector("[data-testid='chip-next-up']")).not.toBeNull();
  });

  /**
   * "Up next" is only a question in the month that contains today. In any other month the first
   * entry in date order is simply the first one, and marking it would say "next up" about a
   * session five months out or five months gone; paging away removes the mark rather than moving
   * it.
   */
  it("marks nothing in a month that does not contain today", () => {
    const julyEntry = entry({
      sessionId: 5,
      occurrenceDate: "2026-07-20",
      scheduledAt: "2026-07-20T18:00:00Z",
      status: "SCHEDULED",
    });
    setupWith([julyEntry], { month: { year: 2026, monthIndex0: 6 } });

    expect(screen.queryByTestId("chip-next-up")).not.toBeInTheDocument();
  });

  it("shows the loading state", () => {
    setupStatus(true, false);
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows the error state", () => {
    setupStatus(false, true);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("renders session dots on a day that has entries", () => {
    setupFull();
    const dots = document.querySelector(
      "[data-date='2026-06-05'] [data-testid='day-dots']",
    );
    expect(dots).not.toBeNull();
    expect(dots?.children.length).toBeGreaterThan(0);
  });

  it("applies highlight classes to the targeted day only", () => {
    setupFull({ highlightedDate: "2026-06-20" });
    const highlighted = document.querySelector("[data-date='2026-06-20']");
    expect(highlighted?.className).toContain("ring-primary");
    expect(highlighted?.className).toContain("bg-primary/10");
    const other = document.querySelector("[data-date='2026-06-05']");
    expect(other?.className).not.toContain("bg-primary/10");
  });
});
