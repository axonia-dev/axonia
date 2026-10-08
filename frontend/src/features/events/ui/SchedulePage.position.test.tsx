import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SchedulePage } from "@/features/events/ui/SchedulePage";

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
vi.mock("@/features/events/ui/ScheduleTimeline", () => ({
  ScheduleTimeline: ({
    slug,
    fromIso,
    toIso,
    onShowMore,
  }: {
    slug: string;
    fromIso: string;
    toIso: string;
    onShowMore?: () => void;
  }) => (
    <div
      data-testid="schedule-timeline"
      data-slug={slug}
      data-days={String(
        Math.round(
          (new Date(toIso).getTime() - new Date(fromIso).getTime()) / 86400000,
        ),
      )}
    >
      {onShowMore && (
        <button type="button" onClick={onShowMore}>
          show-more
        </button>
      )}
    </div>
  ),
}));
vi.mock("@/features/events/ui/MonthCalendar", () => ({
  MonthCalendar: (props: {
    slug: string;
    month: { year: number; monthIndex0: number };
    onMonthChange: (m: { year: number; monthIndex0: number }) => void;
    onCreateSeries?: unknown;
    onCreateOneOff?: unknown;
    highlightedDate?: unknown;
  }) => (
    <div
      data-testid="month-calendar"
      data-slug={props.slug}
      data-month={`${props.month.year}-${String(props.month.monthIndex0 + 1).padStart(2, "0")}`}
    >
      <span>Mon</span>
      <button
        type="button"
        onClick={() => props.onMonthChange({ year: 2026, monthIndex0: 2 })}
      >
        go-march
      </button>
    </div>
  ),
}));
vi.mock("@/features/events/ui/OneOffSessionForm", () => ({
  OneOffSessionForm: () => <div data-testid="one-off-form" />,
}));
vi.mock("@/features/events/ui/SeriesForm", () => ({
  SeriesForm: () => <div data-testid="series-form" />,
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  localStorage.clear();
  sessionStorage.clear();
});

function LocationProbe() {
  const { search } = useLocation();
  return <span data-testid="location-search">{search}</span>;
}

function renderPage(entry = "/communities/backend-guild/schedule") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SchedulePage slug="backend-guild" />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe("SchedulePage: view and window in the URL", () => {
  it("takes the view from the URL over the stored preference", () => {
    localStorage.setItem("axonia-schedule-view", "calendar");
    renderPage("/communities/backend-guild/schedule?view=timeline");
    expect(screen.getByTestId("schedule-timeline")).toBeInTheDocument();
  });

  it("ignores a view it does not recognise", () => {
    renderPage("/communities/backend-guild/schedule?view=gantt");
    expect(screen.getByTestId("month-calendar")).toBeInTheDocument();
  });

  it("writes the chosen view to the URL", async () => {
    renderPage();
    await user.click(screen.getByRole("radio", { name: /timeline/i }));
    expect(screen.getByTestId("location-search")).toHaveTextContent(
      "view=timeline",
    );
  });

  it("widens the timeline window from the URL", () => {
    renderPage("/communities/backend-guild/schedule?view=timeline&days=180");
    expect(screen.getByTestId("schedule-timeline")).toHaveAttribute(
      "data-days",
      "180",
    );
  });

  it("clamps a window the URL asks for beyond the maximum", () => {
    renderPage("/communities/backend-guild/schedule?view=timeline&days=99999");
    expect(screen.getByTestId("schedule-timeline")).toHaveAttribute(
      "data-days",
      "1095",
    );
  });

  it("extends the window by a further 90 days on Show more", async () => {
    renderPage("/communities/backend-guild/schedule?view=timeline");
    expect(screen.getByTestId("schedule-timeline")).toHaveAttribute(
      "data-days",
      "90",
    );
    await user.click(screen.getByRole("button", { name: "show-more" }));
    expect(screen.getByTestId("schedule-timeline")).toHaveAttribute(
      "data-days",
      "180",
    );
  });

  it("stops offering Show more once the window is already at its maximum", () => {
    renderPage("/communities/backend-guild/schedule?view=timeline&days=1095");
    expect(screen.getByTestId("schedule-timeline")).toHaveAttribute(
      "data-days",
      "1095",
    );
    expect(
      screen.queryByRole("button", { name: "show-more" }),
    ).not.toBeInTheDocument();
  });
});

describe("SchedulePage: month in the URL", () => {
  it("shows the month the URL names", () => {
    renderPage("/communities/backend-guild/schedule?month=2026-03");
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-03",
    );
  });

  it("falls back to the sticky month when the URL names none", () => {
    sessionStorage.setItem("axonia-schedule-month:backend-guild", "2026-03");
    renderPage();
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-03",
    );
  });

  it("prefers the URL over the sticky month", () => {
    sessionStorage.setItem("axonia-schedule-month:backend-guild", "2026-03");
    renderPage("/communities/backend-guild/schedule?month=2026-09");
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-09",
    );
  });

  it("ignores a month it cannot read", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
    renderPage("/communities/backend-guild/schedule?month=august");
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-06",
    );
    vi.useRealTimers();
  });

  // The reported flow: leave the schedule on March, come back to March.
  it("remembers a browsed month for the next visit", async () => {
    const first = renderPage(
      "/communities/backend-guild/schedule?view=calendar&days=180",
    );
    await user.click(screen.getByRole("button", { name: "go-march" }));
    const search = screen.getByTestId("location-search").textContent;
    expect(search).toContain("month=2026-03");
    expect(search).toContain("view=calendar");
    expect(search).toContain("days=180");
    first.unmount();

    renderPage();
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-03",
    );
  });

  it("keeps one community's month out of another", async () => {
    const first = renderPage();
    await user.click(screen.getByRole("button", { name: "go-march" }));
    first.unmount();

    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-06-14T12:00:00Z"));
    render(
      <MemoryRouter initialEntries={["/communities/frontend-guild/schedule"]}>
        <SchedulePage slug="frontend-guild" />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-month",
      "2026-06",
    );
    vi.useRealTimers();
  });
});
