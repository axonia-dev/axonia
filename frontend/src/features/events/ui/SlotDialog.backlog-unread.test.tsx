import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  usePresentFromBacklog,
  useQueueTopic,
} from "@/features/events/api/use-backlog-pull";
import { useAddSlot } from "@/features/events/api/use-slot-sign-up";
import { useTopics } from "@/features/events/api/use-topics";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { SlotDialog } from "@/features/events/ui/SlotDialog";

vi.mock("@/features/events/api/use-backlog-pull", () => ({
  usePresentFromBacklog: vi.fn(),
  useQueueTopic: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useAddSlot: vi.fn(),
  useEditSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-topics", () => ({ useTopics: vi.fn() }));

const session = { id: 9 } as Session;
const section = {
  id: 3,
  sessionId: 9,
  position: 0,
  sectionTypeId: 2,
  sectionType: "Talk",
  sectionTypeIcon: null,
  sectionTypeAccentColor: null,
  title: "Main talk",
  durationMinutes: 60,
  slots: [],
} as Section;

function idle() {
  return { mutate: vi.fn(), isPending: false, error: null } as never;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useAddSlot).mockReturnValue(idle());
  vi.mocked(usePresentFromBacklog).mockReturnValue(idle());
  vi.mocked(useQueueTopic).mockReturnValue(idle());
});

describe("SlotDialog add mode (backlog not loaded)", () => {
  const backlogTopic = {
    id: 42,
    communityId: 1,
    kind: "OFFER",
    title: "Event Sourcing",
    description: "How to use event sourcing",
    proposerDisplayName: "Alice",
    voteCount: 3,
    hasVoted: false,
    canEdit: false,
    canDelete: false,
    status: "PROPOSED",
    scheduledFor: null,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  };

  function addDialog() {
    return (
      <SlotDialog
        mode="add"
        slug="g"
        session={session}
        section={section}
        isOpen
        onOpenChange={vi.fn()}
      />
    );
  }

  it("waits for the backlog before offering a source", () => {
    // given
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: true,
      errorUpdateCount: 0,
    } as never);

    // when
    render(addDialog());

    // then
    expect(screen.getByText("Loading the backlog…")).toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: /new topic/i }),
    ).not.toBeInTheDocument();
  });

  it("says the backlog could not load, and stays on it rather than on a new topic", () => {
    // given
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: false,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);

    // when
    render(addDialog());

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the backlog.",
    );
    expect(screen.getByRole("radio", { name: /from backlog/i })).toBeChecked();
    expect(screen.getByRole("button", { name: /^add$/i })).toBeDisabled();
  });

  it("asks for the backlog again on Try again", async () => {
    // given
    const refetch = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: false,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    } as never);
    render(addDialog());

    // when
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(refetch).toHaveBeenCalledOnce();
  });

  // TanStack Query puts a query with no data back to pending while it is asked again, so a form
  // reading isPending would swap the failure and its Try again for the loading line.
  it("keeps the failure and its Try again while the backlog is asked again", () => {
    // given
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: true,
      errorUpdateCount: 1,
      isFetching: true,
      refetch: vi.fn(),
    } as never);

    // when
    render(addDialog());

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the backlog.",
    );
    expect(
      screen.getByRole("button", { name: "Try again" }),
    ).toBeInTheDocument();
  });

  it("hands the focus to the topic list once Try again brings the backlog", async () => {
    // given
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: false,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const { rerender } = render(addDialog());
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    vi.mocked(useTopics).mockReturnValue({
      data: { content: [backlogTopic] },
      isPending: false,
      errorUpdateCount: 1,
    } as never);
    rerender(addDialog());

    // then
    expect(screen.getByRole("button", { name: /topic/i })).toHaveFocus();
  });

  it("stays on the backlog when Try again finds it empty", async () => {
    // given
    vi.mocked(useTopics).mockReturnValue({
      data: undefined,
      isPending: false,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const { rerender } = render(addDialog());
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    vi.mocked(useTopics).mockReturnValue({
      data: { content: [] },
      isPending: false,
      errorUpdateCount: 1,
    } as never);
    rerender(addDialog());

    // then
    expect(screen.getByRole("button", { name: /topic/i })).toHaveTextContent(
      "No topics in the backlog",
    );
  });
});
