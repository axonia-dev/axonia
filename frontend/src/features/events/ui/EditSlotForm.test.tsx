import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEditSlot } from "@/features/events/api/use-slot-sign-up";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { EditSlotForm } from "@/features/events/ui/EditSlotForm";

vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useEditSlot: vi.fn(),
}));

const s = { id: 9 } as Session;
const sec = {
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
const slotFixture: Slot = {
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
  vi.mocked(useEditSlot).mockReturnValue(idle());
});

describe("EditSlotForm", () => {
  it("pre-fills and saves the description", async () => {
    const mutate = vi.fn();
    vi.mocked(useEditSlot).mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    const slot = { ...slotFixture, notes: "Existing abstract" };
    render(
      <EditSlotForm
        slug="g"
        session={s}
        section={sec}
        slot={slot}
        onDone={() => {}}
      />,
    );
    const field = screen.getByLabelText("Description");
    expect(field).toHaveValue("Existing abstract");
    await user.clear(field);
    await user.type(field, "Updated abstract");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ notes: "Updated abstract" }),
      expect.anything(),
    );
  });

  it("pre-fills description with empty string when slot.notes is null", () => {
    render(
      <EditSlotForm
        slug="g"
        session={s}
        section={sec}
        slot={slotFixture}
        onDone={() => {}}
      />,
    );
    const field = screen.getByLabelText("Description");
    expect(field).toHaveValue("");
  });

  /** The visible field labels, in document order, skipping sr-only duplicates. */
  function visibleFieldLabels(): (string | null)[] {
    return screen
      .getAllByText(/^(Title|Description|Topics|Minutes)$/)
      .filter((el) => !el.className.includes("sr-only"))
      .map((el) => el.textContent);
  }

  it("orders the fields Title, Description, Topics, Minutes", () => {
    render(
      <EditSlotForm
        slug="g"
        session={s}
        section={sec}
        slot={slotFixture}
        onDone={() => {}}
      />,
    );
    expect(visibleFieldLabels()).toEqual([
      "Title",
      "Description",
      "Topics",
      "Minutes",
    ]);
  });

  it("offers a backlog slot no Topics field: Title, Description, Minutes", () => {
    render(
      <EditSlotForm
        slug="g"
        session={s}
        section={sec}
        slot={{ ...slotFixture, topicId: 42 }}
        onDone={() => {}}
      />,
    );
    expect(visibleFieldLabels()).toEqual(["Title", "Description", "Minutes"]);
  });

  it("offers only Save and Cancel - deleting is a separate, confirmed action", () => {
    render(
      <EditSlotForm
        slug="g"
        session={s}
        section={sec}
        slot={slotFixture}
        onDone={() => {}}
      />,
    );
    expect(screen.getByRole("button", { name: /^save$/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /remove slot/i }),
    ).not.toBeInTheDocument();
  });
});
