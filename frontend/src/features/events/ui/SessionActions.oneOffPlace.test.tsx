import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
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

const OWN_PLACE = {
  location: "Board room",
  joinUrl: "https://meet.example/architecture-review",
  joiningDetails: "Passcode 9090",
} satisfies MeetingPlace;

/** A session belonging to no series: no anchor, no standing place to fall back to. */
function aOneOff(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    id: 41,
    seriesId: null,
    // The backend cannot hand back a series zone without a series: `SessionMapper`'s
    // `Standing.NO_SERIES` yields null the moment `seriesId` is, so a fixture keeping the shared
    // factory's zone here would be a session no read can ever produce.
    seriesTimeZone: null,
    title: "Architecture review",
    occurrenceDate: null,
    scheduledAt: "2026-09-07T13:00:00Z",
    durationMinutes: 45,
    ...overrides,
  });
}

let user: ReturnType<typeof userEvent.setup>;
let saveMutate: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  // Reaches its success callback, the way an accepted write does: the dialog closes on it.
  saveMutate = vi.fn((_vars, options?: { onSuccess?: () => void }) =>
    options?.onSuccess?.(),
  );
  mockUseReschedule.mockReturnValue({
    mutate: saveMutate,
    isPending: false,
    isError: false,
    reset: vi.fn(),
  } as never);
  mockUseSkip.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
});

async function openTheEditDialog(session: Session) {
  render(
    <SessionActions
      slug="g"
      session={session}
      canEditStructure={true}
      canManage={true}
    />,
  );
  await user.click(screen.getByRole("button", { name: /session actions/i }));
  await user.click(screen.getByRole("menuitem", { name: /^edit$/i }));
  return screen.getByLabelText("Join link");
}

/** The dialog's one way out, which is also its commit. */
function pressDone() {
  return user.click(screen.getByRole("button", { name: /^done$/i }));
}

function saved(): { sessionId: number; input: UpdateSessionInput } {
  const [call] = saveMutate.mock.calls[0] as [
    { sessionId: number; input: UpdateSessionInput },
  ];
  return call;
}

describe("a one-off session's meeting place", () => {
  /**
   * The place was write-once. A one-off is offered no "Reschedule" - that menu item exists for an
   * occurrence of a series - so the place set at creation had no second surface anywhere in the
   * product, and a typo'd or expired link stayed in the reminder mail and in every subscriber's
   * calendar for good.
   */
  it("can be set after creation, through the dialog a one-off does reach", async () => {
    const field = await openTheEditDialog(aOneOff());
    expect(field).toHaveValue("");
    await user.type(screen.getByLabelText("Location"), OWN_PLACE.location);
    await user.type(field, OWN_PLACE.joinUrl);
    await user.type(
      screen.getByLabelText("Joining details"),
      OWN_PLACE.joiningDetails,
    );

    await pressDone();

    expect(saved().sessionId).toBe(41);
    expect(saved().input.place).toEqual(OWN_PLACE);
  }, 15_000);

  /**
   * The place is the only draft in a dialog whose every other control writes on the spot, which is
   * what made the footer's Done safe to mean "just close". A place saved by a button of its own,
   * with that Done still sitting bottom-right in the conventional commit position, threw the
   * correction away on the gesture most likely to be used - leaving the bad link in the reminder
   * mail and in every subscriber's calendar, which is the exact failure this editor exists to
   * prevent. The footer writes.
   */
  it("survives the footer action rather than being discarded by it", async () => {
    const field = await openTheEditDialog(aOneOff());
    await user.type(field, OWN_PLACE.joinUrl);

    await pressDone();

    expect(saveMutate).toHaveBeenCalledOnce();
    expect(saved().input.place.joinUrl).toBe(OWN_PLACE.joinUrl);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  }, 15_000);

  it("prefills the place the one-off already holds and can clear it", async () => {
    const field = await openTheEditDialog(
      aOneOff({ place: OWN_PLACE, placeOverride: OWN_PLACE }),
    );
    expect(field).toHaveValue(OWN_PLACE.joinUrl);
    expect(screen.getByLabelText("Location")).toHaveValue(OWN_PLACE.location);
    await user.clear(screen.getByLabelText("Location"));
    await user.clear(field);
    await user.clear(screen.getByLabelText("Joining details"));

    await pressDone();

    expect(saved().input.place).toEqual(EMPTY_PLACE);
  }, 15_000);

  /**
   * The schedule is handed straight back untouched, which is what keeps the save from being a
   * reschedule: the backend's release policy leaves an untouched reminder exactly where it is
   * only while the instant it is given equals the one already stored.
   */
  it("moves nothing but the place", async () => {
    const field = await openTheEditDialog(aOneOff());
    await user.type(field, OWN_PLACE.joinUrl);

    await pressDone();

    expect(saved().input.scheduledAt).toEqual({
      date: "2026-09-07",
      time: "13:00",
      zone: "UTC",
    });
  }, 15_000);

  it("writes nothing when the place was not touched", async () => {
    await openTheEditDialog(
      aOneOff({ place: OWN_PLACE, placeOverride: OWN_PLACE }),
    );

    await pressDone();

    expect(saveMutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  }, 15_000);

  /**
   * A refusal - the server rejects a join link that does not start with http - has to keep the
   * edit on screen. Closing regardless would throw away the correction and say nothing, the same
   * reason the reschedule dialog waits for its own write to be accepted.
   */
  it("keeps the dialog open with the edit intact when the join link is refused", async () => {
    mockUseReschedule.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      reset: vi.fn(),
    } as never);
    const field = await openTheEditDialog(aOneOff());
    await user.type(field, "meet.example/architecture-review");

    await pressDone();

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Join link")).toHaveValue(
      "meet.example/architecture-review",
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
  }, 15_000);

  /**
   * A series occurrence keeps a single place editor. Its own dialog already carries one, and a
   * second copy here would leave two blocks writing the same three columns with different rules
   * about what an empty one means.
   */
  it("stays out of the dialog of a session that has a reschedule editor", async () => {
    render(
      <SessionActions
        slug="g"
        session={aOneOff({ seriesId: 3, occurrenceDate: "2026-09-07" })}
        canEditStructure={true}
        canManage={true}
      />,
    );
    await user.click(screen.getByRole("button", { name: /session actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /^edit$/i }));

    expect(screen.queryByLabelText("Join link")).not.toBeInTheDocument();
  }, 15_000);

  /** A one-off inherits nothing, so there is no rule to state and nothing to describe. */
  it("offers no description, because a one-off has nothing to inherit", async () => {
    await openTheEditDialog(aOneOff());

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription("");
  }, 15_000);
});
