import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useDeleteTopic } from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";
import { DeleteTopicPopover } from "@/features/events/ui/DeleteTopicPopover";

vi.mock("@/features/events/api/use-topics", () => ({
  useDeleteTopic: vi.fn(),
}));

const mockUseDelete = vi.mocked(useDeleteTopic);

const topic: Topic = {
  id: 9,
  communityId: 1,
  kind: "REQUEST",
  title: "GraphQL deep dive",
  description: "",
  tags: [],
  proposerUserId: "user-1",
  proposerDisplayName: "Carol",
  canClose: false,
  canReopen: false,
  voteCount: 1,
  hasVoted: false,
  canEdit: false,
  canDelete: true,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "x",
  updatedAt: "x",
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseDelete.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
});

describe("DeleteTopicPopover", () => {
  it("renders confirmation text and buttons when open", () => {
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByText(/from the backlog\?/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /remove/i })).toBeInTheDocument();
  });

  it("renders nothing when closed", () => {
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={false}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.queryByText(/from the backlog\?/i)).not.toBeInTheDocument();
  });

  it("calls onOpenChange(false) when Cancel is pressed", async () => {
    const onOpenChange = vi.fn();
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={onOpenChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("calls deleteTopic.mutate when Remove is pressed", async () => {
    const mutate = vi.fn();
    mockUseDelete.mockReturnValue({ mutate, isPending: false } as never);
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: /remove/i }));
    expect(mutate).toHaveBeenCalledWith(9, expect.anything());
  });

  it("disables the Remove button while deletion is pending", () => {
    mockUseDelete.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as never);
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /remove/i })).toBeDisabled();
  });

  it("shows the topic title in the confirmation message", () => {
    render(
      <DeleteTopicPopover
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByText("GraphQL deep dive")).toBeInTheDocument();
  });
});
