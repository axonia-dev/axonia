import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import type { Session } from "@/features/events/domain/session";
import { SessionActions } from "@/features/events/ui/SessionActions";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: () => ({ data: [] }),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useRescheduleSession: vi.fn(),
  useSkipOccurrence: vi.fn(),
}));
vi.mock("@/features/events/ui/SessionStructureEditor", () => ({
  SessionStructureEditor: () => <div data-testid="structure-editor" />,
}));

const mockUseReschedule = vi.mocked(useRescheduleSession);
const mockUseSkip = vi.mocked(useSkipOccurrence);

const scheduledSeriesSession: Session = aScheduledSession();

/** The same session with no series: a one-off carries no series zone either, because
 *  `SessionMapper`'s `Standing.NO_SERIES` yields null the moment `seriesId` is. */
const oneOffSession: Session = aScheduledSession({
  seriesId: null,
  seriesTimeZone: null,
});

let user: ReturnType<typeof userEvent.setup>;

/** Pinned a day before the fixture occurrence, so the session under test is one the clock has not
 *  passed. Which of the manage actions the kebab offers turns on that, and the fixture's date is a
 *  fixed one the real clock walks past. */
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-08-14T12:00:00Z"));
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseReschedule.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    reset: vi.fn(),
  } as never);
  mockUseSkip.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
});

describe("SessionActions", () => {
  /** The kebab as an organizer sees it, opened: what every case about a manage action starts from. */
  async function openManageMenu() {
    render(
      <SessionActions
        slug="g"
        session={scheduledSeriesSession}
        canEditStructure={false}
        canManage={true}
      />,
    );
    await user.click(screen.getByRole("button", { name: /session actions/i }));
  }

  /**
   * The fixture meets at 18:00Z, which is 20:00 in its series' Europe/Paris and 11:00 on the
   * viewer's pinned Los Angeles clock. The organizer running these cases is therefore always an
   * organizer abroad, which is the only condition under which the two clocks can be told apart.
   */
  async function openReschedule() {
    const rescheduleMutate = vi.fn();
    mockUseReschedule.mockReturnValue({
      mutate: rescheduleMutate,
      isPending: false,
      reset: vi.fn(),
    } as never);
    await openManageMenu();
    await user.click(screen.getByRole("menuitem", { name: /reschedule/i }));
    return rescheduleMutate;
  }

  function confirmed(mutate: ReturnType<typeof vi.fn>) {
    const [call] = mutate.mock.calls[0] as [
      { sessionId: number; input: UpdateSessionInput },
    ];
    return call;
  }

  it("renders null when no action applies", () => {
    // non-series, no permissions
    const { container } = render(
      <SessionActions
        slug="g"
        session={oneOffSession}
        canEditStructure={false}
        canManage={false}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders the kebab trigger when at least one action applies", () => {
    render(
      <SessionActions
        slug="g"
        session={scheduledSeriesSession}
        canEditStructure={true}
        canManage={false}
      />,
    );
    expect(
      screen.getByRole("button", { name: /session actions/i }),
    ).toBeInTheDocument();
  });

  it("shows 'Edit structure' item for canEditStructure + SCHEDULED session", async () => {
    render(
      <SessionActions
        slug="g"
        session={scheduledSeriesSession}
        canEditStructure={true}
        canManage={false}
      />,
    );
    await user.click(screen.getByRole("button", { name: /session actions/i }));
    expect(
      screen.getByRole("menuitem", { name: /^edit$/i }),
    ).toBeInTheDocument();
  });

  it("offers Edit structure on a held session that has been opened for editing, so a past session can be corrected or backfilled", async () => {
    render(
      <SessionActions
        slug="g"
        session={{ ...scheduledSeriesSession, status: "HELD" }}
        canEditStructure={true}
        canManage={false}
        editing={true}
      />,
    );
    await user.click(screen.getByRole("button", { name: /session actions/i }));
    expect(
      screen.getByRole("menuitem", { name: /^edit$/i }),
    ).toBeInTheDocument();
  });

  it("hides Edit structure on a held session whose Edit toggle is still off, so the kebab menu cannot restructure sections behind the toggle's back", () => {
    const { container } = render(
      <SessionActions
        slug="g"
        session={{ ...scheduledSeriesSession, status: "HELD" }}
        canEditStructure={true}
        canManage={false}
        editing={false}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("does not offer Edit structure on a cancelled session", () => {
    const { container } = render(
      <SessionActions
        slug="g"
        session={{ ...scheduledSeriesSession, status: "CANCELLED" }}
        canEditStructure={true}
        canManage={false}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("shows Reschedule and Skip for canManage + scheduled series occurrence", async () => {
    await openManageMenu();
    expect(
      screen.getByRole("menuitem", { name: /reschedule/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: /^skip$/i }),
    ).toBeInTheDocument();
  });

  /**
   * The server refuses a retroactive skip outright - `OCCURRENCE_IN_PAST`, whose own message says
   * to cancel it instead - and nothing on this page surfaces that conflict, so the item read as
   * live and answered by doing nothing at all. The calendar chip has always withheld it here;
   * this menu is the same decision seen from the session's own page.
   *
   * Reschedule stays: moving a session nobody held onto a new date is exactly what it is for.
   */
  it("withholds Skip once the clock has passed the occurrence, since the server refuses a retroactive one", async () => {
    vi.setSystemTime(new Date("2026-08-15T19:00:00Z"));
    await openManageMenu();
    expect(
      screen.getByRole("menuitem", { name: /reschedule/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /^skip$/i })).toBeNull();
  });

  it("hides Reschedule and Skip for a one-off session (no seriesId)", () => {
    const { container } = render(
      <SessionActions
        slug="g"
        session={oneOffSession}
        canEditStructure={false}
        canManage={true}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("opens the Edit structure dialog when the menu item is selected", async () => {
    render(
      <SessionActions
        slug="g"
        session={scheduledSeriesSession}
        canEditStructure={true}
        canManage={false}
      />,
    );
    await user.click(screen.getByRole("button", { name: /session actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /^edit$/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByTestId("structure-editor")).toBeInTheDocument();
  });

  it("opens the Reschedule dialog when the menu item is selected", async () => {
    await openReschedule();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByRole("group", { name: "New date and time date" }),
    ).toBeInTheDocument();
  }, 15_000);

  it("fires skip mutation with seriesId and occurrenceDate", async () => {
    const skipMutate = vi.fn();
    mockUseSkip.mockReturnValue({
      mutate: skipMutate,
      isPending: false,
    } as never);
    await openManageMenu();
    await user.click(screen.getByRole("menuitem", { name: /^skip$/i }));
    expect(skipMutate).toHaveBeenCalledOnce();
    expect(skipMutate).toHaveBeenCalledWith({
      seriesId: 3,
      occurrenceDate: "2026-08-15",
    });
  }, 15_000);

  it("fires reschedule mutation with the sessionId and the typed parts on Confirm", async () => {
    const rescheduleMutate = await openReschedule();
    await user.click(screen.getByRole("button", { name: /confirm/i }));
    expect(rescheduleMutate).toHaveBeenCalledOnce();
    const call = confirmed(rescheduleMutate);
    expect(call.sessionId).toBe(10);
    expect(call.input.durationMinutes).toBe(60);
    expect(call.input.facilitatorUserId).toBe("alice");
  }, 15_000);

  /**
   * The server composes an occurrence of a series in that series' zone and ignores the zone a
   * request states, so the field has to be read on the same clock. Seeded on the viewer's instead,
   * this gesture sent 11:00 to be read as 11:00 in Paris and moved the session nine hours - on the
   * one gesture that means "change nothing about when", and on the only editor a series
   * occurrence's meeting place has.
   */
  it("moves nothing when an organizer abroad opens the dialog and confirms it untouched", async () => {
    const rescheduleMutate = await openReschedule();

    // The field is read on the series' clock, and says so: the zone is named beside it, and the
    // sentence under it reads the seeded parts back as the instant they compose. 20:00 in
    // Europe/Paris on 2026-08-15 is 18:00Z - the instant already stored - which is 11:00 for the
    // viewer. Seeded on the viewer's clock instead, the same sentence would read 02:00.
    expect(screen.getByText("Paris")).toBeInTheDocument();
    expect(screen.getByText("11:00 in Los Angeles.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(confirmed(rescheduleMutate).input.scheduledAt).toEqual({
      date: "2026-08-15",
      time: "20:00",
      zone: "Europe/Paris",
    });
  }, 15_000);
});
