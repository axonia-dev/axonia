import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRecordFollowUp } from "@/features/events/api/use-session";
import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
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
vi.mock("@/features/events/api/use-session");
vi.mock("@/features/events/api/use-action-items", () => ({
  useActionItems: () => ({ data: undefined }),
}));

let user: ReturnType<typeof userEvent.setup>;

const held: Session = aScheduledSession({
  status: "HELD",
  recordingUrl: "https://videos.example/s1",
  notes: "The p95 numbers landed.",
});

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.mocked(useRescheduleSession).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    reset: vi.fn(),
  } as never);
  vi.mocked(useSkipOccurrence).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useRecordFollowUp).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
  } as unknown as ReturnType<typeof useRecordFollowUp>);
});

function renderActions(session: Session, canManage: boolean) {
  render(
    <SessionActions
      slug="g"
      session={session}
      canEditStructure={false}
      canManage={canManage}
    />,
  );
}

describe("SessionActions, recording and note", () => {
  it("offers an organizer the edit, beside everything else done to a held session", async () => {
    renderActions(held, true);

    await user.click(screen.getByRole("button", { name: /session actions/i }));
    expect(
      screen.getByRole("menuitem", { name: /recording and note/i }),
    ).toBeInTheDocument();
  });

  it("opens the dialog on it", async () => {
    renderActions(held, true);

    await user.click(screen.getByRole("button", { name: /session actions/i }));
    await user.click(
      screen.getByRole("menuitem", { name: /recording and note/i }),
    );
    expect(
      screen.getByRole("dialog", { name: /after the session/i }),
    ).toBeInTheDocument();
  });

  it("says nothing to a member", () => {
    const { container } = render(
      <SessionActions
        slug="g"
        session={held}
        canEditStructure={false}
        canManage={false}
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  /** Nothing to record until the session has actually run. */
  it("withholds it while the session is still ahead", async () => {
    renderActions(aScheduledSession(), true);

    await user.click(screen.getByRole("button", { name: /session actions/i }));
    expect(
      screen.queryByRole("menuitem", { name: /recording and note/i }),
    ).not.toBeInTheDocument();
  });
});
