import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMessagePreview } from "@/features/events/api/use-session";
import type { ReleaseState } from "@/features/events/domain/session";
import type { ReleaseAnswerSet } from "@/features/events/ui/release-answers";
import type { SessionRelease } from "@/features/events/ui/release-chip";
import { releaseChip } from "@/features/events/ui/release-chip";
import { upcomingCopy, wrapUpCopy } from "@/features/events/ui/release-copy";
import {
  REMINDER_PREVIEW,
  WRAP_UP_PREVIEW,
} from "@/features/events/ui/release-preview-copy";
import { SessionReleaseChip } from "@/features/events/ui/SessionReleaseChip";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-session", () => ({
  useMessagePreview: vi.fn(),
}));

const RELEASE_AT = "2026-03-12T09:00:00Z";

const scheduleAt =
  vi.fn<(releaseAt: string, onScheduled: () => void) => void>();

function actions() {
  return {
    isPending: false,
    reset: vi.fn(),
    sendNow: vi.fn(),
    scheduleAt,
    suppress: vi.fn(),
  };
}

/** The reminder as the strip reads it, at whichever state the case under test needs. */
function reminder(state: ReleaseState): SessionRelease {
  const session = {
    ...aScheduledSession(),
    upcomingReleaseAt: state === "OFF" ? null : RELEASE_AT,
  };
  const status = {
    state,
    recipientCount: 2,
    sent: state === "SENT" ? 2 : 0,
    couldNotReach: 0,
  };
  return {
    status,
    chip: releaseChip("Reminder", state, false),
    preview: REMINDER_PREVIEW,
    copy: upcomingCopy(session, status),
    kind: "reminder",
    answerable: state === "OFF" || state === "SCHEDULED",
  };
}

function answerSet(
  overrides: Partial<ReleaseAnswerSet> = {},
): ReleaseAnswerSet {
  return {
    releaseAt: RELEASE_AT,
    copy: {
      note: "When does the reminder go out",
      offHint: "This session gets no reminder",
      dialogTitle: "Schedule the reminder",
      fieldLabel: "Reminder release date and time",
    },
    actions: actions(),
    ...overrides,
  };
}

function renderChip(release: SessionRelease, answers = answerSet()) {
  return render(
    <SessionReleaseChip
      slug="backend-guild"
      sessionId={10}
      release={release}
      answers={answers}
    />,
  );
}

async function openPanel(name: RegExp) {
  await user.click(screen.getByRole("button", { name }));
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useMessagePreview).mockReturnValue({
    data: { subject: "What happened", html: "<p>notes</p>" },
    isLoading: false,
    isError: false,
    error: null,
  } as never);
});

describe("SessionReleaseChip", () => {
  /**
   * The whole point of folding the release into the chip: the sentence that used to sit in a
   * banner at the foot of the page is now one press from the fact itself. A chip that stated
   * only "Reminder scheduled" and never named the instant would have lost what the banner knew.
   */
  it("states the release in full inside the panel the chip opens", async () => {
    renderChip(reminder("SCHEDULED"));

    await openPanel(/Reminder scheduled/);

    expect(
      screen.getByText("The reminder goes out Thu, Mar 12, 2:00 AM PDT."),
    ).toBeVisible();
    expect(screen.getByText(/To 2 members/)).toBeVisible();
  });

  it("offers the three answers in the same panel, with the standing one picked", async () => {
    renderChip(reminder("SCHEDULED"));

    await openPanel(/Reminder scheduled/);

    expect(
      screen.getByRole("menuitemradio", { name: /Send it now/ }),
    ).not.toBeChecked();
    expect(screen.getByRole("menuitemradio", { name: /Mar 12/ })).toBeChecked();
    expect(
      screen.getByRole("menuitemradio", { name: /Do not send it/ }),
    ).not.toBeChecked();
  });

  it("sends the reminder now when that answer is picked", async () => {
    const answers = answerSet();
    renderChip(reminder("SCHEDULED"), answers);

    await openPanel(/Reminder scheduled/);
    await user.click(
      screen.getByRole("menuitemradio", { name: /Send it now/ }),
    );

    expect(answers.actions.sendNow).toHaveBeenCalledOnce();
  });

  it("opens the schedule dialog from the standing answer", async () => {
    renderChip(reminder("SCHEDULED"));

    await openPanel(/Reminder scheduled/);
    await user.click(screen.getByRole("menuitemradio", { name: /Mar 12/ }));

    expect(
      screen.getByRole("dialog", { name: "Schedule the reminder" }),
    ).toBeVisible();
  });

  /**
   * Only a confirmed success may close the picker. Closing on the click instead would report a
   * schedule that the server may still refuse, and take the organizer's pick down with it.
   */
  it("closes the schedule dialog only once the action reports success", async () => {
    renderChip(reminder("SCHEDULED"));

    await openPanel(/Reminder scheduled/);
    await user.click(screen.getByRole("menuitemradio", { name: /Mar 12/ }));
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    const [, onScheduled] = scheduleAt.mock.calls[0] as [string, () => void];
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    act(onScheduled);

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  /** The wrap-up has no upper bound on when it may go out - `answerSet` omits one - so the
   *  picker must not invent one. */
  it("says nothing about a bound for a message type that has none", async () => {
    renderChip(reminder("SCHEDULED"));

    await openPanel(/Reminder scheduled/);
    await user.click(screen.getByRole("menuitemradio", { name: /Mar 12/ }));

    expect(screen.queryByText(/has to land before/)).toBeNull();
  });

  /**
   * A settled release has nothing left to answer, so offering the three answers would invite an
   * organizer to change something already gone. It keeps its statement and its preview.
   */
  it("offers no answers once the release is settled", async () => {
    renderChip(reminder("SENT"));

    await openPanel(/Reminder sent/);

    expect(screen.queryByRole("menuitemradio")).toBeNull();
    expect(screen.getByText(/Delivered to 2 members/)).toBeVisible();
  });

  it("previews the message from the panel rather than on the chip itself", async () => {
    renderChip(reminder("SENT"));

    await openPanel(/Reminder sent/);
    await user.click(screen.getByRole("menuitem", { name: "Preview it" }));

    expect(
      screen.getByRole("dialog", { name: "Reminder preview" }),
    ).toBeVisible();
    expect(screen.getByTitle("Reminder email preview")).toBeInTheDocument();
  });

  it("holds the preview request until the preview is actually opened", async () => {
    renderChip(reminder("SENT"));

    await openPanel(/Reminder sent/);

    expect(useMessagePreview).toHaveBeenLastCalledWith(
      "backend-guild",
      10,
      "session_upcoming",
      false,
    );

    await user.click(screen.getByRole("menuitem", { name: "Preview it" }));

    expect(useMessagePreview).toHaveBeenLastCalledWith(
      "backend-guild",
      10,
      "session_upcoming",
      true,
    );
  });

  /** A suppressed message is answerable and has nothing to look at: the exact reverse of a sent
   *  one, which is why the panel asks `answerable` and `previewable` separately. */
  it("answers for a suppressed message without offering a preview of mail nothing will send", async () => {
    renderChip(reminder("OFF"), answerSet({ releaseAt: null }));

    await openPanel(/Reminder off/);

    expect(screen.getByText("No reminder for this session.")).toBeVisible();
    expect(
      screen.getByRole("menuitemradio", { name: /Do not send it/ }),
    ).toBeChecked();
    expect(screen.queryByRole("menuitem", { name: "Preview it" })).toBeNull();
  });

  /**
   * The preview's two titles are the last place the noun reaches a reader, and the only one
   * nothing else renders: crossing them would title the wrap-up's dialog "Reminder preview" and
   * have a screen reader announce "Reminder email preview", with every other test still green.
   */
  it("titles the wrap-up's preview after the wrap-up, not the reminder", async () => {
    const session = { ...aScheduledSession(), wrapUpReleaseAt: RELEASE_AT };
    const status = {
      state: "SENT" as const,
      recipientCount: 3,
      sent: 3,
      couldNotReach: 0,
    };
    renderChip({
      status,
      chip: releaseChip("Wrap-up", "SENT", false),
      preview: WRAP_UP_PREVIEW,
      copy: wrapUpCopy(session, status),
      kind: "wrapUp",
      answerable: false,
    });

    await openPanel(/Wrap-up sent/);
    await user.click(screen.getByRole("menuitem", { name: "Preview it" }));

    expect(
      screen.getByRole("dialog", { name: "Wrap-up preview" }),
    ).toBeVisible();
    expect(screen.getByTitle("Wrap-up email preview")).toBeInTheDocument();
  });
});
