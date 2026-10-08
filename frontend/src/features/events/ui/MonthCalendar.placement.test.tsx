import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useScheduleActivity } from "@/features/events/api/use-schedule-activity";
import { useSeriesList } from "@/features/events/api/use-series";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { MonthCalendar } from "@/features/events/ui/MonthCalendar";
import { useUsers } from "@/features/identity/api/use-users";
import { aScheduleEntry } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-schedule-activity", () => ({
  useScheduleActivity: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
  vi.mocked(useScheduleActivity).mockReturnValue({
    data: [],
    isSuccess: true,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: [{ id: 1, title: "Backend Guild Series" }] },
  } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});
afterEach(() => vi.useRealTimers());

function renderJune2026(entries: ScheduleEntry[]) {
  vi.mocked(useSchedule).mockReturnValue({
    data: entries,
    isLoading: false,
    isError: false,
  } as never);
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

function cellText(iso: string): string {
  return document.querySelector(`[data-date='${iso}']`)?.textContent ?? "";
}

describe("MonthCalendar entry placement", () => {
  it("draws a rescheduled occurrence in the cell it now meets in", () => {
    // given an occurrence moved off the Friday its series anchors it to
    renderJune2026([
      aScheduleEntry({
        occurrenceDate: "2026-06-12",
        scheduledAt: "2026-06-17T18:00:00Z",
      }),
    ]);
    // then the anchor it keeps for its series is not the cell it is drawn in
    expect(cellText("2026-06-12")).not.toContain("Backend Guild Series");
    expect(cellText("2026-06-17")).toContain("Backend Guild Series");
  });

  it("draws an untimed occurrence on its series' clock, not on UTC", () => {
    // given midnight in Paris, which is the evening before in UTC
    renderJune2026([
      aScheduleEntry({
        occurrenceDate: "2026-06-12",
        scheduledAt: "2026-06-11T22:00:00Z",
        hasTime: false,
      }),
    ]);
    // then
    expect(cellText("2026-06-11")).not.toContain("Backend Guild Series");
    expect(cellText("2026-06-12")).toContain("Backend Guild Series");
  });
});
