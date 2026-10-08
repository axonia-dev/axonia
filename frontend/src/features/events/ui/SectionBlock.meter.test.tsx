import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { SectionBlock } from "@/features/events/ui/SectionBlock";
import type { SessionMode } from "@/features/events/ui/session-mode";

// Split out of SectionBlock.test.tsx (which was pushing eslint's max-lines): where the section's
// own time meter sits - between its heading and its talks, when mode or shape hides it, and never
// as a ratio a round table would only ever read against its own length.
vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useReorderSlots: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(() => ({ data: {} })),
}));
vi.mock("@/features/events/ui/SlotRow", () => ({
  SlotRow: ({ slot }: { slot: { id: number } }) => <div>slot {slot.id}</div>,
}));
vi.mock("@/features/events/ui/AddToSectionForm", () => ({
  AddToSectionForm: () => null,
}));

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

function renderBlock(mode: SessionMode, overrides: Partial<Section> = {}) {
  const section = sectionWith({
    durationMinutes: 30,
    slots: [slotWith({ durationMinutes: 20 })],
    ...overrides,
  });
  const props = { slug: "dojo", session, section, mode };
  return render(<SectionBlock {...props} />);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("where the meter sits", () => {
  it("states its own consumption between its heading and the talks", () => {
    const { container } = renderBlock("contribute");
    const heading = screen.getByRole("heading", { level: 4 });
    const meter = screen.getByRole("progressbar");
    const list = container.querySelector("ul");
    const FOLLOWING = Node.DOCUMENT_POSITION_FOLLOWING;
    expect(heading).toHaveTextContent("20 / 30 min");
    expect(heading.compareDocumentPosition(meter)).toBe(FOLLOWING);
    expect(meter.compareDocumentPosition(list as Node)).toBe(FOLLOWING);
  });

  it("drops the meter on a held session, where a budget guards nothing", () => {
    renderBlock("capture");
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4 })).toHaveTextContent(
      "20 min",
    );
  });

  it("brings the meter back when a held session is opened for editing", () => {
    renderBlock("edit");
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("says only the minutes when no budget was set", () => {
    renderBlock("contribute", { durationMinutes: null });
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4 })).toHaveTextContent(
      "20 min",
    );
  });

  it("draws no meter for a round table, whose own length is not a budget on anything", () => {
    renderBlock("contribute", { slots: [] });
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4 })).toHaveTextContent(
      "30 min",
    );
  });

  it("still draws the meter for a section with talks in that same mode, proving the gate is about the shape and not the mode", () => {
    renderBlock("contribute");
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("drops the meter on a readonly session too, since a cancelled or skipped plan is nothing left to measure against", () => {
    renderBlock("readonly");
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4 })).toHaveTextContent(
      "20 min",
    );
  });
});
