import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render as rtlRender, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useDeleteTopic,
  useEditTopic,
  useProposeTopic,
  useUnvoteTopic,
  useVoteTopic,
} from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";
import { TopicRow } from "@/features/events/ui/TopicRow";

vi.mock("@/features/events/api/use-topics", () => ({
  useVoteTopic: vi.fn(),
  useUnvoteTopic: vi.fn(),
  useDeleteTopic: vi.fn(),
  useProposeTopic: vi.fn(),
  useEditTopic: vi.fn(),
}));

// TopicRow reads pending state via useIsMutating, which needs a QueryClient.
function render(ui: ReactElement) {
  const client = new QueryClient();
  return rtlRender(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

const mockUseVote = vi.mocked(useVoteTopic);
const mockUseUnvote = vi.mocked(useUnvoteTopic);

const topic: Topic = {
  id: 5,
  communityId: 1,
  kind: "REQUEST",
  title: "Intro to event sourcing",
  description: "please",
  tags: [],
  proposerUserId: "user-1",
  proposerDisplayName: "Alice",
  canClose: false,
  canReopen: false,
  voteCount: 2,
  hasVoted: false,
  canEdit: false,
  canDelete: false,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "x",
  updatedAt: "x",
};

const mockUseDelete = vi.mocked(useDeleteTopic);
const mockUsePropose = vi.mocked(useProposeTopic);
const mockUseEdit = vi.mocked(useEditTopic);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseVote.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseUnvote.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseDelete.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUsePropose.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseEdit.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

describe("TopicRow", () => {
  it("renders the title and vote count", () => {
    render(<TopicRow slug="backend-guild" topic={topic} />);
    expect(screen.getByText("Intro to event sourcing")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("votes when the upvote button is pressed", async () => {
    const mutate = vi.fn();
    mockUseVote.mockReturnValue({ mutate, isPending: false } as never);
    render(<TopicRow slug="backend-guild" topic={topic} />);
    await user.click(screen.getByRole("button", { name: /upvote/i }));
    expect(mutate).toHaveBeenCalledWith(5);
  });

  it("unvotes when clicked in voted state", async () => {
    const unvoteMutate = vi.fn();
    mockUseUnvote.mockReturnValue({
      mutate: unvoteMutate,
      isPending: false,
    } as never);
    const votedTopic: Topic = { ...topic, hasVoted: true };
    render(<TopicRow slug="backend-guild" topic={votedTopic} />);
    await user.click(screen.getByRole("button", { name: /remove vote/i }));
    expect(unvoteMutate).toHaveBeenCalledWith(5);
  });

  it("shows expand button and toggles description for long text", async () => {
    const longTopic: Topic = { ...topic, description: "x".repeat(200) };
    render(<TopicRow slug="backend-guild" topic={longTopic} />);
    const showMore = screen.getByRole("button", { name: /show more/i });
    expect(showMore).toBeInTheDocument();
    await user.click(showMore);
    expect(
      screen.getByRole("button", { name: /show less/i }),
    ).toBeInTheDocument();
  });

  it("shows no-description text when description is empty", () => {
    const emptyTopic: Topic = { ...topic, description: "" };
    render(<TopicRow slug="backend-guild" topic={emptyTopic} />);
    expect(screen.getByText(/no description provided/i)).toBeInTheDocument();
  });

  it("opens EditTopicDialog when the edit button is clicked", async () => {
    const editTopic: Topic = { ...topic, canEdit: true };
    render(<TopicRow slug="backend-guild" topic={editTopic} />);
    await user.click(screen.getByRole("button", { name: /^edit/i }));
    expect(screen.getByText("Save")).toBeInTheDocument();
  });

  it("opens DeleteTopicPopover when the delete button is clicked", async () => {
    const deleteTopic: Topic = { ...topic, canDelete: true };
    render(<TopicRow slug="backend-guild" topic={deleteTopic} />);
    await user.click(screen.getByRole("button", { name: /^delete/i }));
    expect(screen.getByText(/from the backlog\?/i)).toBeInTheDocument();
  });

  it("renders the actions slot", () => {
    render(
      <TopicRow
        slug="backend-guild"
        topic={topic}
        actions={<span>Custom action</span>}
      />,
    );
    expect(screen.getByText("Custom action")).toBeInTheDocument();
  });

  describe("animation timing", () => {
    beforeEach(() => {
      vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
        cb(0);
        return 0;
      });
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.runAllTimers();
      vi.useRealTimers();
      vi.unstubAllGlobals();
    });

    it("fires rAF and timeout callbacks on vote", () => {
      render(<TopicRow slug="backend-guild" topic={topic} />);
      // Use fireEvent to avoid userEvent's internal async timers clashing with vi.useFakeTimers()
      fireEvent.click(screen.getByRole("button", { name: /upvote/i }));
      vi.runAllTimers();
      // animation cleanup must have run: button is still accessible
      expect(
        screen.getByRole("button", { name: /upvote/i }),
      ).toBeInTheDocument();
    });
  });
});
