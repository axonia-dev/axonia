import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useReorderSlots } from "@/features/events/api/use-slot-sign-up";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { SectionBlock } from "@/features/events/ui/SectionBlock";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { stubVerticalLayout } from "@/test/dndkit";

vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useReorderSlots: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/events/ui/SlotRow", () => ({
  SlotRow: ({
    slot,
    users = {},
  }: {
    slot: { id: number };
    users?: Record<string, User>;
  }) => (
    <div>
      slot {slot.id}
      {Object.values(users).map((u) => (
        <span key={u.id}>{u.displayName}</span>
      ))}
    </div>
  ),
}));
vi.mock("@/features/events/ui/AddToSectionForm", () => ({
  AddToSectionForm: ({
    canQueueTopics,
    mode,
  }: {
    canQueueTopics?: boolean;
    mode?: string;
  }) => (
    <div>
      add-to-section canQueueTopics:{String(Boolean(canQueueTopics))} mode:
      {mode}
    </div>
  ),
}));

const mockUseReorderSlots = vi.mocked(useReorderSlots);

function sectionWith(overrides: Partial<Section>): Section {
  return {
    id: 3,
    sessionId: 9,
    position: 0,
    sectionTypeId: 2,
    sectionType: "Talk",
    sectionTypeIcon: null,
    sectionTypeAccentColor: null,
    title: "Main talk",
    durationMinutes: 30,
    slots: [],
    ...overrides,
  };
}

function slotWith(overrides: Partial<Slot>): Slot {
  return {
    id: 1,
    sectionId: 3,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: null,
    notes: null,
    durationMinutes: 15,
    tags: [],
    ...overrides,
  };
}

const session = { id: 9 } as Session;
const talkSection: Section = sectionWith({
  slots: [
    slotWith({
      id: 7,
      presenterUserIds: ["bob"],
      title: "Hexagons",
      durationMinutes: 20,
      tags: [],
    }),
  ],
});
const roundTable: Section = {
  ...talkSection,
  id: 4,
  sectionType: "Round table",
  title: null,
  slots: [],
};
const twoSlotSection: Section = {
  ...talkSection,
  slots: [
    slotWith({ id: 101, title: "First" }),
    slotWith({ id: 102, position: 1, title: "Second" }),
  ],
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseReorderSlots.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: {
      "uuid-bob": { id: "uuid-bob", displayName: "Bob Stone", email: null },
    },
  } as never);
});

describe("SectionBlock", () => {
  it("renders the section heading and its slots", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
      />,
    );
    expect(screen.getByText(/Talk · Main talk/)).toBeInTheDocument();
    expect(screen.getByText("slot 7")).toBeInTheDocument();
  });

  it("keeps the separator with the section title, so a wrap never strands it", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
      />,
    );
    const heading = screen.getByText(/Talk · Main talk/);
    expect(heading.textContent).toBe("Talk ·\u00a0Main talk");
  });

  it("renders the add-to-section form and forwards canQueueTopics", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
        canQueueTopics
      />,
    );
    expect(
      screen.getByText(/add-to-section canQueueTopics:true/),
    ).toBeInTheDocument();
  });

  it("hides the add-to-section form in capture mode", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
        mode="capture"
      />,
    );
    expect(screen.queryByText(/add-to-section/)).not.toBeInTheDocument();
    expect(screen.getByText("slot 7")).toBeInTheDocument();
  });

  it("offers the add-to-section form in edit mode and forwards it, so edit mode records who presented instead of signing the caller up", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
        mode="edit"
      />,
    );
    expect(screen.getByText(/add-to-section/)).toBeInTheDocument();
    expect(screen.getByText(/mode:edit/)).toBeInTheDocument();
  });

  it("hides the add-slot form in readonly mode", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={talkSection}
        mode="readonly"
      />,
    );
    expect(screen.queryByText(/add-to-section/)).not.toBeInTheDocument();
  });

  it("renders an open-discussion message for a round-table", () => {
    render(
      <SectionBlock
        slug="backend-guild"
        session={session}
        section={roundTable}
      />,
    );
    expect(screen.getByText(/open discussion/i)).toBeInTheDocument();
    expect(screen.queryByText(/slot 7/)).not.toBeInTheDocument();
  });

  describe("organizer slot reordering", () => {
    beforeEach(stubVerticalLayout);
    afterEach(() => vi.restoreAllMocks());

    it("lets an organizer reorder slots via the keyboard sensor", async () => {
      const mutate = vi.fn();
      mockUseReorderSlots.mockReturnValue({
        mutate,
        isPending: false,
      } as never);
      render(
        <SectionBlock
          slug="backend-guild"
          session={session}
          section={twoSlotSection}
          canManage
        />,
      );
      const handle = screen.getByRole("button", { name: /reorder slot 1/i });
      handle.focus();
      await user.keyboard("{ }");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ }");
      expect(mutate).toHaveBeenCalledWith([102, 101]);
    });
  });

  it("resolves presenter ids and shows names instead of uuids", () => {
    // given a section whose only slot is presented by uuid-bob
    const section = sectionWith({
      slots: [
        slotWith({
          id: 7,
          presenterUserIds: ["uuid-bob"],
          title: "Hexagons",
          durationMinutes: 20,
          tags: [],
        }),
      ],
    });

    // when
    render(
      <SectionBlock
        slug="g"
        session={{ id: 9 } as Session}
        section={section}
      />,
    );

    // then
    expect(useUsers).toHaveBeenCalledWith(["uuid-bob"]);
    expect(screen.getByText("Bob Stone")).toBeInTheDocument();
  });
});
