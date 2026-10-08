import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useMyReactionProposals,
  useProposeReaction,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import { ProposeReactionDialog } from "@/features/recognition/ui/ProposeReactionDialog";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/recognition/api/use-reactions", () => ({
  useProposeReaction: vi.fn(),
  useMyReactionProposals: vi.fn(),
  useReactionEligibility: vi.fn(),
  useRemoveReaction: vi.fn(),
}));

const PNG = new File([new Uint8Array([137, 80, 78, 71])], "y.png", {
  type: "image/png",
});

type MutateOpts = {
  onSuccess?: () => void;
  onError?: (error: unknown) => void;
};
type Mutate = (
  variables: { name: string; file: File },
  opts?: MutateOpts,
) => void;

function stubProposeReaction(mutate: Mutate = vi.fn(), isPending = false) {
  vi.mocked(useProposeReaction).mockReturnValue({
    mutate,
    isPending,
  } as unknown as ReturnType<typeof useProposeReaction>);
  return mutate;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubProposeReaction();
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: { name: "Acme Robotics" },
  } as unknown as ReturnType<typeof useCurrentOrganization>);
  vi.mocked(useMyReactionProposals).mockReturnValue({ data: [] } as never);
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: {
      mayPropose: true,
      openProposals: 0,
      openProposalLimit: 3,
      approvedLimit: 60,
    },
  } as never);
  vi.mocked(useRemoveReaction).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

describe("ProposeReactionDialog", () => {
  it("says what happens after the press, before it", async () => {
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    expect(
      await screen.findByText(/once an organizer approves it/i),
    ).toBeInTheDocument();
  });

  /** Absence, not a zero: a first-time proposer meets the form and nothing else. */
  it("draws no waiting section for a member with nothing waiting", () => {
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    expect(screen.queryByText(/waiting for approval/i)).not.toBeInTheDocument();
  });

  it("puts the waiting proposals above the form", async () => {
    vi.mocked(useMyReactionProposals).mockReturnValue({
      data: [
        {
          id: 7,
          name: "yak-shaving",
          imageUrl: "https://cdn/yak.png",
          pending: true,
          proposedByUserId: "u",
          usageCount: 0,
        },
      ],
    } as never);
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    expect(
      await screen.findByRole("button", { name: /withdraw yak-shaving/i }),
    ).toBeInTheDocument();
  });

  it("refuses a name shorter than two characters", async () => {
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    await user.type(screen.getByLabelText(/name/i), "a");
    await user.upload(screen.getByLabelText(/image/i), PNG);

    expect(screen.getByRole("button", { name: /propose/i })).toBeDisabled();
  });

  it("refuses a file over 128 KB before sending it", async () => {
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    await user.upload(
      screen.getByLabelText(/image/i),
      new File([new Uint8Array(129 * 1024)], "big.png", {
        type: "image/png",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(/128 KB/);
  });

  it("previews the chip as it will render", async () => {
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    await user.upload(screen.getByLabelText(/image/i), PNG);

    await waitFor(() =>
      expect(screen.getByRole("img", { name: /preview/i })).toBeInTheDocument(),
    );
  });

  it("sends the proposal and closes on success", async () => {
    const succeed: Mutate = (_variables, opts) => opts?.onSuccess?.();
    const mutate = stubProposeReaction(vi.fn(succeed));
    const onOpenChange = vi.fn();
    render(<ProposeReactionDialog isOpen onOpenChange={onOpenChange} />);

    await user.type(screen.getByLabelText(/name/i), "campfire");
    await user.upload(screen.getByLabelText(/image/i), PNG);
    await user.click(screen.getByRole("button", { name: /propose/i }));

    expect(mutate).toHaveBeenCalledWith(
      { name: "campfire", file: PNG },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("shows the server's own message when the proposal is rejected", async () => {
    stubProposeReaction((_variables, opts) =>
      opts?.onError?.(new ApiError(400, "Name already used", "DUPLICATE_NAME")),
    );
    render(<ProposeReactionDialog isOpen onOpenChange={vi.fn()} />);

    await user.type(screen.getByLabelText(/name/i), "campfire");
    await user.upload(screen.getByLabelText(/image/i), PNG);
    await user.click(screen.getByRole("button", { name: /propose/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Name already used",
    );
  });
});
