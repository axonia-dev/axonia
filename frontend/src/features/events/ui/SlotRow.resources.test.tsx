import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import {
  useAddPresenter,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";
import {
  usePresentSlot,
  useRemoveSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { SlotRow } from "@/features/events/ui/SlotRow";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";

// Split out of SlotRow.test.tsx (which was pushing eslint's max-lines): the resource-list and
// footer row's canAdd/isOpen gating, across the scheduling and capture (held session) modes -
// and, since a slot carrying nothing at all renders no block, how many resources a slot carries
// decides whether there is anything on the card to see.
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  usePresentSlot: vi.fn(),
  useWithdrawSlot: vi.fn(),
  useRemoveSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useAddPresenter: vi.fn(),
  useRemovePresenter: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "dev-user" }));
vi.mock("@/features/knowledge/ui/SlotResources", () => ({
  SlotResources: () => <div>slot-resources</div>,
}));
vi.mock("@/features/knowledge/ui/AddResourceButton", () => ({
  AddResourceButton: () => <button type="button">+ Add resource</button>,
}));
vi.mock("@/features/events/ui/SlotDialog", () => ({
  SlotDialog: () => null,
}));

// Every slot rendered in this file keeps its fixture id (7) as its segmentId, so one settable
// counter is enough to say whether a test's slot carries a resource yet.
const resourceCount = vi.hoisted(() => ({ value: 0 }));
vi.mock("@/features/knowledge/api/use-session-resources", () => ({
  useSessionResources: () => ({
    data: Array.from({ length: resourceCount.value }, () => ({ segmentId: 7 })),
  }),
}));

const mockUsePresent = vi.mocked(usePresentSlot);
const mockUseWithdraw = vi.mocked(useWithdrawSlot);
const mockUseRemove = vi.mocked(useRemoveSlot);
const mockUseAddPresenter = vi.mocked(useAddPresenter);
const mockUseRemovePresenter = vi.mocked(useRemovePresenter);
const mockUseMembers = vi.mocked(useMembers);
const mockUseUsers = vi.mocked(useUsers);

const users: Record<string, User> = {
  "uuid-bob": {
    id: "uuid-bob",
    displayName: "Bob Stone",
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  },
};

// `sections` is real, not ceremony: an untitled (open) slot names its resource after the session,
// which reads the section tree to do so.
const session = { id: 9, title: null, sections: [] } as unknown as Session;
const section = { id: 3 } as Section;
const openSlot: Slot = {
  id: 7,
  sectionId: 3,
  position: 0,
  presenterUserIds: [],
  topicId: null,
  title: null,
  notes: null,
  durationMinutes: 20,
  tags: [],
};
const filledSlot: Slot = {
  ...openSlot,
  presenterUserIds: ["dev-user"],
  title: "Pairing on hexagons",
};

function renderRow(
  slot: Slot,
  extra: Partial<React.ComponentProps<typeof SlotRow>> = {},
) {
  return render(
    <SlotRow
      slug="backend-guild"
      session={session}
      section={section}
      slot={slot}
      users={users}
      {...extra}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  resourceCount.value = 0;
  mockUsePresent.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseWithdraw.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseRemove.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseAddPresenter.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  mockUseRemovePresenter.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
  } as never);
  mockUseMembers.mockReturnValue({
    data: {
      content: [],
      totalElements: 0,
      totalPages: 1,
      number: 0,
      size: 100,
    },
  } as never);
  mockUseUsers.mockReturnValue({ data: {} } as never);
});

describe("SlotRow - resource block (scheduling / contribute mode)", () => {
  it("shows the read-only resources list to everyone a filled slot renders for", () => {
    resourceCount.value = 1;
    renderRow(filledSlot);
    expect(screen.getByText("slot-resources")).toBeInTheDocument();
  });

  it("keeps the add-resource link hidden from a member who cannot manage the schedule", () => {
    resourceCount.value = 1;
    renderRow(filledSlot);
    expect(
      screen.queryByRole("button", { name: /add resource/i }),
    ).not.toBeInTheDocument();
  });

  it("offers the add-resource link to a schedule manager", () => {
    renderRow(filledSlot, { canManageSchedule: true });
    expect(
      screen.getByRole("button", { name: /add resource/i }),
    ).toBeInTheDocument();
  });

  // Nobody has claimed the slot yet, but the material can exist before the presenter does: a
  // pre-read, a link, the deck a previous run left behind. Only *love* needs a presenter to
  // credit, and that is gated where it is built, not here.
  it("still offers the resource block on an open (unclaimed) slot", () => {
    renderRow(openSlot, { canManageSchedule: true });
    expect(screen.getByText("slot-resources")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /add resource/i }),
    ).toBeInTheDocument();
  });
});

describe("SlotRow - capture mode (held session)", () => {
  const captureSession = {
    id: 9,
    title: null,
    sections: [],
  } as unknown as Session;
  function renderCapture(
    slot: Slot,
    extra: Partial<React.ComponentProps<typeof SlotRow>> = {},
  ) {
    return render(
      <SlotRow
        slug="backend-guild"
        session={captureSession}
        section={section}
        slot={slot}
        users={users}
        mode="capture"
        {...extra}
      />,
    );
  }

  it("offers a single add-resource entry point on a filled slot", () => {
    renderCapture(filledSlot);
    expect(screen.getByText("slot-resources")).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /add resource/i }),
    ).toHaveLength(1);
  });

  it("offers the add-resource link to any member, without manage rights", () => {
    renderCapture(filledSlot);
    expect(
      screen.getByRole("button", { name: /add resource/i }),
    ).toBeInTheDocument();
  });

  // A held session's slot that nobody signed up for was still a thing that happened in the room:
  // whoever was there can attach the recording or the notes to it.
  it("still offers the resource block on an open slot", () => {
    renderCapture(openSlot);
    expect(screen.getByText("slot-resources")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /add resource/i }),
    ).toBeInTheDocument();
  });
});
