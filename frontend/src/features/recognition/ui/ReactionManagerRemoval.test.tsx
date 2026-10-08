import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRemoveReaction } from "@/features/recognition/api/use-reactions";
import {
  givenAQueueWithOneProposal,
  openShipItRemoval,
  removeReactionResult,
} from "@/features/recognition/ui/reaction-manager-fixture";
import { ReactionManager } from "@/features/recognition/ui/ReactionManager";

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

describe("removing a reaction already in use", () => {
  it("names the usage count and says the placements go with it before removing", async () => {
    render(<ReactionManager />);
    await openShipItRemoval(user);

    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent("48");
    expect(dialog).toHaveTextContent(/placements/i);
  });

  it("confirms the removal with the verb the title used", async () => {
    render(<ReactionManager />);
    await openShipItRemoval(user);

    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent(/remove ship-it\?/i);
    expect(screen.getByRole("button", { name: "Remove" })).toBeInTheDocument();
  });

  it("keeps a failed decline from poisoning the next removal dialog", async () => {
    const declineReset = vi.fn();
    const removeReset = vi.fn();
    vi.mocked(useRemoveReaction)
      .mockReturnValueOnce(removeReactionResult({ reset: declineReset }))
      .mockReturnValueOnce(removeReactionResult({ reset: removeReset }));

    render(<ReactionManager />);
    const declineResetsBeforeOpening = declineReset.mock.calls.length;
    await openShipItRemoval(user);
    expect(removeReset).toHaveBeenCalled();
    expect(declineReset.mock.calls).toHaveLength(declineResetsBeforeOpening);
  });

  it("clears an earlier failure before the removal dialog opens", async () => {
    const reset = vi.fn();
    vi.mocked(useRemoveReaction).mockReturnValue(
      removeReactionResult({ reset }),
    );

    render(<ReactionManager />);
    await openShipItRemoval(user);
    expect(reset).toHaveBeenCalled();
  });

  it("removes an approved reaction and closes the dialog once the removal succeeds", async () => {
    // Runs the closure the component hands in, so the assertion exercises it rather than
    // accepting whatever was passed.
    const mutate = vi.fn((_id: number, options: { onSuccess: () => void }) => {
      options.onSuccess();
    });
    vi.mocked(useRemoveReaction).mockReturnValue(
      removeReactionResult({ mutate }),
    );

    render(<ReactionManager />);
    await openShipItRemoval(user);
    await user.click(screen.getByRole("button", { name: "Remove" }));

    expect(mutate).toHaveBeenCalledWith(
      2,
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
  });
});
