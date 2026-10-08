import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { useSetUpcomingRelease } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { sessionRelease } from "@/features/events/ui/release-chip";
import { ReminderChip } from "@/features/events/ui/ReminderChip";
import { ApiError } from "@/lib/api-client";
import { typeDateTime } from "@/test/date-time-fields";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-session", () => ({
  useSetUpcomingRelease: vi.fn(),
  // The chip carries the preview dialog beside the answers; it stays shut here.
  useMessagePreview: vi.fn(() => ({ isLoading: false, isError: false })),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

const releaseMutate = vi.fn();
const releaseReset = vi.fn();

const FIELD = /reminder release date and time/i;

/** The reminder's own fixture: a session far enough ahead to still have one, and a reminder set
 *  two days before it. Both instants are named here because every case below reads one of them. */
function aSession(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    id: 7,
    scheduledAt: "2026-03-14T18:00:00Z",
    upcomingReleaseAt: "2026-03-12T09:00:00Z",
    upcomingStatus: {
      state: "SCHEDULED",
      recipientCount: 3,
      sent: 0,
      couldNotReach: 0,
    },
    ...overrides,
  });
}

/** A session whose reminder is suppressed: no instant, and the state that goes with it. The two
 *  always travel together - a null release instant IS how this endpoint spells OFF. */
function noReminderYet(): Session {
  return aSession({
    upcomingReleaseAt: null,
    upcomingStatus: {
      state: "OFF",
      recipientCount: 3,
      sent: 0,
      couldNotReach: 0,
    },
  });
}

/**
 * The chip's own panel, opened.
 *
 * The phase is passed rather than derived so these cases stay about the reminder's answers and its
 * bound, not about which release a given wall-clock date puts in play - `release-chip.test.ts`
 * owns that rule, and deriving it here would silently retire this whole file the day the fixture's
 * date fell into the past.
 */
async function openMenu(session: Session = aSession()) {
  const release = sessionRelease(session, "upcoming");
  if (release === null) throw new Error("the fixture states no reminder");
  render(
    <ReminderChip slug="backend-guild" session={session} release={release} />,
  );
  const user = userEvent.setup({ delay: null });
  await user.click(screen.getByRole("button", { name: /^Reminder/ }));
  return user;
}

async function openScheduleDialog(session: Session = aSession()) {
  const user = await openMenu(session);
  await user.click(screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }));
  return user;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSetUpcomingRelease).mockReturnValue({
    mutate: releaseMutate,
    reset: releaseReset,
    isPending: false,
    error: null,
  } as never);
});

describe("ReminderChip - the reminder's own words on the shared menu", () => {
  // "Reminder" is the noun the product already uses for this message everywhere a member or
  // an organizer meets it. The menu is shared with the wrap-up and takes its nouns as a
  // constant, so crossing the two constants would leave a working menu saying the wrong word.
  it("names the reminder in its note, its suppression hint and its schedule dialog", async () => {
    const user = await openMenu();

    expect(screen.getByText("When does the reminder go out")).toBeVisible();
    expect(screen.getByText("This session gets no reminder")).toBeVisible();

    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );

    expect(
      screen.getByRole("dialog", { name: "Schedule the reminder" }),
    ).toBeInTheDocument();
  });
});

describe("ReminderChip - the three answers, as this endpoint spells them", () => {
  it("says 'send now' as a flag, never as the browser's clock", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /send it now/i }),
    );

    // The server stamps its own clock for this one. Posting an instant instead would have a
    // browser running a second slow refused as a pick in the past - the reminder refuses
    // one rather than clamping it, unlike the wrap-up.
    expect(releaseMutate).toHaveBeenCalledWith(
      { sessionId: 7, input: { sendNow: true } },
      expect.objectContaining({ onError: expect.any(Function) as unknown }),
    );
    const [call] = releaseMutate.mock.calls[0] as [
      { input: Record<string, unknown> },
    ];
    expect(call.input).not.toHaveProperty("releaseAt");
  });

  it("withholds the reminder with the empty payload - neither answer given", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    );

    expect(releaseMutate).toHaveBeenCalledWith(
      { sessionId: 7, input: {} },
      expect.objectContaining({ onError: expect.any(Function) as unknown }),
    );
  });

  it("schedules the exact UTC instant for the picked local wall-clock time", async () => {
    const user = await openScheduleDialog();

    await typeDateTime(user, "2026-03-13", "15:30");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    // 2026-03-13T15:30 local (PDT, UTC-7) is 2026-03-13T22:30:00.000Z.
    expect(releaseMutate).toHaveBeenCalledWith(
      { sessionId: 7, input: { releaseAt: "2026-03-13T22:30:00.000Z" } },
      expect.objectContaining({
        onSuccess: expect.any(Function) as unknown,
        onError: expect.any(Function) as unknown,
      }),
    );
  });
});

describe("ReminderChip - the session's own start bounds the picker", () => {
  /**
   * The bound is on a date and a time together, and neither field can express half of it, so the
   * prompt naming it and the check below refusing anything past it are the whole of the bound's
   * courtesy: there is nothing for the fields themselves to cap.
   */
  it("names the session's own start in the prompt as the bound", async () => {
    await openScheduleDialog();

    // The session starts 2026-03-14T18:00Z, which is 11:00 under the suite's PDT pin.
    expect(
      screen.getByText(
        /It has to land before the session starts, Sat, Mar 14, 11:00 AM PDT\./,
      ),
    ).toBeInTheDocument();
  });

  it("refuses a pick at or after the start before it is ever posted", async () => {
    const user = await openScheduleDialog();

    await typeDateTime(user, "2026-03-14", "11:00");

    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "That is after the session starts. Pick a time before Sat, Mar 14, 11:00 AM PDT.",
    );

    await user.click(screen.getByRole("button", { name: "Confirm" }));
    expect(releaseMutate).not.toHaveBeenCalled();
  });

  it("marks the field itself invalid and points it at the reason", async () => {
    const user = await openScheduleDialog();
    const field = screen.getByLabelText(FIELD);
    const dateField = screen.getByRole("group", { name: "Sends at date" });
    const segment = screen.getByRole("spinbutton", { name: /month/i });

    expect(segment).not.toHaveAttribute("aria-invalid", "true");
    expect(dateField).not.toHaveAttribute("data-invalid");

    await typeDateTime(user, "2026-03-14", "11:00");

    // Without the first two a screen-reader user hears the alert once as it appears and gets
    // nothing on tabbing back to the control it is about. The refused border is the sighted half
    // of the same signal, drawn off the state the fields carry rather than a class this dialog
    // swaps by hand.
    expect(segment).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveAccessibleDescription(
      "That is after the session starts. Pick a time before Sat, Mar 14, 11:00 AM PDT.",
    );
    expect(dateField).toHaveAttribute("data-invalid");
  }, 15_000);

  it("admits a pick that is still before the start", async () => {
    const user = await openScheduleDialog();

    await typeDateTime(user, "2026-03-14", "10:59");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(releaseMutate).toHaveBeenCalledTimes(1);
  });
});

// One shared dialog serves both message types, so this branch is the wrap-up's warning too.
describe("ReminderChip - the prompt warns before it overwrites", () => {
  it("says the pick replaces the current schedule when there is one", async () => {
    await openScheduleDialog();

    expect(
      screen.getByText(/This replaces the current schedule\./),
    ).toBeInTheDocument();
  });

  it("says nothing about replacing when the session has no reminder yet", async () => {
    const user = await openMenu(noReminderYet());
    await user.click(
      screen.getByRole("menuitemradio", { name: /^pick a time/i }),
    );

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByText(/replaces the current schedule/)).toBeNull();
  });
});

describe("ReminderChip - a session created inside its own lead window", () => {
  it("offers send-now and a pick, with no reminder currently set", async () => {
    await openMenu(noReminderYet());

    expect(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    ).toHaveAttribute("aria-checked", "true");
    expect(
      screen.getByRole("menuitemradio", { name: /send it now/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitemradio", { name: /^pick a time/i }),
    ).toHaveAttribute("aria-checked", "false");
  });
});

describe("ReminderChip - a failed mutation surfaces an error, never silently", () => {
  it("notifies with the refusal's own reason, and leaves the dialog open", async () => {
    const user = await openScheduleDialog();
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    const [, options] = releaseMutate.mock.calls[0] as [
      unknown,
      { onError: (error: unknown) => void },
    ];
    // The server refusal is the real guard: `now` advances while the dialog sits open, so a
    // pick that was in range when it was made can still be refused on confirm.
    options.onError(new ApiError(400, "x", "UPCOMING_RELEASE_IN_PAST"));

    expect(notifyError).toHaveBeenCalledWith(
      "That time has already passed. Pick a later one.",
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
