import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMyReactionProposals,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";
import { MyProposals } from "@/features/recognition/ui/MyProposals";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/recognition/api/use-reactions", () => ({
  useMyReactionProposals: vi.fn(),
  useReactionEligibility: vi.fn(),
  useRemoveReaction: vi.fn(),
}));

function proposal(overrides: Partial<ReactionDefinition> = {}) {
  return {
    id: 1,
    name: "yak-shaving",
    imageUrl: "https://cdn/yak.png",
    pending: true,
    proposedByUserId: "u",
    usageCount: 0,
    ...overrides,
  };
}

function givenWaiting(...waiting: ReactionDefinition[]) {
  vi.mocked(useMyReactionProposals).mockReturnValue({ data: waiting } as never);
}

function givenWithdraw(
  overrides: Partial<{
    mutate: ReturnType<typeof vi.fn>;
    isPending: boolean;
    isError: boolean;
    error: unknown;
    variables: number;
  }> = {},
) {
  const withdraw = {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    ...overrides,
  };
  vi.mocked(useRemoveReaction).mockReturnValue(withdraw as never);
  return withdraw;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: {
      mayPropose: true,
      openProposals: 2,
      openProposalLimit: 3,
      approvedLimit: 60,
    },
  } as never);
  givenWithdraw();
});

describe("MyProposals", () => {
  it("draws nothing at all when nothing is waiting", () => {
    givenWaiting();

    const { container } = render(<MyProposals />);

    expect(container).toBeEmptyDOMElement();
  });

  it("says how many of the allowed proposals are open", () => {
    givenWaiting(proposal(), proposal({ id: 2, name: "campfire" }));

    render(<MyProposals />);

    expect(screen.getByText(/2 of 3 open/i)).toBeInTheDocument();
  });

  /** The ceiling arrives from a separate read; a bare count beats a wrong one. */
  it("falls back to the bare count before the limit has arrived", () => {
    vi.mocked(useReactionEligibility).mockReturnValue({} as never);
    givenWaiting(proposal());

    render(<MyProposals />);

    expect(screen.getByText(/^1 open$/i)).toBeInTheDocument();
  });

  it("withdraws the proposal the press names", async () => {
    givenWaiting(proposal(), proposal({ id: 2, name: "campfire" }));
    const withdraw = givenWithdraw();
    render(<MyProposals />);

    await user.click(
      screen.getByRole("button", { name: /withdraw campfire/i }),
    );

    expect(withdraw.mutate).toHaveBeenCalledWith(2);
  });

  it("holds only the row being withdrawn, not the others", () => {
    givenWaiting(proposal(), proposal({ id: 2, name: "campfire" }));
    givenWithdraw({ isPending: true, variables: 2 });

    render(<MyProposals />);

    expect(
      screen.getByRole("button", { name: /withdraw campfire/i }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: /withdraw yak-shaving/i }),
    ).toBeEnabled();
  });

  it("says so when a withdrawal fails", () => {
    givenWaiting(proposal());
    givenWithdraw({
      isError: true,
      error: new ApiError(500, "boom", "SERVER_ERROR"),
    });

    render(<MyProposals />);

    expect(screen.getByRole("alert")).toHaveTextContent(/went wrong/i);
  });
});
