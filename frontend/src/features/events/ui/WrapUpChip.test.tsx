import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { useSetWrapUpRelease } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { sessionRelease } from "@/features/events/ui/release-chip";
import { WrapUpChip } from "@/features/events/ui/WrapUpChip";
import { ApiError } from "@/lib/api-client";
import { typeDateTime } from "@/test/date-time-fields";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-session", () => ({
  useSetWrapUpRelease: vi.fn(),
  // The chip carries the preview dialog beside the answers; it stays shut here.
  useMessagePreview: vi.fn(() => ({ isLoading: false, isError: false })),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

const releaseMutate = vi.fn();
const releaseReset = vi.fn();

/** The wrap-up's own fixture: a session already held and closed out, with a wrap-up scheduled
 *  for three days later. */
function aSession(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    id: 7,
    status: "HELD",
    scheduledAt: "2026-03-09T10:00:00Z",
    closedAt: "2026-03-09T12:00:00Z",
    wrapUpReleaseAt: "2026-03-12T09:00:00Z",
    wrapUpStatus: {
      state: "SCHEDULED",
      recipientCount: 3,
      sent: 0,
      couldNotReach: 0,
    },
    ...overrides,
  });
}

function mockRelease(overrides: { isPending?: boolean } = {}) {
  vi.mocked(useSetWrapUpRelease).mockReturnValue({
    mutate: releaseMutate,
    reset: releaseReset,
    isPending: false,
    error: null,
    ...overrides,
  } as never);
}

/** The chip's own panel, opened. The phase is passed rather than derived: these cases are about
 *  the wrap-up's own answers, not about which release a given wall-clock date puts in play -
 *  `release-chip.test.ts` owns that rule. */
async function openMenu(session: Session = aSession()) {
  const release = sessionRelease(session, "held");
  if (release === null) throw new Error("the fixture states no wrap-up");
  render(
    <WrapUpChip slug="backend-guild" session={session} release={release} />,
  );
  const user = userEvent.setup({ delay: null });
  await user.click(screen.getByRole("button", { name: /^Wrap-up/ }));
  return user;
}

/** A session whose wrap-up is suppressed: no instant, and the state that goes with it. The two
 *  always travel together - SCHEDULED implies an instant, so a null one is only ever OFF. */
function noWrapUpYet(): Session {
  return aSession({
    wrapUpReleaseAt: null,
    wrapUpStatus: {
      state: "OFF",
      recipientCount: 3,
      sent: 0,
      couldNotReach: 0,
    },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mockRelease();
});

describe("WrapUpChip - the wrap-up's own words on the shared menu", () => {
  // The menu is shared with the reminder and takes its nouns as a constant, so crossing the
  // two constants would leave a working menu quietly saying the wrong thing. These are the
  // wrap-up's, pinned so a second message type cannot change them by accident.
  it("names the wrap-up in its note, its suppression hint and its schedule dialog", async () => {
    const user = await openMenu();

    expect(screen.getByText("When does the wrap-up go out")).toBeVisible();
    expect(screen.getByText("This session gets no wrap-up")).toBeVisible();

    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );

    expect(
      screen.getByRole("dialog", { name: "Schedule the wrap-up" }),
    ).toBeInTheDocument();
  });
});

describe("WrapUpChip - the current answer is ticked", () => {
  it("ticks the schedule option when a release is currently set - native aria-checked, not just a glyph", async () => {
    await openMenu();

    expect(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    ).toHaveAttribute("aria-checked", "true");
    expect(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    ).toHaveAttribute("aria-checked", "false");
    expect(
      screen.getByRole("menuitemradio", { name: /send it now/i }),
    ).toHaveAttribute("aria-checked", "false");
  });

  it("ticks 'Do not send it' and shows a generic 'Pick a time' label when suppressed", async () => {
    await openMenu(noWrapUpYet());

    expect(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    ).toHaveAttribute("aria-checked", "true");
    expect(
      screen.getByRole("menuitemradio", { name: /^pick a time/i }),
    ).toHaveAttribute("aria-checked", "false");
  });
});

describe("WrapUpChip - Send it now / Do not send it", () => {
  it("sends the wrap-up now with an explicit instant close to the moment of the click", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /send it now/i }),
    );

    expect(releaseMutate).toHaveBeenCalledTimes(1);
    const [call] = releaseMutate.mock.calls[0] as [
      { sessionId: number; input: { releaseAt: string } },
      unknown,
    ];
    expect(call.sessionId).toBe(7);
    expect(Date.now() - new Date(call.input.releaseAt).getTime()).toBeLessThan(
      5_000,
    );
  });

  it("suppresses the wrap-up permanently with an explicit null, not an omitted key", async () => {
    const user = await openMenu(
      aSession({ wrapUpReleaseAt: "2026-03-12T09:00:00Z" }),
    );

    await user.click(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    );

    expect(releaseMutate).toHaveBeenCalledWith(
      { sessionId: 7, input: { releaseAt: null } },
      expect.objectContaining({ onError: expect.any(Function) as unknown }),
    );
  });

  it("disables 'Do not send it' when it is already the current answer", async () => {
    await openMenu(noWrapUpYet());

    expect(
      screen.getByRole("menuitemradio", { name: /do not send it/i }),
    ).toHaveAttribute("aria-disabled", "true");
  });
});

describe("WrapUpChip - scheduling any instant", () => {
  it("opens a dialog pre-filled with the current release instant, in the viewer's own zone", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    // 2026-03-12T09:00Z read on the viewer's own clock (PDT, UTC-7) is 02:00 that morning.
    expect(screen.getByRole("spinbutton", { name: /year/i })).toHaveAttribute(
      "aria-valuenow",
      "2026",
    );
    expect(screen.getByRole("spinbutton", { name: /month/i })).toHaveAttribute(
      "aria-valuenow",
      "3",
    );
    expect(screen.getByRole("spinbutton", { name: /day/i })).toHaveAttribute(
      "aria-valuenow",
      "12",
    );
    expect(screen.getByRole("spinbutton", { name: /hour/i })).toHaveAttribute(
      "aria-valuenow",
      "2",
    );
    expect(
      screen.getByRole("spinbutton", { name: /AM\/PM/i }),
    ).toHaveTextContent("AM");
  });

  it("sends the exact UTC instant for the picked local wall-clock time", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );
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

  it("does not mutate when the organizer cancels out of the dialog", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /pick a different/i }),
    );
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(releaseMutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("pre-fills empty and disables Confirm when there is no current schedule", async () => {
    const user = await openMenu(noWrapUpYet());

    await user.click(
      screen.getByRole("menuitemradio", { name: /^pick a time/i }),
    );

    expect(
      screen.getByRole("spinbutton", { name: /year/i }),
    ).not.toHaveAttribute("aria-valuenow");
    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });

  it("resets a stale error from a previous attempt before reopening the dialog", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );

    expect(releaseReset).toHaveBeenCalled();
  });
});

describe("WrapUpChip - a failed mutation surfaces an error, never silently", () => {
  it("notifies on a failed 'Send it now'", async () => {
    const user = await openMenu();

    await user.click(
      screen.getByRole("menuitemradio", { name: /send it now/i }),
    );
    const [, options] = releaseMutate.mock.calls[0] as [
      unknown,
      { onError: (error: unknown) => void },
    ];
    options.onError(new ApiError(409, "x", "SESSION_NOT_HELD"));

    expect(notifyError).toHaveBeenCalledWith(
      "A recording or note can only be added once the session is held.",
    );
  });

  it("notifies on a failed schedule confirmation, and leaves the dialog open", async () => {
    const user = await openMenu();
    await user.click(
      screen.getByRole("menuitemradio", { name: /2:00 AM PDT/i }),
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    const [, options] = releaseMutate.mock.calls[0] as [
      unknown,
      { onError: (error: unknown) => void },
    ];
    options.onError(new ApiError(409, "x", "SESSION_NOT_HELD"));

    expect(notifyError).toHaveBeenCalledWith(
      "A recording or note can only be added once the session is held.",
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
