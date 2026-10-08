import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SessionActions } from "@/features/events/ui/SessionActions";
import { A_PLACE, aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: () => ({
    data: [
      {
        id: 1,
        label: "Talk",
        expectsPresenter: true,
        icon: null,
        accentColor: null,
      },
    ],
  }),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useRescheduleSession: vi.fn(),
  useSkipOccurrence: vi.fn(),
}));
vi.mock("@/features/events/api/use-session-structure", () => ({
  useAddSection: () => ({ mutate: vi.fn() }),
  useUpdateSection: () => ({ mutate: vi.fn() }),
  useRemoveSection: () => ({ mutate: vi.fn() }),
  useReorderSections: () => ({ mutate: vi.fn() }),
}));

const LENGTH_FIELD = "Session length in minutes";

const mockUseReschedule = vi.mocked(useRescheduleSession);
const mockUseSkip = vi.mocked(useSkipOccurrence);

let user: ReturnType<typeof userEvent.setup>;
let saveMutate: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
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

async function openTheEditDialog(session: Session, canManage = true) {
  render(
    <SessionActions
      slug="g"
      session={session}
      canEditStructure={true}
      canManage={canManage}
    />,
  );
  await user.click(screen.getByRole("button", { name: /session actions/i }));
  await user.click(screen.getByRole("menuitem", { name: /^edit$/i }));
}

async function typeLength(minutes: string) {
  const field = screen.getByLabelText(LENGTH_FIELD);
  await user.clear(field);
  if (minutes !== "") {
    await user.type(field, minutes);
  }
}

function pressDone() {
  return user.click(screen.getByRole("button", { name: /^done$/i }));
}

function saved(): { sessionId: number; input: UpdateSessionInput } {
  const [call] = saveMutate.mock.calls[0] as [
    { sessionId: number; input: UpdateSessionInput },
  ];
  return call;
}

describe("a single session's length", () => {
  /**
   * The length was editable only on the series, so one occurrence that runs long or short - the
   * quarterly retro, the week the guest can only stay half an hour - had no way to say so, and its
   * sections were measured against a budget that was not its own.
   */
  it("can be changed for one occurrence without touching its series", async () => {
    await openTheEditDialog(aScheduledSession());

    await typeLength("30");
    await pressDone();

    expect(saveMutate).toHaveBeenCalledOnce();
    expect(saved().sessionId).toBe(10);
    expect(saved().input.durationMinutes).toBe(30);
  }, 15_000);

  /** A length is optional: clearing it says this session has no budget to measure against. */
  it("can be cleared back to no budget at all", async () => {
    await openTheEditDialog(aScheduledSession());

    await typeLength("");
    await pressDone();

    expect(saved().input.durationMinutes).toBeNull();
  }, 15_000);

  it("writes nothing when the length was not touched", async () => {
    await openTheEditDialog(aScheduledSession());

    await pressDone();

    expect(saveMutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  }, 15_000);

  /**
   * The whole session is replaced by this write, so the schedule has to go back exactly as stored -
   * read on the series' clock, which is the one the server composes in. 18:00Z is 20:00 in Paris;
   * stated on any other clock this save would move the session by that clock's offset.
   */
  it("moves nothing but the length", async () => {
    await openTheEditDialog(aScheduledSession());

    await typeLength("30");
    await pressDone();

    expect(saved().input.scheduledAt).toEqual({
      date: "2026-08-15",
      time: "20:00",
      zone: "Europe/Paris",
    });
    expect(saved().input.facilitatorUserId).toBe("alice");
  }, 15_000);

  /**
   * The occurrence's own place, never the one it resolved to. Posting the resolved place back
   * would detach this occurrence from its series' standing place for good, with nothing on screen
   * saying so - and this dialog does not show a series occurrence's place at all.
   */
  it("leaves an occurrence that inherits its place still inheriting", async () => {
    await openTheEditDialog(
      aScheduledSession({ place: A_PLACE, seriesPlace: A_PLACE }),
    );

    await typeLength("30");
    await pressDone();

    expect(saved().input.place).toEqual(EMPTY_PLACE);
  }, 15_000);

  /**
   * The length is a session fact, like the date and the place: `PUT /sessions/{id}` refuses
   * anybody but an organizer. A contributor may shape the structure, so the meter stays - as the
   * plain number it has always been.
   */
  it("stays read-only for a contributor who may shape the structure but not the session", async () => {
    await openTheEditDialog(aScheduledSession(), false);

    expect(screen.queryByLabelText(LENGTH_FIELD)).toBeNull();
    expect(screen.getByText(/0 \/ 60 min/)).toBeInTheDocument();
  }, 15_000);

  /**
   * A one-off's dialog carries a place editor too, and both are drafts of the same row. Two writes
   * would race: pressing Done blurs the length field, and whichever save read the session first
   * would post the other's old value straight back over it.
   */
  it("goes out with a one-off's place in a single write", async () => {
    await openTheEditDialog(
      aScheduledSession({
        id: 41,
        seriesId: null,
        occurrenceDate: null,
        seriesTimeZone: null,
      }),
    );

    await typeLength("30");
    await user.type(screen.getByLabelText("Location"), "The annex");
    await pressDone();

    expect(saveMutate).toHaveBeenCalledOnce();
    expect(saved().input.durationMinutes).toBe(30);
    expect(saved().input.place.location).toBe("The annex");
  }, 15_000);

  /** The meter is the affordance, so it has to answer the number being typed rather than the one
   *  last saved: an organizer shortening a session is usually doing it because of the overrun. */
  it("re-measures the sections against the length as it is typed", async () => {
    await openTheEditDialog(aScheduledSession());

    await typeLength("30");

    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuemax",
      "30",
    );
  }, 15_000);
});
