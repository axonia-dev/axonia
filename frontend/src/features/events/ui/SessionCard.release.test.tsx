import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useSetUpcomingRelease,
  useSetWrapUpRelease,
} from "@/features/events/api/use-session";
import type { ReleaseState, Session } from "@/features/events/domain/session";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { aScheduledSession } from "@/test/session-fixture";

// Split out of SessionCard.test.tsx, which was over eslint's max-lines: what the card says about
// the session's own outgoing mail, and who it says it to.
vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: () => null,
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => null,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => null,
}));
vi.mock("@/features/events/ui/ReleasePreviewDialog", () => ({
  ReleasePreviewDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div role="dialog">the preview</div> : null,
}));

const upcomingMutate = vi.fn();
const wrapUpMutate = vi.fn();

vi.mock("@/features/events/api/use-session", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useSetUpcomingRelease: vi.fn(),
  useSetWrapUpRelease: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSetUpcomingRelease).mockReturnValue({
    mutate: upcomingMutate,
    reset: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useSetWrapUpRelease).mockReturnValue({
    mutate: wrapUpMutate,
    reset: vi.fn(),
    isPending: false,
  } as never);
});

function status(state: ReleaseState) {
  return { state, recipientCount: 5, sent: 5, couldNotReach: 0 };
}

function renderCard(session: Session, canManageMessages = false) {
  return render(
    <SessionCard
      slug="backend-guild"
      session={session}
      phase={sessionPhase(session)}
      statusChip={sessionStatusChip(session)}
      canManageMessages={canManageMessages}
    />,
  );
}

const heldWithWrapUpSent: Session = aScheduledSession({
  id: 9,
  scheduledAt: "2099-01-01T18:00:00Z",
  status: "HELD",
  wrapUpStatus: status("SENT"),
});

describe("SessionCard - the session's own mail", () => {
  /**
   * The strip's own chip is the only place the release is stated, answered or previewed: the two
   * cards that used to carry a copy of it are gone.
   */
  it("opens the wrap-up preview from the panel behind the facts strip's own chip", async () => {
    const user = userEvent.setup({ delay: null });
    renderCard(heldWithWrapUpSent, true);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Wrap-up sent/ }));
    await user.click(screen.getByRole("menuitem", { name: "Preview it" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  /**
   * The two message types post deliberately different payloads, so the panel has to be handed the
   * answers of the message its own phase put in play. Crossing the wiring would have a held
   * session's wrap-up answered by the reminder's endpoint with every chip label still correct.
   */
  it("answers a held session's wrap-up through the wrap-up's own endpoint", async () => {
    const user = userEvent.setup({ delay: null });
    renderCard(
      aScheduledSession({
        id: 9,
        status: "HELD",
        wrapUpStatus: status("SCHEDULED"),
        wrapUpReleaseAt: "2099-01-01T18:00:00Z",
      }),
      true,
    );

    await user.click(screen.getByRole("button", { name: /Wrap-up scheduled/ }));
    await user.click(
      screen.getByRole("menuitemradio", { name: /Send it now/ }),
    );

    expect(wrapUpMutate).toHaveBeenCalledOnce();
    expect(upcomingMutate).not.toHaveBeenCalled();
  });

  it("answers an upcoming session's reminder through the reminder's own endpoint", async () => {
    const user = userEvent.setup({ delay: null });
    renderCard(
      aScheduledSession({
        scheduledAt: "2099-01-01T18:00:00Z",
        upcomingReleaseAt: "2098-01-01T18:00:00Z",
        upcomingStatus: status("SCHEDULED"),
      }),
      true,
    );

    await user.click(
      screen.getByRole("button", { name: /Reminder scheduled/ }),
    );
    await user.click(
      screen.getByRole("menuitemradio", { name: /Send it now/ }),
    );

    expect(upcomingMutate).toHaveBeenCalledOnce();
    expect(wrapUpMutate).not.toHaveBeenCalled();
  });

  it("states nothing about the mail to a reader who may not manage it", () => {
    renderCard(heldWithWrapUpSent);

    expect(screen.queryByText(/Wrap-up/)).not.toBeInTheDocument();
  });

  // The same badge in the same place, whichever release the phase puts in play - which is the
  // whole point: a held page and one still ahead read alike at the top.
  it("states the reminder on a session that is still ahead", () => {
    renderCard(
      aScheduledSession({
        scheduledAt: "2099-01-01T18:00:00Z",
        upcomingStatus: status("SCHEDULED"),
      }),
      true,
    );

    expect(
      screen.getByRole("button", { name: /Reminder scheduled/ }),
    ).toBeInTheDocument();
  });
});
