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
import { useAddSlot } from "@/features/events/api/use-slot-sign-up";
import { useTopics } from "@/features/events/api/use-topics";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { AddToSectionForm } from "@/features/events/ui/AddToSectionForm";
import { useUsers } from "@/features/identity/api/use-users";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-backlog-pull", () => ({
  usePresentFromBacklog: vi.fn(),
  useQueueTopic: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useAddSlot: vi.fn(),
  useEditSlot: vi.fn(),
  useRemoveSlot: vi.fn(),
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

const alice = { userId: "u-alice", displayName: "Alice Dupont" };

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
  vi.mocked(useTopics).mockReturnValue({
    isPending: false,
    data: { content: [] },
  } as never);
  vi.mocked(useAddSlot).mockReturnValue(idle());
  vi.mocked(usePresentFromBacklog).mockReturnValue(idle());
  vi.mocked(useQueueTopic).mockReturnValue(idle());
  vi.mocked(useRecordSlot).mockReturnValue(idle());
  stubMemberSelectField(
    {
      useRosterByName: vi.mocked(useRosterByName),
      useMemberSearch: vi.mocked(useMemberSearch),
      useUsers: vi.mocked(useUsers),
    },
    [alice],
  );
});

describe("AddToSectionForm", () => {
  it("opens the slot dialog when the add button is pressed", async () => {
    render(<AddToSectionForm slug="g" session={session} section={section} />);
    expect(
      screen.queryByRole("dialog", { name: /add a slot/i }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /add a slot/i }));
    expect(
      screen.getByRole("dialog", { name: /add a slot/i }),
    ).toBeInTheDocument();
  });

  it("uses the add-slot flow (self sign-up, backlog source) in contribute mode", async () => {
    render(
      <AddToSectionForm
        slug="g"
        session={session}
        section={section}
        mode="contribute"
      />,
    );
    await user.click(screen.getByRole("button", { name: /add a slot/i }));
    expect(screen.getByText(/from backlog/i)).toBeInTheDocument();
  });

  // Reproduces the critical bug: in edit mode, "+ Add a slot" used to open the same dialog as
  // contribute mode, which calls useAddSlot and hard-codes the caller as presenter. A backfilled
  // talk must be credited to whoever actually presented it, never to whoever is typing.
  it("records the slot through useRecordSlot in edit mode, never through useAddSlot which would self-sign-up the caller", async () => {
    const recordMutate = vi.fn();
    vi.mocked(useRecordSlot).mockReturnValue({
      mutate: recordMutate,
      isPending: false,
      error: null,
    } as never);
    const addMutate = vi.fn();
    vi.mocked(useAddSlot).mockReturnValue({
      mutate: addMutate,
      isPending: false,
      error: null,
    } as never);

    render(
      <AddToSectionForm
        slug="g"
        session={session}
        section={section}
        mode="edit"
      />,
    );
    await user.click(screen.getByRole("button", { name: /add a slot/i }));

    // The backlog - a source of proposed *future* topics - must not be offered when backfilling
    // a *past* session.
    expect(screen.queryByText(/from backlog/i)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    expect(recordMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        sectionId: section.id,
        input: expect.objectContaining({ title: "Kafka 101" }) as unknown,
      }),
      expect.anything(),
    );
    expect(addMutate).not.toHaveBeenCalled();
  });

  it("lets an organizer name who actually presented a backfilled slot, by member, not by typing the caller's own name", async () => {
    const recordMutate = vi.fn();
    vi.mocked(useRecordSlot).mockReturnValue({
      mutate: recordMutate,
      isPending: false,
      error: null,
    } as never);

    render(
      <AddToSectionForm
        slug="g"
        session={session}
        section={section}
        mode="edit"
      />,
    );
    await user.click(screen.getByRole("button", { name: /add a slot/i }));
    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await user.click(screen.getByRole("combobox", { name: "Presenters" }));
    await user.click(screen.getByRole("option", { name: "Alice Dupont" }));
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    expect(recordMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        input: expect.objectContaining({ presenters: ["u-alice"] }) as unknown,
      }),
      expect.anything(),
    );
  });
});
