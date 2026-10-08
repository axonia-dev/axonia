import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type { EditActionItemVariables } from "@/features/events/api/use-action-items";
import {
  useEditActionItem,
  useSettleActionItem,
} from "@/features/events/api/use-action-items";
import { useUpcomingSessions } from "@/features/events/api/use-sessions";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { EditActionItemDialog } from "@/features/events/ui/EditActionItemDialog";
import { useUsers } from "@/features/identity/api/use-users";
import { anActionItem } from "@/test/action-item-fixture";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-action-items", () => ({
  useEditActionItem: vi.fn(),
  useSettleActionItem: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useUpcomingSessions: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const bob = { userId: "bob", displayName: "Bob Barker" };

const session: Session = {
  id: 101,
  seriesId: 5,
  communityId: 1,
  status: "SCHEDULED",
  title: null,
  occurrenceDate: "2026-10-03",
  scheduledAt: "2026-10-03T18:00:00Z",
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

let user: ReturnType<typeof userEvent.setup>;
let edit: ReturnType<typeof vi.fn>;
let settle: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  edit = vi.fn((_vars: unknown, options?: { onSuccess?: () => void }) =>
    options?.onSuccess?.(),
  );
  vi.mocked(useEditActionItem).mockReturnValue({
    mutate: edit,
    isPending: false,
    isError: false,
  } as never);
  settle = vi.fn();
  vi.mocked(useSettleActionItem).mockReturnValue({
    mutate: settle,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUpcomingSessions).mockReturnValue({
    data: { content: [session] },
  } as never);
  stubMemberSelectField(
    {
      useRosterByName: vi.mocked(useRosterByName),
      useMemberSearch: vi.mocked(useMemberSearch),
      useUsers: vi.mocked(useUsers),
    },
    [bob],
  );
});

const item = anActionItem({
  id: 77,
  title: "Renew the certificate",
  assigneeUserId: null,
  assigneeDisplayName: null,
});

function saved(): EditActionItemVariables {
  return edit.mock.calls[0]?.[0] as EditActionItemVariables;
}

function renderDialog(overrides = {}) {
  render(
    <EditActionItemDialog
      slug="backend-guild"
      item={{ ...item, ...overrides }}
      isOpen={true}
      onOpenChange={vi.fn()}
    />,
  );
}

describe("EditActionItemDialog", () => {
  it("saves the corrected title against the item's own id", async () => {
    renderDialog();

    await user.clear(screen.getByLabelText("Title"));
    await user.type(screen.getByLabelText("Title"), "Renew the TLS cert");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(saved().id).toBe(77);
    expect(saved().input.title).toBe("Renew the TLS cert");
  }, 15_000);

  /** The escape hatch: carry returns an item to "next session", and this is the only
   *  surface that can point it at a chosen one instead. */
  it("retargets the item to a chosen session", async () => {
    renderDialog();

    await user.click(screen.getByRole("button", { name: /Next session/ }));
    await user.click(await screen.findByRole("option", { name: "Sat, Oct 3" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(saved().input.checkpointSessionId).toBe(101);
  }, 15_000);

  it("opens on the pinned session an item is already answered at", () => {
    renderDialog({
      checkpoint: {
        sessionId: 101,
        scheduledAt: "2026-10-03T18:00:00Z",
        pinned: true,
      },
    });

    expect(
      screen.getByRole("button", { name: /Sat, Oct 3/ }),
    ).toBeInTheDocument();
  });

  it("saves the assignee the item is dispatched to", async () => {
    renderDialog();

    await user.click(screen.getByRole("combobox", { name: "Assignee" }));
    await user.click(await screen.findByRole("option", { name: "Bob Barker" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(saved().input.assignee).toBe("bob");
  }, 15_000);

  /** A correction that hid what it is about to save would quietly keep a wrong due date. */
  it("reveals a description and a due date the item already carries", () => {
    renderDialog({ description: "The wildcard one", dueDate: "2026-09-30" });

    expect(screen.getByLabelText("Description")).toHaveValue(
      "The wildcard one",
    );
    expect(screen.getByRole("group", { name: "Due date" })).toBeInTheDocument();
  });

  it("keeps description and due date out of the way on an item carrying neither", () => {
    renderDialog();

    expect(screen.queryByLabelText("Description")).not.toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Due date" })).toBeNull();
  });

  /** The dialog is only reachable on an open item, because the backend refuses an edit to a
   *  settled row. So the status control's job is to let one pass through the dialog both
   *  correct an item and settle it, in that order. */
  it("saves the correction, then settles the item", async () => {
    renderDialog(anActionItem({ id: 7, title: "Ship it", canEdit: true }));

    await user.click(screen.getByRole("radio", { name: "Done" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(edit).toHaveBeenCalled();
    expect(settle).toHaveBeenCalledWith("DONE", expect.anything());
  }, 15_000);

  it("settles nothing when the status was not touched", async () => {
    renderDialog(anActionItem({ id: 7, title: "Ship it", canEdit: true }));

    await user.clear(screen.getByLabelText("Title"));
    await user.type(screen.getByLabelText("Title"), "Ship it now");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(edit).toHaveBeenCalled();
    expect(settle).not.toHaveBeenCalled();
  }, 15_000);
});

describe("EditActionItemDialog (upcoming sessions not loaded)", () => {
  const pinned = {
    checkpoint: {
      ...item.checkpoint,
      sessionId: 101,
      scheduledAt: "2026-10-03T18:00:00Z",
      pinned: true,
    },
  };

  it("says the sessions are loading, rather than naming no checkpoint", () => {
    // given
    vi.mocked(useUpcomingSessions).mockReturnValue({
      data: undefined,
      errorUpdateCount: 0,
    } as never);

    // when
    renderDialog(pinned);

    // then
    const checkpoint = screen.getByRole("button", { name: /answered at/i });
    expect(checkpoint).toHaveTextContent("Loading…");
    expect(checkpoint).toBeDisabled();
  });

  it("says the sessions could not load, and asks again on Try again", async () => {
    // given
    const refetch = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useUpcomingSessions).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    } as never);
    renderDialog(pinned);

    // when
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the upcoming sessions.",
    );
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("hands the focus to the checkpoint once Try again brings the sessions", async () => {
    // given
    vi.mocked(useUpcomingSessions).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const dialog = () => (
      <EditActionItemDialog
        slug="backend-guild"
        item={{ ...item, ...pinned }}
        isOpen={true}
        onOpenChange={vi.fn()}
      />
    );
    const { rerender } = render(dialog());
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    vi.mocked(useUpcomingSessions).mockReturnValue({
      data: { content: [session] },
      errorUpdateCount: 1,
    } as never);
    rerender(dialog());

    // then
    expect(screen.getByRole("button", { name: /answered at/i })).toHaveFocus();
  });
});
