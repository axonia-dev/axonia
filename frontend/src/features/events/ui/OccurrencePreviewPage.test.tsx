import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useOpenOccurrence } from "@/features/events/api/use-open-occurrence";
import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import { useSkipOccurrence } from "@/features/events/api/use-sessions";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { OccurrencePreviewPage } from "@/features/events/ui/OccurrencePreviewPage";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/events/api/use-open-occurrence", () => ({
  useOpenOccurrence: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useSkipOccurrence: vi.fn(),
}));
vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(),
}));
const navigate = vi.fn();
vi.mock("react-router-dom", async (orig) => ({
  ...(await orig<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

const mockUseSchedule = vi.mocked(useSchedule);
const mockUseSeriesList = vi.mocked(useSeriesList);
const mockUseOpenOccurrence = vi.mocked(useOpenOccurrence);

const ghost: ScheduleEntry = {
  sessionId: null,
  seriesId: 5,
  title: null,
  occurrenceDate: "2026-07-02",
  scheduledAt: "2026-07-02T18:00:00Z",
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "SCHEDULED",
  virtual: true,
  place: EMPTY_PLACE,
  sections: [
    {
      id: null,
      sessionId: null,
      position: 0,
      sectionTypeId: 2,
      sectionType: "Talk",
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: "Main talk",
      durationMinutes: 30,
      slots: [],
    },
  ],
};

let user: ReturnType<typeof userEvent.setup>;

/** Pinned before the ghost occurrence, so the entry under test is one the clock has not passed.
 *  Whether Skip is on offer turns on that, and the fixture's date is a fixed one the real clock
 *  walks past. */
beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-07-01T12:00:00Z"));
  vi.clearAllMocks();
  mockUseSeriesList.mockReturnValue({
    data: { content: [{ id: 5, title: "Frontend Dojo" }] },
  } as never);
  mockUseOpenOccurrence.mockReturnValue({
    open: vi.fn(),
    isPending: false,
  });
  vi.mocked(useIsOrganizer).mockReturnValue(false);
  vi.mocked(useSkipOccurrence).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("OccurrencePreviewPage", () => {
  it("renders the projected structure and opens for sign-up", async () => {
    const open = vi.fn();
    mockUseOpenOccurrence.mockReturnValue({ open, isPending: false });
    mockUseSchedule.mockReturnValue({
      data: [ghost],
      isLoading: false,
    } as never);

    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );

    expect(screen.getByText("Frontend Dojo")).toBeInTheDocument();
    expect(screen.getByText(/Talk · Main talk/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /open for sign-up/i }));
    expect(open).toHaveBeenCalledWith(5, "2026-07-02");
  });

  it("shows a loading state", () => {
    mockUseSchedule.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as never);
    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows a fallback when the occurrence is gone", () => {
    mockUseSchedule.mockReturnValue({ data: [], isLoading: false } as never);
    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );
    expect(screen.getByText(/no longer on the schedule/i)).toBeInTheDocument();
  });

  it("offers Skip to an organizer and skips the occurrence", async () => {
    const mutate = vi.fn();
    vi.mocked(useIsOrganizer).mockReturnValue(true);
    vi.mocked(useSkipOccurrence).mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    mockUseSchedule.mockReturnValue({
      data: [ghost],
      isLoading: false,
    } as never);

    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );

    await user.click(screen.getByRole("button", { name: /^skip$/i }));
    expect(mutate).toHaveBeenCalledWith(
      { seriesId: 5, occurrenceDate: "2026-07-02" },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
  });

  /** The server refuses a retroactive skip outright (`OCCURRENCE_IN_PAST`), and this page surfaces
   *  no refusal at all: the press posted, was rejected, and left the organizer on an unchanged
   *  page with nothing to read. */
  it("withholds Skip once the clock has passed the occurrence, since the server refuses a retroactive one", () => {
    vi.setSystemTime(new Date("2026-07-03T12:00:00Z"));
    vi.mocked(useIsOrganizer).mockReturnValue(true);
    mockUseSchedule.mockReturnValue({
      data: [ghost],
      isLoading: false,
    } as never);

    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );

    expect(
      screen.getByRole("button", { name: /open for sign-up/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^skip$/i })).toBeNull();
  });

  it("hides Skip from non-organizers", () => {
    vi.mocked(useIsOrganizer).mockReturnValue(false);
    mockUseSchedule.mockReturnValue({
      data: [ghost],
      isLoading: false,
    } as never);
    render(
      <OccurrencePreviewPage
        slug="backend-guild"
        seriesId={5}
        date="2026-07-02"
      />,
    );
    expect(
      screen.queryByRole("button", { name: /^skip$/i }),
    ).not.toBeInTheDocument();
  });
});
