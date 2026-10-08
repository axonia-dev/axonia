import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import { useRaiseActionItem } from "@/features/events/api/use-action-items";
import { useUpcomingSessions } from "@/features/events/api/use-sessions";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { CaptureActionPointDialog } from "@/features/events/ui/CaptureActionPointDialog";
import { useUsers } from "@/features/identity/api/use-users";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-action-items", () => ({
  useRaiseActionItem: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useUpcomingSessions: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseRaise = vi.mocked(useRaiseActionItem);
const mockUseUpcoming = vi.mocked(useUpcomingSessions);
const mockUseRosterByName = vi.mocked(useRosterByName);
const mockUseMemberSearch = vi.mocked(useMemberSearch);
const mockUseUsers = vi.mocked(useUsers);

const bob = { userId: "bob", displayName: "Bob Barker" };

function buildSession(id: number, scheduledAt: string): Session {
  return {
    id,
    seriesId: 5,
    communityId: 1,
    status: "SCHEDULED",
    title: null,
    occurrenceDate: scheduledAt.slice(0, 10),
    scheduledAt,
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
  };
}

let user: ReturnType<typeof userEvent.setup>;
let raise: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  raise = vi.fn();
  mockUseRaise.mockReturnValue({
    mutate: raise,
    isError: false,
    isPending: false,
  } as never);
  mockUseUpcoming.mockReturnValue({
    data: {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 100,
    },
  } as never);
  stubMemberSelectField(
    {
      useRosterByName: mockUseRosterByName,
      useMemberSearch: mockUseMemberSearch,
      useUsers: mockUseUsers,
    },
    [bob],
  );
});

/** Renders the dialog already open: every test drives the capture form itself, never the
 *  trigger that reveals it. `fireEvent` (not `userEvent`) opens it so the click resolves
 *  synchronously, with the form already on screen the instant this returns. */
function renderCaptureDialog() {
  render(<CaptureActionPointDialog slug="green-guild" sessionId={42} />);
  fireEvent.click(screen.getByRole("button", { name: /\+ action point/i }));
}

describe("CaptureActionPointDialog", () => {
  it("submits with a title and an assignee alone", async () => {
    renderCaptureDialog();

    await user.type(screen.getByLabelText("Title"), "Close the Jira ticket");
    await user.click(screen.getByRole("combobox", { name: "Assignee" }));
    await user.click(await screen.findByRole("option", { name: "Bob Barker" }));
    await user.click(screen.getByRole("button", { name: "Capture" }));

    expect(raise).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Close the Jira ticket",
        assignee: "bob",
        checkpointSessionId: null,
        originSessionId: 42,
      }),
      expect.anything(),
    );
  }, 15_000);

  it("defaults the checkpoint to the next session", async () => {
    renderCaptureDialog();

    expect(
      await screen.findByRole("button", { name: /Next session/ }),
    ).toBeInTheDocument();
  });

  it("shows the resolved date of the next session when the community has one", async () => {
    mockUseUpcoming.mockReturnValue({
      data: {
        content: [buildSession(101, "2026-10-03T18:00:00Z")],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 100,
      },
    } as never);
    renderCaptureDialog();

    expect(
      await screen.findByRole("button", {
        name: /Next session \(Sat, Oct 3\)/,
      }),
    ).toBeInTheDocument();
  });

  /**
   * A session's date is read through its series' zone, the rule `SessionCard` states and every
   * other session date follows. This suite's viewer zone is west of UTC, so a Paris session
   * meeting just after midnight would be offered here as Oct 3 while the session card itself
   * calls it Oct 4.
   */
  it("names the next session on the community's clock, not the viewer's", async () => {
    mockUseUpcoming.mockReturnValue({
      data: {
        content: [buildSession(101, "2026-10-04T02:00:00Z")],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 100,
      },
    } as never);
    renderCaptureDialog();

    expect(
      await screen.findByRole("button", {
        name: /Next session \(Sun, Oct 4\)/,
      }),
    ).toBeInTheDocument();
  });

  it("offers a real upcoming session as an alternative checkpoint", async () => {
    mockUseUpcoming.mockReturnValue({
      data: {
        content: [buildSession(101, "2026-10-03T18:00:00Z")],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 100,
      },
    } as never);
    renderCaptureDialog();

    await user.type(screen.getByLabelText("Title"), "Review the RFC");
    await user.click(screen.getByRole("button", { name: /Next session/ }));
    await user.click(await screen.findByRole("option", { name: "Sat, Oct 3" }));
    await user.click(screen.getByRole("button", { name: "Capture" }));

    expect(raise).toHaveBeenCalledWith(
      expect.objectContaining({ checkpointSessionId: 101 }),
      expect.anything(),
    );
  }, 15_000);

  it("keeps description and due date out of the way until asked for", async () => {
    renderCaptureDialog();

    expect(
      screen.queryByRole("group", { name: "Due date" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Description")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "More" }));

    expect(screen.getByRole("group", { name: "Due date" })).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
  });

  it("disables submit until a title is entered", () => {
    renderCaptureDialog();

    expect(screen.getByRole("button", { name: "Capture" })).toBeDisabled();
  });

  it("closes the dialog after a successful capture", async () => {
    raise.mockImplementation(
      (_input: unknown, { onSuccess }: { onSuccess: () => void }) =>
        onSuccess(),
    );
    renderCaptureDialog();

    await user.type(screen.getByLabelText("Title"), "Follow up");
    await user.click(screen.getByRole("button", { name: "Capture" }));

    expect(
      screen.queryByText("Capture an action point"),
    ).not.toBeInTheDocument();
  }, 15_000);

  it("closes the dialog on cancel", async () => {
    renderCaptureDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(
      screen.queryByText("Capture an action point"),
    ).not.toBeInTheDocument();
  });
});
