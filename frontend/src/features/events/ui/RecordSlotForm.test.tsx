import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import { useRecordSlot } from "@/features/events/api/use-slot-presenters";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { RecordSlotForm } from "@/features/events/ui/RecordSlotForm";
import { useUsers } from "@/features/identity/api/use-users";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useRecordSlot: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseRecordSlot = vi.mocked(useRecordSlot);
const mockUseRosterByName = vi.mocked(useRosterByName);
const mockUseMemberSearch = vi.mocked(useMemberSearch);
const mockUseUsers = vi.mocked(useUsers);

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

const mia = { userId: "mia", displayName: "Mia Eriksson" };
const bob = { userId: "bob", displayName: "Bob Brown" };

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubMemberSelectField(
    {
      useRosterByName: mockUseRosterByName,
      useMemberSearch: mockUseMemberSearch,
      useUsers: mockUseUsers,
    },
    [mia, bob],
  );
});

describe("RecordSlotForm", () => {
  it("has no backlog source toggle: a past session cannot pull in a proposed future topic", () => {
    mockUseRecordSlot.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: null,
    } as never);
    render(
      <RecordSlotForm
        slug="guild"
        session={session}
        section={section}
        onDone={vi.fn()}
      />,
    );
    expect(screen.queryByText(/from backlog/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("records a slot with no presenter when none is chosen", async () => {
    const mutate = vi.fn();
    mockUseRecordSlot.mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <RecordSlotForm
        slug="guild"
        session={session}
        section={section}
        onDone={vi.fn()}
      />,
    );
    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    expect(mutate).toHaveBeenCalledWith(
      {
        sectionId: 3,
        input: expect.objectContaining({
          title: "Kafka 101",
          presenters: [],
        }) as unknown,
      },
      expect.anything(),
    );
  });

  it("records every picked member's userId as a presenter, never a free-text name", async () => {
    const mutate = vi.fn();
    mockUseRecordSlot.mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <RecordSlotForm
        slug="guild"
        session={session}
        section={section}
        onDone={vi.fn()}
      />,
    );
    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await user.click(screen.getByRole("combobox", { name: "Presenters" }));
    await user.click(screen.getByRole("option", { name: "Mia Eriksson" }));
    await user.click(screen.getByRole("button", { name: /show suggestions/i }));
    await user.click(screen.getByRole("option", { name: "Bob Brown" }));
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        input: expect.objectContaining({
          presenters: ["mia", "bob"],
        }) as unknown,
      }),
      expect.anything(),
    );
  });

  it("disables Add until a subject is typed", () => {
    mockUseRecordSlot.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: null,
    } as never);
    render(
      <RecordSlotForm
        slug="guild"
        session={session}
        section={section}
        onDone={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /^add$/i })).toBeDisabled();
  });

  it("shows the mutation's error message", () => {
    mockUseRecordSlot.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: new Error("boom"),
    } as never);
    render(
      <RecordSlotForm
        slug="guild"
        session={session}
        section={section}
        onDone={vi.fn()}
      />,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
