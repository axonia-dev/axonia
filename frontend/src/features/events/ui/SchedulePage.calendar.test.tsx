import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SchedulePage } from "@/features/events/ui/SchedulePage";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

// Its own file rather than more of SchedulePage.test.tsx, which is at the 300-line ceiling.
vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: () => false,
}));
vi.mock("@/features/community/api/use-contribution-policy", () => ({
  useContributionPolicy: () => ({
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  }),
}));
// Every child the page can render, not only the one today's default view reaches: there is
// no QueryClientProvider here, so an unmocked child would take the file down the day the
// default view changes.
vi.mock("@/features/events/ui/MonthCalendar", () => ({
  MonthCalendar: () => <div data-testid="month-calendar" />,
}));
vi.mock("@/features/events/ui/ScheduleTimeline", () => ({
  ScheduleTimeline: () => <div data-testid="schedule-timeline" />,
}));
vi.mock("@/features/events/ui/OneOffSessionForm", () => ({
  OneOffSessionForm: () => <div data-testid="one-off-form" />,
}));
vi.mock("@/features/events/ui/SeriesForm", () => ({
  SeriesForm: () => <div data-testid="series-form" />,
}));
vi.mock("@/features/events/ui/CalendarSubscribeDialog", () => ({
  CalendarSubscribeDialog: ({ isOpen }: { slug: string; isOpen: boolean }) =>
    isOpen ? <div data-testid="calendar-subscribe-dialog" /> : null,
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/communities/frontend-dojo/schedule"]}>
      <SchedulePage slug="frontend-dojo" />
    </MemoryRouter>,
  );
}

async function openScheduleActions() {
  await user.click(screen.getByRole("button", { name: /schedule actions/i }));
}

describe("SchedulePage: add to calendar", () => {
  it("offers the action to a plain member, who has a calendar of their own", async () => {
    renderPage();

    await openScheduleActions();

    expect(
      screen.getByRole("menuitem", { name: /add to calendar/i }),
    ).toBeInTheDocument();
  });

  it("opens the subscribe dialog when the action is selected", async () => {
    renderPage();
    expect(screen.queryByTestId("calendar-subscribe-dialog")).toBeNull();

    await openScheduleActions();
    await user.click(
      screen.getByRole("menuitem", { name: /add to calendar/i }),
    );

    expect(screen.getByTestId("calendar-subscribe-dialog")).toBeInTheDocument();
  });
});
