import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useScheduleActivity } from "@/features/events/api/use-schedule-activity";
import { useSeriesList } from "@/features/events/api/use-series";
import { MonthCalendar } from "@/features/events/ui/MonthCalendar";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-schedule-activity", () => ({
  useScheduleActivity: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
// No network, no QueryClient needed, same way SlotRow.test.tsx mocks it.
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
  vi.mocked(useScheduleActivity).mockReturnValue({
    data: [],
    isSuccess: true,
  } as never);
  vi.mocked(useSchedule).mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});
afterEach(() => vi.useRealTimers());

// June 2026 starts on a Monday and has 30 days, so the grid's trailing cells
// spill into July without any leading days from May.
function calendar(
  month: { year: number; monthIndex0: number },
  onMonthChange: (m: { year: number; monthIndex0: number }) => void,
) {
  return (
    <MemoryRouter>
      <MonthCalendar
        slug="backend-guild"
        month={month}
        onMonthChange={onMonthChange}
      />
    </MemoryRouter>
  );
}

function renderJune2026(onMonthChange = vi.fn()) {
  const view = render(calendar({ year: 2026, monthIndex0: 5 }, onMonthChange));
  return { onMonthChange, rerender: view.rerender };
}

function dayButton(iso: string) {
  return document.querySelector(`[data-date='${iso}'] button[aria-pressed]`);
}

describe("MonthCalendar overflow-day selection", () => {
  it("clicking a trailing day outside the shown month navigates there and keeps that day selected", async () => {
    const onMonthChange = vi.fn();
    const { rerender } = renderJune2026(onMonthChange);
    // July 3, not July 1: the pre-fix fallback also lands on the 1st of the
    // newly shown month, so only a non-first day tells the fix apart from it.
    const julyThird = dayButton("2026-07-03");
    expect(julyThird).not.toBeNull();
    await user.click(julyThird as HTMLElement);
    expect(onMonthChange).toHaveBeenCalledWith({ year: 2026, monthIndex0: 6 });

    // Simulate the owner (SchedulePage) responding to onMonthChange by
    // passing the new month back down, the way it does in the real app.
    rerender(calendar({ year: 2026, monthIndex0: 6 }, onMonthChange));
    expect(dayButton("2026-07-03")).toHaveAttribute("aria-pressed", "true");
  });

  it("clicking a day inside the shown month leaves the month alone", async () => {
    const { onMonthChange } = renderJune2026();
    const june20 = dayButton("2026-06-20");
    expect(june20).not.toBeNull();
    await user.click(june20 as HTMLElement);
    expect(onMonthChange).not.toHaveBeenCalled();
    expect(june20).toHaveAttribute("aria-pressed", "true");
  });
});
