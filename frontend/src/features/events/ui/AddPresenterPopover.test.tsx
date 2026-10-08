import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import type { Membership } from "@/features/community/domain/membership";
import { useAddPresenter } from "@/features/events/api/use-slot-presenters";
import { usePresentSlot } from "@/features/events/api/use-slot-sign-up";
import type { PresenterAssignment } from "@/features/events/domain/presenter";
import { AddPresenterPopover } from "@/features/events/ui/AddPresenterPopover";
import { useUsers } from "@/features/identity/api/use-users";
import { ApiError } from "@/lib/api-client";
import type { Page } from "@/lib/page";

vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useAddPresenter: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  usePresentSlot: vi.fn(),
}));

const mockUseMembers = vi.mocked(useMembers);
const mockUseUsers = vi.mocked(useUsers);
const mockUseAddPresenter = vi.mocked(useAddPresenter);
const mockUsePresentSlot = vi.mocked(usePresentSlot);

const session = { id: 7 } as never;

const mia: Membership = {
  id: 1,
  communityId: 1,
  userId: "mia",
  role: "MEMBER",
  joinedAt: "2024-01-01T00:00:00Z",
  updatedAt: "2024-01-01T00:00:00Z",
};

const bob: Membership = {
  id: 2,
  communityId: 1,
  userId: "bob",
  role: "MEMBER",
  joinedAt: "2024-01-01T00:00:00Z",
  updatedAt: "2024-01-01T00:00:00Z",
};

function membersPage(content: Membership[]): Page<Membership> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 100,
  };
}

let presentMutate: ReturnType<typeof vi.fn>;
let addPresenterMutateAsync: ReturnType<typeof vi.fn>;
let onClose: ReturnType<typeof vi.fn<() => void>>;

function renderPopover(
  overrides: Partial<{
    presenterUserIds: string[];
    alreadyPresenting: boolean;
    canPickOthers: boolean;
  }> = {},
) {
  return render(
    <AddPresenterPopover
      slug="eng"
      sessionId={1}
      sectionId={1}
      slotId={9}
      presenterUserIds={overrides.presenterUserIds ?? []}
      alreadyPresenting={overrides.alreadyPresenting ?? false}
      canPickOthers={overrides.canPickOthers ?? true}
      onClose={onClose}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  presentMutate = vi.fn();
  addPresenterMutateAsync = vi.fn().mockResolvedValue({
    session,
    assigned: true,
    reason: null,
  } satisfies PresenterAssignment);
  onClose = vi.fn();

  mockUseMembers.mockReturnValue({
    data: membersPage([mia, bob]),
  } as never);
  mockUseUsers.mockReturnValue({
    data: {
      mia: { id: "mia", displayName: "Mia Eriksson", email: null },
      bob: { id: "bob", displayName: "Bob Brown", email: null },
    },
  } as never);
  mockUsePresentSlot.mockReturnValue({
    mutate: presentMutate,
    isPending: false,
    isError: false,
  } as never);
  mockUseAddPresenter.mockReturnValue({
    mutateAsync: addPresenterMutateAsync,
    isPending: false,
    isError: false,
  } as never);
});

describe("AddPresenterPopover", () => {
  it("pins the self sign-up row on top", () => {
    renderPopover({ alreadyPresenting: false });
    expect(
      screen.getByRole("button", { name: /i'll present this/i }),
    ).toBeInTheDocument();
  });

  it("hides the self sign-up row once the viewer is already presenting", () => {
    renderPopover({ alreadyPresenting: true });
    expect(
      screen.queryByRole("button", { name: /i'll present this/i }),
    ).not.toBeInTheDocument();
  });

  it("fires present.mutate when the viewer signs themself up", async () => {
    renderPopover({ alreadyPresenting: false });
    await user.click(
      screen.getByRole("button", { name: /i'll present this/i }),
    );
    expect(presentMutate).toHaveBeenCalled();
  });

  it("closes once the self sign-up succeeds", async () => {
    presentMutate.mockImplementation(
      (_vars: unknown, opts?: { onSuccess?: () => void }) =>
        opts?.onSuccess?.(),
    );
    renderPopover({ alreadyPresenting: false });
    await user.click(
      screen.getByRole("button", { name: /i'll present this/i }),
    );
    expect(onClose).toHaveBeenCalled();
  });

  it("stays open when the self sign-up fails, so the error stays readable", async () => {
    presentMutate.mockImplementation(
      (_vars: unknown, opts?: { onError?: (error: unknown) => void }) =>
        opts?.onError?.(new ApiError(409, "x", "ALREADY_PRESENTING")),
    );
    renderPopover({ alreadyPresenting: false });
    await user.click(
      screen.getByRole("button", { name: /i'll present this/i }),
    );
    expect(onClose).not.toHaveBeenCalled();
  });

  it("filters the roster as the organizer types", async () => {
    renderPopover({ canPickOthers: true });
    await user.type(screen.getByLabelText(/search members/i), "mia");
    expect(
      screen.getByRole("button", { name: "Mia Eriksson" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Bob Brown" }),
    ).not.toBeInTheDocument();
  });

  it("offers no roster to a plain member", () => {
    renderPopover({ canPickOthers: false });
    expect(screen.queryByLabelText(/search members/i)).not.toBeInTheDocument();
  });

  it("excludes members who already present the slot from the roster", () => {
    renderPopover({ canPickOthers: true, presenterUserIds: ["mia"] });
    expect(
      screen.queryByRole("button", { name: "Mia Eriksson" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Bob Brown" }),
    ).toBeInTheDocument();
  });

  it("picks a member from the roster by their user id", async () => {
    renderPopover({ canPickOthers: true });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    expect(addPresenterMutateAsync).toHaveBeenCalledWith({
      sectionId: 1,
      slotId: 9,
      presenter: "mia",
    });
  });

  it("closes once a roster pick succeeds", async () => {
    renderPopover({ canPickOthers: true });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("surfaces NOT_FOUND/AMBIGUOUS as a not-matched message, not a silent failure", async () => {
    addPresenterMutateAsync.mockResolvedValue({
      session,
      assigned: false,
      reason: "NOT_FOUND",
    } satisfies PresenterAssignment);
    renderPopover({ canPickOthers: true });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));
    expect(
      await screen.findByText(
        "That person could not be matched to a member of this community.",
      ),
    ).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("renders a mutation error from the self sign-up as an alert", () => {
    mockUsePresentSlot.mockReturnValue({
      mutate: presentMutate,
      isPending: false,
      isError: true,
      error: new ApiError(409, "x", "ALREADY_PRESENTING"),
    } as never);
    renderPopover({ alreadyPresenting: false });
    expect(screen.getByRole("alert")).toHaveTextContent(/already presenting/i);
  });

  it("closes on Escape", async () => {
    renderPopover();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
