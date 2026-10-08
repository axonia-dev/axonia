import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLeaveCommunity } from "@/features/community/api/use-leave-community";
import { LeaveCommunityDialog } from "@/features/community/ui/LeaveCommunityDialog";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/community/api/use-leave-community", () => ({
  useLeaveCommunity: vi.fn(),
}));

const mockUseLeaveCommunity = vi.mocked(useLeaveCommunity);

function stub(overrides?: Partial<ReturnType<typeof useLeaveCommunity>>) {
  mockUseLeaveCommunity.mockReturnValue({
    isReady: true,
    isHub: true,
    isPending: false,
    error: null,
    leave: vi.fn(),
    ...overrides,
  });
}

function renderDialog() {
  render(
    <LeaveCommunityDialog
      slug="rust-hub"
      name="Rust Hub"
      isOpen
      onOpenChange={vi.fn()}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stub();
});

describe("LeaveCommunityDialog", () => {
  it("spells out that leaving a hub community signs the member out of the workspace", () => {
    // when
    renderDialog();

    // then
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText("Leave this community?")).toBeInTheDocument();
    expect(screen.getByText(/signs the member out/i)).toBeInTheDocument();
  });

  it("leaves on confirmation", async () => {
    // given
    const leave = vi.fn();
    stub({ leave });
    renderDialog();

    // when
    await user.click(screen.getByRole("button", { name: "Leave" }));

    // then
    expect(leave).toHaveBeenCalledOnce();
  });

  it("does nothing on cancel", async () => {
    // given
    const leave = vi.fn();
    stub({ leave });
    renderDialog();

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(leave).not.toHaveBeenCalled();
  });

  it("shows a human message when leaving fails", () => {
    // given
    stub({ error: new ApiError(409, "x", "LAST_ORGANIZER_CANNOT_LEAVE") });

    // when
    renderDialog();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(/last organizer/i);
  });
});
