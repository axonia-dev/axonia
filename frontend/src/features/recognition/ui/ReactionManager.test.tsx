import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useUsers } from "@/features/identity/api/use-users";
import {
  useApproveReaction,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import {
  allow,
  approveReactionResult,
  givenAQueueWithOneProposal,
  givenDefinitions,
  pending,
  PROPOSER_ID,
  removeReactionResult,
} from "@/features/recognition/ui/reaction-manager-fixture";
import { ReactionManager } from "@/features/recognition/ui/ReactionManager";
import { ApiError } from "@/lib/api-client";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(),
}));

vi.mock("@/features/recognition/api/use-reactions", () => ({
  useAllReactionDefinitions: vi.fn(),
  useApproveReaction: vi.fn(),
  useReactionEligibility: vi.fn(),
  useRemoveReaction: vi.fn(),
}));

beforeEach(givenAQueueWithOneProposal);

/** MemberLabel nests the name in its own span, so the line reads across two elements. */
function proposedBy() {
  return screen.getByText(/^Proposed by/i);
}

describe("ReactionManager", () => {
  it("puts the queue above the reactions already in use", () => {
    render(<ReactionManager />);

    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings[0]).toHaveTextContent(/waiting for approval/i);
    expect(headings[1]).toHaveTextContent(/in use/i);
  });

  it("names who proposed a pending reaction rather than showing their subject", () => {
    render(<ReactionManager />);

    expect(proposedBy()).toHaveTextContent(/proposed by mia nguyen/i);
    expect(screen.queryByText(new RegExp(PROPOSER_ID))).toBeNull();
    expect(vi.mocked(useUsers)).toHaveBeenCalledWith([PROPOSER_ID]);
  });

  it("falls back to the subject while the directory has not answered", () => {
    vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);

    render(<ReactionManager />);

    expect(proposedBy()).toHaveTextContent(
      new RegExp(`proposed by ${PROPOSER_ID}`, "i"),
    );
  });

  it("keeps an erased proposer's proposal in the queue under a neutral label", () => {
    givenDefinitions({ ...pending, proposedByUserId: null });

    render(<ReactionManager />);

    expect(proposedBy()).toHaveTextContent(/proposed by deleted member/i);
  });

  it("shows the usage count before a removal", () => {
    render(<ReactionManager />);

    expect(screen.getByText("48")).toBeInTheDocument();
    expect(screen.getByText("placements")).toBeInTheDocument();
  });

  it("shows the organization's remaining headroom against the backend's own ceiling", () => {
    render(<ReactionManager />);

    expect(screen.getByText(/1 of 60/i)).toBeInTheDocument();
  });

  it("shows the count alone until the ceiling has been fetched", () => {
    vi.mocked(useReactionEligibility).mockReturnValue({
      data: undefined,
    } as never);

    render(<ReactionManager />);

    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings[1]).toHaveTextContent(/in use 1$/i);
  });

  it("hides approve and decline from somebody without reaction:write", () => {
    allow();

    render(<ReactionManager />);

    expect(screen.queryByRole("button", { name: /approve/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /decline/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /remove/i })).toBeNull();
  });

  it("approves a proposal", async () => {
    const mutate = vi.fn();
    vi.mocked(useApproveReaction).mockReturnValue(
      approveReactionResult({ mutate }),
    );

    render(<ReactionManager />);
    await user.click(
      screen.getByRole("button", { name: /approve yak-shaving/i }),
    );

    expect(mutate).toHaveBeenCalledWith(1);
  });

  it("says why an approval failed instead of leaving the queue looking unchanged", () => {
    vi.mocked(useApproveReaction).mockReturnValue(
      approveReactionResult({
        error: new ApiError(409, "at the cap", "REACTION_LIMIT_REACHED"),
      }),
    );

    render(<ReactionManager />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      /limit of custom reactions/i,
    );
  });

  it("drops a failed approval's alert once the queue comes back fresh", () => {
    const reset = vi.fn();
    vi.mocked(useApproveReaction).mockReturnValue(
      approveReactionResult({ reset }),
    );

    const { rerender } = render(<ReactionManager />);
    expect(reset).toHaveBeenCalledTimes(1);

    givenDefinitions({ ...pending, id: 7, name: "campfire" });
    rerender(<ReactionManager />);

    expect(reset).toHaveBeenCalledTimes(2);
  });

  it("says a decline failed too", () => {
    vi.mocked(useRemoveReaction).mockReturnValue(
      removeReactionResult({ isError: true, error: new Error("network down") }),
    );

    render(<ReactionManager />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      /something went wrong/i,
    );
  });

  it("declines a proposal without a confirmation dialog", async () => {
    const mutate = vi.fn();
    vi.mocked(useRemoveReaction).mockReturnValue(
      removeReactionResult({ mutate }),
    );

    render(<ReactionManager />);
    await user.click(
      screen.getByRole("button", { name: /decline yak-shaving/i }),
    );

    expect(mutate).toHaveBeenCalledWith(1);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
