import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SchedulePage } from "@/features/events/ui/SchedulePage";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(() => false),
}));
vi.mock("@/features/community/api/use-contribution-policy", () => ({
  useContributionPolicy: vi.fn(() => ({ canStartSeries: false })),
}));
vi.mock("@/features/events/ui/ScheduleTimeline", () => ({
  ScheduleTimeline: () => <div data-testid="schedule-timeline" />,
}));
vi.mock("@/features/events/ui/MonthCalendar", () => ({
  MonthCalendar: () => <div data-testid="month-calendar" />,
}));
vi.mock("@/features/events/ui/CalendarSyncChip", () => ({
  CalendarSyncChip: () => null,
}));
vi.mock("@/features/bookmarks/ui/CommunitySavedSessions", () => ({
  CommunitySavedSessions: ({ slug }: { slug: string }) => (
    <div data-testid="saved-sessions" data-slug={slug} />
  ),
}));

function LocationProbe() {
  return <span data-testid="search">{useLocation().search}</span>;
}

function renderPage(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SchedulePage slug="backend-guild" />
      <LocationProbe />
    </MemoryRouter>,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  localStorage.clear();
  sessionStorage.clear();
});

describe("SchedulePage saved view", () => {
  it("shows the community's saved sessions in place of the view from ?saved=1", () => {
    renderPage("/communities/backend-guild/schedule?view=calendar&saved=1");

    expect(screen.getByTestId("saved-sessions")).toHaveAttribute(
      "data-slug",
      "backend-guild",
    );
    expect(screen.queryByTestId("month-calendar")).not.toBeInTheDocument();
    expect(screen.queryByTestId("schedule-timeline")).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Saved" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Calendar" })).not.toBeChecked();
  });

  it("picks Saved through the URL, keeping the chosen view to return to", async () => {
    renderPage("/communities/backend-guild/schedule?view=timeline");
    expect(screen.getByTestId("schedule-timeline")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Saved" }));

    expect(screen.getByTestId("saved-sessions")).toBeInTheDocument();
    expect(screen.queryByTestId("schedule-timeline")).not.toBeInTheDocument();
    expect(screen.getByTestId("search")).toHaveTextContent(
      "?view=timeline&saved=1",
    );
  });

  it("leaves saved when a view is picked", async () => {
    renderPage("/communities/backend-guild/schedule?view=timeline&saved=1");

    await user.click(screen.getByRole("radio", { name: "Calendar" }));

    expect(screen.getByTestId("month-calendar")).toBeInTheDocument();
    expect(screen.queryByTestId("saved-sessions")).not.toBeInTheDocument();
    expect(screen.getByTestId("search")).toHaveTextContent("?view=calendar");
  });
});
