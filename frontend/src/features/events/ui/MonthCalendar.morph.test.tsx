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
import { morphNames } from "@/test/morph-names";
import { inQueryClient } from "@/test/query-client";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));
vi.mock(
  "@/features/events/api/use-ready-session",
  () => import("@/test/ready-session"),
);
vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-schedule-activity", () => ({
  useScheduleActivity: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
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
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: [{ id: 1, title: "Backend Guild Series" }] },
  } as never);
});
afterEach(() => vi.useRealTimers());

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: "Guild Sync",
    displayTitle: "Guild Sync",
    occurrenceDate: "2026-06-05",
    scheduledAt: "2026-06-05T18:00:00Z",
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

function renderCalendar(entries: ScheduleEntry[]) {
  vi.mocked(useSchedule).mockReturnValue({
    data: entries,
    isLoading: false,
    isError: false,
  } as never);
  return render(
    <MemoryRouter>
      <MonthCalendar
        slug="backend-guild"
        month={{ year: 2026, monthIndex0: 5 }}
        onMonthChange={() => {}}
      />
    </MemoryRouter>,
    inQueryClient(),
  );
}

describe("MonthCalendar morph source", () => {
  it("names nothing until a session is selected", () => {
    // when
    const { container } = renderCalendar([entry()]);

    // then
    expect(morphNames(container)).toEqual([]);
  });

  it("names only the desktop panel's heading for a session picked on the grid", async () => {
    // given
    const { container } = renderCalendar([entry()]);

    // when
    await user.click(screen.getByRole("button", { name: /guild sync/i }));

    // then
    expect(morphNames(container)).toEqual(["morph-title-session-1"]);
    expect(morphNames(screen.getByTestId("day-detail"))).toEqual([]);
  });

  it("names only the phone panel's heading for a session picked in the day list", async () => {
    // given
    const today = entry({
      sessionId: 3,
      occurrenceDate: "2026-06-14",
      scheduledAt: "2026-06-14T18:00:00Z",
    });
    const { container } = renderCalendar([today]);
    const detail = screen.getByTestId("day-detail");

    // when
    await user.click(
      within(detail).getByRole("button", { name: /guild sync/i }),
    );

    // then
    expect(morphNames(container)).toEqual(["morph-title-session-3"]);
    expect(morphNames(detail)).toEqual(["morph-title-session-3"]);
  });

  it("names nothing for a selected projection", async () => {
    // given
    const projected = entry({
      sessionId: null,
      virtual: true,
      occurrenceDate: "2026-06-20",
      scheduledAt: "2026-06-20T18:00:00Z",
    });
    const { container } = renderCalendar([projected]);

    // when
    await user.click(screen.getByRole("button", { name: /guild sync/i }));

    // then
    expect(screen.getAllByRole("complementary").length).toBeGreaterThan(0);
    expect(morphNames(container)).toEqual([]);
    expect(
      screen.getAllByRole("link", { name: /open the preview/i })[0],
    ).toHaveAttribute(
      "href",
      "/communities/backend-guild/series/1/occurrences/2026-06-20",
    );
  });
});
