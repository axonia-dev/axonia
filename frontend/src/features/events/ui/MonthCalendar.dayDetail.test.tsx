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

function renderCalendar(props?: {
  month?: { year: number; monthIndex0: number };
  onMonthChange?: (m: { year: number; monthIndex0: number }) => void;
}) {
  const { month = { year: 2026, monthIndex0: 5 }, ...rest } = props ?? {};
  render(
    <MemoryRouter>
      <MonthCalendar
        slug="backend-guild"
        month={month}
        onMonthChange={rest.onMonthChange ?? (() => {})}
      />
    </MemoryRouter>,
  );
}

const SERIES_LIST = [{ id: 1, title: "Backend Guild Series" }] as never[];

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

function setupFull(props?: {
  month?: { year: number; monthIndex0: number };
  onMonthChange?: (m: { year: number; monthIndex0: number }) => void;
}) {
  vi.mocked(useSchedule).mockReturnValue({
    data: [heldEntry, ghostEntry],
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: SERIES_LIST },
  } as never);
  renderCalendar(props);
}

describe("MonthCalendar day detail", () => {
  it("day detail defaults to today and shows an empty state", () => {
    setupFull();
    // Today is mocked to 2026-06-14, which has no sessions in setupFull().
    const detail = screen.getByTestId("day-detail");
    expect(detail.textContent).toMatch(/no sessions/i);
  });

  it("tapping a day shows that day's sessions in the detail panel", async () => {
    setupFull();
    const cell = document.querySelector(
      "[data-date='2026-06-12']",
    ) as HTMLElement;
    await user.click(
      within(cell).getByRole("button", { name: /friday, june 12/i }),
    );
    const detail = screen.getByTestId("day-detail");
    expect(detail.textContent).toContain("Backend Guild Series");
    expect(
      within(detail).getByRole("button", { name: /backend guild series/i }),
    ).toBeInTheDocument();
  });

  // Today's panel under another month's grid shows a day the reader cannot see.
  it("moves the day panel into the month being shown", () => {
    vi.mocked(useSchedule).mockReturnValue({ data: [] } as never);
    vi.mocked(useSeriesList).mockReturnValue({
      data: { content: [] },
    } as never);
    renderCalendar({ month: { year: 2026, monthIndex0: 2 } });
    expect(screen.getByText(/sunday, march 1/i)).toBeInTheDocument();
  });
});
