import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { SectionType } from "@/features/events/domain/section-type";
import type { Session } from "@/features/events/domain/session";
import { SessionStructureEditor } from "@/features/events/ui/SessionStructureEditor";
import { stubVerticalLayout } from "@/test/dndkit";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const mutate = {
  add: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  reorder: vi.fn(),
};

vi.mock("@/features/events/api/use-session-structure", () => ({
  useAddSection: () => ({ mutate: mutate.add }),
  useUpdateSection: () => ({ mutate: mutate.update }),
  useRemoveSection: () => ({ mutate: mutate.remove }),
  useReorderSections: () => ({ mutate: mutate.reorder }),
}));

const sectionTypes: SectionType[] = [
  {
    id: 1,
    label: "Talk",
    expectsPresenter: true,
    icon: null,
    accentColor: null,
  },
  {
    id: 2,
    label: "Round-table",
    expectsPresenter: false,
    icon: null,
    accentColor: null,
  },
];

function sessionWith(sections: Session["sections"]): Session {
  return {
    id: 9,
    seriesId: null,
    communityId: 1,
    status: "SCHEDULED",
    title: null,
    occurrenceDate: null,
    scheduledAt: "2099-01-01T18:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: null,
    attendeeCount: 0,
    notes: null,
    recordingUrl: null,
    place: EMPTY_PLACE,
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: null,
    sections,
    createdAt: "x",
    updatedAt: "x",
    closedAt: null,
    wrapUpReleaseAt: null,
    wrapUpStatus: null,
    upcomingReleaseAt: null,
    upcomingStatus: null,
  };
}

function section(
  id: number,
  slots: number,
  sectionTypeId = 1,
): Session["sections"][number] {
  return {
    id,
    sessionId: 9,
    position: id,
    sectionTypeId,
    sectionType: "Talk",
    sectionTypeIcon: null,
    sectionTypeAccentColor: null,
    title: null,
    durationMinutes: null,
    slots: Array.from({ length: slots }, (_, i) => ({
      id: id * 100 + i,
      sectionId: id,
      position: i,
      presenterUserIds: [],
      topicId: null,
      title: null,
      notes: null,
      durationMinutes: 0,
      tags: [],
    })),
  };
}

beforeEach(() => vi.clearAllMocks());

describe("SessionStructureEditor", () => {
  it("adds a section with the first section type", async () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([])}
        sectionTypes={sectionTypes}
        budgetMinutes={60}
      />,
    );
    await user.click(screen.getByRole("button", { name: /add section/i }));
    expect(mutate.add).toHaveBeenCalledWith({
      sectionTypeId: 1,
      title: null,
      durationMinutes: null,
    });
  });

  it("disables remove for a section that has slots", () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([section(21, 2)])}
        sectionTypes={sectionTypes}
        budgetMinutes={60}
      />,
    );
    expect(
      screen.getByRole("button", { name: /remove section 1/i }),
    ).toBeDisabled();
  });

  it("removes an empty section", async () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([section(21, 0)])}
        sectionTypes={sectionTypes}
        budgetMinutes={60}
      />,
    );
    await user.click(screen.getByRole("button", { name: /remove section 1/i }));
    expect(mutate.remove).toHaveBeenCalledWith(21);
  });

  it("commits a title change on blur", async () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([section(21, 0)])}
        sectionTypes={sectionTypes}
        budgetMinutes={60}
      />,
    );
    const title = screen.getByLabelText(/title/i);
    await user.type(title, "Intro");
    await user.tab();
    expect(mutate.update).toHaveBeenCalledWith({
      sectionId: 21,
      input: { sectionTypeId: 1, title: "Intro", durationMinutes: null },
    });
  });

  it("shows a hint when there are no section types", () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([])}
        sectionTypes={[]}
        budgetMinutes={60}
      />,
    );
    expect(screen.getByText(/create a section type/i)).toBeInTheDocument();
  });

  /**
   * The budget comes from the dialog that owns the length draft, not from the stored session: the
   * meter has to answer the number being typed, or an organizer shortening a session watches the
   * overrun they are trying to fix stay exactly where it was.
   */
  it("measures the sections against the budget it is handed, not the stored one", () => {
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([{ ...section(21, 0), durationMinutes: 30 }])}
        sectionTypes={sectionTypes}
        budgetMinutes={20}
      />,
    );
    expect(screen.getByText(/30 \/ 20 min/)).toBeInTheDocument();
  });

  it("hands a typed length up to its owner rather than writing it itself", async () => {
    const onBudgetChange = vi.fn();
    render(
      <SessionStructureEditor
        slug="g"
        session={sessionWith([section(21, 0)])}
        sectionTypes={sectionTypes}
        budgetMinutes={20}
        onBudgetChange={onBudgetChange}
      />,
    );
    const length = screen.getByLabelText("Session length in minutes");
    await user.clear(length);
    await user.type(length, "45");

    expect(onBudgetChange).toHaveBeenLastCalledWith(45);
    expect(mutate.update).not.toHaveBeenCalled();
  });

  describe("reorder", () => {
    beforeEach(stubVerticalLayout);
    afterEach(() => vi.restoreAllMocks());

    it("reorders via keyboard drag handle", async () => {
      render(
        <SessionStructureEditor
          slug="g"
          session={sessionWith([section(21, 0), section(22, 0)])}
          sectionTypes={sectionTypes}
          budgetMinutes={60}
        />,
      );
      const handle = screen.getByRole("button", { name: /reorder section 1/i });
      handle.focus();
      await user.keyboard("{ }");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ }");
      expect(mutate.reorder).toHaveBeenCalledWith([22, 21]);
    });
  });
});
