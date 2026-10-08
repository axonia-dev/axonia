import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { MonthCalendar } from "@/features/events/ui/MonthCalendar";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {},
}));

const SERIES_LIST = [{ id: 1, title: "Backend Guild Series" }] as never[];

const ghostEntry: ScheduleEntry = {
  sessionId: null,
  seriesId: 1,
  title: null,
  occurrenceDate: "2026-06-12",
  scheduledAt: "2026-06-12T18:00:00Z",
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "SCHEDULED",
  virtual: true,
  place: EMPTY_PLACE,
  sections: [],
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
  vi.mocked(useSchedule).mockReturnValue({
    data: [ghostEntry],
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({
    data: { content: SERIES_LIST },
  } as never);
});
afterEach(() => vi.useRealTimers());

it("renders draggable chips and a day context menu for an organizer", async () => {
  render(
    <MonthCalendar
      slug="backend-guild"
      month={{ year: 2026, monthIndex0: 5 }}
      onMonthChange={() => {}}
      canManage
      canStartSeries
    />,
    { wrapper },
  );

  // The future ghost renders as a draggable chip.
  const cell = document.querySelector("[data-date='2026-06-12']");
  expect(cell?.querySelector(".cursor-grab")).not.toBeNull();

  // Right-clicking the day opens the context menu with the day action items.
  fireEvent.contextMenu(cell as Element);
  expect(
    await screen.findByRole("menuitem", { name: /new series/i }),
  ).toBeInTheDocument();
});

/**
 * The whole chain the calendar owns, end to end: the cell resolves what the chip is called, the
 * menu opens on that entry rather than on the day under it, and it says so in the one place a
 * reader who cannot see the popover will hear it. Nothing below the cell can resolve this label,
 * which is why it is threaded from here.
 */
it("names the chip that was right-clicked, not the day beneath it", async () => {
  render(
    <MonthCalendar
      slug="backend-guild"
      month={{ year: 2026, monthIndex0: 5 }}
      onMonthChange={() => {}}
      canManage
      canStartSeries
    />,
    { wrapper },
  );

  const chip = document
    .querySelector("[data-date='2026-06-12']")
    ?.querySelector(".cursor-grab");
  fireEvent.contextMenu(chip as Element);

  expect(
    await screen.findByRole("menu", {
      name: /^Backend Guild Series, Fri, Jun 12/,
    }),
  ).toBeInTheDocument();
  expect(screen.queryByRole("menuitem", { name: /new series/i })).toBeNull();
});
