import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import {
  useAddPresenter,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";
import {
  usePresentSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";
import type { Slot } from "@/features/events/domain/slot";
import {
  PresenterStack,
  type PresenterStackProps,
} from "@/features/events/ui/PresenterStack";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { ApiError } from "@/lib/api-client";
import type { Page } from "@/lib/page";

vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  usePresentSlot: vi.fn(),
  useWithdrawSlot: vi.fn(),
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

const mockUsePresentSlot = vi.mocked(usePresentSlot);
const mockUseWithdrawSlot = vi.mocked(useWithdrawSlot);
const mockUseAddPresenter = vi.mocked(useAddPresenter);
const mockUseRemovePresenter = vi.mocked(useRemovePresenter);
const mockUseMembers = vi.mocked(useMembers);
const mockUseUsers = vi.mocked(useUsers);

const users: Record<string, User> = {
  "dev-user": {
    id: "dev-user",
    displayName: "Dev User",
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
  mia: {
    id: "mia",
    displayName: "Mia Eriksson",
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
};

function membersPage(userIds: string[] = []): Page<{ userId: string }> {
  return {
    content: userIds.map((userId) => ({ userId })),
    totalElements: userIds.length,
    totalPages: 1,
    number: 0,
    size: 100,
  };
}

let withdrawMutate: ReturnType<typeof vi.fn>;
let removeMutate: ReturnType<typeof vi.fn>;

function renderStack(
  slotOverrides: Partial<Slot> = {},
  propsOverrides: Partial<Omit<PresenterStackProps, "slot" | "users">> = {},
) {
  const slot: Slot = {
    id: 9,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: "Kafka deep dive",
    notes: null,
    durationMinutes: 30,
    tags: [],
    ...slotOverrides,
  };
  return render(
    <PresenterStack
      slug="eng"
      sessionId={1}
      sectionId={1}
      slot={slot}
      users={users}
      canManageSchedule={false}
      interactive={true}
      {...propsOverrides}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  withdrawMutate = vi.fn();
  removeMutate = vi.fn();

  mockUsePresentSlot.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseWithdrawSlot.mockReturnValue({
    mutate: withdrawMutate,
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseAddPresenter.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseRemovePresenter.mockReturnValue({
    mutate: removeMutate,
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseMembers.mockReturnValue({ data: membersPage() } as never);
  mockUseUsers.mockReturnValue({ data: {} } as never);
});

describe("PresenterStack", () => {
  it("renders a centered add button for an open slot", () => {
    renderStack({ presenterUserIds: [] });
    expect(screen.getByLabelText("Add presenter")).toBeInTheDocument();
  });

  it("draws the add button in the accent on an open slot", () => {
    // when
    renderStack({ presenterUserIds: [] });

    // then
    expect(screen.getByLabelText("Add presenter")).toHaveClass(
      "border-primary",
      "size-7",
    );
  });

  it("draws the add button smaller and neutral beside presenters", () => {
    // when
    renderStack({ presenterUserIds: ["mia"] });

    // then
    const add = screen.getByLabelText("Add presenter");
    expect(add).toHaveClass("border-border-strong", "size-5");
    expect(add).not.toHaveClass("border-primary");
  });

  it("renders nothing for a read-only slot nobody presents", () => {
    // when
    const { container } = renderStack(
      { presenterUserIds: [] },
      { interactive: false },
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("lets the viewer withdraw from their own avatar", async () => {
    renderStack({ presenterUserIds: ["dev-user", "mia"] });
    await user.click(screen.getByRole("button", { name: "Dev User" }));
    await user.click(screen.getByRole("button", { name: "Withdraw" }));
    expect(withdrawMutate).toHaveBeenCalled();
  });

  it("lets an organizer remove another presenter", async () => {
    renderStack({ presenterUserIds: ["mia"] }, { canManageSchedule: true });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    await user.click(screen.getByRole("button", { name: "Remove presenter" }));
    expect(removeMutate).toHaveBeenCalledWith({
      sectionId: 1,
      slotId: 9,
      userId: "mia",
    });
  });

  it("gives a plain member no management popover on someone else's avatar", async () => {
    renderStack({ presenterUserIds: ["mia"] }, { canManageSchedule: false });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    expect(
      screen.queryByRole("button", { name: "Remove presenter" }),
    ).not.toBeInTheDocument();
  });

  it("opens the add popover, pinning self sign-up for a non-presenting viewer", async () => {
    renderStack({ presenterUserIds: [] });
    await user.click(screen.getByLabelText("Add presenter"));
    expect(
      screen.getByRole("button", { name: /i'll present this/i }),
    ).toBeInTheDocument();
  });

  it("offers a presenter (not organizer) the roster to invite a co-presenter, and no self row", async () => {
    mockUseMembers.mockReturnValue({
      data: membersPage(["dev-user", "mia"]),
    } as never);
    mockUseUsers.mockReturnValue({
      data: { mia: { id: "mia", displayName: "Mia Eriksson", email: null } },
    } as never);
    renderStack(
      { presenterUserIds: ["dev-user"] },
      { canManageSchedule: false },
    );

    await user.click(screen.getByLabelText("Add presenter"));

    expect(screen.getByLabelText(/search members/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Mia Eriksson" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /i'll present this/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps only one popover open at a time", async () => {
    renderStack(
      { presenterUserIds: ["dev-user", "mia"] },
      {
        canManageSchedule: true,
      },
    );
    await user.click(screen.getByRole("button", { name: "Dev User" }));
    expect(
      screen.getByRole("button", { name: "Withdraw" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    expect(
      screen.queryByRole("button", { name: "Withdraw" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Remove presenter" }),
    ).toBeInTheDocument();
  });

  it("suppresses the avatar's identity card while its manage popover is open", async () => {
    renderStack({ presenterUserIds: ["dev-user"] });
    // At rest the (CSS-hidden) hover card is in the DOM, ready for hover/focus.
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dev User" }));
    // The avatar keeps focus, so the card would paint through the popover:
    // while the popover is open the card must be gone entirely.
    expect(
      screen.getByRole("button", { name: "Withdraw" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
  });

  it("closes an open popover on Escape", async () => {
    renderStack({ presenterUserIds: ["dev-user"] });
    await user.click(screen.getByRole("button", { name: "Dev User" }));
    expect(
      screen.getByRole("button", { name: "Withdraw" }),
    ).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(
      screen.queryByRole("button", { name: "Withdraw" }),
    ).not.toBeInTheDocument();
  });

  it("renders a withdraw failure inside the manage popover as an alert", async () => {
    mockUseWithdrawSlot.mockReturnValue({
      mutate: withdrawMutate,
      isPending: false,
      isError: true,
      isSuccess: false,
      error: new ApiError(409, "x", "NOT_PRESENTING"),
    } as never);
    renderStack({ presenterUserIds: ["dev-user"] });
    await user.click(screen.getByRole("button", { name: "Dev User" }));
    expect(screen.getByRole("alert")).toHaveTextContent(/not presenting/i);
  });
});
