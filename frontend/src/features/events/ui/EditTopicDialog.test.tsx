import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useEditTopic,
  useProposeTopic,
} from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";
import { EditTopicDialog } from "@/features/events/ui/EditTopicDialog";

vi.mock("@/features/events/api/use-topics", () => ({
  useProposeTopic: vi.fn(),
  useEditTopic: vi.fn(),
}));

const mockUsePropose = vi.mocked(useProposeTopic);
const mockUseEdit = vi.mocked(useEditTopic);

const topic: Topic = {
  id: 7,
  communityId: 1,
  kind: "OFFER",
  title: "DDD Workshop",
  description: "Deep dive into DDD",
  tags: [],
  proposerUserId: "user-1",
  proposerDisplayName: "Bob",
  speakerUserIds: [],
  canManageSpeakers: false,
  canClose: false,
  canReopen: false,
  voteCount: 3,
  hasVoted: false,
  canEdit: true,
  canDelete: false,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "x",
  updatedAt: "x",
};

beforeEach(() => {
  vi.clearAllMocks();
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

describe("EditTopicDialog", () => {
  it("renders the form when open", () => {
    render(
      <EditTopicDialog
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByText("Edit topic")).toBeInTheDocument();
    expect(screen.getByText("Save")).toBeInTheDocument();
  });

  it("does not render dialog content when closed", () => {
    render(
      <EditTopicDialog
        slug="backend-guild"
        topic={topic}
        isOpen={false}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.queryByText("Edit topic")).not.toBeInTheDocument();
  });

  it("pre-fills the form with the topic values", () => {
    render(
      <EditTopicDialog
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByDisplayValue("DDD Workshop")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Deep dive into DDD")).toBeInTheDocument();
  });

  it("offers no kind field", () => {
    // when
    render(
      <EditTopicDialog
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );

    // then
    expect(screen.queryByText("Kind")).not.toBeInTheDocument();
  });

  it("saves without a kind in the request", async () => {
    // given
    const mutate = vi.fn();
    mockUseEdit.mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
    } as never);
    render(
      <EditTopicDialog
        slug="backend-guild"
        topic={topic}
        isOpen={true}
        onOpenChange={vi.fn()}
      />,
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      topicId: 7,
      input: {
        title: "DDD Workshop",
        description: "Deep dive into DDD",
        tags: [],
      },
    });
  });
});
