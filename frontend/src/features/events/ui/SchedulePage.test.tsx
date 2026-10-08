import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useContributionPolicy } from "@/features/community/api/use-contribution-policy";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { SchedulePage } from "@/features/events/ui/SchedulePage";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(() => false),
}));
vi.mock("@/features/community/api/use-contribution-policy", () => ({
  useContributionPolicy: vi.fn(() => ({
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  })),
}));
vi.mock("@/features/events/ui/ScheduleTimeline", () => ({
  ScheduleTimeline: ({
    slug,
    onShowMore,
  }: {
    slug: string;
    fromIso: string;
    toIso: string;
    onShowMore: () => void;
  }) => (
    <div data-testid="schedule-timeline" data-slug={slug}>
      <button type="button" onClick={onShowMore}>
        show-more
      </button>
    </div>
  ),
}));
vi.mock("@/features/events/ui/MonthCalendar", () => ({
  MonthCalendar: (props: {
    slug: string;
    onCreateSeries?: unknown;
    onCreateOneOff?: unknown;
    highlightedDate?: unknown;
  }) => (
    <div data-testid="month-calendar" data-slug={props.slug}>
      <span>Mon</span>
    </div>
  ),
}));
vi.mock("@/features/events/ui/OneOffSessionForm", () => ({
  OneOffSessionForm: ({ onSaved }: { slug: string; onSaved: () => void }) => (
    <div data-testid="one-off-form">
      <button type="button" onClick={onSaved}>
        save
      </button>
    </div>
  ),
}));
vi.mock("@/features/events/ui/SeriesForm", () => ({
  SeriesForm: ({ onSaved }: { slug: string; onSaved: () => void }) => (
    <div data-testid="series-form">
      <button type="button" onClick={onSaved}>
        save-series
      </button>
    </div>
  ),
}));

const mockUseIsOrganizer = vi.mocked(useIsOrganizer);
const mockUseContributionPolicy = vi.mocked(useContributionPolicy);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  localStorage.clear();
  sessionStorage.clear();
  mockUseIsOrganizer.mockReturnValue(false);
  mockUseContributionPolicy.mockReturnValue({
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  });
});

function renderPage(entry = "/communities/backend-guild/schedule") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SchedulePage slug="backend-guild" />
    </MemoryRouter>,
  );
}

describe("SchedulePage", () => {
  it("renders the Schedule heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /schedule/i }),
    ).toBeInTheDocument();
  });

  it("defaults to the calendar view", () => {
    renderPage();
    expect(screen.getByTestId("month-calendar")).toBeInTheDocument();
    expect(screen.queryByTestId("schedule-timeline")).toBeNull();
  });

  it("passes the slug to MonthCalendar", () => {
    renderPage();
    expect(screen.getByTestId("month-calendar")).toHaveAttribute(
      "data-slug",
      "backend-guild",
    );
  });

  it("shows the ScheduleTimeline when Timeline is selected", async () => {
    renderPage();
    await user.click(screen.getByRole("radio", { name: /timeline/i }));
    expect(screen.getByTestId("schedule-timeline")).toBeInTheDocument();
    expect(screen.queryByTestId("month-calendar")).toBeNull();
  });

  it("returns to calendar view when Calendar is clicked again", async () => {
    renderPage();
    await user.click(screen.getByRole("radio", { name: /timeline/i }));
    await user.click(screen.getByRole("radio", { name: /calendar/i }));
    expect(screen.getByTestId("month-calendar")).toBeInTheDocument();
    expect(screen.queryByTestId("schedule-timeline")).toBeNull();
  });

  it("restores the last-selected view after a remount", async () => {
    const first = renderPage();
    await user.click(screen.getByRole("radio", { name: /timeline/i }));
    expect(screen.getByTestId("schedule-timeline")).toBeInTheDocument();
    first.unmount();

    renderPage();
    expect(screen.getByTestId("schedule-timeline")).toBeInTheDocument();
    expect(screen.queryByTestId("month-calendar")).toBeNull();
  });

  it("exposes Timeline and Calendar toggle controls", () => {
    renderPage();
    expect(
      screen.getByRole("radio", { name: /timeline/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /calendar/i }),
    ).toBeInTheDocument();
  });
});

async function openNewMenu() {
  await user.click(screen.getByRole("button", { name: /\+ new/i }));
}

describe("SchedulePage: one-off session action", () => {
  it("offers the One-off session action for an organizer", async () => {
    // given
    mockUseIsOrganizer.mockReturnValue(true);

    // when
    renderPage();
    await openNewMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: /one-off session/i }),
    ).toBeInTheDocument();
  });

  it("offers the One-off session action when policy grants canStartSeries", async () => {
    // given
    mockUseContributionPolicy.mockReturnValue({
      canStartSeries: true,
      canEditStructure: false,
      canQueueTopics: false,
      canManageSchedule: false,
    });

    // when
    renderPage();
    await openNewMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: /one-off session/i }),
    ).toBeInTheDocument();
  });

  it("hides the + New menu for a plain member with curated policy", () => {
    // given — defaults: not organizer, no policy grants

    // when
    renderPage();

    // then
    expect(screen.queryByRole("button", { name: /\+ new/i })).toBeNull();
  });

  it("reveals the OneOffSessionForm inside a dialog when the action is selected", async () => {
    // given
    mockUseIsOrganizer.mockReturnValue(true);
    renderPage();

    // when
    await openNewMenu();
    await user.click(
      screen.getByRole("menuitem", { name: /one-off session/i }),
    );

    // then
    expect(
      screen.getByRole("dialog", { name: /new one-off session/i }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("one-off-form")).toBeInTheDocument();
  });

  it("closes the dialog after onSaved is called", async () => {
    // given
    mockUseIsOrganizer.mockReturnValue(true);
    renderPage();
    await openNewMenu();
    await user.click(
      screen.getByRole("menuitem", { name: /one-off session/i }),
    );
    expect(
      screen.getByRole("dialog", { name: /new one-off session/i }),
    ).toBeInTheDocument();

    // when — the form's internal save button triggers onSaved
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(
      screen.queryByRole("dialog", { name: /new one-off session/i }),
    ).toBeNull();
  });
});

describe("SchedulePage: new series action", () => {
  it("offers the New series action for someone who can start a series", async () => {
    mockUseIsOrganizer.mockReturnValue(true);
    renderPage();
    await openNewMenu();
    expect(
      screen.getByRole("menuitem", { name: /new series/i }),
    ).toBeInTheDocument();
  });

  it("hides the + New menu for a plain member with curated policy", () => {
    renderPage();
    expect(screen.queryByRole("button", { name: /\+ new/i })).toBeNull();
  });

  it("reveals the SeriesForm inside a dialog when the action is selected", async () => {
    mockUseIsOrganizer.mockReturnValue(true);
    renderPage();
    await openNewMenu();
    await user.click(screen.getByRole("menuitem", { name: /new series/i }));
    expect(
      screen.getByRole("dialog", { name: /new series/i }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("series-form")).toBeInTheDocument();
  });

  it("closes the dialog after the SeriesForm saves", async () => {
    mockUseIsOrganizer.mockReturnValue(true);
    renderPage();
    await openNewMenu();
    await user.click(screen.getByRole("menuitem", { name: /new series/i }));
    expect(
      screen.getByRole("dialog", { name: /new series/i }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^save-series$/i }));
    expect(screen.queryByRole("dialog", { name: /new series/i })).toBeNull();
  });
});
