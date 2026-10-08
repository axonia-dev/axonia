import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCloseSession } from "@/features/events/api/use-session";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SessionClosePanel } from "@/features/events/ui/SessionClosePanel";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/events/api/use-session", () => ({
  useCloseSession: vi.fn(),
}));

const closeMutate = vi.fn();
const closeReset = vi.fn();

function aSession(overrides: Partial<Session> = {}): Session {
  return {
    id: 9,
    seriesId: 1,
    communityId: 1,
    status: "SCHEDULED",
    title: null,
    occurrenceDate: null,
    scheduledAt: "2099-01-01T18:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: null,
    attendeeCount: 0,
    notes: null,
    recordingUrl: null,
    place: EMPTY_PLACE,
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    sections: [],
    createdAt: "x",
    updatedAt: "x",
    closedAt: null,
    wrapUpReleaseAt: null,
    wrapUpStatus: null,
    upcomingReleaseAt: null,
    upcomingStatus: null,
    ...overrides,
  };
}

function renderPanel(session: Session, canManage = true) {
  return render(
    <SessionClosePanel
      slug="backend-guild"
      session={session}
      canManage={canManage}
    />,
  );
}

function mockClose(overrides: { error?: unknown; isPending?: boolean } = {}) {
  vi.mocked(useCloseSession).mockReturnValue({
    mutate: closeMutate,
    reset: closeReset,
    isPending: false,
    error: null,
    ...overrides,
  } as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockClose();
});

describe("SessionClosePanel - dispatch", () => {
  /**
   * The panel asks one question and nothing else. Its wrap-up half is gone: a release is stated,
   * answered and previewed in the facts strip's own chip, so a panel that also carried one would
   * put the same fact on the page twice - which is exactly what this change removed.
   */
  it("states nothing once the session is held, having only ever had a question to ask", () => {
    // A wrap-up state the deleted card would certainly have rendered: without it a held session
    // draws nothing whatever the panel does, and this would pass against the old code too.
    const { container } = renderPanel(
      aSession({
        status: "HELD",
        wrapUpReleaseAt: "2099-01-02T18:00:00Z",
        wrapUpStatus: {
          state: "SCHEDULED",
          recipientCount: 4,
          sent: 0,
          couldNotReach: 0,
        },
      }),
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("does not offer the close question on a future session", () => {
    renderPanel(
      aSession({ status: "SCHEDULED", scheduledAt: "2099-01-01T10:00:00Z" }),
    );

    expect(
      screen.queryByRole("button", { name: "It happened" }),
    ).not.toBeInTheDocument();
  });

  it("shows nothing to a non-organizer", () => {
    renderPanel(
      aSession({ status: "SCHEDULED", scheduledAt: "2026-03-09T10:00:00Z" }),
      false,
    );

    expect(
      screen.queryByRole("button", { name: "It happened" }),
    ).not.toBeInTheDocument();
  });

  it("shows nothing for a cancelled session", () => {
    renderPanel(aSession({ status: "CANCELLED" }));

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("SessionClosePanel - asking the question", () => {
  it("asks whether a past session happened", async () => {
    renderPanel(
      aSession({ status: "SCHEDULED", scheduledAt: "2026-03-09T10:00:00Z" }),
    );

    expect(
      await screen.findByRole("button", { name: "It happened" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "It did not" }),
    ).toBeInTheDocument();
  });

  it("closes the session as held when the organizer confirms it happened, with no confirmation step", async () => {
    const user = userEvent.setup({ delay: null });
    renderPanel(
      aSession({
        id: 42,
        status: "SCHEDULED",
        scheduledAt: "2026-03-09T10:00:00Z",
      }),
    );

    await user.click(screen.getByRole("button", { name: "It happened" }));

    expect(closeMutate).toHaveBeenCalledWith(
      { sessionId: 42, input: { held: true } },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("surfaces a failed close through eventsErrorMessage", () => {
    mockClose({ error: new ApiError(409, "x", "SESSION_ALREADY_CLOSED") });
    renderPanel(
      aSession({ status: "SCHEDULED", scheduledAt: "2026-03-09T10:00:00Z" }),
    );

    expect(screen.getByRole("alert")).toHaveTextContent(/already been closed/i);
  });
});

describe("SessionClosePanel - the 'it did not happen' consequence", () => {
  function pastSchedule() {
    return aSession({
      id: 42,
      status: "SCHEDULED",
      scheduledAt: "2026-03-09T10:00:00Z",
    });
  }

  it("does not cancel the session on the first click - it asks for confirmation first", async () => {
    const user = userEvent.setup({ delay: null });
    renderPanel(pastSchedule());

    await user.click(screen.getByRole("button", { name: "It did not" }));

    expect(closeMutate).not.toHaveBeenCalled();
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(/cancelled/i);
    expect(dialog).toHaveTextContent(/return to the backlog/i);
  });

  it("cancels the session only once the organizer confirms", async () => {
    const user = userEvent.setup({ delay: null });
    renderPanel(pastSchedule());

    await user.click(screen.getByRole("button", { name: "It did not" }));
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(closeMutate).toHaveBeenCalledWith(
      { sessionId: 42, input: { held: false } },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
  });

  it("dismisses without mutating when the organizer backs out", async () => {
    const user = userEvent.setup({ delay: null });
    renderPanel(pastSchedule());

    await user.click(screen.getByRole("button", { name: "It did not" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(closeMutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("resets a stale error from a previous attempt before reopening the dialog", async () => {
    const user = userEvent.setup({ delay: null });
    renderPanel(pastSchedule());

    await user.click(screen.getByRole("button", { name: "It did not" }));

    expect(closeReset).toHaveBeenCalled();
  });

  it("never shows the failure message in two places at once while the dialog is open", async () => {
    mockClose({ error: new ApiError(409, "x", "SESSION_ALREADY_CLOSED") });
    const user = userEvent.setup({ delay: null });
    renderPanel(pastSchedule());

    await user.click(screen.getByRole("button", { name: "It did not" }));

    expect(screen.getAllByRole("alert")).toHaveLength(1);
  });
});
