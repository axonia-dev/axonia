import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRecordFollowUp } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { RecordingChip } from "@/features/events/ui/RecordingChip";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-session");
vi.mock("@/features/events/api/use-action-items", () => ({
  useActionItems: () => ({ data: undefined }),
}));

beforeEach(() => {
  vi.mocked(useRecordFollowUp).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
  } as unknown as ReturnType<typeof useRecordFollowUp>);
});

function held(overrides: Partial<Session> = {}) {
  return aScheduledSession({
    id: 10,
    status: "HELD",
    recordingUrl: null,
    notes: null,
    ...overrides,
  });
}

function renderChip(session = held(), canManage = false) {
  return render(
    <RecordingChip slug="guild" session={session} canManage={canManage} />,
  );
}

describe("RecordingChip", () => {
  it("links straight out to the recording, in one word", () => {
    renderChip(held({ recordingUrl: "https://meet.example/rec/10" }));

    const link = screen.getByRole("link", { name: "Recording" });
    expect(link).toHaveAttribute("href", "https://meet.example/rec/10");
  });

  /** A member cannot add one, so an absence they cannot act on is stated by saying nothing. */
  it("says nothing to a member when no recording was captured", () => {
    const { container } = renderChip();

    expect(container).toBeEmptyDOMElement();
  });

  it("invites an organizer to add the one that is missing", () => {
    renderChip(held(), true);

    expect(
      screen.getByRole("button", { name: /add a recording/i }),
    ).toBeInTheDocument();
  });

  it("opens the capture dialog from that invitation", () => {
    renderChip(held(), true);

    fireEvent.click(screen.getByRole("button", { name: /add a recording/i }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  /**
   * Once there is a recording the chip states it and nothing more: editing lives in the session's
   * actions menu, so an organizer is never offered two controls for one fact.
   */
  it("offers an organizer no second control once a recording exists", () => {
    renderChip(held({ recordingUrl: "https://meet.example/rec/10" }), true);

    expect(screen.getByRole("link", { name: "Recording" })).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  /** A session that was never held has no recording to state and none to ask for. */
  it("says nothing at all before the session is held", () => {
    const { container } = renderChip(
      aScheduledSession({ id: 10, status: "SCHEDULED" }),
      true,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
