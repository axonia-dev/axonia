import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import {
  useAddPresenter,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";
import {
  usePresentSlot,
  useRemoveSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";
import type { Section } from "@/features/events/domain/section";
import type { Slot } from "@/features/events/domain/slot";
import { SlotRow } from "@/features/events/ui/SlotRow";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { ApiError } from "@/lib/api-client";
import { aScheduledSession } from "@/test/session-fixture";

// SlotRow renders the real PresenterStack, so its own hooks are mocked here the
// same way PresenterStack.test.tsx mocks them: no network, no QueryClient needed.
// Split out of the resource-block/capture-mode cases (SlotRow.resources.test.tsx), which were
// pushing eslint's max-lines: this file keeps title/byline/duration/manager-action coverage.
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  usePresentSlot: vi.fn(),
  useWithdrawSlot: vi.fn(),
  useRemoveSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useAddPresenter: vi.fn(),
  useRemovePresenter: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "dev-user" }));
vi.mock("@/features/knowledge/ui/SlotResources", () => ({
  SlotResources: () => <div>slot-resources</div>,
}));
vi.mock("@/features/knowledge/ui/AddResourceButton", () => ({
  AddResourceButton: () => <button type="button">+ Add resource</button>,
}));
vi.mock("@/features/knowledge/api/use-session-resources", () => ({
  useSessionResources: () => ({ data: [] }),
}));
// Render the dialog as a marker so opening it is observable without its full tree.
vi.mock("@/features/events/ui/SlotDialog", () => ({
  SlotDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div role="dialog">slot dialog</div> : null,
}));

const mockUsePresent = vi.mocked(usePresentSlot);
const mockUseWithdraw = vi.mocked(useWithdrawSlot);
const mockUseRemove = vi.mocked(useRemoveSlot);
const mockUseAddPresenter = vi.mocked(useAddPresenter);
const mockUseRemovePresenter = vi.mocked(useRemovePresenter);
const mockUseMembers = vi.mocked(useMembers);
const mockUseUsers = vi.mocked(useUsers);

const users: Record<string, User> = {
  "uuid-bob": {
    id: "uuid-bob",
    displayName: "Bob Stone",
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
};

const session = aScheduledSession({ id: 9 });
const section = { id: 3 } as Section;
const openSlot: Slot = {
  id: 7,
  sectionId: 3,
  position: 0,
  presenterUserIds: [],
  topicId: null,
  title: null,
  notes: null,
  durationMinutes: 20,
  tags: [],
};
const filledSlot: Slot = {
  ...openSlot,
  presenterUserIds: ["dev-user"],
  title: "Pairing on hexagons",
};

function renderRow(
  slot: Slot,
  extra: Partial<React.ComponentProps<typeof SlotRow>> = {},
) {
  return render(
    <SlotRow
      slug="backend-guild"
      session={session}
      section={section}
      slot={slot}
      users={users}
      {...extra}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePresent.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseWithdraw.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseRemove.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseAddPresenter.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseRemovePresenter.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseMembers.mockReturnValue({
    data: {
      content: [],
      totalElements: 0,
      totalPages: 1,
      number: 0,
      size: 100,
    },
  } as never);
  mockUseUsers.mockReturnValue({ data: {} } as never);
});

describe("SlotRow", () => {
  it("shows the slot's own title even with no presenters", () => {
    renderRow({ ...openSlot, title: "Spring Boot 4: what changes for us" });
    expect(
      screen.getByText("Spring Boot 4: what changes for us"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Open slot")).not.toBeInTheDocument();
  });

  it("falls back to 'Open slot' only when the slot has no title", () => {
    renderRow(openSlot);
    expect(screen.getByText("Open slot")).toBeInTheDocument();
  });

  it("resolves the presenter to a name and never shows the raw id", () => {
    renderRow({ ...filledSlot, presenterUserIds: ["uuid-bob"] });
    expect(screen.getByText("Pairing on hexagons")).toBeInTheDocument();
    expect(screen.getByText("Bob Stone")).toBeInTheDocument();
    expect(screen.queryByText("uuid-bob")).not.toBeInTheDocument();
  });

  it("shows a byline crediting the presenters", () => {
    renderRow({ ...filledSlot, presenterUserIds: ["uuid-bob"] });
    expect(screen.getByText("by Bob Stone")).toBeInTheDocument();
  });

  it("omits the byline row for an open slot", () => {
    renderRow(openSlot);
    expect(screen.queryByText(/^by /)).not.toBeInTheDocument();
  });

  it("shows the slot duration", () => {
    renderRow(openSlot);
    expect(screen.getByText("20 min")).toBeInTheDocument();
  });

  // The scheduled action item's owner line (present/deactivated/erased) is fully covered
  // in SlotRow.owner.test.tsx, including the present-owner case - not duplicated here.

  it("hides slot actions and the presenter stack's add button in readonly mode", () => {
    renderRow(filledSlot, { mode: "readonly" });
    expect(
      screen.queryByRole("button", { name: /edit slot|remove slot/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Add presenter")).not.toBeInTheDocument();
  });

  it("tags a slot that carries a backlog topic", () => {
    renderRow({ ...openSlot, topicId: 99 });
    expect(screen.getByText(/from backlog/i)).toBeInTheDocument();
  });

  it("shows a backfilled talk's tags on a filled, note-less, topic-less slot", () => {
    renderRow({ ...filledSlot, tags: ["kafka", "streaming"] });
    expect(screen.getByText("#kafka")).toBeInTheDocument();
    expect(screen.getByText("#streaming")).toBeInTheDocument();
  });

  it("shows an error message when removing the slot fails", () => {
    mockUseRemove.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: new ApiError(409, "x", "SLOT_NOT_FOUND"),
    } as never);
    renderRow(filledSlot);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  describe("manager actions", () => {
    it("opens the edit dialog from the edit action (presenter)", async () => {
      renderRow(filledSlot);
      await user.click(screen.getByRole("button", { name: /edit slot/i }));
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    it("opens the edit dialog from the edit action (schedule manager)", async () => {
      const otherSlot = { ...filledSlot, presenterUserIds: ["other-user"] };
      renderRow(otherSlot, { canManageSchedule: true });
      await user.click(screen.getByRole("button", { name: /edit slot/i }));
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    it("keeps structural affordances (edit/remove) available in edit mode", () => {
      renderRow(filledSlot, { mode: "edit", canManageSchedule: true });
      expect(
        screen.getByRole("button", { name: /edit slot/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /remove slot/i }),
      ).toBeInTheDocument();
    });

    it("removes the slot only after confirming in the popover", async () => {
      const mutate = vi.fn();
      mockUseRemove.mockReturnValue({ mutate, isPending: false } as never);
      renderRow(filledSlot);

      // The trash button opens a confirmation - it must not delete on its own.
      await user.click(screen.getByRole("button", { name: /remove slot/i }));
      expect(mutate).not.toHaveBeenCalled();

      const confirm = screen.getByRole("dialog", { name: /confirm remove/i });
      await user.click(
        within(confirm).getByRole("button", { name: /^remove$/i }),
      );
      expect(mutate).toHaveBeenCalledOnce();
    });

    it("keeps the slot when the remove confirmation is cancelled", async () => {
      const mutate = vi.fn();
      mockUseRemove.mockReturnValue({ mutate, isPending: false } as never);
      renderRow(filledSlot);

      await user.click(screen.getByRole("button", { name: /remove slot/i }));
      const confirm = screen.getByRole("dialog", { name: /confirm remove/i });
      await user.click(
        within(confirm).getByRole("button", { name: /cancel/i }),
      );
      expect(mutate).not.toHaveBeenCalled();
      expect(
        screen.queryByRole("dialog", { name: /confirm remove/i }),
      ).not.toBeInTheDocument();
    });

    it("hides edit and remove from a plain member (not presenter, not manager)", () => {
      const otherSlot = { ...filledSlot, presenterUserIds: ["other-user"] };
      renderRow(otherSlot);
      expect(
        screen.queryByRole("button", { name: /edit slot/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /remove slot/i }),
      ).not.toBeInTheDocument();
    });
  });
});
