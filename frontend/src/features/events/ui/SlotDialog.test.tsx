import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import {
  usePresentFromBacklog,
  useQueueTopic,
} from "@/features/events/api/use-backlog-pull";
import { useRecordSlot } from "@/features/events/api/use-slot-presenters";
import {
  useAddSlot,
  useEditSlot,
} from "@/features/events/api/use-slot-sign-up";
import { useTopics } from "@/features/events/api/use-topics";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { SlotDialog } from "@/features/events/ui/SlotDialog";
import { useUsers } from "@/features/identity/api/use-users";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-backlog-pull", () => ({
  usePresentFromBacklog: vi.fn(),
  useQueueTopic: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useAddSlot: vi.fn(),
  useEditSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useRecordSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-topics", () => ({ useTopics: vi.fn() }));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

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
const slot: Slot = {
  id: 7,
  sectionId: 3,
  position: 0,
  presenterUserIds: ["uuid-alice"],
  topicId: null,
  title: "Hexagons",
  notes: null,
  durationMinutes: 20,
  tags: [],
};

function idle() {
  return { mutate: vi.fn(), isPending: false, error: null } as never;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useTopics).mockReturnValue({
    isPending: false,
    data: { content: [] },
  } as never);
  vi.mocked(useAddSlot).mockReturnValue(idle());
  vi.mocked(usePresentFromBacklog).mockReturnValue(idle());
  vi.mocked(useQueueTopic).mockReturnValue(idle());
  vi.mocked(useEditSlot).mockReturnValue(idle());
  vi.mocked(useRecordSlot).mockReturnValue(idle());
  stubMemberSelectField({
    useRosterByName: vi.mocked(useRosterByName),
    useMemberSearch: vi.mocked(useMemberSearch),
    useUsers: vi.mocked(useUsers),
  });
});

describe("SlotDialog add mode", () => {
  it("shows the source toggle and adds a new-topic slot", async () => {
    const mutate = vi.fn();
    vi.mocked(useAddSlot).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <SlotDialog
        mode="add"
        slug="g"
        session={session}
        section={section}
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByText(/from backlog/i)).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /new topic/i }));
    await user.type(screen.getByLabelText(/subject/i), "Pairing");
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Pairing" }),
      expect.anything(),
    );
  });

  it("sends the typed description when adding a new topic", async () => {
    const mutate = vi.fn();
    vi.mocked(useAddSlot).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <SlotDialog
        mode="add"
        slug="g"
        session={session}
        section={section}
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("radio", { name: /new topic/i }));
    await user.type(screen.getByLabelText(/subject/i), "DDD Patterns");
    await user.type(screen.getByLabelText("Description"), "An intro to DDD");
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "DDD Patterns",
        notes: "An intro to DDD",
      }),
      expect.anything(),
    );
  });

  it("sends notes in the backlog path", async () => {
    const mutate = vi.fn();
    vi.mocked(usePresentFromBacklog).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    vi.mocked(useTopics).mockReturnValue({
      isPending: false,
      data: {
        content: [
          {
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
          },
        ],
      },
    } as never);
    render(
      <SlotDialog
        mode="add"
        slug="g"
        session={session}
        section={section}
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    // backlog source is default when topics exist; select the topic
    await user.click(screen.getByRole("button", { name: /topic/i }));
    await user.click(screen.getByRole("option", { name: /Event Sourcing/i }));
    // type a custom description
    await user.type(screen.getByLabelText("Description"), "My notes");
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ notes: "My notes" }),
      expect.anything(),
    );
  });
});

describe("SlotDialog edit mode", () => {
  it("pre-fills the title and saves title + duration, no source toggle", async () => {
    const mutate = vi.fn();
    vi.mocked(useEditSlot).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <SlotDialog
        mode="edit"
        slug="g"
        session={session}
        section={section}
        slot={slot}
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.queryByText(/from backlog/i)).not.toBeInTheDocument();
    const title = screen.getByLabelText(/title/i);
    expect(title).toHaveValue("Hexagons");
    await user.clear(title);
    await user.type(title, "Hexagons v2");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Hexagons v2", durationMinutes: 20 }),
      expect.anything(),
    );
  });
});

describe("SlotDialog record mode", () => {
  it("titles the dialog 'Record a slot', offers no backlog source, and records through useRecordSlot", async () => {
    const mutate = vi.fn();
    vi.mocked(useRecordSlot).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <SlotDialog
        mode="record"
        slug="g"
        session={session}
        section={section}
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("dialog", { name: /record a slot/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/from backlog/i)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    expect(mutate).toHaveBeenCalledWith(
      {
        sectionId: section.id,
        input: expect.objectContaining({
          title: "Kafka 101",
          presenters: [],
        }) as unknown,
      },
      expect.anything(),
    );
    expect(useAddSlot).not.toHaveBeenCalled();
  });
});
